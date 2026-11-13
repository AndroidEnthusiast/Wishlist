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
            modifier = Modifier.lookSurface(look, shape, look.accent, depth = 8.dp, brush = look.accentBrush),
            shape = shape, containerColor = Color.Transparent, contentColor = look.onAccent, elevation = flat
        )
    }
}

/**
 * The Themer (App settings > Theme): a big sample wishlist in the look being tried, and under it the choices: the
 * style (Quiet and the seven styles, side by side with a small sample each), Light or Dark, and the accent colour
 * (2.97; only Quiet and Gen Z chaos take one since 13 November 2026). Nothing changes in the app until "Apply" (top right, shown once the choice differs from the look in use).
 * Back without Apply keeps the look in use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    val inUseMode = effectiveTheme()
    var tryStyle by remember { mutableStateOf(styleFor(Repo.design).key) }
    var tryMode by remember { mutableStateOf(inUseMode) }
    var tryAccent by remember { mutableStateOf(accentFor(Repo.palette).key) }
    // The colour set of each style being tried (owner, 13 November 2026: at least three per style).
    var trySets by remember { mutableStateOf(Repo.colourSets) }
    val trySet = setFor(tryStyle, trySets[tryStyle].orEmpty()).key
    // The options of each style being tried (decoration amount, card style), "decoration/cards".
    var tryOptions by remember { mutableStateOf(Repo.themeOptions) }
    val tryDeco = tryOptions[tryStyle].orEmpty().substringBefore('/')
    val tryCards = tryOptions[tryStyle].orEmpty().substringAfter('/', "")
    val changed = tryStyle != styleFor(Repo.design).key || tryMode != inUseMode || tryAccent != accentFor(Repo.palette).key ||
        trySet != setFor(Repo.design, Repo.colourSet).key || tryOptions[tryStyle].orEmpty() != Repo.themeOptions[tryStyle].orEmpty()
    var picker by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Apply, then "Theme applied" (owner, 2.101).
    val apply = {
        Repo.setLook(ctx, tryStyle, tryMode, tryAccent, trySet, tryDeco.takeIf { hasOptions(tryStyle) }, tryCards.takeIf { hasOptions(tryStyle) })
        scope.launch { snack.showToast(tr("Theme applied")) }
        Unit
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Themer")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                actions = {
                    if (changed) Button(onClick = apply, modifier = Modifier.padding(end = 8.dp)) { Text(tr("Apply")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            // The preview is drawn in the look being tried out; the rest of the screen keeps the look in use.
            LookTheme(lookFor(tryStyle, tryAccent, tryMode, trySet, tryDeco, tryCards)) {
                ThemePreview(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp))
            }
            Text(
                tr(styleFor(tryStyle).about), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)
            )
            // The styles, side by side; swipe to see them all.
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(126.dp),
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(STYLES.size) { i ->
                    val st = STYLES[i]
                    DesignTile(tr(st.name), lookFor(st.key, tryAccent, tryMode, trySets[st.key].orEmpty()), tryStyle == st.key) { tryStyle = st.key }
                }
            }
            // Light or dark, then the accent colours with the rainbow dot (any colour, owner 2.101) as the last one.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = if (usesAccent(tryStyle)) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("light" to tr("Light"), "dark" to tr("Dark")).forEach { (k, n) ->
                    val on = tryMode == k
                    Text(
                        n, style = MaterialTheme.typography.labelLarge, color = LocalLook.current.chipContent(on),
                        modifier = Modifier.lookChip(LocalLook.current, on).clickable { tryMode = k }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            // The colour sets of a style with its own colours, each chip with a dot of its button colour.
            COLOUR_SETS[tryStyle]?.let { sets ->
                LazyRow(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sets.size) { i ->
                        val set = sets[i]
                        val on = set.key == trySet
                        val sample = lookFor(tryStyle, tryAccent, tryMode, set.key, tryDeco, tryCards)
                        Row(
                            Modifier.lookChip(LocalLook.current, on).clickable(onClickLabel = tr(set.name)) { trySets = trySets + (tryStyle to set.key) }
                                .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape)
                                    .then(if (sample.accentBrush != null) Modifier.background(sample.accentBrush) else Modifier.background(sample.accent))
                                    .border(1.5.dp, LocalLook.current.chipContent(on), CircleShape)
                            )
                            Text(
                                tr(set.name), style = MaterialTheme.typography.labelLarge, color = LocalLook.current.chipContent(on),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            // The style's options (owner, 13 November 2026): how much decoration, and the card style.
            if (hasOptions(tryStyle)) {
                val cardChoices = CARD_STYLES.getValue(tryStyle)
                val decoNow = DECO_LEVELS.firstOrNull { it.key == tryDeco }?.key ?: "normal"
                val cardsNow = cardStyleFor(tryStyle, trySet, tryCards)
                OptionRow(tr("Decoration"), DECO_LEVELS, decoNow) { tryOptions = tryOptions + (tryStyle to "$it/$tryCards") }
                OptionRow(tr("Cards"), cardChoices, cardsNow) { tryOptions = tryOptions + (tryStyle to "$tryDeco/$it") }
            }
            // The accent colours: only for the styles that take one (Quiet and Gen Z chaos); the others have their own
            // colours (owner, 13 November 2026), so the row would change nothing there.
            if (usesAccent(tryStyle)) Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ACCENTS.forEach { ac ->
                    val on = ac.key == tryAccent
                    val col = Color(if (tryMode == "light") ac.light else ac.dark)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .border(if (on) 2.dp else 0.dp, if (on) cs.onSurface else Color.Transparent, CircleShape)
                            .padding(if (on) 4.dp else 0.dp).clip(CircleShape).background(col)
                            .clickable(onClickLabel = tr(ac.name)) { tryAccent = ac.key }
                    )
                }
                // The rainbow dot: opens the colour ball. Once a colour of your own is chosen, it shows in the middle.
                val own = customAccent(tryAccent)
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .border(if (own != null) 2.dp else 0.dp, if (own != null) cs.onSurface else Color.Transparent, CircleShape)
                        .padding(if (own != null) 4.dp else 0.dp).rainbowDot()
                        .clickable(onClickLabel = tr("Any colour")) { picker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (own != null) Box(
                        Modifier.size(14.dp).clip(CircleShape).background(Color(if (tryMode == "light") own.light else own.dark))
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
    if (picker) ColourPickerDialog(tryAccent, onPick = { tryAccent = it; picker = false }, onClose = { picker = false })
}

/** A row of choices for one option of a style: its name, then a chip per choice (the chosen one filled). */
@Composable
private fun OptionRow(name: String, choices: List<ThemeOption>, chosen: String, onPick: (String) -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        choices.forEach { ch ->
            val on = ch.key == chosen
            Text(
                tr(ch.name), style = MaterialTheme.typography.labelLarge, color = look.chipContent(on),
                modifier = Modifier.lookChip(look, on).clickable(onClickLabel = tr(ch.name)) { onPick(ch.key) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
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
            // Long names ("Anthropomorphic") a size smaller so they fit the tile.
            name, style = if (name.length > 12) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
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
