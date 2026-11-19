package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of reactions (added for the rebuild, 19 November 2026): under a wish with four kinds (yours highlighted,
 * the fourth in a "+1" circle), the compact size, a wish without reactions ("+ React"), one where you may not react,
 * light and dark, and the emoji picker. Pictures in app/build/outputs/roborazzi/reaction_*.png; only with -Pwl.screens.
 */
class ReactionScreenshotTest : NavigationBase() {
    private fun settle() = repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); captureScreenRoboImage("build/outputs/roborazzi/reaction_$name.png") }
    private fun w(id: Long) = Wish(id, "", "Wish $id", "", "", null, null, "EUR", "")

    private fun bars(mode: String) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            Reactions.map["RXCODE"] = listOf(
                React(1, "😍", "Magdalena", false, 1), React(1, "😍", "", true, 2), React(1, "🎉", "Jorai", false, 3),
                React(1, "👍", "Noor", false, 4), React(1, "🔥", "Sam", false, 5), React(2, "😂", "Noor", false, 6)
            )
            compose.activity.setContent { LookTheme(lookFor("quiet", "berry", mode)) {
                Column(Modifier.fillMaxSize().lookPage().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompositionLocalProvider(LocalWishDeco provides WishDeco("RXCODE", canReact = true)) {
                        ReactionBar(w(1))
                        ReactionBar(w(1), compact = true)
                        ReactionBar(w(2))
                        ReactionBar(w(3))
                    }
                    CompositionLocalProvider(LocalWishDeco provides WishDeco("RXCODE", canReact = false)) {
                        ReactionBar(w(1))
                        ReactionBar(w(3))
                    }
                }
            } }
        }
        settle()
    }

    @Test fun bars() { bars("light"); shot("bars") }
    @Test fun barsDark() { bars("dark"); shot("bars_dark") }

    @Test fun picker() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "berry", "light")) { EmojiPickerDialog(onPick = {}, onClose = {}) } } }
        shot("picker")
    }
}
