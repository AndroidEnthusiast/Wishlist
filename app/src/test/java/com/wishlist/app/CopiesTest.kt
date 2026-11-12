package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** A wish on several of your lists is one wish (owner, 12 November 2026, 2.198). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class CopiesTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    // Each check has its own wish number: the pretend phone keeps lists between checks of one class.
    private var id = 0L
    private lateinit var teapot: Wish
    private lateinit var other: String

    private fun twoListsWithOneWish(n: Long) {
        id = n
        teapot = Wish(n, "https://shop.example/$n", "Teapot", "", "", 10.0, null, "EUR", "Home")
        c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().clear().commit()
        // A fresh pretend phone for every check: the database file would otherwise stay between checks of this class.
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        Repo.load(c)
        Repo.addMine(c, teapot)
        other = Repo.createMine(c, "Birthday", "")
        Repo.copyInto(c, other, listOf(teapot.copy(category = "Kitchen")))
    }

    private fun on(list: String) = Repo.mine(c, list).wishes.first { it.id == id }

    @Test fun anEditOnOneListReachesItsCopy() {
        twoListsWithOneWish(101)
        assertEquals(listOf(Repo.openMine, other), Repo.listsWith(c, id))
        Repo.replace(c, on(Repo.openMine).copy(title = "Blue teapot", price = 12.0, star = true))
        assertEquals("Blue teapot", on(other).title)
        assertEquals(12.0, on(other).price!!, 0.0)
        assertTrue(on(other).star)
        // Each list keeps its own category.
        assertEquals("Kitchen", on(other).category)
        assertEquals("Home", on(Repo.openMine).category)
    }

    @Test fun changeEverywhereReachesEveryList() {
        twoListsWithOneWish(102)
        Repo.changeEverywhere(c, id) { it.copy(got = true) }
        assertTrue(on(Repo.openMine).got)
        assertTrue(on(other).got)
    }

    @Test fun receivedFromEveryListIsOneReceivedGift() {
        twoListsWithOneWish(103)
        val main = Repo.openMine
        Repo.receiveEverywhere(c, id)
        assertFalse(Repo.mine(c, main).wishes.any { it.id == id })
        assertFalse(Repo.mine(c, other).wishes.any { it.id == id })
        assertEquals(1, Repo.myBin.count { it.id == id && it.recv > 0L })
    }

    @Test fun aWishOnOneListIsLeftAlone() {
        twoListsWithOneWish(104)
        Repo.addMine(c, teapot.copy(id = 105, title = "Lamp"))
        assertFalse(Repo.hasCopies(105))
        Repo.replace(c, Repo.myWishes.first { it.id == 105L }.copy(title = "Big lamp"))
        assertFalse(Repo.mine(c, other).wishes.any { it.id == 105L })
    }
}
