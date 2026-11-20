package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The bin of your own lists, Undo and Received gifts (a part of [Repo] since the data rebuild, owner 19 November 2026).
 * See also Bin.kt (how long things stay) and BinUi.kt (the screen).
 */
internal object OwnBin {
    /** The bin of My wishlist: deleted wishes, kept for [BIN_DAYS] days (newest first). Not on the list and never price-checked. */
    var myBin by mutableStateOf(listOf<Wish>())
        internal set
    /** The copies the last move to the bin took along, so Undo can put them back. */
    internal var cascades: Map<Long, List<String>> = emptyMap()
    /** Names of deleted lists whose wishes may still be in the bin ("gone_<time>" to name). */
    internal var goneNames: Map<String, String> = emptyMap()
    /** Which of your lists a wish in the bin came from (wish id to list id); missing means "main". */
    internal var binOwner: Map<Long, String> = emptyMap()
}

// ---- what you can do with it (callers keep writing Repo.…, as before the rebuild) ----


/**
 * My wishlist as it goes online for friends. The bin goes along too (marked "bin", so friends don't see it), so a
 * reservation on it stays when it is restored. Private wishes never go online.
 */
fun Repo.syncWishes(): List<Wish> = (myWishes + myBin.filter { ownerOf(it.id) == openMine }).filter { !it.priv }

/** The list a wish in the bin came from (the original list when unknown or deleted). */
fun Repo.ownerOf(wishId: Long): String = OwnBin.binOwner[wishId]?.takeIf { it in mineIds } ?: MAIN_LIST

/** The name of the deleted list a wish in the bin came from, or null when its list still exists. */
fun Repo.deletedListOf(wishId: Long): String? {
    val o = OwnBin.binOwner[wishId] ?: return null
    return if (o in mineIds) null else OwnBin.goneNames[o] ?: ""
}

/** The name to show for a wish in the bin: its list, or the deleted list it came from. */
fun Repo.binListName(wishId: Long): String = deletedListOf(wishId)?.ifBlank { null } ?: mineName(ownerOf(wishId))

/** Restores a wish from the bin to one of your lists (for a wish whose own list was deleted). */
fun Repo.restoreMineTo(c: Context, wishId: Long, listId: String): Boolean = synchronized(this) {
    val w = myBin.firstOrNull { it.id == wishId } ?: return false
    if (listId !in mineIds) return false
    OwnBin.myBin = myBin.filter { it.id != wishId }
    OwnBin.binOwner = OwnBin.binOwner - wishId
    updateMine(c, listId) { l -> if (l.any { it.id == wishId }) l else listOf(w.copy(bin = 0L, binBy = "")) + l }
    persist(c)
    OwnLists.mineVersion++
    return true
}

/** Wishes of your list [id] that are in the bin. */
fun Repo.binnedOf(id: String): List<Wish> = myBin.filter { ownerOf(it.id) == id }

/** Moves a wish of the open list to the bin at once. Null if the wish isn't there. */
fun Repo.trash(c: Context, id: Long): Trashed? = synchronized(this) {
    if (Shared.active != null) return Shared.trash(c, id)
    val i = myWishes.indexOfFirst { it.id == id }
    if (i < 0) return null
    val w = myWishes[i].copy(bin = System.currentTimeMillis())
    OwnLists.myWishes = myWishes.filter { it.id != id }
    OwnBin.myBin = listOf(w) + myBin.filter { it.id != id }
    OwnBin.binOwner = OwnBin.binOwner + (id to openMine)
    val wasCopy = OwnCopies.copies[openMine]?.contains(id) == true
    OwnCopies.copies = dropCopy(OwnCopies.copies, openMine, id)
    // Removing an original also removes its copies on your other lists (removing a copy leaves the rest).
    OwnBin.cascades = OwnBin.cascades - id
    if (!wasCopy) {
        val others = OwnCopies.copies.filter { (l, ids) -> l != openMine && id in ids && l in mineIds }.keys.toList()
        for (l in others) {
            updateMine(c, l) { ws -> ws.filter { it.id != id } }
            OwnCopies.copies = dropCopy(OwnCopies.copies, l, id)
        }
        if (others.isNotEmpty()) OwnBin.cascades = OwnBin.cascades + (id to others)
    }
    commit(c)
    return Trashed(w, i, null)
}

/** Undo: the wish comes out of the bin and back where it was. */
fun Repo.untrash(c: Context, t: Trashed): Unit = synchronized(this) {
    if (t.listCode != null) {
        Shared.untrash(c, t)
        return
    }
    val owner = ownerOf(t.wish.id)
    OwnBin.myBin = myBin.filter { it.id != t.wish.id }
    OwnBin.binOwner = OwnBin.binOwner - t.wish.id
    // Undo also brings back the copies the removal took along.
    OwnBin.cascades[t.wish.id]?.let { lists ->
        val wid = t.wish.id
        val back = t.wish.copy(bin = 0L, binBy = "")
        for (l in lists) {
            if (l !in mineIds) continue
            updateMine(c, l) { ws -> if (ws.any { it.id == wid }) ws else listOf(back) + ws }
            OwnCopies.copies = OwnCopies.copies + (l to (OwnCopies.copies[l].orEmpty() + wid))
        }
        OwnBin.cascades = OwnBin.cascades - wid
    }
    if (owner != openMine) {
        // You opened another of your lists before tapping Undo: the wish goes back to its own list.
        updateMine(c, owner) { l -> if (l.any { it.id == t.wish.id }) l else listOf(t.wish.copy(bin = 0L, binBy = "")) + l }
        persist(c)
        return
    }
    if (myWishes.none { it.id == t.wish.id }) {
        OwnLists.myWishes = myWishes.toMutableList().also { it.add(t.index.coerceIn(0, it.size), t.wish.copy(bin = 0L, binBy = "")) }
    }
    commit(c)
}

/**
 * "What did you get?" (owner, 2.128): wishes [ids] of your list [listId] were received. They leave the list (also
 * for friends) and go to "Received gifts": kept in the bin with "recv" (never cleaned up after 30 days), marked as
 * owned, and online with "bin" and "recv", so givers' phones say thank you.
 */
fun Repo.receive(c: Context, listId: String, ids: Set<Long>): Unit = synchronized(this) {
    if (ids.isEmpty() || listId !in mineIds) return
    val now = System.currentTimeMillis()
    val src = if (listId == openMine) myWishes else MyLists.read(prefs(c), listId).wishes
    val got = src.filter { it.id in ids }.map { it.copy(bin = now, recv = now, got = true) }
    if (got.isEmpty()) return
    OwnBin.myBin = got + myBin.filter { b -> got.none { it.id == b.id } }
    OwnBin.binOwner = OwnBin.binOwner + got.associate { it.id to listId }
    if (listId == openMine) {
        OwnLists.myWishes = myWishes.filter { it.id !in ids }
        commit(c)
    } else {
        updateMine(c, listId) { ws -> ws.filter { it.id !in ids } }
        persist(c)
        mine(c, listId).takeIf { it.code != null }?.let { Sync.pushStored(c, it, binnedOf(listId)) }
    }
}

/** A received gift goes back on its list (from "Received gifts"), as it was before. */
fun Repo.unreceive(c: Context, id: Long): Boolean = synchronized(this) {
    OwnBin.myBin = myBin.map { if (it.id == id) it.copy(recv = 0L, got = false) else it }
    return restoreMine(c, id)
}

/** Takes a wish out of the bin and puts it back at the top of My wishlist. */
fun Repo.restoreMine(c: Context, id: Long): Boolean = synchronized(this) {
    val w = myBin.firstOrNull { it.id == id } ?: return false
    val owner = ownerOf(id)
    OwnBin.myBin = myBin.filter { it.id != id }
    OwnBin.binOwner = OwnBin.binOwner - id
    if (owner != openMine) {
        updateMine(c, owner) { l -> if (l.any { it.id == id }) l else listOf(w.copy(bin = 0L, binBy = "")) + l }
        persist(c)
        return true
    }
    if (myWishes.none { it.id == id }) OwnLists.myWishes = listOf(w.copy(bin = 0L, binBy = "")) + myWishes
    commit(c)
    return true
}

/** Deletes wishes from the bin for good, with their pictures and price history. */
fun Repo.dropFromBin(c: Context, ids: Set<Long>): Unit = synchronized(this) {
    val gone = myBin.filter { it.id in ids }
    if (gone.isEmpty()) return
    OwnBin.myBin = myBin.filter { it.id !in ids }
    OwnBin.binOwner = OwnBin.binOwner - ids
    commit(c)
    // A copy on another list may still use the same picture and price history.
    val inUse = allMine(c).flatMap { it.wishes }
    gone.forEach { g ->
        if (inUse.none { it.image == g.image } && myBin.none { it.image == g.image }) ImageStore.delete(c, g.image)
        if (inUse.none { it.id == g.id }) History.remove(c, g.id)
    }
    // Their price notifications go too: tapping one would lead nowhere (owner, 20 November 2026).
    NoteCheck.forgetGone(c)
}
