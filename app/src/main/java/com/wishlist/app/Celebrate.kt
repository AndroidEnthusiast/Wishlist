package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small celebrations (owner, 15 November 2026, docs/styles/identity.md: Duolingo's trait, borrowed lightly): only when
 * you reserve a gift, and when a price drops (that one is on the price page, [PriceCountDown]). Reserving gives one
 * soft vibration (owner: vibration "only for relevant and a select few moments") and a short burst of confetti in Berry,
 * your colour and gold. "Remove animations" on the phone: the vibration only.
 */
object Celebrate {
    /** Counts the reservations that just succeeded; [CelebrationLayer] plays one burst for each new one. */
    var reservations by mutableIntStateOf(0)
        private set

    /** A gift was reserved (the server said yes). */
    fun reserved() { reservations++ }
}

/** The layer over the whole app that plays the burst. */
@Composable
fun CelebrationLayer() {
    val n = Celebrate.reservations
    val haptic = LocalHapticFeedback.current
    val off = animationsOff(LocalContext.current)
    val look = LocalLook.current
    val t = remember { Animatable(1f) }
    // The first value is where the app started: no burst for that.
    val seen = remember { intArrayOf(n) }
    LaunchedEffect(n) {
        if (n == seen[0]) return@LaunchedEffect
        seen[0] = n
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (off) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(1100, easing = LinearEasing))
    }
    if (t.value >= 1f) return
    val colours = listOf(Color(0xFFB0245A), look.accent, Color(0xFFF2B33D), Color(0xFFF0679A))
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val origin = Offset(size.width / 2, size.height * 0.62f)
        for (i in 0 until 22) {
            // Each piece flies out on its own angle and speed, slows, falls a little and fades.
            val angle = (-160f + i * (140f / 21f) + (i * 37 % 11) - 5f) * (Math.PI / 180).toFloat()
            val speed = size.minDimension * (0.42f + (i * 53 % 17) / 60f)
            val ease = 1 - (1 - p) * (1 - p)
            val x = origin.x + cos(angle) * speed * ease
            val y = origin.y + sin(angle) * speed * ease + size.height * 0.18f * p * p
            val alpha = (1f - p).coerceIn(0f, 1f)
            val s = 7f + (i % 3) * 3f
            rotate(p * 540f * (if (i % 2 == 0) 1 else -1), Offset(x, y)) {
                drawRoundRect(
                    colours[i % colours.size].copy(alpha = alpha), Offset(x - s, y - s / 2), Size(2 * s, s),
                    androidx.compose.ui.geometry.CornerRadius(s / 2.5f)
                )
            }
        }
    }
}

/**
 * A price drop, celebrated (owner, 15 November 2026): on a wish's price page the price counts down from the old price to
 * the new one, then the saving pops in. Bold, like a banking app's numbers (Revolut's trait, borrowed). No vibration.
 * "Remove animations": the new price and the saving at once.
 */
@Composable
fun PriceCountDown(w: Wish) {
    val old = w.oldPrice ?: return
    val now = w.price ?: return
    if (now >= old) return
    val look = LocalLook.current
    val off = animationsOff(LocalContext.current)
    val shown = remember(w.id, now) { Animatable(if (off) now.toFloat() else old.toFloat()) }
    val pop = remember(w.id, now) { Animatable(if (off) 1f else 0f) }
    LaunchedEffect(w.id, now) {
        if (off) return@LaunchedEffect
        shown.animateTo(now.toFloat(), tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text(
            money(shown.value.toDouble(), w.currency),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                fontFamily = look.display, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = look.scheme.onSurface
        )
        val pct = Math.round((1 - now / old) * 100).coerceAtLeast(1)
        androidx.compose.material3.Text(
            "−$pct%", style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = look.drop,
            modifier = Modifier.padding(start = 10.dp).scale(pop.value)
                .clip(Squircle(8.dp)).background(look.drop.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
