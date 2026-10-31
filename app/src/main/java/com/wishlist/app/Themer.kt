package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.RadioButton
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
            shape = shape, containerColor = cs.primary, contentColor = cs.onPrimary, elevation = flat
        )
        "clay" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick,
            modifier = Modifier
                .shadow(16.dp, shape, clip = false, ambientColor = cs.primary, spotColor = cs.primary)
                .clayEdge(look, shape),
            shape = shape, containerColor = cs.primary, contentColor = cs.onPrimary, elevation = flat
        )
        "organic" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick, shape = shape,
            containerColor = cs.primary, contentColor = cs.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(3.dp, 4.dp, 3.dp, 3.dp)
        )
        // Material 3 Expressive: a big squircle in the strong container colour.
        "material" -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick, shape = shape,
            modifier = Modifier.height(64.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary
        )
        else -> ExtendedFloatingActionButton(
            text = text, icon = icon, onClick = onClick,
            containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp)
    )
}

/** One theme in the list: the classic ones by their mode, the designs by their key. */
private class ThemeOption(val classicMode: String?, val design: String?, val name: String, val about: String)

@Composable
private fun themeOptions(): List<ThemeOption> = listOf(
    ThemeOption("system", null, tr("System default"), tr("The original look, light or dark like your phone.")),
    ThemeOption("light", null, tr("Light"), tr("The original look, light.")),
    ThemeOption("dark", null, tr("Dark"), tr("The original look, dark.")),
    ThemeOption("amoled", null, tr("AMOLED black"), tr("The original look on pure black.")),
) + DESIGNS.map { ThemeOption(null, it.key, tr(it.name), tr(it.about)) }

/**
 * The Themer (App settings > Theme > Themer): the four classic themes and the four designs, and the colour sets of
 * the chosen design. Every choice is used at once; the sample wishlist at the top shows how it looks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val sysDark = isSystemInDarkTheme()
    val design = designFor(Repo.design)
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) { onDispose { ThemerNav.open = false } }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Themer"), fontWeight = LocalLook.current.titleWeight) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ThemePreview(Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 16.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionTitle(tr("Theme"))
                themeOptions().forEach { o ->
                    val chosen = if (o.classicMode != null) Repo.design == "classic" && Repo.theme == o.classicMode
                    else Repo.design == o.design
                    val pick = {
                        if (o.classicMode != null) Repo.setClassic(ctx, o.classicMode) else Repo.setDesign(ctx, o.design ?: "classic")
                    }
                    val sample = if (o.classicMode != null) {
                        lookFor("classic", "", if (o.classicMode == "system") (if (sysDark) "dark" else "light") else o.classicMode)
                    } else {
                        lookFor(o.design ?: "classic", if (Repo.design == o.design) Repo.colours else "", "light")
                    }
                    val box = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(box)
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) cs.primary else cs.outlineVariant, box)
                            .clickable { pick() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemeSample(sample)
                        Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
                            Text(o.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text(o.about, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        RadioButton(selected = chosen, onClick = { pick() })
                    }
                    // The colour sets of a design appear right under it once it is chosen.
                    if (chosen && design != null) {
                        Text(
                            tr("Colour combinations made for %s.", tr(design.name)),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        design.sets.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { set ->
                                    ColourOption(set, set.key == colourSetFor(design, Repo.colours).key, Modifier.weight(1f)) {
                                        Repo.setColours(ctx, set.key)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
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
                Modifier.padding(5.dp).width(20.dp).height(5.dp).clip(CircleShape).background(look.scheme.primary)
            )
        }
    }
}

/** One colour set: a round swatch (background, main colour and the two accents) with its name. */
@Composable
private fun ColourOption(set: ColourSet, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
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
            tr(set.name), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = if (chosen) cs.onSurface else cs.onSurfaceVariant, maxLines = 2,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

/**
 * A sample wishlist in the chosen theme: the list name, the category and view buttons, wishes and Add wish.
 * The view buttons switch the sample between two per row and large cards; nothing else reacts to a tap.
 */
@Composable
private fun ThemePreview(modifier: Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    var grid by remember { mutableStateOf(true) }
    val frame = RoundedCornerShape(28.dp)
    // Ids 4, 5, 6 give each card a different colour, tint and pebble shape, as on a real list.
    val sample = listOf(
        Wish(4L, "", tr("Wireless headphones"), tr("Noise cancelling, 30 hours of music."), "", 129.0, 159.0, "EUR", "Tech", star = true),
        Wish(5L, "", tr("Plant pot"), tr("Glazed ceramic, about 20 cm."), "", 24.95, null, "EUR", "Home"),
        Wish(6L, "", tr("Running shoes"), tr("Light, soft and made for long runs."), "", 89.0, 79.0, "EUR", "Fashion")
    )
    val emojis = listOf("🎧", "🪴", "👟")
    Box(modifier.clip(frame).background(cs.background).border(1.dp, cs.outlineVariant, frame)) {
        Column(
            Modifier.fillMaxSize().lookBackdrop(look).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(color = cs.onBackground)) {
                ListTitle(tr("My wishlist"), sample.size)
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
            Spacer(Modifier.height(56.dp))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) { AddWishButton {} }
    }
}
