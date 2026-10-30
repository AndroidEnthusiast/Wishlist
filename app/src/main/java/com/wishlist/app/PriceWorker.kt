package com.wishlist.app

import android.content.Context
import androidx.work.CoroutineWorker
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
    /** Asks every shop for its price now. Returns how many prices dropped (each drop also sends a notification). */
    suspend fun mine(c: Context): Int {
        var dropped = 0
        for (w in Repo.myWishes.toList()) {
            if (w.got || w.url.isBlank()) continue
            // Items that already have a price may use the slower hidden-browser read; priceless ones are skipped cheaply.
            val np = fetchMeta(w.url, c, w.price != null)?.price ?: continue
            // Re-read the wish: it may have been edited while the page was loading.
            val cw = Repo.myWishes.firstOrNull { it.id == w.id } ?: continue
            val cur = cw.price
            when {
                cur == null -> Repo.replaceMine(c, cw.copy(price = np))
                np < cur - 0.005 -> {
                    Repo.replaceMine(c, cw.copy(price = np, oldPrice = cur))
                    if (Repo.notifyDrops) Alerts.notifyDrop(c, cw.id.toInt(), cw.title, cur, np, cw.currency)
                    dropped++
                }
                np > cur + 0.005 -> {
                    // A price that went up keeps the old price too, so the wish shows "↑ +12%".
                    Repo.replaceMine(c, cw.copy(price = np, oldPrice = cur))
                    if (Repo.notifyRises) Alerts.notifyRise(c, cw.id.toInt(), cw.title, cur, np, cw.currency)
                }
            }
        }
        return dropped
    }
}
