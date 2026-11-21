package com.wishlist.app

import androidx.activity.compose.setContent
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * "Move or copy?" (two choice rows, the wish on top) and "Copy to…" (owner, 21 November 2026), in both styles, light
 * and dark, checked by the layout inspector. Pictures in app/build/outputs/roborazzi/movecopy_*.png; only with
 * -Pwl.screens.
 */
class MoveCopyScreenshotTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun bothSteps() {
        compose.runOnUiThread { Repo.add(app, Wish(501L, "https://example.com/j", "Mackinaw Wool G9 jacket in navy check", "", "", 470.0, null, "EUR", "")) }
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) for (step in listOf("choose", "pick")) {
            compose.runOnUiThread { compose.activity.setContent {
                LookTheme(lookFor(st, "blue", m)) {
                    if (step == "choose") MoveOrCopyDialog(setOf(501L), onChoose = {}, onClose = {})
                    else MoveToDialog(setOf(501L), copy = true, onPick = {}, onClose = {})
                }
            } }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/movecopy_${step}_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }
}
