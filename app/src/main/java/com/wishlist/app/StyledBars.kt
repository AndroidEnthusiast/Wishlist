package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar as M3TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/* The app's own dialogs, top bar, icon button, titles and badges in the chosen style (see Styled.kt). */

/** [t] with LocalInDialog on, so its buttons are the compact size. */
private fun inDialog(t: @Composable () -> Unit): @Composable () -> Unit = {
    CompositionLocalProvider(LocalInDialog provides true) { t() }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    // A second choice next to the main one ("Keep both", …): the outlined button under it (docs/STYLE.md "Dialogs").
    secondButton: (@Composable () -> Unit)? = null
) {
    // Buttons inside the dialog's text are the compact size (LocalInDialog, docs/STYLE.md).
    val body: (@Composable () -> Unit)? = if (text == null) null else inDialog(text)
    // One layout in every style (owner, 21 November 2026: "uniformity for buttons and clicks"): the buttons stacked and
    // full width, the main one filled, a second choice outlined, Cancel / Close plain words at the bottom. Before,
    // Gen Z chaos had words in a row and two buttons in one slot made the second invisible in Quiet (Move or copy).
    QuietDialog(onDismissRequest, confirmButton, modifier, dismissButton, title, body, properties, secondButton)
}

/** True inside a top bar: Quiet's icon buttons there sit in soft round buttons (2.210). */
val LocalInTopBar = compositionLocalOf { false }

/**
 * The collapsing title of Quiet's screens (2.210, owner 15 November 2026: Google's flat top bar was one of the "standard
 * Android" parts): set by [Scaffold], read by [TopAppBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopBarScroll = compositionLocalOf<TopBarScroll?> { null }

/**
 * The page's scrolling and the large title (2.210): the title only takes part in scrolling while a large title is on
 * screen ([active]). Without that, a screen with a one-line bar (Home, Wishlist settings) could not scroll at all: the
 * collapsing title used up every swipe (owner, 15 November 2026).
 */
@OptIn(ExperimentalMaterial3Api::class)
class TopBarScroll(val behavior: androidx.compose.material3.TopAppBarScrollBehavior) {
    var active by androidx.compose.runtime.mutableStateOf(false)
    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
            if (active) behavior.nestedScrollConnection.onPreScroll(available, source) else Offset.Zero
        override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
            if (active) behavior.nestedScrollConnection.onPostScroll(consumed, available, source) else Offset.Zero
        override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity) =
            if (active) behavior.nestedScrollConnection.onPreFling(available) else androidx.compose.ui.unit.Velocity.Zero
        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
            if (active) behavior.nestedScrollConnection.onPostFling(consumed, available) else androidx.compose.ui.unit.Velocity.Zero
    }
}

/** True inside a large (collapsing) title: [ScreenTitle] then takes the bar's own sizes. */
val LocalInLargeBar = compositionLocalOf { false }

/**
 * A screen's top bar. Quiet (2.210): a large title in Fraunces that shrinks smoothly into the bar as you scroll, and the
 * ← and icons in soft round buttons. [large] false: one line from the start (a chat's name and picture, "2 selected",
 * a title with a second line under it, Home's wordmark).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    large: Boolean = true
) {
    val look = LocalLook.current
    // A line under the top bar in Gen Z chaos: thick ink. Quiet: none.
    val line = when (look.design) {
        "quiet" -> Modifier
        else -> Modifier.drawWithContent {
            drawContent()
            val u = 1.dp.toPx()
            drawRect(look.line, Offset(0f, size.height - 3 * u), Size(size.width, 3 * u))
        }
    }
    val nav: @Composable () -> Unit = { CompositionLocalProvider(LocalInTopBar provides true) { navigationIcon() } }
    val acts: @Composable RowScope.() -> Unit = { CompositionLocalProvider(LocalInTopBar provides true) { actions() } }
    val scroll = LocalTopBarScroll.current
    // The big title that shrinks as you scroll, in every style (owner, 21 November 2026: "big title everywhere"; before,
    // only Quiet, which made the Theme screen jump when switching styles).
    if (large && scroll != null) {
        androidx.compose.runtime.DisposableEffect(scroll) { scroll.active = true; onDispose { scroll.active = false } }
        androidx.compose.material3.LargeTopAppBar(
            { CompositionLocalProvider(LocalInLargeBar provides true) { title() } }, modifier.then(line), nav, acts,
            colors = colors.copy(scrolledContainerColor = colors.containerColor), scrollBehavior = scroll.behavior
        )
    } else M3TopAppBar(title, modifier.then(line), nav, acts, colors = colors)
}

/**
 * An icon button: Material's (48 dp to tap) with the app's tap feel (it sinks a little, Feel.kt). In Quiet's top bars the
 * icon sits in a soft round button (2.210), like the app's own controls rather than Android's bare icons.
 */
@Composable
fun IconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, round: Boolean = true,
    content: @Composable () -> Unit
) {
    val look = LocalLook.current
    val round = round && LocalInTopBar.current && look.design == "quiet"
    androidx.compose.material3.IconButton(onClick, modifier.pressScale(), enabled) {
        if (round) Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (look.dark) look.raised else Color.White),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { content() } }
        else content()
    }
}

/** The name of a list in its top bar, the same in every theme. ([count] is no longer shown: the owner removed
 * Organic's "7 WISHES" line in 2.60.) */
@Composable
fun ListTitle(name: String, @Suppress("UNUSED_PARAMETER") count: Int) {
    ScreenTitle(name)
}

/** A screen's title in the top bar: one line, the style's title weight, cut off with … when too long. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTitle(text: String, big: Boolean = false) {
    // [big]: the main tabs (Messages, Friends, My shops), as large as Home's wordmark in Quiet (2.207: 28).
    val look = LocalLook.current
    Text(
        text, fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
        // Quiet's large top bar (2.210) gives every screen the 28 title, shrinking to 20 as you scroll; [big] is kept
        // for a one-line bar.
        style = if (big && look.design == "quiet" && !LocalInLargeBar.current) MaterialTheme.typography.headlineSmall else LocalTextStyle.current
    )
}

/** The price change on a wish ("−20%", "+5%"; plus and minus, not arrows: it isn't a stock trade). */
@Composable
fun PriceBadge(text: String, rise: Boolean, small: Boolean) {
    val look = LocalLook.current
    val col = if (rise) look.rise else look.drop
    val bg: Color
    val fg: Color
    when (look.design) {
        // Chaos and Bauhaus: bold pastel blocks with ink text.
        "chaos" -> { bg = if (rise) Color(0xFFFFB3B3) else Color(0xFFB8F5C8); fg = Color(0xFF111111) }
        else -> { bg = lerp(col, look.scheme.surfaceVariant, if (look.dark) 0.78f else 0.88f); fg = col }
    }
    StyleBadge(text, bg, fg, small, null)
}

/** A status on a wish ("Owned", "Reserved"). */
@Composable
fun StatusBadge(text: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val bg = when (look.design) {
        "quiet" -> if (look.dark) look.raised else look.page
        else -> cs.surfaceVariant
    }
    StyleBadge(text, bg, cs.onSurface, small, null, modifier)
}

@Composable
private fun StyleBadge(text: String, bg: Color, fg: Color, small: Boolean, lineColor: Color?, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val shape = look.badgeShape(small)
    // The line drawn with edgeLine (2.202: Modifier.border was slow on irregular shapes in lists). One height for every
    // label on a photo, its text in the middle (owner, 20 November 2026: the price and Love labels didn't line up).
    androidx.compose.foundation.layout.Box(
        modifier.heightIn(min = badgeHeight(small)).clip(shape).background(bg)
            .edgeLine(if (look.edge > 0.dp) minOf(look.edge, 2.dp) else 0.dp, lineColor ?: look.line, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = fg, maxLines = 1,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 4.dp)
        )
    }
}

/** The height of every label on a photo (the price change, Love, a status): small on Two per row and Rows. */
fun badgeHeight(small: Boolean): androidx.compose.ui.unit.Dp = if (small) 22.dp else 28.dp
