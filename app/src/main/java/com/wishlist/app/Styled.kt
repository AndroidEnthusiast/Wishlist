package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.offset
import coil.imageLoader
import coil.memory.MemoryCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.automirrored.sharp.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.Switch as M3Switch
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.material3.TopAppBar as M3TopAppBar

/*
 * The app's own Button, OutlinedButton, TextButton, FilledTonalButton, Switch, AlertDialog and TopAppBar. The screens
 * use these (they don't import the Material ones), so every screen takes the style of the chosen theme. In the classic
 * themes they are exactly the Material components, as before.
 */

/** Text fields for names and notes start with a capital letter (the keyboard does it; you can still change it). */
val Capitalised = androidx.compose.foundation.text.KeyboardOptions(
    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
)

/*
 * The app's buttons, switches, dialogs and top bar in the chosen style (Design.kt, 2.97). Material's own parts are
 * used underneath; outside Quiet they are see-through and the style's surface ([lookSurface]) is drawn behind them.
 */

/**
 * True inside a dialog's text (docs/STYLE.md): buttons there are the compact size, so they stay in proportion to the
 * dialog's text, and they keep room for their shadow so a scrolling dialog never cuts them off.
 */
val LocalInDialog = compositionLocalOf { false }

/** The two button sizes of docs/STYLE.md: regular on screens (46 dp), compact in dialogs (40 dp, a smaller shadow). */
private class ButtonSize(val height: androidx.compose.ui.unit.Dp, val pad: PaddingValues, val depth: androidx.compose.ui.unit.Dp, val room: androidx.compose.ui.unit.Dp)

@Composable
private fun buttonSize(): ButtonSize = if (LocalInDialog.current)
    ButtonSize(40.dp, PaddingValues(horizontal = 18.dp, vertical = 8.dp), 3.dp, 6.dp)
else ButtonSize(46.dp, PaddingValues(horizontal = 22.dp, vertical = 10.dp), 5.dp, 0.dp)

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val shape = look.buttonShape
    val z = buttonSize()
    if (look.design == "quiet") {
        // Quiet: a calm pill in the accent colour, a little taller than Material's.
        M3Button(
            onClick, modifier.padding(vertical = z.room).heightIn(min = z.height), enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = look.accent, contentColor = look.onAccent),
            contentPadding = z.pad, content = content
        )
    } else {
        M3Button(
            onClick, modifier.padding(vertical = z.room).heightIn(min = z.height)
                .then(if (enabled) Modifier.lookSurface(look, shape, look.accent, depth = z.depth, brush = look.accentBrush) else Modifier),
            enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = look.onAccent),
            contentPadding = z.pad, content = content
        )
    }
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.buttonShape
    val z = buttonSize()
    if (look.design == "quiet") {
        // Quiet: a soft tinted pill with the accent as text (the "secondary" button of iOS).
        M3Button(
            onClick, modifier.padding(vertical = z.room).heightIn(min = z.height), enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = cs.primaryContainer, contentColor = if (look.dark) look.accent else cs.onPrimaryContainer),
            contentPadding = z.pad, content = content
        )
    } else {
        M3Button(
            onClick, modifier.padding(vertical = z.room).heightIn(min = z.height)
                .then(if (enabled) Modifier.lookSurface(look, shape, cs.surfaceVariant, depth = z.depth) else Modifier),
            enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = cs.onSurface),
            contentPadding = z.pad, content = content
        )
    }
}

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    OutlinedButton(onClick, modifier, enabled, content)

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    // Chaos: ink text (its accent is for blocks); the others: the style's readable text colour (its primary; in Quiet
    // that is the accent).
    val col = if (look.design == "chaos") cs.onSurface else cs.primary
    M3TextButton(
        onClick, modifier, enabled, shape = look.buttonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = col), content = content
    )
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    when (look.design) {
        "chaos" -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = look.onAccent, checkedTrackColor = look.accent, checkedBorderColor = look.line,
                uncheckedThumbColor = look.line, uncheckedTrackColor = cs.surfaceVariant, uncheckedBorderColor = look.line
            )
        )
        "quiet" -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = look.accent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = if (look.dark) Color(0xFF39393D) else Color(0xFFE5E5EA),
                uncheckedBorderColor = Color.Transparent
            )
        )
        // The other styles: on in the style's block colour, off with the style's line (Flat: a darker tint, no line).
        else -> {
            val edge = if (look.design == "flat") cs.outline else look.line
            M3Switch(
                checked, onCheckedChange, modifier, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = look.onAccent, checkedTrackColor = look.accent,
                    checkedBorderColor = if (look.edge > 0.dp) look.line else look.accent,
                    uncheckedThumbColor = edge, uncheckedTrackColor = cs.surfaceVariant, uncheckedBorderColor = edge
                )
            )
        }
    }
}
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
    properties: DialogProperties = DialogProperties()
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.dialogShape
    // Buttons inside the dialog's text are the compact size (LocalInDialog, docs/STYLE.md).
    val body: (@Composable () -> Unit)? = if (text == null) null else inDialog(text)
    if (look.design == "quiet") {
        M3AlertDialog(
            onDismissRequest, confirmButton, modifier, dismissButton, title = title, text = body,
            shape = shape, containerColor = cs.surfaceContainerHigh, tonalElevation = 0.dp, properties = properties
        )
    } else {
        M3AlertDialog(
            onDismissRequest, confirmButton, modifier.lookSurface(look, shape, cs.surfaceContainerHigh, depth = 10.dp), dismissButton,
            title = title, text = body, shape = shape, containerColor = Color.Transparent, tonalElevation = 0.dp, properties = properties
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors()
) {
    val look = LocalLook.current
    // A line under the top bar in the style's own way: thick ink (chaos), a holographic line (Y2K), a printed double
    // line (Retro), three racing stripes (Pop futurism), a rough ink line (Grunge), red, yellow and blue blocks
    // (Bauhaus). Quiet and Flat: none.
    val line = when (look.design) {
        "quiet", "flat" -> Modifier
        else -> Modifier.drawWithContent {
            drawContent()
            val u = 1.dp.toPx()
            val w = size.width; val b = size.height
            when (look.design) {
                "y2k" -> drawRect(look.accentBrush!!, Offset(0f, b - 2 * u), Size(w, 2 * u))
                "retro" -> {
                    drawRect(look.line, Offset(0f, b - 2 * u), Size(w, 2 * u))
                    drawRect(look.line, Offset(0f, b - 5 * u), Size(w, 1 * u))
                }
                "pop" -> listOf(look.art[3], look.art[1], look.art[0]).forEachIndexed { i, col ->
                    drawRect(col, Offset(0f, b - (3 - i) * 2 * u), Size(w, 2 * u))
                }
                "grunge" -> {
                    var x = 0f; var k = 0
                    while (x < w) {
                        val t = (2 + (k * 7 % 5) * 0.35f) * u
                        drawRect(look.line, Offset(x, b - t), Size(9 * u, t)); x += 8 * u; k++
                    }
                }
                // Doodle: a wavy marker line.
                "doodle" -> {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, b - 3 * u); var x = 0f
                        while (x < w) { quadraticTo(x + 10 * u, b - 6 * u, x + 20 * u, b - 3 * u); quadraticTo(x + 30 * u, b, x + 40 * u, b - 3 * u); x += 40 * u }
                    }
                    drawPath(p, look.line, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                // Cottage-core: a fine line and a row of stitches.
                "cottage" -> {
                    drawRect(look.line, Offset(0f, b - 1.5f * u), Size(w, 1.5f * u))
                    var x = 4 * u
                    while (x < w) { drawRect(look.art[1], Offset(x, b - 5 * u), Size(6 * u, 1.5f * u)); x += 12 * u }
                }
                "japandi" -> drawRect(look.line.copy(alpha = 0.6f), Offset(0f, b - 1 * u), Size(w, 1 * u))
                // Risograph: a pink and a blue line printed slightly off each other.
                "riso" -> {
                    drawRect(look.art[0], Offset(0f, b - 4 * u), Size(w, 3 * u))
                    drawRect(look.art[1], Offset(3 * u, b - 3 * u), Size(w, 2 * u), alpha = 0.8f)
                }
                // Synthwave: a neon sunset line with a soft glow.
                "synth" -> {
                    drawRect(look.line.copy(alpha = 0.25f), Offset(0f, b - 6 * u), Size(w, 6 * u))
                    drawRect(look.accentBrush!!, Offset(0f, b - 2 * u), Size(w, 2 * u))
                }
                // Anthropomorphic: a thick ink line with coloured dots on it.
                "anthro" -> {
                    drawRect(look.line, Offset(0f, b - 2.5f * u), Size(w, 2.5f * u))
                    listOf(look.art[0], look.art[3], look.art[2]).forEachIndexed { i, col -> drawCircle(col, 4 * u, Offset(w - (16 + i * 12) * u, b - 1.25f * u)) }
                }
                // Swiss: a heavy black rule and a short colour bar at its start.
                "swiss" -> {
                    drawRect(look.line, Offset(0f, b - 3 * u), Size(w, 3 * u))
                    drawRect(look.art[0], Offset(16 * u, b - 7 * u), Size(40 * u, 4 * u))
                }
                // Punk: a strip of torn tape and a ragged ink line.
                "punk" -> {
                    var x = 0f; var k = 0
                    while (x < w) {
                        val t = (2 + (k * 5 % 4) * 0.5f) * u
                        drawRect(look.line, Offset(x, b - t), Size(7 * u, t)); x += 6 * u; k++
                    }
                    drawRect(look.art[0], Offset(w * 0.6f, b - 6 * u), Size(w * 0.3f, 3 * u))
                }
                "bauhaus" -> {
                    drawRect(look.art[0], Offset(0f, b - 4 * u), Size(w * 0.5f, 4 * u))
                    drawRect(look.art[2], Offset(w * 0.5f, b - 4 * u), Size(w * 0.25f, 4 * u))
                    drawRect(look.art[1], Offset(w * 0.75f, b - 4 * u), Size(w * 0.25f, 4 * u))
                }
                else -> drawRect(look.line, Offset(0f, b - 3 * u), Size(w, 3 * u))
            }
        }
    }
    // The style's effect on big titles (Risograph, Synthwave) goes on the screen's title.
    val shadow = look.titleShadow
    val titled: @Composable () -> Unit = if (shadow == null) title else ({
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(shadow = shadow)) { title() }
    })
    M3TopAppBar(titled, modifier.then(line), navigationIcon, actions, colors = colors)
}

/** The name of a list in its top bar, the same in every theme. ([count] is no longer shown: the owner removed
 * Organic's "7 WISHES" line in 2.60.) */
@Composable
fun ListTitle(name: String, @Suppress("UNUSED_PARAMETER") count: Int) {
    Text(name, fontWeight = LocalLook.current.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        "chaos", "bauhaus", "anthro", "doodle", "punk" -> { bg = if (rise) Color(0xFFFFB3B3) else Color(0xFFB8F5C8); fg = Color(0xFF111111) }
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
        "quiet" -> if (look.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
        "flat", "pop" -> cs.primaryContainer
        else -> cs.surfaceVariant
    }
    StyleBadge(text, bg, cs.onSurface, small, null, modifier)
}

@Composable
private fun StyleBadge(text: String, bg: Color, fg: Color, small: Boolean, lineColor: Color?, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val shape = look.badgeShape(small)
    val border = if (look.edge > 0.dp) BorderStroke(minOf(look.edge, 2.dp), lineColor ?: look.line) else null
    Surface(color = bg, shape = shape, modifier = modifier, border = border) {
        Text(
            text, color = fg,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 4.dp)
        )
    }
}

/**
 * The icons of the app, in the style of the theme: filled (classic, as before), rounded (Material 3, clay),
 * thin outlined (organic) or sharp (neobrutalism). All from Google's free Material icons.
 */
object AppIcons {
    @Composable
    private fun v(f: ImageVector, o: ImageVector, r: ImageVector, s: ImageVector): ImageVector =
        when (LocalLook.current.iconStyle) {
            "outlined" -> o
            "rounded" -> r
            "sharp" -> s
            else -> f
        }

    val Add: ImageVector @Composable get() = v(Icons.Filled.Add, Icons.Outlined.Add, Icons.Rounded.Add, Icons.Sharp.Add)
    val ArrowBack: ImageVector @Composable get() = v(
        Icons.AutoMirrored.Filled.ArrowBack, Icons.AutoMirrored.Outlined.ArrowBack,
        Icons.AutoMirrored.Rounded.ArrowBack, Icons.AutoMirrored.Sharp.ArrowBack
    )
    val ArrowDropDown: ImageVector @Composable get() =
        v(Icons.Filled.ArrowDropDown, Icons.Outlined.ArrowDropDown, Icons.Rounded.ArrowDropDown, Icons.Sharp.ArrowDropDown)
    val Check: ImageVector @Composable get() = v(Icons.Filled.Check, Icons.Outlined.Check, Icons.Rounded.Check, Icons.Sharp.Check)
    val Close: ImageVector @Composable get() = v(Icons.Filled.Close, Icons.Outlined.Close, Icons.Rounded.Close, Icons.Sharp.Close)
    val Delete: ImageVector @Composable get() = v(Icons.Filled.Delete, Icons.Outlined.Delete, Icons.Rounded.Delete, Icons.Sharp.Delete)
    val Edit: ImageVector @Composable get() = v(Icons.Filled.Edit, Icons.Outlined.Edit, Icons.Rounded.Edit, Icons.Sharp.Edit)
    val Favorite: ImageVector @Composable get() =
        v(Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite, Icons.Sharp.Favorite)
    val Home: ImageVector @Composable get() = v(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home, Icons.Sharp.Home)
    val Lock: ImageVector @Composable get() = v(Icons.Filled.Lock, Icons.Outlined.Lock, Icons.Rounded.Lock, Icons.Sharp.Lock)
    val ListIcon: ImageVector @Composable get() = v(
        Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List,
        Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Sharp.List
    )
    val Notifications: ImageVector @Composable get() =
        v(Icons.Filled.Notifications, Icons.Outlined.Notifications, Icons.Rounded.Notifications, Icons.Sharp.Notifications)
    val MoreVert: ImageVector @Composable get() = v(Icons.Filled.MoreVert, Icons.Outlined.MoreVert, Icons.Rounded.MoreVert, Icons.Sharp.MoreVert)
    val Person: ImageVector @Composable get() = v(Icons.Filled.Person, Icons.Outlined.Person, Icons.Rounded.Person, Icons.Sharp.Person)
    val Refresh: ImageVector @Composable get() = v(Icons.Filled.Refresh, Icons.Outlined.Refresh, Icons.Rounded.Refresh, Icons.Sharp.Refresh)
    val Search: ImageVector @Composable get() = v(Icons.Filled.Search, Icons.Outlined.Search, Icons.Rounded.Search, Icons.Sharp.Search)
    val Settings: ImageVector @Composable get() = v(Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Rounded.Settings, Icons.Sharp.Settings)
    val Share: ImageVector @Composable get() = v(Icons.Filled.Share, Icons.Outlined.Share, Icons.Rounded.Share, Icons.Sharp.Share)
    /** Wishlist groups (2.108): Google's Material "group" icon (Apache 2.0), drawn from its path here because the app
     *  only includes the core icon set. One shape in every style. */
    val Group: ImageVector get() = groupIcon
    /** Received gifts and gift lists (2.129): Google's Material "redeem" gift-box icon, drawn from its path. */
    val Gift: ImageVector get() = giftIcon
    /** Gifts I reserved (2.129): Google's Material "shopping bag" icon (outlined), drawn from its path. */
    val Bag: ImageVector get() = bagIcon
    /** Gift exchange (2.129): Google's Material "swap horiz" icon, drawn from its path. */
    val Swap: ImageVector get() = swapIcon
    /** Gift exchange in the side menu (owner, 7 November 2026): two gifts with an arrow from each to the other, our own drawing. */
    val Exchange: ImageVector get() = exchangeIcon
    /** Discover, the shops (owner, 7 November 2026): Google's Material "store" icon, drawn from its path. */
    val Store: ImageVector get() = storeIcon
    /** "Your wishlists ›" (owner, 7 November 2026): Google's Material "chevron right" icon, drawn from its path. */
    val Chevron: ImageVector get() = chevronIcon
}

private val exchangeIcon: ImageVector by lazy {
    // Line drawing on the 24 × 24 grid of Material icons: a gift bottom left, a gift top right, and two curved arrows
    // swapping them (our own drawing, allowed by the icon rule).
    val b = ImageVector.Builder("Exchange", 24.dp, 24.dp, 24f, 24f)
    fun line(d: String) {
        b.addPath(
            androidx.compose.ui.graphics.vector.addPathNodes(d), fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }
    // The gift bottom left: lid, box, ribbon, bow.
    line("M2,11h9v2.5h-9z"); line("M3,13.5h7v7.5h-7z"); line("M6.5,11v10")
    line("M6.5,11c-1.4,-2.4 -3.4,-1.6 -2.4,0M6.5,11c1.4,-2.4 3.4,-1.6 2.4,0")
    // The gift top right.
    line("M13,4h9v2.5h-9z"); line("M14,6.5h7v6h-7z"); line("M17.5,4v8.5")
    line("M17.5,4c-1.4,-2.4 -3.4,-1.6 -2.4,0M17.5,4c1.4,-2.4 3.4,-1.6 2.4,0")
    // The arrows: up and over to the right, and back under to the left.
    line("M4.5,8.5C4.5,5 6.5,3.5 10,3.5"); line("M8.4,1.9L10,3.5L8.4,5.1")
    line("M19.5,15.5C19.5,19 17.5,20.5 14,20.5"); line("M15.6,18.9L14,20.5L15.6,22.1")
    b.build()
}

private val chevronIcon: ImageVector by lazy {
    pathIcon("Chevron", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
}

private val storeIcon: ImageVector by lazy {
    // My shops: a shopping bag (owner, 7 November 2026; Google's Material icon, Apache 2.0).
    pathIcon("Store", "M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM12,4c1.1,0 2,0.9 2,2h-4c0,-1.1 0.9,-2 2,-2zM18,20H6V8h2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8h4v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8h2v12z")
}

private fun pathIcon(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.addPathNodes(d),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ).build()

private val giftIcon: ImageVector by lazy {
    pathIcon(
        "Gift",
        "M20,6h-2.18c0.11,-0.31 0.18,-0.65 0.18,-1 0,-1.66 -1.34,-3 -3,-3 -1.05,0 -1.96,0.54 -2.5,1.35l-0.5,0.67 -0.5,-0.68C10.96,2.54 10.05,2 9,2 7.34,2 6,3.34 6,5c0,0.35 0.07,0.69 0.18,1H4c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM15,4c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM9,4c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM20,19H4v-2h16v2zM20,14H4V8h5.08L7,10.83 8.62,12 11,8.76l1,-1.36 1,1.36L15.38,12 17,10.83 14.92,8H20v6z"
    )
}

private val bagIcon: ImageVector by lazy {
    pathIcon(
        "Bag",
        "M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM12,4c1.1,0 2,0.9 2,2h-4c0,-1.1 0.9,-2 2,-2zM18,20H6V8h2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8h4v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8h2v12z"
    )
}

private val swapIcon: ImageVector by lazy {
    pathIcon("Swap", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z")
}

private val groupIcon: ImageVector by lazy {
    ImageVector.Builder("Group", 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.addPathNodes(
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z"
        ),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ).build()
}

/**
 * A green tick with "Saved" next to a field, for about a second after each save ([saves] goes up by one each time).
 * The tick draws itself: from its short end down to the point, then up. It always takes its room, so the field next
 * to it keeps its width.
 */
@Composable
fun SavedFeedback(saves: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val green = if (LocalLook.current.dark) Color(0xFF6FD39A) else Color(0xFF1E7B45)
    var visible by remember { mutableStateOf(false) }
    val draw = remember { Animatable(0f) }
    // Little lines that circle around the tick once and fade (owner, 2.111: the tick alone wasn't always noticed).
    val spin = remember { Animatable(0f) }
    LaunchedEffect(saves) {
        if (saves == 0) return@LaunchedEffect
        draw.snapTo(0f)
        spin.snapTo(0f)
        visible = true
        launch { spin.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(1100)
        visible = false
    }
    // compact: only the tick (where there is little room, like next to the target price).
    Box(modifier.width(if (compact) 26.dp else 96.dp), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(400))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(20.dp)) {
                    val u = size.width / 24f
                    // Eight short lines around the tick (drawn a little outside the 20 dp, which is fine: no clipping),
                    // turning a third of a circle while they fade out.
                    val fade = (1f - spin.value).coerceIn(0f, 1f)
                    if (fade > 0f) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        for (k in 0 until 8) {
                            val a = Math.toRadians((k * 45.0) + spin.value * 120.0)
                            val r1 = (13f + 2f * spin.value) * u
                            val r2 = r1 + 4f * u
                            drawLine(
                                green.copy(alpha = 0.85f * fade),
                                c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()),
                                c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()),
                                strokeWidth = 2f * u, cap = StrokeCap.Round
                            )
                        }
                    }
                    val path = Path().apply {
                        moveTo(4.5f * u, 12.5f * u)
                        lineTo(9.5f * u, 17.5f * u)
                        lineTo(19.5f * u, 6.5f * u)
                    }
                    val pm = PathMeasure()
                    pm.setPath(path, false)
                    val part = Path()
                    pm.getSegment(0f, pm.length * draw.value, part, true)
                    drawPath(part, green, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (!compact) Text(
                    tr("Saved!"), color = green, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** A wish photo in a small spot (lists, rows, chat): drawn from memory when it was shown before (no flash). */
@Composable
fun WishImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Image(rememberWishPhoto(url).first, null, modifier, contentScale = contentScale)
}

/**
 * Blur stage (owner, 2.100): a soft blur of the photo fills the whole picture area and the photo itself ([PhotoCard])
 * sits big and uncut in the middle. The blur is a tiny copy of the photo stretched out (soft on every Android version),
 * blurred once more on Android 12 and newer; a light veil (dark in dark mode) keeps it calm. Colours come from the photo,
 * the theme stays as it is.
 */
/** The size (pixels) every wish photo is loaded at, so all screens and views share one copy in memory. */
const val PHOTO_PX = 720

/**
 * A wish photo, loaded once and then drawn straight from memory (owner, 2.102: "switch between pages where products
 * are listed without the images loading or doing something"). A photo that was shown before is in Coil's memory
 * under its own key (url + size); it is then drawn on the very first frame, without a loading step that could flash.
 * Otherwise it loads as usual (from the phone's disk cache or the shop). Returns the painter and the photo's
 * width/height (null while it loads). [exact]: exactly this small (the blur behind a photo).
 */
@Composable
fun rememberWishPhoto(url: String, px: Int = PHOTO_PX, exact: Boolean = false): Pair<androidx.compose.ui.graphics.painter.Painter, Float?> {
    val ctx = LocalContext.current
    val key = "$url#$px"
    val cached = remember(key) {
        try { ctx.imageLoader.memoryCache?.get(MemoryCache.Key(key))?.bitmap } catch (e: Exception) { null }
    }
    if (cached != null) {
        val p = remember(cached) { androidx.compose.ui.graphics.painter.BitmapPainter(cached.asImageBitmap()) }
        return p to (if (cached.height > 0) cached.width.toFloat() / cached.height else 1f)
    }
    val request = remember(key) {
        ImageRequest.Builder(ctx).data(url).size(px).memoryCacheKey(key)
            .apply { if (exact) precision(Precision.EXACT) }.build()
    }
    val painter = rememberAsyncImagePainter(request)
    val d = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val ratio = d?.let { if (it.intrinsicWidth > 0 && it.intrinsicHeight > 0) it.intrinsicWidth.toFloat() / it.intrinsicHeight else null }
    return painter to ratio
}

@Composable
fun PhotoStage(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val tiny = rememberWishPhoto(url, 24, exact = true).first
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            tiny, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(
                if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(if (small) 14.dp else 20.dp, BlurredEdgeTreatment.Rectangle)
                else Modifier
            )
        )
        Box(Modifier.fillMaxSize().background(if (look.dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.18f)))
        PhotoCard(url, description, small)
    }
}

/**
 * A shop photo shown as a small rounded card in the middle of the picture area, which has a soft colour of the theme
 * (the owner found the big white areas of white-background shop photos dull, 2.63). The card takes the photo's own
 * shape, so its corners and shadow sit on the photo itself. Neobrutalism gives it an ink line and a hard shadow.
 */
@Composable
fun PhotoCard(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    // One fixed size everywhere, drawn from memory when it was shown before (rememberWishPhoto, 2.101/2.102).
    val (painter, ratio) = rememberWishPhoto(url)
    Box(modifier.fillMaxSize().padding(if (small) 9.dp else 14.dp), contentAlignment = Alignment.Center) {
        if (ratio == null) {
            // Still loading (or it failed): nothing to frame yet. The painter needs to be drawn to load.
            Image(painter, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            // The photo on a calm frame that fits the style (owner, 13 November 2026): its corners, its line and its
            // shadow; Retro gives it the white border of an old print, Grunge a rough edge, Bauhaus square corners.
            val shape = when (look.design) {
                "chaos" -> RoundedCornerShape(8.dp)
                "y2k", "pop" -> RoundedCornerShape(if (small) 12.dp else 16.dp)
                "retro" -> RoundedCornerShape(3.dp)
                "grunge" -> RoughShape(61, 1.4.dp, 6.dp)
                "flat" -> RoundedCornerShape(8.dp)
                "bauhaus" -> androidx.compose.ui.graphics.RectangleShape
                "doodle" -> WobblyShape(63, 8.dp, 1.dp)
                "japandi" -> RoundedCornerShape(2.dp)
                "riso", "synth" -> RoundedCornerShape(6.dp)
                "cottage" -> RoundedCornerShape(6.dp)
                "anthro" -> RoundedCornerShape(if (small) 12.dp else 16.dp)
                "swiss" -> androidx.compose.ui.graphics.RectangleShape
                "punk" -> RoughShape(65, 1.6.dp, 6.dp)
                else -> RoundedCornerShape(if (small) 10.dp else 14.dp)
            }
            val frame = when (look.design) {
                "chaos" -> Modifier.hardShadow(look.line, shape, 3.dp)
                "quiet" -> Modifier.shadow(if (small) 3.dp else 5.dp, shape, clip = false)
                "flat", "japandi", "doodle", "swiss" -> Modifier
                "synth" -> Modifier.shadow(if (small) 4.dp else 8.dp, shape, clip = false, ambientColor = look.line, spotColor = look.line)
                "pop" -> Modifier.hardShadow(look.shadows[1], shape, 5.dp).hardShadow(look.shadows[0], shape, 2.5.dp)
                else -> Modifier.hardShadow(look.shadows[0], shape, 3.dp, look.shadowBrush)
            }
            Image(
                painter, description, contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .then(frame).clip(shape).background(Color.White)
                    .then(if (look.design == "retro") Modifier.border(if (small) 3.dp else 5.dp, Color(0xFFFBF6EA), shape) else Modifier)
                    .then(if (look.edge > 0.dp) Modifier.border(minOf(look.edge, 2.dp), look.line, shape) else Modifier)
            )
        }
    }
}

/**
 * Shows a message at the bottom (a "toast" with or without Undo). Every message stays 5 seconds ([AppSnackbarHost]
 * takes it away then), whatever duration is asked, and can be swiped away left or right before that (owner, 2.84).
 */
suspend fun SnackbarHostState.showToast(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    @Suppress("UNUSED_PARAMETER") duration: SnackbarDuration = SnackbarDuration.Indefinite
): SnackbarResult =
    // Right after "Please try again in a moment" (ServerNotice), a screen's own "Couldn't save. Check your connection."
    // would only cover it with something untrue; a message with a button (Undo) always shows.
    if (actionLabel == null && ServerNotice.covers(message)) SnackbarResult.Dismissed
    else showSnackbar(message, actionLabel, withDismissAction, SnackbarDuration.Indefinite)

/** The place where messages show: each one for 5 seconds, and swipe it left or right to dismiss it. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        LaunchedEffect(data) {
            delay(5000)
            data.dismiss()
        }
        SwipeAway(data, onGone = { data.dismiss() }) { ThemedSnackbar(data) }
    }
}

/**
 * Swipe it left or right (far enough) and it slides away; not far enough and it springs back. [trash]: while sliding,
 * a red block with a bin icon grows on the side that is uncovered (deleting a notification).
 */
@Composable
fun SwipeAway(key: Any, onGone: () -> Unit, trash: Boolean = false, content: @Composable () -> Unit) {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember(key) { mutableStateOf(1f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        if (trash && offset.value != 0f) {
            val shown = with(density) { kotlin.math.abs(offset.value).toDp() }
            Box(
                Modifier.matchParentSize().padding(vertical = 2.dp),
                contentAlignment = if (offset.value > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(
                    Modifier.fillMaxHeight().width(shown).clip(RoundedCornerShape(14.dp)).background(Color(0xFFD32F2F)),
                    contentAlignment = Alignment.Center
                ) {
                    if (shown > 32.dp) Icon(AppIcons.Delete, null, tint = Color.White)
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset.value
                    if (!trash) alpha = 1f - (kotlin.math.abs(offset.value) / width).coerceIn(0f, 1f) * 0.8f
                }
                .pointerInput(key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (kotlin.math.abs(offset.value) > width * 0.3f) {
                                    offset.animateTo(if (offset.value > 0) width else -width, tween(160))
                                    onGone()
                                } else offset.animateTo(0f, tween(180))
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } }
                    ) { ch, drag ->
                        ch.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    }
                }
        ) { content() }
    }
}
