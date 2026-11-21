package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.material3.SheetValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Form sheets (New wishlist, New group, New gift exchange, Import, Screenshot to wish, Wishlist settings, your profile)
 * close only with the X or the pull tab; a swipe anywhere else or a tap outside never closes them; Back asks first
 * when something is typed (owner, 21 November 2026: "swiping down anywhere closes it and all my details are gone").
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class FormSheetCloseTest : NavigationBase() {
    @Test fun onlyItsOwnWaysOutMayHideIt() {
        assertFalse(formSheetMayHide(SheetValue.Hidden, allowed = false))
        assertTrue(formSheetMayHide(SheetValue.Hidden, allowed = true))
        assertTrue(formSheetMayHide(SheetValue.Expanded, allowed = false))
        assertTrue(backAsks(true)); assertFalse(backAsks(false))
    }

    @Test fun swipingDownOnTheFormKeepsItOpen() {
        var closed = 0
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "blue", "dark")) { NewWishlistSheet(withOthers = false, onCreatedMine = {}, onCreatedShared = {}, onClose = { closed++ }) }
        } }
        repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        for (t in listOf("Description", "Privacy")) {
            compose.onAllNodes(hasText(t)).onFirst().performTouchInput { swipeDown(startY = centerY, endY = centerY + 900f) }
            repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        }
        assertEquals(0, closed)
        compose.onAllNodes(hasText("New wishlist")).onFirst().assertExists()
    }

    @Test fun everyFormThatCanBeTypedInSaysSo() {
        val dir = File("src/main/java/com/wishlist/app")
        for (f in listOf("NewWishlist.kt", "SharedUi.kt")) {
            assertTrue("$f: Back must know when something is typed", File(dir, f).readText().contains("unsaved = "))
        }
    }
}
