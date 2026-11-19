package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of a shared wishlist's dialogs (added for the rebuild, 19 November 2026): Share, Make a new invite link, Leave, Delete for everyone, and the notice when the list is
 * gone; light, and dark for some. (Join isn't pictured: how fast the failed check comes back differs from run to run.) A pretend shared list is open. Pictures in app/build/outputs/roborazzi/sharedlist_*.png;
 * only with -Pwl.screens (the class name has "Screenshot").
 */
class SharedListScreenshotTest : NavigationBase() {
    private fun settle() = repeat(12) { Thread.sleep(20); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); captureScreenRoboImage("build/outputs/roborazzi/sharedlist_$name.png") }

    private fun show(mode: String = "light", content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            // A shared list you joined (not made: owner empty), open.
            Shared.lists = listOf(SharedList("SHCODE1", "SHKEY1234567890", "Grandma’s 80th"))
            Shared.active = "SHCODE1"
            compose.activity.setContent { LookTheme(lookFor("quiet", "berry", mode)) { Column(Modifier.fillMaxSize().lookPage()) { content() } } }
        }
        settle()
    }

    @Test fun share() { show { SharedShareDialog(onClose = {}) }; shot("share") }
    @Test fun shareDark() { show("dark") { SharedShareDialog(onClose = {}) }; shot("share_dark") }
    @Test fun newLink() { show { SharedManageDialog("reset", onClose = {}, onGone = {}) }; shot("reset") }
    @Test fun leave() { show { SharedManageDialog("leave", onClose = {}, onGone = {}) }; shot("leave") }
    @Test fun delete() { show { SharedManageDialog("delete", onClose = {}, onGone = {}) }; shot("delete") }
    @Test fun deleteDark() { show("dark") { SharedManageDialog("delete", onClose = {}, onGone = {}) }; shot("delete_dark") }
    @Test fun gone() { show { SharedGoneNotice() }; shot("gone") }
}
