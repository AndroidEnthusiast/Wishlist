package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a reaction chip (the rebuild, 19 November 2026): who reacted, pick another, or take yours away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReactionModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun st() = ReactionBarState(ctx, "RXCODE", kotlinx.coroutines.MainScope())

    @Test fun withoutReactingYouSeeWho() {
        val s = st(); s.tapChip(1, "😍", mine = false, canReact = false)
        assertEquals("😍", s.who)
    }

    @Test fun yourOwnFirstOffersAnother() {
        val s = st(); s.tapChip(1, "😍", mine = true, canReact = true)
        assertTrue(s.again)
    }

    @Test fun reactingClosesTheChoice() {
        val s = st(); s.again = true
        s.react(1, "🎉")
        assertFalse(s.again)
    }
}
