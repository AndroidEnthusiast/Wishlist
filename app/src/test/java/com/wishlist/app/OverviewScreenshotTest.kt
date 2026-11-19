package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of the pages behind Home's numbers (added for the rebuild, 19 November 2026): All your wishes (light,
 * dark) and Wishes you love, with pretend wishes (one on two lists, one loved, one without a price), and the empty
 * Wishes you love. Pictures in app/build/outputs/roborazzi/overview_*.png; only with -Pwl.screens.
 */
class OverviewScreenshotTest : NavigationBase() {
    private fun settle() = repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); captureScreenRoboImage("build/outputs/roborazzi/overview_$name.png") }
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun wishes() = compose.runOnUiThread {
        Repo.add(app, Wish(101L, "https://example.com/a", "Cosy wool blanket", "", "", 49.95, null, "EUR", "", star = true))
        Repo.add(app, Wish(102L, "", "Board game night", "", "", 24.5, null, "EUR", ""))
        Repo.add(app, Wish(103L, "", "Something handmade", "", "", null, null, "EUR", ""))
        val other = Repo.createMine(app, "Birthday", "")
        Repo.copyInto(app, other, Repo.myWishes.filter { it.id == 101L })
    }

    private fun show(page: String, mode: String = "light") {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "berry", mode)) {
                CompositionLocalProvider(LocalPlaces provides Places()) {
                    OverviewScreen(page, SnackbarHostState(), lists = {}, onOpenMine = {})
                }
            }
        } }
        settle()
    }

    @Test fun all() { wishes(); show("wishes"); shot("all") }
    @Test fun allDark() { wishes(); show("wishes", "dark"); shot("all_dark") }
    @Test fun loved() { wishes(); show("top"); shot("loved") }
    @Test fun lovedEmpty() { show("top"); shot("loved_empty") }
}
