package com.wishlist.app

import android.content.Context
import androidx.work.CoroutineWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.WorkerParameters

class PriceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Repo.load(c)
        PriceCheck.mine(c)
        // Shared wishlists too: new prices go online for everyone, and every person on the list gets the alert.
        try {
            SharedPrices.check(c)
        } catch (e: Exception) {
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

/** The price check of My wishlist, used by [PriceWorker] and by pulling down on the list. */
object PriceCheck {
    /**
     * Asks every shop for its price now, for every wishlist of your own (open or not). Returns how many prices
     * dropped (each drop also sends a notification).
     */
    suspend fun mine(c: Context): Int {
        var dropped = 0
        for (listId in Repo.mineIds.toList()) {
            for (w in Repo.mine(c, listId).wishes) {
                if (w.got || w.url.isBlank()) continue
                // Items that already have a price may use the slower hidden-browser read; priceless ones are skipped cheaply.
                val np = fetchMeta(w.url, c, w.price != null)?.price ?: continue
                // Re-read the wish: it may have been edited (or the list deleted) while the page was loading.
                if (listId !in Repo.mineIds) break
                val cw = Repo.mine(c, listId).wishes.firstOrNull { it.id == w.id } ?: continue
                val cur = cw.price
                // A price that went up keeps the old price too, so the wish shows "+12%".
                val nw = when {
                    cur == null -> cw.copy(price = np)
                    np < cur - 0.005 || np > cur + 0.005 -> cw.copy(price = np, oldPrice = cur)
                    else -> null
                } ?: continue
                Repo.updateMine(c, listId) { l -> l.map { if (it.id == nw.id) nw else it } }
                // Came down to your target price? (see Targets.kt)
                Targets.check(c, "m:$listId", nw)
                // A changed price becomes a point in the price history.
                History.record(c, nw.id, np)
                // Kept for the Notifications page (drops and rises as switched on in App settings).
                if (cur != null) Notifications.priceChanged(c, "m:$listId", cw, cur, np)
                if (cur != null && np < cur - 0.005) {
                    if (Repo.notifyDrops) Alerts.notifyDrop(c, cw.id.toInt(), cw.title, cur, np, cw.currency)
                    dropped++
                } else if (cur != null && np > cur + 0.005) {
                    if (Repo.notifyRises) Alerts.notifyRise(c, cw.id.toInt(), cw.title, cur, np, cw.currency)
                }
            }
        }
        return dropped
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
                        val n = SharedPrices.check(c, shared)
                        Shared.refresh(c)
                        if (n > 0) tr("Prices checked: %d cheaper", n) else tr("Prices checked")
                    }
                } else {
                    val n = PriceCheck.mine(c)
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
