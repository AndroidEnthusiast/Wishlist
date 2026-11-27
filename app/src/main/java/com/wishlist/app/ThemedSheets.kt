package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet as M3ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar as M3Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* The style's bottom panels, message bar and form sheets (see Themed.kt). */

/**
 * A panel that slides up from the bottom (a wish's ⋮, your profile) in the style: its top corners, its card colour
 * and a handle in the style's colours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    // A form sheet's own handle and Back (FormSheetHost, owner 21 November 2026); other panels keep the defaults.
    dragHandle: (@Composable () -> Unit)? = null,
    properties: androidx.compose.material3.ModalBottomSheetProperties = androidx.compose.material3.ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit
) {
    val look = LocalLook.current
    val corner = MaterialTheme.shapes.extraLarge
    val shape = when (look.design) {
        "quiet" -> SquircleTop(28.dp)
        else -> (corner as? RoundedCornerShape)?.let { RoundedCornerShape(topStart = it.topStart, topEnd = it.topEnd, bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp), bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)) }
            ?: RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }
    val container = sheetColor(look, containerColor)
    M3ModalBottomSheet(
        onDismissRequest, modifier, sheetState, shape = shape, containerColor = container,
        // The text colour too (2.208): Quiet's lifted grey isn't a colour of the scheme, so without it the text in the
        // sheet fell back to black on dark grey (owner's screenshot of the profile sheet, 15 November 2026).
        contentColor = sheetContent(look, container),
        tonalElevation = 0.dp,
        dragHandle = dragHandle ?: { SheetHandle(look) },
        properties = properties,
        content = content
    )
}

/**
 * Quiet (2.207): what floats (panels, dialogs, menus) is white in light and a lifted grey in dark, where the page's
 * own colour would be black. A colour a screen asked for on purpose stays.
 */
internal fun quietRaised(look: Look, asked: Color): Color = when {
    asked != look.scheme.surface && asked != look.scheme.background -> asked
    look.dark -> look.raised
    else -> Color.White
}

/** The colour of a bottom panel: Quiet's raised white or lifted grey, the style's card colour elsewhere. */
internal fun sheetColor(look: Look, asked: Color): Color =
    if (look.design == "quiet") quietRaised(look, asked) else look.scheme.surfaceVariant

/**
 * The text and icon colour in a bottom panel of colour [bg]: the scheme's own pair when [bg] is a scheme colour, and the
 * page's text colour on Quiet's raised panel (white in light, lifted grey in dark), never Android's default black.
 */
internal fun sheetContent(look: Look, bg: Color): Color {
    val cs = look.scheme
    return when (bg) {
        // First (22 November 2026): Quiet light uses one colour for surface and surfaceVariant, so a plain sheet got
        // the grey of surfaceVariant's text: titles like "My wishlist" or "Add friends" came out grey.
        cs.surface -> cs.onSurface
        cs.primary -> cs.onPrimary
        cs.primaryContainer -> cs.onPrimaryContainer
        cs.secondaryContainer -> cs.onSecondaryContainer
        cs.surfaceVariant -> cs.onSurfaceVariant
        else -> cs.onSurface
    }
}

/** The handle at the top of a bottom panel. [modifier]: a form sheet's own pull (FormSheetHost). */
@Composable
internal fun SheetHandle(look: Look, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (look.design) {
            "chaos" -> Box(Modifier.size(width = 44.dp, height = 6.dp).clip(CircleShape).background(look.line))
            else -> Box(Modifier.size(width = 32.dp, height = 4.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.4f)))
        }
    }
}

/** A message at the bottom of the screen in the style: its surface, line, shadow and shape. */
@Composable
fun ThemedSnackbar(data: SnackbarData) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    if (look.design == "quiet") { M3Snackbar(data); return }
    // Gen Z chaos (2.209): one box in the style, as tall as Android's (48 dp for one line). Android's own message box
    // inside it kept its blurry shadow, so a big white box held a grey one (owner's screenshot, 15 November 2026).
    val shape = MaterialTheme.shapes.small
    val ink = cs.inverseOnSurface
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
            .lookSurface(look, shape, cs.inverseSurface, depth = 4.dp)
            .heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = ink,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        )
        data.visuals.actionLabel?.let { label ->
            androidx.compose.material3.TextButton(onClick = { data.performAction() }) {
                Text(label, color = ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------- the form sheet (owner, 17 November 2026; docs/designs/plan-2026-11-17.md, step 3) ----------

/**
 * The sheet a form sits in: [close] slides it down, then closes it. [unsaved]: the form has something typed, so the
 * phone's Back asks first (set by [FormSheet]).
 */
class FormHost(val close: () -> Unit) {
    var unsaved by mutableStateOf(false)
}

/** Set inside a [FormSheetHost]: a [FormSheet] then draws itself in that sheet instead of opening its own. */
val LocalFormHost = androidx.compose.runtime.compositionLocalOf<FormHost?> { null }

/** How far the pull tab has to be pulled down to close a form sheet. */
internal val PULL_TO_CLOSE = 24.dp

/**
 * Whether the form sheet may go: only when one of its own ways out asked for it ([allowed]: the X, the pull tab,
 * Back, Discard). A swipe anywhere else or a tap outside never closes it (owner, 21 November 2026: "all my details
 * are gone").
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun formSheetMayHide(target: androidx.compose.material3.SheetValue, allowed: Boolean): Boolean =
    target != androidx.compose.material3.SheetValue.Hidden || allowed

/** Back in a form sheet: with something typed it asks first (owner, 21 November 2026); otherwise it closes. */
internal fun backAsks(unsaved: Boolean): Boolean = unsaved

/**
 * A full-width sheet that slides up from the bottom, for forms (New wishlist, New group, New gift exchange, Import,
 * Screenshot to wish, Wishlist settings, your profile). Several forms can take turns inside one host (Personal /
 * Shared) without the sheet moving. It closes only with the X or by pulling its tab down; swiping elsewhere and taps
 * outside do nothing; Back closes, or with something typed asks "Discard what you typed?" first (owner, 21 November
 * 2026; before, a swipe anywhere closed it and lost what was typed).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FormSheetHost(
    onClose: () -> Unit,
    // A tall menu, not a form (You and your settings): it closes like every menu, by a swipe down or a tap outside,
    // and has no ✕ (owner, 21 November 2026).
    menu: Boolean = false,
    content: @Composable () -> Unit
) {
    var allowed by androidx.compose.runtime.remember { mutableStateOf(menu) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { formSheetMayHide(it, allowed) })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val latest = androidx.compose.runtime.rememberUpdatedState(onClose)
    val host = androidx.compose.runtime.remember {
        FormHost { allowed = true; scope.launch { state.hide() }.invokeOnCompletion { latest.value() } }
    }
    var asking by androidx.compose.runtime.remember { mutableStateOf(false) }
    val look = LocalLook.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = { if (allowed) latest.value() },
        sheetState = state,
        // The pull tab closes it: pulled down past PULL_TO_CLOSE (it takes the drag, so the sheet itself never moves).
        dragHandle = {
            var pulled by androidx.compose.runtime.remember { mutableStateOf(0f) }
            SheetHandle(look, Modifier.fillMaxWidth()
                .semantics { onClick(label = tr("Close")) { host.close(); true } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { pulled = 0f },
                        onDragEnd = { if (pulled > with(density) { PULL_TO_CLOSE.toPx() }) host.close() },
                        onDragCancel = { pulled = 0f }
                    ) { change, dy -> change.consume(); pulled += dy }
                })
        },
        properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        androidx.activity.compose.BackHandler { if (backAsks(host.unsaved)) asking = true else host.close() }
        // Swipes on the form never reach the sheet (only the pull tab moves it); a menu moves as any sheet.
        Box(if (menu) Modifier else Modifier.nestedScroll(KeepFormOpen).pointerInput(Unit) { detectVerticalDragGestures { change, _ -> change.consume() } }) {
            androidx.compose.runtime.CompositionLocalProvider(LocalFormHost provides host) { content() }
        }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(tr("Discard what you typed?")) },
        confirmButton = { TextButton(danger = true, onClick = { asking = false; host.close() }) { Text(tr("Discard")) } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("Keep editing")) } }
    )
}

/** Takes what a scrolling form leaves over, so pulling past its top never drags the sheet down. */
private object KeepFormOpen : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
        androidx.compose.ui.geometry.Offset(0f, available.y)
    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
        androidx.compose.ui.unit.Velocity(0f, available.y)
}

/**
 * A form in the full-width sheet, with the same parts as [AlertDialog] so a dialog becomes a sheet by its name alone:
 * the [title] centred with a round X top right, the [text] (the fields, scrolling inside when long) and the
 * [confirmButton] full width at the bottom. There is no Cancel ([dismissButton] is not shown: the X or the pull tab
 * close it, see [FormSheetHost]). The sheet has one fixed height, so changing what's in it never makes it jump.
 */
@Composable
fun FormSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    @Suppress("UNUSED_PARAMETER") dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") properties: androidx.compose.ui.window.DialogProperties? = null,
    // Something is typed: Back asks before closing (owner, 21 November 2026).
    unsaved: Boolean = false
) {
    val host = LocalFormHost.current
    if (host == null) {
        FormSheetHost(onDismissRequest) { FormSheet(onDismissRequest, confirmButton, null, title, text, unsaved = unsaved) }
        return
    }
    androidx.compose.runtime.SideEffect { host.unsaved = unsaved }
    val tall = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.88f).dp
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().height(tall).imePadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            if (title != null) androidx.compose.material3.ProvideTextStyle(
                MaterialTheme.typography.titleLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            ) {
                Box(Modifier.align(Alignment.Center).padding(horizontal = 56.dp)) { title() }
            }
            // The X in the same soft round button as the top bars' (LocalInTopBar).
            androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) {
                IconButton(onClick = host.close, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(AppIcons.Close, tr("Close"))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) { text?.invoke() }
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp).navigationBarsPadding()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalInFormSheet provides true, LocalDialogSlot provides "confirm") {
                confirmButton()
            }
        }
    }
}

/** True for the main button of a [FormSheet]: it is the filled full-width button in every style. */
val LocalInFormSheet = androidx.compose.runtime.compositionLocalOf { false }
