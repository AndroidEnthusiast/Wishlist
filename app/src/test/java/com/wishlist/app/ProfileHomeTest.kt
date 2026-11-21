package com.wishlist.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertEquals
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

    @Test fun homeShowsTheBlocksAndTabsOnlyForKindsYouHave() {
        tabOn("Home")
        shows("Wishes"); shows("Wishlist"); shows("Total value")
        // Only Personal wishlists: no tab bar (owner, 21 November 2026).
        compose.onAllNodes(androidx.compose.ui.test.hasText("Personal")).assertCountEquals(0)
        compose.onAllNodes(androidx.compose.ui.test.hasText("Shared")).assertCountEquals(0)
        // A co-op wishlist: Personal | Co-op.
        compose.runOnUiThread { Shared.lists = listOf(SharedList("COOP1", "coopkey_coopkey_coopkey", "Gifts for Mum")) }
        compose.waitForIdle()
        shows("Personal"); shows("Co-op")
        compose.onAllNodes(androidx.compose.ui.test.hasText("Groups")).assertCountEquals(0)
        compose.onAllNodes(androidx.compose.ui.test.hasText("Exchanges")).assertCountEquals(0)
        tap("Co-op")
        shows("Gifts for Mum")
        compose.runOnUiThread { Shared.lists = emptyList() }
    }

    @Test fun theTabsAreInOrderAndOnlyWhenThereIsOne() {
        assertEquals(listOf("personal"), homeTabs(coop = false, groups = false, exchanges = false))
        assertEquals(listOf("personal", "groups", "exchanges"), homeTabs(coop = false, groups = true, exchanges = true))
        assertEquals(listOf("personal", "coop", "groups", "exchanges"), homeTabs(coop = true, groups = true, exchanges = true))
        assertEquals(listOf("Personal", "Co-op", "Groups", "Exchanges"), homeTabs(true, true, true).map { homeTabLabel(it) })
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
