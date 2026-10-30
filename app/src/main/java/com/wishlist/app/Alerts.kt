package com.wishlist.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Everything about price-drop alerts: the background schedule, the notification and the settings shortcuts. */
object Alerts {
    private const val CHANNEL = "price_drops"

    /** Creates the notification channel (high importance, so the alert pops up). Safe to call again and again. */
    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("drops") // the older, quieter channel from earlier versions
        val ch = NotificationChannel(CHANNEL, tr("Price changes"), NotificationManager.IMPORTANCE_HIGH)
        ch.description = tr("Tells you when something on your wishlists gets cheaper or more expensive")
        nm.createNotificationChannel(ch)
    }

    /** Runs the price check in the background every [hours] hours. update=true applies a changed interval. */
    fun schedule(c: Context, hours: Int, update: Boolean) {
        val req = PeriodicWorkRequestBuilder<PriceWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "prices",
            if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    fun checkNow(c: Context) {
        WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<PriceWorker>().build())
    }

    /** Title says a price dropped, the text says which item and by how much. */
    fun notifyDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price drop on your wishlist")) {
        ensureChannel(c)
        val pct = if (old > 0) Math.round((1 - now / old) * 100) else 0L
        val text = tr("%1\$s is now %2\$s (was %3\$s, -%4\$d%%)", item, money(now, currency), money(old, currency), pct)
        val pi = PendingIntent.getActivity(
            c, id, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(heading)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
    }

    /** A price went up (only when switched on in Settings). Title and text like [notifyDrop]. */
    fun notifyRise(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price increase on your wishlist")) {
        ensureChannel(c)
        val pct = if (old > 0) Math.round((now / old - 1) * 100) else 0L
        val text = tr("%1\$s is now %2\$s (was %3\$s, +%4\$d%%)", item, money(now, currency), money(old, currency), pct)
        val pi = PendingIntent.getActivity(
            c, id, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(heading)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
    }

    /** Shows an example alert. Returns false if notifications are switched off for the app. */
    fun sendTest(c: Context): Boolean {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return false
        notifyDrop(c, 1, tr("Example item"), 100.0, 80.0, "USD", tr("Test: price drop on your wishlist"))
        return true
    }

    /** Opens Android's page for this app, where Battery can be set to Unrestricted. */
    fun openAppSettings(c: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openNotificationSettings(c: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
