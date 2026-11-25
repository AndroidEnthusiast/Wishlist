package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gifts I reserved shows each gift with its photo (owner, 25 November 2026: "people don't remember or read text,
 * wishes are inherently visual"). The phone remembers the picture when it reserves, fills it in for reservations made
 * before (when the friend's list is checked), only keeps web pictures, and forgets them with the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReservedPhotosTest {
    private val c get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun w(id: Long, img: String) = Wish(id, "https://shop.example/$id", "Candle $id", "", img, 29.95, null, "EUR", "")

    @Test fun rememberedWhenReserving() {
        FriendWatch.reserved(c, "F1", w(1, "https://img.example/1.jpg"))
        val g = ReservedGifts.remembered(c).first { it.code == "F1" }
        assertEquals(listOf(ReservedItem(1, "Candle 1", "https://img.example/1.jpg")), g.items)
    }

    @Test fun filledInForOlderReservations() {
        // A reservation from before 2.291: only its title was kept.
        c.getSharedPreferences("wishlist", 0).edit().putString("fres_F2", """{"2":"Candle 2"}""").commit()
        assertEquals("", ReservedGifts.remembered(c).first { it.code == "F2" }.items.single().image)
        FriendWatch.check(c, Friend("F2", "Sam"), RemoteList("Sam’s wishlist", emptyList(), listOf(w(2, "https://img.example/2.jpg"))))
        assertEquals("https://img.example/2.jpg", ReservedGifts.remembered(c).first { it.code == "F2" }.items.single().image)
    }

    @Test fun onlyWebPicturesAndForgottenWithTheList() {
        FriendWatch.reserved(c, "F3", w(3, "javascript:alert(1)"))
        assertTrue(FriendWatch.images(c, "F3").isEmpty())
        FriendWatch.reserved(c, "F3", w(4, "https://img.example/4.jpg"))
        FriendWatch.forget(c, "F3")
        assertTrue(FriendWatch.images(c, "F3").isEmpty())
    }
}
