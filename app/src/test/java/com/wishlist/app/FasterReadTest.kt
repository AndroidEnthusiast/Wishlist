package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shared link fills the form faster (owner, 21 November 2026): the quick read shows first, the hidden browser stops
 * as soon as the product data has a price, slow shops are remembered, and nothing typed while waiting is lost.
 */
class FasterReadTest {
    private val ld = """<html><head><script type="application/ld+json">
        {"@type":"Product","name":"Mackinaw Wool G9","offers":{"@type":"Offer","price":"470.00","priceCurrency":"EUR"}}
        </script></head><body><h1>Mackinaw</h1></body></html>"""

    @Test fun stopsAtOnceWhenTheProductDataHasAPrice() {
        assertTrue(hasProductPrice(ld, "https://shop.example/g9"))
        assertTrue(settled(0, found = true))
    }

    @Test fun withoutProductDataItWaitsForThePageToSettleAsBefore() {
        val noPrice = ld.replace(""","offers":{"@type":"Offer","price":"470.00","priceCurrency":"EUR"}""", "")
        assertFalse(hasProductPrice(noPrice, "https://shop.example/g9"))
        assertFalse(hasProductPrice("<html><h1>Hi</h1></html>", "https://shop.example/g9"))
        assertFalse(settled(0, found = false))
        assertFalse(settled(SETTLE_MS - LOOK_EVERY_MS, found = false))
        assertTrue(settled(SETTLE_MS, found = false))
    }

    @Test fun theFirstWaitIsShorterForTheForm() {
        assertEquals(8000, QUICK_WAIT_MS)
    }

    @Test fun onlyACompleteFirstReadSkipsTheHiddenBrowser() {
        assertTrue(complete(Meta("A", "", "", 10.0, "EUR")))
        assertFalse(complete(Meta("A", "", "", null, "EUR")))
        assertFalse(complete(Meta("", "", "", null, "USD", blocked = true)))
        assertFalse(complete(null))
    }

    @Test fun slowShopsAreRememberedByAddressAndNeverGrowWithoutEnd() {
        assertEquals("bol.com", shopHost("https://www.bol.com/nl/p/x/123/"))
        val s = SlowShops.added(emptySet(), "bol.com")
        assertEquals(setOf("bol.com"), s)
        assertEquals(s, SlowShops.added(s, "bol.com"))
        assertEquals(s, SlowShops.added(s, ""))
        val full = (1..100).map { "shop$it.com" }.toSet()
        assertEquals(setOf("new.com"), SlowShops.added(full, "new.com"))
    }

    @Test fun whatWasTypedWhileWaitingIsKept() {
        // Untouched since the quick read: the hidden browser's better value replaces it.
        assertEquals("Mackinaw Wool G9", keepTyped("Mackinaw", "Mackinaw", "Mackinaw Wool G9"))
        // Typed over: kept.
        assertEquals("My jacket", keepTyped("My jacket", "Mackinaw", "Mackinaw Wool G9"))
        // Emptied: filled again.
        assertEquals("Mackinaw Wool G9", keepTyped("", "Mackinaw", "Mackinaw Wool G9"))
        // The second read found nothing: what is there stays.
        assertEquals("Mackinaw", keepTyped("Mackinaw", "Mackinaw", ""))
    }
}
