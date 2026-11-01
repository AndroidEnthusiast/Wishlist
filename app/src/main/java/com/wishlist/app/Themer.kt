package com.wishlist.app

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
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
        Text(tr("Add wish"), fontWeight = if (look.classic) null else FontWeight.Bold)
    }
    val icon: @Composable () -> Unit = { Icon(AppIcons.Add, null) }
    when (look.design) {
        "neo" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick,
            modifier = Modifier.hardShadow(look.line, shape, 4.dp).border(2.dp, look.line, shape),
            shape = shape, containerColor = look.accent, contentColor = look.onAccent, elevation = flat
        )
        "clay" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick,
            modifier = Modifier.clay(look, shape, look.accent, 12.dp),
            shape = shape, containerColor = Color.Transparent, contentColor = look.onAccent, elevation = flat
        )
        "organic" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick, shape = shape,
            containerColor = look.accent, contentColor = look.onAccent,
            elevation = FloatingActionButtonDefaults.elevation(3.dp, 4.dp, 3.dp, 3.dp)
        )
        // Material 3 Expressive: a big squircle in the strong container colour.
        "material" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick, shape = shape,
            modifier = Modifier.height(64.dp),
            containerColor = look.accent, contentColor = look.onAccent
        )
        else -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick,
            containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer
        )
    }
}

/**
 * The Themer (App settings > Theme > Themer): a big sample wishlist, and below it the designs side by side. Tapping a
 * design shows it in the preview and pops up its colour sets ("Standard" pops up Light, Dark and AMOLED black).
 * Nothing changes in the app until "Apply" (top right, shown once the choice differs from the theme in use).
 * Back without Apply keeps the theme in use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) { onDispose { ThemerNav.open = false } }
    // The choice being tried out: a design (or "classic"), its colour set, and the standard theme for "classic".
    val inUseMode = effectiveTheme()
    var tryDesign by remember { mutableStateOf(Repo.design) }
    var tryColours by remember { mutableStateOf(Repo.colours) }
    var tryMode by remember { mutableStateOf(inUseMode) }
    // The colour set of the standard themes (one of PALETTES).
    var tryPalette by remember { mutableStateOf(Repo.palette) }
    // Standard: first Light / Dark / AMOLED black, then (after picking one) the colour sets made for it.
    var standardColours by remember { mutableStateOf(false) }
    val tryD = designFor(tryDesign)
    val changed = tryDesign != Repo.design || (
        if (tryD == null) tryMode != inUseMode || tryPalette != Repo.palette
        else colourSetFor(tryD, tryColours).key != colourSetFor(tryD, Repo.colours).key
    )
    val apply = {
        if (tryD == null) { Repo.setClassic(ctx, tryMode); Repo.setPalette(ctx, tryPalette) }
        else { Repo.setDesign(ctx, tryD.key); Repo.setColours(ctx, colourSetFor(tryD, tryColours).key) }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Themer"), fontWeight = LocalLook.current.titleWeight) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                actions = {
                    if (changed) Button(onClick = apply, modifier = Modifier.padding(end = 8.dp)) { Text(tr("Apply")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Spacer(Modifier.height(16.dp))
            // The preview is drawn in the theme being tried out; the rest of the screen keeps the theme in use.
            LookTheme(lookFor(tryDesign, if (tryD == null) tryPalette else tryColours, tryMode)) {
                ThemePreview(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp))
            }
            // The colour sets of the chosen design pop up between the preview and the designs.
            AnimatedContent(
                targetState = tryDesign,
                transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() },
                label = "colourSets"
            ) { key ->
                val d = designFor(key)
                if (d != null) ColourSets(d, colourSetFor(d, tryColours).key) { tryColours = it }
                else if (!standardColours) StandardThemes(tryMode) { tryMode = it; standardColours = true }
                else StandardColours(tryMode, tryPalette, onPick = { tryPalette = it }, onModes = { standardColours = false })
            }
            // The designs, side by side; swipe to see them all.
            // All tiles have the same fixed height, so the row never changes height (and nothing jumps) while swiping.
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(132.dp),
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DesignTile(tr("Standard"), lookFor("classic", tryPalette, tryMode), tryDesign == "classic") {
                        tryDesign = "classic"
                        standardColours = false
                    }
                }
                items(DESIGNS.size) { i ->
                    val d = DESIGNS[i]
                    val chosen = tryDesign == d.key
                    DesignTile(tr(d.name), lookFor(d.key, if (chosen) tryColours else "", "light"), chosen) {
                        // A design keeps the colour set when it has one with that name, otherwise its first.
                        if (d.sets.none { it.key == tryColours }) tryColours = d.sets[0].key
                        tryDesign = d.key
                    }
                }
            }
        }
    }
}

/** One design in the row under the preview: a small sample and its name. */
@Composable
private fun DesignTile(name: String, sample: Look, chosen: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val box = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(96.dp).height(112.dp).clip(box)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) cs.primary else cs.outlineVariant, box)
            .background(if (chosen) cs.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
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

/** The standard themes (Light, Dark, AMOLED black), in a card that pops up above the tiles when Standard is chosen. */
@Composable
private fun StandardThemes(chosen: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val card = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .clip(card).background(cs.surfaceContainerHigh).border(1.dp, cs.outlineVariant, card)
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            tr("Standard themes"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf("light" to tr("Light"), "dark" to tr("Dark"), "amoled" to tr("AMOLED black")).forEach { (k, n) ->
                val look = lookFor("classic", "", k)
                // A swatch like the colour sets: the background with the indigo of that theme.
                val set = ColourSet(
                    k, n, look.scheme.primary.toArgb().toLong() and 0xFFFFFFFFL,
                    look.scheme.primaryContainer.toArgb().toLong() and 0xFFFFFFFFL,
                    look.scheme.surfaceVariant.toArgb().toLong() and 0xFFFFFFFFL,
                    look.scheme.background.toArgb().toLong() and 0xFFFFFFFFL,
                    look.scheme.onBackground.toArgb().toLong() and 0xFFFFFFFFL
                )
                ColourOption(set, chosen == k, Modifier.weight(1f), translate = false) { onPick(k) }
            }
        }
    }
}

/**
 * The colour sets of a standard theme, after Light, Dark or AMOLED black was picked: each set in the tones made for
 * that mode. "Light, Dark or AMOLED black" goes back to the modes.
 */
@Composable
private fun StandardColours(mode: String, chosen: String, onPick: (String) -> Unit, onModes: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val card = RoundedCornerShape(24.dp)
    val modeName = when (mode) { "dark" -> tr("Dark"); "amoled" -> tr("AMOLED black"); else -> tr("Light") }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .clip(card).background(cs.surfaceContainerHigh).border(1.dp, cs.outlineVariant, card)
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Colours for %s", modeName), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onModes) { Text(tr("Light, dark or AMOLED")) }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            verticalAlignment = Alignment.Top,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(PALETTES.size) { i ->
                val p = PALETTES[i]
                val sc = lookFor("classic", p.key, mode).scheme
                fun argb(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL
                val set = ColourSet(p.key, p.name, argb(sc.primary), argb(sc.primaryContainer), argb(sc.surfaceVariant), argb(sc.background), argb(sc.onBackground))
                ColourOption(set, p.key == chosen, Modifier.width(80.dp)) { onPick(p.key) }
            }
        }
    }
}

/** The colour sets of a design, in a card that pops up above the designs. */
@Composable
private fun ColourSets(d: Design, chosen: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val card = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .clip(card).background(cs.surfaceContainerHigh).border(1.dp, cs.outlineVariant, card)
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            tr("Colour combinations made for %s.", tr(d.name)),
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        // One fixed height for the row and every option (a name may take two lines), so nothing jumps while swiping.
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            verticalAlignment = Alignment.Top,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(d.sets.size) { i ->
                val set = d.sets[i]
                ColourOption(set, set.key == chosen, Modifier.width(80.dp)) { onPick(set.key) }
            }
        }
    }
}

/** A little card drawn in a theme, on its own background, so the themes can be told apart. */
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
                    .clip(look.pictureShape(4L, small = true)).background(look.tints[1 % look.tints.size])
            )
            if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
            Box(
                Modifier.padding(5.dp).width(20.dp).height(5.dp).clip(CircleShape).background(look.accent)
            )
        }
    }
}

/** One colour set: a round swatch (background, main colour and the two accents) with its name. */
@Composable
private fun ColourOption(set: ColourSet, chosen: Boolean, modifier: Modifier, translate: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.height(104.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(52.dp)) {
                val r = size.minDimension / 2 - 5.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2, r * 2)
                drawCircle(Color(set.paper), r, center)
                drawArc(Color(set.main), 90f, 180f, true, tl, sz)
                drawArc(Color(set.a2), 270f, 90f, true, tl, sz)
                drawArc(Color(set.a3), 0f, 90f, true, tl, sz)
                // The background of the set as a ring, so a dark set shows it is dark.
                drawCircle(Color(set.paper), r - 1.5f.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                drawCircle(Color(set.ink).copy(alpha = 0.35f), r, center, style = Stroke(1.dp.toPx()))
                if (chosen) drawCircle(cs.primary, r + 3.5f.dp.toPx(), center, style = Stroke(2.5f.dp.toPx()))
            }
            if (chosen) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            if (translate) tr(set.name) else set.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = if (chosen) cs.onSurface else cs.onSurfaceVariant, maxLines = 2,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
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
