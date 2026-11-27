package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * The state holder of the pages behind Home's numbers (the rebuild, owner 19 November 2026; MAP "How a screen is
 * built"): All your wishes and Wishes you love. What is selected and open, and what a tap changes there; the actions
 * work on the list that has the wish's original (its copies follow, owner: "every copy, everywhere"). The page
 * (OverviewScreen.kt) only draws; your lists stay in Repo.
 */
@Stable
internal class OverviewState(private val ctx: Context) {
    var unstarring by mutableStateOf<Placed?>(null)
    // Like any list (owner, 12 November 2026): a long press selects, the ⋮ opens the wish's sheet.
    var picked by mutableStateOf(setOf<Long>())
    val selecting: Boolean get() = picked.isNotEmpty()
    var editing by mutableStateOf<Wish?>(null)
    var history by mutableStateOf<Wish?>(null)
    // "From which list?" for Move to… or Delete, when the wishes are on more than one of your lists.
    var fromAsk by mutableStateOf<Pair<String, Set<Long>>?>(null)

    fun toggle(id: Long) { picked = if (id in picked) picked - id else picked + id }

    /** Makes [list] the open one, so the usual actions (and their Undo) work on the wish there. */
    private fun focus(list: String) { Shared.close(); Repo.openMine(ctx, list) }

    /** The list with the original of wish [p] (else the first list it is on), made the open one. */
    private fun focusOriginal(p: Placed) = focus(Repo.originalOf(ctx, p.w.id) ?: p.listIds.first())

    /** Moves or deletes [ids] from [list], or (null, Delete only) from every list they are on. */
    fun act(action: String, ids: Set<Long>, list: String?) {
        if (action == "move" && list != null) {
            focus(list)
            MoveFlow.ask(ids.filter { id -> Repo.myWishes.any { it.id == id } }.toSet())
        } else if (action == "delete") {
            val byList = if (list != null) mapOf(list to ids)
            else ids.groupBy { Repo.originalOf(ctx, it) ?: Repo.openMine }.mapValues { it.value.toSet() }
            // The selection ends once deleted; Cancel keeps it (owner, 27 November 2026).
            DeleteFlow.requestFrom(ctx, byList) { picked = emptySet() }
        }
    }

    /** Move to… or Delete: asks "From which list?" only when the wishes are on more than one list. */
    fun start(action: String, ids: Set<Long>, all: List<Placed>) {
        val lists = all.filter { it.w.id in ids }.flatMap { it.listIds }.distinct()
        if (lists.size <= 1) act(action, ids, lists.firstOrNull()) else fromAsk = action to ids
    }

    /** Edit: the form opens on the wish's original. */
    fun edit(p: Placed) { focusOriginal(p); editing = Repo.myWishes.firstOrNull { it.id == p.w.id } }

    fun setPrivate(p: Placed, on: Boolean) { focusOriginal(p); Repo.myWishes.firstOrNull { it.id == p.w.id }?.let { PrivateFlow.set(ctx, it, on) } }

    fun setOwned(p: Placed, on: Boolean) {
        focusOriginal(p); Repo.myWishes.firstOrNull { it.id == p.w.id }?.takeIf { it.got != on }?.let { OwnedFlow.toggle(ctx, it) }
    }

    fun received(p: Placed) = ReceivedFlow.everywhere(ctx, p.w.id)

    /** ♥ on or off for every copy of the wish, on every list it is on (taking it off has Undo). */
    fun star(p: Placed, on: Boolean) = p.listIds.forEach { id ->
        Repo.updateMine(ctx, id) { ws -> ws.map { if (it.id == p.w.id) it.copy(star = on) else it } }
    }
}
