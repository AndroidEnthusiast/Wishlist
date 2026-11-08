package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Price history (ChartMath.kt): what is stored and what the graph and "−12%" show. */
class ChartMathTest {
    @Test fun samePriceIsNotStoredTwice() {
        val h = appendPoint(appendPoint(emptyList(), 100, 9.99), 200, 9.99)
        assertEquals(1, h.size)
        assertEquals(2, appendPoint(h, 300, 8.99).size)
    }

    @Test fun timeNeverRunsBackwards() {
        val h = appendPoint(appendPoint(emptyList(), 500, 10.0), 100, 12.0)
        assertEquals(500L, h.last().t)
    }

    @Test fun oldPointsDropButTheFirstStays() {
        var h = emptyList<PricePoint>()
        for (i in 0 until 10) h = appendPoint(h, i.toLong(), i.toDouble(), limit = 4)
        assertEquals(listOf(0.0, 7.0, 8.0, 9.0), h.map { it.p })
    }

    @Test fun priceAtAMoment() {
        val h = listOf(PricePoint(100, 10.0), PricePoint(200, 8.0))
        assertEquals(10.0, priceAt(h, 50), 0.0)
        assertEquals(10.0, priceAt(h, 150), 0.0)
        assertEquals(8.0, priceAt(h, 250), 0.0)
    }

    @Test fun change() {
        assertNull(changeSince(listOf(PricePoint(1, 10.0))))
        assertNull(changeSince(listOf(PricePoint(1, 10.0), PricePoint(2, 10.0))))
        val (amount, pct) = changeSince(listOf(PricePoint(1, 50.0), PricePoint(2, 40.0)))!!
        assertEquals(-10.0, amount, 1e-9); assertEquals(-20.0, pct, 1e-9)
    }

    @Test fun range() {
        val r = chartRange(listOf(PricePoint(1000, 10.0)), 1000)
        assertEquals(1000L + 86400L, r.t1)
        assertEquals(9.0, r.yMin, 1e-9); assertEquals(11.0, r.yMax, 1e-9)
    }
}
