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
        // No welcome, no background question and a name, so no pop-up covers the screens.
        app.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("asked_background", true).putString("sh_me", "Sam").commit()
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
        compose.runOnUiThread { NotesNav.open = true }
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

    @Test fun priceChangesBackToWhereItWasOpened() {
        // "All price changes" (a wish's price page, or a notification) from the Messages tab.
        tap("Messages")
        tabOn("Messages")
        compose.runOnUiThread { PricesNav.open = true }
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
        compose.runOnUiThread { NoteNav.key = "m:$id" }
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
