package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate

/*
 * Import (owner, 18 November 2026; docs/decisions/import.md): a list from GoWish, Giftful, Amazon, bol.com, Etsy,
 * Pinterest, any shop page, or text from a notes app, brought into one of your own lists in one go.
 *  - A link is read in a hidden in-app browser by the Import reader (web/import/reader.js, built in as an asset and
 *    downloaded fresh from our web pages by [ImportReaders], so a fix reaches phones without a new app).
 *  - Text: each line is a wish ([Import.parseText]).
 *  - Links are unwrapped to the real shop page ([Import.unwrap]), so price-drop alerts work from day one.
 *  - Wishes go in at once; missing photos and prices, and the shop's own bigger photo, fill in afterwards
 *    ([ImportFill]), also after a restart or when the phone was offline. A price is only filled when the wish has none
 *    (Rule 8: a price check never changes a price from one reading; this is a new wish's first price, as in Add a wish).
 *  - Own lists only, never a collab wishlist. App only (the browser can't read these sites).
 *
 * Since the data rebuild (owner, 19 November 2026): this file holds the rules; [ImportReaders] is in ImportReaders.kt
 * and [ImportFill] in ImportFill.kt, moved whole. ImportSameTest proves they do exactly what they did before.
 */

/** One wish found by Import, before it is a [Wish]. */
data class Found(
    val title: String,
    val url: String,
    val image: String,
    val price: Double?,
    val currency: String,
    val love: Boolean = false
)

/** What a list page or a text gave: the list's name and date (YYYY-MM-DD, when there is one) and its wishes. */
data class ImportRead(val name: String, val date: String, val site: String, val wishes: List<Found>, val version: Int = 0)

object Import {
    /** At most this many wishes in one import (a page with more is cut off: said on the check screen). */
    const val MAX = 300

    /** The sites shown on the Import sheet, with a page their icon is looked up for (Google's icon service). */
    val SITES: List<Shop> = listOf(
        Shop("GoWish", "https://gowish.com/"),
        Shop("Giftful", "https://giftful.com/"),
        Shop("Amazon", "https://www.amazon.com/"),
        Shop("bol.com", "https://www.bol.com/"),
        Shop("Etsy", "https://www.etsy.com/"),
        Shop("Pinterest", "https://www.pinterest.com/")
    )

    /** "gowish" → "GoWish" (the reader's site key, as said on screen), "" for any other page. */
    fun siteName(site: String): String = when (site) {
        "gowish" -> "GoWish"; "giftful" -> "Giftful"; "amazon" -> "Amazon"; "bol" -> "bol.com"
        "etsy" -> "Etsy"; "pinterest" -> "Pinterest"; else -> ""
    }

    /** How to find the link to share, per site (tapping a site on the Import sheet). */
    fun tip(name: String): String = when (name) {
        "GoWish" -> tr("In GoWish, open your wishlist, tap Share and copy the link.")
        "Giftful" -> tr("In Giftful, open your list, tap Share and copy the link.")
        "Amazon" -> tr("On Amazon, open Your Lists, pick the list, set it to Public or Shared, then tap Send list to others and copy the link.")
        "bol.com" -> tr("On bol.com, open your wish list, tap Share and copy the link.")
        "Etsy" -> tr("On Etsy, open Favourites, make the list public, then tap Share and copy the link.")
        "Pinterest" -> tr("In Pinterest, open your board, tap Share and copy the link. Secret boards can't be read.")
        else -> ""
    }

    /** The reader's site key of a page ("gowish", "amazon", …), "" for any other page. Same list as reader.js. */
    fun siteKey(url: String): String {
        val h = hostOf(url)
        return when {
            h.startsWith("gowish.") || h.endsWith(".gowish.com") -> "gowish"
            h == "giftful.com" || h.endsWith(".giftful.com") -> "giftful"
            h.startsWith("amazon.") || h.contains(".amazon.") -> "amazon"
            h == "bol.com" || h.endsWith(".bol.com") -> "bol"
            h == "etsy.com" || h.endsWith(".etsy.com") -> "etsy"
            h.startsWith("pinterest.") || h.contains(".pinterest.") -> "pinterest"
            else -> ""
        }
    }

    private fun hostOf(u: String): String = try { URI(u.trim()).host.orEmpty().lowercase().removePrefix("www.") } catch (e: Exception) { "" }
    private fun pathOf(u: String): String = try { URI(u.trim()).path.orEmpty().lowercase() } catch (e: Exception) { "" }

    /**
     * True when [url] is a wish list of a site we know (so sharing it into the app opens Import, not Add a wish):
     * GoWish, Giftful, Amazon wish lists, bol.com wish lists, Etsy favourites or lists, a Pinterest board.
     */
    fun isListLink(url: String): Boolean {
        val h = hostOf(url)
        val p = pathOf(url)
        return when {
            h.startsWith("gowish.") || h.endsWith(".gowish.com") -> p.length > 1
            h == "giftful.com" || h.endsWith(".giftful.com") -> p.length > 1
            h.contains("amazon.") -> p.contains("/wishlist") || p.contains("/registry/")
            h.endsWith("bol.com") -> p.contains("verlanglijst") || p.contains("wishlist")
            h.endsWith("etsy.com") -> p.contains("/favorites") || p.contains("/favourites") || p.contains("/lists")
            h.contains("pinterest.") -> {
                val parts = p.trim('/').split('/').filter { it.isNotEmpty() }
                // pinterest.com/<person>/<board>/ (not a single pin, not a search or idea page).
                parts.size == 2 && parts[0] !in setOf("pin", "search", "ideas", "today", "explore", "business")
            }
            else -> false
        }
    }

    /**
     * True when shared text should open Import rather than Add a wish: a list link of a known site, two or more links
     * (a list from a notes app), or three or more lines without any link. One shop link with words around it ("I just
     * found this on AliExpress: … https://a.aliexpress.com/…", owner 18 November 2026) is one wish: Add a wish.
     */
    fun looksLikeList(text: String): Boolean {
        val links = Regex("https?://\\S+").findAll(text).map { it.value }.toList()
        if (links.size == 1) return isListLink(links[0])
        if (links.size >= 2) return true
        return text.lines().count { it.any(Char::isLetterOrDigit) } >= 3
    }

    private val WRAP_KEYS = listOf("url", "u", "murl", "target", "dest", "destination", "redirect", "redirect_url", "redirecturl", "link", "to", "r", "ued", "deeplink")
    private val TRACKING = Regex("^(utm_[a-z]+|ref|ref_|tag|affid|aff_id|affiliate|clickid|gclid|fbclid|irclickid|ranmid|ransiteid|ranEAID|srsltid)$", RegexOption.IGNORE_CASE)

    /**
     * The real shop page behind a link (owner: "real shop links"): a referral or tracking redirect
     * (…/go?url=https%3A%2F%2Fshop…) is unwrapped, tracking parameters are dropped (utm_…, tag, ref…), Amazon and Etsy
     * links are trimmed as everywhere ([cleanUrl]). Only http(s) comes out; anything else is "".
     */
    fun unwrap(url: String): String {
        var u = url.trim()
        repeat(3) {
            val q = try { URI(u).rawQuery } catch (e: Exception) { null } ?: return@repeat
            val inner = q.split('&').mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val k = kv.substring(0, i).lowercase()
                if (k !in WRAP_KEYS) return@mapNotNull null
                val v = try { URLDecoder.decode(kv.substring(i + 1), "UTF-8") } catch (e: Exception) { "" }
                v.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            }.firstOrNull() ?: return@repeat
            u = inner
        }
        if (!(u.startsWith("http://", true) || u.startsWith("https://", true))) return ""
        u = dropTracking(u)
        return cleanUrl(u) ?: u
    }

    private fun dropTracking(u: String): String {
        val qi = u.indexOf('?')
        if (qi < 0) return u
        val hash = u.indexOf('#', qi).let { if (it < 0) u.length else it }
        val kept = u.substring(qi + 1, hash).split('&').filter { kv -> kv.isNotEmpty() && !TRACKING.matches(kv.substringBefore('=')) }
        return u.substring(0, qi) + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + u.substring(hash)
    }

    /** The same shop page, however it is written (http/https, www, a slash at the end, letter case of the host). */
    fun linkKey(url: String): String {
        val u = unwrap(url).ifEmpty { url.trim() }
        return u.replace(Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE), "").let { s ->
            val slash = s.indexOf('/')
            if (slash < 0) s.lowercase() else s.substring(0, slash).lowercase() + s.substring(slash)
        }.trimEnd('/')
    }

    /** A price as written ("€ 39,99", "EUR 39.99", "£12.50") → number and currency. [host]: the page it came from. */
    fun price(text: String, host: String): Pair<Double, String>? {
        val t = text.trim()
        if (t.isEmpty() || !t.any(Char::isDigit)) return null
        val n = t.toNum()?.takeIf { it > 0 && it < 10_000_000 } ?: return null
        val code = Regex("\\b(EUR|GBP|USD|CAD|AUD|CHF|SEK|NOK|DKK|PLN|JPY|INR)\\b", RegexOption.IGNORE_CASE).find(t)?.value?.uppercase()
        return n to (code ?: currencyOf(t, host))
    }

    private val PRICE_IN_TEXT = Regex(
        "(?:€|£|\\$|\\b(?:EUR|GBP|USD)\\b)\\s?\\d[\\d.,]*|\\d[\\d.,]*\\s?(?:€|£|\\$|\\b(?:EUR|GBP|USD)\\b)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Text from a notes app (owner): each line is a wish; a link in the line is its shop page (photo and price are
     * fetched afterwards); a written price ("Lego set €39") becomes the price; bullets, dashes, tick boxes and
     * numbering are removed; empty lines are skipped. A line that is only a link gets its name from the shop.
     */
    fun parseText(text: String): List<Found> = text.lines().mapNotNull { raw ->
        var line = raw.trim()
            .replace(Regex("^(?:[-*•·▪◦‣–—☐☑✓✔□■]|\\[[ xX]?\\]|\\(?\\d{1,3}[.)]|\\d{1,3}\\s*-)\\s*"), "")
            .replace(Regex("^\\[[ xX]?\\]\\s*"), "")
            .trim()
        if (line.isEmpty() || !line.any(Char::isLetterOrDigit)) return@mapNotNull null
        val link = Regex("https?://\\S+").find(line)?.value?.trimEnd('.', ',', ')', ']', '>')
        if (link != null) line = line.replace(Regex("https?://\\S+"), " ")
        val p = PRICE_IN_TEXT.find(line)
        val money = p?.let { price(it.value, link?.let(::hostOf).orEmpty()) }
        if (p != null && money != null) line = line.removeRange(p.range)
        val title = line.replace(Regex("\\s+"), " ").trim().trim('-', ':', '–', '—', ',', '|').trim().take(200)
        val url = link?.let(::unwrap).orEmpty()
        if (title.isEmpty() && url.isEmpty()) return@mapNotNull null
        Found(title, url, "", money?.first, money?.second ?: localCurrency())
    }.take(MAX)

    /**
     * What the reader handed back (JSON from web/import/reader.js), checked: only http(s) links and pictures, short
     * titles, at most [MAX] wishes. Never trusted as HTML: it only ever becomes text on screen.
     */
    fun fromReader(json: String?, pageUrl: String): ImportRead? {
        val o = try { JSONObject(json ?: return null) } catch (e: Exception) { return null }
        val host = hostOf(pageUrl)
        val arr = o.optJSONArray("wishes") ?: JSONArray()
        val list = ArrayList<Found>()
        for (i in 0 until minOf(arr.length(), MAX)) {
            val w = arr.optJSONObject(i) ?: continue
            val url = w.optString("url").let { if (it.startsWith("http", true)) unwrap(it) else "" }
            val image = w.optString("image").takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }?.let(::secureImage).orEmpty()
            val title = w.optString("title").replace(Regex("\\s+"), " ").trim().take(200)
            if (title.isEmpty() && url.isEmpty()) continue
            val money = price(w.optString("price"), hostOf(url).ifEmpty { host })
            list.add(Found(title, url, image, money?.first, money?.second ?: localCurrency(), w.optBoolean("love", false)))
        }
        val date = o.optString("date").takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }.orEmpty()
        return ImportRead(cleanName(o.optString("name")), date, o.optString("site"), list, o.optInt("version", 0))
    }

    /** A list's name from a page title: without " | GoWish", " - Amazon.nl" and the like. */
    fun cleanName(s: String): String = s.replace(Regex("\\s+"), " ").trim()
        .replace(Regex("\\s*[|·–—-]\\s*(GoWish|Giftful|Amazon[^|]*|bol\\.com|Etsy[^|]*|Pinterest)\\s*$", RegexOption.IGNORE_CASE), "")
        .trim().take(60)

    /** The indexes of [found] that are already on [existing] (same shop link): shown unticked, "Already on this list". */
    fun duplicates(found: List<Found>, existing: List<Wish>): Set<Int> {
        val have = existing.filter { it.bin == 0L && it.url.isNotBlank() }.map { linkKey(it.url) }.toSet()
        val seen = HashSet<String>()
        return found.indices.filter { i ->
            val u = found[i].url
            if (u.isBlank()) false else {
                val k = linkKey(u)
                k in have || !seen.add(k)
            }
        }.toSet()
    }

    /**
     * The wishes to add, newest first in the original's order (the first one gets the newest id). Ids are the time
     * counted back from [now], skipping any id in [taken], so they never meet a wish made a moment later.
     */
    fun toWishes(found: List<Found>, now: Long, taken: Set<Long>): List<Wish> {
        var id = now
        return found.map { f ->
            while (id in taken) id--
            val w = Wish(
                id = id, url = f.url, title = f.title, desc = "", image = f.image, price = f.price, oldPrice = null,
                currency = f.currency, category = "", star = f.love
            )
            id--
            w
        }
    }

    /** The original's date as the new list's occasion ("own", with the list's name), when it is still to come. */
    fun occasionOf(date: String, name: String, today: LocalDate = LocalDate.now()): Occasion? {
        val d = try { LocalDate.parse(date) } catch (e: Exception) { return null }
        if (d.isBefore(today)) return null
        return Occasion("own", name.take(Occasions.NAME_MAX).ifBlank { tr("Occasion") }, date)
    }

    /**
     * Puts the chosen wishes into list [listId] (one of your own) and remembers them for the fill-in afterwards.
     * Returns how many were added.
     */
    fun add(c: Context, listId: String, found: List<Found>): Int {
        if (found.isEmpty() || listId !in Repo.mineIds) return 0
        val taken = Repo.allMine(c).flatMap { it.wishes }.map { it.id }.toSet() + Repo.myBin.map { it.id }
        val wishes = toWishes(found, System.currentTimeMillis(), taken)
        Repo.updateMine(c, listId) { ws -> wishes + ws }
        for (w in wishes) w.price?.let { History.record(c, w.id, it) }
        ImportFill.queue(c, listId, wishes.filter { it.url.isNotBlank() }.map { it.id })
        return wishes.size
    }

    /** A quiet report when an import found nothing (owner: the site, the reader version and the count only). */
    class NothingFound(site: String, version: Int) : Exception("Import found 0 wishes: site=${site.ifEmpty { "other" }} reader=v$version")

    fun reportNothing(site: String, version: Int) = Crash.report(NothingFound(site, version))
}
