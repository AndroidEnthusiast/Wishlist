package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every part of the app in every style, light and dark (owner, 13 November 2026: "a theme must change absolutely every
 * element"): a field, tick boxes, choice circles, chips, a switch, the buttons, a spinner, a progress bar, a slider,
 * badges, a divider and the emblem of a wish without a photo. Pictures in app/build/outputs/roborazzi/parts_*.png.
 * Slow, so only with -Pwl.screens, like the other screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PartsScreenshotTest {
    @Test fun everyPartInEveryStyle() {
        StillSpinners.on = true
        for (st in STYLES.map { it.key }) for (m in listOf("light", "dark")) captureRoboImage("build/outputs/roborazzi/parts_${st}_$m.png") {
            LookTheme(lookFor(st, "indigo", m)) {
                Column(
                    Modifier.fillMaxWidth().lookPage().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("${styleFor(st).name} · $m", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    OutlinedTextField("Teapot", {}, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField("", {}, label = { Text("Note") }, supportingText = { Text("Only you see this") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(true, {}); Checkbox(false, {}); RadioButton(true, {}); RadioButton(false, {})
                        Switch(true, {}); Switch(false, {})
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(true, {}, { Text("Exactly this") }); FilterChip(false, {}, { Text("Similar") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button({}) { Text("Save") }; OutlinedButton({}) { Text("Later") }; TextButton({}) { Text("Undo") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        PriceBadge("−20%", rise = false, small = false); PriceBadge("+5%", rise = true, small = false)
                        StatusBadge("Reserved", small = false)
                        PlaceholderIcon("gift", 48.dp)
                    }
                    LinearProgressIndicator({ 0.6f }, Modifier.fillMaxWidth())
                    HorizontalDivider()
                    Slider(0.4f, {}, Modifier.width(240.dp))
                }
            }
        }
    }
}
