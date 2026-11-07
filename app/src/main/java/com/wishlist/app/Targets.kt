package com.wishlist.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Target prices (since 2.77): in a wish's price history you can set the price you'd like to pay. When a price check
 * finds the price at or below it, you get a notification (also kept in Notifications); tapping it opens that wish's
 * price history, where the target shows in gold. You hear it once each time the price comes down to the target: when
 * it goes above again, the next time counts again. Kept on this phone only, like the price history (wish id -> price).
 */
object Targets {
    var all by mutableStateOf(mapOf<Long, Double>())
        private set
    /** Targets that were reached and already told (until the price goes above them again). */
    private var told: Set<Long> = emptySet()
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val o = JSONObject(prefs(c).getString("targets", null) ?: "{}")
            all = o.keys().asSequence().associate { it.toLong() to o.getDouble(it) }
            val a = JSONArray(prefs(c).getString("targets_told", null) ?: "[]")
            told = (0 until a.length()).map { a.getLong(it) }.toSet()
        } catch (e: Exception) {
            all = emptyMap()
            told = emptySet()
        }
    }

    private fun save(c: Context) {
        val o = JSONObject()
        all.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs(c).edit().putString("targets", o.toString()).putString("targets_told", JSONArray(told.toList()).toString()).apply()
    }

    fun of(id: Long): Double? = all[id]

    /** True when [w]'s price is at or below its target. */
    fun reached(w: Wish): Boolean {
        val t = all[w.id] ?: return false
        val p = w.price ?: return false
        return p <= t + 0.005
    }

    /** Sets (or with null removes) the target of wish [id]. A target the price is already at counts as told. */
    @Synchronized
    fun set(c: Context, w: Wish, target: Double?) {
        load(c)
        all = if (target == null) all - w.id else all + (w.id to target)
        told = if (target != null && (w.price ?: Double.MAX_VALUE) <= target + 0.005) told + w.id else told - w.id
        save(c)
    }

    /** After a price check found [w]'s new price on list [listKey]: tells you when it came down to the target. */
    @Synchronized
    fun check(c: Context, listKey: String, w: Wish) {
        load(c)
        val t = all[w.id] ?: return
        val p = w.price ?: return
        if (p <= t + 0.005) {
            if (w.id in told) return
            told = told + w.id
            save(c)
            Notifications.targetReached(c, listKey, w, t)
            notify(c, w, t, p)
        } else if (w.id in told) {
            told = told - w.id
            save(c)
        }
    }

    /** "Target price reached": tapping it opens the wish's price history. */
    private fun notify(c: Context, w: Wish, target: Double, now: Double) {
        Alerts.ensureChannel(c)
        val id = w.id.toInt() + 17
        val open = Intent(c, MainActivity::class.java)
            .putExtra(MainActivity.HISTORY_ID, w.id)
            .putExtra(Alerts.NOTE_KIND, "target").putExtra(Alerts.NOTE_ID, w.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = tr("%1\$s is now %2\$s. Your price alert was %3\$s.", w.title, money(now, w.currency), money(target, w.currency))
        val n = NotificationCompat.Builder(c, Alerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("Price alert: it’s cheaper now"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, id, n)
    }
}

/** A "Target price reached" notification was tapped: the wish whose price history to open (with its target lit up). */
object HistoryNav {
    var open by mutableStateOf<Long?>(null)
}
