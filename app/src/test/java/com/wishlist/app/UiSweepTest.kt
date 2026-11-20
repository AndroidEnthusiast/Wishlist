package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The UI sweep of 20 November 2026 (owner: "fix … generally any other poorly implemented UI"): each thing found has a
 * check here, so it can't quietly come back.
 */
class UiSweepTest : NavigationBase() {
    private fun src(f: String) = File("src/main/java/com/wishlist/app/$f").readText()

    /** "Send a wish" with no wishes said nothing: now it says so and how to add one. */
    @Test fun sendAWishWithNoWishesSaysSo() {
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "blue", "light")) { WishPicker(onPick = { _, _ -> }, onClose = {}) } } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("You have no wishes to send yet", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    /** Friends: "Couldn't reach the server" now has Try again. */
    @Test fun friendsOfflineHasTryAgain() {
        val tab = src("FriendsTab.kt")
        val at = tab.indexOf("if (problem && s != null) item {")
        assertTrue(at > 0 && tab.substring(at, at + 500).contains("Try again"))
    }

    /** Large phone text: Home's numbers card labels and shop names shrink a little to fit, never cut or broken. */
    @Test fun largeTextShrinksToFit() {
        assertTrue(src("HomeHero.kt").contains("FitText(label, MaterialTheme.typography.bodySmall, look.cardMuted(5L))"))
        assertTrue(src("ShopsScreen.kt").contains("FitText(s.name, MaterialTheme.typography.labelMedium"))
    }

    /** The add-shop tile follows the theme (it was light in dark mode; shop logos keep their light tile on purpose). */
    @Test fun addShopTileFollowsTheTheme() {
        val shops = src("ShopsScreen.kt")
        val tile = shops.substring(shops.indexOf("if (s == null) {"), shops.indexOf("tr(\"Add shop\")"))
        assertTrue(tile.contains("MaterialTheme.colorScheme.surfaceVariant"))
        assertFalse(tile.contains("Color(0xFF"))
    }

    /** Quiet's switch shows its track when off in dark (only the knob showed on a sheet). */
    @Test fun darkSwitchTrackShows() {
        assertTrue(src("QuietParts.kt").contains("else if (look.dark) look.scheme.onSurface.copy(alpha = 0.34f) else look.line"))
    }

    /** "I have it" is an action now: no switch unless the wish was marked Owned before. */
    @Test fun haveItIsNoSwitch() {
        assertTrue(src("WishSheet.kt").contains("checked = if (w.got) true else null"))
    }
}
