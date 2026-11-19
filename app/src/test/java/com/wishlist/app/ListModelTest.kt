package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The open list's rebuild (owner, 19 November 2026): which wishes show, in which order, and the whole list's order after
 * a drag are plain functions now, checked here on their own.
 */
class ListModelTest {
    private fun w(id: Long, title: String, cat: String = "", price: Double? = null) = Wish(id, "", title, "", "", price, null, "EUR", cat)
    private val ws = listOf(w(1, "Lamp", "Home", 30.0), w(2, "Book", "Books", 10.0), w(3, "Mug", "Home", 5.0))

    @Test fun categoryFallsBackToAll() {
        assertEquals("Home", shownCategory("Home", listOf("Home", "Books")))
        assertEquals("All", shownCategory("Gone", listOf("Home", "Books")))
        assertEquals("All", shownCategory("All", emptyList()))
    }

    @Test fun categorySearchAndSort() {
        assertEquals(listOf(1L, 3L), shownWishes(ws, "Home", "", "new", emptyList()).map { it.id }.sorted())
        assertEquals(listOf(3L), shownWishes(ws, "All", "mug", "new", emptyList()).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), shownWishes(ws, "All", "", "low", emptyList()).map { it.id })
        // "My order": the order you dragged them into.
        assertEquals(listOf(2L, 3L, 1L), shownWishes(ws, "All", "", ListOrder.MINE, listOf(2L, 3L, 1L)).map { it.id })
    }

    @Test fun hiddenWishesKeepTheirPlaceAfterADrag() {
        // Everything on screen: the new order, then nothing else.
        assertEquals(listOf(3L, 1L, 2L), keptOrder(listOf(3L, 1L, 2L), listOf(1L, 2L, 3L), listOf(1L, 2L, 3L)))
        // Only Home on screen (1 and 3, dragged to 3, 1): Book (2) keeps its place in the middle.
        assertEquals(listOf(3L, 2L, 1L), keptOrder(listOf(3L, 1L), listOf(1L, 3L), listOf(1L, 2L, 3L)))
    }
}
