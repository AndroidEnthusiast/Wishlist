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

/**
 * Everything about notifications: the channels, the background schedule, the settings shortcuts. Since the data
 * rebuild (owner, 19 November 2026) the notifications themselves are in parts, while callers keep writing Alerts.…:
 * AlertsPrices.kt (price changes), AlertsLists.kt (your lists and gifts), AlertsPeople.kt (friends, birthdays, groups,
 * gift exchanges, messages). AlertsSameTest proves every notification is exactly as before.
 */
object Alerts {
    const val CHANNEL = "price_drops"
    internal const val BIN_CHANNEL = "reserved_bin"
    internal const val FRIENDS_CHANNEL = "friends"
    internal const val MESSAGES_CHANNEL = "messages"
    internal const val GROUPS_CHANNEL = "groups"
    internal const val EXCHANGE_CHANNEL = "exchange"
    internal const val BIRTHDAY_CHANNEL = "birthdays"
    internal const val OCC_CHANNEL = "occasions"
    internal const val GIFTLIST_CHANNEL = "gift_lists"
    internal const val REACT_CHANNEL = "reactions"

    /** Where a tapped notification sits on the Notifications page: it is marked as read (owner, 2.126: no red dot). */
    const val NOTE_KIND = "note_kind"
    const val NOTE_LIST = "note_list"
    const val NOTE_ID = "note_id"

    internal fun Intent.note(kind: String, list: String = "", id: Long = 0L): Intent =
        putExtra(NOTE_KIND, kind).putExtra(NOTE_LIST, list).putExtra(NOTE_ID, id)

    /**
     * Creates every notification channel (high importance, so the alert pops up). Safe to call again and again. Each
     * kind has its own channel with a clear name (owner, 2.126), so muting chats in Android's settings doesn't mute
     * reserved gifts or group invitations.
     */
    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("drops") // the older, quieter channel from earlier versions
        fun ch(id: String, name: String, about: String) =
            nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply { description = about })
        ch(CHANNEL, tr("Price changes"), tr("Tells you when something on your wishlists gets cheaper or more expensive"))
        ch(BIN_CHANNEL, tr("Reserved gifts"), tr("Tells you when a gift you reserved is no longer on a wishlist"))
        ch(FRIENDS_CHANNEL, tr("Friend requests"), tr("Someone wants to be your friend"))
        ch(BIRTHDAY_CHANNEL, tr("Friends’ birthdays"), tr("A few days before a friend’s birthday"))
        ch(GROUPS_CHANNEL, tr("Wishlist groups"), tr("Invitations, and being removed from a group"))
        ch(MESSAGES_CHANNEL, tr("Messages"), tr("New messages from your friends"))
        ch(EXCHANGE_CHANNEL, tr("Gift exchange"), tr("Invitations, names drawn, anonymous messages and the exchange day"))
        ch(OCC_CHANNEL, tr("Occasions"), tr("Reminders around birthdays and other occasions"))
        ch(GIFTLIST_CHANNEL, tr("Wishlist invitations"), tr("A Friend invites you to plan gifts together"))
        ch(REACT_CHANNEL, tr("Reactions"), tr("Once a day: who reacted to your wishes"))
    }

    /**
     * The notifications that always come (reserved gifts, group invitations) but that Android has blocked: their
     * names, for the warning in App settings (owner, 2.126). Empty when all is well.
     */
    fun blockedImportant(c: Context): List<String> {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(c)
        // Says how it really is (owner, 2.128 copy review).
        if (!androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled())
            return listOf(tr("Notifications are off for Wishlist. You’ll miss important updates about gifts you reserved and invitations."))
        fun off(id: String) = nm.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
        return listOfNotNull(
            if (off(BIN_CHANNEL)) tr("Notifications about gifts you reserved are off. You’ll miss important updates about them.") else null,
            if (off(GROUPS_CHANNEL)) tr("Group notifications are off. You’ll miss invitations.") else null,
            if (off(GIFTLIST_CHANNEL)) tr("Wishlist invitations are off. You’ll miss invitations from Friends.") else null,
            if (off(EXCHANGE_CHANNEL)) tr("Gift exchange notifications are off. You’ll miss invitations and the draw.") else null
        )
    }

    /** Opens Android's notification settings for this app, where blocked notifications can be switched on again. */
    fun openSettings(c: Context) {
        val i = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.startActivity(i) } catch (e: Exception) {}
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

    /** Checks the bins every half hour, for "your reserved gift has been moved to the bin" (see Bin.kt), and once now. */
    fun scheduleBin(c: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniquePeriodicWork(
            "bin", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BinWorker>(30, TimeUnit.MINUTES).setConstraints(net).build()
        )
        wm.enqueue(OneTimeWorkRequestBuilder<BinWorker>().setConstraints(net).build())
    }

    internal fun post(c: Context, channel: String, nid: Int, open: Intent, title: String, text: String, bundle: NotifyPace.Bundle? = null) {
        ensureChannel(c)
        val pi = PendingIntent.getActivity(c, nid, open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, nid, n, bundle)
    }

    fun cancel(c: Context, nid: Int) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(nid)
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
