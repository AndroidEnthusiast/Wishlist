package com.wishlist.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

/* My shops (see Shops.kt): a shop inside the app. What it shows and does is in ShopBrowserState (ShopsModel.kt). */

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
fun ShopBrowser(shop: Shop, onClose: () -> Unit, onFound: (String, Meta?) -> Unit, search: String? = null, onImport: ((ImportRead) -> Unit)? = null) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    // The page, what it shows and what a tap does (ShopBrowserState).
    val st = remember { ShopBrowserState(ctx, shop, search, scope) }
    val known = remember(st.host, st.shopsVersion, Shops.version) { st.host.isEmpty() || Shops.isSearch(st.host) || Shops.has(ctx, st.host) }
    // "Added to …" for a moment after a wish from this shop was saved.
    val savesAtStart = remember { places.shopSaves }
    LaunchedEffect(places.shopSaves) {
        if (places.shopSaves == savesAtStart) return@LaunchedEffect
        st.saved(places.shopSavedTo)
    }
    LaunchedEffect(st.hint) { if (st.hint) { kotlinx.coroutines.delay(5000); st.hint = false } }
    LaunchedEffect(st.checks) { st.checkProduct() }
    fun back() = st.back(onClose)
    fun add(anyway: Boolean = false) = st.add(anyway, onImport, onFound)

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)
    ) {
        // Back (button or swipe) goes to the shop's previous page; only on the first page it closes (owner, 2.116).
        BackHandler { back() }
        Surface(Modifier.fillMaxSize().lookPage(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(AppIcons.Close, if (search != null) tr("Close browser") else tr("Close shop")) }
                    // The site on screen: its icon (Google's icon service, like the shop tiles).
                    if (search == null || !Shops.isSearch(st.host)) ShopIcon(if (st.host == shop.host) shop else Shop(st.host, "https://${st.host}/"), 28)
                    if (search != null) {
                        // From "Search in browser": what you searched; tap it to search again (owner, 7 November 2026).
                        val focus = androidx.compose.ui.platform.LocalFocusManager.current
                        androidx.compose.foundation.text.BasicTextField(
                            st.q, { st.q = it.take(200) }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                if (st.q.isNotBlank()) { focus.clearFocus(); st.web?.loadUrl(Shops.searchUrl(st.q)) }
                            }),
                            modifier = Modifier.weight(1f).padding(start = 8.dp).height(44.dp)
                                .lookSurface(LocalLook.current, LocalLook.current.chipShape, cs.surfaceVariant, raised = false),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                    if (st.q.isEmpty()) Text(tr("Search in browser"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                    } else Text(
                        st.pageTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp).weight(1f)
                    )
                    // "+ Add shop" while the site isn't one of yours; "✓ In My shops" right after adding it.
                    if (!known || st.justAdded == st.host) {
                        val added = st.justAdded == st.host
                        // A filled accent button: the plain text was barely visible on a shop's page (owner, 7 November 2026).
                        if (added) Text("✓ " + tr("In My shops"), style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
                        else Button(onClick = { st.addSite(st.host) }) { Text("+ " + tr("Add shop"), maxLines = 1) }
                    }
                }
                if (st.progress in 1..99) LinearProgressIndicator(progress = { st.progress / 100f }, modifier = Modifier.fillMaxWidth())
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

                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { st.progress = 10 }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        st.progress = 100
                                        url?.let { u -> try { URI(u).host?.removePrefix("www.") } catch (e: Exception) { null } }?.let { st.host = it }
                                        view.title?.takeIf { it.isNotBlank() }?.let { st.pageTitle = it }
                                        st.checks++
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                        if (url != st.productAt) st.productAt = null
                                        url?.let { u -> try { URI(u).host?.removePrefix("www.") } catch (e: Exception) { null } }?.let { st.host = it }
                                        st.checks++
                                    }
                                }
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, p: Int) { st.progress = p }
                                }
                                loadUrl(shop.url)
                                st.web = this
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
                            .combinedClickable(enabled = !st.reading, onClickLabel = if (onImport != null) tr("Import this page") else tr("Add wish")) { add() }
                            .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fg = cs.onPrimary
                        Icon(AppIcons.Add, null, tint = fg, modifier = Modifier.size(20.dp))
                        Text(
                            if (st.reading) tr("Reading…") else if (onImport != null) tr("Import this page") else tr("Add wish"), color = fg,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                if (st.importHint) {
                    Text(
                        tr("No wishes found on this page yet. Scroll until you see them, then try again."), color = cs.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().background(cs.inverseSurface).padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                if (st.hint) {
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
                st.offer?.let { h ->
                    Row(
                        Modifier.fillMaxWidth().background(cs.inverseSurface).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("Add %s to My shops?", h), color = cs.inverseOnSurface, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                        )
                        // A filled accent button, so it stands out on the dark bar (owner, 7 November 2026).
                        Button(onClick = { st.addSite(h) }) { Text(tr("Add shop")) }
                        IconButton(onClick = { st.offer = null }) { Icon(AppIcons.Close, tr("Not now"), tint = cs.inverseOnSurface) }
                    }
                }
                st.addedNote?.let { n ->
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
