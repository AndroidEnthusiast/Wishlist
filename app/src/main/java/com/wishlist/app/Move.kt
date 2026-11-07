package com.wishlist.app

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
import androidx.compose.material3.CircularProgressIndicator
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
    class Done(val ids: Set<Long>, val from: String, val to: String, val toName: String, val failed: String?)

    var done by mutableStateOf<Done?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun ask(ids: Set<Long>) {
        if (ids.isNotEmpty()) picking = ids
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

    /** Runs a move from the open list and shows its message. */
    fun start(c: Context, to: String, ids: Set<Long>, scope: kotlinx.coroutines.CoroutineScope) {
        val from = Repo.openMine
        picking = null
        busy = true
        scope.launch {
            val (moved, problem) = move(c, from, to, ids)
            busy = false
            done = Done(moved, from, to, Repo.mineName(to), problem)
        }
    }

    fun undo(c: Context, d: Done, scope: kotlinx.coroutines.CoroutineScope) {
        done = null
        if (d.ids.isEmpty()) return
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
    MoveFlow.picking?.let { ids -> MoveToDialog(ids, onPick = { to -> MoveFlow.start(ctx, to, ids, scope) }, onClose = { MoveFlow.picking = null }) }
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
        val msg = (if (d.ids.size == 1) tr("Moved to “%s”", name) else tr("%1\$d wishes moved to “%2\$s”", d.ids.size, name)) +
            (d.failed?.let { " " + tr("Some couldn’t move.") + " " + it } ?: "")
        val r = snack.showToast(message = msg, actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) MoveFlow.undo(ctx, d, scope) else MoveFlow.finish(d)
    }
}

private fun shortName(s: String): String = if (s.length <= 30) s else s.take(29).trimEnd() + "…"

/** "Move to…": your other wishlists, then "+ New wishlist" (the usual form; the wishes go to the new list). */
@Composable
fun MoveToDialog(ids: Set<Long>, onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val others = remember(Repo.mineVersion, Repo.mineIds) { Repo.allMine(ctx).filter { it.id != Repo.openMine } }
    var newList by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (ids.size == 1) tr("Move to…") else tr("Move %d wishes to…", ids.size)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (others.isEmpty()) Text(
                    tr("You have one wishlist. Make a new one to move wishes to."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)
                )
                others.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(d.id) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Repo.mineName(d.id), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(wishesLabel(d.wishes.size), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { newList = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Add, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                    Text(tr("New wishlist"), style = MaterialTheme.typography.bodyLarge, color = cs.primary, modifier = Modifier.padding(start = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
    if (newList) NewListDialog(onCreated = { id -> newList = false; onPick(id) }, onClose = { newList = false })
}

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
    Box {
        content()
        if (selecting) {
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier.matchParentSize().clip(shape)
                    .then(if (on) Modifier.background(cs.primary.copy(alpha = 0.14f)).border(3.dp, cs.primary, shape) else Modifier)
                    .semantics { selected = on }
                    .combinedClickable(onClickLabel = if (on) tr("Untick") else tr("Tick"), onClick = onToggle, onLongClick = onToggle)
            ) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).clip(CircleShape)
                        .background(if (on) cs.primary else cs.surface.copy(alpha = 0.9f))
                        .border(2.dp, if (on) cs.primary else cs.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Icon(AppIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
