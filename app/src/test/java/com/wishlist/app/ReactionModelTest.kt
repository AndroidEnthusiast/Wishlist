package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Owner, 20 November 2026: one tap on your own reaction takes it away (before, it only brought "+ React" back). */
    @Test fun tappingYoursTakesItAwayAtOnce() {
        val sent = mutableListOf<String>()
        Cloud.pretendServer = { fn, b -> sent += fn + " " + b.optString("p_emoji"); "true" }
        try {
            Reactions.map["RXCODE"] = listOf(React(1, "😍", "You", true, 1L), React(1, "😍", "Ben", false, 2L))
            val s = st()
            s.tapChip(1, "😍", mine = true, canReact = true)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(200); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("yours is gone, Ben's stays", listOf("Ben"), Reactions.of("RXCODE", 1).map { it.name })
            assertEquals("taken away online", listOf("wl_rx_set "), sent)
            assertFalse("no dialog first", s.picking)
        } finally {
            Cloud.pretendServer = null
            Reactions.map.remove("RXCODE")
        }
    }
}
