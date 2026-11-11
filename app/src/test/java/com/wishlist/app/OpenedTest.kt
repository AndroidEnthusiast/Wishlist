package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "The app was opened" (Opened.kt, ROADMAP S4.3): once a day, and which lists are reported. */
class OpenedTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun onceADay() {
        assertTrue(Opened.due(0L, 5 * day))
        assertFalse(Opened.due(5 * day, 5 * day + day - 1))
        assertTrue(Opened.due(5 * day, 6 * day))
        // A clock that went back doesn't stop the reports.
        assertTrue(Opened.due(9 * day, 5 * day))
    }

    @Test fun onlyListsThatAreOnlineAndMadeHere() {
        val mine = listOf(
            MineData("a", "Online", "", emptyList(), emptyList(), "CODE0001", "tok1", emptySet()),
            MineData("b", "Only me", "", emptyList(), emptyList(), null, null, emptySet())
        )
        val collab = listOf(SharedList("COLLAB01", "key1", "Made here", owner = "own1"), SharedList("COLLAB02", "key2", "Joined"))
        val a = Opened.lists(mine, collab)
        assertEquals(2, a.length())
        assertEquals("CODE0001", a.getJSONObject(0).getString("c"))
        assertEquals("own1", a.getJSONObject(1).getString("t"))
    }
}
