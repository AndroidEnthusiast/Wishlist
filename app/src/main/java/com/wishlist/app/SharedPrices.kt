package com.wishlist.app

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

/**
 * Price-drop checks for shared wishlists, run by [PriceWorker] next to the check of My wishlist.
 *
 * The phones on a shared list share the work: a phone that asks a shop for a price writes the time online ("chk", with
 * the new price and old price when it changed; only those fields, so nobody's edit is undone), and the other phones
 * skip that wish until it is due again. So each shop is asked about once per check time, not once per phone. Each phone then compares the prices online with the
 * prices it saw last time (kept on the phone, "shp_<code>"), so every person on the list gets the alert once, also
 * when another phone found the drop first. A price someone typed in by hand has no old price and gives no alert.
 */
object SharedPrices {

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun seenKey(code: String) = "shp_$code"

    /**
     * Checks every shared list (the background check), or only the one with code [only] (pulling down on the list).
     * A wish another phone checked recently is skipped: within this phone's check interval in the background, within
     * 15 minutes when pulling down. Returns how many prices dropped.
     */
    suspend fun check(c: Context, only: String? = null): Int {
        var dropped = 0
        val fresh = if (only != null) 15L * 60 * 1000 else Repo.intervalHours * 60L * 60 * 1000 * 9 / 10
        for (l in Shared.lists.toList()) {
            if (only != null && l.code != only) continue
            val r = try {
                Cloud.shGet(l.key)
            } catch (e: Exception) {
                null
            } ?: continue
            // Wishes in the bin get no price checks and no alerts.
            var wishes = r.list.wishes.filter { it.bin == 0L }
            for (w in wishes.toList()) {
                if (w.url.isBlank()) continue
                if (System.currentTimeMillis() - w.checked < fresh) continue
                // A shop that couldn't be read is not marked: another phone may have more luck.
                val np = fetchMeta(w.url, c, w.price != null)?.price ?: continue
                val changed = newPrice(w, np)
                val upd = (changed ?: w).copy(checked = System.currentTimeMillis())
                try {
                    Cloud.shPut(l.key, upd, if (changed != null) listOf("price", "old", "chk") else listOf("chk"))
                    wishes = wishes.map { if (it.id == w.id) upd else it }
                } catch (e: Exception) {
                }
            }
            val (alerts, seen) = drops(readSeen(c, l.code), wishes)
            dropped += alerts.count { (w, was) -> (w.price ?: was) < was }
            for ((w, was) in alerts) {
                val now = w.price ?: continue
                val id = l.code.hashCode() * 31 + w.id.hashCode()
                if (now < was) {
                    if (Repo.notifyDrops) Alerts.notifyDrop(c, id, w.title, was, now, w.currency, tr("Price drop on %s", r.list.name))
                } else if (Repo.notifyRises) {
                    Alerts.notifyRise(c, id, w.title, was, now, w.currency, tr("Price increase on %s", r.list.name))
                }
            }
            writeSeen(c, l.code, seen)
        }
        return dropped
    }

    /** The wish with the price a shop gave now, or null when nothing changes. Same rules as for My wishlist. */
    fun newPrice(w: Wish, np: Double): Wish? {
        val cur = w.price
        return when {
            cur == null -> w.copy(price = np)
            np < cur - 0.005 -> w.copy(price = np, oldPrice = cur)
            np > cur + 0.005 -> w.copy(price = np, oldPrice = cur)
            else -> null
        }
    }

    /**
     * Which wishes got cheaper or more expensive since this phone last looked (with the price it saw then), and the
     * prices to remember now. Only a change found by a price check counts (it has an old price), never a price typed
     * in by hand. A wish seen for the first time gives no alert.
     */
    fun drops(seen: Map<Long, Double>, wishes: List<Wish>): Pair<List<Pair<Wish, Double>>, Map<Long, Double>> {
        val alerts = ArrayList<Pair<Wish, Double>>()
        val next = HashMap<Long, Double>()
        for (w in wishes) {
            val p = w.price ?: continue
            next[w.id] = p
            val was = seen[w.id] ?: continue
            val old = w.oldPrice
            if (old != null && old > p + 0.005 && p < was - 0.005) alerts.add(w to was)
            else if (old != null && old < p - 0.005 && p > was + 0.005) alerts.add(w to was)
        }
        return alerts to next
    }

    private fun readSeen(c: Context, code: String): Map<Long, Double> = try {
        val o = JSONObject(prefs(c).getString(seenKey(code), null) ?: "{}")
        o.keys().asSequence().associate { it.toLong() to o.getDouble(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun writeSeen(c: Context, code: String, seen: Map<Long, Double>) {
        val o = JSONObject()
        seen.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs(c).edit().putString(seenKey(code), o.toString()).apply()
    }
}
