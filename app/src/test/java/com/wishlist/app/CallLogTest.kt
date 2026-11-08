package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The server call counter (Calls.kt, ROADMAP S3.4). */
class CallLogTest {
    private val d = LocalDate.of(2026, 11, 8)

    @Test fun countsPerDayAndKind() {
        val l = CallLog()
        l.add(d, "wl_get_list"); l.add(d, "wl_get_list"); l.add(d, "wl_chat_list")
        l.add(d.minusDays(1), "wl_get_list")
        assertEquals(mapOf("wl_chat_list" to 1, "wl_get_list" to 2), l.day(d))
        assertEquals(3, l.total(d))
        assertEquals(1, l.total(d.minusDays(1)))
        assertEquals(0, l.total(d.plusDays(1)))
    }

    @Test fun keepsOnlyTheLastSevenDays() {
        val l = CallLog()
        for (i in 0 until 10) l.add(d.plusDays(i.toLong()), "x")
        assertEquals(0, l.total(d.plusDays(2)))
        assertEquals(1, l.total(d.plusDays(3)))
        assertEquals(1, l.total(d.plusDays(9)))
    }

    @Test fun survivesSavingAndBrokenText() {
        val l = CallLog()
        l.add(d, "photo upload", 4); l.add(d.minusDays(1), "wl_sync_list")
        val back = CallLog.fromJson(l.toJson())
        assertEquals(l.day(d), back.day(d))
        assertEquals(1, back.total(d.minusDays(1)))
        assertTrue(CallLog.fromJson(null).day(d).isEmpty())
        assertTrue(CallLog.fromJson("""{"not a day":{"x":1},"2026-11-08":{"y":-3}}""").day(d) == mapOf("y" to 0))
    }
}
