package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Co-op form (owner, 23 November 2026): no "Gifts for someone?" field, no "Shown at the top of the list." line,
 * and Friends picked while making the list. "Add friends" is one card everywhere (New wishlist, New group, New gift
 * exchange): never a plain "+ Add friends" line. Without Friends the card still opens and says what to do.
 */
class CoopFormTest : NavigationBase() {
    private val dir = File("src/main/java/com/wishlist/app")

    private fun open() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "blue", "dark")) { NewWishlistSheet(withOthers = true, onCreatedMine = {}, onCreatedShared = {}, onClose = {}) }
        } }
        idle()
    }

    private fun idle() = repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    @Test fun theFormHasNameDescriptionAndFriends() {
        open()
        for (t in listOf("Wishlist name*", "Description", "Add friends", "None chosen yet")) compose.onAllNodes(hasText(t)).onFirst().assertExists()
        for (t in listOf("Gifts for someone? (optional)", "Shown at the top of the list.")) assertEquals(0, compose.onAllNodes(hasText(t)).fetchSemanticsNodes().size)
    }

    @Test fun withoutFriendsTheCardSaysWhatToDo() {
        open()
        compose.onAllNodes(hasText("Add friends")).onFirst().performClick()
        idle()
        compose.onAllNodes(hasText("No Friends yet. After creating, you can send the invite link.")).onFirst().assertExists()
    }

    @Test fun everyNewFormUsesTheSameCard() {
        for (f in listOf("SharedUi.kt", "GroupNew.kt", "ExchangeNew.kt")) {
            val text = File(dir, f).readText()
            assertTrue("$f: use FriendsCard", text.contains("FriendsCard(friends, friendIds"))
            assertTrue("$f: no plain + Add friends line", !text.contains("Icon(AppIcons.Add, null, tint = cs.primary"))
            assertTrue("$f: the card shows without Friends too", !text.contains("if (friends.isNotEmpty()) FriendsCard"))
        }
        for (f in listOf("SharedUi.kt", "NewWishlist.kt")) assertTrue(f, !File(dir, f).readText().contains("supportingText = { Text(tr(\"Shown at the top"))
        val shared = File(dir, "SharedUi.kt").readText()
        assertTrue(!shared.contains("tr(\"Gifts for someone") && !shared.contains("var forWho"))
    }

    @Test fun failedInvitationsSayWhereToTryAgain() {
        assertEquals("Couldn’t send 1 invitation. Tap Share, then Invite Friends, to try again.", coopInviteFailed(1))
        assertEquals("Couldn’t send 3 invitations. Tap Share, then Invite Friends, to try again.", coopInviteFailed(3))
        // The list is made and opens even when the invitations fail.
        val shared = File(dir, "SharedUi.kt").readText()
        val at = shared.indexOf("model.inviteFriends(code, friendIds)")
        assertTrue(at > 0 && shared.indexOf("onCreated(code)", at) > at)
        // A list that isn't on this phone (or no profile): every invitation counts as not sent, nothing crashes.
        val m = SharedListModel(compose.activity)
        assertEquals(0, runBlocking { m.inviteFriends("NOPE", emptyList()) })
        assertEquals(2, runBlocking { m.inviteFriends("NOPE", listOf("a", "b")) })
    }
}
