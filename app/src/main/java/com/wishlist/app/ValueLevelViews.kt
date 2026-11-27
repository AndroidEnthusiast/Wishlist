package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * How a value level looks (ValueLevels.kt; owner, 27 November 2026, chosen from three previews): a label with the
 * level's name on a gradient (gradients, so a level never looks like a flat green or red price change), its drawn
 * icon on top in the middle (a rising sun, a star, a lightning bolt; Iconic: a bigger crown and a holographic
 * shimmer), and a thin line under the amount in the level's colours whose light glides along it three times when the
 * screen opens. Gen Z chaos gives the label and the icon its outline and hard shadow. Nothing moves with Remove
 * animations.
 */

private val HOLO = listOf(Color(0xFFFF7AC6), Color(0xFFFFD36E), Color(0xFF7CF0C8), Color(0xFF7AA8FF), Color(0xFFC77DFF))

/** The level's gradient (deep enough at the start for its text to read). */
internal fun ValueLevel.colours(): List<Color> = when (this) {
    ValueLevel.RISING -> listOf(Color(0xFF1F7FE0), Color(0xFF4AAEF5))
    ValueLevel.MAIN -> listOf(Color(0xFFF7B733), Color(0xFFFFD86B))
    ValueLevel.ENERGY -> listOf(Color(0xFF6A3CF0), Color(0xFF9C74FF))
    ValueLevel.ICONIC -> HOLO
}

/** The text on the label. */
private fun ValueLevel.ink(): Color = when (this) {
    ValueLevel.RISING, ValueLevel.ENERGY -> Color.White
    ValueLevel.MAIN -> Color(0xFF4A3300)
    ValueLevel.ICONIC -> Color(0xFF1B1F2A)
}

/** A slow holographic drift for Iconic (0..1), still with Remove animations. */
@Composable
private fun holoDrift(level: ValueLevel): Float {
    if (level != ValueLevel.ICONIC || animationsOff(LocalContext.current)) return 0f
    val t = rememberInfiniteTransition(label = "holo")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "drift").value
}

private fun DrawScope.levelBrush(level: ValueLevel, drift: Float): Brush {
    val c = level.colours()
    if (level != ValueLevel.ICONIC) return Brush.horizontalGradient(c)
    val w = size.width
    return Brush.horizontalGradient(c + c.first(), startX = -w * drift, endX = w * (1f - drift), tileMode = TileMode.Repeated)
}

/** The label: the level's name on its gradient, its icon on top in the middle. [small]: in the Home summary. */
@Composable
fun ValueLevelTag(level: ValueLevel, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val chaos = look.design == "chaos"
    val drift = holoDrift(level)
    val disc = if (level == ValueLevel.ICONIC) (if (small) 24.dp else 28.dp) else (if (small) 17.dp else 19.dp)
    // The icon only touches the label's top edge, never its words.
    val below = disc - 4.dp
    Box(modifier.semantics { contentDescription = tr(level.label) }, contentAlignment = Alignment.TopCenter) {
        val pill = Modifier.padding(top = below)
            .then(if (chaos) Modifier.hardShadow(look.shadows[0], CircleShape, 2.dp) else Modifier)
            .clip(CircleShape).drawBehind { drawRect(levelBrush(level, drift)) }
            .then(if (chaos) Modifier.edgeLine(maxOf(look.edge, 1.5.dp), look.line, CircleShape) else Modifier)
            .padding(start = if (small) 7.dp else 9.dp, end = if (small) 7.dp else 9.dp, top = if (chaos) 4.dp else 3.dp, bottom = if (chaos) 3.dp else 1.5.dp)
        Box(pill) {
            FitText(
                tr(level.label), TextStyle(fontSize = if (small) 10.sp else 11.5.sp, fontWeight = FontWeight.ExtraBold), level.ink(),
                minScale = 0.75f
            )
        }
        Box(
            Modifier.size(disc).rotate(if (level == ValueLevel.ICONIC) -6f else 0f)
                .then(if (chaos) Modifier.hardShadow(look.shadows[0], CircleShape, 1.5.dp) else Modifier.shadow(2.dp, CircleShape))
                .clip(CircleShape).background(look.scheme.surface)
                .then(if (chaos) Modifier.edgeLine(maxOf(look.edge, 1.5.dp), look.line, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) { LevelIcon(level, disc * 0.68f) }
    }
}

/** The level's own icon, drawn here (docs: icons only from Material or our own code). */
@Composable
internal fun LevelIcon(level: ValueLevel, size: Dp) {
    val path = remember(level) { PathParser().parsePathString(ICONS.getValue(level)).toPath() }
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val p = Path().apply { addPath(path); transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) }) }
        when (level) {
            ValueLevel.RISING -> drawPath(p, Color(0xFF2A8BE8), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            ValueLevel.MAIN -> drawPath(p, Color(0xFFF2A91F))
            ValueLevel.ENERGY -> drawPath(p, Color(0xFF7B4DFF))
            ValueLevel.ICONIC -> drawPath(p, Brush.horizontalGradient(HOLO))
        }
    }
}

/** In a 24 × 24 box: a rising sun, a star, a lightning bolt, a crown. */
private val ICONS = mapOf(
    ValueLevel.RISING to "M4 17h16M7 17a5 5 0 0 1 10 0M12 7V4M5.6 10.6 3.8 8.8M18.4 10.6l1.8-1.8",
    ValueLevel.MAIN to "M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7z",
    ValueLevel.ENERGY to "M13.5 2 5 13.5h6L10 22l9-12h-6z",
    ValueLevel.ICONIC to "M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5z"
)

/**
 * The thin line under the amount, in the level's colours: its light glides along it three times when the screen
 * opens, then it rests (owner: calm, kind to the battery). [widest]: never wider than this (the amount's width).
 */
fun Modifier.levelLine(level: ValueLevel?, widest: Dp): Modifier = if (level == null) this else composed {
    val off = animationsOff(LocalContext.current)
    val t = remember(level) { Animatable(if (off) 3f else 0f) }
    LaunchedEffect(level) { if (!off) t.animateTo(3f, tween(9600, easing = LinearEasing)) }
    val drift = holoDrift(level)
    padding(bottom = 5.dp).drawBehind {
        val w = minOf(size.width * 0.84f, widest.toPx())
        val x0 = (size.width - w) / 2f
        val y = size.height - 1.dp.toPx()
        // Each pass: the light enters on the left and leaves on the right; at rest it sits in the middle.
        val pass = t.value
        val p = if (pass >= 3f) 0.5f else FastOutSlowInEasing.transform(pass - pass.toInt())
        val brush = if (level == ValueLevel.ICONIC) {
            Brush.horizontalGradient(HOLO + HOLO.first(), startX = x0 - w * drift, endX = x0 + w * (1f - drift), tileMode = TileMode.Repeated)
        } else {
            val c = level.colours()
            val mid = x0 - w + 2f * w * p
            Brush.horizontalGradient(listOf(Color.Transparent, c[0], c[1], Color.Transparent), startX = mid, endX = mid + w)
        }
        drawLine(brush, Offset(x0, y), Offset(x0 + w, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}
