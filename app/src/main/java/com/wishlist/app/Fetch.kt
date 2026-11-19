package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

// Price reading. Since the data rebuild (owner, 19 November 2026) this file fetches a page and holds Meta and
// ReaderUpgrade; the rules are in FetchPage.kt (a whole page), FetchProduct.kt (the shop's product data, the shown
// price) and FetchText.kt (links, numbers, currencies, names). FetchSameTest proves they read exactly as before.

data class Meta(
    val title: String,
    val desc: String,
    val image: String,
    val price: Double?,
    val currency: String,
    val blocked: Boolean = false,
    // The page is a product page (2.145): the shop's product data, og:type product, product microdata, or a price.
    // A product without a readable price still counts; its price is left empty to fill in.
    val product: Boolean = false,
    // The page itself named the currency (its product data, or a symbol next to the price), so [currency] isn't a
    // guess from the web address or the phone's country (2.178): a price check never compares across currencies.
    val currencySure: Boolean = false
)

private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * A picture address that the phone can load (owner, 16 November 2026: a Baracuta wish stayed white): http:// becomes
 * https://, since Android refuses plain http and shops (Shopify above all) serve the same picture over https.
 * Same as secureImage in web/parse.js.
 */
fun secureImage(url: String): String = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

/** The plain request to a shop, the same for reading a product (fetchMeta) and for a price report (PriceReport.kt). */
internal fun shopRequest(url: String): org.jsoup.Connection.Response = Jsoup.connect(url).userAgent(UA)
    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .header("Accept-Language", acceptLanguage())
    .timeout(15000).followRedirects(true).ignoreHttpErrors(true).execute()

/**
 * Reads title, image, description and price from a product page. Returns blocked=true if the store refused.
 * If a plain request is blocked or finds no price and [ctx] is given, the page is loaded in a hidden browser
 * (which runs JavaScript like Chrome) and read again. Pass browser=false to skip that slower step.
 */
suspend fun fetchMeta(url: String, ctx: Context? = null, browser: Boolean = true): Meta? =
    withContext(Dispatchers.IO) {
        var first: Meta? = null
        try {
            val res = shopRequest(url)
            first = parsePage(res.parse(), res.url().host.orEmpty(), res.statusCode())
        } catch (e: Exception) {
        }
        val f = first
        if (ctx != null && browser && (f == null || f.blocked || f.price == null)) {
            val html = try { renderHtml(ctx, url) } catch (e: Exception) { null }
            if (html != null) {
                val host = try { java.net.URI(url).host.orEmpty() } catch (e: Exception) { "" }
                val second = try { parsePage(Jsoup.parse(html, url), host, 200) } catch (e: Exception) { null }
                if (second != null && !second.blocked) return@withContext second
            }
        }
        f
    }

/**
 * The better product reader of 2.145 can read a different price than before, only because it now reads the right one
 * (the product's own price, not a "you may also like" item's). The owner chose: the first check of each wish after
 * the update takes the newly read price quietly: no notification, no crossed-out old price, no point in the history.
 * Wishes added after the update were read with the new reader already.
 */
object ReaderUpgrade {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** True when wish [id] (made at that time, in milliseconds) hasn't been checked with the new reader yet. */
    @Synchronized
    fun quiet(c: Context, id: Long): Boolean {
        val p = prefs(c)
        var since = p.getLong("rd3_since", 0L)
        if (since == 0L) { since = System.currentTimeMillis(); p.edit().putLong("rd3_since", since).apply() }
        return id < since && id.toString() !in (p.getStringSet("rd3_done", null) ?: emptySet())
    }

    /** Wish [id] was checked with the new reader. */
    @Synchronized
    fun done(c: Context, id: Long) {
        val p = prefs(c)
        val s = (p.getStringSet("rd3_done", null) ?: emptySet()).toMutableSet()
        if (s.add(id.toString())) p.edit().putStringSet("rd3_done", s).apply()
    }
}
