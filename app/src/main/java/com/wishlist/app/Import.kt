package com.wishlist.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import kotlin.coroutines.resume

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
     * True when shared text should open Import rather than Add a wish: a list link of a known site, or a list (three or
     * more lines with words, or two or more links), as a notes app shares it.
     */
    fun looksLikeList(text: String): Boolean {
        val links = Regex("https?://\\S+").findAll(text).map { it.value }.toList()
        if (links.size == 1 && isListLink(links[0])) return true
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

/**
 * The Import reader script: the newest of the built-in copy (assets/import/reader.js) and the one on our web pages
 * (downloaded at most every 12 hours, kept on the phone), so a fixed reader reaches every phone within a day.
 * Only our own site over https; the script only reads the list page it runs in (no bridge to the app).
 */
object ImportReaders {
    const val URL = "https://androidenthusiast.github.io/Wishlist/import/reader.js"
    private const val EVERY = 12 * 60 * 60 * 1000L

    fun versionOf(src: String): Int = Regex("var VERSION = (\\d+);").find(src)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun builtIn(c: Context): String = try {
        c.assets.open("import/reader.js").bufferedReader().use { it.readText() }
    } catch (e: Exception) { "" }

    private fun file(c: Context) = java.io.File(c.filesDir, "import-reader.js")

    /** The script to run: the downloaded one when it is newer than the built-in one. */
    suspend fun script(c: Context): String = withContext(Dispatchers.IO) {
        refresh(c)
        val mine = builtIn(c)
        val got = try { file(c).takeIf { it.exists() }?.readText().orEmpty() } catch (e: Exception) { "" }
        if (got.isNotEmpty() && versionOf(got) > versionOf(mine)) got else mine
    }

    private fun refresh(c: Context) {
        val p = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        val last = p.getLong("import_reader_at", 0L)
        if (System.currentTimeMillis() - last < EVERY || Crash.onComputer) return
        try {
            val res = org.jsoup.Jsoup.connect(URL).ignoreContentType(true).timeout(6000).maxBodySize(512 * 1024).execute()
            val body = res.body()
            if (res.statusCode() == 200 && body.contains("WLImport") && versionOf(body) > 0) file(c).writeText(body)
            p.edit().putLong("import_reader_at", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            // Offline: the copy on the phone (or the built-in one) is used.
        }
    }

    /** Runs the reader on [w]'s page; the reader's JSON, or null. */
    suspend fun run(w: WebView, script: String): String? = withContext(Dispatchers.Main) {
        val code = "(function(){try{" + script + "\n;return JSON.stringify(window.WLImport.read(document, location.href));}catch(e){return null;}})()"
        withTimeoutOrNull(6000L) {
            suspendCancellableCoroutine<String?> { cont ->
                w.evaluateJavascript(code) { r ->
                    val s = if (r == null || r == "null") null else try { JSONTokener(r).nextValue() as? String } catch (e: Exception) { null }
                    if (cont.isActive) cont.resume(s)
                }
            }
        }
    }

    /**
     * Opens [url] in a hidden browser and reads its wishes: again every 1.5 seconds while the page fills in (and
     * scrolled down, so lists that load more while scrolling show them), until the count stays the same twice or
     * [timeoutMs] is over. Null when the page couldn't be read at all.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun readHidden(c: Context, url: String, timeoutMs: Long = 30000L): ImportRead? {
        val script = script(c)
        if (script.isEmpty()) return null
        return withContext(Dispatchers.Main) {
            var wv: WebView? = null
            try {
                val w = WebView(c.applicationContext)
                wv = w
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.allowFileAccess = false
                w.settings.allowContentAccess = false
                w.settings.blockNetworkImage = true
                w.webViewClient = WebViewClient()
                w.loadUrl(url)
                val start = SystemClock.elapsedRealtime()
                var best: ImportRead? = null
                var same = 0
                while (SystemClock.elapsedRealtime() - start < timeoutMs) {
                    delay(1500L)
                    val r = Import.fromReader(run(w, script), w.url ?: url) ?: continue
                    if (best != null && r.wishes.size == best.wishes.size && r.wishes.isNotEmpty()) same++ else same = 0
                    if (best == null || r.wishes.size >= best.wishes.size) best = r
                    if (same >= 2) break
                    w.evaluateJavascript("window.scrollTo(0, document.body ? document.body.scrollHeight : 0)", null)
                }
                best
            } catch (e: Exception) {
                null
            } finally {
                try { wv?.stopLoading(); wv?.destroy() } catch (e: Exception) { }
            }
        }
    }
}

/**
 * The fill-in after an import (owner: wishes in at once, details afterwards; "it can take a minute"): for each
 * imported wish with a shop link, the shop's page is read once, slowly (one at a time, a pause between), and only what
 * is missing is filled: the name, the price (only when it has none) and the photo. The shop's own photo replaces the
 * list page's small one (owner: sharper photos). Waiting wishes are kept on the phone, so a restart or being offline
 * only delays them. Never touches a wish you changed or removed meanwhile: it is looked up again just before.
 */
object ImportFill {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false
    private const val KEY = "import_fill"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    private fun waiting(c: Context): JSONObject = try { JSONObject(prefs(c).getString(KEY, null) ?: "{}") } catch (e: Exception) { JSONObject() }

    @Synchronized
    fun queue(c: Context, listId: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        val o = waiting(c)
        val a = o.optJSONArray(listId) ?: JSONArray()
        ids.forEach { a.put(it) }
        o.put(listId, a)
        prefs(c).edit().putString(KEY, o.toString()).apply()
        start(c)
    }

    @Synchronized
    private fun next(c: Context): Pair<String, Long>? {
        val o = waiting(c)
        for (k in o.keys().asSequence().toList()) {
            val a = o.optJSONArray(k)
            if (a == null || a.length() == 0) { o.remove(k); continue }
            prefs(c).edit().putString(KEY, o.toString()).apply()
            return k to a.getLong(0)
        }
        prefs(c).edit().putString(KEY, o.toString()).apply()
        return null
    }

    @Synchronized
    private fun done(c: Context, listId: String, id: Long) {
        val o = waiting(c)
        val a = o.optJSONArray(listId) ?: return
        val left = JSONArray()
        for (i in 0 until a.length()) if (a.optLong(i) != id) left.put(a.optLong(i))
        if (left.length() == 0) o.remove(listId) else o.put(listId, left)
        prefs(c).edit().putString(KEY, o.toString()).apply()
    }

    /** Starts (or resumes, at app start) the fill-in, when there is anything waiting. */
    fun start(c: Context) {
        val ctx = c.applicationContext
        synchronized(this) { if (running) return; running = true }
        scope.launch {
            try {
                var misses = 0
                while (true) {
                    val (listId, id) = next(ctx) ?: break
                    if (listId !in Repo.mineIds) { done(ctx, listId, id); continue }
                    val w = Repo.mine(ctx, listId).wishes.firstOrNull { it.id == id }
                    if (w == null || w.url.isBlank()) { done(ctx, listId, id); continue }
                    val meta = try { fetchMeta(w.url, ctx) } catch (e: Exception) { null }
                    if (meta == null) {
                        // Offline or the shop didn't answer: try again later (next start), at most a few in a row now.
                        // A wish whose shop never answers is given up after 5 tries (it keeps what it has).
                        done(ctx, listId, id)
                        if (tried(ctx, id) < 5) queueLast(ctx, listId, id)
                        if (++misses >= 3) break
                        delay(3000L)
                        continue
                    }
                    misses = 0
                    withContext(Dispatchers.Main) { fill(ctx, listId, id, meta) }
                    done(ctx, listId, id)
                    delay(1500L)
                }
            } finally {
                synchronized(this@ImportFill) { running = false }
            }
        }
    }

    /** Counts a failed try of wish [id] and returns how many there were. */
    @Synchronized
    private fun tried(c: Context, id: Long): Int {
        val o = try { JSONObject(prefs(c).getString("import_fill_tries", null) ?: "{}") } catch (e: Exception) { JSONObject() }
        val n = o.optInt(id.toString(), 0) + 1
        o.put(id.toString(), n)
        prefs(c).edit().putString("import_fill_tries", o.toString()).apply()
        return n
    }

    @Synchronized
    private fun queueLast(c: Context, listId: String, id: Long) {
        val o = waiting(c)
        val a = o.optJSONArray(listId) ?: JSONArray()
        a.put(id)
        o.put(listId, a)
        prefs(c).edit().putString(KEY, o.toString()).apply()
    }

    /** Fills only what is missing (see [filled]); the wish as it is now, so nothing you changed is overwritten. */
    private fun fill(c: Context, listId: String, id: Long, meta: Meta) {
        if (listId !in Repo.mineIds) return
        var priced: Double? = null
        Repo.updateMine(c, listId) { ws ->
            ws.map { w ->
                if (w.id != id) w else filled(w, meta).also { n -> if (w.price == null && n.price != null) priced = n.price }
            }
        }
        priced?.let { History.record(c, id, it) }
    }

    /**
     * [w] with what the shop's page adds: a name when it has none, a price when it has none (never a changed price:
     * Rule 8), and the shop's own photo (bigger than a list page's thumbnail; only when the page is a product page).
     */
    fun filled(w: Wish, meta: Meta): Wish {
        if (meta.blocked) return w
        val title = w.title.ifBlank { meta.title.take(200) }
        val price = w.price ?: meta.price
        val currency = if (w.price == null && meta.price != null) meta.currency else w.currency
        val image = if (meta.image.startsWith("http") && (w.image.isBlank() || meta.product)) secureImage(meta.image) else w.image
        return w.copy(title = title, price = price, currency = currency, image = image)
    }
}
