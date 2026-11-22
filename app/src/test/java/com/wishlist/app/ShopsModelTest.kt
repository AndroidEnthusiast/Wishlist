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
    private val m = ShopsModel(ctx, kotlinx.coroutines.MainScope(), SnackbarHostState(), online = { true })

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

    @Test fun searchOpensTheFirstShopsResultsAndClosingForgetsIt() {
        m.search("  wool blanket ")
        assertEquals("wool blanket", m.searched)
        assertEquals("https://www.bol.com/nl/nl/s/?searchtext=wool+blanket", m.open!!.url)
        assertEquals("bol.com", m.open!!.name)
        m.close()
        assertNull(m.open); assertNull(m.searched)
    }

    // Search the shops (owner, 22 November 2026): offline, a message with Try again instead of a shop's error page.
    @Test fun offlineSearchSaysSoAndTryAgainOpensOnceOnline() {
        var up = false
        val o = ShopsModel(ctx, kotlinx.coroutines.MainScope(), SnackbarHostState(), online = { up })
        o.search("lego castle")
        assertTrue(o.offline); assertNull(o.open); assertNull(o.searched)
        up = true
        o.search("lego castle")
        assertEquals(false, o.offline)
        assertEquals("lego castle", o.searched)
    }

    @Test fun anEmptySearchDoesNothing() {
        m.search("   ")
        assertNull(m.open); assertNull(m.searched)
    }

    // The six big shops, then Google; always the same set (owner), even when one was removed from My shops.
    @Test fun theSearchRowIsTheSixBigShopsThenGoogle() {
        assertEquals(listOf("bol.com", "Amazon", "Coolblue", "Wehkamp", "Zalando", "de Bijenkorf", "Google"), Shops.searchShops.map { it.shop.name })
        Shops.remove(ctx, Shops.list(ctx).first { it.host == "zalando.nl" })
        assertEquals(7, Shops.searchShops.size)
        Shops.searchShops.forEach { s ->
            val u = s.url("tea & cake")
            assertTrue(u, u.startsWith("https://") && u.endsWith("tea+%26+cake"))
            assertEquals(s.shop.host, java.net.URI(u).host.removePrefix("www."))
        }
        assertEquals(1, Shops.searchShopAt("www.amazon.nl"))
        assertEquals(-1, Shops.searchShopAt("lego.com"))
    }

    @Test fun removingStartsWithAPulseOnce() {
        m.startEditing(); m.startEditing()
        assertEquals(1, m.pulses)
        val s = Shops.list(ctx).first()
        m.removeShop(s)
        assertTrue(Shops.list(ctx).none { it.host == s.host })
    }
}
