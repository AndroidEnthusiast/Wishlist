package com.wishlist.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
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
 * page the floating "+ Add wish" button reads the page that is open (with the same reading as a shared link, parsePage
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

    /** Your shops in the order you added them (see [list] for the order on Home). */
    private fun stored(c: Context): List<Shop> {
        val raw = prefs(c).getString("shops", null) ?: return starters
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it) }.map { Shop(it.optString("name"), it.optString("url")) }
                .filter { it.url.startsWith("http") }
        } catch (e: Exception) {
            starters
        }
    }

    /** The shops on Home: the one you opened last first (owner, 2.121), then the ones never opened, in their order. */
    fun list(c: Context): List<Shop> {
        @Suppress("UNUSED_VARIABLE") val v = version
        val p = prefs(c)
        return stored(c).withIndex().sortedWith(
            compareByDescending<IndexedValue<Shop>> { p.getLong("shopopened_" + it.value.host, 0L) }.thenBy { it.index }
        ).map { it.value }
    }

    /** Remembers that a shop was opened, for the order on Home. */
    fun opened(c: Context, s: Shop) {
        prefs(c).edit().putLong("shopopened_" + s.host, System.currentTimeMillis()).apply()
        version++
    }

    /** The shop you already have with the same web address, if any (2.121: adding it again says so). */
    fun existing(c: Context, address: String): Shop? {
        val u = cleanUrl(address.trim()) ?: return null
        val h = Shop("", u).host
        return stored(c).firstOrNull { it.host == h }
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
        save(c, stored(c).filter { it.host != s.host } + s)
        return s
    }

    fun remove(c: Context, s: Shop) = save(c, stored(c).filter { it.url != s.url })

    /** Puts back the starter shops you removed (your own stay). */
    fun restoreStarters(c: Context) {
        val now = stored(c)
        save(c, now + starters.filter { st -> now.none { it.host == st.host } })
    }
}

/**
 * Discover (owner, 7 November 2026: the shops got their own tab instead of a row on Home, in the place of Price watch:
 * "people are more interested in finding inspiration for their wishlist"). Every shop as a tile, four per row, with
 * "Add shop" at the end; tap one to shop inside the app and add what you find with one button. Long press removes it.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(snack: androidx.compose.material3.SnackbarHostState, onProfile: () -> Unit, onFound: (String, Meta?) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val shops = remember(Shops.version) { Shops.list(ctx) }
    var open by remember { mutableStateOf<Shop?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Shop?>(null) }
    androidx.compose.material3.Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Discover"), fontWeight = look.titleWeight) },
                navigationIcon = { MenuSlot() },
                actions = { ProfileButton(onProfile) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().lookBackdrop(look)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp)
        ) {
            Text(
                tr("Shop in the app and add what you love to your wishlist with one tap."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            )
            val cells: List<Shop?> = shops + listOf(null)
            cells.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    row.forEach { s ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (s == null) {
                                Column(
                                    Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable { adding = true }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // A tile like the shops' (owner, 2.121), with a frame so it reads as a button.
                                    Box(
                                        Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEDEDF0))
                                            .border(1.5.dp, Color(0xFFB9B9C3), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(AppIcons.Add, null, tint = Color(0xFF44444C)) }
                                    Text(tr("Add shop"), style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                                }
                            } else {
                                Column(
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(onLongClickLabel = tr("Remove"), onLongClick = { menuFor = s }) { open = s; Shops.opened(ctx, s) }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ShopIcon(s, 60)
                                    Text(
                                        s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                DropdownMenu(expanded = menuFor == s, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text(tr("Remove %s", s.name)) }, onClick = { menuFor = null; Shops.remove(ctx, s) })
                                }
                            }
                        }
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Text(
                tr("Long press a shop to remove it."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
        }
    }
    // The shop stays open under the add form (owner, 2.116): after Save or Cancel you are back where you were in it.
    open?.let { s -> ShopBrowser(s, onClose = { open = null }, onFound = { u, m -> onFound(u, m) }) }
    if (adding) AddShopDialog(onClose = { adding = false })
}

/**
 * Where a shop's icon comes from: only Google's free icon service (owner, 6 November 2026, after bol.com blocked "unusual
 * traffic"): the app never asks the shops themselves for their icon; Google only gets the shop's web address.
 */
object ShopIcons {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun google(host: String) = "https://www.google.com/s2/favicons?domain=$host&sz=256"

    // 2.117 (owner's recording: Zalando's icon showed, then vanished after scrolling): the icon that was accepted is
    // remembered (here and on the phone), and icons that looked empty are never tried again, so a tile that scrolls
    // away and back shows the same icon at once.
    private val rejected = HashSet<String>()

    /** The icon that was accepted earlier, when it came from Google's service (one from a shop's own site isn't used). */
    fun accepted(c: Context, host: String): String? =
        prefs(c).getString("shopiconok3_$host", null)?.takeIf { it.startsWith("https://www.google.com/s2/favicons") }

    fun accept(c: Context, host: String, url: String, px: Int = 0) {
        if (accepted(c, host) != url) prefs(c).edit().putString("shopiconok3_$host", url).putInt("shopiconpx_$host", px).apply()
    }

    /** Goes up when the tiles should look again. */
    var version by mutableIntStateOf(0)
        private set

    fun reject(c: Context, host: String, url: String) {
        rejected.add(url)
        if (accepted(c, host) == url) prefs(c).edit().remove("shopiconok3_$host").apply()
    }

    fun isRejected(url: String) = url in rejected

    /**
     * Icons smaller than this (pixels) look blurry on a 52 dp tile (2.119: 64, not 96; 96 also passed over icons that
     * looked fine, like Amazon's and HEMA's).
     */
    const val SHARP = 64

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
    LaunchedEffect(s.url, ShopIcons.version) {
        // Only Google's icon service (owner, 6 November 2026): the shops themselves are never asked.
        tries = listOf(ShopIcons.google(s.host)).filter { !ShopIcons.isRejected(it) }
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
                else if (px in 1 until ShopIcons.SHARP && url != bestSmall && url != ShopIcons.accepted(ctx, s.host)) {
                    // Not thrown away (2.119: in 2.118 it was, so shops whose best icon is a little small showed
                    // nothing after scrolling): kept as the fallback while a sharper one is looked for.
                    if (px > bestSmallPx) { bestSmall = url; bestSmallPx = px }
                    at++
                } else ShopIcons.accept(ctx, s.host, url, px) // sharp, or the best there is: remembered
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
    var twice by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Add shop")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    address, { address = it; bad = false; twice = null }, label = { Text(tr("Web address")) }, singleLine = true,
                    placeholder = { Text("shop.nl") }, isError = bad || twice != null,
                    supportingText = when {
                        bad -> ({ Text(tr("That isn’t a web address.")) })
                        twice != null -> ({ Text(tr("%s is already in your shops.", twice!!)) })
                        else -> null
                    },
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
            TextButton(onClick = {
                val have = Shops.existing(ctx, address)
                when {
                    have != null -> twice = have.name
                    Shops.add(ctx, name, address) != null -> onClose()
                    else -> bad = true
                }
            }) { Text(tr("Add")) }
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
 * A shop inside the app, full screen: ✕ closes it (owner, 2.122: no other buttons at the top), the phone's Back goes
 * back a page (or closes on the first one). A floating "+ Add wish" (owner, 2.120; flat and smaller in 2.121) has colour only on a page where a product is
 * recognised; tapped elsewhere it says so, with "Add anyway" as a way out in case the product wasn't recognised.
 * Links that open another app (intent:) load their web address instead when they have one; others are ignored. The page
 * gets no access to the app (no JavaScript bridge, no files).
 */
@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShopBrowser(shop: Shop, onClose: () -> Unit, onFound: (String, Meta?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(shop.name) }
    var reading by remember { mutableStateOf(false) }
    // The address of the page on screen when a product was recognised there (null: not a product page).
    var productAt by remember { mutableStateOf<String?>(null) }
    var checks by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf(false) }
    // "Added to …" for a moment after a wish from this shop was saved.
    val savesAtStart = remember { ShopNav.saves }
    var addedNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ShopNav.saves) {
        if (ShopNav.saves == savesAtStart) return@LaunchedEffect
        addedNote = tr("Added to %s", ShopNav.savedTo)
        kotlinx.coroutines.delay(2500)
        addedNote = null
    }
    LaunchedEffect(hint) { if (hint) { kotlinx.coroutines.delay(5000); hint = false } }

    suspend fun js(w: WebView, code: String): String? = withTimeoutOrNull(4000L) {
        suspendCancellableCoroutine<String?> { cont ->
            w.evaluateJavascript(code) { r -> if (cont.isActive) cont.resume(decodeJsString(r)) }
        }
    }

    fun back() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    // Is the page on screen a product? Looked at when a page has loaded or its address changed (shops that change
    // pages without reloading), and again a little later, as many shops fill in the price after loading.
    LaunchedEffect(checks) {
        if (checks == 0) return@LaunchedEffect
        for (wait in listOf(300L, 1500L, 3000L)) {
            kotlinx.coroutines.delay(wait)
            val w = web ?: return@LaunchedEffect
            val url = w.url ?: return@LaunchedEffect
            if (productAt == url) return@LaunchedEffect
            val html = js(w, "document.documentElement.outerHTML") ?: continue
            val ok = withContext(Dispatchers.Default) {
                try { parsePage(Jsoup.parse(html, url), URI(url).host ?: "", 200)?.let { !it.blocked && it.price != null } ?: false }
                catch (e: Exception) { false }
            }
            if (web?.url != url) return@LaunchedEffect
            productAt = if (ok) url else null
            if (ok) return@LaunchedEffect
        }
    }

    fun add(anyway: Boolean = false) {
        val w = web ?: return
        val url = w.url ?: return
        if (!anyway && productAt != url) { hint = true; return }
        hint = false
        reading = true
        scope.launch {
            val html = js(w, "document.documentElement.outerHTML")
            val read = try {
                html?.let { parsePage(Jsoup.parse(it, url), URI(url).host ?: "", 200) }
            } catch (e: Exception) {
                null
            }
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
                // The page fills the screen; "+ Add wish" floats over its bottom-right corner (owner, 2.120).
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { c ->
                            // Plain, like a normal browser (owner, 6 November 2026: every bol.com "fix" since 2.121
                            // removed; the page is never changed or tapped by the app).
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
                                        checks++
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                        if (url != productAt) productAt = null
                                        checks++
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
                        modifier = Modifier.fillMaxSize()
                    )
                    // Flat (owner, 2.121: no shadow) and compact, so the products stay in view. Always in colour (owner,
                    // 2.126: no grey version); off a product page a tap says so, with "Add anyway".
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(14.dp).height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(cs.primary)
                            .combinedClickable(enabled = !reading, onClickLabel = tr("Add wish")) { add() }
                            .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fg = cs.onPrimary
                        Icon(AppIcons.Add, null, tint = fg, modifier = Modifier.size(20.dp))
                        Text(
                            if (reading) tr("Reading…") else tr("Add wish"), color = fg,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                if (hint) {
                    Row(
                        Modifier.fillMaxWidth().background(cs.inverseSurface).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("No product recognised on this page."), color = cs.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                        )
                        TextButton(onClick = { add(anyway = true) }) { Text(tr("Add anyway"), color = cs.inversePrimary) }
                    }
                }
                addedNote?.let { n ->
                    Text(
                        "✓ $n", color = cs.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().background(cs.secondaryContainer).padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
