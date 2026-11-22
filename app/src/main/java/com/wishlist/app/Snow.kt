package com.wishlist.app

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import kotlin.math.sin

/*
 * Snow on the Gift exchange item of the side menu (owner, 7 November 2026): very light, only inside that item, from
 * 1 November to 20 December (the dates of the "Secret Santa this year?" card on Home; it stays when that card is
 * clicked away). When the phone's animations are switched off (Settings > Accessibility > Remove animations), the
 * flakes stand still: festive, but nothing moves.
 */
object Snow {
    /** 1 November to 20 December. */
    fun season(today: LocalDate = LocalDate.now()): Boolean =
        today.monthValue == 11 || (today.monthValue == 12 && today.dayOfMonth <= 20)
}

private class Flake(val x: Float, val start: Float, val speed: Float, val r: Float, val sway: Float, val alpha: Float)

