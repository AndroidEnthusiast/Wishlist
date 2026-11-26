package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Inviting people, neat and the same everywhere (owner, 26 November 2026, a screen recording: the friend she had just
 * invited wasn't on the share screen, a naked link, "messy and busy"): one sheet for Co-op lists, groups and gift
 * exchanges with the people and their status, "+ Add friends" last and one "Share invite link"; one "Invite people"
 * in the ⋮ menus and on the pages; the friend pickers only offer friends who aren't in or invited yet.
 * docs/decisions/people.md. Pictures in app/build/outputs/roborazzi/invite_*.png with -Pwl.screens.
 */
class InvitePeopleTest : NavigationBase() {
    private fun settle() = repeat(12) { Thread.sleep(20); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.guard("build/outputs/roborazzi/invite_$name.png"); captureScreenRoboImage("build/outputs/roborazzi/invite_$name.png") }
    private fun show(mode: String = "light", content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "berry", mode)) { content() } } }
        settle()
    }
    private val src = File("src/main/java/com/wishlist/app")
    private fun read(vararg f: String) = f.joinToString("\n") { File(src, it).readText() }

    @Test fun theSheetShowsPeopleAndOneShareButton() {
        val people = listOf(CoopPerson("p1", "Magdalena", true), CoopPerson("p2", "Joost", false))
        show { PeopleShareSheet("“Grandma’s 80th” is ready", "Everyone on it can add, edit and reserve gifts.", null, people, false, {}, 2, {}, {}, {}) }
        compose.onNodeWithText("Magdalena").assertExists()
        compose.onNodeWithText("On the list").assertExists()
        compose.onNodeWithText("Invited").assertExists()
        compose.onNodeWithText("Add friends").assertExists()
        assertEquals(1, compose.onAllNodesWithText("Share invite link").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithText("https://", substring = true).fetchSemanticsNodes().isEmpty())
        shot("sheet")
    }

    @Test fun addFriendsIsOffWhenEveryoneIsIn() {
        show("dark") { PeopleShareSheet("Share “Office gift”", "Everyone on it can add, edit and reserve gifts.", "Don’t share it with Anna: it’s a surprise.", listOf(CoopPerson("p1", "Magdalena", true)), false, {}, 0, {}, {}, {}) }
        compose.onNodeWithText("All your friends are on it or invited").assertExists()
        shot("sheet_all_dark")
    }

    @Test fun offlineSaysWhatToDo() {
        show { PeopleShareSheet("Share “Office gift”", "Everyone on it can add, edit and reserve gifts.", null, null, true, {}, 1, {}, {}, {}) }
        compose.onNodeWithText("Try again").assertExists()
        shot("sheet_offline")
    }

    @Test fun groupSheetShowsMembersAndInvited() {
        val g = Group("g1", "LINK", "Family", 0L, "me", false, listOf(GroupMember("p1", "Magdalena", 0L, false, "", true, null)), setOf("p2"))
        show { GroupShareSheet(g, onAdd = {}, onClose = {}) }
        compose.onNodeWithText("On the list").assertExists()
        compose.onNodeWithText("Invited").assertExists()
        shot("group")
    }

    @Test fun oneInvitePeopleEverywhere() {
        val pages = read("GroupScreen.kt", "ExchangeScreen.kt", "ListSettings.kt")
        for (gone in listOf("Share group link", "Share exchange link", "tr(\"Share link\")", "tr(\"Invite friends\")")) assertFalse(gone, gone in pages)
        assertTrue(Regex("tr\\(\"Invite people\"\\)").findAll(pages).count() >= 4)
        assertTrue("GroupShareSheet(" in read("GroupScreen.kt") && "ExchangeShareSheet(" in read("ExchangeDialogs.kt"))
        // No naked link on the Co-op share screen: one sheet, opened right after making the list too.
        val share = read("SharedManage.kt")
        assertTrue("InvitePeopleSheet(" in share && "LocalClipboardManager" !in share)
        assertTrue("SharedShareDialog(justMade = a.inviteMade)" in read("AppPopups.kt"))
    }

    @Test fun pickersOnlyOfferFriendsNotInOrInvited() {
        assertTrue("val taken = g.members.map { it.id }.toSet() + g.invited" in read("GroupMembers.kt"))
        assertTrue("Share group link" !in read("GroupMembers.kt"))
        assertTrue("val taken = x.members.map { it.id }.toSet() + x.invited.map { it.first }" in read("ExchangeRows.kt"))
        assertTrue("friends.filter { it.id !in t }" in read("GiftInviteViews.kt"))
        // Who is on a Co-op list comes from the server: members first, then the invitations not taken up yet.
        assertTrue("ids(\"invited\").filter { it !in members }" in read("GiftInvites.kt"))
    }
}
