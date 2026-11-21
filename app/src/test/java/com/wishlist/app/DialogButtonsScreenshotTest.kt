package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
            compose.guard("build/outputs/roborazzi/dialog_buttons_$m.png"); captureScreenRoboImage("build/outputs/roborazzi/dialog_buttons_$m.png")
        }
    }

    /** "New wishlist" (2.223): the full-width sheet with My own / With others, X top right, Create at the bottom. */
    @Test fun newWishlistForSomeoneElse() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "blue", "dark")) { NewWishlistSheet(withOthers = true, onCreatedMine = {}, onCreatedShared = {}, onClose = {}) }
        } }
        repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        compose.guard("build/outputs/roborazzi/new_wishlist_with_others.png"); captureScreenRoboImage("build/outputs/roborazzi/new_wishlist_with_others.png")
    }

    /** "New wishlist" of your own with "Who can see it" (owner, 21 November 2026), light and dark, Quiet and chaos. */
    @Test fun newWishlistOfMyOwn() {
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) {
            compose.runOnUiThread { compose.activity.setContent {
                LookTheme(lookFor(st, "blue", m)) { NewWishlistSheet(withOthers = false, onCreatedMine = {}, onCreatedShared = {}, onClose = {}) }
            } }
            repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/new_wishlist_mine_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }

    /** "Add wishes from other wishlists" as a card (owner, 21 November 2026): none chosen, and 7 chosen with photos. */
    @Test fun copyWishesCard() {
        compose.mainClock.autoAdvance = false
        val ws = (1..7).map { Wish(it.toLong(), "", "Wish $it", "", "", null, null, "EUR", "") }
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) {
            compose.runOnUiThread { compose.activity.setContent {
                LookTheme(lookFor(st, "blue", m)) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { CopyWishesCard(emptyList()) {}; CopyWishesCard(ws, onRemove = {}) {}; WhoIsItFor(false) {}; WhoIsItFor(true) {} }
                }
            } }
            repeat(6) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/copy_card_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }
}
