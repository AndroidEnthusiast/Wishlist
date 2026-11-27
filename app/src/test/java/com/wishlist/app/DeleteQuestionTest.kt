package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Delete wish?" (owner, 27 November 2026, chosen from a preview): the wish's photo on top, the title, the wish's
 * name, a tickbox "Turn on one-tap delete" that only counts with Delete, a red Delete and Cancel. One-tap delete
 * (App settings) skips the question; the first delete that turned it on says where to switch it off.
 */
class DeleteQuestionTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private val teapot = Wish(1_700_000_000_001, "https://example.com/t", "Blue teapot", "", "", 30.0, null, "EUR", "")
    private val mug = Wish(1_700_000_000_002, "https://example.com/m", "Mug", "", "", 8.0, null, "EUR", "")
    private val pan = Wish(1_700_000_000_003, "https://example.com/p", "Pan", "", "", 25.0, null, "EUR", "")

    private fun show(ask: DeleteFlow.Ask, design: String, mode: String, onDelete: (Boolean) -> Unit = {}, onCancel: () -> Unit = {}) =
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor(design, "blue", mode)) { DeleteQuestion(ask, onDelete, onCancel) } } }

    @Test fun oneWishShowsItsNameTheTickboxAndDelete() {
        var ticked: Boolean? = null
        show(DeleteFlow.Ask(listOf(teapot)) {}, "quiet", "dark", onDelete = { ticked = it })
        compose.onNodeWithText("Delete wish?").assertExists()
        compose.onNodeWithText("Blue teapot").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/delete_question_quiet_dark.png")
        compose.onNodeWithText("Turn on one-tap delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals("the tickbox goes along with Delete", true, ticked)
    }

    @Test fun severalWishesShowTheCountAndTheFirstNames() {
        show(DeleteFlow.Ask(listOf(teapot, mug, pan)) {}, "chaos", "light")
        compose.onNodeWithText("Delete 3 wishes?").assertExists()
        compose.onNodeWithText("Blue teapot, Mug and 1 more").assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/delete_question_chaos_light.png")
        assertEquals("Blue teapot and Mug", deleteNames(listOf(teapot, mug)))
    }

    @Test fun theTickboxOnlyCountsWithDelete() {
        DeleteFlow.setOneTap(app, false)
        DeleteFlow.request(app, teapot)
        assertNotNull("asked first", DeleteFlow.asking)
        DeleteFlow.cancel()
        assertFalse("Cancel changes nothing", DeleteFlow.oneTap(app))

        DeleteFlow.request(app, teapot)
        DeleteFlow.confirm(app, oneTapFromNowOn = true)
        assertTrue("Delete with the tick turns it on", DeleteFlow.oneTap(app))
        assertTrue("its message says where to switch it off", DeleteFlow.justTurnedOn)
        DeleteFlow.toldOneTap()

        DeleteFlow.request(app, mug)
        assertNull("one-tap: no question", DeleteFlow.asking)
        assertFalse("told once only", DeleteFlow.justTurnedOn)
        DeleteFlow.setOneTap(app, false)
    }

    @Test fun cancellingKeepsTheSelection() {
        DeleteFlow.setOneTap(app, false)
        var ended = false
        DeleteFlow.requestMany(app, listOf(teapot, mug)) { ended = true }
        DeleteFlow.cancel()
        assertFalse(ended)
        DeleteFlow.requestMany(app, listOf(teapot, mug)) { ended = true }
        DeleteFlow.confirm(app)
        assertTrue(ended)
        assertFalse("off unless ticked", DeleteFlow.oneTap(app))
    }

    @Test fun theSwitchIsInAppSettingsAndOffAtFirst() {
        val s = java.io.File("src/main/java/com/wishlist/app/Settings.kt").readText()
        assertTrue(s.contains("tr(\"One-tap delete\")"))
        assertFalse(Show.on(app, DeleteFlow.ONE_TAP))
    }

}
