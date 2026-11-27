package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Deleting a wish. Every Delete button calls [request] (one wish) or [requestMany] / [requestFrom] (several): "Delete
 * wish?" asks first, with the wish's photo (DeleteUi.kt), unless One-tap delete is on (owner, 27 November 2026; the
 * question had been gone since 2.95). Then the wish goes to the bin at once (Bin.kt, 30 days) and a short message with
 * Undo takes it out again. Ticking "Turn on one-tap delete" in the question turns the App settings switch on, only
 * when Delete is tapped (Cancel changes nothing), and that one message says where to switch it off again.
 */
object DeleteFlow {
    /** The question that is open: the wishes it is about (photos and names) and what Delete does. */
    class Ask(val wishes: List<Wish>, internal val run: (Context) -> Unit)

    /** The "Delete wish?" question that is open, or null. */
    var asking by mutableStateOf<Ask?>(null)
        private set

    /** The latest delete that can still be undone from the message. */
    var undoable by mutableStateOf<Trashed?>(null)
        private set

    /** Several wishes deleted at once (selected after a long press, owner 7 November 2026), for one Undo. */
    var undoableMany by mutableStateOf<List<Trashed>?>(null)
        private set

    /** True for the delete that just turned One-tap delete on: its message says where to switch it off. */
    var justTurnedOn by mutableStateOf(false)
        private set

    /** One-tap delete (App settings): no question first. */
    fun oneTap(c: Context): Boolean { Show.load(c); return Show[ONE_TAP] }

    /** Turns One-tap delete on or off (App settings, and the question's tickbox). */
    fun setOneTap(c: Context, on: Boolean) = Show.set(c, ONE_TAP, on)

    /** Asks first, unless One-tap delete is on; [run] does the delete. */
    private fun ask(c: Context, wishes: List<Wish>, run: (Context) -> Unit) {
        if (wishes.isEmpty()) return
        if (oneTap(c)) { justTurnedOn = false; run(c) } else asking = Ask(wishes, run)
    }

    /** Several wishes of the open list ([done]: after the delete, e.g. the selection ends; not on Cancel). */
    fun requestMany(c: Context, ws: List<Wish>, done: () -> Unit = {}) = ask(c, ws) { ctx ->
        done()
        val gone = ws.mapNotNull { Repo.trash(ctx, it.id) }
        if (gone.isNotEmpty()) { undoable = null; undoableMany = gone }
    }

    /**
     * Several wishes from All your wishes (owner, 12 November 2026): each from the list named for it, with one Undo.
     * Deleting a wish from the list that has its original takes its copies along (as always).
     */
    fun requestFrom(c: Context, byList: Map<String, Set<Long>>, done: () -> Unit = {}) {
        val ids = byList.values.flatten().toSet()
        val ws = Repo.allMine(c).flatMap { it.wishes }.filter { it.id in ids }.distinctBy { it.id }
        ask(c, ws) { ctx ->
            done()
            Shared.close()
            val gone = byList.flatMap { (l, ids) -> Repo.openMine(ctx, l); ids.mapNotNull { Repo.trash(ctx, it) } }
            if (gone.isNotEmpty()) { undoable = null; undoableMany = gone }
        }
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

    fun request(c: Context, w: Wish) = ask(c, listOf(w)) { delete(it, w) }

    fun cancel() {
        asking = null
    }

    /** Delete in the question; [oneTapFromNowOn]: its tickbox "Turn on one-tap delete" was ticked. */
    fun confirm(c: Context, oneTapFromNowOn: Boolean = false) {
        val a = asking ?: return
        asking = null
        if (oneTapFromNowOn) setOneTap(c, true)
        justTurnedOn = oneTapFromNowOn
        a.run(c)
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

    /** The message about One-tap delete has been shown (once). */
    fun toldOneTap() { justTurnedOn = false }

    /** Show's key for One-tap delete (off by default; the older "instant_delete" isn't read, so the question returns). */
    const val ONE_TAP = "one_tap_delete"
}
