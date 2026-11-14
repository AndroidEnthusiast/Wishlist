package com.wishlist.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.DropdownMenu as M3DropdownMenu
import androidx.compose.material3.HorizontalDivider as M3HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator as M3LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet as M3ModalBottomSheet
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.Scaffold as M3Scaffold
import androidx.compose.material3.Slider as M3Slider
import androidx.compose.material3.Snackbar as M3Snackbar

/*
 * The rest of the app's own parts (owner, 13 November 2026: "a theme must change absolutely every element of the app":
 * until 2.199 these were plain Android in every style). Text fields, menus, tick boxes, choice circles, chips, spinners,
 * progress bars, divider lines, sliders, the bottom panels, the message bar and the page behind every screen take the
 * chosen style's lines, shapes, shadows and colours. Screens use these, never Material's own (docs/STYLE.md); Quiet
 * keeps Material's calm look. Buttons, switches, dialogs and the top bar are in Styled.kt.
 */

/** The page behind a screen: the style's background colour and its drawing ([lookBackdrop]). */
@Composable
fun Modifier.lookPage(): Modifier {
    val look = LocalLook.current
    return background(look.scheme.background).lookBackdrop(look)
}

/**
 * Material's Scaffold with the style's page behind it ([lookPage]): every screen gets the style's background
 * drawing, not only Home. [containerColor] is the page's colour (normally the background).
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val look = LocalLook.current
    M3Scaffold(
        modifier.background(containerColor).then(if (containerColor == look.scheme.background) Modifier.lookBackdrop(look) else Modifier),
        topBar, bottomBar, snackbarHost, floatingActionButton, floatingActionButtonPosition,
        containerColor = Color.Transparent, contentColor = contentColor, contentWindowInsets = contentWindowInsets, content = content
    )
}

/** How thick a field's line is when it is not and when it is being typed in. */
private fun Look.fieldLines(): Pair<Dp, Dp> = when (design) {
    "quiet", "flat" -> 1.dp to 2.dp
    "chaos" -> 3.dp to 3.5.dp
    else -> maxOf(edge, 1.5.dp) to maxOf(edge, 1.5.dp) + 1.dp
}

/** The colours of a text field in the style: lines in the style's ink, the style's text colour when typing. */
@Composable
private fun fieldColors(look: Look): TextFieldColors {
    val cs = look.scheme
    val edge = when (look.design) {
        "quiet" -> cs.outline
        "flat" -> cs.outline
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
        M3OutlinedTextField(
            value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon,
            prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine,
            maxLines, minLines, shape = sh
        )
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
        M3OutlinedTextField(
            value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon,
            prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine,
            maxLines, minLines, shape = sh
        )
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
    val lift = focused && look.design in setOf("chaos", "retro", "grunge", "bauhaus", "y2k", "pop", "riso", "anthro", "cottage", "punk")
    val shadow = when {
        !lift -> Modifier
        look.design == "pop" -> Modifier.hardShadow(look.shadows[0], shape, 3.dp)
        else -> Modifier.hardShadow(look.shadows[0], shape, 3.dp, look.shadowBrush)
    }
    OutlinedTextFieldDefaults.Container(
        enabled = enabled, isError = isError, interactionSource = source, modifier = shadow, colors = colors,
        shape = shape, focusedBorderThickness = thick, unfocusedBorderThickness = thin
    )
}

/** A menu (⋮ and the pick-one lists) in the style: its corners, its line instead of a soft shadow, its card colour. */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val line = if (look.edge > 0.dp) BorderStroke(minOf(look.edge, 2.5.dp), look.line) else null
    M3DropdownMenu(
        expanded, onDismissRequest, modifier, androidx.compose.ui.unit.DpOffset(0.dp, 0.dp), scrollState, properties,
        shape = MaterialTheme.shapes.medium,
        containerColor = cs.surfaceContainer,
        tonalElevation = 0.dp,
        // A soft shadow only where the style has soft shadows (Quiet, Y2K); the others are framed by their line, Flat
        // by a slightly deeper colour.
        shadowElevation = if (look.design in setOf("quiet", "y2k", "flat", "japandi", "synth")) 6.dp else 0.dp,
        border = line,
        content = content
    )
}

/** The size and shape of a tick box in the style. */
private fun Look.boxShape(): Shape = when (design) {
    "bauhaus" -> RectangleShape
    "grunge" -> RoughShape(71, 1.dp, 4.dp)
    "y2k", "pop", "anthro" -> RoundedCornerShape(7.dp)
    "doodle" -> WobblyShape(73, 6.dp, 1.dp)
    "swiss" -> RectangleShape
    "punk" -> RoughShape(75, 1.dp, 4.dp)
    "japandi" -> RoundedCornerShape(2.dp)
    "chaos" -> RoundedCornerShape(6.dp)
    else -> RoundedCornerShape(4.dp)
}

/** A tick, drawn (the same lines as the rest of the app's own drawings). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.tick(col: Color, inset: Float, width: Float) {
    val w = size.width - 2 * inset
    val p = Path().apply {
        moveTo(inset + w * 0.18f, inset + w * 0.52f)
        lineTo(inset + w * 0.42f, inset + w * 0.76f)
        lineTo(inset + w * 0.84f, inset + w * 0.26f)
    }
    drawPath(p, col, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A tick box in the style: its shape and line; ticked, it fills with the style's colour (a gradient in Y2K and Pop). */
@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.boxShape()
    val edge = when (look.design) { "quiet", "flat" -> cs.outline; else -> look.line }
    val w = maxOf(look.edge, 2.dp)
    val tap = if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize()
        .toggleable(checked, enabled = enabled, role = Role.Checkbox) { onCheckedChange(it) } else Modifier
    // Doodle: drawn by hand (Doodle.kt); Y2K: a gel bubble (Y2k.kt).
    if (look.design == "doodle" || look.design == "y2k") {
        Box(modifier.then(tap).alphaIf(!enabled), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) { if (look.design == "doodle") doodleCheckbox(look, checked) else y2kCheckbox(look, checked) }
        }
        return
    }
    Box(modifier.then(tap).alphaIf(!enabled), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp)
                .then(if (checked && look.design in setOf("chaos", "bauhaus")) Modifier.hardShadow(look.shadows[0], shape, 2.dp) else Modifier)
                .clip(shape)
                .then(
                    when {
                        !checked -> Modifier.background(cs.surfaceVariant)
                        look.accentBrush != null -> Modifier.background(look.accentBrush)
                        look.design == "quiet" || look.design == "chaos" -> Modifier.background(if (look.design == "chaos") look.tints[0] else cs.primary)
                        else -> Modifier.background(look.accent)
                    }
                )
                .then(if (!checked || look.edge > 0.dp) Modifier.edgeLine(w, if (checked && look.edge == 0.dp) Color.Transparent else edge, shape) else Modifier)
                .drawBehind {
                    if (checked) tick(
                        when (look.design) { "quiet" -> cs.onPrimary; "chaos" -> Color(0xFF0D0D0D); else -> look.onAccent },
                        2.dp.toPx(), 2.2.dp.toPx()
                    )
                }
        )
    }
}

/** A choice circle in the style: its line; chosen, a dot in the style's colour. */
@Composable
fun RadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val edge = when (look.design) { "quiet", "flat" -> if (selected) cs.primary else cs.outline; "chaos" -> look.line; else -> look.line }
    val w = maxOf(look.edge, 2.dp)
    val dot = when (look.design) { "chaos" -> look.line; "quiet", "flat" -> cs.primary; else -> cs.primary }
    val tap = if (onClick != null) Modifier.minimumInteractiveComponentSize()
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick) else Modifier
    if (look.design == "doodle" || look.design == "y2k") {
        Box(modifier.then(tap).alphaIf(!enabled), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) { if (look.design == "doodle") doodleRadio(look, selected) else y2kRadio(look, selected) }
        }
        return
    }
    Box(modifier.then(tap).alphaIf(!enabled), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(cs.surfaceVariant).edgeLine(w, edge, CircleShape)
                .drawBehind {
                    if (selected) {
                        if (look.accentBrush != null) drawCircle(look.accentBrush, size.minDimension * 0.28f)
                        else drawCircle(if (look.design == "bauhaus") look.art[0] else dot, size.minDimension * 0.25f)
                        if (look.accentBrush != null) drawCircle(look.line, size.minDimension * 0.28f, style = Stroke(1.dp.toPx()))
                    }
                }
        )
    }
}

private fun Modifier.alphaIf(faded: Boolean): Modifier = if (faded) alpha(0.45f) else this

/** A chip to pick (occasion, quantity, exact or similar) in the style ([lookChip]): chosen ones filled. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val look = LocalLook.current
    val ink = look.chipContent(selected)
    Box(
        modifier.minimumInteractiveComponentSize()
            .selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(vertical = 2.dp).lookChip(look, selected)
                // Retro's tag chips keep room at their pointed end for the hole.
                .padding(start = (if (leadingIcon != null || selected) 8.dp else 14.dp) + (if (look.design == "retro") 8.dp else 0.dp), end = if (trailingIcon != null) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides ink,
                LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(color = ink)
            ) {
                if (leadingIcon != null) { leadingIcon(); Box(Modifier.width(6.dp)) }
                else if (selected) {
                    Canvas(Modifier.size(18.dp)) { tick(ink, 1.dp.toPx(), 2.dp.toPx()) }
                    Box(Modifier.width(6.dp))
                }
                label()
                if (trailingIcon != null) { Box(Modifier.width(6.dp)); trailingIcon() }
            }
        }
    }
}

/**
 * "Loading", in the style: Quiet uses Material's spinner; the others draw their own turning ring (a holographic
 * sweep in Y2K, red and mustard in Retro, three racing stripes in Pop futurism, a broken ink ring in Grunge, a
 * calm teal arc in Flat, red, yellow and blue quarters in Bauhaus, thick ink in chaos). [color] given: one colour
 * (on a filled button or a chat bubble).
 */
@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = Color.Unspecified, strokeWidth: Dp = Dp.Unspecified) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    if (look.design == "quiet" && StillSpinners.on) {
        M3CircularProgressIndicator({ 0.3f }, modifier, color = if (color.isSpecified) color else cs.primary)
        return
    }
    if (look.design == "quiet") {
        M3CircularProgressIndicator(
            modifier, color = if (color.isSpecified) color else cs.primary,
            strokeWidth = if (strokeWidth != Dp.Unspecified) strokeWidth else 4.dp
        )
        return
    }
    val turn = if (StillSpinners.on) 30f else rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "turn"
    ).value
    val w = if (strokeWidth != Dp.Unspecified) strokeWidth else when (look.design) { "chaos", "bauhaus", "pop" -> 5.dp; else -> 4.dp }
    Canvas(modifier.progressSemantics().size(40.dp)) {
        val sw = w.toPx()
        val inset = sw / 2 + 1f
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        val tl = Offset(inset, inset)
        val square = look.design == "bauhaus" || look.design == "grunge"
        val cap = if (square) StrokeCap.Butt else StrokeCap.Round
        val one = color.isSpecified
        rotate(turn) {
            when {
                one -> drawArc(color, 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                look.design == "bauhaus" -> listOf(look.art[0], look.art[2], look.art[1]).forEachIndexed { i, col ->
                    drawArc(col, i * 120f, 80f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                look.design == "pop" -> {
                    val third = sw / 3
                    listOf(look.art[3], look.art[0], look.art[1]).forEachIndexed { i, col ->
                        val k = inset - third + i * third
                        drawArc(col, 0f, 140f, false, Offset(k, k), Size(size.width - 2 * k, size.height - 2 * k), style = Stroke(third, cap = StrokeCap.Butt))
                    }
                }
                look.design == "grunge" -> for (i in 0 until 5) {
                    drawArc(if (i == 0) look.accent else look.line, i * 34f, 22f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                look.design == "retro" -> {
                    drawArc(look.art[1], 0f, 360f, false, tl, arcSize, style = Stroke(sw * 0.5f))
                    drawArc(look.art[0], 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                look.design == "y2k" -> drawArc(
                    Brush.sweepGradient(listOf(look.art[0], look.art[2], look.art[1], look.art[0]), center),
                    0f, 300f, false, tl, arcSize, style = Stroke(sw, cap = cap)
                )
                look.design == "doodle" -> doodleSpinner(look, sw)
                look.design == "riso" -> {
                    drawArc(look.art[1], 12f, 110f, false, Offset(tl.x + 2f, tl.y + 2f), arcSize, style = Stroke(sw, cap = cap))
                    drawArc(look.art[0], 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                look.design == "synth" -> drawArc(
                    Brush.sweepGradient(listOf(look.art[3], look.art[2], look.art[1], look.art[0], look.art[3]), center),
                    0f, 300f, false, tl, arcSize, style = Stroke(sw, cap = cap)
                )
                look.design == "swiss" -> {
                    drawArc(look.scheme.outlineVariant, 0f, 360f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
                    drawArc(look.art[0], 0f, 90f, true, Offset.Zero, size, style = androidx.compose.ui.graphics.drawscope.Fill)
                }
                look.design == "punk" -> for (i in 0 until 4) {
                    drawArc(if (i % 2 == 0) look.accent else look.line, i * 90f, 55f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
                }
                look.design == "japandi" -> {
                    drawArc(cs.outlineVariant, 0f, 360f, false, tl, arcSize, style = Stroke(1.dp.toPx()))
                    drawArc(look.scheme.onSurface, 0f, 90f, false, tl, arcSize, style = Stroke(sw * 0.6f, cap = cap))
                }
                look.design == "cottage" -> {
                    drawArc(look.art[0], 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                    drawArc(look.art[1], 180f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                look.design == "anthro" -> listOf(look.art[0], look.art[1], look.art[2], look.art[3]).forEachIndexed { i, col ->
                    drawArc(col, i * 90f, 60f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                look.design == "flat" -> {
                    drawArc(cs.primaryContainer, 0f, 360f, false, tl, arcSize, style = Stroke(sw))
                    drawArc(cs.primary, 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
                }
                else -> drawArc(look.line, 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
            }
        }
    }
}

/** Only for the screenshot checks: spinners stand still, so the pretend phone can settle and be photographed. */
object StillSpinners { @Volatile var on = false }

/** A progress bar (a page loading) in the style's colour, track and line ends. */
@Composable
fun LinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val square = look.design == "bauhaus" || look.design == "grunge"
    M3LinearProgressIndicator(
        progress = progress, modifier = modifier.height(if (look.edge > 2.dp) 5.dp else 4.dp),
        color = if (look.design == "chaos") look.line else cs.primary, trackColor = cs.primaryContainer,
        strokeCap = if (square) StrokeCap.Butt else StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
    )
}

/** The shape of text fields: the style's small shape; Doodle's hand-drawn box. */
@Composable
private fun fieldShape(look: Look): Shape = if (look.design == "doodle") WobblyShape(91, 10.dp, 1.4.dp) else MaterialTheme.shapes.small

/** A divider line in the style: thicker in the styles with ink lines; Doodle a wavy marker line. */
@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    if (look.design == "doodle") {
        Canvas(modifier.fillMaxWidth().height(6.dp)) { doodleDivider(look, look.line.copy(alpha = 0.6f)) }
        return
    }
    if (look.design == "y2k") {
        Canvas(modifier.fillMaxWidth().height(10.dp)) { y2kDivider(look) }
        return
    }
    M3HorizontalDivider(
        modifier,
        thickness = when (look.design) { "chaos", "bauhaus", "doodle", "anthro", "swiss", "punk" -> 2.dp; "retro", "grunge", "riso", "cottage" -> 1.5.dp; else -> 1.dp },
        color = if (color.isSpecified && look.design == "quiet") color else when (look.design) {
            "chaos", "bauhaus", "retro", "grunge", "doodle", "anthro", "riso", "cottage", "synth", "punk" -> look.line.copy(alpha = 0.7f)
            "swiss" -> look.line
            else -> if (color.isSpecified) color else cs.outlineVariant
        }
    )
}

/** A slider in the style's colour (a colour given, as for brightness in the colour picker, stays). */
@Composable
fun Slider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, colors: SliderColors? = null
) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val col = if (look.design == "chaos") look.line else cs.primary
    M3Slider(
        value, onValueChange, modifier, valueRange = valueRange,
        colors = colors ?: SliderDefaults.colors(thumbColor = col, activeTrackColor = col, inactiveTrackColor = cs.primaryContainer)
    )
}

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
        "bauhaus" -> RectangleShape
        "grunge" -> RoughShape(81, 2.dp)
        "pop" -> RoundedCornerShape(topStart = 30.dp, topEnd = 8.dp)
        "doodle" -> WobblyShape(83, 28.dp, 2.dp)
        "swiss" -> RectangleShape
        "punk" -> RoughShape(85, 2.6.dp, 8.dp)
        else -> (corner as? RoundedCornerShape)?.let { RoundedCornerShape(topStart = it.topStart, topEnd = it.topEnd, bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp), bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)) }
            ?: RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }
    M3ModalBottomSheet(
        onDismissRequest, modifier, sheetState, shape = shape, containerColor = if (look.design == "quiet") containerColor else look.scheme.surfaceVariant,
        tonalElevation = 0.dp,
        dragHandle = { SheetHandle(look) },
        content = content
    )
}

/** The handle at the top of a bottom panel. */
@Composable
private fun SheetHandle(look: Look) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (look.design) {
            "bauhaus" -> Row {
                listOf(look.art[0], look.art[2], look.art[1]).forEach { Box(Modifier.size(width = 14.dp, height = 5.dp).background(it)) }
            }
            "pop" -> Row {
                listOf(look.art[3], look.art[0], look.art[1]).forEach { Box(Modifier.padding(horizontal = 2.dp).size(width = 12.dp, height = 5.dp).clip(CircleShape).background(it)) }
            }
            "y2k" -> Box(Modifier.size(width = 40.dp, height = 5.dp).clip(CircleShape).background(look.accentBrush!!).edgeLine(1.dp, look.line, CircleShape))
            "chaos" -> Box(Modifier.size(width = 44.dp, height = 6.dp).clip(CircleShape).background(look.line))
            "grunge" -> Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoughShape(91, 1.dp, 4.dp)).background(look.line))
            "retro" -> Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(look.art[0]))
            "riso" -> Box {
                Box(Modifier.offset(2.dp, 1.dp).size(width = 40.dp, height = 5.dp).background(look.art[1]))
                Box(Modifier.size(width = 40.dp, height = 5.dp).background(look.art[0]))
            }
            "synth" -> Box(Modifier.size(width = 44.dp, height = 4.dp).clip(CircleShape).background(look.accentBrush!!))
            "swiss" -> Row { Box(Modifier.size(width = 28.dp, height = 4.dp).background(look.line)); Box(Modifier.size(width = 12.dp, height = 4.dp).background(look.art[0])) }
            "punk" -> Box(Modifier.size(width = 46.dp, height = 12.dp).clip(RoughShape(95, 1.dp, 4.dp)).background(Color(0xFFF2E6B8).copy(alpha = 0.85f)))
            "anthro" -> Row { listOf(look.art[0], look.art[3], look.art[1]).forEach { Box(Modifier.padding(horizontal = 3.dp).size(9.dp).clip(CircleShape).background(it)) } }
            "cottage" -> Row { listOf(look.art[0], look.art[1], look.art[0]).forEach { Box(Modifier.padding(horizontal = 2.dp).size(width = 12.dp, height = 4.dp).clip(CircleShape).background(it)) } }
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
    val shape = MaterialTheme.shapes.small
    M3Snackbar(
        data,
        modifier = Modifier.padding(4.dp).lookSurface(look, shape, cs.inverseSurface, depth = 5.dp),
        shape = shape, containerColor = Color.Transparent, contentColor = cs.inverseOnSurface,
        actionColor = cs.inverseOnSurface, actionContentColor = cs.inverseOnSurface, dismissActionContentColor = cs.inverseOnSurface
    )
}
