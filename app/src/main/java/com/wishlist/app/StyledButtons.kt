package com.wishlist.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch as M3Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/* The app's own buttons and switch in the chosen style (see Styled.kt). */

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
 * True for Reserve / Unreserve on a wish card (owner, 18 November 2026: the regular size "barely feels proportional"):
 * a compact pill, 36 dp to see, with room above and below so it is still 48 dp to tap.
 */
val LocalCardButton = compositionLocalOf { false }

/**
 * Quiet's own dialogs (2.210): which button slot a button is in, "confirm" or "dismiss" (null: not in a dialog's
 * buttons). There the buttons are full width and stacked; the confirm one filled (the main action), the other plain.
 */
val LocalDialogSlot = compositionLocalOf<String?> { null }

/** The two button sizes of docs/STYLE.md: regular on screens (46 dp), compact in dialogs (40 dp, a smaller shadow). */
private class ButtonSize(val height: androidx.compose.ui.unit.Dp, val pad: PaddingValues, val depth: androidx.compose.ui.unit.Dp, val room: androidx.compose.ui.unit.Dp)

@Composable
private fun buttonSize(): ButtonSize = if (LocalCardButton.current)
    ButtonSize(36.dp, PaddingValues(horizontal = 16.dp, vertical = 6.dp), 3.dp, 6.dp)
else if (LocalInDialog.current)
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
