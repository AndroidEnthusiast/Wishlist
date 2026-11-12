package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** The wish's ⋮ sheet: the same rows in the same order on every list (WishSheet.kt; owner, 12 November 2026). */
class WishSheetTest {
    private val w = Wish(1, "https://example.com/a", "Teapot", "", "", 20.0, null, "EUR", "Home")
    private val none: () -> Unit = {}

    @Test fun yourOwnList() {
        assertEquals(
            listOf("edit", "share", "move", "private", "owned", "history"),
            wishSheetRows(w, WishActions(onEdit = none, onMove = none, own = true, onHistory = none))
        )
        // No link, no price history.
        assertEquals(listOf("edit", "share", "move", "private", "owned"), wishSheetRows(w, WishActions(onEdit = none, onMove = none, own = true)))
    }

    @Test fun aCollabListHasReserveFirst() {
        val free = Reserving(null) {}
        assertEquals(listOf("reserve", "edit", "share", "move"), wishSheetRows(w, WishActions(reserve = free, onEdit = none, onMove = none, shareAsIdea = true)))
        val mine = Reserving(true) {}
        assertEquals(listOf("unreserve", "edit", "share", "move"), wishSheetRows(w, WishActions(reserve = mine, onEdit = none, onMove = none)))
        // Reserved by someone else: nothing to reserve, the rest stays.
        val theirs = Reserving(false) {}
        assertEquals(listOf("edit", "share", "move"), wishSheetRows(w, WishActions(reserve = theirs, onEdit = none, onMove = none)))
    }

    @Test fun aFriendsList() {
        assertEquals(listOf("reserve", "share"), wishSheetRows(w, WishActions(reserve = Reserving(null) {}, shareAsIdea = true)))
        // Owned already: only Share.
        assertEquals(listOf("share"), wishSheetRows(w, WishActions(shareAsIdea = true)))
    }
}
