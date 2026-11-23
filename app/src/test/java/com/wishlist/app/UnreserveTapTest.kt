package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Reserved by you" → one tap opens the "Unreserve?" pop-up (owner, 23 November 2026: like the other pop-ups, with a
 * padlock that opens as it appears): "No, keep it" keeps the reservation, "Yes, unreserve" ends it.
 */
class UnreserveTapTest : NavigationBase() {
    private val w = Wish(7, "", "La Vie est Belle", "", "", 79.95, null, "EUR", "")

    private fun show(calls: ArrayList<Boolean>, st: String = "quiet", m: String = "dark") {
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor(st, "blue", m)) { ReserveButton(w, "mine", "Reserved by you", { calls.add(it) }) }
        } }
        compose.waitForIdle()
    }

    @Test fun oneTapAsksYesOrNo() {
        val calls = ArrayList<Boolean>()
        show(calls)
        compose.onAllNodes(hasText("Reserved by you")).onFirst().performClick(); compose.waitForIdle()
        compose.onAllNodes(hasText("Unreserve?")).onFirst().assertExists()
        compose.onAllNodes(hasText("No, keep it")).onFirst().performClick(); compose.waitForIdle()
        assertEquals(emptyList<Boolean>(), calls)
        assertEquals(0, compose.onAllNodes(hasText("Unreserve?")).fetchSemanticsNodes().size)
        compose.onAllNodes(hasText("Reserved by you")).onFirst().performClick(); compose.waitForIdle()
        compose.onAllNodes(hasText("Yes, unreserve")).onFirst().performClick(); compose.waitForIdle()
        assertEquals(listOf(false), calls)
    }

    /** The pop-up in both styles, light and dark (build/outputs/roborazzi/unreserve_*.png; with -Pwl.screens). */
    @Test fun thePopUp() {
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) {
            show(ArrayList(), st, m)
            compose.onAllNodes(hasText("Reserved by you")).onFirst().performClick()
            compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
            val path = "build/outputs/roborazzi/unreserve_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
            compose.onAllNodes(hasText("No, keep it")).onFirst().performClick(); compose.waitForIdle()
        }
    }
}
