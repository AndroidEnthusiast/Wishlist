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
 * a list's settings (2.156). Everything else (Home, the tabs, a list, a friend's list, groups, gift exchanges) still
 * lives on [MAIN] with its switches, and moves over one screen at a time. Each move adds its name here.
 */
object Route {
    /** Home, the tabs and the lists: the screens that haven't moved yet. Always at the bottom of the back stack. */
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

    /** The routes that open on top of [MAIN] (from the profile sheet, the bell, a list or a notification). */
    val onTop = listOf(PROFILE, SETTINGS, BIN, NOTES, RECEIVED, RESERVED, PRICES, LIST_SETTINGS)
}

/**
 * Opens one of [Route.onTop] over the screen on [Route.MAIN]: Back returns to that screen, just as you left it. Only
 * one of them is open at a time (as before: Profile, then Bin from the sheet, then Back goes to where you started), and
 * opening the one already on screen does nothing.
 */
fun NavController.openOnTop(route: String) {
    navigate(route) {
        popUpTo(Route.MAIN)
        launchSingleTop = true
    }
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
