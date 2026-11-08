package com.wishlist.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * The app's screens as routes (ROADMAP Stage 1, Navigation Compose). A route is a screen with its own place in the back
 * stack, so the phone's Back (and the ← in its top bar) always returns to the screen it was opened from, without the
 * on/off switches in [App] having to know about it.
 *
 * Moved so far: Profile, App settings, Bin, Notifications (2.154); Received gifts, Gifts I reserved, Price changes and
 * a list's settings (2.156); a friend's list opened from a link or code (2.157); the tabs Messages, My shops and
 * Friends (2.158); a wishlist group and Gift exchanges (2.159). Home and a list still live on [MAIN] with their
 * switches, and move last.
 *
 * The back stack is at most three deep: [MAIN], then perhaps one tab or section, then perhaps one screen on top.
 */
object Route {
    /** Home and the lists: the screens that haven't moved yet. Always at the bottom of the back stack. */
    const val MAIN = "main"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val BIN = "bin"
    const val NOTES = "notifications"
    const val RECEIVED = "received"
    const val RESERVED = "reserved"
    const val PRICES = "prices"
    /** The settings of the list open on [MAIN] (its ⚙). */
    const val LIST_SETTINGS = "list-settings"

    /** The bottom bar's tabs; Home is [MAIN]. A tab sits right on top of [MAIN], so Back on it goes Home. */
    const val MESSAGES = "messages"
    const val SHOPS = "shops"
    const val FRIENDS = "friends"
    val tabs = listOf(MESSAGES, SHOPS, FRIENDS)

    /**
     * Sections reached from Home, with the bottom bar (Home lit, as before): a wishlist group (`group/<id>`) and your
     * gift exchanges (one exchange inside it is ExchangeNav.open). Like a tab: right on top of [MAIN], Back goes Home.
     */
    const val GROUP = "group/{id}"
    fun group(id: String) = "group/" + android.net.Uri.encode(id)
    const val EXCHANGES = "exchanges"
    val sections = listOf(GROUP, EXCHANGES)

    /** A list someone else owns, opened from its link or code (not through Friends): `friend/<code>`. */
    const val FRIEND = "friend/{code}"
    fun friend(code: String) = "friend/" + android.net.Uri.encode(code)

    /** The routes that open on top of [MAIN] (from the profile sheet, the bell, a list or a notification). */
    val onTop = listOf(PROFILE, SETTINGS, BIN, NOTES, RECEIVED, RESERVED, PRICES, LIST_SETTINGS, FRIEND)
}

/**
 * Opens one of [Route.onTop] over Home, a list or a tab: Back returns to that screen, just as you left it. Only one of
 * them is open at a time (as before: Profile, then Bin from the sheet, then Back goes to where you started), and
 * opening the one already on screen does nothing.
 */
fun NavController.openOnTop(route: String) {
    // The tab or section underneath stays (Profile from Friends goes back to Friends); otherwise Home or the list.
    val base = (Route.tabs + Route.sections).firstOrNull { has(it) } ?: Route.MAIN
    navigate(route) {
        popUpTo(base)
        // A route with a value (a friend's list) is a new screen for each value; the others open once.
        launchSingleTop = '/' !in route
    }
}

/**
 * Opens a tab of the bottom bar over Home or the list (whatever was on top closes). Its place inside the tab (a
 * friend's page, a chat) lives in FriendsNav and ChatNav, so it opens where you left it (owner, 8 November 2026).
 */
fun NavController.openTab(tab: String) {
    navigate(tab) {
        popUpTo(Route.MAIN)
        // A group is a new screen for each group; a tab or Gift exchanges opens once.
        launchSingleTop = '/' !in tab
    }
}

/** Whether this route is in the back stack. */
private fun NavController.has(route: String): Boolean = try {
    getBackStackEntry(route); true
} catch (e: IllegalArgumentException) {
    false
}

/** Closes every route on top, back to the screens on [Route.MAIN] (a list, a chat or a tab is about to open there). */
fun NavController.backToMain() {
    popBackStack(Route.MAIN, inclusive = false)
}

/** A route's screen fills the window and makes room for the keyboard (the main route does the same itself). */
@Composable
fun RouteBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().imePadding()) { content() }
}
