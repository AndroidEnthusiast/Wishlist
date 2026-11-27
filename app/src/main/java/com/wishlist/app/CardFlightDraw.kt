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

/* How the cards in flight are drawn (CardMotion.kt; owner, 25 and 26 November 2026; the bin's drawing went with the
 * delete animation, owner 27 November 2026). */

internal val LinearOutSlowIn = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

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
