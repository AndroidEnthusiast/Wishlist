package com.wishlist.app

import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import kotlin.coroutines.resume

/*
 * The state holders of My shops (the rebuild, owner 19 November 2026; MAP "How a screen is built"): [ShopsModel] for
 * the shops page (ShopsScreen.kt) and [ShopBrowserState] for a shop open inside the app (ShopBrowser.kt). The screens
 * only draw; your shops are kept by Shops (Shops.kt).
 */

/** The shops page: which shop or search is open, removing, Add shop. */
@Stable
internal class ShopsModel(
    private val ctx: Context, private val scope: CoroutineScope, private val snack: SnackbarHostState,
    private val online: () -> Boolean = { pretendOnline ?: isOnline(ctx) }
) {
    var open by mutableStateOf<Shop?>(null)
    // Search the shops (owner, 22 November 2026): what was searched, while its results are open.
    var searched by mutableStateOf<String?>(null)
    // Search tapped while offline: "You're offline…" with Try again; your words stay in the box (owner).
    var offline by mutableStateOf(false)
    var query by mutableStateOf("")
    var adding by mutableStateOf(false)
    var editing by mutableStateOf(false)
    // The short pulse of every tile when the ✕ marks appear.
    var pulses by mutableStateOf(0)

    fun startEditing() { if (!editing) { editing = true; pulses++ } }

    /** A shop opened: it moves to the front on Home. */
    fun openShop(s: Shop) { open = s; Shops.opened(ctx, s) }

    /**
     * Search the shops (owner, 22 November 2026): the first shop of the search row opens with its own results for
     * [q]; the row in the browser switches to the others. Offline: a message with Try again instead.
     */
    fun search(q: String) {
        val words = q.trim()
        if (words.isEmpty()) return
        if (!online()) { offline = true; return }
        offline = false
        searched = words
        val first = Shops.searchShops.first()
        open = Shop(first.shop.name, first.url(words))
    }

    fun close() { open = null; searched = null }

    /** ✕ on a tile: the shop goes at once, with Undo. */
    fun removeShop(s: Shop) {
        val at = Shops.remove(ctx, s)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showToast(tr("%s removed", s.name), tr("Undo"))
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) Shops.putBack(ctx, s, at)
        }
    }

    /**
     * Add shop: null when it was added; else the name of the shop you already have at that address, or "" when it
     * isn't a web address.
     */
    fun addShop(name: String, address: String): String? {
        Shops.existing(ctx, address)?.let { return it.name }
        return if (Shops.add(ctx, name, address) != null) null else ""
    }
}

/** Finds the product picture on the page on screen (see [ShopBrowserState.add]). Returns JSON: ld, ogurl, img. */
private const val PICTURE_JS = """(function(){
  // The FIRST big picture in the page (owner, 2.118: the first product photo is the clean one; later ones show
  // dimensions or details), in the upper part of the page, also when a gallery slide is off to the side.
  var best='';
  var imgs=document.querySelectorAll('img');
  for(var k=0;k<imgs.length&&!best;k++){
    var i=imgs[k], r=i.getBoundingClientRect();
    if(r.width<150||r.height<150) continue;
    if(r.top+scrollY>innerHeight*2) continue;
    var s=i.currentSrc||i.src||''; if(!/^https?:/.test(s)) continue;
    best=s;
  }
  var ld='';
  document.querySelectorAll('script[type="application/ld+json"]').forEach(function(sc){
    try{
      var j=JSON.parse(sc.textContent); var all=[].concat(j['@graph']||j);
      all.forEach(function(o){
        if(ld||!o||!/Product/.test(String(o['@type']))) return;
        var im=o.image; if(Array.isArray(im)) im=im[0]; if(im&&typeof im==='object') im=im.url||im.contentUrl;
        if(typeof im==='string') ld=im;
      });
    }catch(e){}
  });
  var og=document.querySelector('meta[property="og:url"]');
  return JSON.stringify({img:best, ld:ld, ogurl:og?og.content:''});
})()"""

/** The checks' pretend connection (the test phone has no network); null on a real phone. */
internal var pretendOnline: Boolean? = null

/** True when the phone has a connection to the internet (wifi or mobile data). */
internal fun isOnline(c: Context): Boolean = try {
    val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val n = cm.activeNetwork
    // No network at all: offline. A network whose details are unknown: let the shop page say so.
    n != null && (cm.getNetworkCapabilities(n)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true)
} catch (e: Exception) { true }

private fun decodeJsString(r: String?): String? {
    if (r == null || r == "null") return null
    return try { JSONTokener(r).nextValue() as? String } catch (e: Exception) { null }
}

/** A shop open inside the app: the page, whether it shows a product, "+ Add shop", the messages at the bottom. */
@Stable
internal class ShopBrowserState(private val ctx: Context, shop: Shop, search: String?, private val scope: CoroutineScope) {
    var web by mutableStateOf<WebView?>(null)
    var progress by mutableIntStateOf(0)
    var pageTitle by mutableStateOf(shop.name)
    var reading by mutableStateOf(false)
    // The address of the page on screen when a product was recognised there (null: not a product page).
    var productAt by mutableStateOf<String?>(null)
    var checks by mutableIntStateOf(0)
    var hint by mutableStateOf(false)
    // The site on screen (owner, 7 November 2026): a site that isn't in My shops gets "+ My shops" at the top.
    var host by mutableStateOf(shop.host)
    var shopsVersion by mutableIntStateOf(0)
    var justAdded by mutableStateOf("")
    // "Add bol.com to My shops?" once per site, after a wish from it was saved.
    var offer by mutableStateOf<String?>(null)
    var q by mutableStateOf(search.orEmpty())
    // "Added to …" for a moment after a wish from this shop was saved.
    var addedNote by mutableStateOf<String?>(null)
    // Import (owner, 18 November 2026): no wishes were found on the list page on screen.
    var importHint by mutableStateOf(false)
    // The search row (owner, 22 November 2026): which of Shops.searchShops shows on screen (-1: another site).
    val searchAt: Int get() = Shops.searchShopAt(host)

    /** A shop in the search row: its own results for the words in the search field. */
    fun searchIn(i: Int) {
        val words = q.trim().ifEmpty { return }
        Shops.searchShops.getOrNull(i)?.let { web?.loadUrl(it.url(words)) }
    }

    /** The search field's Search key: the shop on screen again (Google when it is another site). */
    fun searchAgain() = searchIn(searchAt.takeIf { it >= 0 } ?: Shops.searchShops.lastIndex)

    /**
     * "Open in Chrome" (owner, 22 November 2026: bol.com may refuse the app's browser; Chrome works): the page on
     * screen in the phone's own browser. Only http(s) pages.
     */
    fun openOutside() {
        val url = web?.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return
        try {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, tr("No browser found on this phone."), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun js(w: WebView, code: String): String? = withTimeoutOrNull(4000L) {
        suspendCancellableCoroutine<String?> { cont ->
            w.evaluateJavascript(code) { r -> if (cont.isActive) cont.resume(decodeJsString(r)) }
        }
    }

    /** A wish from this shop was saved (to [listName]): says so, and offers the site once if it isn't one of yours. */
    suspend fun saved(listName: String) {
        addedNote = tr("Added to %s", listName)
        val h = host
        if (h.isNotEmpty() && !Shops.isSearch(h) && !Shops.has(ctx, h) && !Shops.offered(ctx, h)) {
            Shops.markOffered(ctx, h)
            offer = h
        }
        kotlinx.coroutines.delay(2500)
        addedNote = null
    }

    // Adds the site on screen to My shops: its own name when the page says it (og:site_name), else its address.
    fun addSite(h: String) {
        val w = web
        scope.launch {
            // The page on screen is that site (the offer may be for a site you have since left).
            val cur = try { URI(w?.url ?: "").host } catch (e: Exception) { null }
            val here = cur != null && cur.removePrefix("www.") == h
            val named = if (!here || w == null) "" else js(w, "(document.querySelector('meta[property=\"og:site_name\"]')||{}).content||''")
                ?.trim()?.take(30).orEmpty()
            val raw = if (here) cur!! else h
            Shops.add(ctx, named.ifEmpty { h.removePrefix("www.") }, "https://$raw/")
            shopsVersion++
            justAdded = h
            offer = null
            addedNote = tr("%s is in My shops", named.ifEmpty { h.removePrefix("www.") })
            kotlinx.coroutines.delay(2500)
            addedNote = null
        }
    }

    fun back(onClose: () -> Unit) {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    // Is the page on screen a product? Looked at when a page has loaded or its address changed (shops that change
    // pages without reloading), then again and again while you stay (2.145: every 5 seconds for a minute), as many
    // shops fill in the product and its price well after loading. A product without a readable price counts too.
    suspend fun checkProduct() {
        if (checks == 0) return
        for (wait in listOf(300L, 1200L, 1500L) + List(12) { 5000L }) {
            kotlinx.coroutines.delay(wait)
            val w = web ?: return
            val url = w.url ?: return
            if (productAt == url) return
            val html = js(w, "document.documentElement.outerHTML") ?: continue
            val ok = withContext(Dispatchers.Default) {
                try { parsePage(Jsoup.parse(html, url), URI(url).host ?: "", 200)?.let { !it.blocked && it.product } ?: false }
                catch (e: Exception) { false }
            }
            if (web?.url != url) return
            productAt = if (ok) url else null
            if (ok) return
        }
    }

    // Import (owner, 18 November 2026): the list page on screen is read by the Import reader (Import.kt).
    private fun importPage(done: (ImportRead) -> Unit) {
        val w = web ?: return
        val url = w.url ?: return
        importHint = false
        reading = true
        scope.launch {
            val r = try { Import.fromReader(ImportReaders.run(w, ImportReaders.script(ctx)), url) } catch (e: Exception) { null }
            reading = false
            if (r == null || r.wishes.isEmpty()) {
                Import.reportNothing(r?.site ?: Import.siteKey(url), r?.version ?: 0)
                importHint = true
            } else done(r)
        }
    }

    /** "+ Add wish" (or, from Import, "Import this page"). [anyway]: "Add anyway" off a product page. */
    fun add(anyway: Boolean, onImport: ((ImportRead) -> Unit)?, onFound: (String, Meta?) -> Unit) {
        if (onImport != null) { importPage(onImport); return }
        val w = web ?: return
        val url = w.url ?: return
        hint = false
        reading = true
        scope.launch {
            // The page as it is right now (2.145): a price or a size chosen after the last check counts.
            val html = js(w, "document.documentElement.outerHTML")
            val read = try {
                html?.let { h -> withContext(Dispatchers.Default) { parsePage(Jsoup.parse(h, url), URI(url).host ?: "", 200) } }
            } catch (e: Exception) {
                null
            }
            if (!anyway && (read == null || read.blocked || !read.product)) {
                reading = false
                hint = true
                return@launch
            }
            if (!anyway) productAt = url
            // The product picture (owner, 2.116: it often stayed empty; 2.118: always the FIRST product photo). Shops
            // that change pages without reloading keep an old "og:image" (or none), so the picture is taken from the
            // page itself: the first image in the product's own structured data, then the page's og:image when it
            // belongs to this page, then the first big picture in the page.
            val pics = try { JSONObject(js(w, PICTURE_JS) ?: "{}") } catch (e: Exception) { JSONObject() }
            fun abs(u: String): String = try { URI(url).resolve(u.trim()).toString() } catch (e: Exception) { "" }
            val ld = pics.optString("ld").takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty()
            val ogUrl = pics.optString("ogurl")
            val ogFits = ogUrl.isBlank() || try { URI(abs(ogUrl)).path.trimEnd('/') == URI(url).path.trimEnd('/') } catch (e: Exception) { false }
            val big = pics.optString("img").takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty()
            val picture = listOf(ld, if (ogFits) read?.image.orEmpty() else "", big).firstOrNull { it.startsWith("http") }.orEmpty()
            val meta = read?.let { if (picture.isNotEmpty()) it.copy(image = picture) else it }
            reading = false
            // "Add anyway": whatever could be read (or nothing), to fill in yourself.
            onFound(url, meta?.takeIf { !it.blocked })
        }
    }
}
