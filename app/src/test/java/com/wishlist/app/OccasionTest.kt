package com.wishlist.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The occasion rules that only the app has (the countdown line itself is in SameAnswersTest). */
class OccasionTest {
    private val nov8 = LocalDate.of(2026, 11, 8)

    @Test fun storedAndReadBack() {
        val o = Occasion("own", "Party", "2026-12-31")
        assertEquals(o, Occasion.parse(o.toJson().toString()))
        assertEquals("{\"k\":\"christmas\"}", Occasion("christmas").toJson().toString())
        assertNull(Occasion.parse(null)); assertNull(Occasion.parse("")); assertNull(Occasion.parse("not json"))
        assertNull(Occasion.fromJson(JSONObject("{\"n\":\"no kind\"}")))
        assertEquals(Occasions.NAME_MAX, Occasion.fromJson(JSONObject().put("k", "own").put("n", "x".repeat(99)))!!.name.length)
    }

    @Test fun nextDay() {
        assertEquals(LocalDate.of(2026, 12, 25), Occasions.next(Occasion("christmas"), nov8))
        assertEquals(LocalDate.of(2027, 5, 9), Occasions.next(Occasion("mother"), nov8))
        assertEquals(LocalDate.of(2027, 6, 20), Occasions.next(Occasion("father"), nov8))
        assertEquals(LocalDate.of(2027, 2, 28), Occasions.next(Occasion("birthday", date = "02-29"), nov8))
        assertNull(Occasions.next(Occasion("own", "Party", "not a date"), nov8))
    }

    @Test fun ownDateMovesToTheNextTime() {
        assertEquals("2026-12-31", Occasions.ownDate("12-31", nov8))
        assertEquals("2026-11-08", Occasions.ownDate("11-08", nov8))
        assertEquals("2027-11-07", Occasions.ownDate("11-07", nov8))
        assertEquals("", Occasions.ownDate("13-01", nov8))
    }

    @Test fun pickerAndComplete() {
        assertEquals("07-14", Occasions.pickerValue(Occasion("birthday", date = "07-14")))
        assertEquals("12-31", Occasions.pickerValue(Occasion("own", "x", "2026-12-31")))
        assertEquals("", Occasions.pickerValue(null))
        assertTrue(Occasions.complete(null))
        assertTrue(Occasions.complete(Occasion("christmas")))
        assertFalse(Occasions.complete(Occasion("birthday")))
        assertTrue(Occasions.complete(Occasion("birthday", date = "07-14")))
        assertFalse(Occasions.complete(Occasion("own", "", "2099-01-01")))
        assertFalse(Occasions.complete(Occasion("own", "Party", "2001-01-01")))
        assertTrue(Occasions.complete(Occasion("own", "Party", "2099-01-01")))
    }

    @Test fun labels() {
        assertEquals("Custom", Occasions.choiceLabel("own"))
        assertEquals("Valentine’s Day", Occasions.choiceLabel("valentine"))
        assertEquals("Occasion", Occasions.label(Occasion("own")))
        assertTrue(Occasions.asksDate("anniversary")); assertFalse(Occasions.asksDate("christmas"))
    }
}
