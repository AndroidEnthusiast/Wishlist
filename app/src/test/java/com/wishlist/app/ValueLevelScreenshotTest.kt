package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Value levels as drawn (owner, 27 November 2026): each level's label with its icon over the amount and the line under
 * it, the size of Home's narrow Total value tile and of a list's summary, in Quiet and Gen Z chaos, light and dark.
 * Only with -Pwl.screens: build/outputs/roborazzi/levels_<style>_<mode>.png.
 */
class ValueLevelScreenshotTest : NavigationBase() {
    private fun wish(p: Double) = Wish(1_700_000_000_000 + p.toLong(), "", "x", "", "", p, null, "EUR", "")

    private fun shot(design: String, mode: String) {
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor(design, "blue", mode)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf(1240.0, 2980.0, 6415.0, 12870.0).forEach { p ->
                            val level = ValueLevels.of(p, "EUR")!!
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Home's tile: about 100 dp wide.
                                Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    ValueLevelTag(level, small = true, modifier = Modifier.padding(bottom = 2.dp))
                                    FitText(wholeMoney(p, "EUR"), MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), MaterialTheme.colorScheme.onSurface, Modifier.levelLine(level, 64.dp))
                                    Text("Total value", style = MaterialTheme.typography.bodySmall)
                                }
                                ListSummaryCard(listOf(wish(p)), Modifier.weight(1f), levels = true)
                            }
                        }
                    }
                }
            }
        } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/levels_${design}_$mode.png")
    }

    @Test fun quietLight() = shot("quiet", "light")
    @Test fun quietDark() = shot("quiet", "dark")
    @Test fun chaosLight() = shot("chaos", "light")
}
