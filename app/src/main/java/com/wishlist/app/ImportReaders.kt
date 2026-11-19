package com.wishlist.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

// Moved here from Import.kt whole in the data rebuild (owner, 19 November 2026); nothing in it changed.

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
