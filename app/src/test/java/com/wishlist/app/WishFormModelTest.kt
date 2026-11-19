package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Add / Edit wish form's state holder (the rebuild, 19 November 2026): the name rule, saving, the list, the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WishFormModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun form(edit: Wish? = null, url: String = "") =
        WishFormState(ctx, edit, chooseList = false, fromShop = false, places = Places(), scope = kotlinx.coroutines.MainScope(), initialUrl = url).also { Repo.load(ctx) }

    @Test fun aLinkNeedsANameNothingAtAllToo() {
        val f = form(url = "https://example.com/p")
        assertFalse(f.save()); assertTrue(f.nameErr)
        val empty = form()
        assertFalse(empty.save()); assertTrue(empty.nameErr)
    }

    @Test fun savingAddsTheWish() {
        val f = form()
        f.title = "  Socks  "; f.price = "12,50"; f.star = true
        val before = Repo.myWishes.size
        assertTrue(f.save())
        val w = Repo.myWishes.first { it.title == "Socks" }
        assertEquals(before + 1, Repo.myWishes.size)
        assertEquals(12.5, w.price!!, 0.001); assertTrue(w.star)
    }

    @Test fun editingKeepsTheIdAndAnOldPriceOnlyWhenThePriceStayed() {
        val f0 = form(); f0.title = "Lamp"; f0.price = "30"; f0.save()
        val w = Repo.myWishes.first { it.title == "Lamp" }.copy(oldPrice = 40.0)
        val f = form(w); f.title = "Desk lamp"; assertTrue(f.save())
        assertEquals(40.0, Repo.myWishes.first { it.id == w.id }.oldPrice!!, 0.001)
        val g = form(Repo.myWishes.first { it.id == w.id }); g.price = "25"; g.save()
        assertEquals(null, Repo.myWishes.first { it.id == w.id }.oldPrice)
    }

    @Test fun whatAPageGaveFillsTheForm() {
        val f = form()
        f.useMeta(Meta("Board game", "", "https://x/p.jpg", 39.95, "EUR"))
        assertEquals("Board game", f.title); assertEquals("39.95", f.price); assertEquals("https://x/p.jpg", f.image)
        f.useMeta(null)
        assertEquals("Couldn’t read that page. Fill in the details yourself.", f.err)
    }

    @Test fun anotherListStartsWithoutACategory() {
        val f = form(); f.cat = "Home"
        f.pickTarget(MyLists.saveKey(Repo.openMine, false))
        assertEquals("", f.cat)
    }
}
