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

/** "Shop for gifts" on Home (called "Find a gift" until 2.121): the shops in a row (swipe sideways), with "Add shop" at the end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FindAGiftRow(onFound: (String, Meta?) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val shops = remember(Shops.version) { Shops.list(ctx) }
    var open by remember { mutableStateOf<Shop?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Shop?>(null) }
    HubTitle(tr("Shop for gifts"))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shops.size) { i ->
            val s = shops[i]
            Box {
                Column(
                    Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onLongClickLabel = tr("Remove"), onLongClick = { menuFor = s }) { open = s; Shops.opened(ctx, s) },
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
                // A tile like the shops' (owner, 2.121), with a frame so it reads as a button.
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEDEDF0))
                        .border(1.5.dp, Color(0xFFB9B9C3), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(AppIcons.Add, null, tint = Color(0xFF44444C)) }
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

    fun accepted(c: Context, host: String): String? = prefs(c).getString("shopiconok3_$host", null)

    fun accept(c: Context, host: String, url: String, px: Int = 0) {
        if (accepted(c, host) != url) prefs(c).edit().putString("shopiconok3_$host", url).putInt("shopiconpx_$host", px).apply()
    }

    /** Goes up when a shop's icon was learnt from its page, so its tile looks again. */
    var version by mutableIntStateOf(0)
        private set

    /**
     * The icons a shop's page named while it was open in the shop browser (2.121), biggest first. Used when the biggest
     * is sharper than the icon the tile shows now; then the tile tries them first.
     */
    fun learn(c: Context, host: String, found: List<Pair<String, Int>>) {
        val big = found.filter { it.second >= SHARP }.sortedByDescending { it.second }.map { it.first }.distinct().take(3)
        if (big.isEmpty()) return
        val p = prefs(c)
        val now = p.getInt("shopiconpx_$host", 0)
        val best = found.maxOf { it.second }
        if (accepted(c, host) != null && now >= best) return
        val old = p.getString("shopicon3_$host", null)?.takeIf { it.startsWith("http") }?.split('\n').orEmpty()
        val list = (big + old).distinct()
        if (list == old && accepted(c, host) == null) return
        p.edit().putString("shopicon3_$host", list.joinToString("\n")).remove("shopiconok3_$host").apply()
        big.forEach { rejected.remove(it) }
        version++
    }

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
                val d = Jsoup.connect(s.url).userAgent(UA).timeout(5000).followRedirects(true).get()
                fun notSvg(href: String, type: String) =
                    !href.lowercase().substringBefore('?').endsWith(".svg") && !type.lowercase().contains("svg")
                fun sizeOf(sizes: String): Int =
                    Regex("(\\d+)x\\d+").findAll(sizes).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
                // 1. The web app manifest.
                d.selectFirst("link[rel=manifest][href]")?.absUrl("href")?.takeIf { it.startsWith("http") }?.let { mu ->
                    try {
                        val body = Jsoup.connect(mu).userAgent(UA).timeout(5000).ignoreContentType(true).execute().body()
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
    LaunchedEffect(s.url, ShopIcons.version) {
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
 * Reads the icons the shop's own page names, from the page on screen (2.121, owner: "beautiful full-size icons"). The
 * phone's quiet lookup ([ShopIcons.declared]) is refused by several shops' bot checks (H&M, Amazon, HEMA, ...), but the
 * page in the shop browser gets through, so it can tell which big icons the shop publishes (its web app manifest and
 * its linked icons). Returns JSON: [{src, size}].
 */
private const val ICONS_JS = """(function(){
  var out=[];
  function size(s){var m=/(\d+)x\d+/g,b=0,r;while((r=m.exec(s||''))){b=Math.max(b,+r[1]);}return b;}
  function svg(h,t){return /\.svg(\?|$)/i.test(h||'')||/svg/i.test(t||'');}
  document.querySelectorAll('link[rel][href]').forEach(function(l){
    var rel=(l.rel||'').toLowerCase();
    if(rel.indexOf('icon')<0||rel==='mask-icon'||svg(l.href,l.type)) return;
    out.push({src:l.href,size:Math.max(size(l.getAttribute('sizes')),rel.indexOf('apple-touch-icon')>=0?180:0)});
  });
  var m=document.querySelector('link[rel=manifest][href]');
  if(!m) return Promise.resolve(JSON.stringify(out));
  return fetch(m.href,{credentials:'same-origin'}).then(function(r){return r.json();}).then(function(j){
    (j.icons||[]).forEach(function(ic){
      if(!ic.src||svg(ic.src,ic.type)||/monochrome/.test(ic.purpose||'')) return;
      out.push({src:new URL(ic.src,m.href).href,size:size(ic.sizes)});
    });
    return JSON.stringify(out);
  }).catch(function(){return JSON.stringify(out);});
})()"""

/**
 * Cookie questions in the shop browser (owner, 2.124: "Refuse for me", on every shop). bol.com stayed behind a dark
 * layer: its cookie question didn't show on top of it, so nothing could be tapped. Run on the page a few times after
 * it loads; returns what it did ("refused", "settings", "saved" or "").
 *  1. A button inside a cookie question that refuses or keeps only the necessary cookies is tapped.
 *  2. A question without such a button: its settings are opened ("settings"), and the next round saves them as they
 *     are, which is "only necessary" by law ("saved").
 * Only buttons inside something that is about cookies are tapped, at most four times per page, and nothing is sent.
 * Empty dark layers are GUARD_JS's job.
 */
private const val CONSENT_JS = """(function(){
  var W=window.__wlc||(window.__wlc={n:0,settings:0});
  if(W.n>=4) return '';
  var REFUSE=[/^(alle(s)? )?(cookies )?weiger(en)?( alle)?( cookies)?$/,/^(alleen|enkel|uitsluitend) (de )?(strikt )?(noodzakelijke|functionele|essenti[eë]le|basis)( cookies)?( (accepteren|toestaan|gebruiken))?$/,
    /^(nee,? )?niet akkoord$/,/^(doorgaan|ga verder) zonder( te)? accepteren$/,/^(reject|decline|deny|refuse)( all)?( cookies)?$/,
    /^(use |accept |allow )?(only )?(strictly )?(necessary|essential|required)( cookies)?( only)?$/,/^continue without accepting$/,/^(alle )?ablehnen$/,/^nur (notwendige|essenzielle)( cookies)?( akzeptieren)?$/];
  var SETTINGS=[/^(cookie[- ]?)?(instellingen|voorkeuren)( (aanpassen|wijzigen|beheren|bekijken))?$/,/^(zelf )?(instellen|aanpassen|kiezen)$/,/^meer opties$/,
    /^(manage|customi[sz]e|change)( (cookies|preferences|options|settings|choices))?$/,/^(cookie )?(settings|preferences)$/,/^more options$/,/^einstellungen$/];
  var SAVE=[/^(mijn )?(keuze|voorkeuren|instellingen|selectie)( (opslaan|bevestigen))$/,/^(opslaan|bewaren|bevestig( (mijn )?keuze)?)$/,
    /^(save|confirm)( (my )?(choices|preferences|settings|selection))?$/,/^auswahl (speichern|best[aä]tigen)$/,/^speichern$/];
  var ABOUT=/cookie|consent|privacy|gdpr|\bcmp\b|onetrust|didomi|cookiebot|usercentrics|truste|qc-cmp|sp_message|toestemming/i;
  function txt(e){return (e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\s+/g,' ').trim().toLowerCase();}
  function shown(e){var r=e.getBoundingClientRect();if(r.width<2||r.height<2)return false;var s=getComputedStyle(e);return s.visibility!=='hidden'&&s.display!=='none'&&+s.opacity>0.05;}
  function parent(e){return e.parentElement||(e.getRootNode&&e.getRootNode().host)||null;}
  function aboutCookies(e){
    for(var p=e,i=0;p&&i<15;p=parent(p),i++){
      if(p.nodeType!==1) continue;
      var a=(p.id||'')+' '+(typeof p.className==='string'?p.className:'')+' '+(p.getAttribute('aria-label')||'')+' '+(p.getAttribute('aria-labelledby')||'');
      if(ABOUT.test(a)) return true;
      var s=getComputedStyle(p);
      if((p.getAttribute('role')==='dialog'||p.getAttribute('aria-modal')==='true'||p.tagName==='DIALOG'||s.position==='fixed'||s.position==='sticky')&&/cookie/i.test((p.textContent||'').slice(0,4000))) return true;
    }
    return false;
  }
  function buttons(){
    var out=[],roots=[document];
    try{for(var f=0;f<window.frames.length;f++){try{if(window.frames[f].document)roots.push(window.frames[f].document);}catch(e){}}}catch(e){}
    for(var r=0;r<roots.length&&r<40;r++){
      var all=roots[r].querySelectorAll('*');
      for(var i=0;i<all.length;i++){
        var el=all[i];
        if(el.shadowRoot&&roots.length<40) roots.push(el.shadowRoot);
        var t=el.tagName;
        if(t==='BUTTON'||(t==='A'&&el.getAttribute('role')==='button')||el.getAttribute('role')==='button'||(t==='INPUT'&&/^(button|submit)$/i.test(el.type))||(t==='A'&&/^#|^javascript:/i.test(el.getAttribute('href')||'#'))) out.push(el);
      }
    }
    return out;
  }
  function find(list){
    var b=buttons();
    for(var i=0;i<b.length;i++){var t=txt(b[i]);if(!t||t.length>60)continue;
      for(var j=0;j<list.length;j++) if(list[j].test(t)&&shown(b[i])&&aboutCookies(b[i])) return b[i];}
    return null;
  }
  var e=find(REFUSE);
  if(e){W.n++;e.click();return 'refused';}
  if(W.settings){e=find(SAVE);if(e){W.n++;W.settings=0;e.click();return 'saved';}}
  else{e=find(SETTINGS);if(e){W.n++;W.settings=1;e.click();return 'settings';}}
  return '';
})()"""

/**
 * Empty layers (owner, 2.125: bol.com put a grey layer back on every page; tapping cleared it, then it came back, so
 * the shop felt broken). For every shop: a see-through dark or light layer that covers the middle of the screen while
 * nothing on it can be seen or tapped is first tapped once (the shop closes its own pop-up, 2.126) and else hidden,
 * and kept hidden; after one, an invisible layer that still blocks every tap is treated the same: the page is watched (MutationObserver), so a
 * layer that comes back is hidden again before it shows. Never breaks the shop (owner): a layer that gets text,
 * pictures or buttons is shown again at once (it is a real pop-up), and a layer with a pop-up on top of it (a menu, a
 * size picker, a cookie question; checked in five places on the screen) is left alone. Scrolling that such a layer
 * switched off comes back, and goes back to how it was when the layer is shown again. Installed once per page.
 */
private const val GUARD_JS = """(function(){
  // Only the page itself, never the frames inside it (ads, payments, cookie frames): as before 2.130, when it ran only
  // after a page loaded (2.131: since 2.130 it also starts at the beginning of every page, which reaches every frame).
  try{if(window.top!==window) return 'frame';}catch(e){return 'frame';}
  if(window.__wlg) return 'on';
  var G=window.__wlg={hidden:[],saved:null,count:0};
  function empty(v){return (v.innerText||'').trim()===''&&!v.querySelector('img,svg,video,iframe,canvas,input,button,select,textarea,a[href]');}
  function tinted(v){
    var s=getComputedStyle(v),m=/rgba?\(([^)]+)\)/.exec(s.backgroundColor||''),a=m?(m[1].split(',')[3]===undefined?1:+m[1].split(',')[3]):0;
    return a>=0.1||(s.backdropFilter||s.webkitBackdropFilter||'none')!=='none';
  }
  function unlock(){
    if(G.saved) return;
    var de=document.documentElement,b=document.body;if(!b) return;
    G.saved=[de.style.getPropertyValue('overflow'),de.style.getPropertyPriority('overflow'),b.style.getPropertyValue('overflow'),b.style.getPropertyPriority('overflow'),b.style.getPropertyValue('position'),b.style.getPropertyPriority('position'),b.style.getPropertyValue('top'),b.style.getPropertyPriority('top')];
    var top=getComputedStyle(b).position==='fixed'?-parseInt(getComputedStyle(b).top||'0',10)||0:null;
    if(getComputedStyle(de).overflow==='hidden') de.style.setProperty('overflow','auto','important');
    if(getComputedStyle(b).overflow==='hidden') b.style.setProperty('overflow','auto','important');
    [de,b].forEach(function(e){var c=getComputedStyle(e);if(c.pointerEvents==='none'){G.pe=G.pe||[];G.pe.push([e,e.style.getPropertyValue('pointer-events'),e.style.getPropertyPriority('pointer-events')]);e.style.setProperty('pointer-events','auto','important');}
      if(c.touchAction==='none'){G.ta=G.ta||[];G.ta.push([e,e.style.getPropertyValue('touch-action'),e.style.getPropertyPriority('touch-action')]);e.style.setProperty('touch-action','auto','important');}});
    if(top!==null){b.style.setProperty('position','static','important');b.style.setProperty('top','auto','important');window.scrollTo(0,top);}
  }
  function relock(){
    var x=G.saved;if(!x) return;G.saved=null;
    var de=document.documentElement,b=document.body;
    function put(e,n,v,p){if(v) e.style.setProperty(n,v,p); else e.style.removeProperty(n);}
    put(de,'overflow',x[0],x[1]);put(b,'overflow',x[2],x[3]);put(b,'position',x[4],x[5]);put(b,'top',x[6],x[7]);
    (G.pe||[]).forEach(function(r){put(r[0],'pointer-events',r[1],r[2]);});(G.ta||[]).forEach(function(r){put(r[0],'touch-action',r[1],r[2]);});G.pe=G.ta=null;
  }
  function tap(v,x,y){
    var o={bubbles:true,cancelable:true,composed:true,clientX:x,clientY:y,screenX:x,screenY:y,button:0,view:window};
    try{v.dispatchEvent(new PointerEvent('pointerdown',Object.assign({pointerType:'touch',isPrimary:true,pointerId:1},o)));}catch(e){}
    try{var t=new Touch({identifier:1,target:v,clientX:x,clientY:y});v.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true,touches:[t],targetTouches:[t],changedTouches:[t]}));v.dispatchEvent(new TouchEvent('touchend',{bubbles:true,cancelable:true,touches:[],targetTouches:[],changedTouches:[t]}));}catch(e){}
    try{v.dispatchEvent(new PointerEvent('pointerup',Object.assign({pointerType:'touch',isPrimary:true,pointerId:1},o)));}catch(e){}
    try{v.dispatchEvent(new MouseEvent('mousedown',o));v.dispatchEvent(new MouseEvent('mouseup',o));v.dispatchEvent(new MouseEvent('click',o));}catch(e){}
  }
  // An empty grey layer covering the screen under [v] (seen through the invisible layer on top of it).
  function greyBelow(v,w,h){
    var all=document.elementsFromPoint?document.elementsFromPoint(w/2,h/2):[];
    for(var i=0;i<all.length;i++){var e=all[i];if(e===v||v.contains(e)||e===document.body||e===document.documentElement) continue;
      var s=getComputedStyle(e),r=e.getBoundingClientRect();
      if((s.position==='fixed'||s.position==='absolute')&&r.width>=w*0.9&&r.height>=h*0.8&&empty(e)&&tinted(e)) return true;
      if(!empty(e)) return false;}
    return false;
  }
  function check(){
    // A hidden layer that now has something on it is a real pop-up: show it again.
    G.hidden=G.hidden.filter(function(v){
      if(!v.isConnected) return false;
      if(!empty(v)){v.style.removeProperty('display');v.style.removeProperty('opacity');v.__wlKeep=1;return false;}
      return true;
    });
    if(!G.hidden.length) relock();
    var w=innerWidth,h=innerHeight;if(w<50||h<50) return;
    var hit=document.elementFromPoint(w/2,h/2);
    if(!hit||hit===document.body||hit===document.documentElement) return;
    var v=null;
    for(var p=hit;p&&p!==document.body&&p!==document.documentElement;p=p.parentElement){
      var s=getComputedStyle(p),r=p.getBoundingClientRect();
      if((s.position==='fixed'||s.position==='absolute')&&r.width>=w*0.9&&r.height>=h*0.8){v=p;break;}
    }
    if(!v||!empty(v)||v.__wlKeep) return;
    var pts=[[w/2,h/4],[w/2,h*3/4],[w/4,h/2],[w*3/4,h/2]];
    for(var k=0;k<pts.length;k++){var o=document.elementFromPoint(pts[k][0],pts[k][1]);if(o&&o!==v&&!v.contains(o))return;}
    // An invisible layer only counts once a tinted one was seen on this page (2.126: bol.com keeps a see-through layer
    // over the page that waits for one tap; before, you had to tap once on every page).
    if(!tinted(v)&&!G.count&&!greyBelow(v,w,h)) return;
    // First tap it once, as a finger would: the shop then closes its own pop-up the normal way (scrolling and taps
    // work again). It is empty, so the tap can only reach the shop's "tap outside" handler. Still there: hide it.
    if(!v.__wlTapped){v.__wlTapped=1;tap(v,w/2,h/2);soon();return;}
    v.style.setProperty('display','none','important');
    G.hidden.push(v);G.count++;
    unlock();
  }
  // 2.130 (owner: bol.com still flickered on every new page): a new empty dark layer is made see-through (opacity 0)
  // in the same moment it appears, before the screen draws it (MutationObserver runs before the next paint). 2.131: it
  // stays on top and tappable, so check() taps it exactly as before and the shop closes its own pop-up (2.130 hid it
  // with visibility, and bol.com's page then stayed blocked: you could scroll but not tap). A layer under a real
  // pop-up, or one that gets content, is made visible again.
  G.quick=[];
  function cover(e){
    if(!e||e.nodeType!==1||e===document.body||e===document.documentElement||e.__wlQ) return false;
    var s=getComputedStyle(e);if(s.position!=='fixed'&&s.position!=='absolute') return false;
    if(s.display==='none'||s.visibility==='hidden'||+s.opacity<0.05) return false;
    var r=e.getBoundingClientRect(),w=innerWidth,h=innerHeight;
    return r.width>=w*0.9&&r.height>=h*0.8&&r.top<h*0.2&&empty(e)&&tinted(e);
  }
  function quick(e){e.__wlQ=1;e.style.setProperty('opacity','0','important');G.quick.push(e);}
  function unquick(e){e.style.removeProperty('opacity');e.__wlQ=0;e.__wlKeep=1;}
  // Something else that isn't empty sits over the middle of the screen in a fixed place: a real pop-up.
  function popupOver(v){
    var w=innerWidth,h=innerHeight,pts=[[w/2,h/2],[w/2,h/4],[w/2,h*3/4]];
    for(var k=0;k<pts.length;k++){var all=document.elementsFromPoint?document.elementsFromPoint(pts[k][0],pts[k][1]):[];
      for(var i=0;i<all.length;i++){var e=all[i];if(e===v||v.contains(e)||e===document.body||e===document.documentElement) continue;
        for(var p=e;p&&p!==document.body;p=p.parentElement){var s=getComputedStyle(p);
          if((s.position==='fixed'||s.position==='sticky')&&p!==v&&!p.contains(v)&&!empty(p)){var r=p.getBoundingClientRect();if(r.width<w*0.98||r.height<h*0.95) return true;}}
        break;}}
    return false;
  }
  function settle(){
    G.quick=G.quick.filter(function(v){
      if(!v.isConnected) return false;
      // It got something on it, or a real pop-up uses it as its background: show it again.
      if(!empty(v)||popupOver(v)){unquick(v);return false;}
      // Still on screen and empty: check() taps it once, then hides it (as before 2.130). Watched until it is gone.
      return getComputedStyle(v).display!=='none';
    });
  }
  function scan(list){
    for(var i=0;i<list.length;i++){var m=list[i];
      if(m.type==='attributes'){if(!m.target.__wlKeep&&cover(m.target)) quick(m.target);}
      else for(var j=0;j<m.addedNodes.length;j++){var n=m.addedNodes[j];if(n.nodeType!==1) continue;
        if(!n.__wlKeep&&cover(n)) quick(n);
        if(n.children&&n.children.length<40) for(var c=0;c<n.children.length;c++) if(!n.children[c].__wlKeep&&cover(n.children[c])) quick(n.children[c]);}}
  }
  var t=0;
  function soon(){if(t) return;t=setTimeout(function(){t=0;try{settle();check();}catch(e){}},100);}
  try{new MutationObserver(function(list){try{scan(list);}catch(e){}soon();}).observe(document,{subtree:true,childList:true,attributes:true,attributeFilter:['class','style','hidden','open']});}catch(e){}
  addEventListener('resize',soon);
  try{check();}catch(e){}
  return 'on';
})()"""

/** The page's own user agent without "; wv": some shops treat the in-app browser as "open our app" and get stuck (2.121). */
private fun browserUserAgent(ua: String): String = ua.replace("; wv)", ")").replace(Regex("\\sVersion/\\d+(\\.\\d+)*"), "")

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
    // Goes up when a page has loaded or its address changed: the cookie question is looked at again (CONSENT_JS).
    var loads by remember { mutableIntStateOf(0) }
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

    // Cookie questions are refused for you (owner, 2.124), and empty dark layers are kept away (GUARD_JS, 2.125). Looked
    // at for about 12 seconds after a page loads, as many shops show their question a moment later.
    LaunchedEffect(loads) {
        if (loads == 0) return@LaunchedEffect
        for (round in 0 until 10) {
            kotlinx.coroutines.delay(if (round == 0) 600L else 1200L)
            val w = web ?: return@LaunchedEffect
            js(w, GUARD_JS)
            js(w, CONSENT_JS)
        }
    }

    // The shop's own big icons, read from its page (see ICONS_JS), for its tile on Home.
    fun learnIcons(w: WebView) {
        val host = try { URI(w.url ?: "").host?.removePrefix("www.") } catch (e: Exception) { null } ?: return
        if (host != shop.host) return
        scope.launch {
            val raw = js(w, ICONS_JS) ?: return@launch
            try {
                val a = JSONArray(raw)
                val found = (0 until a.length()).map { a.getJSONObject(it) }
                    .map { it.optString("src") to it.optInt("size") }.filter { it.first.startsWith("http") }
                ShopIcons.learn(ctx, shop.host, found)
            } catch (e: Exception) {
            }
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
                            WebView(c).apply {
                                settings.javaScriptEnabled = true
                                // The guard at the very start of every page and frame (owner, 2.130: no flicker of a
                                // dark layer on a new page). Older WebViews without this still get it as before.
                                try {
                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT))
                                        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, GUARD_JS, setOf("*"))
                                } catch (e: Exception) {
                                }
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.userAgentString = browserUserAgent(settings.userAgentString)
                                // Cookie questions and logins of several shops run on another site (2.121, owner's
                                // recording: bol.com stayed behind a grey veil).
                                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val u = request.url
                                        val s = u.scheme ?: return true
                                        if (s == "http" || s == "https") return false
                                        // "Open in our app" links: their web address, when they have one.
                                        if (s == "intent") {
                                            val fallback = try {
                                                android.content.Intent.parseUri(u.toString(), android.content.Intent.URI_INTENT_SCHEME)
                                                    .getStringExtra("browser_fallback_url")
                                            } catch (e: Exception) { null }
                                            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) view.loadUrl(fallback)
                                        }
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                                    // The guard goes on as soon as the page shows, before a layer can flicker in.
                                    override fun onPageCommitVisible(view: WebView, url: String?) { view.evaluateJavascript(GUARD_JS, null) }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        progress = 100
                                        view.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                                        checks++
                                        loads++
                                        learnIcons(view)
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                        if (url != productAt) productAt = null
                                        checks++
                                        loads++
                                    }
                                }
                                // Pages that ask for your location, camera or microphone get a "no" at once (owner, 2.125): a
                                // page left waiting for an answer can sit behind a grey screen.
                                settings.setGeolocationEnabled(false)
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?, callback: android.webkit.GeolocationPermissions.Callback?
                                    ) { callback?.invoke(origin, false, false) }
                                    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) { request?.deny() }
                                    override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                                    override fun onReceivedTitle(view: WebView, title: String?) {
                                        title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                                    }
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
