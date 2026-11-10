package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/**
 * A whole price check of your own wishlists (PriceCheck.mine, 2.178) with pretend shop pages: what ends up on the wish
 * and in the notes, through the real Repo and database.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class PriceCheckTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val url = "https://shop.example/teapot"
    /** What the pretend shop answers, reading after reading (the last answer repeats); null = couldn't be read. */
    private val answers = ArrayDeque<PriceWatch.Reading?>()
    private var asked = 0
    private val oldReader = PriceCheck.reader
    private val oldWait = PriceCheck.againAfterMs

    @Before fun start() {
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        val teapot = Wish(7, url, "Teapot", "", "", 34.95, null, "EUR", "Home")
        prefs.edit().clear().putString("wishes", JSONArray().put(wishToJson(teapot)).toString()).putString("cats", "Home").commit()
        // Repo loads once per process (the tests of this class share one): every test starts from the same wish again.
        Repo.load(c)
        for (id in Repo.mineIds.toList()) Repo.updateMine(c, id) { if (id == MAIN_LIST) listOf(teapot) else emptyList() }
        runBlocking { PriceWatch.prune(c, emptySet()) }
        PriceCheck.againAfterMs = 0L
        PriceCheck.reader = { _, _, _ -> asked++; if (answers.size > 1) answers.removeFirst() else answers.firstOrNull() }
    }

    @After fun stop() { PriceCheck.reader = oldReader; PriceCheck.againAfterMs = oldWait }

    private fun shop(vararg prices: Double?) { answers.clear(); prices.forEach { answers.add(it?.let { p -> PriceWatch.Reading(p, "EUR", true) }) }; asked = 0 }
    private fun check(): Int = runBlocking { PriceCheck.mine(c) }
    private fun teapot() = Repo.mine(c, MAIN_LIST).wishes.single { it.id == 7L }
    private fun notes() = runBlocking { PriceWatch.get(c, 7) }

    @Test fun aRealDropIsBelievedInOneCheckAfterTwoReadings() {
        shop(34.95); assertEquals(0, check()); assertEquals(1, asked)
        shop(29.95)
        assertEquals(1, check())
        assertEquals(2, asked)
        assertEquals(29.95, teapot().price!!, 0.0)
        assertEquals(34.95, teapot().oldPrice!!, 0.0)
        // The same price again: nothing more to tell.
        shop(29.95); assertEquals(0, check()); assertEquals(1, asked)
    }

    @Test fun oneOddReadingLeavesTheWishAlone() {
        shop(34.95); check()
        shop(159.95, 34.95)
        assertEquals(0, check())
        assertEquals(34.95, teapot().price!!, 0.0)
        assertNull(teapot().oldPrice)
        assertNull(notes()!!.cand)
    }

    @Test fun theFirstCheckOfAnExistingWishIsQuiet() {
        // An update from an older version: no notes yet, and the shop reads another price than the wish shows.
        shop(31.5)
        assertEquals(0, check())
        assertEquals(31.5, teapot().price!!, 0.0)
        assertNull(teapot().oldPrice)
    }

    @Test fun aTypedPriceSurvivesTheCheck() {
        runBlocking { PriceWatch.typed(c, 7, url, 34.95) }
        shop(159.95)
        assertEquals(0, check())
        assertEquals(34.95, teapot().price!!, 0.0)
        assertEquals(159.95, notes()!!.read!!, 0.0)
        // The shop's own price changes later: now the wish follows and it counts as a drop or rise.
        shop(20.0)
        assertEquals(1, check())
        assertEquals(20.0, teapot().price!!, 0.0)
        assertEquals(34.95, teapot().oldPrice!!, 0.0)
    }

    @Test fun aShopThatCannotBeReadIsNoted() {
        shop(null)
        assertEquals(0, check())
        assertEquals(34.95, teapot().price!!, 0.0)
        assert(notes()!!.failSince > 0)
        shop(34.95); check()
        assertEquals(0L, notes()!!.failSince)
    }

    @Test fun aCopyOnTwoListsIsReadAndToldOnce() {
        shop(34.95); check()
        val second = Repo.createMine(c, "Second", "")
        Repo.updateMine(c, second) { it + teapot() }
        shop(29.95)
        assertEquals(1, check())
        assertEquals(2, asked)
        assertEquals(29.95, Repo.mine(c, second).wishes.single().price!!, 0.0)
        assertEquals(29.95, teapot().price!!, 0.0)
    }

    @Test fun notesOfRemovedWishesAreForgotten() {
        shop(34.95); check()
        Repo.updateMine(c, MAIN_LIST) { emptyList() }
        check()
        assertNull(notes())
    }
}
