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

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.buttonShape
    when (look.design) {
        "neo" -> M3Button(
            onClick, modifier.then(if (enabled) Modifier.hardShadow(look.line, shape, 3.dp) else Modifier), enabled,
            shape = shape, colors = ButtonDefaults.buttonColors(containerColor = look.accent, contentColor = look.onAccent),
            elevation = null, border = BorderStroke(2.dp, look.line), content = content
        )
        // Clay: a puffy pillow in the main colour (the button itself is see-through on top of it).
        "clay" -> M3Button(
            onClick, modifier.then(if (enabled) Modifier.clay(look, shape, look.accent, 6.dp) else Modifier), enabled,
            shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = look.onAccent),
            content = content
        )
        "organic" -> M3Button(onClick, modifier, enabled, shape = shape, content = content)
        "material" -> M3Button(
            onClick, modifier.heightIn(min = 48.dp), enabled, shape = shape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), content = content
        )
        else -> M3Button(onClick, modifier, enabled, content = content)
    }
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.buttonShape
    when (look.design) {
        "neo" -> M3OutlinedButton(
            onClick, modifier.then(if (enabled) Modifier.hardShadow(look.line, shape, 3.dp) else Modifier), enabled,
            shape = shape, colors = ButtonDefaults.outlinedButtonColors(containerColor = cs.surface, contentColor = cs.onSurface),
            border = BorderStroke(2.dp, look.line), content = content
        )
        // Clay and Material 3 have soft "secondary" buttons filled with the light container colour.
        "clay" -> M3Button(
            onClick, modifier.then(if (enabled) Modifier.clay(look, shape, cs.primaryContainer, 5.dp) else Modifier), enabled,
            shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = cs.onPrimaryContainer),
            content = content
        )
        "material" -> M3FilledTonalButton(
            onClick, modifier.heightIn(min = 48.dp), enabled, shape = shape,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), content = content
        )
        "organic" -> M3OutlinedButton(onClick, modifier, enabled, shape = shape, border = BorderStroke(1.dp, look.line), content = content)
        else -> M3OutlinedButton(onClick, modifier, enabled, content = content)
    }
}

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    if (look.classic) M3FilledTonalButton(onClick, modifier, enabled, content = content)
    else OutlinedButton(onClick, modifier, enabled, content)
}

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    when (look.design) {
        "classic" -> M3TextButton(onClick, modifier, enabled, content = content)
        // Neobrutalism: ink text (the bright main colour is unreadable as text on white).
        "neo" -> M3TextButton(
            onClick, modifier, enabled, shape = look.buttonShape,
            colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface), content = content
        )
        else -> M3TextButton(onClick, modifier, enabled, shape = look.buttonShape, content = content)
    }
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    when (look.design) {
        "neo" -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = look.onAccent, checkedTrackColor = look.accent, checkedBorderColor = look.line,
                uncheckedThumbColor = look.line, uncheckedTrackColor = cs.surface, uncheckedBorderColor = look.line
            )
        )
        "clay" -> M3Switch(
            checked, onCheckedChange,
            modifier.shadow(4.dp, CircleShape, clip = false, ambientColor = look.shadows[0], spotColor = look.shadows[0]),
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = cs.primary, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = cs.primaryContainer, uncheckedBorderColor = Color.Transparent
            )
        )
        "organic" -> M3Switch(
            checked, onCheckedChange, modifier, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = cs.onPrimary, checkedTrackColor = cs.primary,
                uncheckedThumbColor = cs.outline, uncheckedTrackColor = cs.surfaceVariant, uncheckedBorderColor = look.line
            )
        )
        // Material 3 Expressive: a tick in the thumb when on.
        "material" -> M3Switch(
            checked, onCheckedChange, modifier,
            thumbContent = if (checked) ({ Icon(AppIcons.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null,
            enabled = enabled
        )
        else -> M3Switch(checked, onCheckedChange, modifier, enabled = enabled)
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
    when (look.design) {
        "classic" -> M3AlertDialog(
            onDismissRequest, confirmButton, modifier, dismissButton, title = title, text = text, properties = properties
        )
        else -> {
            val frame = when (look.design) {
                "neo" -> Modifier.hardShadow(look.line, shape, 6.dp).border2(look, shape)
                "organic" -> Modifier.border1(look, shape)
                "clay" -> Modifier.clay(look, shape, cs.surfaceContainerHigh, 16.dp)
                else -> Modifier
            }
            M3AlertDialog(
                onDismissRequest, confirmButton, modifier.then(frame), dismissButton, title = title, text = text,
                shape = shape, containerColor = if (look.design == "clay") Color.Transparent else cs.surfaceContainerHigh,
                tonalElevation = 0.dp, properties = properties
            )
        }
    }
}

private fun Modifier.border2(look: Look, shape: Shape) = border(2.dp, look.line, shape)
private fun Modifier.border1(look: Look, shape: Shape) = border(1.dp, look.line, shape)

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
    // Neobrutalism: a thick ink line under the top bar.
    val line = if (look.design == "neo") Modifier.drawWithContent {
        drawContent()
        val h = 2.dp.toPx()
        drawRect(look.line, Offset(0f, size.height - h), Size(size.width, h))
    } else Modifier
    M3TopAppBar(title, modifier.then(line), navigationIcon, actions, colors = colors)
}

/** The name of a list in its top bar, the same in every theme. ([count] is no longer shown: the owner removed
 * Organic's "7 WISHES" line in 2.60.) */
@Composable
fun ListTitle(name: String, @Suppress("UNUSED_PARAMETER") count: Int) {
    Text(name, fontWeight = LocalLook.current.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The price change on a wish ("−20%", "+5%"; plus and minus, not arrows: it isn't a stock trade). Classic: white on green or red, as before. */
@Composable
fun PriceBadge(text: String, rise: Boolean, small: Boolean) {
    val look = LocalLook.current
    val col = if (rise) look.rise else look.drop
    val bg: Color
    val fg: Color
    when (look.design) {
        "classic" -> { bg = col; fg = Color.White }
        "neo" -> { bg = if (rise) Color(0xFFFFB3B3) else Color(0xFFB8F5C8); fg = Color(0xFF111111) }
        else -> { bg = lerp(col, look.scheme.surface, if (look.dark) 0.72f else 0.86f); fg = col }
    }
    val clay = look.design == "clay"
    Surface(
        color = if (clay) Color.Transparent else bg, shape = look.badgeShape(small),
        modifier = if (clay) Modifier.clay(look, look.badgeShape(small), bg, 3.dp) else Modifier,
        border = if (look.design == "neo") BorderStroke(1.5.dp, Color(0xFF111111)) else null
    ) {
        Text(
            text, color = fg,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (look.classic) null else FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 4.dp)
        )
    }
}

/** A status on a wish ("Got it", "Reserved"). Classic: on the background colour, as before. */
@Composable
fun StatusBadge(text: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val bg = when (look.design) {
        "classic" -> cs.background
        "neo" -> cs.surface
        else -> cs.primaryContainer
    }
    val fg = when (look.design) {
        "classic" -> cs.onBackground
        "neo" -> cs.onSurface
        else -> cs.onPrimaryContainer
    }
    val clay = look.design == "clay"
    Surface(
        color = if (clay) Color.Transparent else bg, shape = look.badgeShape(small),
        modifier = if (clay) modifier.clay(look, look.badgeShape(small), bg, 3.dp) else modifier,
        border = if (look.design == "neo") BorderStroke(1.5.dp, look.line) else null
    ) {
        Text(
            text, color = fg,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (look.classic) null else FontWeight.Bold,
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
 * A shop photo shown as a small rounded card in the middle of the picture area, which has a soft colour of the theme
 * (the owner found the big white areas of white-background shop photos dull, 2.63). The card takes the photo's own
 * shape, so its corners and shadow sit on the photo itself. Neobrutalism gives it an ink line and a hard shadow.
 */
@Composable
fun PhotoCard(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val painter = rememberAsyncImagePainter(url)
    val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val ratio = drawable?.let { d ->
        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) d.intrinsicWidth.toFloat() / d.intrinsicHeight else null
    }
    Box(modifier.fillMaxSize().padding(if (small) 12.dp else 16.dp), contentAlignment = Alignment.Center) {
        if (ratio == null) {
            // Still loading (or it failed): nothing to frame yet. The painter needs to be drawn to load.
            Image(painter, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            val shape = when (look.design) {
                "organic" -> RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
                "neo" -> RoundedCornerShape(8.dp)
                else -> RoundedCornerShape(if (small) 10.dp else 14.dp)
            }
            val frame = if (look.design == "neo") Modifier.hardShadow(look.line, shape, 3.dp)
            else Modifier.shadow(if (small) 4.dp else 6.dp, shape, clip = false)
            Image(
                painter, description, contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .then(frame).clip(shape).background(Color.White)
                    .then(if (look.design == "neo") Modifier.border(2.dp, look.line, shape) else Modifier)
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
