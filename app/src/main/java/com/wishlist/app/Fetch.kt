package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/**
 * The plain request to a shop, the same for reading a product (fetchMeta) and for a price report (PriceReport.kt).
 * [waitMs]: how long to wait for the shop; a link shared into the form waits [QUICK_WAIT_MS] (owner, 21 November 2026).
 */
internal fun shopRequest(url: String, waitMs: Int = 15000): org.jsoup.Connection.Response = Jsoup.connect(url).userAgent(UA)
    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .header("Accept-Language", acceptLanguage())
    .timeout(waitMs).followRedirects(true).ignoreHttpErrors(true).execute()

/** The plain read's wait when a person is waiting for the form (owner, 21 November 2026: 8 s instead of 15 s). */
const val QUICK_WAIT_MS = 8000

/** The plain read found everything: a real page with a price. Otherwise the hidden browser has a go. */
internal fun complete(m: Meta?): Boolean = m != null && !m.blocked && m.price != null

/** The hidden browser can stop early: the page's own product data has a price (owner, 21 November 2026). */
internal fun hasProductPrice(html: String, url: String): Boolean =
    html.contains("ld+json") && try { ldProduct(Jsoup.parse(html, url), url)?.price != null } catch (e: Exception) { false }

internal fun shopHost(url: String): String = try { java.net.URI(url).host.orEmpty().removePrefix("www.") } catch (e: Exception) { "" }

/**
 * Reads title, image, description and price from a product page. Returns blocked=true if the store refused.
 * If a plain request is blocked or finds no price and [ctx] is given, the page is loaded in a hidden browser
 * (which runs JavaScript like Chrome) and read again. Pass browser=false to skip that slower step.
 *
 * Faster (owner, 21 November 2026): [quick] waits at most 8 s for the plain read; [onFirst] gets what the plain read
 * found (a name, a photo) while the hidden browser is still looking for the price; the hidden browser stops as soon as
 * the page's product data has a price; and a shop that needed the hidden browser before ([SlowShops], on the phone
 * only) gets both reads at the same time.
 */
suspend fun fetchMeta(
    url: String, ctx: Context? = null, browser: Boolean = true, quick: Boolean = false, onFirst: (suspend (Meta) -> Unit)? = null
): Meta? = coroutineScope {
    val host = shopHost(url)
    val c = if (browser) ctx else null
    val known = c != null && SlowShops.has(c, host)
    // A shop known to need the hidden browser: it starts at once, next to the plain read.
    val early = if (c != null && known) async { rendered(c, url) } else null
    val first = withContext(Dispatchers.IO) {
        try {
            val res = shopRequest(url, if (quick) QUICK_WAIT_MS else 15000)
            parsePage(res.parse(), res.url().host.orEmpty(), res.statusCode())
        } catch (e: Exception) { null }
    }
    if (c == null || complete(first)) {
        early?.cancel()
        // The shop reads plainly again: no need to start the hidden browser for it next time.
        if (c != null && known) SlowShops.forget(c, host)
        return@coroutineScope first
    }
    if (first != null && !first.blocked && onFirst != null) onFirst(first)
    val second = early?.await() ?: rendered(c, url)
    if (second != null) {
        if (second.price != null) SlowShops.remember(c, host)
        return@coroutineScope second
    }
    first
}

/** The page read in the hidden browser, or null when it never showed a real page. */
private suspend fun rendered(ctx: Context, url: String): Meta? {
    val html = try {
        renderHtml(ctx, url, done = { withContext(Dispatchers.Default) { hasProductPrice(it, url) } })
    } catch (e: Exception) { null } ?: return null
    val host = try { java.net.URI(url).host.orEmpty() } catch (e: Exception) { "" }
    val m = withContext(Dispatchers.Default) { try { parsePage(Jsoup.parse(html, url), host, 200) } catch (e: Exception) { null } }
    return m?.takeIf { !it.blocked }
}

/**
 * Shops whose plain read was blocked or had no price, so the hidden browser was needed (owner, 21 November 2026:
 * "remember slow shops"). Next time both reads start together. Only the shop's address, kept on this phone; a shop
 * that reads plainly again is forgotten.
 */
object SlowShops {
    private const val KEY = "slow_shops"
    private const val MAX = 100
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun all(c: Context): Set<String> = prefs(c).getStringSet(KEY, null) ?: emptySet()

    fun has(c: Context, host: String) = host.isNotEmpty() && host in all(c)

    @Synchronized fun remember(c: Context, host: String) = save(c, added(all(c), host))

    @Synchronized fun forget(c: Context, host: String) = save(c, all(c) - host)

    private fun save(c: Context, s: Set<String>) { prefs(c).edit().putStringSet(KEY, s).apply() }

    /** [s] with [host] added; past [MAX] shops the list starts again, so it never grows without end. */
    internal fun added(s: Set<String>, host: String): Set<String> = when {
        host.isEmpty() || host in s -> s
        s.size >= MAX -> setOf(host)
        else -> s + host
    }
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
