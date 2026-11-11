package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first lines of a "Price is wrong?" file (PriceReport.header, ROADMAP R4). */
class PriceReportTest {
    private val w = wish(7, price = 89.95, url = "https://shop.example/jacket?size=m")

    @Test fun itSaysWhatWasAskedAndWhatWasRead() {
        val h = PriceReport.header(w, "plain request", 200, Meta("Jacket", "", "", 79.95, "EUR", product = true, currencySure = true), "2.182", "nl-NL,nl;q=0.9,en;q=0.8")
        assertTrue(h.startsWith("<!-- Wishlist price report\n"))
        assertTrue(h.contains("link: https://shop.example/jacket?size=m\n"))
        assertTrue(h.contains("on the wish: 89.95 EUR\n"))
        assertTrue(h.contains("read from this page: 79.95 EUR (the page named the currency)\n"))
        assertTrue(h.contains("how: plain request, status 200, languages asked: nl-NL"))
        assertTrue(h.contains("app: 2.182\n"))
        assertTrue(h.endsWith("-->\n"))
    }

    @Test fun aPageWithoutAPriceOrABotCheckIsNamed() {
        assertTrue(PriceReport.header(w, "hidden browser", 200, Meta("Jacket", "", "", null, "EUR"), "", "").contains("read from this page: no price, not seen as a product page"))
        assertTrue(PriceReport.header(w, "plain request", 403, Meta("", "", "", null, "USD", true), "", "").contains("blocked"))
        assertTrue(PriceReport.header(w, "plain request", 0, null, "", "").contains("nothing"))
    }

    @Test fun aLinkCannotEndTheCommentEarly() {
        val h = PriceReport.header(w.copy(url = "https://shop.example/a-->b<script>"), "plain request", 200, null, "", "")
        // One comment only: its end is the last thing in the header.
        assertEquals(h.length - "-->\n".length, h.indexOf("-->"))
    }
}
