package com.wishlist.app

import androidx.activity.compose.setContent
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * The pickers as bottom sheets (owner, 22 November 2026; PickSheet.kt): What did you get?, Send a wish, Add friends
 * and Add to my wishlist, in both styles, light and dark and with large text, checked by the layout inspector.
 * Pictures in app/build/outputs/roborazzi/pick_*.png; only with -Pwl.screens.
 */
class PickSheetScreenshotTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun each(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark", "big")) {
            // The sheet before this one closed first (a sheet is its own window; it stayed in the next picture).
            compose.runOnUiThread { compose.activity.setContent {} }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            compose.runOnUiThread { compose.activity.setContent {
                val look = lookFor(st, "blue", if (m == "big") "light" else m)
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, if (m == "big") 1.3f else d.fontScale)
                ) { LookTheme(look) { content() } }
            } }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/pick_${name}_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }

    // Once: the checks of this file share the test phone's data, so a second add would show every wish twice.
    private fun wishes() = compose.runOnUiThread {
        if (Repo.wishes.any { it.id == 601L }) return@runOnUiThread
        Repo.add(app, Wish(601L, "https://example.com/a", "Mackinaw Wool G9 jacket in navy check", "", "", 470.0, null, "EUR", ""))
        Repo.add(app, Wish(602L, "https://example.com/b", "Board game", "", "", 39.95, null, "EUR", ""))
        Repo.add(app, Wish(603L, "https://example.com/c", "A very long wish name that goes on and on to see how it wraps in the row", "", "", null, null, "EUR", ""))
    }

    @Test fun received() { wishes(); each("received") { ReceivedDialog(Repo.openMine, onClose = {}) } }

    @Test fun sendAWish() { wishes(); each("sendwish") { WishPicker(onPick = { _, _ -> }, onClose = {}) } }

    @Test fun addFriends() {
        val friends = listOf(Person("f1", "Magdalena with a long name", 0L), Person("f2", "Sam", 0L), Person("f3", "", 0L))
        each("friends") { PickFriendsDialog(friends, start = listOf("f2"), onDone = {}, onClose = {}) }
    }

    @Test fun addToMine() {
        wishes()
        compose.runOnUiThread { Repo.createMine(app, "Birthday", ""); AddToMine.ask(Repo.wishes.first()) }
        each("addtomine") { AddToMineHost(androidx.compose.material3.SnackbarHostState()) }
    }
}
