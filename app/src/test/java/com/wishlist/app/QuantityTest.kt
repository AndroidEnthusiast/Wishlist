package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "2×" wishes: what givers may still reserve and the label they see (same wording as web/reserve.js). */
class QuantityTest {
    private val one = wish(qty = 1)
    private val three = wish(qty = 3)

    @Test fun free() {
        assertEquals(3, freeToReserve(three, null))
        assertEquals(2, freeToReserve(three, ResCount(my = 0, n = 1, full = false)))
        // Your own part counts as free for you, so you can change your number.
        assertEquals(3, freeToReserve(three, ResCount(my = 1, n = 1, full = false)))
        assertEquals(1, freeToReserve(three, ResCount(my = 1, n = 3, full = true)))
        assertEquals(0, freeToReserve(three, ResCount(my = 0, n = 3, full = true)))
        // More reserved than wanted (the owner lowered the number): never below 0.
        assertEquals(0, freeToReserve(wish(qty = 2), ResCount(my = 0, n = 3, full = true)))
    }

    @Test fun labels() {
        assertNull(reserveLabelOf(one, null, null))
        assertEquals("Reserved by you", reserveLabelOf(one, null, true))
        assertEquals("Reserved", reserveLabelOf(one, null, false))
        assertEquals("1 of 3 reserved", reserveLabelOf(three, ResCount(0, 1, false), false))
        assertEquals("You reserved 2", reserveLabelOf(three, ResCount(2, 2, false), true))
        assertEquals("Reserved", reserveLabelOf(three, ResCount(0, 3, true), false))
        // A 2× wish without counts (an older answer) falls back to the plain labels.
        assertEquals("Reserved by you", reserveLabelOf(three, null, true))
    }

    @Test fun storedCounts() {
        ResCounts.put("ABC", mapOf(1L to ResCount(0, 1, false)))
        assertEquals("1 of 3 reserved", reserveLabel("ABC", three, false))
        ResCounts.put("ABC", emptyMap())
        assertNull(ResCounts.of("ABC", 1L))
        assertEquals(3, ResCounts.free("ABC", three))
    }
}
