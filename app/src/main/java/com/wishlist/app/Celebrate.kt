package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * the new one when the page opens (the change badge is next to it). Bold, like a banking app's numbers (Revolut's
 * trait, borrowed). No vibration. Null when there is nothing to count (no drop, or "Remove animations": the new price
 * at once).
 */
@Composable
fun rememberCountDown(w: Wish): Double? {
    val old = w.oldPrice ?: return null
    val now = w.price ?: return null
    if (now >= old || animationsOff(LocalContext.current)) return null
    val shown = remember(w.id, now) { Animatable(old.toFloat()) }
    LaunchedEffect(w.id, now) {
        shown.animateTo(now.toFloat(), tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return if (shown.value <= now.toFloat()) now else shown.value.toDouble()
}
