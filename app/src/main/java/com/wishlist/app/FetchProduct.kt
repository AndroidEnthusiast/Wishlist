package com.wishlist.app

import org.jsoup.nodes.Document

// The shop's own product data and the price shown on its page, leaving out other products (a part of price reading
// since the data rebuild, owner 19 November 2026; nothing in it changed). Same rules as web/parse.js (Rule 2).

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
