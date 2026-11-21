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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.DropdownMenu as M3DropdownMenu
import androidx.compose.material3.HorizontalDivider as M3HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator as M3LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider as M3Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/* The style's menus, tick boxes, choice circles, chips, tabs, spinners, progress bars, dividers and sliders (see Themed.kt). */

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
        shadowElevation = if (look.design in setOf("quiet")) 6.dp else 0.dp,
        border = line,
        content = content
    )
}

/** The size and shape of a tick box in the style. */
private fun Look.boxShape(): Shape = when (design) {
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
    val edge = when (look.design) { "quiet" -> cs.outline; else -> look.line }
    val w = maxOf(look.edge, 2.dp)
    val tap = if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize()
        .toggleable(checked, enabled = enabled, role = Role.Checkbox) { onCheckedChange(it) } else Modifier
    Box(modifier.then(tap).alphaIf(!enabled), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp)
                .then(if (checked && look.design in setOf("chaos")) Modifier.hardShadow(look.shadows[0], shape, 2.dp) else Modifier)
                .clip(shape)
                .then(
                    when {
                        !checked -> Modifier.background(cs.surfaceVariant)
                        else -> Modifier.background(if (look.design == "chaos") look.tints[0] else cs.primary)
                    }
                )
                .then(if (!checked || look.edge > 0.dp) Modifier.edgeLine(w, if (checked && look.edge == 0.dp) Color.Transparent else edge, shape) else Modifier)
                .drawBehind {
                    if (checked) tick(
                        if (look.design == "chaos") Color(0xFF0D0D0D) else cs.onPrimary,
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
    val edge = if (look.design == "chaos") look.line else if (selected) cs.primary else cs.outline
    val w = maxOf(look.edge, 2.dp)
    val dot = if (look.design == "chaos") look.line else cs.primary
    val tap = if (onClick != null) Modifier.minimumInteractiveComponentSize()
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick) else Modifier
    Box(modifier.then(tap).alphaIf(!enabled), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(cs.surfaceVariant).edgeLine(w, edge, CircleShape)
                .drawBehind {
                    if (selected) drawCircle(dot, size.minDimension * 0.25f)
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
                .padding(start = if (leadingIcon != null || selected) 8.dp else 14.dp, end = if (trailingIcon != null) 8.dp else 14.dp),
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
 * Two or three tabs side by side (Chats / Friends, All wishes / Top wishes). Quiet (2.207): a segmented switch, a grey
 * track with the chosen one on a white (dark: lifted grey) pill, in ink; never a black button. Chaos: its chips, as
 * before.
 */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (look.design == "quiet") {
        val track = if (look.dark) look.raised else look.line
        val chosen = if (look.dark) Color(0xFF453F47) else Color.White
        Row(modifier.fillMaxWidth().clip(CircleShape).background(track).padding(3.dp)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp)
                        .then(if (on) Modifier.shadow(if (look.dark) 0.dp else 1.dp, CircleShape) else Modifier)
                        .clip(CircleShape).background(if (on) chosen else Color.Transparent)
                        .selectable(on, role = Role.Tab, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center
                ) {
                    // A little smaller rather than cut when four tabs share a narrow phone (owner, 21 November 2026).
                    FitText(label, MaterialTheme.typography.labelLarge, if (on) look.scheme.onSurface else look.scheme.onSurfaceVariant, Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    } else Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).lookChip(look, on).clickable { onSelect(i) }
                    .semantics { this.selected = on }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                FitText(label, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), look.chipContent(on), Modifier.padding(horizontal = 4.dp))
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
    val w = if (strokeWidth != Dp.Unspecified) strokeWidth else when (look.design) { "chaos" -> 5.dp; else -> 4.dp }
    Canvas(modifier.progressSemantics().size(40.dp)) {
        val sw = w.toPx()
        val inset = sw / 2 + 1f
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        val tl = Offset(inset, inset)
        val cap = StrokeCap.Round
        val one = color.isSpecified
        rotate(turn) {
            when {
                one -> drawArc(color, 0f, 110f, false, tl, arcSize, style = Stroke(sw, cap = cap))
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
    M3LinearProgressIndicator(
        progress = progress, modifier = modifier.height(if (look.edge > 2.dp) 5.dp else 4.dp),
        color = if (look.design == "chaos") look.line else cs.primary, trackColor = cs.primaryContainer,
        strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
    )
}

/** The shape of text fields: the style's small shape. */
@Composable
internal fun fieldShape(@Suppress("UNUSED_PARAMETER") look: Look): Shape = MaterialTheme.shapes.small

/** A divider line in the style: thicker in the styles with ink lines; Doodle a wavy marker line. */
@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    M3HorizontalDivider(
        modifier,
        thickness = when (look.design) { "chaos" -> 2.dp; else -> 1.dp },
        color = if (color.isSpecified && look.design == "quiet") color else when (look.design) {
            "chaos" -> look.line.copy(alpha = 0.7f)
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
