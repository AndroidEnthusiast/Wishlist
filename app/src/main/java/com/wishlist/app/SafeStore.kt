package com.wishlist.app

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reading the lists stored on the phone without ever losing them. Before, a list that couldn't be read was quietly
 * taken as empty, and the next save wrote the empty list over it: everything was gone.
 *  - One entry that can't be read is skipped; the others are kept.
 *  - Whenever something couldn't be read, the stored text is kept as it was under "<key>_unreadable" (never written
 *    over), so it can still be rescued by a later version.
 *  - If nothing can be read at all, the copy from the last good start ("<key>_backup") is used instead.
 * A copy of every list that was read fine is kept as "<key>_backup" once per start.
 */
object SafeStore {
    private const val TAG = "Wishlist"

    fun <T> readList(p: SharedPreferences, key: String, read: (JSONObject) -> T): List<T> {
        val raw = p.getString(key, null) ?: return emptyList()
        val whole = parse(raw, read)
        if (whole != null && whole.second) {
            p.edit().putString(key + "_backup", raw).apply()
            return whole.first
        }
        Log.w(TAG, "Couldn't read all of \"$key\"; the stored text is kept as ${key}_unreadable")
        if (!p.contains(key + "_unreadable")) p.edit().putString(key + "_unreadable", raw).apply()
        if (whole != null) return whole.first
        val backup = p.getString(key + "_backup", null)?.let { parse(it, read) }
        return backup?.first ?: emptyList()
    }

    /** The entries that could be read, and whether that was all of them. Null when the text isn't a list at all. */
    private fun <T> parse(raw: String, read: (JSONObject) -> T): Pair<List<T>, Boolean>? {
        val a = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<T>()
        var all = true
        for (i in 0 until a.length()) {
            try {
                out.add(read(a.getJSONObject(i)))
            } catch (e: Exception) {
                all = false
            }
        }
        return out to all
    }
}
