package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * When a price check may believe what it read (2.178; owner, 10 November 2026: "I'm not confident it'll show the
 * correct price of the item I saved every time").
 *
 * Before, a wish took any price the moment one check read it, and told you. One odd reading (a half-loaded page, another
 * size shown first, the shop's page in another currency, a "not found" page with suggestions) became a wrong price plus
 * a false notification, and a price you had corrected by hand was overwritten at the next check. The rules now:
 *
 * 1. **Twice.** A reading that differs from the last believed one only counts when a second reading agrees: later in
 *    the same check, or else at the next check ([PriceWatchRow.cand] waits). So a real price drop is told without delay.
 * 2. **The first look is quiet.** The first check of a new wish, a new link or a price you changed by hand compares the
 *    shop's page with the wish. A price you typed stays; a price the app read itself is corrected quietly (no
 *    notification, no crossed-out old price). After that only a change at the shop counts.
 * 3. **Same currency only.** A page that names another currency than last time is not a price change (see
 *    [Meta.currencySure]); it counts as "couldn't be read".
 * 4. **It shows.** When each wish was last read, and since when reading fails, is kept for the price page.
 *
 * The rules are plain logic ([judge], checked by PriceWatchTest); the notes per wish live in the database
 * ([PriceWatchRow], only on this phone). Collab wishlists share their prices online, so they only use rule 1 and 3
 * ([SharedPrices]).
 */
object PriceWatch {
    /** What a shop's page said now. [sure]: the page named the currency itself. */
    data class Reading(val price: Double, val cur: String, val sure: Boolean)

    enum class Verdict {
        /** Nothing changes on the wish. */
        NOTHING,
        /** The reading differs and nothing agreed with it yet: read the page once more. */
        AGAIN,
        /** The first look: the wish takes the price (and currency) quietly. */
        QUIET,
        /** The shop's price changed: the wish takes it, keeps the old price next to it, and you are told. */
        CHANGED,
        /** The page named another currency than before: treat it as not read. */
        UNREADABLE
    }

    /** What to do, the notes to keep, and for [Verdict.QUIET] and [Verdict.CHANGED] the wish's new price and currency. */
    data class Judged(val verdict: Verdict, val row: PriceWatchRow, val price: Double? = null, val currency: String = "")

    /** Two prices are the same price (also both empty). */
    fun same(a: Double?, b: Double?): Boolean = if (a == null || b == null) a == b else abs(a - b) <= 0.005

    /**
     * The rules above for one wish. [st]: the notes kept for it (null: never checked). Pure: nothing is read or
     * written here.
     */
    fun judge(w: Wish, st: PriceWatchRow?, r: Reading, now: Long): Judged {
        // A new wish, another link, or a price changed by hand since the last look: start again from the wish.
        val fresh = st == null || st.url != w.url || !same(st.own, w.price)
        val base = if (!fresh) st!! else PriceWatchRow(
            wishId = w.id, url = w.url, read = null, cur = "", own = w.price,
            // Typed: the form said so and nothing changed since, or the price differs from what the check left there.
            typed = st != null && st.url == w.url && w.price != null && (if (same(st.own, w.price)) st.typed else true),
            cand = null, okAt = 0L, failSince = 0L
        )
        val seen = base.copy(okAt = now, failSince = 0L)
        val readCur = if (r.sure) r.cur else ""

        if (base.read == null) {
            // The first look. The wish already shows what the shop shows: done.
            val curFits = !r.sure || w.currency.isBlank() || w.currency == r.cur
            if (w.price != null && same(w.price, r.price) && curFits)
                return Judged(Verdict.NOTHING, seen.copy(read = r.price, cur = readCur, cand = null))
            // Otherwise two readings have to agree before anything is believed.
            if (!same(base.cand, r.price)) return Judged(Verdict.AGAIN, seen.copy(cand = r.price))
            // A price typed by hand stays; the shop's reading is only remembered, to notice when it changes.
            if (base.typed && w.price != null)
                return Judged(Verdict.NOTHING, seen.copy(read = r.price, cur = readCur, cand = null))
            return Judged(
                Verdict.QUIET, seen.copy(read = r.price, cur = readCur, own = r.price, typed = false, cand = null),
                r.price, if (r.sure) r.cur else w.currency
            )
        }

        // Later looks: only a change of the shop's own reading counts, and only in the same currency.
        if (r.sure && base.cur.isNotEmpty() && r.cur != base.cur) return Judged(Verdict.UNREADABLE, base)
        val keepCur = base.cur.ifEmpty { readCur }
        if (same(base.read, r.price)) return Judged(Verdict.NOTHING, seen.copy(cur = keepCur, cand = null))
        if (!same(base.cand, r.price)) return Judged(Verdict.AGAIN, seen.copy(cur = keepCur, cand = r.price))
        val next = seen.copy(read = r.price, cur = keepCur, own = r.price, typed = false, cand = null)
        // The wish already shows the new price (it was typed in): nothing to tell.
        if (same(w.price, r.price)) return Judged(Verdict.NOTHING, next)
        return Judged(Verdict.CHANGED, next, r.price, w.currency)
    }

    /** The notes after a check that couldn't read the page (blocked, offline, no price on it). */
    fun failed(w: Wish, st: PriceWatchRow?, now: Long): PriceWatchRow {
        val base = if (st != null && st.url == w.url) st
        else PriceWatchRow(w.id, w.url, null, "", w.price, false, null, 0L, 0L)
        return if (base.failSince != 0L) base else base.copy(failSince = now)
    }

    /** Reads the price a shop shows now, or null when the page couldn't be read or has no price. */
    suspend fun read(c: Context, url: String, browser: Boolean): Reading? {
        val m = fetchMeta(url, c, browser) ?: return null
        if (m.blocked) return null
        val p = m.price ?: return null
        return if (p > 0) Reading(p, m.currency, m.currencySure) else null
    }

    // The notes in the database. A database that can't be used never stops a price check: the check then behaves as
    // if every wish is looked at for the first time (quiet), and nothing is remembered.

    suspend fun get(c: Context, id: Long): PriceWatchRow? = withContext(Dispatchers.IO) {
        try { WishlistDb.get(c).dao().watch(id) } catch (e: Throwable) { Crash.report(e); null }
    }

    suspend fun put(c: Context, row: PriceWatchRow) = withContext(Dispatchers.IO) {
        try { WishlistDb.get(c).dao().putWatch(row) } catch (e: Throwable) { Crash.report(e) }
    }

    /** Forgets the notes of wishes that are no longer on any of your lists. */
    suspend fun prune(c: Context, keep: Set<Long>) = withContext(Dispatchers.IO) {
        try {
            val dao = WishlistDb.get(c).dao()
            for (id in dao.watchIds()) if (id !in keep) dao.deleteWatch(id)
        } catch (e: Throwable) { Crash.report(e) }
    }

    /**
     * The wish form saved a price the person typed in themselves (not the one read from the page): the first check
     * leaves it alone. Called off the main thread.
     */
    suspend fun typed(c: Context, id: Long, url: String, price: Double) =
        put(c, PriceWatchRow(id, url, null, "", price, true, null, 0L, 0L))

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** [typed], from a screen: written in the background so saving a wish never waits for it. */
    fun noteTyped(c: Context, id: Long, url: String, price: Double) {
        val app = c.applicationContext
        scope.launch { typed(app, id, url, price) }
    }

    /**
     * The grey line on a wish's price page (owner, 10 November 2026): when the price was last checked, or since when
     * the shop can't be read. Null when there is nothing to say yet (never checked, or no link).
     */
    fun line(st: PriceWatchRow?, now: Long): String? {
        if (st == null) return null
        if (st.failSince != 0L && now - st.failSince >= FAIL_SHOWS_AFTER)
            return tr("This shop’s price couldn’t be read since %s.", day(st.failSince))
        if (st.okAt == 0L) return null
        val min = (now - st.okAt) / 60000L
        return when {
            min < 60 -> tr("Price checked less than an hour ago.")
            min < 120 -> tr("Price checked 1 hour ago.")
            min < 48 * 60 -> tr("Price checked %d hours ago.", (min / 60).toInt())
            else -> tr("Price checked %d days ago.", (min / 1440).toInt())
        }
    }

    /** One failed check says little (the shop or the connection may hiccup); a day of them is worth telling. */
    const val FAIL_SHOWS_AFTER = 24L * 60 * 60 * 1000

    private fun day(ms: Long): String =
        java.text.SimpleDateFormat("d MMMM", java.util.Locale.UK).format(java.util.Date(ms))
}
