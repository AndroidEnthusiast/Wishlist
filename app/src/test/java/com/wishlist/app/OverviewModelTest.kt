package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pages behind Home's numbers (the rebuild, 19 November 2026): selecting, "From which list?", ♥ on every copy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverviewModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun selecting() {
        val st = OverviewState(ctx)
        assertFalse(st.selecting)
        st.toggle(1); st.toggle(2); st.toggle(1)
        assertEquals(setOf(2L), st.picked); assertTrue(st.selecting)
    }

    @Test fun moreThanOneListAsksWhichOne() {
        val st = OverviewState(ctx)
        val w = Wish(5L, "", "Lamp", "", "", null, null, "EUR", "")
        st.start("move", setOf(5L), listOf(Placed(w, listOf("a", "b"), listOf("A", "B"))))
        assertEquals("move" to setOf(5L), st.fromAsk)
    }

    @Test fun theHeartGoesOnEveryCopy() {
        Repo.load(ctx)
        Repo.add(ctx, Wish(77L, "", "Socks", "", "", null, null, "EUR", "", star = true))
        val other = Repo.createMine(ctx, "Birthday", "")
        Repo.copyInto(ctx, other, Repo.allMine(ctx).flatMap { it.wishes }.filter { it.id == 77L })
        val lists = Repo.allMine(ctx).filter { d -> d.wishes.any { it.id == 77L } }.map { it.id }
        val st = OverviewState(ctx)
        st.star(Placed(Wish(77L, "", "Socks", "", "", null, null, "EUR", ""), lists, lists), false)
        assertTrue(Repo.allMine(ctx).flatMap { it.wishes }.filter { it.id == 77L }.none { it.star })
        assertNull(st.fromAsk)
    }
}
