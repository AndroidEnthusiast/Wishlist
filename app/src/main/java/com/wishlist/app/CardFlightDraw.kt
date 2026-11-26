package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* How the cards in flight and the bin are drawn (CardMotion.kt; owner, 25 and 26 November 2026). */

internal val LinearOutSlowIn = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

/** The bin drawn during a delete: 30 dp, centred on the card's bin button (or the selection bar's Delete). */
internal fun DrawScope.binAt(r: Rect): Rect {
    val half = 15f * density
    return Rect(r.center.x - half, r.center.y - half, r.center.x + half, r.center.y + half)
}

/** The disc under the bin: it pops up first and goes last. */
internal fun discScale(p: Float): Float {
    val inP = (p / 0.12f).coerceIn(0f, 1f)
    val outP = ((p - 0.84f) / 0.16f).coerceIn(0f, 1f)
    val e = 1f - (1f - inP) * (1f - inP)
    return e * (1f - outP * outP)
}

internal fun DrawScope.drawDisc(bin: Rect, s: Float, fill: Color) {
    if (s <= 0f) return
    val r = 28f * density * s
    // A soft shadow, then the disc: the bin reads as an object above whatever is behind it.
    for (k in 1..4) drawCircle(Color.Black.copy(alpha = 0.05f * s), r + k * 1.5f * density, bin.center + Offset(0f, 2f * density))
    drawCircle(fill, r, bin.center)
}

/** The card going into the bin at [go]: along a curve, shrinking to the bin's opening, turning a little. */
internal fun DrawScope.drawIntoBin(f: Flight, from: Rect, bin: Rect, go: Float) {
    val a = from.center
    val b = Offset(bin.center.x, bin.center.y - 2f * density)
    val mid = Offset((a.x + b.x) / 2f, minOf(a.y, b.y) - 24f * density)
    val q = 1f - go
    val c = Offset(q * q * a.x + 2 * q * go * mid.x + go * go * b.x, q * q * a.y + 2 * q * go * mid.y + go * go * b.y)
    val end = (20f * density / from.width).coerceAtMost(1f)
    // It shrinks evenly along the way, so it is still a card when it reaches the bin, then sinks in.
    val s = 1f - (1f - end) * go
    val w = from.width * s
    val h = from.height * s
    rotate(14f * go, c) {
        drawImage(
            f.image, dstOffset = IntOffset((c.x - w / 2f).toInt(), (c.y - h / 2f).toInt()),
            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        )
    }
}

/** How far the card has gone at [p]: a peeled copy lifts first (to 0.25), then flies. */
internal fun DrawScope.drawFlight(f: Flight, p: Float) {
    val lift = if (f.peel) (p / 0.25f).coerceIn(0f, 1f) else 0f
    val go = if (f.peel) ((p - 0.25f) / 0.75f).coerceIn(0f, 1f) else p
    // The card's middle travels to the target's middle along a gentle curve; it shrinks to a sliver as it gets there.
    val a = f.from.center
    val b = f.to.center
    val bend = Offset(0f, -minOf(f.from.height, 220f * density / 2.6f) * 0.35f)
    val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f) + bend
    val q = 1f - go
    val c = Offset(q * q * a.x + 2 * q * go * mid.x + go * go * b.x, q * q * a.y + 2 * q * go * mid.y + go * go * b.y) +
        Offset(6f * density * lift * (1f - go), -18f * density * lift * (1f - go))
    // It shrinks evenly to about the Home icon's size, so you can follow it all the way (owner, 26 November 2026).
    val end = (28f * density / f.from.width).coerceAtMost(1f)
    val s = (1f - (1f - end) * go) * (1f + 0.06f * lift * (1f - go))
    val turn = 18f * go - 7f * lift * (1f - go)
    val alpha = if (go < 0.85f) 1f else 1f - (go - 0.85f) / 0.15f
    val w = f.from.width * s
    val h = f.from.height * s
    rotate(turn, c) {
        // A peeled copy casts a soft shadow while it is lifted, so it reads as a copy coming off the card.
        if (f.peel && go < 0.5f) {
            val sh = lift * (1f - go * 2f)
            for (k in 1..3) drawRoundRect(
                Color.Black.copy(alpha = 0.06f * sh), Offset(c.x - w / 2f + k * 2f * density, c.y - h / 2f + k * 3f * density),
                androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(16f * density * s)
            )
        }
        drawImage(
            f.image, dstOffset = IntOffset((c.x - w / 2f).toInt(), (c.y - h / 2f).toInt()),
            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)), alpha = alpha.coerceIn(0f, 1f)
        )
    }
}

/** The lid opens just after the disc is up and shuts once the card is in. */
internal fun lidOpen(p: Float): Float {
    val open = ((p - 0.08f) / 0.12f).coerceIn(0f, 1f)
    val shut = ((p - 0.6f) / 0.1f).coerceIn(0f, 1f)
    return open * (1f - shut)
}

/** The small bump as the lid shuts. */
internal fun bump(p: Float, back: Boolean): Float {
    if (back) return 0f
    val x = ((p - 0.68f) / 0.1f).coerceIn(0f, 1f)
    return kotlin.math.sin(x * Math.PI).toFloat() * 0.16f
}

/** The bin's size at [r], in units of 24 (the same as [drawBin]). */
internal fun DrawScope.binUnit(r: Rect): Float = minOf(r.width, r.height) / 24f

/** Our own bin (the CardDelete icon's shape) at [r], its lid turned open by [open] (0..1). */
internal fun DrawScope.drawBin(r: Rect, open: Float, bump: Float, ink: Color) {
    val u = binUnit(r)
    val o = Offset(r.center.x - 12f * u, r.center.y - 12f * u)
    val st = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    scale(1f + bump, r.center) {
        // The can.
        val can = Path().apply {
            moveTo(o.x + 6f * u, o.y + 7f * u); lineTo(o.x + 7f * u, o.y + 19f * u)
            lineTo(o.x + 17f * u, o.y + 19f * u); lineTo(o.x + 18f * u, o.y + 7f * u)
        }
        drawPath(can, ink, style = st)
        // The lid with its handle, turning up around its left end.
        rotate(-52f * open, Offset(o.x + 4f * u, o.y + 7f * u)) {
            val lid = Path().apply {
                moveTo(o.x + 4f * u, o.y + 7f * u); lineTo(o.x + 20f * u, o.y + 7f * u)
                moveTo(o.x + 9f * u, o.y + 7f * u); lineTo(o.x + 9f * u, o.y + 5f * u)
                lineTo(o.x + 15f * u, o.y + 5f * u); lineTo(o.x + 15f * u, o.y + 7f * u)
            }
            drawPath(lid, ink, style = st)
        }
    }
}
