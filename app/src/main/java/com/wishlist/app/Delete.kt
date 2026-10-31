package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Deleting a wish, the careful way. Every Delete button calls [request]:
 *  - normally the person is asked "Delete this wish?" first;
 *  - with "Instant delete" on in Settings it is deleted at once;
 *  - either way the wish goes to the bin at once (Bin.kt, 10 days), and a short message with Undo takes it out again.
 */
object DeleteFlow {
    /** The wish the person is being asked about, or null. */
    var asking by mutableStateOf<Wish?>(null)
        private set

    /** The latest delete that can still be undone from the message. */
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
