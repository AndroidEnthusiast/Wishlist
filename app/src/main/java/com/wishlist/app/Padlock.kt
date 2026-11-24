package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/*
 * The golden padlock (owner, 24 November 2026: "like the lock emoji 🔒", our own drawing, rule 7): a gold body with a
 * shine and a darker rim for depth, a silver shackle with a highlight, a keyhole. [open] runs from 0 (closed) to 1
 * (lifted and swung open around its left leg). The shackle is drawn behind the body, so its legs vanish inside it
 * like a real padlock. Someone else's reservation uses [grey]. The web pages draw the same (web/reserve.js).
 */

private val GOLD = listOf(Color(0xFFFFE07A), Color(0xFFFFC21A), Color(0xFFE69A00))
private val GOLD_RIM = Color(0xFFB87900)
private val GOLD_HOLE = Color(0xFF6B4200)
private val GREY = listOf(Color(0xFFE3E6EA), Color(0xFFC3C8CE), Color(0xFF9EA4AB))
private val GREY_RIM = Color(0xFF80868D)
private val GREY_HOLE = Color(0xFF4F555C)
private val SILVER = listOf(Color(0xFFF4F6F8), Color(0xFFB9C0C8), Color(0xFF8D96A0))

/** How far the shackle is lifted (units of 24) and turned (degrees) at openness [p]: up first, then it swings. */
internal fun shackleAt(p: Float): Pair<Float, Float> {
    val q = p.coerceIn(0f, 1f)
    return (q * 2f).coerceAtMost(1f) * 3f to ((q - 0.4f) / 0.6f).coerceIn(0f, 1f) * -30f
}

@Composable
fun Padlock(open: Float, modifier: Modifier = Modifier, grey: Boolean = false, label: String? = null) {
    Canvas(if (label == null) modifier else modifier.semantics { contentDescription = label }) {
        val u = size.minDimension / 24f
        val (lift, turn) = shackleAt(open)
        // The shackle: two legs and a round top, thick and silver with a light edge on the left.
        val shackle = Path().apply {
            moveTo(8f * u, 15f * u); lineTo(8f * u, 8f * u)
            arcTo(Rect(8f * u, 4f * u, 16f * u, 12f * u), 180f, 180f, false)
            lineTo(16f * u, 15f * u)
        }
        translate(top = -lift * u) {
            rotate(turn, Offset(8f * u, 15f * u)) {
                drawPath(shackle, Brush.horizontalGradient(SILVER, 6.5f * u, 17.5f * u), style = Stroke(2.8f * u, cap = StrokeCap.Round))
                drawPath(shackle, Color.White.copy(alpha = 0.45f), style = Stroke(0.7f * u, cap = StrokeCap.Round))
            }
        }
        // The body: a rounded block, light at the top and deeper at the bottom, with a rim and a shine.
        val top = Offset(4.5f * u, 10.5f * u)
        val body = Size(15f * u, 10.5f * u)
        val r = CornerRadius(2.6f * u)
        drawRoundRect(Brush.verticalGradient(if (grey) GREY else GOLD, top.y, top.y + body.height), top, body, r)
        drawRoundRect(if (grey) GREY_RIM else GOLD_RIM, top, body, r, style = Stroke(0.8f * u))
        drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(6.2f * u, 11.7f * u), Size(11.6f * u, 2f * u), CornerRadius(1f * u))
        // The keyhole.
        val hole = if (grey) GREY_HOLE else GOLD_HOLE
        drawCircle(hole, 1.5f * u, Offset(12f * u, 15.4f * u))
        drawRoundRect(hole, Offset(11.35f * u, 15.4f * u), Size(1.3f * u, 2.9f * u), CornerRadius(0.5f * u))
    }
}

/**
 * The padlock of "Unreserve?": closed, then the shackle lifts and swings open (about half a second). With animations
 * turned off on the phone it is shown open at once.
 */
@Composable
fun OpeningPadlock(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val t = remember { Animatable(if (animationsOff(ctx)) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(150)
        t.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
    }
    Padlock(t.value, modifier, label = tr("Padlock opening"))
}

/**
 * The padlock on "Reserved by you". Just after you tapped Reserve ([play]) it starts open and 1.6 times its size,
 * swings shut and drops in (about 0.4 s), then settles to the button's size with a small bounce, and calls [onDone].
 * Otherwise (and with animations off) it is simply closed.
 */
@Composable
fun ClosingPadlock(play: Boolean, modifier: Modifier = Modifier, grey: Boolean = false, onDone: () -> Unit = {}) {
    val ctx = LocalContext.current
    val go = play && !animationsOff(ctx)
    val t = remember { Animatable(if (go) 1f else 0f) }
    val grow = remember { Animatable(if (go) 1.6f else 1f) }
    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        if (go) {
            t.snapTo(1f); grow.snapTo(1.6f)
            t.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            grow.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
        onDone()
    }
    Padlock(t.value, modifier.graphicsLayer { scaleX = grow.value; scaleY = grow.value }, grey)
}
