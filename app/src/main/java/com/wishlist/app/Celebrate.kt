package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Small celebrations (owner, 15 November 2026, docs/styles/identity.md): when you reserve a gift, and when a price drops
 * (that one is on the price page, [PriceCountDown]). Reserving gives one soft vibration (owner: vibration "only for
 * relevant and a select few moments") and its "Reserved by you" label arrives with a pulse, like the top wish label
 * ([rememberAppearPulse], owner: no confetti, 15 November 2026).
 */
object Celebrate {
    /** Counts the reservations that just succeeded; [CelebrationLayer] gives one vibration for each new one. */
    var reservations by mutableIntStateOf(0)
        private set

    /** A gift was reserved (the server said yes). */
    fun reserved() { reservations++ }
}

/** Over the whole app: the soft vibration when a reservation succeeded. */
@Composable
fun CelebrationLayer() {
    val n = Celebrate.reservations
    val haptic = LocalHapticFeedback.current
    // The first value is where the app started: nothing for that.
    val seen = remember { intArrayOf(n) }
    LaunchedEffect(n) {
        if (n == seen[0]) return@LaunchedEffect
        seen[0] = n
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
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
