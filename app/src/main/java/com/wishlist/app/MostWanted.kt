package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * "Love it" (stored as "star", so wishes marked before keep it): a heart on a soft, muted rose pink, quiet enough
 * to sit with the app's colours. Same in light and dark mode.
 */
/** The pale pink of the "Love it" label. */
val LovePink = Color(0xFFF6D9E1)
/** The deep rose of the heart and the words on the label. */
val LoveInk = Color(0xFF8C3A56)
/** A filled heart on the page itself (the form, the menu), between the two. */
val LoveRose = Color(0xFFD27391)

/** Google's Material "favorite" heart (Apache 2.0), on a 24 x 24 grid. */
private val HeartPath: Path by lazy {
    PathParser().parsePathString(
        "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09" +
            "C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
    ).toPath()
}

/** A heart: solid when [filled], an outline otherwise. */
@Composable
fun HeartIcon(filled: Boolean, tint: Color, iconSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            if (filled) drawPath(HeartPath, tint)
            else drawPath(HeartPath, tint, style = Stroke(width = 2f, join = StrokeJoin.Round))
        }
    }
}

/**
 * The little celebration when a wish gets "Love it": a quick pulse of the heart and a few tiny sparkles.
 * Call [play] when the person taps; use [starPulse] on the star and [starSparkles] on something around it.
 * A card that scrolls into view never plays it, only a tap does.
 */
@Stable
class StarFx {
    val scale = Animatable(1f)
    val burst = Animatable(0f)
    var plays by mutableIntStateOf(0)
        private set

    fun play() {
        plays = plays + 1
    }
}

@Composable
fun rememberStarFx(): StarFx {
    val fx = remember { StarFx() }
    val n = fx.plays
    LaunchedEffect(n) {
        if (n > 0) {
            launch {
                fx.burst.snapTo(0.001f)
                fx.burst.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
            }
            fx.scale.snapTo(1f)
            fx.scale.animateTo(1.25f, tween(110, easing = FastOutSlowInEasing))
            fx.scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    return fx
}

/** Makes the star swell a little and settle back. [origin] is the point it swells from. */
fun Modifier.starPulse(fx: StarFx, origin: TransformOrigin = TransformOrigin.Center): Modifier = graphicsLayer {
    scaleX = fx.scale.value
    scaleY = fx.scale.value
    transformOrigin = origin
}

/**
 * Draws the sparkles over whatever this is put on. They start from the middle, or from [centerFromEnd] away from the
 * right edge (where the finger just tapped on a wide label), and they do not change the layout.
 */
fun Modifier.starSparkles(fx: StarFx, centerFromEnd: Dp? = null): Modifier = drawWithContent {
    drawContent()
    val p = fx.burst.value
    if (p > 0f && p < 1f) {
        val c = if (centerFromEnd != null) Offset(size.width - centerFromEnd.toPx(), size.height / 2f)
        else Offset(size.width / 2f, size.height / 2f)
        drawSparkles(c, p)
    }
}

private val SparkleRose = LoveRose
private val SparkleBlush = Color(0xFFFCE8EE)
private val SparkleEdge = Color(0xFFA8486A)

/**
 * Seven tiny four-point sparkles flying outwards from [center] and fading. [p] runs from 0 to 1.
 * They have a thin darker edge so they show up on the pink button, on white pictures and on dark ones.
 */
private fun DrawScope.drawSparkles(center: Offset, p: Float) {
    val out = 1f - (1f - p) * (1f - p)
    val alpha = if (p < 0.55f) 1f else 1f - (p - 0.55f) / 0.45f
    val edge = Stroke(width = 1.dp.toPx() * 0.7f)
    val path = Path()
    for (i in 0 until 7) {
        val big = i % 2 == 0
        val a = (-90.0 + i * (360.0 / 7.0) + (if (big) 0.0 else 10.0)) * PI / 180.0
        val from = (if (big) 9.dp else 8.dp).toPx()
        val to = (if (big) 27.dp else 21.dp).toPx()
        val reach = from + (to - from) * out
        val r = (if (big) 4.dp else 3.dp).toPx() * (1f - 0.5f * p)
        val x = center.x + (reach * cos(a)).toFloat()
        val y = center.y + (reach * sin(a)).toFloat()
        path.reset()
        for (k in 0 until 8) {
            val t = k * PI / 4.0
            val rr = if (k % 2 == 0) r else r * 0.3f
            val px = x + (rr * cos(t)).toFloat()
            val py = y + (rr * sin(t)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, if (big) SparkleRose else SparkleBlush, alpha = alpha)
        drawPath(path, SparkleEdge, alpha = alpha, style = edge)
    }
}

/**
 * The pink "Love it" label for the corner of a picture. [showLabel] false gives just the heart (small cards).
 * With [onClick] the owner can tap it to take the heart away again.
 */
@Composable
fun MostWantedBadge(showLabel: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    var m = modifier.clip(shape).background(LovePink)
    m = if (onClick != null) {
        m.clickable(onClickLabel = tr("Remove the heart"), role = Role.Button, onClick = onClick)
    } else if (!showLabel) {
        m.semantics { contentDescription = tr("Love it") }
    } else m
    Row(
        m.padding(horizontal = if (showLabel) 8.dp else 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HeartIcon(true, LoveInk, 14.dp)
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text(tr("Love it"), style = MaterialTheme.typography.labelMedium, color = LoveInk)
        }
        // A small cross when you can tap the label to take it away again.
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = LoveInk)
        }
    }
}

/** A round outlined heart on the picture: tap it to mark the wish "Love it". */
@Composable
fun MarkMostWantedButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .semantics { contentDescription = tr("Love it") }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        HeartIcon(false, Color.White, 20.dp)
    }
}

/**
 * What sits in the top right corner of a large card for the heart. For the owner ([onToggle] set) it is the label
 * when the wish has "Love it", or an outlined heart to tap when it has not. Friends only ever see the label.
 * Wishes you already got show nothing. Marking a wish plays the pulse and sparkles.
 */
@Composable
fun StarCorner(w: Wish, onToggle: (() -> Unit)?) {
    if (!w.got) {
        val fx = rememberStarFx()
        val toggle: (() -> Unit)? = onToggle?.let { go -> { if (!w.star) fx.play(); go() } }
        // The sparkles start 17 dp from the right edge: that is where the round button was, so where the finger is.
        Box(Modifier.starSparkles(fx, 17.dp)) {
            if (w.star) {
                MostWantedBadge(
                    showLabel = true, modifier = Modifier.starPulse(fx, TransformOrigin(1f, 0.5f)), onClick = toggle
                )
            } else if (toggle != null) MarkMostWantedButton(toggle)
        }
    }
}

/**
 * The small round heart on a two-per-row card, with no text because there is no room: an outlined heart on a dark
 * circle without "Love it", a solid one on pink with it. Tapping switches it, with the pulse and the sparkles when it
 * gets "Love it".
 */
@Composable
fun StarToggle(starred: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val fx = rememberStarFx()
    Box(modifier.size(34.dp).starSparkles(fx), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().starPulse(fx).clip(CircleShape)
                .background(if (starred) LovePink else Color.Black.copy(alpha = 0.45f))
                .semantics { contentDescription = if (starred) tr("Remove the heart") else tr("Love it") }
                .clickable(role = Role.Button) { if (!starred) fx.play(); onToggle() },
            contentAlignment = Alignment.Center
        ) {
            HeartIcon(starred, if (starred) LoveInk else Color.White, 20.dp)
        }
    }
}
