package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Text rules of price reading: cleaned links, numbers, currencies and your currency choice, names, blocked pages (a
// part of price reading since the data rebuild, owner 19 November 2026; nothing in it changed). Same rules as
// web/parse.js (Rule 2).

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
