package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/* The photo editor (see HeroEdit.kt): the text on the photo, the text tool, the colour ball and the tool buttons. */

/**
 * The text on the photo in the editor, inside the frame ([fw] x [fh] pixels on screen). Drag it to move it. Tapped
 * ([selected]) it gets a line around it and four corners: drag one to make the text bigger or smaller.
 */
@Composable
internal fun EditableText(edit: HeroEdit, fw: Float, fh: Float, selected: Boolean, onSelect: (Boolean) -> Unit, onChange: (HeroEdit) -> Unit) {
    val density = LocalDensity.current
    val now by rememberUpdatedState(edit)
    val frame by rememberUpdatedState(Size(fw, fh))
    val sel by rememberUpdatedState(selected)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val col = Color(edit.color)
    val boxCol = if (col.luminance() > 0.5f) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f)
    Box(
        Modifier
            .onSizeChanged { size = it }
            .offset {
                IntOffset((edit.tx * fw - size.width / 2f).roundToInt(), (edit.ty * fh - size.height / 2f).roundToInt())
            }
            .pointerInput(Unit) { detectTapGestures { onSelect(!sel) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { onSelect(true) }) { ch, dr ->
                    ch.consume()
                    val e = now
                    onChange(e.copy(tx = (e.tx + dr.x / frame.width).coerceIn(0f, 1f), ty = (e.ty + dr.y / frame.height).coerceIn(0f, 1f)))
                }
            }
            // Room around the words for the corners, so they are inside and can be touched.
            .padding(12.dp)
    ) {
        Text(
            edit.text, color = col, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall.copy(
                shadow = if (edit.box) null else heroTextShadow,
                fontSize = with(density) { (fh * edit.textSize).toSp() }
            ),
            modifier = Modifier
                .then(if (selected) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                .then(
                    if (edit.box) Modifier.clip(RoundedCornerShape(8.dp)).background(boxCol).padding(horizontal = 10.dp, vertical = 4.dp)
                    else Modifier.padding(horizontal = 4.dp)
                )
        )
    }
    if (selected) {
        // The four corners, placed on the text's corners.
        val half = with(density) { 12.dp.toPx() }
        listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
            Box(
                Modifier
                    .offset {
                        val cx = edit.tx * fw + sx * (size.width / 2f - half)
                        val cy = edit.ty * fh + sy * (size.height / 2f - half)
                        IntOffset((cx - half).roundToInt(), (cy - half).roundToInt())
                    }
                    .size(24.dp)
                    .pointerInput(sx, sy) {
                        detectDragGestures { ch, dr ->
                            ch.consume()
                            val e = now
                            val reach = (kotlin.math.hypot(size.width.toFloat(), size.height.toFloat()) / 2f).coerceAtLeast(1f)
                            val grow = (dr.x * sx + dr.y * sy) / reach
                            onChange(e.copy(textSize = (e.textSize * (1f + grow)).coerceIn(0.06f, 0.6f)))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape))
            }
        }
    }
}

/** The text box: the words, then the colours, the rainbow dot (any colour) and the box behind the text in one row. */
@Composable
internal fun TextTool(edit: HeroEdit, onDark: Color, onChange: (HeroEdit) -> Unit) {
    var wheel by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val w = Color.White
        // Material's own field: the photo editor is always dark, whatever the style.
        androidx.compose.material3.OutlinedTextField(
            edit.text, { onChange(edit.copy(text = it.take(40))) },
            label = { Text(tr("Text on the photo")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            // Light on the dark editor, whatever the theme.
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = w, unfocusedTextColor = w, cursorColor = w,
                focusedLabelColor = w, unfocusedLabelColor = w.copy(alpha = 0.7f),
                focusedBorderColor = w, unfocusedBorderColor = w.copy(alpha = 0.5f)
            ), keyboardOptions = Capitalised)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val custom = TEXT_COLOURS.none { it == edit.color }
            TEXT_COLOURS.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (edit.color == c) 3.dp else 1.dp, if (edit.color == c) Color(0xFF8FB8FF) else onDark.copy(alpha = 0.4f), CircleShape)
                        .clickable { onChange(edit.copy(color = c)); wheel = false }
                )
            }
            // Any colour: a rainbow dot that opens the colour ball.
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(Brush.sweepGradient(RAINBOW))
                    .border(if (custom || wheel) 3.dp else 1.dp, if (custom || wheel) Color(0xFF8FB8FF) else onDark.copy(alpha = 0.4f), CircleShape)
                    .clickable { wheel = !wheel }
            )
            Chip(tr("Box"), edit.box) { onChange(edit.copy(box = !edit.box)) }
        }
        if (wheel) ColourBall(Color(edit.color)) { c -> onChange(edit.copy(color = c.toArgb().toLong() and 0xFFFFFFFFL)) }
    }
}

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/**
 * A ball with every colour: the colours around it, white in the middle. Slide a finger over it; the small rectangle on
 * the right shows the colour under your finger.
 */
@Composable
private fun ColourBall(current: Color, onPick: (Color) -> Unit) {
    var at by remember { mutableStateOf<Offset?>(null) }
    fun colourAt(p: Offset, r: Float, c: Offset): Color {
        val dx = p.x - c.x
        val dy = p.y - c.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(r)
        var hue = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
        if (hue < 0) hue += 360f
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, dist / r, 1f)))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            Modifier.size(150.dp).pointerInput(Unit) {
                val r = size.width / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                fun pickAt(p: Offset) {
                    val v = p - c
                    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                    val q = if (len > r) c + v * (r / len) else p
                    at = q
                    onPick(colourAt(q, r, c))
                }
                detectDragGestures(onDragStart = { pickAt(it) }) { ch, _ ->
                    ch.consume()
                    pickAt(ch.position)
                }
            }.pointerInput(Unit) {
                val r = size.width / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                detectTapGestures { p ->
                    val v = p - c
                    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                    val q = if (len > r) c + v * (r / len) else p
                    at = q
                    onPick(colourAt(q, r, c))
                }
            }
        ) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(RAINBOW, center), r, center)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r, center)
            at?.let {
                drawCircle(Color.White, 9.dp.toPx(), it, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.5f), 10.5.dp.toPx(), it, style = Stroke(1.dp.toPx()))
            }
        }
        // The colour under your finger.
        Box(
            Modifier.size(56.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(current)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = if (on) Color(0xFF111111) else Color.White,
        modifier = Modifier.clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
internal fun ToolButton(label: String, on: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (on) Color(0xFFE6E6E6) else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

/** Two crop corners. */
@Composable
internal fun CropIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(2.2f * u, cap = StrokeCap.Square)
        val a = androidx.compose.ui.graphics.Path().apply { moveTo(6f * u, 2f * u); lineTo(6f * u, 18f * u); lineTo(22f * u, 18f * u) }
        val b = androidx.compose.ui.graphics.Path().apply { moveTo(2f * u, 6f * u); lineTo(18f * u, 6f * u); lineTo(18f * u, 22f * u) }
        drawPath(a, c, style = st)
        drawPath(b, c, style = st)
    }
}

/** A sun: lighter or darker. */
@Composable
internal fun SunIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawCircle(c, 4.5f * u, center)
        for (i in 0 until 8) {
            val ang = Math.toRadians(i * 45.0)
            val s = Offset(center.x + (7.5f * u * Math.cos(ang)).toFloat(), center.y + (7.5f * u * Math.sin(ang)).toFloat())
            val e = Offset(center.x + (10.5f * u * Math.cos(ang)).toFloat(), center.y + (10.5f * u * Math.sin(ang)).toFloat())
            drawLine(c, s, e, 2f * u, StrokeCap.Round)
        }
    }
}

/** An arrow up out of a tray: a new photo. */
@Composable
internal fun UploadIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(2.2f * u, cap = StrokeCap.Round)
        drawLine(c, Offset(12f * u, 3f * u), Offset(12f * u, 15f * u), 2.2f * u, StrokeCap.Round)
        val head = androidx.compose.ui.graphics.Path().apply { moveTo(7f * u, 8f * u); lineTo(12f * u, 3f * u); lineTo(17f * u, 8f * u) }
        drawPath(head, c, style = st)
        val tray = androidx.compose.ui.graphics.Path().apply { moveTo(4f * u, 15f * u); lineTo(4f * u, 20f * u); lineTo(20f * u, 20f * u); lineTo(20f * u, 15f * u) }
        drawPath(tray, c, style = st)
    }
}

/** "Fit whole photo": a frame with a smaller picture inside it (our own drawing). */
@Composable
internal fun FitIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRect(c, Offset(3f * u, 3f * u), Size(18f * u, 18f * u), style = Stroke(1.8f * u))
        drawRect(c, Offset(7f * u, 8f * u), Size(10f * u, 8f * u), style = Stroke(1.8f * u))
    }
}
