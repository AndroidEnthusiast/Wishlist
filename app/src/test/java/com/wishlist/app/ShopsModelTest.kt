package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** My shops' state holder (the rebuild, 19 November 2026): Add shop, opening a shop, searching, removing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopsModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val m = ShopsModel(ctx, kotlinx.coroutines.MainScope(), SnackbarHostState())

    @Test fun addShopSaysWhatHappened() {
        assertEquals("Coolblue", m.addShop("", "coolblue.nl"))         // already one of yours
        assertEquals("", m.addShop("", "not a web address"))
        assertNull(m.addShop("Kruidvat", "kruidvat.nl"))
        assertTrue(Shops.has(ctx, "kruidvat.nl"))
    }

    @Test fun openingAShopPutsItFirst() {
        val s = Shops.list(ctx).last()
        m.openShop(s)
        assertEquals(s, m.open)
        assertEquals(s.host, Shops.list(ctx).first().host)
    }

    @Test fun searchOpensTheBrowserAndClosingForgetsIt() {
        m.search("wool blanket")
        assertEquals("wool blanket", m.searched)
        assertTrue(m.open!!.url.startsWith("https://www.google.com/search?q=wool+blanket"))
        m.close()
        assertNull(m.open); assertNull(m.searched)
    }

    @Test fun removingStartsWithAPulseOnce() {
        m.startEditing(); m.startEditing()
        assertEquals(1, m.pulses)
        val s = Shops.list(ctx).first()
        m.removeShop(s)
        assertTrue(Shops.list(ctx).none { it.host == s.host })
    }
}
