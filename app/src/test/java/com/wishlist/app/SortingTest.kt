package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sorting and searching a list (Sorting.kt; web/edit.js has the same rules). */
class SortingTest {
    private val a = wish(1, "Lamp", price = 30.0)
    private val b = wish(2, "book", price = null, star = true)
    private val c = wish(3, "Apron", price = 10.0, got = true)
    private val d = wish(4, "Mug", price = 20.0)
    private val all = listOf(a, b, c, d)

    private fun ids(mode: String) = sortWishes(all, mode).map { it.id }

    @Test fun orders() {
        assertEquals(listOf(4L, 2L, 1L, 3L), ids("new"))
        assertEquals(listOf(2L, 1L, 4L, 3L), ids("star"))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids("low"))
        assertEquals(listOf(1L, 4L, 2L, 3L), ids("high"))
        // "az" was removed from the menu (2.127) but a stored one still sorts.
        assertEquals(listOf(2L, 1L, 4L, 3L), ids("az"))
        assertEquals(ids("new"), ids("something old"))
    }

    @Test fun ownedAlwaysLast() {
        assertEquals(3L, sortWishes(all, "low").last().id)
    }

    @Test fun shortNames() {
        assertEquals("Price: low", sortShortName("low"))
        assertEquals("Newest", sortShortName("az"))
    }

    @Test fun search() {
        val w = wish(9, "Red Wool Scarf", desc = "Warm", note = "size M", url = "https://www.zalando.nl/x", cat = "Fashion")
        assertTrue(matchesQuery(w, ""))
        assertTrue(matchesQuery(w, "  scarf  red "))
        assertTrue(matchesQuery(w, "zalando fashion"))
        assertTrue(matchesQuery(w, "warm size"))
        assertFalse(matchesQuery(w, "scarf blue"))
        assertTrue(matchesQuery(wish(10, url = "not a url ::"), ""))
    }
}
