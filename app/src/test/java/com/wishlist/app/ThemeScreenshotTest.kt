package com.wishlist.app

import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * The Theme screen and Home in each theme (owner, 17 November 2026, plan step 5), to look at:
 * build/outputs/roborazzi/theme_*.png. Only with -Pwl.screens.
 */
class ThemeScreenshotTest : NavigationBase() {
    private fun settle() = repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    @Test fun themeScreenAndHomeInEachTheme() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.mainClock.autoAdvance = false
        for (m in listOf("dark", "light")) {
            compose.runOnUiThread { Repo.setLook(app, "quiet", m, "blue") }
            settle()
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Profile and settings")).onFirst().performClick()
            settle()
            captureScreenRoboImage("build/outputs/roborazzi/theme_sheet_$m.png")
            compose.onAllNodes(androidx.compose.ui.test.hasText("Theme")).onFirst().performClick()
            settle()
            captureScreenRoboImage("build/outputs/roborazzi/theme_screen_$m.png")
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.activity.onBackPressedDispatcher.onBackPressed(); settle()
            compose.activity.onBackPressedDispatcher.onBackPressed(); settle()
        }
        for (ac in ACCENTS.map { it.key }) {
            compose.runOnUiThread { Repo.setLook(app, "quiet", "dark", ac) }
            settle()
            captureScreenRoboImage("build/outputs/roborazzi/theme_home_${ac}_dark.png")
        }
    }
}
