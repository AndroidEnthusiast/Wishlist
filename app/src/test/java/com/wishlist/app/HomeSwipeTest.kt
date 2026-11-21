package com.wishlist.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's tabs (owner, 20 November 2026, a bug; Personal | Co-op since 21 November 2026): a swipe anywhere under the divider switches sides,
 * also on the empty space under the shorter side, down to the bottom of the screen, and back again from there.
 */
class HomeSwipeTest : NavigationBase() {
    // Since 21 November 2026 the tabs only show with a second kind of list: a co-op wishlist makes Personal | Co-op.
    private val coop = "Gifts for Mum"

    private fun coopShows(): Boolean {
        compose.waitForIdle()
        return compose.onAllNodes(hasText(coop)).fetchSemanticsNodes().any { n ->
            val b = n.boundsInRoot; b.left >= 0f && b.right <= compose.onRoot().fetchSemanticsNode().size.width.toFloat() + 1f
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onRoot() =
        onAllNodes(androidx.compose.ui.test.isRoot()).onFirst()

    @Test fun swipingTheEmptySpaceSwitchesBothWays() {
        compose.runOnUiThread { Shared.lists = listOf(SharedList("COOP1", "coopkey_coopkey_coopkey", coop)) }
        compose.waitForIdle()
        val below = compose.onNodeWithTag("home_below")
        // Near the bottom of the area, far below the one list: empty space on both sides.
        below.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(right - 20f, bottom - 40f), androidx.compose.ui.geometry.Offset(left + 20f, bottom - 40f), 300) }
        assertTrue("swiped to Co-op from the empty space", coopShows())
        below.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(left + 20f, bottom - 40f), androidx.compose.ui.geometry.Offset(right - 20f, bottom - 40f), 300) }
        assertTrue("and back to Personal from the same place", !coopShows())
        compose.runOnUiThread { Shared.lists = emptyList() }
    }

    @Test fun whereASwipeEnds() {
        assertEquals(1, swipeTarget(0, 0.3f, 0f, 2))
        assertEquals(0, swipeTarget(0, 0.05f, 0f, 2))
        assertEquals(1, swipeTarget(0, 0.05f, -1200f, 2))
        assertEquals(0, swipeTarget(1, 0.7f, 0f, 2))
        assertEquals(1, swipeTarget(1, 1.05f, 0f, 2))
        assertEquals("never past the last side", 1, swipeTarget(1, 1.4f, -2000f, 2))
    }
}
