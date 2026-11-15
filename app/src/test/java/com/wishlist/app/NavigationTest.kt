package com.wishlist.app

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config as Robo

/**
 * The screens that are routes (Nav.kt, ROADMAP Stage 1), in the real app on a computer (Robolectric): each opens from
 * where a person opens it, and Back (the phone's, or the ← in the top bar) returns to the screen it was opened from.
 * Runs in the GitHub build; a wrong Back stops the update.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class NavigationBase {
    private val setup = object : TestRule {
        override fun apply(base: Statement, d: Description) = object : Statement() {
            override fun evaluate() { prepare(); base.evaluate() }
        }
    }
    protected val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    protected fun prepare() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Animations off, as on a phone with "Remove animations": November's snow on Home (Snow.kt) never stops
        // otherwise, and on GitHub's slower computer the pretend phone then never settles (2.158's build failed so).
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // No welcome, no background question and a name, so no pop-up covers the screens.
        app.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("asked_background", true).putString("sh_me", "Sam").commit()
        // A new pretend phone: its own database (the app keeps both in objects between checks in one process).
        ListStore.reset(); WishlistDb.reset()
        Repo.load(app)
    }

    /** The bottom bar's tab with this name is the one on screen. */
    protected fun tabOn(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription(name) and isSelected()).onFirst().assertExists()
    }

    protected fun shows(text: String) {
        compose.waitForIdle()
        compose.onAllNodes(hasText(text, substring = false)).onFirst().assertExists()
    }

    /** No bottom bar: a route on top covers the whole screen. */
    protected fun noBar() {
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Messages") and SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.Selected
        )).assertCountEquals(0)
    }

    protected fun tap(text: String) {
        compose.waitForIdle()
        val node = compose.onAllNodes((hasText(text, substring = false) or hasContentDescription(text)) and hasClickAction()).onFirst()
        node.assertExists()
        node.performClick()
    }

    /** A tapped notification or an opened link reaches the app (MainActivity.handle, as onNewIntent does). */
    protected fun send(i: android.content.Intent) {
        compose.runOnUiThread { compose.activity.handle(i) }
        if (compose.mainClock.autoAdvance) compose.waitForIdle()
    }

    protected fun open(extra: String, value: String): android.content.Intent =
        android.content.Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(extra, value)

    protected fun link(url: String): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))

    protected fun phoneBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    /** Profile sheet → one of its screens. */
    protected fun fromSheet(item: String) {
        tap("Profile and settings")
        tap(item)
    }
}

class NavigationTest : NavigationBase() {
    @Test fun appSettingsBackToHome() {
        tabOn("Home")
        fromSheet("App settings")
        shows("App settings"); shows("Themer"); noBar()
        phoneBack()
        tabOn("Home")
    }

    @Test fun binBackToTheTabItWasOpenedFrom() {
        // Before 2.154 Back from the Bin always went to Home; now it returns to Friends, where it was opened.
        tap("Friends")
        tabOn("Friends")
        fromSheet("Bin")
        shows("Bin"); noBar()
        phoneBack()
        tabOn("Friends")
    }

    @Test fun profileBackArrow() {
        tap("My shops")
        tabOn("My shops")
        fromSheet("Profile")
        noBar()
        tap("Back")
        tabOn("My shops")
    }

    @Test fun bellOpensNotificationsAndBackReturns() {
        tabOn("Home")
        tap("Notifications")
        shows("Notifications"); noBar()
        phoneBack()
        tabOn("Home")
    }

    @Test fun onlyOneScreenOnTop() {
        // App settings, then the bell's Notifications from a notification: Back goes straight to Home, not to App
        // settings (one route on top at a time, as before).
        fromSheet("App settings")
        shows("App settings")
        send(android.content.Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.OPEN_NOTES, true))
        shows("Notifications")
        phoneBack()
        tabOn("Home")
    }

    @Test fun receivedAndReservedFromTheSheet() {
        // Full-screen pop-ups until 2.156, routes now: Back returns to the tab they were opened on.
        tap("Friends")
        fromSheet("Received gifts")
        shows("Received gifts"); noBar()
        phoneBack()
        tabOn("Friends")
        fromSheet("Gifts I reserved")
        shows("Gifts I reserved"); noBar()
        tap("Back")
        tabOn("Friends")
    }

    @Test fun backOnATabGoesHome() {
        // Owner, 8 November 2026: Back on a tab's first page goes Home, also when a list was open before.
        val id = Repo.mineIds.first()
        send(open(MainActivity.OPEN_NOTE, "m:$id"))
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Wishlist settings") and hasClickAction()).onFirst().assertExists()
        tap("Messages")
        tabOn("Messages")
        tap("My shops")
        tabOn("My shops")
        phoneBack()
        tabOn("Home")
    }

    @Test fun messagesHasNoTabs() {
        // Owner, 15 November 2026: only the conversations; friends are in the bottom bar, a chat starts with the round
        // button (StartChatButton).
        tap("Messages")
        tabOn("Messages")
        compose.onAllNodes(hasText("Chats", substring = false)).assertCountEquals(0)
    }

    @Test fun screensOnTopOfATabReturnToIt() {
        // Profile from the Friends tab, then the bell: Back returns to Friends each time (the tab stays underneath).
        tap("Friends")
        fromSheet("Profile")
        phoneBack()
        tabOn("Friends")
        tap("Notifications")
        shows("Notifications")
        phoneBack()
        tabOn("Friends")
        phoneBack()
        tabOn("Home")
    }

    @Test fun giftExchangesKeepTheBarAndGoBackHome() {
        // Gift exchanges (Home, a link, a notification): its own screen with the bottom bar, Home lit; ← returns Home.
        tap("Friends")
        send(open(MainActivity.OPEN_EXCHANGE, ""))
        shows("Gift exchange")
        tabOn("Home")
        tap("Back")
        tabOn("Home")
        compose.onAllNodes(hasText("Gift exchange", substring = false)).assertCountEquals(0)
    }

    @Test fun aListHasTheBarAndGoesHome() {
        // The open list is its own screen (2.160): the bottom bar shows with no tab lit, Back goes Home.
        val id = Repo.mineIds.first()
        send(open(MainActivity.OPEN_NOTE, "m:$id"))
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Wishlist settings") and hasClickAction()).onFirst().assertExists()
        compose.onAllNodes(hasContentDescription("Messages") and hasClickAction()).onFirst().assertExists()
        compose.onAllNodes(hasContentDescription("Home") and isSelected()).assertCountEquals(0)
        phoneBack()
        tabOn("Home")
    }

    @Test fun createListFromHome() {
        // "Create list" on Home opens the choice (it lives in Home since 2.163); Cancel closes it, Home stays.
        tap("Create list")
        shows("Create a list")
        tap("Cancel")
        tabOn("Home")
        compose.onAllNodes(hasText("Create a list", substring = false)).assertCountEquals(0)
    }

    @Test fun friendsListFromALinkBackHome() {
        // A list saved from a link (a notification about it opens it): its own screen, Back returns Home.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.addFriend(app, Friend("TESTCODE1", "Robin’s wishlist")) }
        send(open(MainActivity.OPEN_NOTE, "c:TESTCODE1"))
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Remove this list") and hasClickAction()).onFirst().assertExists()
        noBar()
        phoneBack()
        tabOn("Home")
    }

    @Test fun priceChangesBackToWhereItWasOpened() {
        // "All price changes" (a wish's price page, or a notification) from the Messages tab.
        tap("Messages")
        tabOn("Messages")
        send(android.content.Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.OPEN_PRICES, true))
        shows("Price changes"); noBar()
        phoneBack()
        tabOn("Messages")
    }

    @Test fun aTabTappedTwiceOpensOnce() {
        // Tapping a tab again (also while it is still moving in, Motion.kt) must not stack it twice: one Back is Home.
        tap("Messages")
        tap("Messages")
        tabOn("Messages")
        phoneBack()
        tabOn("Home")
    }
}

/**
 * Its own class, so its own fresh process (build.gradle.kts, forkEvery): after the list's settings screen (its cover
 * photo part), Robolectric's pretend phone keeps Compose busy for the next check in the same process. Only seen on the
 * computer; each check class starts clean.
 */
class ListNavigationTest : NavigationBase() {
    @Test fun listSettingsBackToTheList() {
        // A notification about a wish on your own list opens that list; its ⚙ opens the list's settings.
        val id = Repo.mineIds.first()
        send(open(MainActivity.OPEN_NOTE, "m:$id"))
        tap("Wishlist settings")
        shows("Wishlist settings"); noBar()
        phoneBack()
        // The list again: its ⚙ is back, and Back from there goes Home.
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Wishlist settings") and hasClickAction()).onFirst().assertExists()
        phoneBack()
        tabOn("Home")
    }
}

/**
 * Screens with a one-line top bar scroll (2.211, owner 15 November 2026: "nothing happens when I scroll on home",
 * Wishlist settings "can't scroll down anymore"): the collapsing title of other screens used up every swipe.
 */
class ScrollTest : NavigationBase() {
    private fun topOf(text: String) =
        compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot.top

    private fun swipeUpMoves(text: String) {
        compose.waitForIdle()
        val before = topOf(text)
        compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.25f) }
        compose.waitForIdle()
        org.junit.Assert.assertTrue("$text did not move: the page didn't scroll", topOf(text) < before - 20f)
    }

    @Test fun listSettingsScrolls() {
        val id = Repo.mineIds.first()
        send(open(MainActivity.OPEN_NOTE, "m:$id"))
        tap("Wishlist settings")
        shows("Wishlist settings")
        swipeUpMoves("About this list")
    }

    @Test fun homeScrolls() {
        // Enough on Home to scroll: a few lists.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { repeat(4) { i -> Repo.createMine(app, "List $i", "") } }
        swipeUpMoves("Your wishlists")
    }
}

/** A long press on a wish starts selecting; ⋮ opens the wish's sheet (owner, 12 November 2026). */
class WishPressTest : NavigationBase() {
    @Test fun longPressSelectsAndMoreOpensTheSheet() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread {
            Repo.addMine(app, Wish(1, "https://example.com/a", "Ceramic teapot", "", "", 20.0, null, "EUR", "Home"))
            Repo.addMine(app, Wish(2, "https://example.com/b", "Rain jacket", "", "", 80.0, null, "EUR", "Home"))
        }
        send(open(MainActivity.OPEN_NOTE, "m:" + Repo.mineIds.first()))
        compose.waitForIdle()
        compose.onAllNodes(hasText("Ceramic teapot", substring = true)).onFirst()
            .performTouchInput { longClick() }
        shows("1 selected")
        // The action bar takes the bottom bar's place (2.209): Move to… and Delete, no Home or + while selecting.
        compose.onAllNodes(hasText("Move to…") and hasClickAction()).onFirst().assertExists()
        compose.onAllNodes(hasText("Delete") and hasClickAction()).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Add wish")).assertCountEquals(0)
        // The cards' own ⋮, share and bin are gone while selecting.
        compose.onAllNodes(hasContentDescription("More")).assertCountEquals(0)
        // A tap ticks another one.
        compose.onAllNodes(hasText("Rain jacket", substring = true)).onFirst().performClick()
        shows("2 selected")
        // Everything ticked: "Select all" goes.
        compose.onAllNodes(hasText("Select all")).assertCountEquals(0)
        // Back stops selecting.
        phoneBack()
        compose.waitForIdle()
        compose.onAllNodes(hasText("2 selected")).assertCountEquals(0)
        // ⋮ opens the sheet: the wish's name at the top, then its actions.
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        for (t in listOf("Edit", "Share", "Move to…", "Private", "I have it", "Price history")) shows(t)
    }
}

/**
 * A wishlist's ⋮ is a bottom sheet with Visibility as its own sheet; a long press on a wishlist does nothing (owner,
 * 15 November 2026). The wish form starts with the photo, then Top wish and Private, then the link.
 */
class WishlistSheetTest : NavigationBase() {
    @Test fun dotsOpenTheSheetAndVisibility() {
        compose.waitForIdle()
        // A long press opens nothing.
        compose.onAllNodes(hasText("My wishlist", substring = false)).onFirst().performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodes(hasText("Edit name")).assertCountEquals(0)
        // The ⋮: the sheet with its rows and the current Visibility under its name.
        compose.onAllNodes(hasContentDescription("More for", substring = true) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        for (t in listOf("Share", "Edit name", "Visibility", "Delete wishlist")) shows(t)
        compose.onAllNodes(hasText("Visible to")).assertCountEquals(0)
        tap("Visibility")
        compose.waitForIdle()
        for (t in listOf("Only me", "My friends", "Friends + anyone with the link")) shows(t)
    }

    /** The form's order (the open form's blinking cursor never lets this pretend phone settle, so it is read from the code). */
    @Test fun formStartsWithPhotoThenTiles() {
        val src = java.io.File("src/main/java/com/wishlist/app/WishForm.kt").readText()
        val photo = src.indexOf("ImageBlock(image,")
        val top = src.indexOf("tr(\"Top wish\"), tr(\"Givers see it first\")")
        val link = src.indexOf("label = { Text(tr(\"Product link\")) }")
        val name = src.indexOf("tr(\"Name*\")")
        org.junit.Assert.assertTrue("photo, Top wish and Private, link, name", photo in 0 until top && top < link && link < name)
    }
}

/** Taking a top wish off on Home's Top wishes page: at once with Undo, no question (owner, 12 November 2026). */
class TopWishTest : NavigationBase() {
    @Test fun noQuestionWhenUnstarring() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.addMine(app, Wish(7, "https://example.com/t", "Headphones", "", "", 99.0, null, "EUR", "Tech", star = true)) }
        tap("Wish") // the tile says "Wish" with one wish
        tap("Top wishes ★")
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Remove top wish") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("Remove from top wishes?")).assertCountEquals(0)
        org.junit.Assert.assertFalse(Repo.myWishes.first { it.id == 7L }.star)
        shows("Removed from top wishes")
    }
}

/** All your wishes behaves like a list; a wish on two lists asks "From which list?" (owner, 12 November 2026). */
class AllWishesTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private val lamp = Wish(31, "https://example.com/l", "Desk lamp", "", "", 40.0, null, "EUR", "Home")

    /** A lamp on two lists and a mug on one; then All your wishes. */
    private fun twoLists() {
        compose.runOnUiThread {
            Repo.addMine(app, lamp)
            Repo.addMine(app, lamp.copy(id = 32, title = "Mug"))
            val other = Repo.createMine(app, "Birthday", "")
            Repo.copyInto(app, other, listOf(lamp))
        }
        tap("Wishes")
        compose.waitForIdle()
    }

    @Test fun theSheetSaysItIsOnTwoLists() {
        twoLists()
        // The lamp is second (newest first).
        compose.onAllNodes(hasContentDescription("More") and hasClickAction())[1].performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("On 2 lists", substring = true)).onFirst().assertExists()
    }

    @Test fun deleteAsksFromWhichListAndSaysWhy() {
        twoLists()
        compose.onAllNodes(hasText("Desk lamp", substring = true)).onFirst().performTouchInput { longClick() }
        shows("1 selected")
        tap("Delete")
        shows("Delete from which list?")
        compose.onAllNodes(hasText("This wish is on 2 of your wishlists", substring = true)).onFirst().assertExists()
        tap("All lists")
        compose.waitForIdle()
        org.junit.Assert.assertEquals(emptyList<String>(), Repo.listsWith(app, 31))
        // The other wish is still there.
        org.junit.Assert.assertEquals(1, Repo.listsWith(app, 32).size)
    }
}

/** "I have it" asks once where the wish goes (owner, 12 November 2026: it replaces Owned and Received). */
class HaveItTest : NavigationBase() {
    @Test fun movesToReceivedGifts() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.addMine(app, Wish(41, "https://example.com/v", "Vase", "", "", 25.0, null, "EUR", "Home")) }
        send(open(MainActivity.OPEN_NOTE, "m:" + Repo.mineIds.first()))
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        tap("I have it")
        shows("You have it!")
        tap("Move it to Received gifts")
        compose.waitForIdle()
        org.junit.Assert.assertFalse(Repo.myWishes.any { it.id == 41L })
        org.junit.Assert.assertTrue(Repo.myBin.any { it.id == 41L && it.recv > 0L })
        shows("Moved to Received gifts")
    }
}

