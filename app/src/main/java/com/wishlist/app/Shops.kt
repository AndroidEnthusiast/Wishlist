package com.wishlist.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import kotlin.coroutines.resume

/*
 * "Find a gift" (2.114, owner): a row of web shops on Home. Tapping one opens the shop inside the app; on any product
 * page the big "Add to wishlist" button reads the page that is open (with the same reading as a shared link, parsePage
 * in Fetch.kt) and opens the add form filled in. Reading the page the person already sees also works for shops that
 * block the app's own requests.
 *  - A starter list of 20 shops in the Netherlands (owner's choice); "Add shop" adds your own, a long press removes one.
 *    The list is kept on this phone ("shops" in the preferences), not online.
 *  - Each shop shows the icon its own website publishes, loaded live like a browser bookmark (owner's choice; we never
 *    copy logos into the app), with Google's icon service as the last resort (2.115, see [ShopIcons]). When nothing
 *    loads, a coloured tile with the first letter shows.
 *  - App only: a web page can't show other shops inside itself; browser users keep pasting links (owner's choice).
 *  - Plain links, no affiliate codes (owner: maybe discuss later).
 */

/** A wish saved from a shop (2.116): the shop says "Added to …" and stays open. */
object ShopNav {
    var saves by mutableIntStateOf(0)
        private set
    var savedTo by mutableStateOf("")
        private set

    fun saved(listName: String) { savedTo = listName; saves++ }
}

data class Shop(val name: String, val url: String) {
    val host: String get() = try { URI(url).host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }
}

object Shops {
    val starters = listOf(
        Shop("bol.com", "https://www.bol.com/nl/nl/"),
        Shop("Amazon", "https://www.amazon.nl/"),
        Shop("Coolblue", "https://www.coolblue.nl/"),
        Shop("Zalando", "https://www.zalando.nl/"),
        Shop("IKEA", "https://www.ikea.com/nl/nl/"),
        Shop("Wehkamp", "https://www.wehkamp.nl/"),
        Shop("HEMA", "https://www.hema.nl/"),
        Shop("de Bijenkorf", "https://www.debijenkorf.nl/"),
        Shop("Intertoys", "https://www.intertoys.nl/"),
        Shop("LEGO", "https://www.lego.com/nl-nl"),
        Shop("Decathlon", "https://www.decathlon.nl/"),
        Shop("Douglas", "https://www.douglas.nl/"),
        Shop("Rituals", "https://www.rituals.com/nl-nl/"),
        Shop("Blokker", "https://www.blokker.nl/"),
        Shop("fonQ", "https://www.fonq.nl/"),
        Shop("Nike", "https://www.nike.com/nl/"),
        Shop("H&M", "https://www2.hm.com/nl_nl/"),
        Shop("Etsy", "https://www.etsy.com/nl/"),
        Shop("Bever", "https://www.bever.nl/"),
        Shop("Apple", "https://www.apple.com/nl/")
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Goes up on every change, so Home redraws the row. */
    var version by mutableIntStateOf(0)
        private set

    fun list(c: Context): List<Shop> {
        @Suppress("UNUSED_VARIABLE") val v = version
        val raw = prefs(c).getString("shops", null) ?: return starters
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it) }.map { Shop(it.optString("name"), it.optString("url")) }
                .filter { it.url.startsWith("http") }
        } catch (e: Exception) {
            starters
        }
    }

    private fun save(c: Context, l: List<Shop>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs(c).edit().putString("shops", a.toString()).apply()
        version++
    }

    /** Adds a shop by its web address ("coolblue.nl" is fine); null when that isn't a web address. */
    fun add(c: Context, name: String, address: String): Shop? {
        val u = cleanUrl(address.trim()) ?: return null
        val s = Shop(name.trim().ifEmpty { Shop("", u).host }, u)
        if (s.host.isEmpty()) return null
        save(c, list(c).filter { it.host != s.host } + s)
        return s
    }

    fun remove(c: Context, s: Shop) = save(c, list(c).filter { it.url != s.url })

    /** Puts back the starter shops you removed (your own stay). */
    fun restoreStarters(c: Context) {
        val now = list(c)
        save(c, now + starters.filter { st -> now.none { it.host == st.host } })
    }
}

/** "Find a gift" on Home: the shops in a row (swipe sideways), with "Add shop" at the end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FindAGiftRow(onFound: (String, Meta?) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val shops = remember(Shops.version) { Shops.list(ctx) }
    var open by remember { mutableStateOf<Shop?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Shop?>(null) }
    HubTitle(tr("Find a gift"))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shops.size) { i ->
            val s = shops[i]
            Box {
                Column(
                    Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onLongClickLabel = tr("Remove"), onLongClick = { menuFor = s }) { open = s },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShopIcon(s, 52)
                    Text(
                        s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                DropdownMenu(expanded = menuFor == s, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text(tr("Remove %s", s.name)) }, onClick = { menuFor = null; Shops.remove(ctx, s) })
                }
            }
        }
        item {
            Column(
                Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).combinedClickable { adding = true },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Icon(AppIcons.Add, null, tint = cs.onSurfaceVariant) }
                Text(tr("Add shop"), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    // The shop stays open under the add form (owner, 2.116): after Save or Cancel you are back where you were in it.
    open?.let { s -> ShopBrowser(s, onClose = { open = null }, onFound = { u, m -> onFound(u, m) }) }
    if (adding) AddShopDialog(onClose = { adding = false })
}

/**
 * Where a shop's icon comes from (owner, 2.115: eight of the twenty didn't load). First the icon the shop's homepage
 * itself names (what a browser shows on its tab: an apple-touch-icon, else the largest icon; SVG skipped, the app can't
 * draw it), then the standard /apple-touch-icon.png, then Google's free icon service (it only gets the shop's web
 * address). What the homepage named is remembered on the phone, so it is looked up once (again after a week if the
 * homepage couldn't be read).
 */
object ShopIcons {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun google(host: String) = "https://www.google.com/s2/favicons?domain=$host&sz=256"

    // 2.117 (owner's recording: Zalando's icon showed, then vanished after scrolling): the icon that was accepted is
    // remembered (here and on the phone), and icons that looked empty are never tried again, so a tile that scrolls
    // away and back shows the same icon at once.
    private val rejected = HashSet<String>()

    fun accepted(c: Context, host: String): String? = prefs(c).getString("shopiconok2_$host", null)

    fun accept(c: Context, host: String, url: String) {
        if (accepted(c, host) != url) prefs(c).edit().putString("shopiconok2_$host", url).apply()
    }

    fun reject(c: Context, host: String, url: String) {
        rejected.add(url)
        if (accepted(c, host) == url) prefs(c).edit().remove("shopiconok2_$host").apply()
    }

    fun isRejected(url: String) = url in rejected

    /** Icons smaller than this (pixels) look blurry on a 52 dp tile. */
    const val SHARP = 96

    /**
     * True when a loaded icon would look empty on the tile: almost all of it see-through or white (2.116: Zalando's
     * icon is white on see-through). Then the next address is tried.
     */
    fun looksEmpty(b: Bitmap): Boolean {
        val n = 16
        var shown = 0
        for (y in 0 until n) for (x in 0 until n) {
            val px = b.getPixel(x * b.width / n, y * b.height / n)
            val a = android.graphics.Color.alpha(px)
            val r = android.graphics.Color.red(px); val g = android.graphics.Color.green(px); val bl = android.graphics.Color.blue(px)
            if (a > 60 && !(r > 225 && g > 225 && bl > 225)) shown++
        }
        return shown < n * n * 4 / 100
    }

    /**
     * The icons the homepage names, sharpest first (2.118, owner: every icon must be sharp): the largest icon of the
     * shop's web app manifest (shops list their app icons there, usually 192 or 512 pixels), then the largest icon
     * linked in the page. Known answers come straight from the phone's memory.
     */
    suspend fun declared(c: Context, s: Shop): List<String> {
        val key = "shopicon3_" + s.host // "3": looked up again with the manifest of 2.118
        val saved = prefs(c).getString(key, null)
        if (saved != null) {
            if (saved.startsWith("http")) return saved.split('\n').filter { it.startsWith("http") }
            val at = saved.substringAfter("none|", "0").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at < WEEK) return emptyList()
        }
        val found = withContext(Dispatchers.IO) {
            val out = ArrayList<String>()
            try {
                val d = Jsoup.connect(s.url).userAgent(UA).timeout(8000).followRedirects(true).get()
                fun notSvg(href: String, type: String) =
                    !href.lowercase().substringBefore('?').endsWith(".svg") && !type.lowercase().contains("svg")
                fun sizeOf(sizes: String): Int =
                    Regex("(\\d+)x\\d+").findAll(sizes).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
                // 1. The web app manifest.
                d.selectFirst("link[rel=manifest][href]")?.absUrl("href")?.takeIf { it.startsWith("http") }?.let { mu ->
                    try {
                        val body = Jsoup.connect(mu).userAgent(UA).timeout(8000).ignoreContentType(true).execute().body()
                        val icons = JSONObject(body).optJSONArray("icons")
                        if (icons != null) {
                            val list = (0 until icons.length()).mapNotNull { icons.optJSONObject(it) }
                                .filter { notSvg(it.optString("src"), it.optString("type")) && !it.optString("purpose").contains("monochrome") }
                                .sortedByDescending { sizeOf(it.optString("sizes")) }
                            list.firstOrNull()?.let { ic -> out.add(URI(mu).resolve(ic.optString("src")).toString()) }
                        }
                    } catch (e: Exception) {
                    }
                }
                // 2. The icons linked in the page.
                val links = d.select("link[rel][href]").filter { l ->
                    val rel = l.attr("rel").lowercase()
                    rel.contains("icon") && rel != "mask-icon" && notSvg(l.attr("href"), l.attr("type"))
                }
                fun apple(l: org.jsoup.nodes.Element) = l.attr("rel").lowercase().contains("apple-touch-icon")
                links.sortedWith(compareByDescending<org.jsoup.nodes.Element> { maxOf(sizeOf(it.attr("sizes")), if (apple(it)) 180 else 0) })
                    .map { it.absUrl("href") }.filter { it.startsWith("http") }.take(3).forEach { out.add(it) }
            } catch (e: Exception) {
            }
            out.distinct()
        }
        prefs(c).edit().putString(key, if (found.isNotEmpty()) found.joinToString("\n") else "none|" + System.currentTimeMillis()).apply()
        return found
    }
}

/**
 * The shop's own icon (see [ShopIcons]) on a light grey tile (2.115: white icons vanished on white), or a coloured
 * tile with its first letter when none loads.
 */
@Composable
fun ShopIcon(s: Shop, sizeDp: Int) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val base = try { URI(s.url).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { "" }
    // null while the homepage is being asked; then the list of addresses to try in turn.
    var tries by remember(s.url) { mutableStateOf<List<String>?>(null) }
    var at by remember(s.url) { mutableIntStateOf(0) }
    // The biggest of the too-small icons, shown only when no sharp one exists.
    var bestSmall by remember(s.url) { mutableStateOf<String?>(null) }
    var bestSmallPx by remember(s.url) { mutableIntStateOf(0) }
    LaunchedEffect(s.url) {
        val ok = ShopIcons.accepted(ctx, s.host)
        val d = if (ok != null) emptyList() else ShopIcons.declared(ctx, s)
        tries = (listOfNotNull(ok) + d + listOf("$base/apple-touch-icon.png", ShopIcons.google(s.host))).distinct()
            .filter { !ShopIcons.isRejected(it) }
        at = 0
    }
    val list = tries
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEDEDF0)),
        contentAlignment = Alignment.Center
    ) {
        if (list == null) {
            // Still looking: the plain tile.
        } else if (base.isNotEmpty() && (at < list.size || bestSmall != null)) AsyncImage(
            // Software bitmaps, so [ShopIcons.looksEmpty] can read the pixels.
            model = ImageRequest.Builder(ctx).data(if (at < list.size) list[at] else bestSmall).allowHardware(false)
                // Its real size (not shrunk to the tile first), so a small icon can be recognised.
                .size(coil.size.Size.ORIGINAL).build(),
            contentDescription = null, contentScale = ContentScale.Fit,
            onError = { if (at < list.size) at++ else bestSmall = null },
            onSuccess = { st ->
                val url = if (at < list.size) list[at] else bestSmall.orEmpty()
                // Any kind of picture (2.117: some weren't a plain bitmap, so an empty one slipped through).
                val dr = st.result.drawable
                val b = try { dr.toBitmap(48, 48) } catch (e: Exception) { null }
                // Too small to look sharp on the tile (2.118, owner: Rituals was blurry): try the next one, but keep
                // the biggest of these in case no sharp one exists at all.
                val px = minOf(dr.intrinsicWidth, dr.intrinsicHeight)
                if (b != null && ShopIcons.looksEmpty(b)) { ShopIcons.reject(ctx, s.host, url); at++ }
                else if (px in 1 until ShopIcons.SHARP && url != bestSmall) {
                    if (px > bestSmallPx) { bestSmall = url; bestSmallPx = px }
                    ShopIcons.reject(ctx, s.host, url); at++
                } else if (px == 0 || px >= ShopIcons.SHARP) ShopIcons.accept(ctx, s.host, url)
            },
            modifier = Modifier.fillMaxSize().padding(6.dp)
        ) else Box(
            Modifier.fillMaxSize().background(cs.primaryContainer), contentAlignment = Alignment.Center
        ) {
            Text(
                s.name.take(1).uppercase(), color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun AddShopDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Add shop")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    address, { address = it; bad = false }, label = { Text(tr("Web address")) }, singleLine = true,
                    placeholder = { Text("shop.nl") }, isError = bad,
                    supportingText = if (bad) ({ Text(tr("That isn’t a web address.")) }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    name, { name = it.take(30) }, label = { Text(tr("Name (optional)")) }, singleLine = true,
                    keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { Shops.restoreStarters(ctx); onClose() }) { Text(tr("Bring back removed shops")) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (Shops.add(ctx, name, address) != null) onClose() else bad = true }) { Text(tr("Add")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Finds the product picture on the page on screen (see ShopBrowser's add). Returns JSON: ld, ogurl, img. */
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

private fun decodeJsString(r: String?): String? {
    if (r == null || r == "null") return null
    return try { JSONTokener(r).nextValue() as? String } catch (e: Exception) { null }
}

/**
 * A shop inside the app, full screen: ✕ closes it, Back goes back a page (or closes on the first one), and "Add to
 * wishlist" reads the product on screen. Links that want to leave for another app (intent:, market:) are ignored, so
 * the person stays here; the page gets no access to the app (no JavaScript bridge, no files).
 */
@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShopBrowser(shop: Shop, onClose: () -> Unit, onFound: (String, Meta?) -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(shop.name) }
    var reading by remember { mutableStateOf(false) }
    var notProduct by remember { mutableStateOf<Pair<String, Meta?>?>(null) }
    // "Added to …" for a moment after a wish from this shop was saved.
    val savesAtStart = remember { ShopNav.saves }
    var addedNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ShopNav.saves) {
        if (ShopNav.saves == savesAtStart) return@LaunchedEffect
        addedNote = tr("Added to %s", ShopNav.savedTo)
        kotlinx.coroutines.delay(2500)
        addedNote = null
    }

    fun back() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    fun add() {
        val w = web ?: return
        val url = w.url ?: return
        reading = true
        scope.launch {
            suspend fun js(code: String): String? = withTimeoutOrNull(4000L) {
                suspendCancellableCoroutine<String?> { cont ->
                    w.evaluateJavascript(code) { r -> if (cont.isActive) cont.resume(decodeJsString(r)) }
                }
            }
            val html = js("document.documentElement.outerHTML")
            val read = try {
                html?.let { parsePage(Jsoup.parse(it, url), URI(url).host ?: "", 200) }
            } catch (e: Exception) {
                null
            }
            // The product picture (owner, 2.116: it often stayed empty; 2.118: always the FIRST product photo). Shops
            // that change pages without reloading keep an old "og:image" (or none), so the picture is taken from the
            // page itself: the first image in the product's own structured data, then the page's og:image when it
            // belongs to this page, then the first big picture in the page.
            val pics = try { JSONObject(js(PICTURE_JS) ?: "{}") } catch (e: Exception) { JSONObject() }
            fun abs(u: String): String = try { URI(url).resolve(u.trim()).toString() } catch (e: Exception) { "" }
            val ld = pics.optString("ld").takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty()
            val ogUrl = pics.optString("ogurl")
            val ogFits = ogUrl.isBlank() || try { URI(abs(ogUrl)).path.trimEnd('/') == URI(url).path.trimEnd('/') } catch (e: Exception) { false }
            val big = pics.optString("img").takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty()
            val picture = listOf(ld, if (ogFits) read?.image.orEmpty() else "", big).firstOrNull { it.startsWith("http") }.orEmpty()
            val meta = read?.let { if (picture.isNotEmpty()) it.copy(image = picture) else it }
            reading = false
            // A product page has a price; otherwise it is probably a search result or the shop's front page.
            if (meta != null && !meta.blocked && meta.price != null) onFound(url, meta)
            else notProduct = url to meta?.takeIf { !it.blocked }
        }
    }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)
    ) {
        // Back (button or swipe) goes to the shop's previous page; only on the first page it closes (owner, 2.116).
        BackHandler { back() }
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(AppIcons.Close, tr("Close shop")) }
                    ShopIcon(shop, 28)
                    Text(
                        pageTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp).weight(1f)
                    )
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val s = request.url.scheme ?: return true
                                    return s != "http" && s != "https"
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    progress = 100
                                    view.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                            }
                            loadUrl(shop.url)
                            web = this
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                addedNote?.let { n ->
                    Text(
                        "✓ $n", color = cs.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().background(cs.secondaryContainer).padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).lookSurface(look, look.fabShape, look.accent)
                            .combinedClickable(enabled = !reading) { add() },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = look.onAccent)
                        Text(
                            if (reading) tr("Reading this page…") else tr("Add to wishlist"),
                            color = look.onAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        notProduct?.let { (u, m) ->
            AlertDialog(
                onDismissRequest = { notProduct = null },
                title = { Text(tr("No product found here")) },
                text = { Text(tr("Open a product first, then tap Add to wishlist. Or add this page anyway and fill in the details yourself.")) },
                confirmButton = { TextButton(onClick = { notProduct = null; onFound(u, m) }) { Text(tr("Add anyway")) } },
                dismissButton = { TextButton(onClick = { notProduct = null }) { Text(tr("OK")) } }
            )
        }
    }
}
