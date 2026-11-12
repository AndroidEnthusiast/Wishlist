package com.wishlist.app

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
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
        HubNav.page = null
        FriendsNav.reset()
        AccountNav.open = false
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
