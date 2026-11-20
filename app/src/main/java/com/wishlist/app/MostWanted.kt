package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The gold of the "Love this" label, and the dark brown text on it. Same in light and dark mode. */
val StarGold = Color(0xFFFFC83D)
val StarInk = Color(0xFF3B2A00)

/**
 * The heart of "Love this" (owner, 15 November 2026: it replaces the star and "Top wish", nobody says that): our own
 * rounded-line heart, filled when [filled]. Gold on its label, never red or pink, so it doesn't look like Valentine's Day.
 */
@Composable
fun LoveIcon(filled: Boolean, tint: Color, iconSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        if (filled) QuietIcons.heartFilled else QuietIcons.heart, null, tint = tint, modifier = modifier.size(iconSize)
    )
}

/**
 * The little celebration when a wish becomes "most wanted": a quick pulse of the star and a few tiny sparkles.
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

private val SparkleGold = StarGold
private val SparkleCream = Color(0xFFFFF1B8)
private val SparkleEdge = Color(0xFFC77A00)

/**
 * Seven tiny four-point sparkles flying outwards from [center] and fading. [p] runs from 0 to 1.
 * They have a thin darker edge so they show up on the gold button, on white pictures and on dark ones.
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
        drawPath(path, if (big) SparkleGold else SparkleCream, alpha = alpha)
        drawPath(path, SparkleEdge, alpha = alpha, style = edge)
    }
}

/**
 * Whose wishes these are, for the words on the gold label (owner, 16 November 2026): null on your own lists ("Love it"),
 * a name on a friend's list ("Jorai loves it"), "" when the name isn't known ("Loves it"). Small cards always say
 * "Loves it" for givers, so the label stays small.
 */
val LocalLover = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** The words on the gold label for [lover] (see [LocalLover]). Only the first name, so it stays short. */
fun loveWords(lover: String?, small: Boolean): String {
    val first = lover?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.take(20).orEmpty()
    return when {
        lover == null -> tr("Love it")
        small || first.isEmpty() -> tr("Loves it")
        else -> tr("%s loves it", first)
    }
}

/**
 * The gold "Love it" label for the corner of a picture. [showLabel] false gives just the heart.
 * With [onClick] the owner can tap it to take the heart away again. [small]: two per row and rows, a compact label
 * (owner, 16 November 2026: it almost touched the product), 11 sp words and a 10 dp heart.
 */
@Composable
fun MostWantedBadge(showLabel: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, small: Boolean = false) {
    val shape = RoundedCornerShape(if (small) 6.dp else 8.dp)
    val words = loveWords(LocalLover.current, small)
    // The same height as the price label beside it (owner, 20 November 2026: they didn't line up).
    var m = modifier.heightIn(min = badgeHeight(small)).clip(shape).background(StarGold)
    m = if (onClick != null) {
        m.clickable(onClickLabel = tr("Remove from wishes you love"), role = Role.Button, onClick = onClick)
    } else if (!showLabel) {
        m.semantics { contentDescription = words }
    } else m
    Row(
        m.padding(horizontal = if (showLabel) (if (small) 5.dp else 8.dp) else 5.dp, vertical = if (small) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        LoveIcon(true, StarInk, if (small) 10.dp else 14.dp)
        if (showLabel) {
            Spacer(Modifier.width(if (small) 3.dp else 4.dp))
            Text(
                words, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = StarInk, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        // A small cross when you can tap the label to take it away again.
        if (onClick != null) {
            Spacer(Modifier.width(if (small) 4.dp else 6.dp))
            Icon(AppIcons.Close, null, Modifier.size(if (small) 10.dp else 14.dp), tint = StarInk)
        }
    }
}

/** A round outlined star on the picture: tap it to mark the wish as most wanted. */
@Composable
fun MarkMostWantedButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val quiet = LocalLook.current.design == "quiet"
    Box(
        modifier.size(size).then(if (quiet) Modifier.shadow(2.dp, CircleShape) else Modifier).clip(CircleShape)
            .background(if (quiet) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.45f))
            .semantics { contentDescription = tr("Love this wish") }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LoveIcon(false, if (quiet) Color(0xFF6C6C74) else Color.White, size * 0.59f)
    }
}

/**
 * What sits in the top right corner of a large card for the star. For the owner ([onToggle] set) it is the label
 * when the wish is most wanted, or an outlined star to tap when it is not. Friends only ever see the label.
 * Wishes you already got show nothing. Marking a wish plays the pulse and sparkles.
 */
@Composable
fun StarCorner(w: Wish, onToggle: (() -> Unit)?, small: Boolean = false, buttonSize: Dp = 34.dp, compact: Boolean = false) {
    if (!w.got) {
        val fx = rememberStarFx()
        val toggle: (() -> Unit)? = onToggle?.let { go -> { if (!w.star) fx.play(); go() } }
        // The sparkles start 17 dp from the right edge: that is where the round button was, so where the finger is.
        Box(Modifier.starSparkles(fx, buttonSize / 2)) {
            if (w.star) {
                // [compact]: no room next to the price label (CardLabels.kt): the heart alone.
                MostWantedBadge(
                    showLabel = !compact, modifier = Modifier.starPulse(fx, TransformOrigin(1f, 0.5f)), onClick = if (compact) null else toggle, small = small
                )
            } else if (toggle != null) MarkMostWantedButton(toggle, size = buttonSize)
        }
    }
}

/**
 * The small round star on a two-per-row card, with no text because there is no room: an outlined star on a dark
 * circle when the wish is not most wanted, a solid one on gold when it is. Tapping switches it, with the pulse and
 * the sparkles when it becomes most wanted.
 */
@Composable
fun StarToggle(starred: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val fx = rememberStarFx()
    // Quiet (2.207): a small white disc on the light photo surface instead of a dark one; a top wish stays gold.
    val quiet = LocalLook.current.design == "quiet"
    Box(modifier.size(size).starSparkles(fx), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().starPulse(fx).then(if (quiet) Modifier.padding(2.dp).shadow(2.dp, CircleShape) else Modifier).clip(CircleShape)
                .background(if (starred) StarGold else if (quiet) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.45f))
                .semantics { contentDescription = if (starred) tr("Remove from wishes you love") else tr("Love this wish") }
                .clickable(role = Role.Button) { if (!starred) fx.play(); onToggle() },
            contentAlignment = Alignment.Center
        ) {
            LoveIcon(starred, if (starred) StarInk else if (quiet) Color(0xFF6C6C74) else Color.White, size * 0.59f)
        }
    }
}

/**
 * A pulse when [on] turns true while you look (not when the card first shows): a label that just arrived, such as
 * "Top wish" on a row or "Reserved by you" (owner, 15 November 2026: "a little pulse effect like the top wish one").
 * Use it with [starPulse].
 */
@Composable
fun rememberAppearPulse(on: Boolean): StarFx {
    val fx = rememberStarFx()
    val was = remember { androidx.compose.runtime.mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !was.value) fx.play()
        was.value = on
    }
    return fx
}

/** Whether a status label says that you reserved the wish ("Reserved by you", "You reserved 2"). */
fun reservedByMe(label: String?): Boolean =
    label != null && (label == tr("Reserved by you") || label.startsWith(tr("You reserved %d", 0).substringBefore("0")))

