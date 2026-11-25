package com.wishlist.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The owner's review of 25 November 2026 (phone screenshots): the price tag taken out again; cards side by side the
 * same height (the old price always on its own line, kept empty when there is none); photos on Received gifts; the
 * "Unreserve?" pop-up with the padlock at the top and the gift's photo instead of a long sentence; the open padlock's
 * right leg short. The web pages: tests/index-page.
 */
class ReviewNov25Test {
    private fun src(f: String) = File("src/main/java/com/wishlist/app", f).readText()

    @Test fun noPriceTag() {
        val all = File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.endsWith(".kt") }.joinToString("\n") { it.readText() }
        assertFalse(Regex("\\bfun PriceTag\\(|\\bRestingTag\\(").containsMatchIn(all))
    }

    @Test fun cardsSideBySideAreTheSameHeight() {
        assertTrue(src("CompactCard.kt").contains("stacked = true)"))
        val line = src("WishPriceLine.kt")
        // No old price: an empty line of the same height, no stray strike-through (since 2.292 the line is drawn by OldPrice).
        assertTrue(line.contains("if (was != null) OldPrice(w, was, Modifier) else Text(\" \""))
    }

    @Test fun receivedGiftsShowTheirPhoto() = assertTrue(src("Reminders.kt").contains("WishThumb(w)"))

    @Test fun unreserveShowsPadlockThenTheGift() {
        val d = src("WishCardParts.kt")
        val pad = d.indexOf("OpeningPadlock(Modifier.size(72.dp))")
        assertTrue(pad > 0 && pad < d.indexOf("tr(\"Unreserve?\")", pad) && d.contains("WishThumb(w, 64.dp)"))
        assertTrue(d.contains("tr(\"Someone else can reserve it again.\")") && !d.contains("becomes free again"))
    }

    @Test fun openShackleHasAShortRightLeg() {
        val p = src("Padlock.kt")
        assertTrue(p.contains("moveTo(8f * u, 14.5f * u)") && p.contains("lineTo(16f * u, 12f * u)"))
    }
}
