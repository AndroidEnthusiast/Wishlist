package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * "Add to my wishlist" (owner, 18 November 2026): in the ⋮ of someone else's wish (a friend's list, a group member's,
 * a gift exchange's or a collab wishlist's), copy it straight into one of your own lists: a small list picker, then
 * "Added to …" with Undo. Only what describes the thing comes along (link, name, photo, price, currency); never their
 * note, size, quantity, "Love this", reservations or reactions. A new wish of yours with its own id.
 */
object AddToMine {
    /** The wish whose list picker is open. */
    var asking by mutableStateOf<Wish?>(null)
    /** What was just added (the list and the new wish), for the message with Undo. */
    var done by mutableStateOf<Pair<String, Long>?>(null)

    fun ask(w: Wish) { asking = w }

    /** Your copy of [w]: a new id, only what describes the thing, an online photo only (a phone file of theirs isn't yours). */
    fun copyOf(w: Wish, id: Long): Wish = Wish(
        id = id, url = w.url, title = w.title, desc = w.desc,
        image = w.image.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty(),
        price = w.price, oldPrice = null, currency = w.currency, category = ""
    )

    fun add(c: Context, listId: String, w: Wish): Long {
        val taken = Repo.allMine(c).flatMap { it.wishes }.map { it.id }.toSet() + Repo.myBin.map { it.id }
        var id = System.currentTimeMillis()
        while (id in taken) id++
        val mine = copyOf(w, id)
        Repo.updateMine(c, listId) { ws -> listOf(mine) + ws }
        mine.price?.let { History.record(c, id, it) }
        return id
    }

    fun undo(c: Context, listId: String, id: Long) {
        if (listId in Repo.mineIds) Repo.updateMine(c, listId) { ws -> ws.filter { it.id != id } }
    }
}

/** The list picker and the "Added to …" message with Undo; one host for the whole app (AppScreen). */
@Composable
fun AddToMineHost(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val motion = LocalCardMotion.current
    val cs = MaterialTheme.colorScheme
    AddToMine.asking?.let { w ->
        val lists = remember(Repo.mineIds, Repo.mineVersion) { Repo.mineIds }
        // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt), the wish on top.
        PickSheet(tr("Add to my wishlist"), onClose = { AddToMine.asking = null }, top = { MovingWishes(setOf(w.id), listOf(w)) }) {
            SheetGroup(lists.map { id ->
                {
                    SheetRow({ androidx.compose.material3.Icon(listKindIcon(false), null, tint = cs.onSurfaceVariant) }, Repo.mineName(id), {
                        AddToMine.asking = null
                        // A copy peels off the friend's card and flies to the Home icon, like Copy (owner, 25 November 2026).
                        val add = { AddToMine.done = id to AddToMine.add(ctx, id, w) }
                        motion?.fly(listOf(w.id), "home", peel = true) { add() } ?: add()
                    })
                }
            })
        }
    }
    val d = AddToMine.done
    LaunchedEffect(d) {
        if (d == null) return@LaunchedEffect
        val (listId, id) = d
        snack.currentSnackbarData?.dismiss()
        val r = snack.showToast(message = tr("Added to “%s”", Repo.mineName(listId)), actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) AddToMine.undo(ctx, listId, id)
        AddToMine.done = null
    }
}
