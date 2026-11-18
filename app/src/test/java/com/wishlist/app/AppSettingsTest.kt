package com.wishlist.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * App settings redone (owner, 18 November 2026; docs/decisions/screens.md, last section): a short list of rows that
 * open small pages; no theme in it (the Theme screen is its own route from the profile sheet); price alerts with the
 * other notifications; the check interval as one row with a small list; the removed switches are gone with their code.
 */
class AppSettingsTest : NavigationBase() {
    @Test fun shortListOfRowsOpeningPages() {
        fromSheet("App settings")
        for (row in listOf("Notifications", "Prices", "Display", "Backup", "About")) shows(row)
        compose.onAllNodes(hasText("Change theme")).assertCountEquals(0)
        compose.onAllNodes(hasText("Price change on pictures")).assertCountEquals(0)
        tap("Prices")
        shows("Check prices"); shows("Currency")
        tap("Check prices")
        tap("Every 12 hours")
        assertEquals(12, Repo.intervalHours)
        phoneBack()
        shows("Display")
    }

    @Test fun priceAlertsAreWithTheNotifications() {
        fromSheet("App settings")
        tap("Notifications")
        for (s in listOf("Friend requests", "New messages", "Price drops", "Price increases")) shows(s)
        phoneBack()
        shows("Backup")
        phoneBack()
        tabOn("Home")
    }

    @Test fun themeIsItsOwnScreenFromTheSheet() {
        fromSheet("Theme")
        shows("Appearance"); shows("Black")
        phoneBack()
        tabOn("Home")
        compose.onAllNodes(hasText("App settings")).assertCountEquals(0)
    }

    @Test fun removedSwitchesLeftNoCode() {
        val main = File("src/main/java/com/wishlist/app")
        val all = main.listFiles()!!.filter { it.name.endsWith(".kt") }.joinToString("\n") { it.readText() }
        assertFalse(all.contains("show_badge"))
        assertFalse(all.contains("show_desc"))
        assertFalse(all.contains("ThemeOpen"))
    }
}
