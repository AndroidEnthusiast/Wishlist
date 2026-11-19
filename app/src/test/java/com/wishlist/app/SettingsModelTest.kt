package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** App settings' state holder (the rebuild, 19 November 2026): every switch and choice lands where it is kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val m = SettingsModel(ctx).also {
        Repo.load(ctx); Show.load(ctx)
        // Choosing how often plans the background check again (WorkManager).
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx, androidx.work.Configuration.Builder().build())
    }

    @Test fun switchesAreKept() {
        m.setShown("home_swipe", false); assertFalse(Show["home_swipe"])
        m.setShown("home_swipe", true); assertTrue(Show["home_swipe"])
        m.setPriceRises(true); assertTrue(Repo.notifyRises)
        m.setPriceDrops(false); assertFalse(Repo.notifyDrops)
    }

    @Test fun intervalAndCurrency() {
        m.setInterval(12); assertEquals(12, Repo.intervalHours)
        m.setCurrency("GBP"); assertEquals("GBP", CurrencyChoice.chosen)
        m.setCurrency(""); assertEquals("", CurrencyChoice.chosen)
    }
}
