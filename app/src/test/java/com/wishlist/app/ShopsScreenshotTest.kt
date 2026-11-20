package com.wishlist.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of My shops (added for the rebuild, 19 November 2026, so the before/after comparison covers it): the shops,
 * removing (✕ on every tile), Add shop with a shop you already have, a shop open inside the app and Search in browser.
 * Shop icons come from the internet: here none are asked for, so the tiles show their letter. Pictures in
 * app/build/outputs/roborazzi/shops_*.png; only with -Pwl.screens (the class name has "Screenshot").
 */
class ShopsScreenshotTest : NavigationBase() {
    private fun settle() = repeat(10) { Thread.sleep(30); compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.guard("build/outputs/roborazzi/shops_$name.png"); compose.onRoot().captureRoboImage("build/outputs/roborazzi/shops_$name.png") }
    private fun screen(name: String) { settle(); compose.guard("build/outputs/roborazzi/shops_$name.png"); captureScreenRoboImage("build/outputs/roborazzi/shops_$name.png") }
    private fun press(text: String) { compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick(); settle() }

    private fun shops() {
        // Shop icons come from the internet and arrive at random moments in a test: none are asked for, so every
        // tile shows its letter, the same every time.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { (Shops.starters + Shop("x", "https://www.google.com/")).forEach { ShopIcons.reject(app, it.host, ShopIcons.google(it.host)) } }
        compose.mainClock.autoAdvance = false; settle(); tap("My shops"); settle()
    }

    @Test fun shopsPage() { shops(); shot("page") }

    @Test fun shopsPageDark() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.setLook(app, Repo.design, "dark", Repo.palette) }
        shops(); shot("page_dark")
    }

    @Test fun removing() {
        shops()
        compose.onAllNodes(hasText("Amazon") and hasClickAction()).onFirst().performTouchInput { longClick() }
        shot("removing")
    }

    @Test fun addShop() {
        shops()
        press("Add shop"); screen("add")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("coolblue.nl")
        press("Add"); screen("add_twice")
    }

    @Test fun openShop() { shops(); press("HEMA"); screen("browser") }

    @Test fun search() {
        shops()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("wool blanket")
        settle(); shot("search_typed")
    }
}
