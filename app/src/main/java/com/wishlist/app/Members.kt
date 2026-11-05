package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

/*
 * Members of a gift list and a new owner (owner, 6 November 2026, A51; supabase-features.sql):
 *  - Every app user on a gift list is remembered online as a member (their profile id and name), once per list each
 *    time the app starts. People in the browser have no profile and are not listed.
 *  - When the person who made the list leaves it, they pick a new owner from the members ("Pick a new owner"). The
 *    list gets a new owner token at once (the old phone loses it); the picked member's phone takes it the next time it
 *    looks ("You're now the owner of “…”") and can then renew the link and delete the list. Nobody to pick: "Delete
 *    “…” for everyone?".
 */

data class Member(val id: String, val name: String)

object Members {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val done = HashSet<String>()

    /** Remembers you as a member of [l] and takes over ownership when it was handed to you (once per app start). */
    fun seen(c: Context, l: SharedList) {
        if (!done.add(l.code)) return
        scope.launch {
            val auth = Friends.auth(c) ?: return@launch
            try {
                Cloud.rpc("wl_sh_member", auth.put("p_key", l.key))
            } catch (e: Exception) {
                done.remove(l.code); return@launch
            }
            claim(c, l)
        }
    }

    /** Takes the ownership of [l] when it was handed to this phone. Returns true when it did. */
    suspend fun claim(c: Context, l: SharedList): Boolean {
        if (l.owner.isNotEmpty()) return false
        val auth = Friends.auth(c) ?: return false
        val t = try {
            Cloud.rpc("wl_sh_owner_claim", auth.put("p_key", l.key)).trim().removeSurrounding("\"")
        } catch (e: Exception) {
            return false
        }
        if (t.isEmpty() || t == "null") return false
        Shared.becameOwner(c, l.code, t)
        Notifications.add(c, "owner", "s:${l.code}", tr("You’re now the owner of “%s”", l.name))
        return true
    }

    /** The background check: every gift list you're on, for a handed-over ownership. */
    suspend fun claimAll(c: Context) {
        for (l in Shared.lists.toList()) claim(c, l)
    }

    /** You left [key]'s list: off its members. */
    suspend fun left(c: Context, key: String) {
        val auth = Friends.auth(c) ?: return
        try { Cloud.rpc("wl_sh_member_leave", auth.put("p_key", key)) } catch (e: Exception) {}
    }

    /** The members you can hand list [l] to (not you). Throws when offline or without the script. */
    suspend fun list(c: Context, l: SharedList): List<Member> {
        val me = Friends.auth(c)?.optString("p_id").orEmpty()
        val a = JSONArray(Cloud.rpc("wl_sh_members", org.json.JSONObject().put("p_code", l.code).put("p_owner", l.owner)))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Member(it.optString("id"), it.optString("name")) }
            .filter { it.id != me && it.id.isNotEmpty() }
    }

    /** Hands list [l] to member [to]; this phone is no longer its owner. */
    suspend fun handOver(c: Context, l: SharedList, to: String) {
        Cloud.rpc("wl_sh_handover", org.json.JSONObject().put("p_code", l.code).put("p_owner", l.owner).put("p_profile", to))
    }
}
