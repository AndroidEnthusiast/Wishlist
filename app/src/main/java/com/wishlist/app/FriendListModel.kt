package com.wishlist.app

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * A friend's list's state holder (the rebuild, owner 19 November 2026; MAP "How a screen is built"): the list as the
 * server gave it, your reservations on it, what is open (search, a category, the dialogs), and reserving. The screen
 * (FriendScreen in Friend.kt) only draws. [f]: the list; the owner never sees its reservations (Rule 4).
 */
@Stable
internal class FriendListState(private val ctx: Context, private val f: Friend, private val snack: SnackbarHostState, private val scope: CoroutineScope) {
    var remote by mutableStateOf<RemoteList?>(null)
    var res by mutableStateOf<Map<Long, Boolean>>(emptyMap())
    // "loading", "ok", "gone" (the list isn't shared any more) or "offline".
    var state by mutableStateOf("loading")
    // Wishes whose reservation is on its way to the server (owner, 7 November 2026): only their button waits; the label
    // changes at once and goes back if the server says no.
    var pending by mutableStateOf<Set<Long>>(emptySet())
    var cat by mutableStateOf("All")
    // Search and sort, as on your own lists (owner, 12 November 2026: the same tools on every list).
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var catMenu by mutableStateOf(false)
    // Goes up for "Try again": the list loads afresh.
    var tick by mutableStateOf(0)
    var confirmRemove by mutableStateOf(false)
    // A 2× wish asks "How many will you buy?" (2.128).
    var askFor by mutableStateOf<Wish?>(null)
    // "Is it still being bought?" about someone else's reservation (2.142), and "Reserve it now?" after "X is free".
    var askBought by mutableStateOf<Wish?>(null)
    var reserveNow by mutableStateOf<Wish?>(null)

    /** Your reservations on this list (the checks give pretend ones; the app asks the server). */
    internal var fetchRes: suspend () -> Map<Long, Boolean> = { Cloud.fetchReservations(f.code, Repo.deviceId) }

    fun stopSearch() { searching = false; query = "" }

    /**
     * Loads the list and your reservations ([fetch]: through a friendship or a group instead of the link). A list that
     * renamed itself is renamed on this phone too.
     */
    suspend fun refresh(fetch: (suspend () -> RemoteList?)?) {
        try {
            val r = if (fetch != null) fetch() else Cloud.fetchList(f.code)
            if (r == null) {
                state = "gone"
            } else {
                val m = fetchRes()
                remote = r
                // A tap still on its way keeps what it shows.
                res = if (pending.isEmpty()) m else m.toMutableMap().apply { pending.forEach { id -> res[id]?.let { put(id, it) } ?: remove(id) } }
                FriendWatch.check(ctx, f, r, m.filterValues { it }.keys, FriendWatch.movedFrom(ctx, f.code, r))
                state = "ok"
                if (fetch == null && r.name != f.name) Repo.renameFriend(ctx, f.code, r.name)
            }
        } catch (e: Exception) {
            if (remote == null) state = "offline"
        }
    }

    /** "How many will you buy?" answered ([n] = 0: none any more). */
    fun reserveCount(w: Wish, n: Int) {
        val before = res[w.id]
        res = if (n > 0) res + (w.id to true) else res - w.id
        pending = pending + w.id
        scope.launch {
            var msg: String? = null
            try {
                if (!Cloud.reserveCount(f.code, w.id, Repo.deviceId, n)) msg = tr("Someone else just reserved that one.")
                // Fixed in the rebuild (19 November 2026): a 2× wish you reserved was never remembered on this phone, so
                // it was missing from "Gifts I reserved" and no news came about it.
                else if (n > 0) { FriendWatch.reserved(ctx, f.code, w); Celebrate.reserved() }
                else FriendWatch.unreserved(ctx, f.code, w.id)
            } catch (e: Exception) {
                msg = tr("No connection. Try again.")
                res = if (before == null) res - w.id else res + (w.id to before)
            }
            pending = pending - w.id
            try { res = fetchRes() } catch (e: Exception) {}
            msg?.let { snack.showToast(it) }
        }
    }

    // Reserve shows "Reserved by you" and Unreserve at once (owner, 7 November 2026: it took too long, and every
    // Reserve button went grey); the server is asked in the background and a "no" puts it back with a message.
    fun toggle(w: Wish, reserve: Boolean) {
        if (w.qty > 1) { if (reserve) askFor = w else reserveCount(w, 0); return }
        if (w.id in pending) return
        val before = res[w.id]
        res = if (reserve) res + (w.id to true) else res - w.id
        pending = pending + w.id
        scope.launch {
            var msg: String? = null
            var failed = false
            try {
                if (reserve) {
                    if (!Cloud.reserve(f.code, w.id, Repo.deviceId)) { msg = tr("Someone else just reserved that one."); failed = true }
                    else { FriendWatch.reserved(ctx, f.code, w); Celebrate.reserved() }
                } else {
                    Cloud.unreserve(f.code, w.id, Repo.deviceId)
                    FriendWatch.unreserved(ctx, f.code, w.id)
                }
            } catch (e: Exception) {
                msg = tr("No connection. Try again.")
                failed = true
            }
            if (failed) res = if (before == null) res - w.id else res + (w.id to before)
            pending = pending - w.id
            try { res = fetchRes() } catch (e: Exception) {}
            msg?.let { snack.showToast(it) }
        }
    }

    /** The sort and the view are one choice for all lists (owner, 7 November 2026). */
    fun setSort(s: String) = Repo.setSort(ctx, s)
    fun setView(v: String) = Repo.setView(ctx, v)

    /** "Remove this list": it goes from this phone (your reservations stay). */
    fun removeList() { confirmRemove = false; Repo.removeFriend(ctx, f.code) }
}
