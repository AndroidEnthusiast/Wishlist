package com.wishlist.app

import org.jsoup.nodes.Document

// Reading a whole shop page: name, price, currency, picture, blocked (a part of price reading since the data
// rebuild, owner 19 November 2026; nothing in it changed). Same rules as web/parse.js (Rule 2).

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
    // The shop's own secure address first (Shopify writes og:image as http://, owner 16 November 2026: Baracuta's
    // picture stayed white, Android won't load http).
    var image = d.selectFirst("meta[property=\"og:image:secure_url\"], meta[property=\"og:image\"], meta[name=\"twitter:image\"]")
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
        secureImage(image), price, cur.ifEmpty { currencyOf("", host) }, product = product, currencySure = sure && price != null
    )
}
