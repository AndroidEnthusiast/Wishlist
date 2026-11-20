package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Chat about this wish" is in the ⋮ of a friend's wish in every view (owner, 20 November 2026: in 2.266 it was
 * missing from Two per row), and not when the list's owner isn't a friend (chatWith null).
 */
class FriendChatMenuTest : NavigationBase() {
    private fun settle() = repeat(12) { Thread.sleep(20); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private val list = RemoteList(
        "Magdalena’s wishlist", emptyList(),
        listOf(Wish(42L, "https://shop.example/jas", "Barbour jas", "", "", 263.95, 349.95, "EUR", ""))
    )

    @After fun noServer() { Cloud.pretendServer = null }

    private fun menuHasChat(view: String, chatWith: String?): Boolean {
        Cloud.pretendServer = { _, _ -> "[]" }
        compose.runOnUiThread { Repo.setView(compose.activity, view) }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "berry", "dark")) {
                FriendScreen(Friend("FRIENDCODE1", "Magdalena’s wishlist"), SnackbarHostState(), onRemoved = {}, fetch = { list }, onBack = {}, ownerName = "Magdalena", chatWith = chatWith)
            }
        } }
        settle()
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performClick()
        settle()
        return compose.onAllNodes(hasText("Chat about this wish")).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun rows() = assertTrue(menuHasChat("rows", "p2"))
    @Test fun twoPerRow() = assertTrue(menuHasChat("grid", "p2"))
    @Test fun largeCards() = assertTrue(menuHasChat("cards", "p2"))
    @Test fun notWithoutAFriend() = assertTrue(!menuHasChat("grid", null))
}
