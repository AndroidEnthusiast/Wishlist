package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The golden padlock (owner, 24 November 2026): it lifts first and then swings open (and back the other way when it
 * closes); "Reserved by you" plays the closing only just after you tapped Reserve; someone else's is grey; with
 * Remove animations on, nothing moves. The web pages draw the same (tests/index-page).
 */
class PadlockTest {
    private val src = File("src/main/java/com/wishlist/app")

    @Test fun liftsThenSwings() {
        assertEquals(0f, shackleAt(0f).first, 0.01f)
        assertEquals(0f, shackleAt(0f).second, 0.01f)
        // Lifted all the way by half-way; it only starts turning after 40 %.
        assertEquals(3f, shackleAt(0.5f).first, 0.01f)
        assertEquals(0f, shackleAt(0.4f).second, 0.01f)
        assertEquals(3f to -30f, shackleAt(1f))
        assertEquals(3f to -30f, shackleAt(2f))
    }

    @Test fun reserveButtonPlaysItOnce() {
        val parts = File(src, "WishCardParts.kt").readText()
        assertTrue(parts.contains("\"reserve\" -> Button(onClick = { justReserved = true; set(true) }"))
        assertTrue(parts.contains("play = !grey && justReserved, grey = grey, onDone = { justReserved = false }"))
        assertTrue(parts.contains("lock(false); FitWords(text)") && parts.contains("lock(true); FitWords(text)"))
    }

    @Test fun calmWithAnimationsOff() {
        val p = File(src, "Padlock.kt").readText()
        assertTrue(p.contains("val go = play && !animationsOff(ctx)"))
        assertTrue(p.contains("Animatable(if (animationsOff(ctx)) 1f else 0f)"))
    }
}
