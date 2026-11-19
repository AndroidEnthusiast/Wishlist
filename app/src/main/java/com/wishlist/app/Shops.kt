package com.wishlist.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/*
 * "Find a gift" (2.114, owner): a row of web shops on Home. Tapping one opens the shop inside the app; on any product
 * page the floating "+ Add wish" button reads the page that is open (with the same reading as a shared link, parsePage
 * in Fetch.kt) and opens the add form filled in. Reading the page the person already sees also works for shops that
 * block the app's own requests.
 *  - A starter list of 20 shops in the Netherlands (owner's choice); "Add shop" adds your own, a long press removes one.
 *    The list is kept on this phone ("shops" in the preferences), not online.
 *  - Each shop shows the icon its own website publishes, loaded live like a browser bookmark (owner's choice; we never
 *    copy logos into the app), with Google's icon service as the last resort (2.115, see [ShopIcons]). When nothing
 *    loads, a coloured tile with the first letter shows.
 *  - App only: a web page can't show other shops inside itself; browser users keep pasting links (owner's choice).
 *  - Plain links, no affiliate codes (owner: maybe discuss later).
 * Split up in the rebuild (owner, 19 November 2026; MAP "How a screen is built"): this file keeps your shops and their
 * icons; the page is ShopsScreen.kt, a shop inside the app ShopBrowser.kt, their state holders ShopsModel.kt.
 */

data class Shop(val name: String, val url: String) {
    val host: String get() = try { URI(url).host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }
}

object Shops {
    val starters = listOf(
        Shop("bol.com", "https://www.bol.com/nl/nl/"),
        Shop("Amazon", "https://www.amazon.nl/"),
        Shop("Coolblue", "https://www.coolblue.nl/"),
        Shop("Zalando", "https://www.zalando.nl/"),
        Shop("IKEA", "https://www.ikea.com/nl/nl/"),
        Shop("Wehkamp", "https://www.wehkamp.nl/"),
        Shop("HEMA", "https://www.hema.nl/"),
        Shop("de Bijenkorf", "https://www.debijenkorf.nl/"),
        Shop("Intertoys", "https://www.intertoys.nl/"),
        Shop("LEGO", "https://www.lego.com/nl-nl"),
        Shop("Decathlon", "https://www.decathlon.nl/"),
        Shop("Douglas", "https://www.douglas.nl/"),
        Shop("Rituals", "https://www.rituals.com/nl-nl/"),
        Shop("Blokker", "https://www.blokker.nl/"),
        Shop("fonQ", "https://www.fonq.nl/"),
        Shop("Nike", "https://www.nike.com/nl/"),
        Shop("H&M", "https://www2.hm.com/nl_nl/"),
        Shop("Etsy", "https://www.etsy.com/nl/"),
        Shop("Bever", "https://www.bever.nl/"),
        Shop("Apple", "https://www.apple.com/nl/")
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Goes up on every change, so Home redraws the row. */
    var version by mutableIntStateOf(0)
        private set

    /** Your shops in the order you added them (see [list] for the order on Home). */
    private fun stored(c: Context): List<Shop> {
        val raw = prefs(c).getString("shops", null) ?: return starters
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it) }.map { Shop(it.optString("name"), it.optString("url")) }
                .filter { it.url.startsWith("http") }
        } catch (e: Exception) {
            starters
        }
    }

    /** The shops on Home: the one you opened last first (owner, 2.121), then the ones never opened, in their order. */
    fun list(c: Context): List<Shop> {
        @Suppress("UNUSED_VARIABLE") val v = version
        val p = prefs(c)
        return stored(c).withIndex().sortedWith(
            compareByDescending<IndexedValue<Shop>> { p.getLong("shopopened_" + it.value.host, 0L) }.thenBy { it.index }
        ).map { it.value }
    }

    /** Remembers that a shop was opened, for the order on Home. */
    fun opened(c: Context, s: Shop) {
        prefs(c).edit().putLong("shopopened_" + s.host, System.currentTimeMillis()).apply()
        version++
    }

    /** The shop you already have with the same web address, if any (2.121: adding it again says so). */
    fun existing(c: Context, address: String): Shop? {
        val u = cleanUrl(address.trim()) ?: return null
        val h = Shop("", u).host
        return stored(c).firstOrNull { it.host == h }
    }

    private fun save(c: Context, l: List<Shop>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs(c).edit().putString("shops", a.toString()).apply()
        version++
    }

    /** Adds a shop by its web address ("coolblue.nl" is fine); null when that isn't a web address. */
    fun add(c: Context, name: String, address: String): Shop? {
        val u = cleanUrl(address.trim()) ?: return null
        val s = Shop(name.trim().ifEmpty { Shop("", u).host }, u)
        if (s.host.isEmpty()) return null
        save(c, stored(c).filter { it.host != s.host } + s)
        return s
    }

    /** Removes a shop. Returns where it was, for [putBack] (Undo). */
    fun remove(c: Context, s: Shop): Int {
        val l = stored(c)
        save(c, l.filter { it.url != s.url })
        return l.indexOfFirst { it.url == s.url }
    }

    /** Undo of [remove]: the shop goes back where it was. */
    fun putBack(c: Context, s: Shop, at: Int) {
        val l = stored(c).filter { it.host != s.host }.toMutableList()
        l.add(at.coerceIn(0, l.size), s)
        save(c, l)
    }

    /** True when a site with host [h] ("www." or not) is in your shops. */
    fun has(c: Context, h: String): Boolean = h.removePrefix("www.").let { x -> stored(c).any { it.host == x } }

    /** The web search behind "Search in browser" (owner, 7 November 2026: Google). */
    fun searchUrl(q: String): String = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q.trim(), "UTF-8")

    /** A search engine's own pages are not offered as a shop. */
    fun isSearch(h: String): Boolean = h.removePrefix("www.").let { it.startsWith("google.") || it == "consent.google.com" || it.endsWith(".google.com") }

    /** "Add … to My shops?" after adding a wish from a site: asked once per site. */
    fun offered(c: Context, h: String): Boolean = prefs(c).getBoolean("shop_offer_" + h.removePrefix("www."), false)
    fun markOffered(c: Context, h: String) = prefs(c).edit().putBoolean("shop_offer_" + h.removePrefix("www."), true).apply()

    /** Puts back the starter shops you removed (your own stay). */
    fun restoreStarters(c: Context) {
        val now = stored(c)
        save(c, now + starters.filter { st -> now.none { it.host == st.host } })
    }
}

/**
 * Where a shop's icon comes from: only Google's free icon service (owner, 6 November 2026, after bol.com blocked "unusual
 * traffic"): the app never asks the shops themselves for their icon; Google only gets the shop's web address.
 */
object ShopIcons {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun google(host: String) = "https://www.google.com/s2/favicons?domain=$host&sz=256"

    // 2.117 (owner's recording: Zalando's icon showed, then vanished after scrolling): the icon that was accepted is
    // remembered (here and on the phone), and icons that looked empty are never tried again, so a tile that scrolls
    // away and back shows the same icon at once.
    private val rejected = HashSet<String>()

    /** The icon that was accepted earlier, when it came from Google's service (one from a shop's own site isn't used). */
    fun accepted(c: Context, host: String): String? =
        prefs(c).getString("shopiconok3_$host", null)?.takeIf { it.startsWith("https://www.google.com/s2/favicons") }

    fun accept(c: Context, host: String, url: String, px: Int = 0) {
        if (accepted(c, host) != url) prefs(c).edit().putString("shopiconok3_$host", url).putInt("shopiconpx_$host", px).apply()
    }

    /** Goes up when the tiles should look again. */
    var version by mutableIntStateOf(0)
        private set

    fun reject(c: Context, host: String, url: String) {
        rejected.add(url)
        if (accepted(c, host) == url) prefs(c).edit().remove("shopiconok3_$host").apply()
    }

    fun isRejected(url: String) = url in rejected

    /**
     * Icons smaller than this (pixels) look blurry on a 52 dp tile (2.119: 64, not 96; 96 also passed over icons that
     * looked fine, like Amazon's and HEMA's).
     */
    const val SHARP = 64

    /**
     * True when a loaded icon would look empty on the tile: almost all of it see-through or white (2.116: Zalando's
     * icon is white on see-through). Then the next address is tried.
     */
    fun looksEmpty(b: Bitmap): Boolean {
        val n = 16
        var shown = 0
        for (y in 0 until n) for (x in 0 until n) {
            val px = b.getPixel(x * b.width / n, y * b.height / n)
            val a = android.graphics.Color.alpha(px)
            val r = android.graphics.Color.red(px); val g = android.graphics.Color.green(px); val bl = android.graphics.Color.blue(px)
            if (a > 60 && !(r > 225 && g > 225 && bl > 225)) shown++
        }
        return shown < n * n * 4 / 100
    }

}
