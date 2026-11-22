package com.wishlist.app

import androidx.activity.compose.setContent
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * "Move or copy?" (two rows, the wish on top) and "Copy to…" (owner, 21 November 2026; one bottom sheet since 22
 * November 2026), in both styles, light
 * and dark, checked by the layout inspector. Pictures in app/build/outputs/roborazzi/movecopy_*.png; only with
 * -Pwl.screens.
 */
class MoveCopyScreenshotTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun bothSteps() {
        compose.runOnUiThread {
            Repo.add(app, Wish(501L, "https://example.com/j", "Mackinaw Wool G9 jacket in navy check", "", "", 470.0, null, "EUR", ""))
            // A second wishlist, so "Copy to…" shows rows (22 November 2026: both steps are one bottom sheet).
            Repo.createMine(app, "Birthday", "")
        }
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) for (step in listOf("choose", "pick")) {
            // The sheet before this one closed first (a sheet is its own window; it stayed in the next picture).
            compose.runOnUiThread { compose.activity.setContent {} }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            compose.runOnUiThread { compose.activity.setContent {
                LookTheme(lookFor(st, "blue", m)) {
                    MoveSheet(setOf(501L), picking = step == "pick", copy = true, onChoose = {}, onBack = {}, onPick = {}, onClose = {})
                }
            } }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/movecopy_${step}_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }
}
