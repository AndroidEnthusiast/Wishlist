package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Your own lists go online in small saves (ROADMAP S3.1, owner 8 November 2026).
 *
 * Before 2.173 every change sent the whole list (`wl_sync_list` deletes and rewrites every wish online). Now the phone
 * remembers a fingerprint of every wish as the server has it ([SentState], its own small store "sent", never in an
 * Export) and sends only the wishes that are new or changed and the ids of the ones that are gone, in one call
 * (`wl_mine_put`, supabase-sync.sql). Nothing changed: no call at all.
 *
 * A save that fails (offline, server down) keeps your change on the phone (owner's choice: no undo, no message): the
 * fingerprints stay as they were, so the next try sends everything still missing: at the next change, at the next
 * start ([Sync.catchUp]) and at the background check.
 *
 * The whole list is still sent the old way: the first time (no fingerprints yet), once a week as a repair (in case
 * the online copy changed some other way), and when the server doesn't have `wl_mine_put` yet (the script not run).
 */
data class SentState(
    /** Wish id (0 = the note item) → fingerprint of the wish as sent. */
    val items: Map<Long, String>,
    val name: String,
    val cats: String,
    /** The profile name sent with `wl_set_owner` ("Owned by …"), so it is sent only when it changed. */
    val owner: String = "",
    /** When the whole list was last sent. */
    val fullAt: Long = 0L
) {
    fun toJson(): String {
        val it = JSONObject()
        for ((k, v) in items) it.put(k.toString(), v)
        return JSONObject().put("items", it).put("name", name).put("cats", cats).put("owner", owner).put("fullAt", fullAt)
            .toString()
    }

    companion object {
        fun fromJson(s: String?): SentState? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                val it = o.getJSONObject("items")
                val m = HashMap<Long, String>()
                for (k in it.keys()) m[k.toLong()] = it.getString(k)
                SentState(m, o.optString("name"), o.optString("cats"), o.optString("owner"), o.optLong("fullAt"))
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** What to send to make the online copy equal to the phone's: pure logic, checked in MineSyncTest. */
object MineDiff {
    data class Diff(val put: List<JSONObject>, val del: List<Long>, val name: String?, val cats: List<String>?) {
        val empty get() = put.isEmpty() && del.isEmpty() && name == null && cats == null
    }

    fun fingerprint(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return d.take(10).joinToString("") { "%02x".format(it) }
    }

    fun catsText(cats: List<String>): String = JSONArray(cats).toString()

    private fun byId(items: JSONArray): LinkedHashMap<Long, JSONObject> {
        val m = LinkedHashMap<Long, JSONObject>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            if (!o.has("id")) continue
            m[o.getLong("id")] = o
        }
        return m
    }

    fun diff(sent: SentState, items: JSONArray, name: String, cats: List<String>): Diff {
        val now = byId(items)
        val put = now.filter { (id, o) -> sent.items[id] != fingerprint(o.toString()) }.values.toList()
        val del = sent.items.keys.filter { it !in now }.sorted()
        val catsNow = catsText(cats)
        return Diff(put, del, name.takeIf { it != sent.name }, cats.takeIf { catsNow != sent.cats })
    }

    /** The state once the server has exactly [items], [name] and [cats]. */
    fun state(items: JSONArray, name: String, cats: List<String>, owner: String, fullAt: Long): SentState =
        SentState(byId(items).mapValues { fingerprint(it.value.toString()) }, name, catsText(cats), owner, fullAt)
}

object MineSync {
    const val WEEK = 7L * 24 * 3_600_000

    /** The server calls (a seam for the app's checks, which never reach the real server). */
    interface Server {
        suspend fun full(code: String, token: String, name: String, cats: List<String>, items: JSONArray)
        suspend fun put(code: String, token: String, name: String?, cats: List<String>?, items: List<JSONObject>, del: List<Long>)
        suspend fun owner(code: String, token: String, name: String)
    }

    internal var server: Server = object : Server {
        override suspend fun full(code: String, token: String, name: String, cats: List<String>, items: JSONArray) =
            Cloud.syncList(code, token, name, cats, items)
        override suspend fun put(code: String, token: String, name: String?, cats: List<String>?, items: List<JSONObject>, del: List<Long>) =
            Cloud.minePut(code, token, name, cats, items, del)
        override suspend fun owner(code: String, token: String, name: String) = Cloud.setOwner(code, token, name)
    }
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /** False after the server said it doesn't know `wl_mine_put` (supabase-sync.sql not run yet): whole lists until the next start. */
    @Volatile private var putKnown = true

    /** For the app's checks: a fresh start. */
    internal fun reset() { putKnown = true }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("sent", Context.MODE_PRIVATE)
    fun load(c: Context, code: String): SentState? = SentState.fromJson(prefs(c).getString(code, null))
    private fun save(c: Context, code: String, s: SentState) { prefs(c).edit().putString(code, s.toJson()).commit() }
    /** The list went offline (or got a new code): its fingerprints are forgotten. */
    fun forget(c: Context, code: String) { prefs(c).edit().remove(code).apply() }

    /** True when the server says the function doesn't exist (PostgREST answers 404, PGRST202). */
    internal fun missingFunction(e: Exception): Boolean {
        val m = e.message.orEmpty()
        return m.contains("PGRST202") || (m.contains("404") && m.contains("wl_mine_put"))
    }

    /**
     * Makes the online copy of your list [code] equal to [items] (the wishes as friends may see them, plus the note
     * item), [name] and [cats]. Throws when the server can't be reached; nothing is lost then (see above).
     * Called with Sync's lock held, so two saves of the same list never overlap.
     */
    suspend fun send(c: Context, code: String, token: String, name: String, cats: List<String>, items: JSONArray, me: String) {
        val sent = load(c, code)
        val now = clock()
        var owner = sent?.owner ?: ""
        if (sent == null || now - sent.fullAt > WEEK || !putKnown) {
            server.full(code, token, name, cats, items)
            save(c, code, MineDiff.state(items, name, cats, owner, now))
        } else {
            val d = MineDiff.diff(sent, items, name, cats)
            if (!d.empty) {
                try {
                    server.put(code, token, d.name, d.cats, d.put, d.del)
                    save(c, code, MineDiff.state(items, name, cats, owner, sent.fullAt))
                } catch (e: Exception) {
                    if (!missingFunction(e)) throw e
                    putKnown = false
                    server.full(code, token, name, cats, items)
                    save(c, code, MineDiff.state(items, name, cats, owner, now))
                }
            }
        }
        // "Owned by …" on friends' screens (2.102): your profile name, sent only when it changed.
        if (me.isNotEmpty() && me != owner) {
            try {
                server.owner(code, token, me)
                owner = me
                load(c, code)?.let { save(c, code, it.copy(owner = owner)) }
            } catch (e: Exception) {
            }
        }
    }
}
