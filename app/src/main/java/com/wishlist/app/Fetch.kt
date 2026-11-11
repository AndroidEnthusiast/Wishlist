package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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

/** What the shop's own product data (JSON-LD, schema.org) says: the product itself, never "related" products. */
internal data class LdProduct(val name: String, val image: String, val price: Double?, val currency: String)

/** True for a JSON-LD @type of Product or ProductGroup (a string or a list). */
private fun ldIsProduct(o: org.json.JSONObject): Boolean {
    val t = o.opt("@type")
    val types = if (t is org.json.JSONArray) (0 until t.length()).map { t.optString(it) } else listOf(t?.toString().orEmpty())
    return types.any { it.endsWith("Product") || it.endsWith("ProductGroup") }
}

/** The top-level objects of one JSON-LD block: itself, a list, or its @graph (not nested properties). */
private fun ldNodes(v: Any?): List<org.json.JSONObject> = when (v) {
    is org.json.JSONArray -> (0 until v.length()).flatMap { ldNodes(v.opt(it)) }
    is org.json.JSONObject -> listOf(v) + ldNodes(v.opt("@graph")).filter { it !== v }
    else -> emptyList()
}

/** A price from schema.org offers: an Offer, a list of them, an AggregateOffer (lowPrice) or a priceSpecification. */
private fun ldPrice(offers: Any?): Pair<Double, String>? {
    when (offers) {
        is org.json.JSONArray -> { for (i in 0 until offers.length()) ldPrice(offers.opt(i))?.let { return it }; return null }
        is org.json.JSONObject -> {
            val cur = offers.optString("priceCurrency")
            for (k in listOf("price", "lowPrice")) {
                val n = offers.opt(k)?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.toNum()
                if (n != null && n > 0) return n to cur
            }
            ldPrice(offers.opt("priceSpecification"))?.let { (n, c) -> return n to c.ifEmpty { cur } }
            ldPrice(offers.opt("offers"))?.let { return it }
            return null
        }
        else -> return null
    }
}

private fun ldImage(v: Any?): String = when (v) {
    is String -> v
    is org.json.JSONArray -> if (v.length() > 0) ldImage(v.opt(0)) else ""
    is org.json.JSONObject -> v.optString("url").ifEmpty { v.optString("contentUrl") }
    else -> ""
}

/**
 * The product in the page's JSON-LD (2.145): the first Product or ProductGroup at the top of a block or in its @graph.
 * A ProductGroup (sizes, colours) takes the variant whose web address is the page's [url], else its own offers, else
 * its first variant's. Null when the page has no product data.
 */
internal fun ldProduct(d: Document, url: String): LdProduct? {
    for (sc in d.select("script[type=application/ld+json]")) {
        val v = try { org.json.JSONTokener(sc.data().trim()).nextValue() } catch (e: Exception) { continue }
        val p = ldNodes(v).firstOrNull { ldIsProduct(it) } ?: continue
        var price = ldPrice(p.opt("offers"))
        val variants = p.optJSONArray("hasVariant")
        if (variants != null) {
            val list = (0 until variants.length()).mapNotNull { variants.optJSONObject(it) }
            val here = list.firstOrNull { val u = it.optString("url"); u.isNotEmpty() && (url == u || url.endsWith(u) || u.endsWith(url.substringAfter("://").substringAfter('/'))) }
            price = (here?.let { ldPrice(it.opt("offers")) }) ?: price ?: list.firstNotNullOfOrNull { ldPrice(it.opt("offers")) }
        }
        val image = ldImage(p.opt("image")).ifEmpty { ldImage(variants?.optJSONObject(0)?.opt("image")) }
        return LdProduct(p.optString("name").trim(), image, price?.first, price?.second.orEmpty())
    }
    return null
}

/**
 * The price a shop shows on the page (2.145, the last try before the "Price: €…" words): the first element whose class
 * or id says "price", that holds a short amount with a currency, and isn't an old (crossed-out) price or a delivery
 * cost. Same rules as web/parse.js.
 */
internal fun shownPrice(d: Document, host: String): Pair<Double, String>? {
    var n = 0
    for (e in d.select("[class~=(?i)price], [id~=(?i)price]")) {
        if (++n > 400) break
        val cls = (e.className() + " " + e.id()).lowercase()
        if (OLD_PRICE.containsMatchIn(cls)) continue
        if (foreign(e, d.location())) continue
        if (e.parents().any { it.tagName() in listOf("del", "s", "strike") || OLD_PRICE.containsMatchIn((it.className()).lowercase()) }) continue
        if (e.tagName() in listOf("del", "s", "strike", "script", "style", "meta")) continue
        val t = e.ownText().ifBlank { e.text() }.trim()
        if (t.length > 40 || NOT_A_PRICE.containsMatchIn(t.lowercase())) continue
        val m = SHOWN_PRICE.find(t) ?: continue
        val amount = (m.groupValues[2].ifEmpty { m.groupValues[3] }).toNum() ?: continue
        if (amount <= 0) continue
        return amount to currencyOf(t, host)
    }
    return null
}

/**
 * Another product on the page (owner, 8 November 2026: Zalando's "Gesponsord" tile above the product gave a wish its
 * €159,95): an element inside a sponsored, "you might also like" or related block, or inside a link to another page.
 * Its price is never this product's price. Same rules as foreign() in web/parse.js.
 */
private val FOREIGN = Regex("sponsor|gesponsord|recommend|aanbevol|related|similar|you-may|you-might|also-like|ook-leuk|upsell|cross-sell|crosssell|reco-|reco_|carousel")

private fun pathOf(u: String, base: String): String = try {
    val x = java.net.URI(base.ifEmpty { "https://x.invalid/" }).resolve(u.trim().replace(" ", "%20"))
    (x.host.orEmpty()) + (x.path.orEmpty()).trimEnd('/')
} catch (e: Exception) { "" }

internal fun foreign(e: org.jsoup.nodes.Element, base: String): Boolean {
    val here = if (base.isNotEmpty()) pathOf(base, base) else ""
    var a: org.jsoup.nodes.Element? = e
    while (a != null) {
        val tag = a.tagName().lowercase()
        if (tag == "body" || tag == "html") break
        val marks = listOf(a.className(), a.id(), a.attr("aria-label"), a.attr("data-testid")).joinToString(" ").lowercase()
        if (FOREIGN.containsMatchIn(marks)) return true
        if (tag == "a" && here.isNotEmpty()) {
            val h = a.attr("href").trim()
            if (h.isNotEmpty() && !h.startsWith("#") && !h.lowercase().startsWith("javascript:") && pathOf(h, base) != here) return true
        }
        a = a.parent()
    }
    return false
}

/** "€ 39,90", "€39.90", "39,90 €", "£12", "$9.99", "12,50 EUR". */
private val SHOWN_PRICE = Regex("([€\$£])\\s?([0-9][0-9.,]*)|([0-9][0-9.,]*)\\s?(?:€|EUR|£|GBP|USD)")
/** Classes of old prices and price ranges that aren't this product's price now. */
private val OLD_PRICE = Regex("old|was|strike|compare|before|previous|original|crossed|rrp|msrp|list-price|from-price")
/** Amounts that aren't the price: delivery, shipping, free shipping thresholds. */
private val NOT_A_PRICE = Regex("verzend|bezorg|shipping|delivery|versand|livraison|vanaf|from|per maand|/mnd|p/m")

private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** Pulls the first link out of shared text, or turns "store.com/item" into a full URL. */
fun cleanUrl(s: String): String? {
    val raw = Regex("https?://\\S+").find(s)?.value
        ?: s.trim().takeIf { it.contains('.') && !it.contains(' ') }?.let { "https://$it" }
        ?: return null
    return etsyCanonical(amazonCanonical(raw))
}

/** Trims Amazon links to https://host/dp/ASIN so tracking junk never breaks price checks. */
private fun amazonCanonical(u: String): String {
    val host = Regex("https?://([^/?#]+)").find(u)?.groupValues?.get(1) ?: return u
    if (!host.contains("amazon.")) return u
    val asin = Regex("/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})").find(u)?.groupValues?.get(1) ?: return u
    return "https://$host/dp/$asin"
}

/** Trims Etsy links to https://host/listing/ID, dropping the long tracking part. */
private fun etsyCanonical(u: String): String {
    val host = Regex("https?://([^/?#]+)").find(u)?.groupValues?.get(1) ?: return u
    if (!host.contains("etsy.com")) return u
    val id = Regex("/listing/([0-9]+)").find(u)?.groupValues?.get(1) ?: return u
    return "https://$host/listing/$id"
}

/** Turns "$1,299.00", "1.299,00 €", "12,50" or "725.000" into a number. */
internal fun String.toNum(): Double? {
    val s = replace(Regex("[^0-9.,]"), "").trim('.', ',')
    if (s.isEmpty()) return null
    val n = when {
        s.contains(',') && s.contains('.') ->
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        s.contains(',') ->
            if (s.length - s.lastIndexOf(',') == 3) s.replace(',', '.') else s.replace(",", "")
        // "725.000" is seven hundred twenty-five thousand (dots as thousands separators), not 725.
        Regex("^[0-9]{1,3}(\\.[0-9]{3})+$").matches(s) -> s.replace(".", "")
        else -> s
    }
    return n.toDoubleOrNull()
}

/** True when [text] itself says which currency it is: a symbol or a three-letter code ("€ 12", "12 EUR"). */
internal fun namesCurrency(text: String): Boolean = Regex("[€£\$₹¥]|\\b(EUR|GBP|USD|CAD|AUD|CHF|SEK|NOK|DKK|PLN|JPY|INR)\\b").containsMatchIn(text)

/**
 * The languages a shop is asked for (2.178): the phone's own first, like its browser sends, then English. Before, every
 * request said "American English", so a shop could answer with its American page and prices while you saved the wish
 * from its Dutch one. "nl-NL,nl;q=0.9,en;q=0.8".
 */
internal fun acceptLanguage(l: java.util.Locale = java.util.Locale.getDefault()): String {
    val lang = l.language.lowercase().takeIf { Regex("[a-z]{2,3}").matches(it) } ?: return "en-US,en;q=0.9"
    val country = l.country.uppercase().takeIf { Regex("[A-Z]{2}").matches(it) }
    val parts = ArrayList<String>()
    if (country != null) parts.add("$lang-$country")
    parts.add(if (parts.isEmpty()) lang else "$lang;q=0.9")
    if (lang != "en") parts.add("en;q=0.8")
    return parts.joinToString(",")
}

internal fun currencyOf(text: String, host: String): String = when {
    text.contains("£") || host.endsWith(".co.uk") -> "GBP"
    text.contains("€") || host.endsWith(".de") || host.endsWith(".fr") ||
        host.endsWith(".it") || host.endsWith(".es") || host.endsWith(".nl") -> "EUR"
    text.contains("₹") || host.endsWith(".in") -> "INR"
    text.contains("¥") || host.endsWith(".co.jp") -> "JPY"
    host.endsWith(".ca") -> "CAD"
    host.endsWith(".com.au") -> "AUD"
    host.endsWith(".com.mx") -> "MXN"
    text.contains("$") -> "USD"
    // Nothing on the page says (owner, 2.120): the currency of the phone's country, else the euro. Before, this was $.
    else -> localCurrency()
}

/** The currencies of the countries the phone (or browser) may be set to; other countries get the euro. Same list as web/parse.js. */
private val COUNTRY_CURRENCY: Map<String, String> =
    listOf("AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "HR", "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PT", "SI", "SK")
        .associateWith { "EUR" } + mapOf(
        "GB" to "GBP", "US" to "USD", "CA" to "CAD", "AU" to "AUD", "NZ" to "NZD", "JP" to "JPY", "CN" to "CNY",
        "KR" to "KRW", "IN" to "INR", "MX" to "MXN", "BR" to "BRL", "CH" to "CHF", "SE" to "SEK", "NO" to "NOK",
        "DK" to "DKK", "PL" to "PLN"
    )

/** The currency of the phone's country (Settings > Language and region), or the euro (owner, 2.120). */
fun countryCurrency(): String = COUNTRY_CURRENCY[java.util.Locale.getDefault().country.uppercase()] ?: "EUR"

/** The currency for new wishes: the one picked in App settings > Prices (2.122), else the phone country's. */
fun localCurrency(): String = CurrencyChoice.chosen.ifEmpty { countryCurrency() }

/** App settings > Prices > "Currency for new wishes" (owner, 2.122): "" is Automatic (the phone's country). */
object CurrencyChoice {
    var chosen by androidx.compose.runtime.mutableStateOf("")
        private set
    /** The choices besides Automatic. */
    val all = listOf("EUR", "GBP", "USD", "CHF", "SEK", "NOK", "DKK", "PLN", "CAD", "AUD", "NZD", "JPY", "CNY", "KRW", "INR", "MXN", "BRL")

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    fun load(c: Context) { chosen = prefs(c).getString("new_cur", "") ?: "" }
    fun set(c: Context, cur: String) { chosen = cur; prefs(c).edit().putString("new_cur", cur).apply() }
}

private val AMAZON_PRICE = listOf(
    "#corePriceDisplay_desktop_feature_div .priceToPay .a-offscreen",
    "#corePrice_feature_div .priceToPay .a-offscreen",
    "#corePriceDisplay_desktop_feature_div .a-price:not(.a-text-price) .a-offscreen",
    "#corePrice_feature_div .a-price:not(.a-text-price) .a-offscreen",
    "#apex_desktop .a-price:not(.a-text-price) .a-offscreen",
    "#priceblock_dealprice", "#priceblock_ourprice", "#price_inside_buy",
    "#tp_price_block_total_price_ww .a-offscreen",
    ".a-price:not(.a-text-price) .a-offscreen"
)

/** "Vraagprijs € 725.000", "Price: $19.99" and similar: a price word directly followed by a currency symbol. */
private val PRICE_WORD = Regex(
    "(?i)\\b(vraagprijs|huurprijs|prijs|price|preis|prix)\\b\\s*:?\\s*([€\$£])\\s?([0-9][0-9.,]*)"
)

/** Funda shows the asking price as "€ 725.000 k.k." */
private val EURO_THOUSANDS = Regex("€\\s?([0-9]{1,3}(?:\\.[0-9]{3})+)")

/** True for the titles that anti-bot pages use. */
internal fun looksBlocked(title: String): Boolean {
    val t = title.lowercase()
    return listOf(
        "robot check", "just a moment", "access denied", "attention required",
        "pardon our interruption", "captcha", "are you a human", "verify you are human"
    ).any { t.contains(it) }
}

/** The shop's own name in its web address: "link.marktplaats.nl" -> "marktplaats", "www.bol.com" -> "bol". */
internal fun shopWord(host: String): String {
    val parts = host.lowercase().removePrefix("www.").split('.').filter { it.isNotEmpty() }
    return if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull().orEmpty()
}

private val TITLE_SEP = Regex("\\s+[|\\-–—:·]\\s+")
private val ARTICLE_NO = Regex("\\s*[(\\[][^)\\]]*\\d{5,}[^)\\]]*[)\\]]")

/**
 * A wish name without the shop (owner, 6 November 2026): article numbers in brackets go ("(m2434550716)"), and so does
 * a part that names the shop, at the end or the start ("… : Amazon.nl: PC & Video Games", "… — Marktplaats",
 * "bol.com | …"). The shop is still on the card's shop line. Same rules as cleanTitle in web/parse.js.
 */
fun cleanTitle(title: String, host: String): String {
    val word = shopWord(host)
    var t = title.replace(ARTICLE_NO, "").replace(Regex("\\s+"), " ").trim()
    if (word.length >= 3) {
        while (true) {
            val seps = TITLE_SEP.findAll(t).toList()
            if (seps.isEmpty()) break
            val last = seps.last()
            val first = seps.first()
            when {
                t.substring(last.range.last + 1).lowercase().contains(word) -> t = t.substring(0, last.range.first).trim()
                t.substring(0, first.range.first).lowercase().contains(word) -> t = t.substring(first.range.last + 1).trim()
                else -> break
            }
        }
        // A name that is only the shop's name ("Amazon.nl") stays as it was.
    }
    return t.ifBlank { title.trim() }
}

/** Reads title, image, description and price out of a loaded page. Returns blocked=true for anti-bot pages. */
internal fun parsePage(d: Document, host: String, status: Int): Meta? {
    val isAmazon = host.contains("amazon.")
    val blocked = status in listOf(403, 429, 503) ||
        d.selectFirst("form[action*=validateCaptcha]") != null ||
        looksBlocked(d.title())
    if (blocked) return Meta("", "", "", null, "USD", true)

    fun m(vararg keys: String): String {
        for (k in keys) {
            // Not the price of another product's tile (sponsored, related).
            for (e in d.select("meta[property=\"$k\"], meta[name=\"$k\"], meta[itemprop=\"$k\"]")) {
                if (foreign(e, d.location())) continue
                val v = e.attr("content").trim()
                if (v.isNotEmpty()) return v
            }
        }
        return ""
    }

    var title = m("og:title", "twitter:title").ifEmpty { d.title() }
    if (isAmazon) {
        title = title.replace(Regex("^Amazon\\.[a-z.]+\\s*:\\s*"), "").replace(Regex("\\s+:\\s+[^:]+$"), "")
    }
    var image = d.selectFirst("meta[property=\"og:image\"], meta[name=\"twitter:image\"]")
        ?.absUrl("content").orEmpty()
    if (image.isEmpty() && isAmazon) {
        image = d.selectFirst("#landingImage, #imgBlkFront")?.let {
            it.attr("data-old-hires").ifEmpty { it.absUrl("src") }
        }.orEmpty()
    }

    val ld = ldProduct(d, d.location())
    // An error page (404 "not found", 410, 500…) is not the product (2.178): the prices on it belong to the shop's
    // suggestions, so none is read. The name and picture are still read, as before.
    val gone = status >= 400
    var price = if (gone) null else m("product:price:amount", "og:price:amount", "price").toNum()
    var cur = m("product:price:currency", "og:price:currency", "priceCurrency")
    // Did the page name the currency itself (see Meta.currencySure)?
    var sure = cur.isNotEmpty()
    if (price == null && isAmazon && !gone) {
        for (sel in AMAZON_PRICE) {
            val t = d.selectFirst(sel)?.text().orEmpty()
            val n = t.toNum()
            if (n != null && n > 0) { price = n; cur = currencyOf(t, host); sure = namesCurrency(t); break }
        }
    }
    // The product's own data (2.145): its own price, not the first "price" anywhere in the page (that could be a
    // product under "you may also like").
    if (price == null && ld?.price != null && !gone) {
        price = ld.price
        if (cur.isEmpty()) { cur = ld.currency; sure = cur.isNotEmpty() }
    }
    if (price == null && !gone) {
        price = d.select("[itemprop=price]").firstOrNull { !foreign(it, d.location()) }?.let { it.attr("content").ifEmpty { it.text() } }?.toNum()
    }
    if (price == null && ld == null && !gone) {
        // Product data that isn't valid JSON: the first "price" in it, as before 2.145.
        val raw = d.select("script[type=application/ld+json]").joinToString(" ") { it.data() }
        price = Regex("\"price\"\\s*:\\s*\"?([0-9][0-9.,]*)").find(raw)?.groupValues?.get(1)?.toNum()
        if (price != null && cur.isEmpty()) { cur = Regex("\"priceCurrency\"\\s*:\\s*\"([A-Z]{3})\"").find(raw)?.groupValues?.get(1).orEmpty(); sure = cur.isNotEmpty() }
    }
    if (price == null && !gone) {
        // The price the page shows (2.145; the owner chose this last try knowing it can now and then pick a wrong one).
        // It always stands next to a currency symbol or code.
        shownPrice(d, host)?.let { (n, c) -> price = n; if (cur.isEmpty()) { cur = c; sure = true } }
    }
    if (price == null && !gone) {
        // Last resort for sites that only print the price as text.
        val text = d.body().text()
        val hit = PRICE_WORD.find(text)
        if (hit != null) {
            price = hit.groupValues[3].toNum()
            if (price != null) { cur = currencyOf(hit.groupValues[2], host); sure = true }
        } else if (host.contains("funda.")) {
            val e = EURO_THOUSANDS.find(text)
            if (e != null) {
                price = e.groupValues[1].toNum()
                if (price != null) { cur = "EUR"; sure = true }
            }
        }
    }
    if (title.isBlank() && ld != null) title = ld.name
    if (image.isEmpty() && ld != null && ld.image.isNotEmpty()) image = try { java.net.URI(d.location()).resolve(ld.image).toString() } catch (e: Exception) { ld.image }
    val product = ld != null || price != null || m("og:type").lowercase().contains("product") ||
        d.selectFirst("[itemtype*=schema.org/Product]") != null
    return if (title.isBlank()) null
    else Meta(
        cleanTitle(title, host).take(120),
        m("og:description", "twitter:description", "description").take(200),
        image, price, cur.ifEmpty { currencyOf("", host) }, product = product, currencySure = sure && price != null
    )
}

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
