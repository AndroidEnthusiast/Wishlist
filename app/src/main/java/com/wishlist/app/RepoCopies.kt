package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One wish on several of your lists (a part of [Repo] since the data rebuild, owner 19 November 2026). */
internal object OwnCopies {
    /**
     * Wishes copied from one of your lists into another (list id to the ids copied into it). A copy keeps the wish's
     * id, so it is the same wish: when the original is removed, its copies go too (and anyone who reserved a copy
     * hears that it's gone, as with any removed wish). Removing a copy only removes that copy.
     */
    internal var copies: Map<String, Set<Long>> = emptyMap()
}

// ---- what you can do with it (callers keep writing Repo.…, as before the rebuild) ----

/** Whether wish [id] has copies on your other lists (or is a copy itself). */
fun Repo.hasCopies(id: Long): Boolean = OwnCopies.copies.values.any { id in it }

/** Your lists that hold wish [id] (the original and its copies), in the order of your lists. */
fun Repo.listsWith(c: Context, id: Long): List<String> = synchronized(this) {
    mineIds.filter { l -> (if (l == openMine) myWishes else MyLists.read(prefs(c), l).wishes).any { it.id == id } } }

/** The list that holds the original of wish [id] (not a copy), or the first one that has it. */
fun Repo.originalOf(c: Context, id: Long): String? = synchronized(this) { listsWith(c, id).let { ls -> ls.firstOrNull { OwnCopies.copies[it]?.contains(id) != true } ?: ls.firstOrNull() } }

/**
 * The copies of [w] on your other lists take its changes (owner, 12 November 2026: "every copy, everywhere"), all
 * but what belongs to each list: its category and its bin state.
 */
internal fun Repo.syncCopies(c: Context, w: Wish, from: String) {
    if (!hasCopies(w.id)) return
    for (l in mineIds) if (l != from) {
        val has = (if (l == openMine) myWishes else MyLists.read(prefs(c), l).wishes).any { it.id == w.id }
        if (has) updateMine(c, l) { ws -> ws.map { if (it.id == w.id) w.copy(category = it.category, bin = it.bin, binBy = it.binBy) else it } }
    }
}

/** Changes wish [id] on every list of yours that has it (All your wishes: Private, I have it, Top wish). */
fun Repo.changeEverywhere(c: Context, id: Long, change: (Wish) -> Wish): Unit = synchronized(this) {
    for (l in listsWith(c, id)) updateMine(c, l) { ws -> ws.map { if (it.id == id) change(it) else it } }
}

/**
 * "I have it" → Received gifts for a wish on one or more of your lists: the copies leave their lists, the original
 * goes to Received gifts (one entry), givers get their thank-you as with "What did you get?".
 */
fun Repo.receiveEverywhere(c: Context, id: Long): Unit = synchronized(this) {
    val orig = originalOf(c, id) ?: return
    for (l in listsWith(c, id)) if (l != orig) {
        updateMine(c, l) { ws -> ws.filter { it.id != id } }
        OwnCopies.copies = dropCopy(OwnCopies.copies, l, id)
    }
    persist(c)
    receive(c, orig, setOf(id))
}

internal fun Repo.dropCopy(m: Map<String, Set<Long>>, list: String, id: Long): Map<String, Set<Long>> {
    val left = m[list].orEmpty() - id
    return if (left.isEmpty()) m - list else m + (list to left)
}

/**
 * Copies wishes from your other lists into one of yours (at the top, skipping any it already has). They keep their
 * id: see [copies].
 */
fun Repo.copyInto(c: Context, listId: String, wishes: List<Wish>): Set<Long> = synchronized(this) {
    if (listId !in mineIds || wishes.isEmpty()) return emptySet()
    var added = emptySet<Long>()
    updateMine(c, listId) { ws ->
        val fresh = wishes.filter { w -> ws.none { it.id == w.id } }.distinctBy { it.id }
            .map { it.copy(bin = 0L, binBy = "") }
        added = fresh.map { it.id }.toSet()
        fresh + ws
    }
    OwnCopies.copies = OwnCopies.copies + (listId to (OwnCopies.copies[listId].orEmpty() + added))
    persist(c)
    OwnLists.mineVersion++
    return added
}

/** Undo of "Copy" (Move or copy…, owner 16 November 2026): takes the copies [ids] off [listId] again. */
fun Repo.uncopy(c: Context, listId: String, ids: Set<Long>) {
    if (listId !in mineIds || ids.isEmpty()) return
    updateMine(c, listId) { ws -> ws.filter { it.id !in ids } }
    OwnCopies.copies = OwnCopies.copies + (listId to (OwnCopies.copies[listId].orEmpty() - ids))
    persist(c)
    OwnLists.mineVersion++
}

/**
 * Moves wishes [ids] from your list [fromId] to your list [toId] (owner, 7 November 2026), at the top, keeping their
 * id (and so their price history). A wish that was a copy stays a copy; one the other list already has just leaves
 * [fromId]. The reservations moved online first (MoveFlow). Returns the ids that moved.
 */
fun Repo.moveMine(c: Context, fromId: String, toId: String, ids: Set<Long>): Set<Long> = synchronized(this) {
    if (fromId == toId || fromId !in mineIds || toId !in mineIds || ids.isEmpty()) return emptySet()
    val moving = mine(c, fromId).wishes.filter { it.id in ids }
    if (moving.isEmpty()) return emptySet()
    val moved = moving.map { it.id }.toSet()
    var had = emptySet<Long>()
    updateMine(c, toId) { ws ->
        had = ws.filter { it.id in moved }.map { it.id }.toSet()
        moving.filter { it.id !in had } + ws
    }
    updateMine(c, fromId) { ws -> ws.filter { it.id !in moved } }
    for (id in moved) {
        val wasCopy = OwnCopies.copies[fromId]?.contains(id) == true
        OwnCopies.copies = dropCopy(OwnCopies.copies, fromId, id)
        if (wasCopy && id !in had) OwnCopies.copies = OwnCopies.copies + (toId to (OwnCopies.copies[toId].orEmpty() + id))
        // The original arrived where a copy of it was: that one is the original now.
        if (!wasCopy && id in had) OwnCopies.copies = dropCopy(OwnCopies.copies, toId, id)
    }
    persist(c)
    OwnLists.mineVersion++
    return moved
}
