package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * The top of Home (2.89, owner): your picture with your name under it, and three numbers next to it: wishlists,
 * wishes and top wishes. Each number opens a page: all your wishlists (your own, then group wishlists), all wishes of
 * your own lists, or only the top wishes (with a star to take a wish off again).
 */

/**
 * A wish opened from "All your wishes" or "Top wishes" (2.91): the list scrolls to [target] and lets it glow softly
 * twice ([GlowBox]).
 */
object WishFocus {
    var target by mutableStateOf<Long?>(null)
    /** The wish that glows and a counter, so the same wish can glow again. */
    var glowing by mutableStateOf<Pair<Long, Int>?>(null)
        private set

    fun glow(id: Long) { glowing = id to ((glowing?.second ?: 0) + 1) }

    /** The glow of [id] is over: the card goes back to how it was. */
    fun done(id: Long) { if (glowing?.first == id) glowing = null }
}

/**
 * Around a wish card on a list: a soft glow in the theme's colour that pulses twice and fades, when [WishFocus] says
 * so. When it is over (or stopped halfway, for example by scrolling), [WishFocus] forgets it and the card is drawn
 * exactly as before: no glow modifiers at all (2.93: in 2.92 a rim could stay behind).
 */
@Composable
fun GlowBox(id: Long, small: Boolean, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val col = MaterialTheme.colorScheme.primary
    val g = remember { Animatable(0f) }
    val n = WishFocus.glowing?.takeIf { it.first == id }?.second ?: 0
    LaunchedEffect(n) {
        if (n == 0) return@LaunchedEffect
        try {
            g.snapTo(0f)
            repeat(2) {
                g.animateTo(1f, tween(450))
                g.animateTo(0.3f, tween(450))
            }
            g.animateTo(0f, tween(700))
        } finally {
            WishFocus.done(id)
        }
    }
    if (n == 0) {
        content()
        return
    }
    val lit = g.value
    val shape = look.cardShape(id, small)
    Box(
        Modifier.shadow((18 * lit).dp, shape, clip = false, ambientColor = col, spotColor = col)
            .then(if (lit > 0.01f) Modifier.border(3.dp, col.copy(alpha = 0.8f * lit), shape) else Modifier)
    ) { content() }
}

/** Which page of Home is open: null (Home itself), "lists", "wishes" or "top". */
object HubNav {
    var page by mutableStateOf<String?>(null)
}

/**
 * A wish with the lists it is on (your own). A wish copied into other lists keeps its id, so it is one wish: counted
 * and shown once, with every list in "Added to …" (owner, 2.90). Top wish when any of its copies is.
 */
private data class Placed(val w: Wish, val listIds: List<String>, val listNames: List<String>) {
    val starred: Boolean get() = w.star
}

private fun listLabel(name: String) = if (name == "My wishlist") tr(name) else name

@Composable
private fun placedWishes(): List<Placed> {
    val ctx = LocalContext.current
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    return mine.flatMap { d -> d.wishes.map { Triple(it, d.id, listLabel(d.name)) } }
        .groupBy { it.first.id }
        .map { (_, copies) ->
            val w = copies.first().first.copy(star = copies.any { it.first.star })
            Placed(w, copies.map { it.second }, copies.map { it.third })
        }
        .sortedByDescending { it.w.id }
}

/** Your picture and name on the left; the three numbers next to it. */
@Composable
fun ProfileSummary(onProfile: () -> Unit) {
    val ctx = LocalContext.current
    Friends.load(ctx)
    val all = placedWishes()
    val lists = Repo.mineIds.size + Shared.lists.size
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.widthIn(max = 96.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onProfile).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(Friends.myPhoto, Shared.lastName, 64.dp)
            Text(
                Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(Modifier.weight(1f).padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(lists, if (lists == 1) tr("Wishlist") else tr("Wishlists")) { HubNav.page = "lists" }
            Stat(all.size, if (all.size == 1) tr("Wish") else tr("Wishes")) { HubNav.page = "wishes" }
            val top = all.count { it.starred }
            Stat(top, if (top == 1) tr("Top wish") else tr("Top wishes")) { HubNav.page = "top" }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(n.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The page behind one of the numbers. Back (arrow or the phone's Back) returns to Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    page: String, snack: SnackbarHostState, lists: @Composable () -> Unit,
    onOpenMine: (String) -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    BackHandler { HubNav.page = null }
    var unstarring by remember { mutableStateOf<Placed?>(null) }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) { "lists" -> tr("Wishlists"); "top" -> tr("Top wishes"); else -> tr("All your wishes") },
                        fontWeight = look.titleWeight
                    )
                },
                navigationIcon = { IconButton(onClick = { HubNav.page = null }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (page == "lists") {
            Column(
                Modifier.padding(pad).fillMaxSize().lookBackdrop(look)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp)
            ) { lists() }
            return@Scaffold
        }
        val all = placedWishes()
        val shown = if (page == "top") all.filter { it.starred } else all
        if (shown.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    if (page == "top") tr("No top wishes yet. Tap the star on a wish to make it a top wish.")
                    else tr("No wishes yet."),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().lookBackdrop(look),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A short line under the title (owner, 2.91); Top wishes keeps its gold label above it.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    if (page == "top") MostWantedBadge(showLabel = true)
                    Text(
                        if (page == "top") tr("The wishes you’d love most, from all your wishlists.")
                        else tr("Every wish from all your wishlists, in one place."),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
            }
            items(shown, key = { it.w.id }) { p ->
                WishLine(
                    p,
                    // Opens the list, scrolls to the wish and lets it glow (WishFocus).
                    onOpen = { WishFocus.target = p.w.id; onOpenMine(p.listIds.first()) },
                    onUnstar = if (page == "top") ({ unstarring = p }) else null
                )
            }
        }
    }
    // Taking a top wish off is asked first (owner, 2.90): an accidental tap on the star shouldn't do it.
    unstarring?.let { p ->
        AlertDialog(
            onDismissRequest = { unstarring = null },
            title = { Text(tr("Remove from top wishes?")) },
            text = { Text(tr("“%s” stays on your wishlist, just no longer as a top wish.", p.w.title)) },
            confirmButton = {
                TextButton(onClick = {
                    unstarring = null
                    // Every copy of the wish, on every list it is on.
                    p.listIds.forEach { id ->
                        Repo.updateMine(ctx, id) { ws -> ws.map { if (it.id == p.w.id) it.copy(star = false) else it } }
                    }
                }) { Text(tr("Yes")) }
            },
            dismissButton = { TextButton(onClick = { unstarring = null }) { Text(tr("No")) } }
        )
    }
}

/** One wish: a small picture, its name with "Added to …" under it, the price, and (top wishes) the star. */
@Composable
private fun WishLine(p: Placed, onOpen: () -> Unit, onUnstar: (() -> Unit)?) {
    val look = LocalLook.current
    val w = p.w
    val id = w.id
    Row(
        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).clickable(onClick = onOpen)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = if (onUnstar != null) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(look.pictureBg(id, w.image.isNotBlank())),
            contentAlignment = Alignment.Center
        ) {
            if (w.image.isNotBlank()) AsyncImage(w.image, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            else Text("🎁", fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(w.title, style = MaterialTheme.typography.bodyLarge, color = look.cardInk(id), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                tr("Added to %s", p.listNames.joinToString(", ")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = look.cardMuted(id), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        w.price?.let {
            Text(money(it, w.currency), style = MaterialTheme.typography.titleSmall, fontWeight = look.priceWeight, color = look.cardInk(id))
        }
        if (onUnstar != null) {
            StarToggle(true, onUnstar, Modifier.padding(horizontal = 8.dp))
        }
    }
}
