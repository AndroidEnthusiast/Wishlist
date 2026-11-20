package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Chat about this wish" (owner, 20 November 2026): the wish travels inside the text message with its list and id, so
 * a tap opens it in its list; wish cards carry their list too; older messages still read; only http(s) shop links.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatAboutTest {
    private val wish = Wish(42L, "https://shop.example/teapot", "Blue teapot", "", "https://img.example/t.jpg", 39.0, null, "EUR", "")

    @Test fun aTextAboutAWishKeepsTheWish() {
        val about = ChatWish.of(wish, "ABCD1234")
        val back = Chat.parse(Chat.plain(ChatContent(true, "text", "Is it the large one?", about = about)))
        assertEquals("Is it the large one?", back.text)
        assertEquals(about, back.about)
        assertEquals("ABCD1234", back.about!!.code); assertEquals(42L, back.about!!.id)
    }

    @Test fun anOlderTextHasNoWish() {
        val back = Chat.parse("""{"t":"text","x":"Hi"}""")
        assertEquals("Hi", back.text); assertNull(back.about)
    }

    @Test fun aWishCardKnowsItsList() {
        val c = ChatContent(true, "wish", "", "Blue teapot", 39.0, "EUR", "", wish.url, code = "ABCD1234", wishId = 42L)
        val back = Chat.parse(Chat.plain(c))
        assertEquals("ABCD1234", back.code); assertEquals(42L, back.wishId)
        // An older card (no list): it still opens the shop.
        val old = Chat.parse("""{"t":"wish","title":"Lamp","price":null,"cur":"EUR","img":"","url":"https://shop.example/l"}""")
        assertEquals("", old.code); assertEquals(0L, old.wishId); assertEquals("https://shop.example/l", old.url)
    }

    @Test fun onlyWebLinksGetThrough() {
        assertEquals("", ChatWish.safeLink("javascript:alert(1)"))
        assertEquals("", ChatWish.safeLink("intent://x#Intent;end"))
        assertEquals("https://a.example/b", ChatWish.safeLink("https://a.example/b"))
        val bad = Chat.parse("""{"t":"text","x":"x","w":{"title":"T","price":null,"cur":"","img":"","url":"javascript:alert(1)","code":"C","id":1}}""")
        assertEquals("", bad.about!!.url)
    }

    @Test fun aPhotoOnlyOnThisPhoneIsNotSent() {
        assertEquals("", ChatWish.of(wish.copy(image = "file:///data/x.jpg"), "C").img)
    }

    @Test fun theSheetOffersItOnlyWithAFriend() {
        val withFriend = wishSheetRows(wish, WishActions(shareAsIdea = true, chatAbout = {}))
        assertTrue("chat" in withFriend)
        assertTrue("chat" !in wishSheetRows(wish, WishActions(shareAsIdea = true)))
    }
}
