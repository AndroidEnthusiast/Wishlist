package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Home's Total value and a list's total (Totals.kt). Amounts are shown in the phone's style; here UK English. */
class TotalsTest {
    @Test fun homeTotalCountsWhatIsStillWished() = withLocale(Locale.UK) {
        val ws = listOf(
            wish(1, price = 10.0), wish(2, price = 5.5, qty = 2), wish(3, price = 100.0, got = true),
            wish(4, price = 50.0, bin = 1L), wish(5)
        )
        assertEquals("€21.00", homeTotal(ws))
    }

    @Test fun homeTotalOtherCurrencies() = withLocale(Locale.UK) {
        assertEquals("€30.00+", homeTotal(listOf(wish(1, price = 30.0), wish(2, price = 20.0, cur = "USD"))))
        assertEquals("US$40.00+", homeTotal(listOf(wish(1, price = 30.0), wish(2, price = 40.0, cur = "USD"))))
    }

    @Test fun homeTotalWithoutPrices() = withLocale(Locale.UK) {
        assertEquals("£0.00", homeTotal(listOf(wish(1))))
    }

    @Test fun listTotal() = withLocale(Locale.UK) {
        assertNull(totalValue(listOf(wish(1))))
        assertEquals("€15.50", totalValue(listOf(wish(1, price = 10.0), wish(2, price = 5.5))))
        assertEquals("€20.00 + US$5.00", totalValue(listOf(wish(1, price = 20.0), wish(2, price = 5.0, cur = "USD"))))
    }

    @Test fun wholeAmounts() = withLocale(Locale.UK) {
        assertEquals("€1,246", wholeMoney(1245.5, "EUR"))
        assertEquals("1245", wholeMoney(1245.0, "not a currency"))
        assertEquals("12.50", money(12.5, "not a currency"))
    }
}
