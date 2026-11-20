package com.wishlist.app

import androidx.activity.compose.setContent
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * Pictures of the Add / Edit wish form (added for the rebuild, 19 November 2026, so the before/after comparison covers
 * more than the add and edit pictures of ScreenshotTest): editing a wish with details (More details open by itself), in
 * light and dark. (Tapping in the add form can't be done here: its blinking cursor never lets the test settle.) Pictures in
 * app/build/outputs/roborazzi/wishform_*.png; only with -Pwl.screens (the class name has "Screenshot").
 */
class WishFormScreenshotTest : NavigationBase() {
    private fun settle() = repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.guard("build/outputs/roborazzi/wishform_$name.png"); captureScreenRoboImage("build/outputs/roborazzi/wishform_$name.png") }

    private val detailed = Wish(
        77L, "https://example.com/blanket", "Cosy wool blanket", "In forest green, please", "", 49.95, null, "EUR", "Home",
        star = true, size = "L", qty = 2
    )

    private fun show(mode: String, edit: Wish?) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "berry", mode)) { WishDialog(edit, "") {} } } }
        settle()
    }

    @Test fun editDetailed() { show("light", detailed); shot("edit_details") }

    @Test fun editDetailedDark() { show("dark", detailed); shot("edit_details_dark") }

    // Screenshot to wish (20 November 2026): "Wish 2 of 5" with Skip and "Save and next"; Save without a name asks
    // for one, also with only a photo.
    @Test fun severalScreenshots() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("chaos", "berry", "light")) { WishDialog(null, "", step = 2 to 5, onSkip = {}, onCancelAsk = {}) {} }
        } }
        settle(); shot("steps_chaos")
        compose.onNodeWithText("Save and next").performClick()
        shot("steps_no_name")
    }

    @Test fun severalScreenshotsQuiet() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "berry", "light")) { WishDialog(null, "", step = 5 to 5, onSkip = {}, onCancelAsk = {}) {} }
        } }
        settle(); shot("steps_quiet_last")
    }
}
