package com.wishlist.app

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import org.junit.Test

/**
 * Every way into the app from outside (ROADMAP S1.3, Deep.kt): each kind of tapped notification, each kind of link and
 * something shared into the app opens the right screen, and Back leads Home. The checks run offline (Cloud.kt), so a
 * screen that needs the server shows its "couldn't reach" state, which is enough to see the right one opened.
 */
class EntryTest : NavigationBase() {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun notificationsBundle() {
        tap("Friends")
        send(Intent(app, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTES, true))
        shows("Notifications"); noBar()
        phoneBack()
        tabOn("Friends")
    }

    @Test fun severalPriceChanges() {
        send(Intent(app, MainActivity::class.java).putExtra(MainActivity.OPEN_PRICES, true))
        shows("Price changes"); noBar()
        phoneBack()
        tabOn("Home")
    }

    @Test fun friendRequest() {
        tap("My shops")
        send(Intent(app, MainActivity::class.java).putExtra(MainActivity.OPEN_FRIENDS, true))
        tabOn("Friends")
        phoneBack()
        tabOn("Home")
    }

    @Test fun birthdayOpensFriends() {
        send(open(MainActivity.OPEN_FRIEND, "someone"))
        compose.waitForIdle()
        // Friends, at that person (unknown here: Friends shows its own page); Back leads Home in one or two steps.
        compose.onAllNodes(hasText("Friends", substring = false)).onFirst().assertExists()
        phoneBack(); compose.waitForIdle()
        if (compose.onAllNodes(hasContentDescription("Home") and androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().isEmpty()) phoneBack()
        tabOn("Home")
    }

    @Test fun messageOpensTheChat() {
        send(open(MainActivity.OPEN_CHAT, "someone"))
        // An open conversation: no bottom bar. Back: the conversations, then Home.
        noBar()
        phoneBack()
        tabOn("Messages")
        phoneBack()
        tabOn("Home")
    }

    @Test fun reservedGiftGoneOpensTheList() {
        compose.runOnUiThread { Repo.addFriend(app, Friend("TESTCODE2", "Robin’s wishlist")) }
        send(open(MainActivity.FRIEND_CODE, "TESTCODE2"))
        compose.onAllNodes(hasContentDescription("Remove this list") and hasClickAction()).onFirst().assertExists()
        phoneBack()
        tabOn("Home")
    }

    @Test fun reservedGiftInTheBin() {
        tap("Messages")
        send(Intent(app, MainActivity::class.java).putExtra(MainActivity.BIN_CODE, "TESTCODE3").putExtra(MainActivity.BIN_ID, 5L))
        shows("Bin"); noBar()
        phoneBack()
        // The bin closes what was open before (as before 2.162): Home.
        tabOn("Home")
    }

    @Test fun groupAndExchangeInvitations() {
        // Not on this phone: the Join screen, which can't reach the server here.
        send(open(MainActivity.OPEN_GROUP, "unknown-group"))
        shows("Wishlist group")
        compose.onAllNodes(hasText("OK", substring = false) and hasClickAction()).fetchSemanticsNodes()
        phoneBack()
        send(open(MainActivity.OPEN_EXCHANGE, ""))
        shows("Gift exchange")
        tabOn("Home")
    }

    @Test fun friendLinkAsksForAFriendRequest() {
        // Read on its own (deepOf): "Send Robin a friend request?" opens with the keyboard, which never settles on the
        // computer's pretend phone. Opening it is one line in App.go (Friends tab + FriendsNav.addCode).
        val i = link("wishlist://friend?f=ABC234&n=Robin")
        org.junit.Assert.assertEquals(Deep.FriendLink("ABC234", "Robin"), deepOf(i))
        // Handled once: taken off the Intent, so turning the phone doesn't open it again.
        org.junit.Assert.assertNull(deepOf(i))
        org.junit.Assert.assertEquals(
            Deep.FriendLink("ABC234", "Robin"), deepOf(link("https://androidenthusiast.github.io/Wishlist/friend/?f=ABC234&n=Robin"))
        )
    }

    @Test fun webLinksOpenTheApp() {
        // A collab wishlist invitation (App Link): the Join screen.
        send(link("https://androidenthusiast.github.io/Wishlist/?j=ABCDEFGHJKLMNPQRSTUV"))
        shows("Invitation to a collab wishlist")
    }

    @Test fun groupLinkShowsTheJoinScreen() {
        send(link("https://androidenthusiast.github.io/Wishlist/group/?g=ABCDEFGHJKLMNPQRSTUV"))
        shows("Wishlist group")
    }

    @Test fun exchangeLinkShowsGiftExchange() {
        send(link("https://androidenthusiast.github.io/Wishlist/exchange/?x=ABCDEFGHJKLMNPQRSTUV"))
        shows("Gift exchange")
    }

    @Test fun aListComesBackAfterAndroidClosedTheApp() {
        // The list on screen is named in its route. Another list became "open" in the app's memory (as when Android
        // closes the app in the background and only the screen is restored); rebuilding the screen opens the right one.
        val first = Repo.mineIds.first()
        send(open(MainActivity.OPEN_NOTE, "m:$first"))
        compose.runOnUiThread {
            val other = Repo.createMine(app, "Second list", "")
            Repo.openMine(app, other)
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(first, Repo.openMine)
        compose.onAllNodes(hasContentDescription("Wishlist settings") and hasClickAction()).onFirst().assertExists()
        // A list that is no longer on this phone isn't opened.
        org.junit.Assert.assertFalse(openListOf(app, "s:NOTHERE1"))
        org.junit.Assert.assertFalse(openListOf(app, "m:not-here"))
    }

    @Test fun rotatingKeepsTheScreen() {
        tap("Friends")
        fromSheet("App settings")
        shows("App settings")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        shows("App settings")
        phoneBack()
        tabOn("Friends")
        compose.onAllNodes(hasText("App settings", substring = false)).assertCountEquals(0)
    }
}

/** Something shared into the app, read on its own (deepOf); the forms open with the keyboard (see above). */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ShareEntryTest {
    @Test fun sharedTextBecomesTheRightPlace() {
        fun send(t: String) = deepOf(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t))
        org.junit.Assert.assertEquals(Deep.AddLink("https://www.example.com/p/teapot-123"), send("Look https://www.example.com/p/teapot-123"))
        org.junit.Assert.assertEquals(Deep.Invite("ABCDEFGHJKLMNPQRSTUV"), send("Join us https://androidenthusiast.github.io/Wishlist/?j=ABCDEFGHJKLMNPQRSTUV"))
        org.junit.Assert.assertEquals(Deep.GroupLink("ABCDEFGHJKLMNPQRSTUV"), send("https://androidenthusiast.github.io/Wishlist/group/?g=ABCDEFGHJKLMNPQRSTUV"))
        org.junit.Assert.assertNull(send("just some words"))
    }
}
