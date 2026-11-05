package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** 2.120 (owner): a page without a currency gets the phone's country's, else the euro. Same checks as tests/parse. */
class CurrencyTest {
    private fun with(l: Locale, f: () -> Unit) { val old = Locale.getDefault(); Locale.setDefault(l); try { f() } finally { Locale.setDefault(old) } }

    @Test fun pageSays() {
        assertEquals("USD", currencyOf("$19.99", "nike.com"))
        assertEquals("EUR", currencyOf("€ 19,99", "nike.com"))
        assertEquals("GBP", currencyOf("19.99", "shop.co.uk"))
    }

    @Test fun phoneCountry() {
        with(Locale("nl", "NL")) { assertEquals("EUR", currencyOf("19.99", "nike.com")) }
        with(Locale("en", "GB")) { assertEquals("GBP", localCurrency()) }
        with(Locale("sv", "SE")) { assertEquals("SEK", localCurrency()) }
        with(Locale("en", "US")) { assertEquals("USD", localCurrency()) }
        with(Locale("en")) { assertEquals("EUR", localCurrency()) }
        with(Locale("fr", "ZZ")) { assertEquals("EUR", localCurrency()) }
    }
}
