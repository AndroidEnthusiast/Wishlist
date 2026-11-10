package com.wishlist.app

import com.wishlist.app.PriceWatch.Reading
import com.wishlist.app.PriceWatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * When a price check believes a reading (PriceWatch.judge, 2.178; owner, 10 November 2026: read twice, a typed price
 * stays, read prices are corrected quietly). Plain logic, no phone needed.
 */
class PriceWatchTest {
    private val url = "https://shop.example/teapot"
    private fun w(price: Double?, cur: String = "EUR", u: String = url) = wish(7, price = price, cur = cur, url = u)
    private fun eur(p: Double) = Reading(p, "EUR", true)

    /** Runs readings one after the other like a check does, keeping the notes; returns the verdicts and the last notes. */
    private fun run(start: Wish, st0: PriceWatchRow?, vararg readings: Reading): Pair<List<Verdict>, PriceWatchRow> {
        var wish = start
        var st = st0
        val out = ArrayList<Verdict>()
        for ((i, r) in readings.withIndex()) {
            val j = PriceWatch.judge(wish, st, r, 1000L + i)
            out.add(j.verdict)
            st = j.row
            when (j.verdict) {
                Verdict.QUIET -> wish = wish.copy(price = j.price, oldPrice = null, currency = j.currency)
                Verdict.CHANGED -> wish = wish.copy(price = j.price, oldPrice = wish.price)
                else -> {}
            }
        }
        return out to st!!
    }

    /** Notes of a wish that has been checked before: the shop showed [read], the wish shows the same. */
    private fun settled(read: Double) = PriceWatchRow(7, url, read, "EUR", read, false, null, 500L, 0L)

    @Test fun theSamePriceIsBelievedAtOnce() {
        val (v, st) = run(w(34.95), null, eur(34.95))
        assertEquals(listOf(Verdict.NOTHING), v)
        assertEquals(34.95, st.read!!, 0.0)
        assertEquals(1000L, st.okAt)
    }

    @Test fun aNewPriceNeedsTwoReadingsThatAgree() {
        val (v, st) = run(w(34.95), settled(34.95), eur(29.95), eur(29.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.CHANGED), v)
        assertEquals(29.95, st.read!!, 0.0)
        assertNull(st.cand)
    }

    @Test fun oneOddReadingChangesNothing() {
        // The page shows another product's price once, then the real one again: no change, no notification.
        val (v, st) = run(w(34.95), settled(34.95), eur(159.95), eur(34.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.NOTHING), v)
        assertEquals(34.95, st.read!!, 0.0)
        assertNull(st.cand)
    }

    @Test fun twoDifferentOddReadingsChangeNothing() {
        val (v, st) = run(w(34.95), settled(34.95), eur(10.0), eur(20.0))
        assertEquals(listOf(Verdict.AGAIN, Verdict.AGAIN), v)
        assertEquals(34.95, st.read!!, 0.0)
    }

    @Test fun theNextCheckMayAgreeWithAWaitingReading() {
        // The second reading failed in one check; the next check reads the same new price: believed then.
        val waiting = settled(34.95).copy(cand = 29.95)
        val j = PriceWatch.judge(w(34.95), waiting, eur(29.95), 2000L)
        assertEquals(Verdict.CHANGED, j.verdict)
        assertEquals(29.95, j.price!!, 0.0)
    }

    @Test fun theFirstLookCorrectsAReadPriceQuietly() {
        // Added from the in-app shop at 89.95; the background reading keeps saying 79.95: quiet, never "CHANGED".
        val (v, st) = run(w(89.95), null, eur(79.95), eur(79.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.QUIET), v)
        assertEquals(79.95, st.read!!, 0.0)
        assertEquals(79.95, st.own!!, 0.0)
        // And the check after that is calm.
        assertEquals(Verdict.NOTHING, PriceWatch.judge(w(79.95), st, eur(79.95), 3000L).verdict)
    }

    @Test fun theFirstLookNeedsTwoReadingsToo() {
        // One odd first reading must not be remembered as "the shop's price" (the next real one would look like a change).
        val (v, st) = run(w(89.95), null, eur(159.95), eur(89.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.NOTHING), v)
        assertEquals(89.95, st.read!!, 0.0)
    }

    @Test fun aTypedPriceStaysAtTheFirstLook() {
        // The wish form noted that the person typed 89 themselves; the shop's page reads 159.95.
        val typed = PriceWatchRow(7, url, null, "", 89.0, true, null, 0L, 0L)
        val (v, st) = run(w(89.0), typed, eur(159.95), eur(159.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.NOTHING), v)
        assertEquals(159.95, st.read!!, 0.0)
        assertEquals(89.0, st.own!!, 0.0)
        assertTrue(st.typed)
        // Later checks with the same reading stay calm; the typed price is never overwritten by them.
        assertEquals(Verdict.NOTHING, PriceWatch.judge(w(89.0), st, eur(159.95), 3000L).verdict)
    }

    @Test fun aPriceCorrectedByHandLaterStaysToo() {
        // Checked before at 34.95; the person changes the price to 30 in the form (no note from the form needed).
        val (v, st) = run(w(30.0), settled(34.95), eur(34.95), eur(34.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.NOTHING), v)
        assertTrue(st.typed)
        assertEquals(30.0, st.own!!, 0.0)
    }

    @Test fun aTypedPriceFollowsTheShopOnceTheShopChanges() {
        val st = PriceWatchRow(7, url, 159.95, "EUR", 89.0, true, null, 500L, 0L)
        val (v, after) = run(w(89.0), st, eur(149.95), eur(149.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.CHANGED), v)
        assertFalse(after.typed)
        assertEquals(149.95, after.own!!, 0.0)
    }

    @Test fun aWishWithoutAPriceGetsOneQuietly() {
        val (v, st) = run(w(null), null, eur(12.5), eur(12.5))
        assertEquals(listOf(Verdict.AGAIN, Verdict.QUIET), v)
        assertEquals(12.5, st.own!!, 0.0)
    }

    @Test fun anotherLinkStartsAgain() {
        val other = "https://shop.example/kettle"
        val (v, st) = run(w(34.95, u = other), settled(34.95), eur(49.0), eur(49.0))
        assertEquals(listOf(Verdict.AGAIN, Verdict.QUIET), v)
        assertEquals(other, st.url)
    }

    @Test fun anotherCurrencyIsNotAPriceChange() {
        val j = PriceWatch.judge(w(34.95), settled(34.95), Reading(37.5, "USD", true), 2000L)
        assertEquals(Verdict.UNREADABLE, j.verdict)
        // A page that names no currency is compared as before.
        assertEquals(Verdict.NOTHING, PriceWatch.judge(w(34.95), settled(34.95), Reading(34.95, "USD", false), 2000L).verdict)
    }

    @Test fun theFirstLookAlsoCorrectsTheCurrency() {
        // Saved long ago as dollars by a guess; the page says euros.
        val (v, st) = run(w(34.95, cur = "USD"), null, eur(34.95), eur(34.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.QUIET), v)
        assertEquals("EUR", st.cur)
        assertEquals("EUR", PriceWatch.judge(w(34.95, cur = "USD"), st.copy(read = null, cand = 34.95), eur(34.95), 1L).currency)
    }

    @Test fun aPriceTheWishAlreadyShowsIsNotTold() {
        // Typed 149.95 by hand while the shop read 159.95; then the shop's page reads 149.95 too.
        val st = PriceWatchRow(7, url, 159.95, "EUR", 149.95, true, null, 500L, 0L)
        val (v, _) = run(w(149.95), st, eur(149.95), eur(149.95))
        assertEquals(listOf(Verdict.AGAIN, Verdict.NOTHING), v)
    }

    @Test fun failuresAreRememberedFromTheFirstOne() {
        val a = PriceWatch.failed(w(34.95), settled(34.95), 9000L)
        assertEquals(9000L, a.failSince)
        assertEquals(9000L, PriceWatch.failed(w(34.95), a, 9999L).failSince)
        // A good reading clears it.
        assertEquals(0L, PriceWatch.judge(w(34.95), a, eur(34.95), 10000L).row.failSince)
        // Never checked: the notes start with the failure.
        assertEquals(5L, PriceWatch.failed(w(34.95), null, 5L).failSince)
    }

    @Test fun theLineOnThePricePage() {
        val hour = 3600_000L
        val now = 100 * 24 * hour
        assertNull(PriceWatch.line(null, now))
        assertEquals("Price checked less than an hour ago.", PriceWatch.line(settled(1.0).copy(okAt = now - 60_000), now))
        assertEquals("Price checked 1 hour ago.", PriceWatch.line(settled(1.0).copy(okAt = now - hour - 1), now))
        assertEquals("Price checked 5 hours ago.", PriceWatch.line(settled(1.0).copy(okAt = now - 5 * hour), now))
        assertEquals("Price checked 3 days ago.", PriceWatch.line(settled(1.0).copy(okAt = now - 3 * 24 * hour), now))
        // One failed check says nothing yet; a day of them does.
        assertEquals("Price checked 5 hours ago.", PriceWatch.line(settled(1.0).copy(okAt = now - 5 * hour, failSince = now - hour), now))
        val since = java.util.GregorianCalendar(2026, java.util.Calendar.NOVEMBER, 3, 12, 0).timeInMillis
        assertEquals(
            "This shop’s price couldn’t be read since 3 November.",
            PriceWatch.line(settled(1.0).copy(failSince = since), since + 2 * 24 * hour)
        )
    }

    @Test fun shopsAreAskedInThePhonesLanguage() {
        assertEquals("nl-NL,nl;q=0.9,en;q=0.8", acceptLanguage(Locale("nl", "NL")))
        assertEquals("en-GB,en;q=0.9", acceptLanguage(Locale.UK))
        assertEquals("de,en;q=0.8", acceptLanguage(Locale("de")))
        assertEquals("en-US,en;q=0.9", acceptLanguage(Locale.ROOT))
    }
}
