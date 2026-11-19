package com.wishlist.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner, 18 November 2026 (docs/decisions/settings-profile.md): Profile as short rows, the friend code only on the
 * Friends tab, backups on "Keep your lists safe"; Home's three blocks and My wishlists | Shared, each with a switch.
 */
class ProfileHomeTest : NavigationBase() {
    private fun src(f: String) = File("src/main/java/com/wishlist/app/$f").readText()

    @Test fun profileIsShortRows() {
        fromSheet("Profile")
        for (r in listOf("Name", "Birthday", "About me", "Keep your lists safe")) shows(r)
        compose.onAllNodes(hasText("Your friend code")).assertCountEquals(0)
        compose.onAllNodes(hasText("Wishlists your friends can see")).assertCountEquals(0)
        tap("Keep your lists safe")
        shows("Show and save your key"); shows("Backup file")
        tap("Backup file")
        shows("Export everything")
    }

    @Test fun homeShowsTheBlocksAndTheTwoPages() {
        tabOn("Home")
        shows("Wishes"); shows("Wishlist"); shows("Total value")
        shows("My wishlists"); shows("Shared")
        tap("Shared")
        shows("Nothing shared yet. Tap + to make a wishlist with others or start a wishlist group.")
    }

    @Test fun bothHomePartsHaveASwitchAndAreOnByDefault() {
        val show = src("Show.kt")
        assertTrue(show.contains("\"home_summary\" to true") && show.contains("\"home_swipe\" to true"))
        val st = src("Settings.kt") + src("SettingsPages.kt")
        assertTrue(st.contains("Show[\"home_summary\"]") && st.contains("Show[\"home_swipe\"]"))
        assertFalse(st.contains("tr(\"Backup\")"))
        assertTrue((File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.startsWith("Home") && it.name.endsWith(".kt") || it.name == "NewWishlist.kt" }.sortedBy { it.name }.joinToString("\n") { it.readText() }).contains("if (!Show[\"home_summary\"])"))
        // The three numbers sit on a card in every style (owner, 18 November 2026: "it looks too flat").
        assertTrue((File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.startsWith("Home") && it.name.endsWith(".kt") || it.name == "NewWishlist.kt" }.sortedBy { it.name }.joinToString("\n") { it.readText() }).contains(".padding(start = 12.dp).lookCard(look, 5L, look.cardShape(5L, small = true))"))
    }
}
