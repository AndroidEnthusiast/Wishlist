package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of gift list invitations (added for the rebuild, 19 November 2026): the invitation that is no longer open
 * (light, dark; without a profile the app can't ask the server), Invite Friends when the server can't be reached, and
 * the chat card. Pictures in app/build/outputs/roborazzi/giftinvite_*.png; only with -Pwl.screens.
 */
class GiftInviteScreenshotTest : NavigationBase() {
    private fun settle() = repeat(12) { Thread.sleep(20); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.guard("build/outputs/roborazzi/giftinvite_$name.png"); captureScreenRoboImage("build/outputs/roborazzi/giftinvite_$name.png") }

    private fun show(mode: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "berry", mode)) { content() } } }
        settle()
    }

    @Test fun gone() { show("light") { GiftInviteDialog("GIFTCODE1", onJoined = {}, onClose = {}) }; shot("gone") }
    @Test fun goneDark() { show("dark") { GiftInviteDialog("GIFTCODE1", onJoined = {}, onClose = {}) }; shot("gone_dark") }

    @Test fun inviteFriendsOffline() {
        show("light") { GiftInviteFriendsDialog(SharedList("SHCODE1", "Grandma’s 80th", "", ""), onClose = {}) }
        shot("invite_offline")
    }

    @Test fun chatCard() {
        show("light") {
            Column(Modifier.fillMaxSize().lookPage().padding(24.dp)) {
                GiftListChatCard("Grandma’s 80th", mine = false, ink = androidx.compose.ui.graphics.Color.Black)
                GiftListChatCard("Office leaving gift", mine = true, ink = androidx.compose.ui.graphics.Color.Black)
            }
        }
        shot("chatcard")
    }
}
