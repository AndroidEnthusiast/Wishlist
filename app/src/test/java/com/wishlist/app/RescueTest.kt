package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Get my lists back" (Rescue.merge, ROADMAP R1): the server's list, plus what only the phone knows. */
class RescueTest {
    private fun w(id: Long, title: String, priv: Boolean = false, image: String = "", bin: Long = 0L) =
        wish(id, title = title, bin = bin).copy(priv = priv, image = image)

    @Test fun theServersListReplacesTheOldCopy() {
        val old = listOf(w(1, "Lamp (old name)"), w(2, "Deleted since"))
        val online = listOf(w(3, "Added since"), w(1, "Lamp"))
        assertEquals(listOf("Added since", "Lamp"), Rescue.merge(old, online) { false }.map { it.title })
    }

    @Test fun privateWishesOfTheOldCopyStay() {
        val old = listOf(w(1, "Lamp"), w(5, "Secret", priv = true), w(6, "Binned secret", priv = true, bin = 9L))
        val out = Rescue.merge(old, listOf(w(1, "Lamp")), { false })
        assertEquals(listOf("Lamp", "Secret"), out.map { it.title })
        assertEquals(true, out.last().priv)
    }

    @Test fun aPrivateWishThatWentPublicSinceIsNotDoubled() {
        val old = listOf(w(5, "Secret", priv = true))
        assertEquals(listOf("Now public"), Rescue.merge(old, listOf(w(5, "Now public"))) { false }.map { it.title })
    }

    @Test fun yourOwnPhotoStaysTheFileOnThePhoneWhileItIsThere() {
        val old = listOf(w(1, "Lamp", image = "file:///data/p/1.jpg"), w(2, "Book", image = "file:///data/p/2.jpg"))
        val online = listOf(w(1, "Lamp", image = "https://x.supabase.co/1.jpg"), w(2, "Book", image = "https://x.supabase.co/2.jpg"))
        val out = Rescue.merge(old, online) { it.endsWith("1.jpg") }
        assertEquals(listOf("file:///data/p/1.jpg", "https://x.supabase.co/2.jpg"), out.map { it.image })
    }

    @Test fun wishesInTheServersBinStayOut() {
        assertEquals(listOf("Lamp"), Rescue.merge(emptyList(), listOf(w(1, "Lamp"), w(2, "Binned", bin = 5L))) { false }.map { it.title })
    }
}
