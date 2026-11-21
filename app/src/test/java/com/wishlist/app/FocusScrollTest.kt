package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A wish opened from a notification or a chat is fully in sight before it glows, never under the pinned filters
 * (owner, 21 November 2026, a recording), in every view and on every list that opens on a wish.
 */
class FocusScrollTest : NavigationBase() {
    @Test fun howFarToScroll() {
        assertEquals(0, focusScrollBy(top = 100, bottom = 300, from = 80, to = 900))
        // Half under the pinned part (the recording): its top goes just under it.
        assertEquals(-60, focusScrollBy(top = 20, bottom = 220, from = 80, to = 900))
        // Below the screen: its bottom comes up to the edge.
        assertEquals(150, focusScrollBy(top = 850, bottom = 1050, from = 80, to = 900))
        // Taller than the space: its top first.
        assertEquals(-30, focusScrollBy(top = 50, bottom = 1500, from = 80, to = 900))
    }

    /** A pretend list (two per row, 180 dp cards) with a 64 dp part pinned on top, like the filters. */
    @Test fun theWishEndsUpWholeBelowThePinnedPart() {
        val state = LazyGridState()
        val target = androidx.compose.runtime.mutableIntStateOf(-1)
        compose.runOnUiThread { compose.activity.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.LaunchedEffect(target.intValue) {
                if (target.intValue >= 0) state.showWhole(target.intValue, { with(d) { 64.dp.roundToPx() } }, with(d) { 12.dp.roundToPx() })
            }
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(GridCells.Fixed(2), state = state, modifier = Modifier.fillMaxSize()) {
                    items(40) { i -> Box(Modifier.height(180.dp).testTag("w$i")) { Text("Wish $i") } }
                }
                Box(Modifier.fillMaxWidth().height(64.dp).testTag("pinned"))
            }
        } }
        compose.waitForIdle()
        fun check(i: Int) {
            compose.runOnUiThread { target.intValue = i }
            compose.waitForIdle()
            val b = compose.onNodeWithTag("w$i").fetchSemanticsNode().boundsInRoot
            val pin = compose.onNodeWithTag("pinned").fetchSemanticsNode().boundsInRoot
            assertTrue("wish $i starts under the pinned part (${b.top} < ${pin.bottom})", b.top >= pin.bottom)
            assertTrue("wish $i ends on screen", b.bottom <= compose.activity.window.decorView.height + 1f)
        }
        check(31)   // far below
        check(4)    // back up: a plain scroll would land it under the pinned part
        check(5)    // already in sight: stays
    }

    @Test fun everyListThatOpensOnAWishUsesIt() {
        val dir = File("src/main/java/com/wishlist/app")
        for (f in listOf("ListScreen.kt", "Friend.kt")) {
            val src = File(dir, f).readText()
            assertTrue("$f brings the wish whole into sight", src.contains("gridState.showWhole(i + pre,"))
            assertTrue("$f no longer scrolls the old way", !src.contains("animateScrollToItem(i + pre"))
        }
    }
}
