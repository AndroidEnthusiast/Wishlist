package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * "The app was opened" (ROADMAP S4.3, 2.186; supabase-cleanup.sql). Lists and profiles that their owner's app hasn't
 * used for 12 months are removed online (owner, 11 November 2026). While the app is on screen it tells the server, at
 * most once a day, which profile and lists are still in use ([run]). The server answers which of these lists are no
 * longer online (removed by the clean-up); they are put back under the same link, quietly (owner's choice), from the
 * copy on this phone. Reservations made before are gone with the old online copy.
 *
 * Before supabase-cleanup.sql is run, the call doesn't exist and nothing happens.
 */
object Opened {
    private const val DAY = 24L * 60 * 60 * 1000
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Due when the last report is a day old (or the clock went back). */
    fun due(last: Long, now: Long): Boolean = now - last >= DAY || now < last

    /** The lists to report: your own lists that are online, and collab wishlists made on this phone. */
    fun lists(mine: List<MineData>, collab: List<SharedList>): JSONArray {
        val a = JSONArray()
        for (d in mine) if (!d.code.isNullOrEmpty() && !d.token.isNullOrEmpty()) a.put(JSONObject().put("c", d.code).put("t", d.token))
        for (l in collab) if (l.owner.isNotEmpty()) a.put(JSONObject().put("c", l.code).put("t", l.owner))
        return a
    }

    suspend fun run(c: Context) {
        if (!Config.enabled || ListStore.stale || !Live.inFront) return
        val now = System.currentTimeMillis()
        if (!due(prefs(c).getLong("opened_at", 0L), now)) return
        val mine = Repo.allMine(c)
        val auth = Friends.auth(c)
        val gone = try {
            JSONArray(Cloud.rpc(
                "wl_opened",
                JSONObject().put("p_id", auth?.optString("p_id").orEmpty()).put("p_token", auth?.optString("p_token").orEmpty())
                    .put("p_lists", lists(mine.take(80), Shared.lists.take(20)))
            ))
        } catch (e: Exception) {
            return // offline, or the script isn't run yet: tried again at the next start
        }
        prefs(c).edit().putLong("opened_at", now).apply()
        val codes = (0 until gone.length()).map { gone.optString(it) }.toSet()
        // Your own lists that were removed online come back under the same link and token.
        var back = false
        for (d in mine) {
            val code = d.code ?: continue
            val token = d.token ?: continue
            if (code !in codes) continue
            try {
                Cloud.createList(code, token, d.name)
                MineSync.forget(c, code)
                back = true
            } catch (e: Exception) {
            }
        }
        if (back) Sync.catchUp(c)
    }
}
