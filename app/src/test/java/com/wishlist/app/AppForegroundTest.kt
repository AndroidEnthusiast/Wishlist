package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** AppForeground: the app is in front while any of its windows is started (bug of 8 November 2026). */
class AppForegroundTest {
    @Test fun sharingIntoTheAppKeepsItInFront() {
        val told = ArrayList<Boolean>()
        val fg = AppForeground { told += it }
        fg.start()                 // the app opens
        // Share from a Friend's list into Wishlist: the new window starts, then the old one stops.
        fg.start()
        fg.stop()
        assertEquals(listOf(true), told)
        // Home button: the last window stops.
        fg.stop()
        assertEquals(listOf(true, false), told)
        // Back in front.
        fg.start()
        assertEquals(listOf(true, false, true), told)
    }

    @Test fun anExtraStopNeverGoesBelowZero() {
        val told = ArrayList<Boolean>()
        val fg = AppForeground { told += it }
        fg.stop()
        fg.start()
        assertEquals(listOf(true), told)
    }
}
