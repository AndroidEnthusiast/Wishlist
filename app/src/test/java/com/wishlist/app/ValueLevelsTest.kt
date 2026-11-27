package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Value levels (owner, 27 November 2026): Rising from 1,000, Main character 2,500, Big wish energy 5,000, Iconic
 * 10,000; the same numbers in currencies close to the euro, converted for far-off ones; only on your own Total value.
 */
class ValueLevelsTest {
    private fun w(price: Double, cur: String = "EUR", got: Boolean = false, qty: Int = 1) =
        Wish(System.nanoTime(), "", "x", "", "", price, null, cur, "", got = got, qty = qty)

    @Test fun theFourLevelsStartAtTheOwnersAmounts() {
        assertNull(ValueLevels.of(999.99, "EUR"))
        assertEquals(ValueLevel.RISING, ValueLevels.of(1000.0, "EUR"))
        assertEquals(ValueLevel.MAIN, ValueLevels.of(2500.0, "EUR"))
        assertEquals(ValueLevel.ENERGY, ValueLevels.of(9999.0, "EUR"))
        assertEquals(ValueLevel.ICONIC, ValueLevels.of(10000.0, "EUR"))
        assertEquals(listOf("Rising", "Main character", "Big wish energy", "Iconic"), ValueLevel.entries.map { it.label })
    }

    @Test fun otherCurrencies() {
        assertEquals("the same numbers close to the euro", ValueLevel.RISING, ValueLevels.of(1200.0, "GBP"))
        assertEquals(ValueLevel.MAIN, ValueLevels.of(3000.0, "usd"))
        assertNull("¥1,000 is about €6", ValueLevels.of(1000.0, "JPY"))
        assertEquals("converted when far off", ValueLevel.RISING, ValueLevels.of(170_000.0, "JPY"))
        assertEquals(ValueLevel.RISING, ValueLevels.of(12_000.0, "SEK"))
        assertNull("a currency the app doesn't know: no level", ValueLevels.of(1_000_000.0, "XYZ"))
    }

    @Test fun theSameAmountsAsTheTiles() {
        // Home: only what is still wished for, a 2x wish twice, the biggest currency.
        assertEquals(ValueLevel.RISING, ValueLevels.ofHome(listOf(w(600.0, qty = 2))))
        assertNull("owned wishes don't count on Home", ValueLevels.ofHome(listOf(w(5000.0, got = true))))
        assertEquals("the biggest currency", ValueLevel.MAIN, ValueLevels.ofHome(listOf(w(2600.0), w(40.0, "USD"))))
        // A list: as its summary adds it up.
        assertEquals(ValueLevel.ICONIC, ValueLevels.ofList(listOf(w(6000.0), w(4000.0))))
        assertNull(ValueLevels.ofList(emptyList()))
    }

    @Test fun onlyOnYourOwnTotalValueAndOffInSettings() {
        val dir = File("src/main/java/com/wishlist/app")
        val users = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name != "ValueLevelViews.kt" && it.readText().contains("ValueLevelTag(") }.map { it.name }.sorted()
        assertEquals("only Home's summary and your own list's summary", listOf("CoverViews.kt", "HomeHero.kt"), users)
        assertTrue(File(dir, "ListScreen.kt").readText().contains("val counts = !inShared && Show[\"show_summary\"]"))
        assertTrue(File(dir, "Settings.kt").readText().contains("SettingSwitch(tr(\"Value levels\")"))
        assertTrue("the web never shows them", File("../web").walk().filter { it.isFile }.none { it.readText().contains("Big wish energy") })
    }
}
