package com.wishlist.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The owner's screen recording of 26 November 2026 (a move jumped underneath) and the owner's choice of 27 November
 * 2026: "I don't like the animation of binning wishes. Remove it entirely." A move keeps its place until it reaches
 * Home; deleting has no flight, no bin and no animated Undo (the question is checked in DeleteQuestionTest).
 */
class ReviewNov26Test {
    private fun src(f: String) = File("src/main/java/com/wishlist/app", f).readText()

    @Test fun aMoveKeepsItsPlaceUntilHome() =
        assertTrue(src("CardMotion.kt").contains("peel = false, onLand = go)"))

    @Test fun deletingHasNoAnimation() {
        val m = src("CardMotion.kt") + src("CardFlightDraw.kt")
        for (gone in listOf("toBin", "drawBin", "drawDisc", "fun back(", "motionBin", "motionDelete")) assertFalse(gone, m.contains(gone))
        for (f in listOf("ListWishItem.kt", "CompactCard.kt", "ListScreen.kt", "DeleteUi.kt", "Selecting.kt"))
            assertFalse(f, Regex("""motion\??\.(fly|back)\(|motionBin|motionDelete""").containsMatchIn(src(f)))
    }
}
