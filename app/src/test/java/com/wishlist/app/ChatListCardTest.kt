package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

/**
 * A whole wishlist sent in chat (owner, 24 November 2026: the chat's +): the card carries the list's friends' code,
 * name, picture and count; older apps show a plain line; only http(s) pictures; private and binned wishes don't count;
 * an "Only me" list asks to become visible before it is sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatListCardTest {
    private fun wish(id: Long, priv: Boolean = false, bin: Long = 0L) =
        Wish(id, "", "W$id", "", "", null, null, "EUR", "", priv = priv, bin = bin)

    private fun list(code: String? = "FRIEND01", name: String = "Birthday") =
        MineData("l1", name, "", emptyList(), listOf(wish(1), wish(2), wish(3, priv = true), wish(4, bin = 5L)), code, "tok", emptySet())

    @Test fun theCardTravelsWhole() {
        val c = listCard(list(), "https://img.example/c.jpg")
        assertEquals(2, c.count)
        val back = Chat.parse(Chat.plain(c))
        assertEquals("list", back.kind)
        assertEquals("FRIEND01", back.text)
        assertEquals("Birthday", back.title)
        assertEquals("https://img.example/c.jpg", back.img)
        assertEquals(2, back.count)
        assertEquals("Wishlist: Birthday", Chat.preview(back))
    }

    @Test fun olderAppsReadAPlainLine() {
        val raw = JSONObject(Chat.plain(listCard(list(), "")))
        assertTrue(raw.optString("x").contains("Birthday"))
        // An older app treats an unknown kind as text and shows "x".
        assertEquals(raw.optString("x"), Chat.parse("""{"t":"text","x":${JSONObject.quote(raw.optString("x"))}}""").text)
    }

    @Test fun onlyWebPictures() {
        val bad = Chat.parse("""{"t":"list","code":"C","name":"N","img":"javascript:alert(1)","n":1}""")
        assertEquals("", bad.img)
        assertEquals("N", bad.title)
    }

    @Test fun myWishlistIsNamedKindly() {
        assertEquals("My wishlist", listCard(list(name = "My wishlist"), "").title)
    }

    @Test fun aListOnlyYouCanSeeAsksFirst() {
        val src = java.io.File("src/main/java/com/wishlist/app/ChatModel.kt").readText()
        assertTrue(src.contains("if (d.code == null || ListVisibility.of(ctx, d.id) == Who.ME) { askVisible = d; return }"))
        assertTrue(src.contains("ListVisibility.set(ctx, d.id, Who.FRIENDS)"))
    }
}
