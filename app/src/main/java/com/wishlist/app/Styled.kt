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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Precision
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

/** The Themer is open (App settings > Theme): the menu button is hidden, it has its own back arrow. */
object ThemerNav {
    var open by mutableStateOf(false)
}

/*
 * The app's buttons, switches, dialogs and top bar in the chosen style (Design.kt, 2.97). Material's own parts are
 * used underneath; outside Quiet they are see-through and the style's surface ([lookSurface]) is drawn behind them.
 */

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val shape = look.buttonShape
    if (look.design == "quiet") {
        // Quiet: a calm pill in the accent colour, a little taller than Material's.
        M3Button(
            onClick, modifier.heightIn(min = 46.dp), enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = look.accent, contentColor = look.onAccent),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), content = content
        )
    } else {
        M3Button(
            onClick, modifier.heightIn(min = 46.dp).then(if (enabled) Modifier.lookSurface(look, shape, look.accent, depth = 5.dp) else Modifier),
            enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = look.onAccent),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), content = content
        )
    }
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.buttonShape
    if (look.design == "quiet") {
        // Quiet: a soft tinted pill with the accent as text (the "secondary" button of iOS).
        M3Button(
            onClick, modifier.heightIn(min = 46.dp), enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = cs.primaryContainer, contentColor = if (look.dark) look.accent else cs.onPrimaryContainer),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), content = content
        )
    } else {
        M3Button(
            onClick, modifier.heightIn(min = 46.dp).then(if (enabled) Modifier.lookSurface(look, shape, cs.surfaceVariant, depth = 5.dp) else Modifier),
            enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = cs.onSurface),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), content = content
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
    // Neobrutalism and chaos: ink text (their accents are for blocks); the others: the accent.
    val col = if (look.design == "neo" || look.design == "chaos") cs.onSurface else look.accent
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
        "neo", "chaos" -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = look.onAccent, checkedTrackColor = look.accent, checkedBorderColor = look.line,
                uncheckedThumbColor = look.line, uncheckedTrackColor = cs.surfaceVariant, uncheckedBorderColor = look.line
            )
        )
        "neu" -> M3Switch(
            checked, onCheckedChange, modifier.neu(look, CircleShape, cs.background, 3.dp, pressed = true), enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = look.accent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = cs.onSurfaceVariant, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = Color.Transparent
            )
        )
        "cyber" -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = look.onAccent, checkedTrackColor = look.accent, checkedBorderColor = look.accent,
                uncheckedThumbColor = look.line, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = look.line
            )
        )
        // Quiet: the accent when on, a soft grey when off, no outline (like iOS).
        else -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = look.accent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = if (look.dark) Color(0xFF39393D) else Color(0xFFE5E5EA),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
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
    val shape = MaterialTheme.shapes.extraLarge
    if (look.design == "quiet") {
        M3AlertDialog(
            onDismissRequest, confirmButton, modifier, dismissButton, title = title, text = text,
            shape = shape, containerColor = cs.surfaceContainerHigh, tonalElevation = 0.dp, properties = properties
        )
    } else {
        M3AlertDialog(
            onDismissRequest, confirmButton, modifier.lookSurface(look, shape, cs.surfaceContainerHigh, depth = 10.dp), dismissButton,
            title = title, text = text, shape = shape, containerColor = Color.Transparent, tonalElevation = 0.dp, properties = properties
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
    // A line under the top bar: thick ink (Neobrutalism, chaos) or thin neon (Cyberpunk).
    val h = when (look.design) { "neo" -> 2.dp; "chaos" -> 3.dp; "cyber" -> 1.dp; else -> 0.dp }
    val line = if (h > 0.dp) Modifier.drawWithContent {
        drawContent()
        val px = h.toPx()
        drawRect(look.line, Offset(0f, size.height - px), Size(size.width, px))
    } else Modifier
    M3TopAppBar(title, modifier.then(line), navigationIcon, actions, colors = colors)
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
        "neo", "chaos" -> { bg = if (rise) Color(0xFFFFB3B3) else Color(0xFFB8F5C8); fg = Color(0xFF111111) }
        "cyber" -> { bg = look.scheme.surfaceVariant; fg = col }
        else -> { bg = lerp(col, look.scheme.surfaceVariant, if (look.dark) 0.78f else 0.88f); fg = col }
    }
    StyleBadge(text, bg, fg, small, if (look.design == "cyber") col else null)
}

/** A status on a wish ("Owned", "Reserved"). */
@Composable
fun StatusBadge(text: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val bg = when (look.design) {
        "quiet" -> if (look.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
        "neo", "chaos", "cyber" -> cs.surfaceVariant
        else -> cs.primaryContainer
    }
    StyleBadge(text, bg, cs.onSurface, small, null, modifier)
}

@Composable
private fun StyleBadge(text: String, bg: Color, fg: Color, small: Boolean, lineColor: Color?, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val shape = look.badgeShape(small)
    val border = when (look.design) {
        "neo" -> BorderStroke(1.5.dp, look.line)
        "chaos" -> BorderStroke(2.dp, look.line)
        "cyber" -> BorderStroke(1.dp, lineColor ?: look.line)
        else -> null
    }
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
    LaunchedEffect(saves) {
        if (saves == 0) return@LaunchedEffect
        draw.snapTo(0f)
        visible = true
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(1100)
        visible = false
    }
    // compact: only the tick (where there is little room, like next to the target price).
    Box(modifier.width(if (compact) 26.dp else 88.dp), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(400))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(20.dp)) {
                    val u = size.width / 24f
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
                    tr("Saved"), color = green, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

/**
 * Blur stage (owner, 2.100): a soft blur of the photo fills the whole picture area and the photo itself ([PhotoCard])
 * sits big and uncut in the middle. The blur is a tiny copy of the photo stretched out (soft on every Android version),
 * blurred once more on Android 12 and newer; a light veil (dark in dark mode) keeps it calm. Colours come from the photo,
 * the theme stays as it is.
 */
/** The size (pixels) every wish photo is loaded at on cards, so all views share one copy in memory. */
const val PHOTO_PX = 1000

@Composable
fun PhotoStage(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val ctx = LocalContext.current
    val tiny = remember(url) { ImageRequest.Builder(ctx).data(url).size(24).precision(Precision.EXACT).build() }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            rememberAsyncImagePainter(tiny), null, contentScale = ContentScale.Crop,
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
    val ctx = LocalContext.current
    // One fixed size for every card (2.101): switching between large cards and two per row then finds the photo in
    // memory at once. Before, each view asked for its own size, the photo was loaded again and flickered.
    val request = remember(url) { ImageRequest.Builder(ctx).data(url).size(PHOTO_PX).build() }
    val painter = rememberAsyncImagePainter(request)
    val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val ratio = drawable?.let { d ->
        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) d.intrinsicWidth.toFloat() / d.intrinsicHeight else null
    }
    Box(modifier.fillMaxSize().padding(if (small) 9.dp else 14.dp), contentAlignment = Alignment.Center) {
        if (ratio == null) {
            // Still loading (or it failed): nothing to frame yet. The painter needs to be drawn to load.
            Image(painter, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            val shape = when (look.design) {
                "neo", "chaos" -> RoundedCornerShape(8.dp)
                "cyber" -> androidx.compose.foundation.shape.CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
                else -> RoundedCornerShape(if (small) 10.dp else 14.dp)
            }
            val hard = look.design == "neo" || look.design == "chaos"
            val frame = if (hard) Modifier.hardShadow(look.line, shape, 3.dp)
            else Modifier.shadow(if (small) 3.dp else 5.dp, shape, clip = false)
            Image(
                painter, description, contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .then(frame).clip(shape).background(Color.White)
                    .then(
                        when {
                            hard -> Modifier.border(2.dp, look.line, shape)
                            look.design == "cyber" -> Modifier.border(1.dp, look.line, shape)
                            else -> Modifier
                        }
                    )
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
): SnackbarResult = showSnackbar(message, actionLabel, withDismissAction, SnackbarDuration.Indefinite)

/** The place where messages show: each one for 5 seconds, and swipe it left or right to dismiss it. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        LaunchedEffect(data) {
            delay(5000)
            data.dismiss()
        }
        SwipeAway(data, onGone = { data.dismiss() }) { Snackbar(data) }
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
