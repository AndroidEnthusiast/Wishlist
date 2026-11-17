package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The Add wish button, drawn in the chosen theme (classic: as it always was). */
@Composable
fun AddWishButton(onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.fabShape
    val flat = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    val text: @Composable () -> Unit = {
        Text(tr("Add wish"), fontWeight = FontWeight.Bold)
    }
    val icon: @Composable () -> Unit = { Icon(AppIcons.Add, null) }
    if (look.design == "quiet") {
        // Quiet: an accent pill with a soft coloured shadow.
        ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick, shape = shape,
            modifier = Modifier.shadow(10.dp, shape, clip = false, ambientColor = look.accent, spotColor = look.accent),
            containerColor = look.accent, contentColor = look.onAccent, elevation = flat
        )
    } else {
        ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick,
            modifier = Modifier.lookSurface(look, shape, look.accent, depth = 8.dp),
            shape = shape, containerColor = Color.Transparent, contentColor = look.onAccent, elevation = flat
        )
    }
}

/**
 * The Theme screen (owner, 17 November 2026, plan step 5; like Giftful's, our own drawing, no app-icon picker): six
 * cards in two columns, the five themes and Gen Z chaos, each a small preview of the app in its colours, the chosen
 * one with a tick; under them Appearance: System, Light or Dark. A tap changes the whole app at once.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThemerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    val mode = Repo.theme.takeIf { it == "system" || it == "light" } ?: "dark"
    val shownMode = effectiveTheme()
    val style = styleFor(Repo.design).key
    val accent = accentFor(Repo.palette).key
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Theme")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The five themes, then Gen Z chaos as the sixth (a bonus, owner).
            val cards: List<Triple<String, String, String>> = ACCENTS.map { Triple("quiet", it.key, tr(it.name)) } +
                Triple("chaos", accent, tr("Gen Z chaos"))
            cards.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (st, ac, name) ->
                        val chosen = st == style && (st == "chaos" || ac == accent)
                        ThemeCard(name, lookFor(st, ac, shownMode), chosen, Modifier.weight(1f)) {
                            Repo.setLook(ctx, st, mode, ac)
                        }
                    }
                }
            }
            Text(
                tr("Appearance"), style = MaterialTheme.typography.titleMedium, color = cs.onBackground,
                modifier = Modifier.padding(top = 12.dp)
            )
            Segmented(
                listOf("system" to tr("System"), "light" to tr("Light"), "dark" to tr("Dark")), mode
            ) { Repo.setLook(ctx, style, it, accent) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One theme: a small page in its colours with its name, a card and a button in it; a tick when chosen. */
@Composable
private fun ThemeCard(name: String, sample: Look, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(132.dp).clip(shape)
            .border(if (chosen) 3.dp else 1.dp, if (chosen) ring else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = name, onClick = onClick)
            .semantics { selected = chosen }
    ) {
        LookTheme(sample) {
            val cs = MaterialTheme.colorScheme
            Column(Modifier.fillMaxSize().background(sample.page).padding(14.dp)) {
                Text(
                    name, style = MaterialTheme.typography.titleMedium, color = cs.primary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 28.dp)
                )
                Spacer(Modifier.weight(1f))
                // A wish card in this theme: a picture, two lines and its button.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(sample.card).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(sample.pictureBg(1L, true)))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.7f)))
                        Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.6f)))
                    }
                    Box(Modifier.width(26.dp).height(14.dp).clip(CircleShape).background(sample.accent))
                }
            }
        }
        if (chosen) Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(ring),
            contentAlignment = Alignment.Center
        ) { Icon(AppIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
    }
}

/** A row of choices in one rounded bar, the chosen one filled in the theme's colour (Appearance). */
@Composable
private fun Segmented(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(if (look.design == "quiet") (if (look.dark) look.raised else look.card) else cs.surfaceVariant)
            .border(1.dp, cs.outlineVariant, CircleShape).padding(4.dp)
    ) {
        options.forEach { (k, label) ->
            val on = k == value
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (on) look.accent else Color.Transparent)
                    .clickable(onClickLabel = label) { onPick(k) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) look.onAccent else cs.onSurface)
            }
        }
    }
}

/** One style in the row under the preview: a small sample and its name. */
@Composable
private fun DesignTile(name: String, sample: Look, chosen: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val box = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(96.dp).height(110.dp).clip(box)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) cs.onSurface else cs.outlineVariant, box)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ThemeSample(sample)
        Text(
            name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            fontWeight = if (chosen) FontWeight.Bold else null, color = cs.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** A little card drawn in a look, on its own background, so the styles can be told apart. */
@Composable
private fun ThemeSample(look: Look) {
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(look.scheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize().lookCard(look, 4L, look.cardShape(4L, small = true))) {
            Box(
                Modifier.padding(look.inset(small = true) / 2).fillMaxWidth().weight(1f)
                    .lookMat(look, look.pictureShape(4L, small = true), look.tints[1 % look.tints.size])
            )
            if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
            Box(
                Modifier.padding(5.dp).width(20.dp).height(5.dp).clip(CircleShape).background(look.accent)
            )
        }
    }
}

/**
 * A sample wishlist in the chosen theme, titled "Preview". Its name, category button and view buttons stay put while
 * the wishes scroll, as on a real list. The view buttons switch between two per row and large cards.
 */
@Composable
private fun ThemePreview(modifier: Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    var grid by remember { mutableStateOf(true) }
    val frame = RoundedCornerShape(28.dp)
    // Ids 4 to 9 give the cards different colours, tints and pebble shapes, as on a real list.
    val sample = listOf(
        Wish(4L, "", tr("Wireless headphones"), tr("Noise cancelling, 30 hours of music."), "", 129.0, 159.0, "EUR", "Tech", star = true),
        Wish(5L, "", tr("Plant pot"), tr("Glazed ceramic, about 20 cm."), "", 24.95, null, "EUR", "Home"),
        Wish(6L, "", tr("Running shoes"), tr("Light, soft and made for long runs."), "", 89.0, 79.0, "EUR", "Fashion"),
        Wish(7L, "", tr("Cookbook"), tr("Simple recipes for every day."), "", 29.99, null, "EUR", "Books"),
        Wish(8L, "", tr("Board game"), tr("For two to six players."), "", 44.5, 49.99, "EUR", "Home"),
        Wish(9L, "", tr("Scented candle"), tr("Fig and cedar, burns for 50 hours."), "", 19.95, null, "EUR", "Home")
    )
    val emojis = listOf("🎧", "🪴", "👟", "📚", "🎲", "🕯️")
    Box(modifier.clip(frame).background(cs.background).border(1.dp, cs.outlineVariant, frame)) {
        Column(Modifier.fillMaxSize().lookBackdrop(look)) {
            // Fixed at the top, like the top of a real list.
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(color = cs.onBackground)) {
                    ListTitle(tr("Preview"), sample.size)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.lookChip(look).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(catLabel("All"), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        Icon(AppIcons.ArrowDropDown, null, tint = cs.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    ViewButton("list", tr("Large cards"), !grid) { grid = false }
                    Spacer(Modifier.width(6.dp))
                    ViewButton("grid", tr("Two per row"), grid) { grid = true }
                }
            }
            // The wishes scroll underneath.
            Column(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (grid) {
                    sample.chunked(2).forEachIndexed { r, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { i, w ->
                                Box(Modifier.weight(1f)) { CompactCard(w, emoji = emojis[r * 2 + i], inert = true) {} }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    sample.forEachIndexed { i, w ->
                        WishCard(w, emoji = emojis[i]) {
                            Icon(AppIcons.Edit, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                            Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) { AddWishButton {} }
    }
}
