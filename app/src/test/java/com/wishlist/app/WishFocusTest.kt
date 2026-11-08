package com.wishlist.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which notifications open their list on the wish they are about (WishFocus.forNote, 2.161). */
class WishFocusTest {
    @After fun clear() { WishFocus.target = null }

    @Test fun ownAndCollabListsOpenOnTheWish() {
        WishFocus.forNote("m:list1", 42L, "price_drop")
        assertEquals(42L, WishFocus.target)
        WishFocus.target = null
        WishFocus.forNote("s:CODE", 7L, "gift_lost")
        assertEquals(7L, WishFocus.target)
    }

    @Test fun othersOpenAsBefore() {
        // A friend's list loads from the server first; "Still buying …?" news has its own focus; no wish, no focus.
        WishFocus.forNote("c:CODE", 42L, "price_drop")
        assertNull(WishFocus.target)
        WishFocus.forNote("m:list1", 42L, "ask_free")
        assertNull(WishFocus.target)
        WishFocus.forNote("m:list1", 0L, "reacts")
        assertNull(WishFocus.target)
        WishFocus.forNote("", 42L, "price")
        assertNull(WishFocus.target)
    }
}
