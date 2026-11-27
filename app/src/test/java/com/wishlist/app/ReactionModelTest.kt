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

    /**
     * Owner, 27 November 2026: a reaction sometimes showed late. The 30-second reload had started before you reacted
     * and put the list from before it back; such an older answer is ignored now.
     */
    @Test fun aReloadFromBeforeYourReactionDoesNotHideIt() = kotlinx.coroutines.runBlocking {
        Cloud.pretendServer = { fn, _ ->
            if (fn == "wl_rx_get") {
                // You react while the reload is on its way; its answer is from before that.
                kotlinx.coroutines.runBlocking { Reactions.set(ctx, "RXRACE", 7, "🎉") }
                "[]"
            } else "true"
        }
        try {
            Reactions.load(ctx, "RXRACE")
            assertEquals("your reaction stays", listOf("🎉"), Reactions.of("RXRACE", 7).map { it.emoji })
            Cloud.pretendServer = { _, _ -> "[{\"i\":7,\"e\":\"🎉\",\"n\":\"\",\"m\":true,\"at\":\"2026-11-27T19:00:00Z\"}]" }
            Reactions.load(ctx, "RXRACE")
            assertEquals("a later reload is used as usual", listOf("🎉"), Reactions.of("RXRACE", 7).map { it.emoji })
        } finally {
            Cloud.pretendServer = null
            Reactions.map.remove("RXRACE")
        }
    }
}
