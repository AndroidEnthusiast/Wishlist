package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * The Share dialog's layout (owner's screenshot, 17 November 2026: "Send invite" and "Copy" grey on blue) in Quiet,
 * dark and light, to look at: build/outputs/roborazzi/dialog_buttons_*.png. Only with -Pwl.screens.
 */
class DialogButtonsScreenshotTest : NavigationBase() {
    @Test fun shareDialog() {
        for (m in listOf("dark", "light")) {
            compose.runOnUiThread { compose.activity.setContent {
                LookTheme(lookFor("quiet", "blue", m)) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Share “Twst”") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("They can edit and reserve wishes. Only send it to people you trust.")
                                Text("https://androidenthusiast.github.io/Wishlist/join/?j=SHWWS9C5BV7HCHL2RYLK")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button({}) { Text("Send invite") }
                                    OutlinedButton({}) { Text("Copy") }
                                }
                                Text("Or invite your Friends in the app:")
                                OutlinedButton({}) { Text("Invite Friends") }
                            }
                        },
                        confirmButton = { TextButton({}) { Text("Done") } }
                    )
                }
            } }
            compose.waitForIdle()
            captureScreenRoboImage("build/outputs/roborazzi/dialog_buttons_$m.png")
        }
    }
}
