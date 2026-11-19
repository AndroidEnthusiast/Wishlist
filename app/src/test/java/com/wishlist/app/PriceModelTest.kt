package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A wish's price alert (the rebuild, 19 November 2026): only an amount below the price now is saved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PriceModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val w = Wish(9L, "https://example.com/p", "Board game", "", "", 39.95, null, "EUR", "")

    @Test fun theRule() {
        assertEquals("Fill in an amount.", targetError(null, 39.95, "EUR"))
        assertEquals("Fill in an amount.", targetError(0.0, 39.95, "EUR"))
        assertTrue(targetError(39.95, 39.95, "EUR")!!.startsWith("Choose an amount below the price now"))
        assertNull(targetError(30.0, 39.95, "EUR"))
    }

    @Test fun savingAndRemoving() {
        Repo.load(ctx)
        val t = TargetState(ctx, w)
        t.text = "45"; assertFalse(t.save()); assertTrue(t.err != null)
        t.text = "29,5"; assertTrue(t.save())
        assertEquals("29.50", t.text); assertEquals(1, t.saves); assertEquals(29.5, Targets.all[9L]!!, 0.001)
        t.remove()
        assertNull(Targets.all[9L]); assertEquals("", t.text); assertTrue(t.editing)
    }
}
