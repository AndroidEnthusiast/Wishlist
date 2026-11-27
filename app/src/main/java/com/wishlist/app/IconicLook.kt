package com.wishlist.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * How the Iconic prize looks (Iconic.kt; owner, 27 November 2026, from the preview): the same holographic colours as
 * the Iconic level, slowly turning (still with Remove animations): a ring round a photo, a glow round a reaction,
 * an edge round the top of a list.
 */

/** The holographic colours (the Iconic level's too). */
internal val HoloColours = listOf(Color(0xFFFF7AC6), Color(0xFFFFD36E), Color(0xFF7CF0C8), Color(0xFF7AA8FF), Color(0xFFC77DFF))

/** A slow turn, 0..360, for the holo colours (0 with Remove animations). */
@Composable
internal fun holoTurn(): Float {
    if (animationsOff(LocalContext.current)) return 0f
    val t = rememberInfiniteTransition(label = "holo-turn")
    return t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "turn").value
}

private fun sweep(center: Offset) = Brush.sweepGradient(HoloColours + HoloColours.first(), center)

/** The holo ring round a photo of [size]: drawn just inside its edge (the photo is made smaller by [ringGap]). */
fun Modifier.holoRing(on: Boolean, size: Dp): Modifier = if (!on) this else composed {
    val turn = holoTurn()
    drawWithContent {
        drawContent()
        val w = ringWidth(size).toPx()
        rotate(turn) { drawCircle(sweep(center), radius = this.size.minDimension / 2f - w / 2f, style = Stroke(w)) }
    }
}

/** How thick the ring is for a photo of [size]. */
internal fun ringWidth(size: Dp): Dp = (size * 0.06f).coerceIn(2.dp, 5.dp)

/** How much smaller the photo gets inside the ring (the ring and a little air). */
internal fun ringGap(size: Dp): Dp = ringWidth(size) + (size * 0.03f).coerceIn(1.dp, 3.dp)

/** A glow round a reaction chip of an Iconic member (or yours, when your glow is on). */
fun Modifier.holoGlow(on: Boolean): Modifier = if (!on) this else composed {
    val turn = holoTurn()
    drawBehind {
        val r = size.height / 2f
        // Soft halo: a few wider, fainter outlines, then the holo edge itself.
        for (k in 3 downTo 1) {
            val grow = k * 2.dp.toPx()
            drawRoundRect(
                Brush.linearGradient(HoloColours.map { it.copy(alpha = 0.10f) }), Offset(-grow, -grow),
                Size(size.width + 2 * grow, size.height + 2 * grow), CornerRadius(r + grow), style = Stroke(2.dp.toPx())
            )
        }
        rotate(turn) {
            drawRoundRect(sweep(center), cornerRadius = CornerRadius(r), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** The holo edge round the top of an Iconic member's list (as their friends see it). [radius]: the card's corners. */
fun Modifier.holoEdge(on: Boolean, radius: Dp): Modifier = if (!on) this else composed {
    val turn = holoTurn()
    drawWithContent {
        drawContent()
        val w = 3.dp.toPx()
        val shift = size.width * (turn / 360f)
        drawRoundRect(
            Brush.linearGradient(HoloColours + HoloColours.first(), start = Offset(-shift, 0f), end = Offset(size.width - shift, size.height),
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated),
            topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx()), style = Stroke(w)
        )
    }
}

/** The holo band at the top of an Iconic member's list that has no cover photo (as their friends see it). */
@Composable
fun HoloBand(modifier: Modifier = Modifier) {
    val turn = holoTurn()
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).drawBehind {
            val shift = size.width * (turn / 360f)
            drawRect(Brush.horizontalGradient(HoloColours + HoloColours.first(), startX = -shift, endX = size.width - shift,
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated))
        }
    )
}

