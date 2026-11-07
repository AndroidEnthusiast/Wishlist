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

/** Draws slowly falling flakes over this element (behind nothing: they sit lightly over its content). */
fun Modifier.snowfall(dark: Boolean): Modifier = composed {
    val ctx = LocalContext.current
    val still = remember {
        try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }
    }
    val flakes = remember {
        val rnd = kotlin.random.Random(2026)
        List(14) { Flake(rnd.nextFloat(), rnd.nextFloat(), if (rnd.nextBoolean()) 1f else 2f, 1.1f + rnd.nextFloat() * 1.1f, rnd.nextFloat() * 6.28f, 0.35f + rnd.nextFloat() * 0.35f) }
    }
    val t = if (still) 0f else {
        val tr = rememberInfiniteTransition(label = "snow")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "fall")
        v
    }
    val col = if (dark) Color.White else Color(0xFF8FA6C8)
    drawWithContent {
        drawContent()
        val dp = density
        flakes.forEach { f ->
            // Each flake falls through the item and comes in again at the top; a soft sideways sway.
            val y = ((f.start + t * f.speed) % 1f) * (size.height + 8 * dp) - 4 * dp
            val x = f.x * size.width + sin(t * 6.28f * 2 + f.sway) * 3 * dp
            drawCircle(col.copy(alpha = f.alpha * if (dark) 0.6f else 0.8f), f.r * dp, Offset(x, y))
        }
    }
}
