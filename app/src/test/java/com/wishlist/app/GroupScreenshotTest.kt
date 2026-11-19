package com.wishlist.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

/**
 * Pictures of the wishlist group screens with pretend groups (added for the rebuild, 19 November 2026, so the
 * before/after comparison covers them): Home with an invitation and a group, a group as its organiser and as a member,
 * its ⋮ menu, Group settings, Leave (the organiser picks the next organiser), Invite friends and Change my wishlist.
 * Pictures in app/build/outputs/roborazzi/group_*.png; only with -Pwl.screens (the class name has "Screenshot").
 */
class GroupScreenshotTest : NavigationBase() {
    private fun settle() = repeat(8) { compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("build/outputs/roborazzi/group_$name.png") }

    private fun pretend() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        // A fixed id for "you", so every picture shows the same person as the organiser.
        app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().putString("pf_id", "me1").commit()
        val me = Groups.myId(app)
        val mine = GroupList("L1", "Sam's wishlist", 3, "")
        val members = listOf(
            GroupMember(me, "Sam", 0L, false, "C1", true, mine),
            GroupMember("p2", "Magdalena with a long name", 0L, false, "C2", false, GroupList("L2", "Birthday", 0, "")),
            GroupMember("p3", "Jorai", 0L, true, "", false, GroupList("L3", "Jorai's wishlist", 7, "")),
            GroupMember("p4", "Noor", 0L, false, "C4", true, null)
        )
        val ours = Group("g1", "LINK1", "Family", 0L, me, false, members, setOf("p5"))
        val theirs = Group("g2", "LINK2", "Book club friends", 0L, "p2", true, members.take(3), emptySet())
        val invite = GroupInvite("g3", "Climbing crew", "LINK3", "p6", "Ana", 0L, 5)
        compose.runOnUiThread { Groups.groups = listOf(ours, theirs); Groups.invites = listOf(invite) }
    }

    private fun open(id: String) {
        compose.runOnUiThread { compose.activity.handle(android.content.Intent(compose.activity, MainActivity::class.java).putExtra(MainActivity.OPEN_GROUP, id)) }
        settle()
    }

    private fun press(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        settle()
    }

    private fun menu() {
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performClick()
        settle()
    }

    @Test fun groupScreens() {
        compose.mainClock.autoAdvance = false
        pretend()
        shot("home")
        open("g1"); shot("organiser")
        open("g2"); shot("member")
    }

    @Test fun groupScreensDark() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.setLook(app, Repo.design, "dark", Repo.palette) }
        compose.mainClock.autoAdvance = false
        pretend()
        shot("home_dark")
        open("g1"); shot("organiser_dark")
    }

    @Test fun groupMenuAndSettings() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("g1"); menu(); shot("menu")
        press("Group settings"); shot("settings")
    }

    @Test fun groupLeave() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("g1"); menu(); press("Leave group"); shot("leave")
    }

    @Test fun groupInviteAndChange() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("g1"); menu(); press("Invite friends"); shot("invite")
    }

    @Test fun groupChangeList() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("g2"); menu(); press("Change my wishlist"); shot("change")
    }
}
