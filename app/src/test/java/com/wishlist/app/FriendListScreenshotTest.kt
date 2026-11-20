package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of a friend's list (added for the rebuild, 19 November 2026): the list can't be loaded (no connection),
 * and the list isn't shared any more; light and dark. Pictures in app/build/outputs/roborazzi/friendlist_*.png; only
 * with -Pwl.screens (the class name has "Screenshot").
 */
class FriendListScreenshotTest : NavigationBase() {
    private fun settle() = repeat(12) { Thread.sleep(20); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.guard("build/outputs/roborazzi/friendlist_$name.png"); captureScreenRoboImage("build/outputs/roborazzi/friendlist_$name.png") }

    private fun show(mode: String, fetch: suspend () -> RemoteList?) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "berry", mode)) {
                FriendScreen(Friend("FRIENDCODE1", "Magdalena’s birthday"), SnackbarHostState(), onRemoved = {}, fetch = fetch, onBack = {}, ownerName = "Magdalena")
            }
        } }
        settle()
    }

    @Test fun offline() { show("light") { throw java.io.IOException("offline") }; shot("offline") }
    @Test fun offlineDark() { show("dark") { throw java.io.IOException("offline") }; shot("offline_dark") }
    @Test fun gone() { show("light") { null }; shot("gone") }
}
