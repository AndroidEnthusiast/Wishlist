package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * The levels page and the Iconic prize as drawn (owner, 27 November 2026), to look at before release. Only with
 * -Pwl.screens: build/outputs/roborazzi/iconic_<name>.png.
 */
class IconicScreenshotTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun show(name: String, design: String = "quiet", mode: String = "light", content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor(design, "blue", mode)) { Surface(Modifier.fillMaxSize()) { content() } } } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/iconic_$name.png")
    }

    @Test fun levelsPage() {
        Repo.addMine(app, Wish(1_700_000_000_001, "", "Jacket", "", "", 2980.0, null, "EUR", ""))
        show("levels") { LevelsScreen(onBack = {}, onPrizes = {}) }
        show("levels_dark", mode = "dark") { LevelsScreen(onBack = {}, onPrizes = {}) }
    }

    @Test fun thePopUpAndThePrizes() {
        Iconic.seen(app, ValueLevel.ICONIC, "EUR")
        show("popup") { IconicDialog(onClose = {}, onChoose = {}) }
        show("settings") { Column(Modifier.padding(16.dp)) { IconicSettingsPage() } }
        show("ring", mode = "dark") { Box(Modifier.padding(24.dp)) { Avatar(null, "Sam", 96.dp, ring = true) } }
    }
}
