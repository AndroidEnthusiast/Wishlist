package com.wishlist.app

import androidx.compose.material3.LocalTextStyle

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
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

/**
 * Quiet's own dialogs (2.210): which button slot a button is in, "confirm" or "dismiss" (null: not in a dialog's
 * buttons). There the buttons are full width and stacked; the confirm one filled (the main action), the other plain.
 */
val LocalDialogSlot = compositionLocalOf<String?> { null }

/** The two button sizes of docs/STYLE.md: regular on screens (46 dp), compact in dialogs (40 dp, a smaller shadow). */
private class ButtonSize(val height: androidx.compose.ui.unit.Dp, val pad: PaddingValues, val depth: androidx.compose.ui.unit.Dp, val room: androidx.compose.ui.unit.Dp)

@Composable
private fun buttonSize(): ButtonSize = if (LocalInDialog.current)
    ButtonSize(40.dp, PaddingValues(horizontal = 18.dp, vertical = 8.dp), 3.dp, 6.dp)
// Quiet (2.207): 48 dp, the height of everything you tap.
else ButtonSize(if (LocalLook.current.design == "quiet") 48.dp else 46.dp, PaddingValues(horizontal = 22.dp, vertical = 10.dp), 5.dp, 0.dp)

/**
 * A button's label always in the button's own text colour: a colour set in the text style around it (a dialog's grey
 * body text) beat the button's colour, so labels went grey on blue (owner's screenshot, 17 November 2026).
 */
private fun ownInk(content: @Composable RowScope.() -> Unit): @Composable RowScope.() -> Unit = {
    val row = this
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = Color.Unspecified)) { row.content() }
}

/**
 * The fill of a dangerous main action (Delete, Leave, Block) and its text: a deep red with white text, readable in
 * light and dark (owner's screenshot, 17 November 2026: "Delete" was pink on blue).
 */
val DangerFill = Color(0xFFD03A2F)

/**
 * [danger]: Delete, Leave, Block and the like. Never colour a button's Text yourself (DialogButtonInkTest checks it):
 * the button picks readable colours for its slot (a filled red main action in a dialog, red text elsewhere).
 */
@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val shape = look.buttonShape
    val z = buttonSize()
    val wide = if (LocalDialogSlot.current != null) Modifier.fillMaxWidth() else Modifier
    if (look.design == "quiet") {
        // Quiet: a calm pill in the accent colour, a little taller than Material's.
        M3Button(
            onClick, modifier.pressScale().then(wide).padding(vertical = z.room).heightIn(min = z.height), enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) DangerFill else look.accent, contentColor = if (danger) Color.White else look.onAccent
            ),
            contentPadding = z.pad, content = ownInk(content)
        )
    } else {
        M3Button(
            onClick, modifier.pressScale().then(wide).padding(vertical = z.room).heightIn(min = z.height)
                .then(if (enabled) Modifier.lookSurface(look, shape, if (danger) DangerFill else look.accent, depth = z.depth) else Modifier),
            enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = if (danger) Color.White else look.onAccent),
            contentPadding = z.pad, content = ownInk(content)
        )
    }
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.buttonShape
    val z = buttonSize()
    val wide = if (LocalDialogSlot.current != null) Modifier.fillMaxWidth() else Modifier
    if (look.design == "quiet") {
        // Quiet: a soft tinted pill with the accent as text (the "secondary" button of iOS).
        M3Button(
            onClick, modifier.pressScale().then(wide).padding(vertical = z.room).heightIn(min = z.height), enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.primaryContainer,
                contentColor = if (danger) cs.error else if (look.dark) look.accent else cs.onPrimaryContainer
            ),
            contentPadding = z.pad, content = ownInk(content)
        )
    } else {
        M3Button(
            onClick, modifier.pressScale().then(wide).padding(vertical = z.room).heightIn(min = z.height)
                .then(if (enabled) Modifier.lookSurface(look, shape, cs.surfaceVariant, depth = z.depth) else Modifier),
            enabled, shape = shape, elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = if (danger) cs.error else cs.onSurface),
            contentPadding = z.pad, content = ownInk(content)
        )
    }
}

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, content: @Composable RowScope.() -> Unit) =
    OutlinedButton(onClick, modifier, enabled, danger, content)

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    // Chaos: ink text (its accent is for blocks); the others: the style's readable text colour (its primary; in Quiet
    // that is the accent).
    val col = if (danger) cs.error else if (look.design == "chaos") cs.onSurface else cs.primary
    // In Quiet's own dialogs (2.210): the confirm button is the filled main action, the other a full-width plain one.
    // A form sheet's main button is the filled full-width one in every style (owner, 17 November 2026).
    when (LocalDialogSlot.current.takeIf { look.design == "quiet" || LocalInFormSheet.current }) {
        "confirm" -> { Button(onClick, modifier, enabled, danger, content); return }
        "only" -> { OutlinedButton(onClick, modifier, enabled, danger, content); return }
        "dismiss" -> {
            M3TextButton(
                onClick, modifier.pressScale().fillMaxWidth().heightIn(min = 48.dp), enabled, shape = look.buttonShape,
                colors = ButtonDefaults.textButtonColors(contentColor = if (danger) cs.error else cs.onSurface), content = ownInk(content)
            )
            return
        }
    }
    M3TextButton(
        onClick, modifier.pressScale(), enabled, shape = look.buttonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = col), content = ownInk(content)
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
        "quiet" -> QuietSwitch(checked, onCheckedChange, modifier, enabled)
        // Gen Z chaos: on in the style's block colour, off with its black line.
        else -> {
            val edge = look.line
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
        QuietDialog(onDismissRequest, confirmButton, modifier, dismissButton, title, body, properties)
    } else {
        M3AlertDialog(
            onDismissRequest, confirmButton, modifier.lookSurface(look, shape, cs.surfaceContainerHigh, depth = 10.dp), dismissButton,
            title = title, text = body, shape = shape, containerColor = Color.Transparent, tonalElevation = 0.dp, properties = properties
        )
    }
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
    if (look.design == "quiet" && large && scroll != null) {
        androidx.compose.runtime.DisposableEffect(scroll) { scroll.active = true; onDispose { scroll.active = false } }
        androidx.compose.material3.LargeTopAppBar(
            { CompositionLocalProvider(LocalInLargeBar provides true) { title() } }, modifier, nav, acts,
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
            Modifier.size(40.dp).clip(CircleShape).background(if (look.dark) QuietColours.raisedDark else Color.White),
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
        "quiet" -> if (look.dark) QuietColours.raisedDark else QuietColours.pageLight
        else -> cs.surfaceVariant
    }
    StyleBadge(text, bg, cs.onSurface, small, null, modifier)
}

@Composable
private fun StyleBadge(text: String, bg: Color, fg: Color, small: Boolean, lineColor: Color?, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val shape = look.badgeShape(small)
    // The line drawn with edgeLine (2.202: Modifier.border was slow on irregular shapes in lists).
    Surface(color = bg, shape = shape, modifier = modifier.edgeLine(if (look.edge > 0.dp) minOf(look.edge, 2.dp) else 0.dp, lineColor ?: look.line, shape)) {
        Text(
            text, color = fg,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 4.dp)
        )
    }
}

/**
 * The icons of the app (2.210, owner 15 November 2026, docs/styles/identity.md): our own rounded-line family
 * ([OwnIcons]) in every style, bolder in Gen Z chaos. They replaced Google's filled Material icons, which made the app
 * look like Android's own settings. The chosen tab uses the filled versions (HomeFilled …).
 */
object AppIcons {
    private val set: OwnIcons @Composable get() = if (LocalLook.current.design == "chaos") ChaosIcons else QuietIcons

    val Add: ImageVector @Composable get() = set.add
    val ArrowBack: ImageVector @Composable get() = set.back
    val ArrowDropDown: ImageVector @Composable get() = set.dropDown
    val Check: ImageVector @Composable get() = set.check
    val Close: ImageVector @Composable get() = set.close
    val Delete: ImageVector @Composable get() = set.bin
    val Edit: ImageVector @Composable get() = set.edit
    val Favorite: ImageVector @Composable get() = set.heartFilled
    val FavoriteLine: ImageVector @Composable get() = set.heart
    val Home: ImageVector @Composable get() = set.home
    val HomeFilled: ImageVector @Composable get() = set.homeFilled
    val Lock: ImageVector @Composable get() = set.lock
    val ListIcon: ImageVector @Composable get() = set.list
    val Notifications: ImageVector @Composable get() = set.bell
    val MoreVert: ImageVector @Composable get() = set.more
    val Person: ImageVector @Composable get() = set.person
    val PersonFilled: ImageVector @Composable get() = set.personFilled
    val Friends: ImageVector @Composable get() = set.friends
    val FriendsFilled: ImageVector @Composable get() = set.friendsFilled
    val Refresh: ImageVector @Composable get() = set.refresh
    val Search: ImageVector @Composable get() = set.search
    val Settings: ImageVector @Composable get() = set.gear
    val Share: ImageVector @Composable get() = set.share
    /** The buttons on a wish card (⋮, share, bin): the same icons, drawn small and grey by the card (2.207). */
    val CardShare: ImageVector @Composable get() = set.share
    val CardDelete: ImageVector @Composable get() = set.bin
    val Group: ImageVector @Composable get() = set.group
    val Gift: ImageVector @Composable get() = set.gift
    val Bag: ImageVector @Composable get() = set.bag
    val Swap: ImageVector @Composable get() = set.swap
    /** Gift exchange in the side menu (owner, 7 November 2026): two gifts with an arrow from each to the other, our own drawing. */
    val Exchange: ImageVector get() = exchangeIcon
    val Store: ImageVector @Composable get() = set.store
    val StoreFilled: ImageVector @Composable get() = set.storeFilled
    val Chevron: ImageVector @Composable get() = set.chevron
    val Chat: ImageVector @Composable get() = set.chat
    val ChatFilled: ImageVector @Composable get() = set.chatFilled
    val Gallery: ImageVector @Composable get() = set.gallery
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
        // An http:// picture saved before 2.215 loads over https (Android refuses plain http).
        ImageRequest.Builder(ctx).data(secureImage(url)).size(px).memoryCacheKey(key)
            .apply { if (exact) precision(Precision.EXACT) }.build()
    }
    val painter = rememberAsyncImagePainter(request)
    // A picture that can't be loaded (a shop that refuses, a broken address, offline) is remembered, so the cards show
    // the gift instead of a white box (owner, 16 November 2026, after a Barnes & Noble review and Baracuta).
    if (painter.state is AsyncImagePainter.State.Error) LaunchedEffect(url) { BrokenPhotos.mark(url) }
    else if (painter.state is AsyncImagePainter.State.Success) LaunchedEffect(url) { BrokenPhotos.clear(url) }
    val d = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val ratio = d?.let { if (it.intrinsicWidth > 0 && it.intrinsicHeight > 0) it.intrinsicWidth.toFloat() / it.intrinsicHeight else null }
    return painter to ratio
}

/**
 * Wish pictures that couldn't be loaded since the app started (in memory only: online again, a new start tries again).
 * Cards then show the gift as for a wish without a picture; the wish form says so and offers your own photo.
 */
object BrokenPhotos {
    private val broken = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    fun mark(url: String) { if (url.isNotBlank()) broken[url] = true }
    fun clear(url: String) { broken.remove(url) }
    fun isBroken(url: String): Boolean = broken[url] == true
}

@Composable
fun PhotoStage(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (BrokenPhotos.isBroken(url)) {
        // Still asked for in the background (so it comes back once it loads), the gift on top.
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(rememberWishPhoto(url).first, null, Modifier.size(1.dp))
            PlaceholderIcon("gift", if (small) 32.dp else 48.dp, look.photoInk)
        }
        return
    }
    if (look.design == "quiet") { QuietPhoto(url, description, small, modifier); return }
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
 * Quiet's photo (2.207, docs/styles/quiet.md): the shop photo whole, on the card's one light photo surface, with small
 * rounded corners and no frame or shadow of its own. It is laid over the surface with "multiply" (a colour filter), so the white
 * background most shop photos have takes the surface's colour and the product seems to stand on the card itself
 * (owner: one photo surface, no frame in a frame).
 */
@Composable
private fun QuietPhoto(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val (painter, ratio) = rememberWishPhoto(url)
    Box(modifier.fillMaxSize().padding(if (small) 10.dp else 16.dp), contentAlignment = Alignment.Center) {
        Image(
            painter, description, contentScale = ContentScale.Fit,
            // Multiplying by the surface's colour is the same as laying the photo over it with "multiply".
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalLook.current.photoSurface(0), androidx.compose.ui.graphics.BlendMode.Multiply),
            modifier = (if (ratio == null) Modifier.fillMaxSize() else Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true))
                .clip(Squircle(if (small) 8.dp else 12.dp))
        )
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
                else -> RoundedCornerShape(if (small) 10.dp else 14.dp)
            }
            val frame = when (look.design) {
                "chaos" -> Modifier.hardShadow(look.line, shape, 3.dp)
                else -> Modifier.shadow(if (small) 3.dp else 5.dp, shape, clip = false)
            }
            Image(
                painter, description, contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .then(frame).clip(shape).background(Color.White)
                    .then(if (look.edge > 0.dp) Modifier.edgeLine(minOf(look.edge, 2.dp), look.line, shape) else Modifier)
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

/**
 * One line of text that is never cut off (2.209, owner 15 November 2026: "€1,434.56" was cut off on Home in Gen Z
 * chaos, "unacceptable"): when it doesn't fit, it gets smaller, down to 60 % of [style]'s size, before anything is
 * cut. Used for numbers and amounts.
 */
@Composable
fun FitText(
    text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier,
    align: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center
) {
    // Measured as it is laid out: too wide, it is laid out again a step smaller (5 %), and only drawn once it fits, so
    // nobody sees the steps. (Not with BoxWithConstraints: it crashes inside rows that ask for their height, 2.209.)
    var scale by remember(text, style) { mutableStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text, style = style.copy(fontSize = style.fontSize * scale), color = color, maxLines = 1, softWrap = false,
        textAlign = align,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && scale > 0.6f) scale = (scale - 0.05f).coerceAtLeast(0.6f) else ready = true
        }
    )
}
