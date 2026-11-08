package com.wishlist.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A wish as it is stored and sent (CLAUDE.md rule 1). Before the data moves to a real database (ROADMAP Stage 2), this
 * pins down exactly what the stored text looks like and how old or odd text is read.
 */
class WishJsonTest {
    private val full = Wish(
        7, "https://shop.example/p", "Lamp", "Desk lamp", "https://shop.example/i.jpg", 29.99, 39.99, "EUR", "Home",
        got = true, star = true, note = "white", size = "M", by = "Sam", bin = 123L, binBy = "Alex", checked = 456L,
        priv = true, qty = 2, exact = "exact", recv = 789L
    )

    @Test fun everyFieldSurvives() {
        assertEquals(full, wishFromJson(JSONObject(wishToJson(full).toString())))
    }

    @Test fun onlySetFieldsAreWritten() {
        val o = wishToJson(wish(1, price = null))
        assertEquals(
            setOf("id", "url", "title", "desc", "image", "price", "old", "cur", "cat", "got"),
            o.keys().asSequence().toSet()
        )
        assertEquals(JSONObject.NULL, o.get("price"))
        val keys = wishToJson(full).keys().asSequence().toSet()
        for (k in listOf("star", "note", "size", "by", "bin", "binBy", "chk", "priv", "qty", "exact", "recv")) assert(k in keys) { k }
    }

    @Test fun oldOrOddTextIsRead() {
        val w = wishFromJson(JSONObject("{\"id\":3,\"url\":\"\",\"title\":\"Old\",\"price\":null,\"old\":null}"))
        assertEquals("USD", w.currency)
        assertEquals(1, w.qty); assertEquals("", w.exact); assertFalse(w.got); assertFalse(w.priv)
        val odd = wishFromJson(JSONObject("{\"id\":4,\"url\":\"\",\"title\":\"x\",\"price\":5,\"old\":null,\"qty\":500,\"exact\":\"maybe\"}"))
        assertEquals(99, odd.qty); assertEquals("", odd.exact); assertEquals(5.0, odd.price!!, 0.0)
        assertEquals(1, wishFromJson(JSONObject("{\"id\":5,\"url\":\"\",\"title\":\"x\",\"price\":null,\"old\":null,\"qty\":-3}")).qty)
    }
}
