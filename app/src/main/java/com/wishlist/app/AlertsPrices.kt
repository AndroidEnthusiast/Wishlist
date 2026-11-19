package com.wishlist.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

// Price notifications: a drop, a rise, and the summary after a check (a part of [Alerts] since the data rebuild,
// owner 19 November 2026; callers keep writing Alerts.…).

/** Price changes found during a check, told together afterwards (see [collectPrices]). */
internal object PriceAlertQueue {
    /** One price change waiting to be told (see [collectPrices]). */
    class PriceAlert(val drop: Boolean, val id: Int, val item: String, val old: Double, val now: Double, val currency: String, val heading: String, val wishId: Long = 0L)
    val pending = ArrayList<PriceAlert>()
    var collecting = 0
}

/**
 * Runs a price check and tells its price changes afterwards (owner, 2.122): one change is one notification as
 * before; several drops become one "3 wishes got cheaper" (and several rises one "… more expensive"), listing them.
 */
suspend fun <T> Alerts.collectPrices(c: Context, check: suspend () -> T): T {
    synchronized(PriceAlertQueue.pending) { PriceAlertQueue.collecting++ }
    try {
        return check()
    } finally {
        val now = synchronized(PriceAlertQueue.pending) {
            PriceAlertQueue.collecting--
            if (PriceAlertQueue.collecting > 0) emptyList() else PriceAlertQueue.pending.toList().also { PriceAlertQueue.pending.clear() }
        }
        try {
            for (drop in listOf(true, false)) {
                val l = now.filter { it.drop == drop }
                when {
                    l.size == 1 -> l[0].let { if (drop) postDrop(c, it.id, it.item, it.old, it.now, it.currency, it.heading, it.wishId) else postRise(c, it.id, it.item, it.old, it.now, it.currency, it.heading, it.wishId) }
                    l.size > 1 -> postSummary(c, drop, l)
                }
            }
        } catch (e: Exception) {
        }
    }
}

internal fun Alerts.postSummary(c: Context, drop: Boolean, l: List<PriceAlertQueue.PriceAlert>) {
    ensureChannel(c)
    val title = if (drop) tr("%d wishes got cheaper", l.size) else tr("%d wishes got more expensive", l.size)
    val lines = l.map { a ->
        val pct = if (a.old > 0) Math.round(kotlin.math.abs(1 - a.now / a.old) * 100) else 0L
        tr("%1\$s: %2\$s (%3\$s%4\$d%%)", a.item, money(a.now, a.currency), if (drop) "-" else "+", pct)
    }
    val style = NotificationCompat.InboxStyle()
    lines.take(6).forEach { style.addLine(it) }
    if (lines.size > 6) style.setSummaryText(tr("and %d more", lines.size - 6))
    val id = if (drop) 7_000_001 else 7_000_002
    // Several of your own wishes: "Price changes" (owner, 7 November 2026); gift lists' prices just open the app.
    val open = Intent(c, MainActivity::class.java).note("price")
    if (l.any { it.wishId != 0L }) open.putExtra(MainActivity.OPEN_PRICES, true)
    val pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val n = NotificationCompat.Builder(c, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(title)
        .setContentText(lines.joinToString(", "))
        .setStyle(style)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pi)
        .setAutoCancel(true)
    NotifyPace.post(c, id, n)
}

/** Title says a price dropped, the text says which item and by how much. During a check: told afterwards. */
fun Alerts.notifyDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price drop on your wishlist"), wishId: Long = 0L) {
    synchronized(PriceAlertQueue.pending) { if (PriceAlertQueue.collecting > 0) { PriceAlertQueue.pending.add(PriceAlertQueue.PriceAlert(true, id, item, old, now, currency, heading, wishId)); return } }
    postDrop(c, id, item, old, now, currency, heading, wishId)
}

/** Tapping one price change of your own wish opens its price page (owner, 7 November 2026); others open the app. */
internal fun Alerts.priceIntent(c: Context, wishId: Long): Intent {
    val open = Intent(c, MainActivity::class.java).note("price")
    if (wishId != 0L) open.putExtra(MainActivity.HISTORY_ID, wishId)
    return open
}

internal fun Alerts.postDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String, wishId: Long = 0L) {
    ensureChannel(c)
    val pct = if (old > 0) Math.round((1 - now / old) * 100) else 0L
    val text = tr("%1\$s is now %2\$s (was %3\$s, -%4\$d%%)", item, money(now, currency), money(old, currency), pct)
    val pi = PendingIntent.getActivity(
        c, id, priceIntent(c, wishId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val n = NotificationCompat.Builder(c, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(heading)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pi)
        .setAutoCancel(true)
    NotifyPace.post(c, id, n)
}

/** A price went up (only when switched on in Settings). Title and text like [notifyDrop]. */
fun Alerts.notifyRise(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price increase on your wishlist"), wishId: Long = 0L) {
    synchronized(PriceAlertQueue.pending) { if (PriceAlertQueue.collecting > 0) { PriceAlertQueue.pending.add(PriceAlertQueue.PriceAlert(false, id, item, old, now, currency, heading, wishId)); return } }
    postRise(c, id, item, old, now, currency, heading, wishId)
}

internal fun Alerts.postRise(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String, wishId: Long = 0L) {
    ensureChannel(c)
    val pct = if (old > 0) Math.round((now / old - 1) * 100) else 0L
    val text = tr("%1\$s is now %2\$s (was %3\$s, +%4\$d%%)", item, money(now, currency), money(old, currency), pct)
    val pi = PendingIntent.getActivity(
        c, id, priceIntent(c, wishId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val n = NotificationCompat.Builder(c, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(heading)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pi)
        .setAutoCancel(true)
    NotifyPace.post(c, id, n)
}
