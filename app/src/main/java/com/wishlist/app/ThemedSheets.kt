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
        dragHandle = { SheetHandle(look) },
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
        cs.primary -> cs.onPrimary
        cs.primaryContainer -> cs.onPrimaryContainer
        cs.secondaryContainer -> cs.onSecondaryContainer
        cs.surfaceVariant -> cs.onSurfaceVariant
        else -> cs.onSurface
    }
}

/** The handle at the top of a bottom panel. */
@Composable
private fun SheetHandle(look: Look) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
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

/** The sheet a form sits in: [close] slides it down, then closes it. */
class FormHost(val close: () -> Unit)

/** Set inside a [FormSheetHost]: a [FormSheet] then draws itself in that sheet instead of opening its own. */
val LocalFormHost = androidx.compose.runtime.compositionLocalOf<FormHost?> { null }

/**
 * A full-width sheet that slides up from the bottom, for forms (New wishlist, New group, New gift exchange …). Several
 * forms can take turns inside one host (My own / With others) without the sheet moving. Swipe down, tap outside or
 * the X: it slides down and closes at once, without asking (owner).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FormSheetHost(onClose: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val latest = androidx.compose.runtime.rememberUpdatedState(onClose)
    val host = androidx.compose.runtime.remember {
        FormHost { scope.launch { state.hide() }.invokeOnCompletion { latest.value() } }
    }
    ModalBottomSheet(onDismissRequest = { latest.value() }, sheetState = state) {
        androidx.compose.runtime.CompositionLocalProvider(LocalFormHost provides host) { content() }
    }
}

/**
 * A form in the full-width sheet, with the same parts as [AlertDialog] so a dialog becomes a sheet by its name alone:
 * the [title] centred with a round X top right, the [text] (the fields, scrolling inside when long) and the
 * [confirmButton] full width at the bottom. There is no Cancel ([dismissButton] is not shown: the X, a swipe down or a
 * tap outside close it). The sheet has one fixed height, so changing what's in it never makes it jump.
 */
@Composable
fun FormSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    @Suppress("UNUSED_PARAMETER") dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") properties: androidx.compose.ui.window.DialogProperties? = null
) {
    val host = LocalFormHost.current
    if (host == null) {
        FormSheetHost(onDismissRequest) { FormSheet(onDismissRequest, confirmButton, null, title, text) }
        return
    }
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
