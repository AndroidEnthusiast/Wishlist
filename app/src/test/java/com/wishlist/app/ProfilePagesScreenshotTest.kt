package com.wishlist.app

import androidx.activity.compose.setContent
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gifts I reserved and Received gifts (owner, 22 November 2026: they had their own small header and Android's own back
 * button; now the app's frame with the big title like every screen), in both styles, light, dark and large text,
 * checked by the layout inspector. Pictures in app/build/outputs/roborazzi/pages_*.png; only with -Pwl.screens.
 */
class ProfilePagesScreenshotTest : NavigationBase() {
    private fun each(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark", "big")) {
            compose.runOnUiThread { compose.activity.setContent {} }
            repeat(4) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            compose.runOnUiThread { compose.activity.setContent {
                val look = lookFor(st, "blue", if (m == "big") "light" else m)
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, if (m == "big") 1.3f else d.fontScale)
                ) { LookTheme(look) { content() } }
            } }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/pages_${name}_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }

    @Test fun reserved() = each("reserved") { ReservedScreen(onClose = {}, onOpen = {}) }

    @Test fun received() = each("received") { ReceivedScreen(onClose = {}) }

    // They keep the app's own top bar and buttons (docs/STYLE.md "Screen titles", "Use the app's own parts").
    @Test fun theyUseTheAppsOwnTopBar() {
        val dir = File("src/main/java/com/wishlist/app")
        for (f in listOf("Reserved.kt", "Reminders.kt")) {
            val t = File(dir, f).readText()
            assertTrue("$f: the app's TopAppBar", "TopAppBar(" in t && "ScreenTitle(" in t)
            assertTrue("$f: Android's own IconButton", "androidx.compose.material3.IconButton" !in t)
        }
    }
}
