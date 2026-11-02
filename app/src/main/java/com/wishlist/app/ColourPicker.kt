package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The colours of the rainbow, round the ball (red at the right, going clockwise as the hue goes up). */
private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** The rainbow dot after the five accents in the Themer: "any colour you want". */
fun Modifier.rainbowDot(): Modifier = clip(CircleShape).background(Brush.sweepGradient(RAINBOW))

/**
 * Any colour (owner, 2.101): slide over the ball of colours (round: the colour; from the middle out: how strong), a
 * brightness slider under it, the colour in a preview, a warning when it may be hard to read (allowed anyway, owner),
 * and "Use this colour", which closes it. [start] is "#RRGGBB" or an accent key.
 */
@Composable
fun ColourPickerDialog(start: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(Color(accentFor(start).light).toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var bright by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.15f)) }
    val colour = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bright)))
    val key = colourKey(colour)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Your colour")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Slide over the ball to pick any colour."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                // The ball: a ring of colours, white in the middle, darker with the slider.
                fun pick(o: Offset, w: Float, h: Float) {
                    val r = min(w, h) / 2f
                    val dx = o.x - w / 2f; val dy = o.y - h / 2f
                    hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                    sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                }
                Canvas(
                    Modifier.fillMaxWidth(0.8f).aspectRatio(1f)
                        .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { pick(it, size.width.toFloat(), size.height.toFloat()) }) { ch, _ ->
                                ch.consume(); pick(ch.position, size.width.toFloat(), size.height.toFloat())
                            }
                        }
                ) {
                    val r = min(size.width, size.height) / 2f
                    drawCircle(Brush.sweepGradient(RAINBOW, center), r)
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                    drawCircle(Color.Black.copy(alpha = 1f - bright), r)
                    // Where your finger is: a ring in the chosen colour.
                    val a = Math.toRadians(hue.toDouble())
                    val p = Offset(center.x + (cos(a) * sat * r).toFloat(), center.y + (sin(a) * sat * r).toFloat())
                    drawCircle(colour, 13.dp.toPx(), p)
                    drawCircle(Color.White, 13.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.35f), 14.5f.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                }
                // Brightness: dark on the left, bright on the right.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Brightness"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))
                    Slider(
                        value = bright, onValueChange = { bright = it }, valueRange = 0.15f..1f,
                        colors = SliderDefaults.colors(thumbColor = colour, activeTrackColor = colour),
                        modifier = Modifier.weight(1f)
                    )
                }
                // The colour, as a button would look in light and in dark.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ac = accentFor(key)
                    listOf(Color(0xFFF7F7F9) to Color(ac.light), Color(0xFF121214) to Color(ac.dark)).forEach { (page, tone) ->
                        Box(
                            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(page)
                                .border(1.dp, cs.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .padding(10.dp)
                        ) {
                            Box(Modifier.size(width = 72.dp, height = 30.dp).clip(CircleShape).background(tone))
                        }
                    }
                }
                if (hardToRead(key)) Text(
                    tr("This colour may be hard to read."), style = MaterialTheme.typography.bodySmall, color = cs.error
                )
            }
        },
        confirmButton = { Button(onClick = { onPick(key) }) { Text(tr("Use this colour")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}
