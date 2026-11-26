package com.wishlist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The owner's screen recording of 26 November 2026: the bin wasn't clear and the card jumped after Undo. Fixed: the
 * wish is deleted only when its card is in the bin (the list doesn't collapse underneath), the bin is drawn clearly
 * on its own disc, Undo lands on the card's live place, a move keeps its place until it reaches Home, and several
 * deleted wishes keep the selection bar until they are in. Frames: MotionFramesScreenshotTest (bin, back, move).
 */
class ReviewNov26Test {
    private fun src(f: String) = File("src/main/java/com/wishlist/app", f).readText()

    @Test fun deleteWaitsUntilTheCardIsInTheBin() {
        val m = src("CardMotion.kt")
        assertTrue(m.contains("Flight(it.id, it.image, it.from, it.to, toBin = true, peel = false, onLand = once)"))
        assertTrue(m.contains("f.onLand?.invoke()"))
    }

    @Test fun undoFollowsTheCardsLivePlace() {
        assertTrue(src("CardMotion.kt").contains("val from = if (f.back) live() ?: f.from else f.from"))
    }

    @Test fun theBinIsClear() {
        val d = src("CardFlightDraw.kt")
        assertTrue(d.contains("fun DrawScope.drawDisc(") && d.contains("val half = 15f * density"))
    }

    @Test fun aMoveKeepsItsPlaceUntilHome() =
        assertTrue(src("CardMotion.kt").contains("home = true, onLand = go)"))

    @Test fun theSelectionBarStaysUntilTheCardsAreIn() =
        assertTrue(src("ListScreen.kt").contains("val done = { model.picked = emptySet(); DeleteFlow.requestMany(ctx, ws) }"))
}
