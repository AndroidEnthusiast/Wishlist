package com.wishlist.app

import org.json.JSONArray

/** Reading the server's answers about reservations (moved out of Cloud.kt to keep it small, 26 November 2026). */
internal object ResAnswers {
    /** Reads an answer of wl_get_reservations: wish id -> true (yours) or false (someone else's); counts to [ResCounts]. */
    internal fun parse(code: String, t: String): Map<Long, Boolean> {
        val a = JSONArray(t)
        val m = HashMap<Long, Boolean>()
        val counts = HashMap<Long, ResCount>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val id = o.getLong("id")
            val mine = o.getBoolean("mine")
            // Before supabase-setup.sql of 6 November 2026 there are no numbers: one reservation takes the wish.
            val c = ResCount(o.optInt("my", if (mine) 1 else 0), o.optInt("n", 1), o.optBoolean("full", true))
            counts[id] = c
            if (mine) m[id] = true else if (c.full) m[id] = false
        }
        ResCounts.put(code, counts)
        return m
    }
}
