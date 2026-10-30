package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Price history for every wish. It is kept on this phone only and is never sent to Supabase,
 * so it uses none of your online storage. A point is stored only when the price changes.
 */
object History {
    var all by mutableStateOf<Map<Long, List<PricePoint>>>(emptyMap())
        private set
    private var loaded = false

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun of(id: Long): List<PricePoint> = all[id].orEmpty()

    private fun nowSec() = System.currentTimeMillis() / 1000

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val s = prefs(c).getString("history", null) ?: return
        try {
            val o = JSONObject(s)
            val m = HashMap<Long, List<PricePoint>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                m[k.toLong()] = (0 until a.length()).map { i ->
                    val e = a.getJSONArray(i)
                    PricePoint(e.getLong(0), e.getDouble(1))
                }
            }
            all = m
        } catch (e: Exception) {
        }
    }

    private fun save(c: Context) {
        val o = JSONObject()
        for ((id, list) in all) {
            val a = JSONArray()
            list.forEach { a.put(JSONArray().put(it.t).put(it.p)) }
            o.put(id.toString(), a)
        }
        prefs(c).edit().putString("history", o.toString()).apply()
    }

    /** Records a price for a wish. Does nothing if it is the same as the last recorded price. */
    @Synchronized
    fun record(c: Context, id: Long, price: Double) {
        val cur = all[id].orEmpty()
        val next = appendPoint(cur, nowSec(), price)
        if (next === cur) return
        all = all + (id to next)
        save(c)
    }

    @Synchronized
    fun remove(c: Context, id: Long) {
        if (id !in all) return
        all = all - id
        save(c)
    }

    /** Wishes saved before price history existed start being tracked from today. */
    @Synchronized
    fun seed(c: Context, wishes: List<Wish>) {
        var m = all
        var changed = false
        for (w in wishes) {
            val p = w.price ?: continue
            if (m[w.id].isNullOrEmpty()) {
                m = m + (w.id to appendPoint(emptyList(), nowSec(), p))
                changed = true
            }
        }
        if (changed) {
            all = m
            save(c)
        }
    }
}
