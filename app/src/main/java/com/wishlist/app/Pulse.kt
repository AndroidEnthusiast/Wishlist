package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * One background question (ROADMAP S3.3, owner 8 November 2026: still every 30 minutes). Before 2.175 the half-hourly
 * check made about ten calls (friends, chats, groups, gift exchanges, news, every collab list, every friend's list you
 * reserved on, plus an ownership check per collab list), usually only to learn that nothing changed. Now it first asks
 * `wl_pulse` (supabase-sync.sql) for a short fingerprint of each part and only asks about the parts whose fingerprint
 * differs from the last time that part was handled.
 *
 * Some checks depend on the date, not on the server (the bin's 30 days, the 7-day reminder before an occasion,
 * birthdays, a gift exchange's countdown), so the first check of every day still asks about everything. When the pulse
 * can't be asked (no connection, the script not run yet) everything is asked as before.
 *
 * Parts: "fr", "chat", "gr", "gx", "news"; "l:CODE" a friend's list you reserved on; "s:CODE" a collab list.
 */
class PulsePlan(
    /** Ask about everything (first check of the day, or no pulse). */
    val all: Boolean,
    private val fresh: Map<String, String>,
    private val old: Map<String, String>,
    /** Collab lists that were handed to you (to claim). */
    val claims: Set<String>
) {
    private val done = HashMap<String, String>()

    fun due(part: String): Boolean = all || fresh[part] == null || fresh[part] != old[part]

    /** Lists of [prefix] ("l:" / "s:") to ask about among [codes]; null = all of them. */
    fun lists(prefix: String, codes: Collection<String>): Set<String>? =
        if (all) null else codes.filter { due(prefix + it) }.toSet()

    /** [part] was handled: its fingerprint is remembered, so it isn't asked about again until it changes. */
    fun ok(part: String) { fresh[part]?.let { done[part] = it } }

    /** What to remember: earlier fingerprints, with the ones handled now replaced; parts no longer asked about drop out. */
    fun remembered(): Map<String, String> = if (fresh.isEmpty()) old else (old + done).filterKeys { it in fresh }

    companion object {
        /** The fingerprints in `wl_pulse`'s answer, as parts. Collab lists come by edit key: [codeOf] gives the code. */
        fun parse(o: JSONObject, codeOf: (String) -> String?): Pair<Map<String, String>, Set<String>> {
            val m = HashMap<String, String>()
            val claims = HashSet<String>()
            for (k in listOf("fr", "chat", "gr", "gx", "news")) o.optString(k).takeIf { it.isNotEmpty() }?.let { m[k] = it }
            o.optJSONObject("lists")?.let { l -> for (k in l.keys()) l.optString(k).takeIf { it.isNotEmpty() }?.let { m["l:$k"] = it } }
            o.optJSONObject("shared")?.let { s ->
                for (k in s.keys()) {
                    val code = codeOf(k) ?: continue
                    val e = s.optJSONObject(k) ?: continue
                    e.optString("fp").takeIf { it.isNotEmpty() }?.let { m["s:$code"] = it }
                    if (e.optBoolean("claim")) claims += code
                }
            }
            return m to claims
        }

        fun readMap(s: String?): Map<String, String> = try {
            val o = JSONObject(s ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

object Pulse {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse", Context.MODE_PRIVATE)

    /** Asks the server what changed and returns the plan for this check. Never throws: without an answer, ask everything. */
    suspend fun plan(c: Context): PulsePlan {
        val p = prefs(c)
        val today = LocalDate.now().toString()
        val newDay = p.getString("full_day", "") != today
        val old = PulsePlan.readMap(p.getString("fps", null))
        val keys = Shared.lists.map { it.key }
        val codes = FriendWatch.codes(c)
        return try {
            val body = (Friends.auth(c) ?: JSONObject().put("p_id", JSONObject.NULL).put("p_token", JSONObject.NULL))
                .put("p_device", Repo.deviceId).put("p_keys", JSONArray(keys)).put("p_codes", JSONArray(codes))
            val (fresh, claims) = PulsePlan.parse(JSONObject(Cloud.rpc("wl_pulse", body))) { k -> Shared.lists.firstOrNull { it.key == k }?.code }
            PulsePlan(newDay, fresh, old, claims)
        } catch (e: Exception) {
            PulsePlan(true, emptyMap(), old, emptySet())
        }
    }

    /** After the check: remember the parts handled; a full check also counts as today's. */
    fun finish(c: Context, plan: PulsePlan) {
        val e = prefs(c).edit().putString("fps", JSONObject(plan.remembered() as Map<*, *>).toString())
        if (plan.all) e.putString("full_day", LocalDate.now().toString())
        e.apply()
    }
}
