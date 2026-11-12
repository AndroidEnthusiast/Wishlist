package com.wishlist.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A report's note needs at least 5 characters, spaces don't count (owner, 12 November 2026; Safety.kt). */
class SafetyTest {
    @Test fun aNoteNeedsFiveRealCharacters() {
        assertFalse(Safety.noteOk(""))
        assertFalse(Safety.noteOk("     "))
        assertFalse(Safety.noteOk(" a b c d "))
        assertTrue(Safety.noteOk("a b c d e"))
        assertTrue(Safety.noteOk("Spams me"))
        assertFalse(Safety.noteOk("x".repeat(2001)))
    }
}
