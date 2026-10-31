package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Deleting a wish, the careful way. Every Delete button calls [request]:
 *  - normally the person is asked "Delete this wish?" first;
 *  - with "Instant delete" on in Settings it is deleted at once;
 *  - either way a message with Undo shows for a few seconds. Only when that time is over (or a next wish is
 *    deleted, or the app is left) does the wish go to the bin (Bin.kt), where it stays for 10 days.
 */
object DeleteFlow {
    /** The wish the person is being asked about, or null. */
    var asking by mutableStateOf<Wish?>(null)
        private set

    /** The latest delete that can still be undone. The screen shows "Deleted ... Undo" for it. */
    var undoable by mutableStateOf<Trashed?>(null)
        private set

    fun request(c: Context, w: Wish) {
        if (Repo.instantDelete) delete(c, w) else asking = w
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
        // A delete that could still be undone becomes final: only the latest one can be undone.
        finishNow(c)
        undoable = Repo.trash(c, w.id)
    }

    /** Brings the last deleted wish back, where it was. */
    fun undo(c: Context) {
        val t = undoable ?: return
        undoable = null
        Repo.untrash(c, t)
    }

    /** The undo time of [t] is over. */
    fun finish(c: Context, t: Trashed) {
        if (undoable !== t) return
        undoable = null
        Repo.finishTrash(c, t)
    }

    /** Makes a delete that is still waiting final (the app is left, or another wish is deleted). */
    fun finishNow(c: Context) {
        val t = undoable ?: return
        undoable = null
        Repo.finishTrash(c, t)
    }
}
