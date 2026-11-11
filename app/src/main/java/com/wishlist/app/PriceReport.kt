package com.wishlist.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

/**
 * "Price is wrong?" on a wish's price page (ROADMAP R4; owner, 10 November 2026). The product reader was only ever
 * checked on hand-made pages, and Claude's sessions can't open most shops. This saves the shop's page exactly as the
 * price check on this phone reads it, with a first line that says what was read, to a file, and opens Android's share
 * sheet so the person can send it along. Nothing is sent by itself. The page then goes into
 * `tests/shared/same-answers.json` → `pages`, with the fix in both readers.
 *
 * The page is read the way the price check reads it: the plain request first; the hidden browser only when that gave
 * no price. The hidden browser shares its sign-ins with the in-app shop, so such a page can show the person's name:
 * the question before saving says so.
 */
object PriceReport {
    /** The first lines of the file: what was asked and what the reader made of it. Kept free of "--" so it stays one comment. */
    fun header(w: Wish, via: String, status: Int, m: Meta?, app: String, lang: String): String {
        fun clean(s: String) = s.replace("--", "- -").replace("\n", " ").take(500)
        val read = when {
            m == null -> "nothing (no title on the page)"
            m.blocked -> "blocked (a bot check or an error page)"
            m.price == null -> "no price" + (if (m.product) ", a product page" else ", not seen as a product page")
            else -> "${m.price} ${m.currency}" + (if (m.currencySure) " (the page named the currency)" else " (currency guessed)")
        }
        return "<!-- Wishlist price report\n" +
            "link: ${clean(w.url)}\n" +
            "on the wish: ${w.price ?: "no price"} ${w.currency}\n" +
            "read from this page: $read\n" +
            "how: $via, status $status, languages asked: $lang\n" +
            "app: $app\n" +
            "-->\n"
    }

    /** Fetches the page as the price check does and writes the report file. Null when the page couldn't be fetched at all. */
    suspend fun make(c: Context, w: Wish): File? = withContext(Dispatchers.IO) {
        val app = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
        var html: String? = null
        var via = "plain request"
        var status = 0
        var meta: Meta? = null
        try {
            val res = shopRequest(w.url)
            status = res.statusCode()
            html = res.body()
            meta = parsePage(Jsoup.parse(html, res.url().toString()), res.url().host.orEmpty(), status)
        } catch (e: Exception) {
        }
        val m = meta
        if (m == null || m.blocked || m.price == null) {
            val shown = try { renderHtml(c, w.url) } catch (e: Exception) { null }
            if (shown != null) {
                val host = try { java.net.URI(w.url).host.orEmpty() } catch (e: Exception) { "" }
                val second = try { parsePage(Jsoup.parse(shown, w.url), host, 200) } catch (e: Exception) { null }
                // The price check takes the hidden browser's page only when it isn't a bot check; the report keeps
                // whichever page the check ended up with.
                if (second != null && !second.blocked) { html = shown; via = "hidden browser"; status = 200; meta = second }
            }
        }
        val page = html ?: return@withContext null
        try {
            val dir = File(c.cacheDir, "share").apply { mkdirs() }
            val host = try { java.net.URI(w.url).host.orEmpty().removePrefix("www.").replace(Regex("[^a-z0-9.-]"), "") } catch (e: Exception) { "" }
            val f = File(dir, "price-report-" + host.ifEmpty { "shop" } + ".html")
            f.writeText(header(w, via, status, meta, app, acceptLanguage()) + page)
            f
        } catch (e: Exception) {
            null
        }
    }

    /** Opens Android's share sheet with the file. */
    fun share(c: Context, f: File): Boolean = try {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wishlist price report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(send, tr("Send the shop’s page")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
