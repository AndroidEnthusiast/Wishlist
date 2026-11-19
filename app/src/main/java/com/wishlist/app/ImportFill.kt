package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Moved here from Import.kt whole in the data rebuild (owner, 19 November 2026); nothing in it changed.

/**
 * The fill-in after an import (owner: wishes in at once, details afterwards; "it can take a minute"): for each
 * imported wish with a shop link, the shop's page is read once, slowly (one at a time, a pause between), and only what
 * is missing is filled: the name, the price (only when it has none) and the photo. The shop's own photo replaces the
 * list page's small one (owner: sharper photos). Waiting wishes are kept on the phone, so a restart or being offline
 * only delays them. Never touches a wish you changed or removed meanwhile: it is looked up again just before.
 */
object ImportFill {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false
    private const val KEY = "import_fill"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    private fun waiting(c: Context): JSONObject = try { JSONObject(prefs(c).getString(KEY, null) ?: "{}") } catch (e: Exception) { JSONObject() }

    @Synchronized
    fun queue(c: Context, listId: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        val o = waiting(c)
        val a = o.optJSONArray(listId) ?: JSONArray()
        ids.forEach { a.put(it) }
        o.put(listId, a)
        prefs(c).edit().putString(KEY, o.toString()).apply()
        start(c)
    }

    @Synchronized
    private fun next(c: Context): Pair<String, Long>? {
        val o = waiting(c)
        for (k in o.keys().asSequence().toList()) {
            val a = o.optJSONArray(k)
            if (a == null || a.length() == 0) { o.remove(k); continue }
            prefs(c).edit().putString(KEY, o.toString()).apply()
            return k to a.getLong(0)
        }
        prefs(c).edit().putString(KEY, o.toString()).apply()
        return null
    }

    @Synchronized
    private fun done(c: Context, listId: String, id: Long) {
        val o = waiting(c)
        val a = o.optJSONArray(listId) ?: return
        val left = JSONArray()
        for (i in 0 until a.length()) if (a.optLong(i) != id) left.put(a.optLong(i))
        if (left.length() == 0) o.remove(listId) else o.put(listId, left)
        prefs(c).edit().putString(KEY, o.toString()).apply()
    }

    /** Starts (or resumes, at app start) the fill-in, when there is anything waiting. */
    fun start(c: Context) {
        val ctx = c.applicationContext
        synchronized(this) { if (running) return; running = true }
        scope.launch {
            try {
                var misses = 0
                while (true) {
                    val (listId, id) = next(ctx) ?: break
                    if (listId !in Repo.mineIds) { done(ctx, listId, id); continue }
                    val w = Repo.mine(ctx, listId).wishes.firstOrNull { it.id == id }
                    if (w == null || w.url.isBlank()) { done(ctx, listId, id); continue }
                    val meta = try { fetchMeta(w.url, ctx) } catch (e: Exception) { null }
                    if (meta == null) {
                        // Offline or the shop didn't answer: try again later (next start), at most a few in a row now.
                        // A wish whose shop never answers is given up after 5 tries (it keeps what it has).
                        done(ctx, listId, id)
                        if (tried(ctx, id) < 5) queueLast(ctx, listId, id)
                        if (++misses >= 3) break
                        delay(3000L)
                        continue
                    }
                    misses = 0
                    withContext(Dispatchers.Main) { fill(ctx, listId, id, meta) }
                    done(ctx, listId, id)
                    delay(1500L)
                }
            } finally {
                synchronized(this@ImportFill) { running = false }
            }
        }
    }

    /** Counts a failed try of wish [id] and returns how many there were. */
    @Synchronized
    private fun tried(c: Context, id: Long): Int {
        val o = try { JSONObject(prefs(c).getString("import_fill_tries", null) ?: "{}") } catch (e: Exception) { JSONObject() }
        val n = o.optInt(id.toString(), 0) + 1
        o.put(id.toString(), n)
        prefs(c).edit().putString("import_fill_tries", o.toString()).apply()
        return n
    }

    @Synchronized
    private fun queueLast(c: Context, listId: String, id: Long) {
        val o = waiting(c)
        val a = o.optJSONArray(listId) ?: JSONArray()
        a.put(id)
        o.put(listId, a)
        prefs(c).edit().putString(KEY, o.toString()).apply()
    }

    /** Fills only what is missing (see [filled]); the wish as it is now, so nothing you changed is overwritten. */
    private fun fill(c: Context, listId: String, id: Long, meta: Meta) {
        if (listId !in Repo.mineIds) return
        var priced: Double? = null
        Repo.updateMine(c, listId) { ws ->
            ws.map { w ->
                if (w.id != id) w else filled(w, meta).also { n -> if (w.price == null && n.price != null) priced = n.price }
            }
        }
        priced?.let { History.record(c, id, it) }
    }

    /**
     * [w] with what the shop's page adds: a name when it has none, a price when it has none (never a changed price:
     * Rule 8), and the shop's own photo (bigger than a list page's thumbnail; only when the page is a product page).
     */
    fun filled(w: Wish, meta: Meta): Wish {
        if (meta.blocked) return w
        val title = w.title.ifBlank { meta.title.take(200) }
        val price = w.price ?: meta.price
        val currency = if (w.price == null && meta.price != null) meta.currency else w.currency
        val image = if (meta.image.startsWith("http") && (w.image.isBlank() || meta.product)) secureImage(meta.image) else w.image
        return w.copy(title = title, price = price, currency = currency, image = image)
    }
}
