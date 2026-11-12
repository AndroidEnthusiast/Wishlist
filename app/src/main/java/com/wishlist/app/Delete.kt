package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Deleting a wish. Every Delete button calls [request]: the wish goes to the bin at once (Bin.kt, 30 days) and a
 * short message with Undo takes it out again. No question first (owner, 2.95: the bin and Undo are the safety net;
 * the "Instant delete wishes" setting is gone).
 */
object DeleteFlow {
    /** The wish the person is being asked about, or null. */
    var asking by mutableStateOf<Wish?>(null)
        private set

    /** The latest delete that can still be undone from the message. */
    var undoable by mutableStateOf<Trashed?>(null)
        private set

    /** Several wishes deleted at once (selected after a long press, owner 7 November 2026), for one Undo. */
    var undoableMany by mutableStateOf<List<Trashed>?>(null)
        private set

    fun requestMany(c: Context, ws: List<Wish>) {
        val done = ws.mapNotNull { Repo.trash(c, it.id) }
        if (done.isEmpty()) return
        undoable = null
        undoableMany = done
    }

    /**
     * Several wishes from All your wishes (owner, 12 November 2026): each from the list named for it, with one Undo.
     * Deleting a wish from the list that has its original takes its copies along (as always).
     */
    fun requestFrom(c: Context, byList: Map<String, Set<Long>>) {
        Shared.close()
        val done = byList.flatMap { (l, ids) -> Repo.openMine(c, l); ids.mapNotNull { Repo.trash(c, it) } }
        if (done.isEmpty()) return
        undoable = null
        undoableMany = done
    }

    /** Puts the wishes deleted together back where they stood (the last one deleted first). */
    fun undoMany(c: Context) {
        val l = undoableMany ?: return
        undoableMany = null
        l.asReversed().forEach { Repo.untrash(c, it) }
    }

    fun finishMany(l: List<Trashed>) {
        if (undoableMany === l) undoableMany = null
    }

    fun request(c: Context, w: Wish) {
        delete(c, w)
    }

    fun cancel() {
        asking = null
    }

    fun confirm(c: Context) {
        val w = asking ?: return
        asking = null
        delete(c, w)
    }

    private fun delete(c: Context, w: Wish) {
        // Only the latest delete can be undone from the message; earlier ones can still be restored from the bin.
        undoable = Repo.trash(c, w.id)
    }

    /** Takes the last deleted wish out of the bin and puts it back where it was. */
    fun undo(c: Context) {
        val t = undoable ?: return
        undoable = null
        Repo.untrash(c, t)
    }

    /** The message for [t] is gone: it can only be restored from the bin now. */
    fun finish(t: Trashed) {
        if (undoable === t) undoable = null
    }
}
