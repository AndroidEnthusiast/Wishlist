package com.wishlist.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * Reservations on the open shared wishlist: the givers reserve what they will buy, and everyone on the list sees it
 * (Rule 4). A part of [Shared] since the data rebuild (owner, 19 November 2026); callers keep writing Shared.….
 */
internal object SharedReservations {
    /**
     * Reservations on the open list: wish id -> true (reserved on this phone) or false (by someone else).
     * Everyone on a shared list sees them: the list is only shared between the givers, so nobody buys a gift twice.
     */
    var reserved by mutableStateOf(mapOf<Long, Boolean>())
        internal set
    @Volatile
    internal var resBusy = false

    /** A "2×" wish waiting for "How many will you buy?" (2.128); the dialog is on the list screen. */
    var askCount by mutableStateOf<Wish?>(null)
}


internal suspend fun Shared.loadReservations(code: String) {
    if (SharedReservations.resBusy) return
    val m = try {
        Cloud.fetchReservations(code, Repo.deviceId)
    } catch (e: Exception) {
        return
    }
    if (active == code && !SharedReservations.resBusy) SharedReservations.reserved = m
}

/** Reserves [n] of [w] on the open list (0 takes yours away). */
fun Shared.reserveCount(w: Wish, n: Int) {
    val l = current ?: return
    if (SharedReservations.resBusy) return
    val before = reserved
    SharedReservations.reserved = if (n > 0) before + (w.id to true) else before - w.id
    SharedReservations.resBusy = true
    scope.launch {
        try {
            if (!Cloud.reserveCount(l.code, w.id, Repo.deviceId, n)) notice = tr("Someone else just reserved that one.")
            else if (n > 0) Celebrate.reserved()
        } catch (e: Exception) {
            if (active == l.code) SharedReservations.reserved = before
            notice = tr("Couldn’t save that. Check your connection and try again.")
        } finally {
            SharedReservations.resBusy = false
        }
        loadReservations(l.code)
    }
}

/** Reserves a wish of the open list ([on] = true) or takes this phone's reservation away again. */
fun Shared.reserve(w: Wish, on: Boolean) {
    val l = current ?: return
    if (SharedReservations.resBusy) return
    // A wish wanted more than once asks how many first.
    if (on && w.qty > 1) { SharedReservations.askCount = w; return }
    if (!on && w.qty > 1) { reserveCount(w, 0); return }
    val before = reserved
    SharedReservations.reserved = if (on) before + (w.id to true) else before - w.id
    SharedReservations.resBusy = true
    scope.launch {
        try {
            if (on) {
                if (!Cloud.reserve(l.code, w.id, Repo.deviceId)) notice = tr("Someone else just reserved that one.")
                else Celebrate.reserved()
            } else {
                Cloud.unreserve(l.code, w.id, Repo.deviceId)
            }
        } catch (e: Exception) {
            if (active == l.code) SharedReservations.reserved = before
            notice = tr("Couldn’t save that. Check your connection and try again.")
        } finally {
            SharedReservations.resBusy = false
        }
        loadReservations(l.code)
    }
}
