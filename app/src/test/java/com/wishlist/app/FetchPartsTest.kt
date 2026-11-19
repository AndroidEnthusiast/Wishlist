package com.wishlist.app

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The risks the owner chose for the price reading rebuild (19 November 2026), in plain words; FetchSameTest checks
 * everything together against the record made before the rebuild, SameAnswersTest that the app and the web pages agree.
 */
class FetchPartsTest {
    private fun read(html: String, url: String, status: Int = 200) = parsePage(Jsoup.parse(html, url), java.net.URI(url).host, status)

    @Test fun theAppAndTheWebPagesShareOneListOfAnswers() {
        // The same file is read by tests/shared/test.js for web/parse.js (Rule 2).
        val f = listOf("../tests/shared/same-answers.json", "tests/shared/same-answers.json").map(::File).first { it.exists() }
        assertTrue(f.readText().contains("\"pages\""))
    }

    @Test fun linksAndCurrenciesStayTheSame() {
        assertEquals("https://www.amazon.com/dp/B0ABC12345", cleanUrl("https://www.amazon.com/gp/product/B0ABC12345?psc=1"))
        assertEquals(1299.0, "1.299,00".toNum()!!, 0.0)
        assertEquals(1299.0, "1,299.00".toNum()!!, 0.0)
        assertEquals("GBP", currencyOf("12", "shop.co.uk"))
        assertEquals("EUR", currencyOf("€12", "shop.com"))
    }

    @Test fun aBlockedPageNeverGivesAPrice() {
        val m = read("<html><head><title>Robot Check</title><meta property='og:price:amount' content='9.99'></head></html>", "https://www.amazon.nl/dp/B01")!!
        assertTrue("recognised, so the hidden browser is tried", m.blocked)
        assertNull("and no price is taken from it (Rule 8)", m.price)
    }

    @Test fun aPageThatIsGoneGivesNoPriceFromSuggestions() {
        val m = read("<html><head><title>Not found</title></head><body><div class='related'>€ 12,99</div></body></html>", "https://shop.example/x", 404)
        assertNull(m?.price)
    }
}
