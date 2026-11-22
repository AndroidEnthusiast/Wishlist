package com.wishlist.app

import androidx.compose.ui.graphics.Color

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/*
 * Moving wishes to another of your own wishlists (owner, 7 November 2026): "Move to…" in a wish's ⋮ menu, or several
 * at once after a long press (select, then Move to…). A picker lists your other wishlists and "+ New wishlist"; then
 * "Moved to “…”" with Undo (which moves them back).
 *
 * Friends may have reserved a wish you move (you never see that). When both lists are online, the reservations (and
 * reactions and open "still being bought?" questions) move along on the server first (supabase-collab.sql,
 * wl_move_item), so nobody buys it twice and the giver isn't told it's gone; their phone or browser follows the wish to
 * its new list (wl_moved_mine). A list that isn't online (Only me): the wish simply disappears for friends, and a giver
 * who reserved it is told as for a removal (owner's choice).
 */
object MoveFlow {
    /** The wishes of the open list waiting for a list to be picked, or null. */
    var picking by mutableStateOf<Set<Long>?>(null)

    /** The last move, for its "Moved to …" message and Undo. */
    class Done(
        val ids: Set<Long>, val from: String, val to: String, val toName: String, val failed: String?,
        // To or from a collab wishlist (MoveAcross.kt): a copy instead of a move, and its own way back.
        val copied: Boolean = false, val undoer: (suspend () -> String?)? = null
    )

    /**
     * Step 1, "Move or copy?" (owner, 21 November 2026: choose the action first, then the wishlist; MoveChoose.kt): the
     * wishes waiting for Move or Copy. Then [picking] asks where, with [copying] saying which.
     */
    var choosing by mutableStateOf<Set<Long>?>(null)
    var copying by mutableStateOf(false)

    /** The list that is open: one of your own (its id) or a collab wishlist (MoveAcross's key). */
    fun sourceKey(): String = Shared.active?.let { MoveAcross.collabKey(it) } ?: Repo.openMine

    fun nameOf(key: String): String =
        if (MoveAcross.isCollab(key)) Shared.lists.firstOrNull { it.code == MoveAcross.codeOf(key) }?.name.orEmpty() else Repo.mineName(key)

    var done by mutableStateOf<Done?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun ask(ids: Set<Long>) {
        if (ids.isNotEmpty()) choosing = ids
    }

    /** ← or Back on "Move to…": back to Move or Copy. */
    fun back() {
        val ids = picking ?: return
        picking = null; choosing = ids
    }

    /** Move or Copy was chosen: now the wishlist. */
    fun choose(copy: Boolean) {
        val ids = choosing ?: return
        copying = copy; choosing = null; picking = ids
    }

    /**
     * Moves [ids] from your list [from] to [to]. Online first (reservations), then on the phone. A wish whose
     * reservations couldn't be moved (offline, or before supabase-collab.sql) stays where it was. Returns what moved
     * and the problem, if any.
     */
    suspend fun move(c: Context, from: String, to: String, ids: Set<Long>): Pair<Set<Long>, String?> {
        var src = Repo.mine(c, from)
        var dst = Repo.mine(c, to)
        val wishes = src.wishes.filter { it.id in ids }
        // A new list that is about to go online (App settings' choice): let it go online first.
        if (src.code != null && dst.code == null && wishes.any { !it.priv }) {
            try { ListVisibility.trySwitch(c) } catch (e: Exception) {}
            dst = Repo.mine(c, to)
        }
        var problem: String? = null
        val ok = HashSet<Long>()
        for (w in wishes) {
            val online = !w.priv && src.code != null && src.token != null && dst.code != null && dst.token != null
            if (online) try {
                Cloud.rpc(
                    "wl_move_item", JSONObject().put("p_from", src.code).put("p_from_token", src.token)
                        .put("p_to", dst.code).put("p_to_token", dst.token).put("p_item", w.id)
                )
            } catch (e: Exception) {
                problem = if ((e.message ?: "").contains("wl_move_item")) tr("Moving needs an update in Supabase first.")
                else tr("Couldn’t move. Check your connection.")
                continue
            }
            ok += w.id
        }
        val moved = Repo.moveMine(c, from, to, ok)
        return moved to problem
    }

    /**
     * The wishlist is picked: move or copy as chosen first (owner, 21 November 2026; before, "Move or copy?" came after
     * the list, 16 November 2026). Copying works between all your lists and co-op wishlists.
     */
    fun start(c: Context, to: String, ids: Set<Long>, scope: kotlinx.coroutines.CoroutineScope) {
        picking = null
        go(c, to, ids, copying, scope)
    }

    /** Moves (or with [copy], copies) [ids] from the open list to [to], after "Move or copy?". */
    fun go(c: Context, to: String, ids: Set<Long>, copy: Boolean, scope: kotlinx.coroutines.CoroutineScope) {
        val from = sourceKey()
        if (MoveAcross.isCollab(from) || MoveAcross.isCollab(to)) { across(c, to, ids, copy = copy, scope = scope); return }
        choosing = null
        if (copy) {
            // Between your own lists a copy is the same wish on both (as "Add wishes from your other lists").
            val wishes = Repo.mine(c, from).wishes.filter { it.id in ids }
            val added = Repo.copyInto(c, to, wishes)
            // Already on that list: nothing new, but it is there, so say so as a copy without Undo.
            done = Done(if (added.isEmpty()) ids else added, from, to, Repo.mineName(to), null, copied = true,
                undoer = if (added.isEmpty()) null else ({ Repo.uncopy(c, to, added); null }))
            return
        }
        busy = true
        scope.launch {
            val (moved, problem) = move(c, from, to, ids)
            busy = false
            done = Done(moved, from, to, Repo.mineName(to), problem)
        }
    }

    /** To or from a collab wishlist (MoveAcross.kt). */
    fun across(c: Context, to: String, ids: Set<Long>, copy: Boolean, scope: kotlinx.coroutines.CoroutineScope) {
        val from = sourceKey()
        choosing = null
        busy = true
        scope.launch {
            val r = MoveAcross.run(c, from, to, ids, copy)
            busy = false
            done = Done(r.done, from, to, nameOf(to), r.problem, copied = copy, undoer = r.undo)
        }
    }

    fun undo(c: Context, d: Done, scope: kotlinx.coroutines.CoroutineScope) {
        done = null
        if (d.ids.isEmpty()) return
        if (d.undoer != null) {
            busy = true
            scope.launch {
                val problem = d.undoer.invoke()
                busy = false
                if (problem != null) done = Done(emptySet(), d.to, d.from, nameOf(d.from), problem)
            }
            return
        }
        busy = true
        scope.launch {
            val (_, problem) = move(c, d.to, d.from, d.ids)
            busy = false
            if (problem != null) done = Done(emptySet(), d.to, d.from, Repo.mineName(d.from), problem)
        }
    }

    fun finish(d: Done) {
        if (done === d) done = null
    }
}

/** The list picker and the "Moved to …" message with Undo. Placed once, in the main screen. */
@Composable
fun MoveHost(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Step 1: Move or Copy (MoveChoose.kt), step 2: which wishlist; the wish shows in both (owner, 21 November 2026).
    // One sheet for both steps (owner, 22 November 2026): it stays up while step 1 turns into step 2.
    (MoveFlow.choosing ?: MoveFlow.picking)?.let { ids ->
        MoveSheet(
            ids, picking = MoveFlow.choosing == null, copy = MoveFlow.copying,
            onChoose = { MoveFlow.choose(it) }, onBack = { MoveFlow.back() },
            onPick = { to -> MoveFlow.start(ctx, to, ids, scope) },
            onClose = { MoveFlow.choosing = null; MoveFlow.picking = null }
        )
    }
    val d = MoveFlow.done
    LaunchedEffect(d) {
        if (d == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        if (d.ids.isEmpty()) {
            snack.showToast(d.failed ?: tr("Couldn’t move. Check your connection."))
            MoveFlow.finish(d)
            return@LaunchedEffect
        }
        val name = shortName(d.toName)
        val msg = (when {
            d.copied && d.ids.size == 1 -> tr("Copied to “%s”", name)
            d.copied -> tr("%1\$d wishes copied to “%2\$s”", d.ids.size, name)
            d.ids.size == 1 -> tr("Moved to “%s”", name)
            else -> tr("%1\$d wishes moved to “%2\$s”", d.ids.size, name)
        }) +
            (d.failed?.let { " " + tr("Some couldn’t move.") + " " + it } ?: "")
        val r = snack.showToast(message = msg, actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) MoveFlow.undo(ctx, d, scope) else MoveFlow.finish(d)
    }
}

internal fun shortName(s: String): String = if (s.length <= 30) s else s.take(29).trimEnd() + "…"

/** A small round spinner while a move runs (it can take a moment online). */
@Composable
fun MoveBusy(modifier: Modifier = Modifier) {
    if (MoveFlow.busy) CircularProgressIndicator(modifier.size(20.dp), strokeWidth = 2.dp)
}

/**
 * A wish while selecting (after a long press on your own list, owner 7 November 2026): a tap ticks it or unticks it;
 * a ticked wish gets the accent colour's edge and a filled tick in its top left corner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Selectable(selecting: Boolean, on: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Quiet (2.207): the card's own corners and a 2 dp line; chaos as before.
    val quiet = LocalLook.current.design == "quiet"
    Box {
        androidx.compose.runtime.CompositionLocalProvider(LocalSelecting provides selecting) { content() }
        if (selecting) {
            val shape = if (quiet) Squircle(20.dp) else RoundedCornerShape(18.dp)
            Box(
                Modifier.matchParentSize().clip(shape)
                    .then(if (on) Modifier.background(cs.primary.copy(alpha = if (quiet) 0.10f else 0.14f)).border(if (quiet) 2.dp else 3.dp, cs.primary, shape) else Modifier)
                    .semantics { selected = on }
                    .combinedClickable(onClickLabel = if (on) tr("Untick") else tr("Tick"), onClick = onToggle, onLongClick = onToggle)
            ) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).clip(CircleShape)
                        // Quiet: the empty circle is white on the light photo surface, in light and dark alike.
                        .background(if (on) cs.primary else if (quiet) Color.White.copy(alpha = 0.92f) else cs.surface.copy(alpha = 0.9f))
                        .border(2.dp, if (on) cs.primary else cs.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Icon(AppIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
