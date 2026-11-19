package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/*
 * Inviting Friends to a gift list (owner, 7 November 2026 evening; supabase-together.sql):
 *  - Everyone on a gift list can invite their Friends from its share screen ("Invite Friends"), next to the link.
 *  - The Friend gets an invitation to accept: a notification and a chat card (no card on Home, owner's choice). Both
 *    open the invitation: who invited, the list, its occasion, the givers and a few pictures, with Join / No thanks.
 *  - The list's edit key is never in the chat or the invitation: the server only gives it to the invited person when
 *    they press Join. "No thanks" tells whoever invited, quietly (their Notifications page, no buzz).
 *
 * News (the same script): the server keeps short news for each phone (an invitation, a "Still buying …?" question and
 * its answers) and wakes the phone with a push at once. [News.poll] fetches it, tells it, and deletes it online.
 */

data class GiftInviteInfo(
    val code: String, val name: String, val by: String, val givers: List<String>, val count: Int,
    val images: List<String>, val occ: Occasion?
)

object GiftInvites {
    private fun str(t: String) = t.trim().removeSurrounding("\"")

    /** What the invitation shows, or null when you aren't invited (any more). Throws when offline. */
    suspend fun info(c: Context, code: String): GiftInviteInfo? {
        val auth = Friends.auth(c) ?: return null
        val t = Cloud.rpc("wl_sh_inv_info", auth.put("p_code", code)).trim()
        if (t.isEmpty() || t == "null") return null
        val o = JSONObject(t)
        fun names(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        return GiftInviteInfo(
            o.optString("code"), o.optString("name"), o.optString("by"), names(o.optJSONArray("givers")),
            o.optInt("count"), names(o.optJSONArray("images")), Occasion.fromJson(o.optJSONObject("occ"))
        )
    }

    /** Join: the server gives the edit key now; the list is added to this phone. Null when the invitation is gone. */
    suspend fun accept(c: Context, code: String): SharedList? {
        val auth = Friends.auth(c) ?: return null
        val key = str(Cloud.rpc("wl_sh_inv_accept", auth.put("p_code", code)))
        if (key.isEmpty() || key == "null") return null
        Notifications.markRead(c, "gl_invite", "i:$code", 0L)
        return Shared.join(c, key, Shared.lastName)
    }

    suspend fun decline(c: Context, code: String) {
        val auth = Friends.auth(c) ?: return
        Cloud.rpc("wl_sh_inv_decline", auth.put("p_code", code))
    }

    /** Who of your Friends is on list [l] already, or invited: profile ids. Throws when offline. */
    suspend fun taken(c: Context, l: SharedList): Set<String> {
        val auth = Friends.auth(c) ?: return emptySet()
        val o = JSONObject(Cloud.rpc("wl_sh_invited", auth.put("p_key", l.key)))
        val out = HashSet<String>()
        for (k in listOf("members", "invited")) o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) out.add(a.optString(i)) }
        return out
    }

    /**
     * Invites Friends [ids] to list [l]: online (the notification) and a chat card (only the list's code and name, never
     * its key). Returns how many couldn't be invited.
     */
    suspend fun invite(c: Context, l: SharedList, ids: List<String>): Int {
        val name = l.name.ifBlank { Repo.listName }
        var failed = 0
        for (id in ids) {
            val auth = Friends.auth(c) ?: return ids.size
            try {
                Cloud.rpc("wl_sh_invite", auth.put("p_key", l.key).put("p_friend", id))
            } catch (e: Exception) {
                failed++
                continue
            }
            try {
                Chat.send(c, id, ChatContent(true, "giftlist", l.code, name))
            } catch (e: Exception) {
            }
        }
        return failed
    }

    /** News: you were invited. A notification (always, like group invitations) and a line on the Notifications page. */
    fun told(c: Context, code: String, name: String, by: String) {
        if (Shared.lists.any { it.code == code }) return
        val who = by.ifBlank { tr("Someone") }
        Notifications.add(c, "gl_invite", "i:$code", name, extra = who)
        Alerts.notifyGiftInvite(c, code, who, name)
    }
}

/** The server's news for this phone, told once (see the top of this file). */
object News {
    private val lock = Mutex()
    private var linked = false

    /** Fetches and tells the news. Quietly does nothing without a profile or connection. */
    suspend fun poll(c: Context) {
        if (Friends.auth(c) == null) return
        lock.withLock {
            // This phone's device id (used for reservations) belongs to this profile, so a question about a gift you
            // reserved reaches you with a push.
            if (!linked) try {
                Cloud.rpc("wl_dev_link", Friends.auth(c)!!.put("p_device", Repo.deviceId)); linked = true
            } catch (e: Exception) {
            }
            val a = JSONArray(Cloud.rpc("wl_news_get", Friends.auth(c)!!.put("p_device", Repo.deviceId)))
            var upto = 0L
            for (i in 0 until a.length()) {
                val n = a.getJSONObject(i)
                upto = maxOf(upto, n.optLong("id"))
                val code = n.optString("code")
                val d = n.optJSONObject("data") ?: JSONObject()
                try {
                    when (val kind = n.optString("kind")) {
                        "sh_invite" -> GiftInvites.told(c, code, d.optString("name"), d.optString("by"))
                        // Only on the Notifications page, no buzz (owner).
                        "sh_declined" -> Notifications.add(c, "gl_declined", "s:$code", d.optString("who").ifBlank { tr("Someone") }, extra = d.optString("name"))
                        // A year without opening the app: a month before the lists are removed online (S4.3, 2.186).
                        "cleanup" -> Alerts.notifyCleanup(c)
                        else -> if (kind.startsWith("ask")) Asks.told(c, kind, code, d)
                    }
                } catch (e: Exception) {
                }
            }
            if (upto > 0) Cloud.rpc("wl_news_done", Friends.auth(c)!!.put("p_device", Repo.deviceId).put("p_upto", upto))
        }
    }
}
