package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving a wish to or from a collab wishlist (MoveAcross.kt, owner 11 November 2026): what travels and what doesn't. */
class MoveAcrossTest {
    private val full = Wish(
        7L, "https://shop.example/x", "Teapot", "Blue", "https://shop.example/t.jpg", 34.95, 39.5, "EUR", "Home",
        got = true, star = true, note = "The blue one", size = "M", by = "", bin = 0L, binBy = "", checked = 0L,
        priv = true, qty = 2, exact = "similar", recv = 456L
    )

    @Test fun onACollabWishlistThereIsNoPrivateTopOwnedOrOldPrice() {
        val w = MoveAcross.forCollab(full, "Sam", listOf("Home", "Garden"))
        assertFalse(w.priv); assertFalse(w.star); assertFalse(w.got)
        assertEquals(null, w.oldPrice)
        assertEquals(0L, w.recv)
        assertEquals("Sam", w.by)
        // What the givers need stays.
        assertEquals(listOf("Teapot", "Blue", "The blue one", "M", "similar"), listOf(w.title, w.desc, w.note, w.size, w.exact))
        assertEquals(34.95, w.price!!, 0.0)
        assertEquals(2, w.qty)
        assertEquals("Home", w.category)
        assertEquals(7L, w.id)
    }

    @Test fun aCategoryTheOtherListDoesNotHaveIsLeftOut() {
        assertEquals("", MoveAcross.forCollab(full, "Sam", listOf("Garden")).category)
        assertEquals("", MoveAcross.forMine(full.copy(category = "For Mum"), listOf("Home")).category)
    }

    @Test fun onYourOwnListThereIsNoAddedByOrSharedCheckTime() {
        val collab = full.copy(by = "Jo", checked = 99L, priv = false, star = false, got = false, recv = 0L)
        val w = MoveAcross.forMine(collab, listOf("Home"))
        assertEquals("", w.by)
        assertEquals(0L, w.checked)
        assertEquals("Teapot", w.title)
        assertEquals("Home", w.category)
    }

    @Test fun aCopyGetsItsOwnId() {
        assertEquals(99L, MoveAcross.forMine(full, emptyList(), 99L).id)
        assertEquals(99L, MoveAcross.forCollab(full, "Sam", emptyList(), 99L).id)
    }

    @Test fun aWishSomeoneElseReservedStays() {
        assertTrue(MoveAcross.mayLeave(null))   // free
        assertTrue(MoveAcross.mayLeave(true))   // reserved on this phone: it just ends
        assertFalse(MoveAcross.mayLeave(false)) // someone else
    }

    @Test fun listKeys() {
        assertTrue(MoveAcross.isCollab(MoveAcross.collabKey("ABCD1234")))
        assertEquals("ABCD1234", MoveAcross.codeOf(MoveAcross.collabKey("ABCD1234")))
        assertFalse(MoveAcross.isCollab("main"))
        assertFalse(MoveAcross.isCollab("1794346703000"))
    }
}
