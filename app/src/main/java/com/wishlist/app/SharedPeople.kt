package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * The other people on a shared (collab) wishlist, for "Chat about this wish" (owner, 21 November 2026;
 * docs/decisions/wish-cards.md, last section). The server (wl_sh_people, supabase-features.sql) lists everyone whose app
 * joined the list, with whether they are your friend; chats stay between friends. The last answer is kept on this
 * phone per list, so the picker opens offline too. No screens here: plain JUnit checks (ChatPeopleTest).
 */

/** Someone else on a shared list: [friend] is "yes", "asked" (a friend request between you waits) or "no". */
data class ListPerson(val id: String, val name: String, val friend: String) {
    val isFriend get() = friend == "yes"
}

object SharedPeople {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun cacheKey(code: String) = "shp_$code"

    /** The server's answer as people (ids and names come from other phones: only ever shown as plain text). */
    internal fun parse(json: String): List<ListPerson> {
        val a = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .map { ListPerson(it.optString("id"), it.optString("name").trim(), it.optString("friend", "no")) }
            .filter { it.id.isNotEmpty() }
    }

    internal fun toJson(people: List<ListPerson>): String = JSONArray().apply {
        people.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("friend", it.friend)) }
    }.toString()

    /**
     * The friend marks brought up to date with this phone's own friends list ([friendIds]): someone who became your
     * friend since the last answer can be picked at once. Friends first, then the rest, each in the list's order.
     */
    internal fun withFriends(people: List<ListPerson>, friendIds: Set<String>): List<ListPerson> =
        people.map { if (it.id in friendIds && !it.isFriend) it.copy(friend = "yes") else it }.sortedBy { if (it.isFriend) 0 else 1 }

    /**
     * Straight to the chat, without the picker (owner, 21 November 2026): only when there is exactly one other person
     * on the list and they are your friend. Otherwise null: the picker shows.
     */
    internal fun direct(people: List<ListPerson>): String? = people.singleOrNull()?.takeIf { it.isFriend }?.id

    /** The last answer for list [code] kept on this phone, or null when never asked. */
    fun cached(c: Context, code: String): List<ListPerson>? = prefs(c).getString(cacheKey(code), null)?.let { parse(it) }

    /** Asks the server who is on [l] and keeps the answer. Throws when offline or before the script was run. */
    suspend fun fetch(c: Context, l: SharedList): List<ListPerson> {
        val auth = Friends.auth(c) ?: throw IllegalStateException("no profile")
        val people = parse(Cloud.rpc("wl_sh_people", auth.put("p_key", l.key)))
        prefs(c).edit().putString(cacheKey(l.code), toJson(people)).apply()
        return people
    }

    /** "Add friend" for [id] on list [l]: the normal friend request. Returns the server's status ("sent", "friends", …). */
    suspend fun befriend(c: Context, l: SharedList, id: String): String {
        val auth = Friends.auth(c) ?: throw IllegalStateException("no profile")
        val o = JSONObject(Cloud.rpc("wl_sh_befriend", auth.put("p_key", l.key).put("p_other", id)))
        return o.optString("status")
    }
}
