package com.wishlist.app

import android.content.Context
import androidx.work.CoroutineWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import androidx.work.WorkerParameters

class PriceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Repo.load(c)
        // All price changes of this check are told together afterwards (Alerts.collectPrices, 2.122).
        Alerts.collectPrices(c) {
            PriceCheck.mine(c)
            // Shared wishlists too: new prices go online for everyone, and every person on the list gets the alert.
            try {
                SharedPrices.check(c)
            } catch (e: Exception) {
            }
        }
        Repo.setLastCheck(c, System.currentTimeMillis())
        // Make sure friends see the new prices even if the app is closed right after.
        try {
            Sync.pushNow()
        } catch (e: Exception) {
        }
        return Result.success()
    }
}

/** The price check of your own wishlists, used by [PriceWorker] and by pulling down on the list. */
object PriceCheck {
    /** One check at a time: the background check and a pull down must never tell the same change twice. */
    private val lock = Mutex()

    /** How long to wait before a page is read a second time (see [PriceWatch], rule 1). The checks set it to 0. */
    internal var againAfterMs = 2000L

    /** How a shop's page is read; the checks put their own pages here. */
    internal var reader: suspend (Context, String, Boolean) -> PriceWatch.Reading? = { c, url, browser -> PriceWatch.read(c, url, browser) }

    /**
     * Asks every shop for its price now, for every wishlist of your own (open or not). Returns how many prices
     * dropped (each drop also sends a notification). What is believed, and when, is in [PriceWatch.judge].
     */
    suspend fun mine(c: Context): Int = lock.withLock {
        var dropped = 0
        // A wish copied to several of your lists is one wish (the same id, owner 2.90): read once, told once.
        val todo = LinkedHashMap<Long, String>()
        val onLists = HashSet<Long>()
        for (listId in Repo.mineIds.toList()) {
            for (w in Repo.mine(c, listId).wishes) {
                onLists.add(w.id)
                if (!w.got && w.url.isNotBlank()) todo.putIfAbsent(w.id, w.url)
            }
        }
        val again = ArrayList<Long>()
        for (second in listOf(false, true)) {
            if (second) {
                if (again.isEmpty()) break
                delay(againAfterMs)
            }
            for (id in if (second) again.toList() else todo.keys.toList()) {
                val url = todo[id] ?: continue
                val before = holder(c, id)?.second ?: continue
                // Items that already have a price may use the slower hidden-browser read; priceless ones are skipped cheaply.
                val r = reader(c, url, before.price != null)
                // Re-read the wish: it may have been edited (or its list deleted) while the page was loading.
                val (listId, w) = holder(c, id) ?: continue
                if (w.url != url || w.got) continue
                val now = System.currentTimeMillis()
                val st = PriceWatch.get(c, id)
                if (r == null) {
                    // A second reading that fails leaves the first one waiting for the next check.
                    if (!second) PriceWatch.put(c, PriceWatch.failed(w, st, now))
                    continue
                }
                val j = PriceWatch.judge(w, st, r, now)
                when (j.verdict) {
                    PriceWatch.Verdict.UNREADABLE -> PriceWatch.put(c, PriceWatch.failed(w, st, now))
                    PriceWatch.Verdict.NOTHING -> PriceWatch.put(c, j.row)
                    PriceWatch.Verdict.AGAIN -> { PriceWatch.put(c, j.row); if (!second) again.add(id) }
                    PriceWatch.Verdict.QUIET -> {
                        val np = j.price ?: continue
                        change(c, id) { it.copy(price = np, oldPrice = null, currency = j.currency.ifBlank { it.currency }) }
                        PriceWatch.put(c, j.row)
                        // A wish that had no price starts its history (and may already be at its price alert).
                        if (w.price == null) {
                            Targets.check(c, "m:$listId", w.copy(price = np))
                            History.record(c, id, np)
                        }
                    }
                    PriceWatch.Verdict.CHANGED -> {
                        val np = j.price ?: continue
                        val cur = w.price
                        // A price that went up keeps the old price too, so the wish shows "+12%".
                        val nw = w.copy(price = np, oldPrice = cur)
                        change(c, id) { it.copy(price = np, oldPrice = cur) }
                        PriceWatch.put(c, j.row)
                        // Came down to your price alert? (see Targets.kt)
                        Targets.check(c, "m:$listId", nw)
                        // A changed price becomes a point in the price history.
                        History.record(c, id, np)
                        if (cur == null) continue
                        // Kept for the Notifications page (drops and rises as switched on in App settings).
                        Notifications.priceChanged(c, "m:$listId", w, cur, np)
                        if (np < cur - 0.005) {
                            if (Repo.notifyDrops) Alerts.notifyDrop(c, id.toInt(), w.label, cur, np, w.currency, wishId = id)
                            dropped++
                        } else if (np > cur + 0.005) {
                            if (Repo.notifyRises) Alerts.notifyRise(c, id.toInt(), w.label, cur, np, w.currency, wishId = id)
                        }
                    }
                }
            }
        }
        PriceWatch.prune(c, onLists)
        dropped
    }

    /** The first of your lists that holds wish [id], with the wish as it is now. */
    private fun holder(c: Context, id: Long): Pair<String, Wish>? {
        for (listId in Repo.mineIds.toList()) Repo.mine(c, listId).wishes.firstOrNull { it.id == id }?.let { return listId to it }
        return null
    }

    /** Changes wish [id] on every list of yours that holds it (a copy is the same wish). */
    private fun change(c: Context, id: Long, f: (Wish) -> Wish) {
        for (listId in Repo.mineIds.toList()) {
            if (Repo.mine(c, listId).wishes.none { it.id == id }) continue
            Repo.updateMine(c, listId) { l -> l.map { if (it.id == id) f(it) else it } }
        }
    }
}

/**
 * Pulling down on a list: the price check runs here, outside the screen, so it goes on when the refresh circle has
 * gone or the screen changes. A pull while a check is still running doesn't start a second one.
 */
object PriceRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null

    /** [shared]: the code of the shared wishlist that is open, or null for My wishlist. [done] gets the message to show. */
    fun start(c: Context, shared: String?, done: suspend (String) -> Unit): Job {
        running?.let { if (it.isActive) return it }
        val job = scope.launch {
            val msg = try {
                if (shared != null) {
                    if (!Shared.refresh(c)) tr("Couldn’t reach the server")
                    else {
                        val n = Alerts.collectPrices(c) { SharedPrices.check(c, shared) }
                        Shared.refresh(c)
                        if (n > 0) tr("Prices checked: %d cheaper", n) else tr("Prices checked")
                    }
                } else {
                    val n = Alerts.collectPrices(c) { PriceCheck.mine(c) }
                    if (n > 0) tr("Prices checked: %d cheaper", n) else tr("Prices checked")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tr("Couldn’t check the prices. Check your connection.")
            }
            try {
                done(msg)
            } catch (e: Exception) {
            }
        }
        running = job
        return job
    }
}
