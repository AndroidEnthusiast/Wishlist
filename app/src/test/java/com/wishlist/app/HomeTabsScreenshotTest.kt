package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Home's tabs (owner, 21 November 2026): all four (Personal | Co-op | Groups | Exchanges) on the narrowest phone
 * (360 dp) and with the phone's text at 130 %, both styles, light and dark: never cut, a little smaller instead. Also
 * two and three tabs. Pictures in app/build/outputs/roborazzi/home_tabs_*.png; only with -Pwl.screens.
 */
class HomeTabsScreenshotTest : NavigationBase() {
    @Test fun tabsFit() {
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) {
            compose.runOnUiThread { compose.activity.setContent {
                LookTheme(lookFor(st, "blue", m)) {
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1.3f)
                    ) {
                        Column(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (n in listOf(4, 3, 2)) Column(Modifier.width(360.dp)) {
                                val tabs = homeTabs(coop = n >= 2, groups = n >= 3, exchanges = n >= 4)
                                SegmentedTabs(tabs.map { homeTabLabel(it) }, 0, {}, Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            } }
            repeat(6) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/home_tabs_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }
}
