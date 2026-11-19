package com.wishlist.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import java.time.LocalDate

/**
 * Pictures of the gift exchange screens with pretend exchanges (added for the rebuild, 19 November 2026, so the
 * before/after comparison covers them): the exchanges list with an invite, an exchange before the draw as its
 * organiser, one after the draw with your giftee, and the organiser's settings. Pictures in
 * app/build/outputs/roborazzi/exchange_*.png; only with -Pwl.screens (the class name has "Screenshot").
 */
class ExchangeScreenshotTest : NavigationBase() {
    private fun settle() = repeat(8) { compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); compose.onRoot().captureRoboImage("build/outputs/roborazzi/exchange_$name.png") }

    private fun member(id: String, name: String, seen: Boolean? = null) = GxMember(id, name, 0L, false, "C$id", true, seen, false, null)

    private fun pretend() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        // A fixed id for "you", so every picture shows the same person as the organiser.
        app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().putString("pf_id", "me1").commit()
        val me = Exchanges.myId(app)
        val day = LocalDate.now().plusDays(12)
        val before = Exchange(
            "gx1", "LINK1", "Family Christmas with a long name", me, "Sam", 25.0, "EUR", day, "xmas", "Bring something cosy!", 0L,
            drawn = false, revealed = false,
            members = listOf(member(me, "Sam"), member("p2", "Magdalena"), member("p3", "Jorai")),
            invited = listOf("p4" to "Noor"), excl = listOf("p2" to "p3"), blocked = null,
            me = GxMe(GroupList("L1", "Sam's wishlist", 3, ""), seen = false, waiting = false, hasSanta = false, giftee = null, unreadGiftee = 0, unreadSanta = 0)
        )
        val after = before.copy(
            id = "gx2", link = "LINK2", name = "Office Secret Santa", organiser = "p2", organiserName = "Magdalena", theme = "office", drawn = true,
            members = listOf(member(me, "Sam", true), member("p2", "Magdalena", false), member("p3", "Jorai", true)),
            invited = emptyList(), excl = emptyList(),
            me = GxMe(GroupList("L1", "Sam's wishlist", 3, ""), seen = true, waiting = false, hasSanta = true,
                giftee = GxGiftee("p3", "Jorai", 0L, false, GroupList("L3", "Jorai's wishlist", 4, "")), unreadGiftee = 1, unreadSanta = 0)
        )
        val invite = GxInvite("gx3", "Book club swap", "LINK3", "p5", "Ana", "sint", 15.0, "EUR", day.plusDays(10), 6)
        compose.runOnUiThread { Exchanges.list = listOf(before, after); Exchanges.invites = listOf(invite) }
    }

    private fun open(id: String?) {
        compose.runOnUiThread { compose.activity.handle(android.content.Intent(compose.activity, MainActivity::class.java).putExtra(MainActivity.OPEN_EXCHANGE, id.orEmpty())) }
        settle()
    }

    @Test fun exchangeScreens() {
        compose.mainClock.autoAdvance = false
        pretend()
        open(null); shot("list")
        open("gx1"); shot("before")
        open("gx2"); shot("after")
    }

    @Test fun exchangeScreensDark() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.setLook(app, Repo.design, "dark", Repo.palette) }
        compose.mainClock.autoAdvance = false
        pretend()
        open(null); shot("list_dark")
        open("gx1"); shot("before_dark")
        open("gx2"); shot("after_dark")
    }

    @Test fun leaveAndRules() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("gx1")
        compose.onAllNodes((hasContentDescription("More") or hasText("Settings")) and hasClickAction()).onFirst().performClick()
        settle()
        compose.onAllNodes(hasText("Leave exchange") and hasClickAction()).onFirst().performClick()
        shot("leave")
    }

    @Test fun organiserSettings() {
        compose.mainClock.autoAdvance = false
        pretend()
        open("gx1")
        compose.onAllNodes((hasContentDescription("More") or hasText("Settings")) and hasClickAction()).onFirst().performClick()
        shot("menu")
    }
}
