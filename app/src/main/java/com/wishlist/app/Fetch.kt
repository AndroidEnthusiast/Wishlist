package com.wishlist.app

import android.content.Context
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
    val blocked: Boolean = false
)

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

internal fun currencyOf(text: String, host: String): String = when {
    text.contains("£") || host.endsWith(".co.uk") -> "GBP"
    text.contains("€") || host.endsWith(".de") || host.endsWith(".fr") ||
        host.endsWith(".it") || host.endsWith(".es") || host.endsWith(".nl") -> "EUR"
    text.contains("₹") || host.endsWith(".in") -> "INR"
    text.contains("¥") || host.endsWith(".co.jp") -> "JPY"
    host.endsWith(".ca") -> "CAD"
    host.endsWith(".com.au") -> "AUD"
    host.endsWith(".com.mx") -> "MXN"
    else -> "USD"
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

/** Reads title, image, description and price out of a loaded page. Returns blocked=true for anti-bot pages. */
internal fun parsePage(d: Document, host: String, status: Int): Meta? {
    val isAmazon = host.contains("amazon.")
    val blocked = status in listOf(403, 429, 503) ||
        d.selectFirst("form[action*=validateCaptcha]") != null ||
        looksBlocked(d.title())
    if (blocked) return Meta("", "", "", null, "USD", true)

    fun m(vararg keys: String): String {
        for (k in keys) {
            val v = d.selectFirst("meta[property=\"$k\"], meta[name=\"$k\"], meta[itemprop=\"$k\"]")
                ?.attr("content")?.trim().orEmpty()
            if (v.isNotEmpty()) return v
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

    var price = m("product:price:amount", "og:price:amount", "price").toNum()
    var cur = m("product:price:currency", "og:price:currency", "priceCurrency")
    if (price == null && isAmazon) {
        for (sel in AMAZON_PRICE) {
            val t = d.selectFirst(sel)?.text().orEmpty()
            val n = t.toNum()
            if (n != null && n > 0) { price = n; cur = currencyOf(t, host); break }
        }
    }
    if (price == null) {
        val ld = d.select("script[type=application/ld+json]").joinToString(" ") { it.data() }
        price = Regex("\"price\"\\s*:\\s*\"?([0-9][0-9.,]*)").find(ld)?.groupValues?.get(1)?.toNum()
        if (cur.isEmpty()) {
            cur = Regex("\"priceCurrency\"\\s*:\\s*\"([A-Z]{3})\"").find(ld)?.groupValues?.get(1).orEmpty()
        }
    }
    if (price == null) {
        price = d.selectFirst("[itemprop=price]")?.let { it.attr("content").ifEmpty { it.text() } }?.toNum()
    }
    if (price == null) {
        // Last resort for sites that only print the price as text.
        val text = d.body().text()
        val hit = PRICE_WORD.find(text)
        if (hit != null) {
            price = hit.groupValues[3].toNum()
            if (price != null) cur = currencyOf(hit.groupValues[2], host)
        } else if (host.contains("funda.")) {
            val e = EURO_THOUSANDS.find(text)
            if (e != null) {
                price = e.groupValues[1].toNum()
                if (price != null) cur = "EUR"
            }
        }
    }
    return if (title.isBlank()) null
    else Meta(
        title.take(120),
        m("og:description", "twitter:description", "description").take(200),
        image, price, cur.ifEmpty { currencyOf("", host) }
    )
}

/**
 * Reads title, image, description and price from a product page. Returns blocked=true if the store refused.
 * If a plain request is blocked or finds no price and [ctx] is given, the page is loaded in a hidden browser
 * (which runs JavaScript like Chrome) and read again. Pass browser=false to skip that slower step.
 */
suspend fun fetchMeta(url: String, ctx: Context? = null, browser: Boolean = true): Meta? =
    withContext(Dispatchers.IO) {
        var first: Meta? = null
        try {
            val res = Jsoup.connect(url).userAgent(UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(15000).followRedirects(true).ignoreHttpErrors(true).execute()
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
