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

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** evaluateJavascript hands back the result as a JSON string; this turns it back into plain text. */
private fun decodeJs(r: String?): String? {
    if (r == null || r == "null") return null
    return try {
        JSONTokener(r).nextValue() as? String
    } catch (e: Exception) {
        null
    }
}

private suspend fun pageHtml(wv: WebView): String? = withTimeoutOrNull(4000L) {
    suspendCancellableCoroutine<String?> { cont ->
        wv.evaluateJavascript("document.documentElement.outerHTML") { r ->
            if (cont.isActive) cont.resume(decodeJs(r))
        }
    }
}

private fun titleOf(html: String): String =
    Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1)?.trim().orEmpty()

/** A real product page has these; an anti-bot challenge page usually does not. */
private fun hasContent(html: String): Boolean =
    (html.contains("og:title") || html.contains("ld+json") || html.contains("<h1")) &&
        !looksBlocked(titleOf(html))

/**
 * Loads [url] in a hidden in-app browser, waits for the page to finish rendering (also lets simple
 * anti-bot checks finish), and returns the page HTML. Returns null if it never got a real page.
 */
@SuppressLint("SetJavaScriptEnabled")
suspend fun renderHtml(ctx: Context, url: String, timeoutMs: Long = 20000L): String? =
    withContext(Dispatchers.Main) {
        var wv: WebView? = null
        try {
            val w = WebView(ctx.applicationContext)
            wv = w
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.blockNetworkImage = true
            w.settings.userAgentString = MOBILE_UA
            w.webViewClient = WebViewClient()
            w.loadUrl(url)

            val start = SystemClock.elapsedRealtime()
            var best: String? = null
            var good = 0
            while (SystemClock.elapsedRealtime() - start < timeoutMs) {
                delay(1200L)
                val h = pageHtml(w)
                if (h != null && hasContent(h)) {
                    best = h
                    good++
                    if (good >= 2) break // same good page twice in a row: it has finished rendering
                } else {
                    good = 0
                }
            }
            best
        } catch (e: Exception) {
            null
        } finally {
            try {
                wv?.stopLoading()
                wv?.destroy()
            } catch (e: Exception) {
            }
        }
    }
