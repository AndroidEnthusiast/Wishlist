package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.net.URI

/* My shops (see Shops.kt): the page with every shop as a tile, Search in browser, Add shop; a shop's icon. */

/**
 * My shops (owner, 7 November 2026; called Discover until 2.144): every shop as a tile, four per row, with "Add shop"
 * at the end; tap one to shop inside the app and add what you find with one button.
 * Removing (owner, 2.144, "Apple style"): a long press on any shop makes all tiles pulse briefly and shows an ✕ on each.
 * Tapping an ✕ removes that shop at once, with Undo. "Done" (top right), a tap on empty space or Back ends it; tapping a
 * shop then does nothing. With the phone's animations off there is no pulse. No wiggle (owner).
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(snack: androidx.compose.material3.SnackbarHostState, onProfile: () -> Unit, onFound: (String, Meta?) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shops = remember(Shops.version) { Shops.list(ctx) }
    // What is open and what a tap changes (ShopsModel).
    val m = remember { ShopsModel(ctx, scope, snack) }
    val still = remember {
        try { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }
    }
    // The short pulse of every tile when the ✕ marks appear.
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(m.pulses) {
        if (m.pulses == 0 || still) return@LaunchedEffect
        pulse.animateTo(1.08f, androidx.compose.animation.core.tween(120))
        pulse.animateTo(1f, androidx.compose.animation.core.tween(180))
    }
    BackHandler(enabled = m.editing) { m.editing = false }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("My shops"), big = true) }, large = false,
                navigationIcon = { MenuSlot(back = false) },
                actions = {
                    if (m.editing) TextButton(onClick = { m.editing = false }) { Text(tr("Done")) }
                    else ProfileButton(onProfile)
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                // A tap on empty space ends removing.
                .then(
                    if (m.editing) Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { m.editing = false } else Modifier
                )
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp)
        ) {
            Text(
                if (m.editing) tr("Tap ✕ to remove a shop. Tap Done when you’re finished.")
                else tr("Find must-haves in popular shops, or with a quick search."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            )
            // Search the whole web in the app's browser, with "+ Add wish" as in the shops.
            if (!m.editing) {
                val focus = androidx.compose.ui.platform.LocalFocusManager.current
                fun go() {
                    val q = m.query.trim()
                    if (q.isEmpty()) return
                    focus.clearFocus()
                    m.search(q)
                }
                OutlinedTextField(
                    m.query, { m.query = it.take(200) }, singleLine = true,
                    placeholder = { Text(tr("Search in browser")) },
                    leadingIcon = { Icon(AppIcons.Search, null) },
                    trailingIcon = if (m.query.isNotEmpty()) ({
                        IconButton(onClick = { m.query = "" }) { Icon(AppIcons.Close, tr("Clear search")) }
                    }) else null,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
            // A title above the shops (owner, 7 November 2026).
            Text(
                tr("Popular shops"), style = MaterialTheme.typography.titleMedium, fontWeight = look.titleWeight,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            )
            val cells: List<Shop?> = if (m.editing) shops else shops + listOf(null)
            cells.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    row.forEach { s ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (s == null) {
                                Column(
                                    Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable { m.adding = true }.padding(4.dp),
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
                                    Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onLongClickLabel = tr("Remove shops"), onLongClick = { m.startEditing() }
                                        ) { if (!m.editing) m.openShop(s) }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box {
                                        ShopIcon(s, 60)
                                        // The ✕: 24 dp to see, a 48 dp area to tap (docs/STYLE.md), on the tile's top right corner.
                                        if (m.editing) Box(
                                            Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-18).dp).size(48.dp)
                                                .clickable(onClickLabel = tr("Remove %s", s.name)) { m.removeShop(s) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF3A3A40))
                                                    .border(1.5.dp, Color.White, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(AppIcons.Close, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                    Text(
                                        s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            if (!m.editing) Text(
                tr("Long press a shop to remove shops."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
        }
    }
    // The shop stays open under the add form (owner, 2.116): after Save or Cancel you are back where you were in it.
    m.open?.let { s -> ShopBrowser(s, onClose = { m.close() }, onFound = { u, meta -> onFound(u, meta) }, search = m.searched) }
    if (m.adding) AddShopDialog(onAdd = { n, a -> m.addShop(n, a) }, onClose = { m.adding = false })
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
private fun AddShopDialog(onAdd: (String, String) -> String?, onClose: () -> Unit) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = onAdd(name, address)) {
                    null -> onClose()
                    "" -> bad = true
                    else -> twice = r
                }
            }) { Text(tr("Add")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}
