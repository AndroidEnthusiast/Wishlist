package com.wishlist.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* The style's text fields (see Themed.kt). */

/** How thick a field's line is when it is not and when it is being typed in. */
private fun Look.fieldLines(): Pair<Dp, Dp> = when (design) {
    "quiet" -> 1.dp to 2.dp
    "chaos" -> 3.dp to 3.5.dp
    else -> maxOf(edge, 1.5.dp) to maxOf(edge, 1.5.dp) + 1.dp
}

/** The colours of a text field in the style: lines in the style's ink, the style's text colour when typing. */
@Composable
private fun fieldColors(look: Look): TextFieldColors {
    val cs = look.scheme
    val edge = when (look.design) {
        "quiet" -> cs.outline
        else -> look.line
    }
    val focus = if (look.design == "chaos") cs.onSurface else cs.primary
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = cs.onSurface, unfocusedTextColor = cs.onSurface,
        focusedContainerColor = if (look.design == "quiet") Color.Transparent else cs.surfaceVariant,
        unfocusedContainerColor = if (look.design == "quiet") Color.Transparent else cs.surfaceVariant,
        disabledContainerColor = if (look.design == "quiet") Color.Transparent else cs.surfaceVariant.copy(alpha = 0.6f),
        focusedBorderColor = focus, unfocusedBorderColor = edge,
        focusedLabelColor = focus, unfocusedLabelColor = cs.onSurfaceVariant,
        cursorColor = focus, errorBorderColor = cs.error
    )
}

/**
 * Material's outlined text field in the style: its corners ([Shapes.small][androidx.compose.material3.Shapes]),
 * the thickness and colour of its line, a filled inside on the style's card colour (so it sits on a calm surface over
 * the page's drawing) and, for styles with hard shadows, a small shadow while you type in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape? = null
) {
    val look = LocalLook.current
    val sh = shape ?: fieldShape(look)
    if (look.design == "quiet") {
        // Typed text starts at the left, also inside Quiet's centred dialogs (2.210), unless the caller asked otherwise.
        // The text's own colour too (owner, 16 November 2026: values looked greyed out): inside a dialog the dialog's
        // grey explanation colour came along with the text style; what you type or chose is full ink.
        val typed = if (textStyle == LocalTextStyle.current) textStyle.copy(
            textAlign = androidx.compose.ui.text.style.TextAlign.Start, color = androidx.compose.ui.graphics.Color.Unspecified
        ) else textStyle
        QuietLabelled(modifier, label) {
            M3OutlinedTextField(
                value, onValueChange, Modifier.fillMaxWidth(), enabled, readOnly, typed, null, placeholder, leadingIcon, trailingIcon,
                prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine,
                maxLines, minLines, shape = shape ?: QuietFieldShape, colors = quietFieldColors(look)
            )
        }
        return
    }
    val source = remember { MutableInteractionSource() }
    ThemedField(
        look, sh, source, enabled, isError, label != null, modifier, textStyle
    ) { m, style, colors, thin, thick ->
        BasicTextField(
            value, onValueChange, m, enabled, readOnly, style, keyboardOptions, keyboardActions, singleLine,
            if (singleLine) 1 else maxLines, if (singleLine) 1 else minLines, visualTransformation,
            interactionSource = source, cursorBrush = SolidColor(if (isError) look.scheme.error else colors.cursorColor),
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value, innerTextField = inner, enabled = enabled, singleLine = singleLine,
                    visualTransformation = visualTransformation, interactionSource = source, isError = isError,
                    label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = { FieldContainer(look, enabled, isError, source, colors, sh, thin, thick) }
                )
            }
        )
    }
}

/** The same with a [TextFieldValue] (when the cursor's place matters, such as the code boxes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape? = null
) {
    val look = LocalLook.current
    val sh = shape ?: fieldShape(look)
    if (look.design == "quiet") {
        // Typed text starts at the left, also inside Quiet's centred dialogs (2.210), unless the caller asked otherwise.
        // The text's own colour too (owner, 16 November 2026: values looked greyed out): inside a dialog the dialog's
        // grey explanation colour came along with the text style; what you type or chose is full ink.
        val typed = if (textStyle == LocalTextStyle.current) textStyle.copy(
            textAlign = androidx.compose.ui.text.style.TextAlign.Start, color = androidx.compose.ui.graphics.Color.Unspecified
        ) else textStyle
        QuietLabelled(modifier, label) {
            M3OutlinedTextField(
                value, onValueChange, Modifier.fillMaxWidth(), enabled, readOnly, typed, null, placeholder, leadingIcon, trailingIcon,
                prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine,
                maxLines, minLines, shape = shape ?: QuietFieldShape, colors = quietFieldColors(look)
            )
        }
        return
    }
    val source = remember { MutableInteractionSource() }
    ThemedField(look, sh, source, enabled, isError, label != null, modifier, textStyle) { m, style, colors, thin, thick ->
        BasicTextField(
            value, onValueChange, m, enabled, readOnly, style, keyboardOptions, keyboardActions, singleLine,
            if (singleLine) 1 else maxLines, if (singleLine) 1 else minLines, visualTransformation,
            interactionSource = source, cursorBrush = SolidColor(if (isError) look.scheme.error else colors.cursorColor),
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value.text, innerTextField = inner, enabled = enabled, singleLine = singleLine,
                    visualTransformation = visualTransformation, interactionSource = source, isError = isError,
                    label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = { FieldContainer(look, enabled, isError, source, colors, sh, thin, thick) }
                )
            }
        )
    }
}

/**
 * Quiet's fields (2.207, owner: labels above the fields): the label sits above the field in ink, never inside its
 * line, so it doesn't move when you type. The caller's [modifier] (width, weight, focus) goes on the whole.
 */
@Composable
private fun QuietLabelled(modifier: Modifier, label: (@Composable () -> Unit)?, field: @Composable () -> Unit) {
    if (label == null) { Box(modifier) { field() }; return }
    val ink = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides ink,
            LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, color = ink)
        ) { Box(Modifier.padding(start = 4.dp, bottom = 6.dp)) { label() } }
        field()
    }
}

private val QuietFieldShape = Squircle(14.dp)

/** Quiet's field colours: filled with the card colour, a grey line (3 : 1), 2 dp in your colour while typing. */
@Composable
private fun quietFieldColors(look: Look): TextFieldColors {
    val cs = look.scheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant, disabledContainerColor = cs.surfaceVariant,
        errorContainerColor = cs.surfaceVariant,
        focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outline, errorBorderColor = cs.error,
        cursorColor = cs.primary, focusedTextColor = cs.onSurface, unfocusedTextColor = cs.onSurface,
        focusedPlaceholderColor = cs.onSurfaceVariant, unfocusedPlaceholderColor = cs.onSurfaceVariant,
        focusedSupportingTextColor = cs.onSurfaceVariant, unfocusedSupportingTextColor = cs.onSurfaceVariant,
        focusedLeadingIconColor = cs.onSurfaceVariant, unfocusedLeadingIconColor = cs.onSurfaceVariant,
        focusedTrailingIconColor = cs.onSurfaceVariant, unfocusedTrailingIconColor = cs.onSurfaceVariant
    )
}

/** What both text fields share: the room for the label, the minimum size and the text's colour. */
@Composable
private fun ThemedField(
    look: Look, @Suppress("UNUSED_PARAMETER") shape: Shape, @Suppress("UNUSED_PARAMETER") source: MutableInteractionSource,
    enabled: Boolean, @Suppress("UNUSED_PARAMETER") isError: Boolean, hasLabel: Boolean, modifier: Modifier, textStyle: TextStyle,
    field: @Composable (Modifier, TextStyle, TextFieldColors, Dp, Dp) -> Unit
) {
    val colors = fieldColors(look)
    val (thin, thick) = look.fieldLines()
    val ink = if (enabled) look.scheme.onSurface else look.scheme.onSurface.copy(alpha = 0.5f)
    val style = textStyle.merge(TextStyle(color = if (textStyle.color.isSpecified) textStyle.color else ink))
    // As Material's own field: room above for the label that floats up into the line.
    val m = modifier
        .then(if (hasLabel) Modifier.semantics(mergeDescendants = true) {}.padding(top = 8.dp) else Modifier)
        .defaultMinSize(OutlinedTextFieldDefaults.MinWidth, OutlinedTextFieldDefaults.MinHeight)
    field(m, style, colors, thin, thick)
}

/** The inside and the line of a field; while typing, styles with hard shadows lift it with a small shadow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldContainer(
    look: Look, enabled: Boolean, isError: Boolean, source: MutableInteractionSource, colors: TextFieldColors,
    shape: Shape, thin: Dp, thick: Dp
) {
    val focused by source.collectIsFocusedAsState()
    val lift = focused && look.design in setOf("chaos")
    val shadow = when {
        !lift -> Modifier
        else -> Modifier.hardShadow(look.shadows[0], shape, 3.dp)
    }
    OutlinedTextFieldDefaults.Container(
        enabled = enabled, isError = isError, interactionSource = source, modifier = shadow, colors = colors,
        shape = shape, focusedBorderThickness = thick, unfocusedBorderThickness = thin
    )
}
