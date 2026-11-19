package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The state holders of gift list invitations (the rebuild, owner 19 November 2026; MAP "How a screen is built"):
 * [InviteFriendsState] for "Invite Friends" on a gift list and [GiftInviteState] for an invitation you got. The
 * dialogs (GiftInviteViews.kt) only draw; the server calls stay in GiftInvites (GiftInvites.kt).
 */

/** Invite Friends to gift list [l]: who is on it or invited already, who you picked, and sending. */
@Stable
internal class InviteFriendsState(private val ctx: Context, private val l: SharedList, private val scope: CoroutineScope) {
    // Friends on the list or invited already (null while loading or when it couldn't load).
    var taken by mutableStateOf<Set<String>?>(null)
    var err by mutableStateOf("")
    var picked by mutableStateOf(setOf<String>())
    var busy by mutableStateOf(false)
    // Goes up for "Try again".
    var attempt by mutableStateOf(0)

    fun toggle(id: String) { picked = if (id in picked) picked - id else picked + id }

    suspend fun load() {
        err = ""
        taken = try {
            GiftInvites.taken(ctx, l)
        } catch (e: Exception) {
            err = if ((e.message ?: "").contains("wl_sh_invited")) tr("Inviting Friends needs an update in Supabase first.")
            else tr("Couldn’t reach the server. Check your connection.")
            null
        }
    }

    /** Sends the invitations; [onSent] when at least one went (the dialog closes). */
    fun send(onSent: () -> Unit) {
        busy = true
        scope.launch {
            val n = picked.size
            val failed = try { GiftInvites.invite(ctx, l, picked.toList()) } catch (e: Exception) { n }
            busy = false
            toast(ctx, inviteResult(n, failed))
            if (failed < n) onSent()
        }
    }
}

/** The message after sending [n] invitations of which [failed] didn't go. */
internal fun inviteResult(n: Int, failed: Int): String = when {
    failed == n -> tr("Couldn’t reach the server. Check your connection.")
    failed > 0 -> tr("%d invitations couldn’t be sent. Try again.", failed)
    n == 1 -> tr("Invitation sent")
    else -> tr("%d invitations sent", n)
}

/** An invitation to gift list [code]: what it says, Join (with confetti) and No thanks. */
@Stable
internal class GiftInviteState(private val ctx: Context, private val code: String, private val scope: CoroutineScope) {
    var info by mutableStateOf<GiftInviteInfo?>(null)
    // "loading", "ok", "gone" (no longer open), "missing" (the server needs an update) or "offline".
    var state by mutableStateOf("loading")
    var attempt by mutableStateOf(0)
    var busy by mutableStateOf(false)
    var err by mutableStateOf("")
    // Goes up when you joined: the confetti.
    var party by mutableStateOf(0)

    suspend fun load() {
        state = "loading"
        state = try {
            if (Friends.auth(ctx) == null) "gone" else {
                info = GiftInvites.info(ctx, code)
                if (info == null) "gone" else "ok"
            }
        } catch (e: Exception) {
            if ((e.message ?: "").contains("wl_sh_inv_info")) "missing" else "offline"
        }
    }

    /** Join: confetti, then the list opens ([onJoined] gets its code). */
    fun join(onJoined: (String) -> Unit) {
        busy = true; err = ""
        scope.launch {
            try {
                val l = GiftInvites.accept(ctx, code)
                if (l == null) { busy = false; state = "gone"; return@launch }
                party++
                delay(1300)
                onJoined(l.code)
            } catch (e: Exception) {
                busy = false
                err = tr("Couldn’t join. Check your connection.")
            }
        }
    }

    /** No thanks: the invitation goes, also from the Notifications page. */
    fun decline(onClose: () -> Unit) {
        busy = true
        scope.launch {
            try {
                GiftInvites.decline(ctx, code)
                Notifications.markRead(ctx, "gl_invite", "i:$code", 0L)
                onClose()
            } catch (e: Exception) {
                busy = false
                err = tr("Couldn’t reach the server. Check your connection.")
            }
        }
    }
}
