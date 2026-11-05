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
    const val CHANNEL = "price_drops"
    private const val BIN_CHANNEL = "reserved_bin"
    private const val FRIENDS_CHANNEL = "friends"
    private const val MESSAGES_CHANNEL = "messages"
    private const val GROUPS_CHANNEL = "groups"

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

    /** One price change waiting to be told (see [collectPrices]). */
    private class PriceAlert(val drop: Boolean, val id: Int, val item: String, val old: Double, val now: Double, val currency: String, val heading: String)
    private val pending = ArrayList<PriceAlert>()
    private var collecting = 0

    /**
     * Runs a price check and tells its price changes afterwards (owner, 2.122): one change is one notification as
     * before; several drops become one "3 wishes got cheaper" (and several rises one "… more expensive"), listing them.
     */
    suspend fun <T> collectPrices(c: Context, check: suspend () -> T): T {
        synchronized(pending) { collecting++ }
        try {
            return check()
        } finally {
            val now = synchronized(pending) {
                collecting--
                if (collecting > 0) emptyList() else pending.toList().also { pending.clear() }
            }
            try {
                for (drop in listOf(true, false)) {
                    val l = now.filter { it.drop == drop }
                    when {
                        l.size == 1 -> l[0].let { if (drop) postDrop(c, it.id, it.item, it.old, it.now, it.currency, it.heading) else postRise(c, it.id, it.item, it.old, it.now, it.currency, it.heading) }
                        l.size > 1 -> postSummary(c, drop, l)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun postSummary(c: Context, drop: Boolean, l: List<PriceAlert>) {
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
        val pi = PendingIntent.getActivity(
            c, id, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
    }

    /** Title says a price dropped, the text says which item and by how much. During a check: told afterwards. */
    fun notifyDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price drop on your wishlist")) {
        synchronized(pending) { if (collecting > 0) { pending.add(PriceAlert(true, id, item, old, now, currency, heading)); return } }
        postDrop(c, id, item, old, now, currency, heading)
    }

    private fun postDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String) {
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
        synchronized(pending) { if (collecting > 0) { pending.add(PriceAlert(false, id, item, old, now, currency, heading)); return } }
        postRise(c, id, item, old, now, currency, heading)
    }

    private fun postRise(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String) {
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

    /** Someone else moved a gift this phone reserved to the bin. Tapping the alert opens the bin at that gift. */
    fun notifyBinned(c: Context, code: String, w: Wish, listName: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(BIN_CHANNEL, tr("Reserved gifts"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Tells you when a gift you reserved is moved to the bin or taken off a list")
            }
        )
        Notifications.add(c, "gift_binned", "s:$code", w.title, w.image, w.id)
        if (!Show.on(c, "nt_bin")) return // switched off in App settings > Notifications (2.101)
        val id = code.hashCode() * 31 + w.id.hashCode() + 7
        val text = if (w.binBy.isNotBlank()) tr("“%1\$s” on %2\$s was moved to the bin by %3\$s.", w.title, listName, w.binBy)
        else tr("“%1\$s” on %2\$s was moved to the bin.", w.title, listName)
        val open = Intent(c, MainActivity::class.java)
            .putExtra(MainActivity.BIN_CODE, code).putExtra(MainActivity.BIN_ID, w.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, BIN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("Your reserved gift has been moved to the bin"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    /**
     * A gift this phone reserved on a friend's wishlist was taken off it (deleted, or made private). Nudges you to
     * pick another one; tapping opens that friend's list.
     */
    fun notifyGiftGone(c: Context, code: String, id: Long, title: String, listName: String, owned: Boolean = false) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(BIN_CHANNEL, tr("Reserved gifts"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Tells you when a gift you reserved is moved to the bin or taken off a list")
            }
        )
        Notifications.add(c, if (owned) "gift_owned" else "gift_gone", "c:$code", title, "", id)
        if (!Show.on(c, "nt_gone")) return
        val nid = code.hashCode() * 31 + id.hashCode() + 11
        val text = if (owned) tr("“%1\$s” on %2\$s is marked as owned: they already have it. Have a look for another gift.", title, listName)
            else tr("“%1\$s” was taken off %2\$s. Have a look for another gift.", title, listName)
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.FRIEND_CODE, code)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, BIN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (owned) tr("A gift you reserved is already owned") else tr("A gift you reserved is no longer on the list"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(nid, n)
    }

    /**
     * Friends: someone sent you a friend request ([request]), or approved yours. Tapping opens the Friends tab.
     */
    fun notifyFriend(c: Context, id: String, name: String, request: Boolean) {
        if (!Show.on(c, if (request) "nt_req" else "nt_ok")) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(FRIENDS_CHANNEL, tr("Friends"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Tells you about friend requests and birthdays")
            }
        )
        val nid = id.hashCode() * 31 + (if (request) 13 else 17)
        val who = name.ifBlank { tr("Someone") }
        val title = if (request) tr("%s wants to be your friend", who) else tr("%s approved your friend request", who)
        val text = if (request) tr("Open Friends to approve or decline.") else tr("You can now see the wishlists %s shares with friends.", who)
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_FRIENDS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, FRIENDS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(nid, n)
    }

    /** A friend's birthday is in [days] days (2.95). Tapping opens that friend's page. */
    fun notifyBirthday(c: Context, id: String, name: String, days: Int) {
        if (!Show.on(c, "nt_bday")) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(FRIENDS_CHANNEL, tr("Friends"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Tells you about friend requests and birthdays")
            }
        )
        val nid = id.hashCode() * 31 + 19
        val who = name.ifBlank { tr("Someone") }
        val title = tr("%1\$s’s birthday is in %2\$d days", who, days)
        val text = tr("Have a look at %s’s wishlist for a gift they’ll love.", who)
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_FRIEND, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, FRIENDS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(nid, n)
    }

    /**
     * Wishlist groups (2.108): an invite, "No thanks" or "You are removed from …". Tapping opens group [gid] (or its
     * join screen for an invite); an empty [gid] only opens the app.
     */
    fun notifyGroup(c: Context, gid: String, title: String, text: String) {
        if (!Show.on(c, "nt_group")) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(GROUPS_CHANNEL, tr("Wishlist groups"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Invitations, and changes in your groups.")
            }
        )
        val nid = (gid + title).hashCode() * 31 + 29
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_GROUP, gid)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, GROUPS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(nid, n)
    }

    /** A new message from friend [id] (2.98). Tapping opens the conversation. */
    fun notifyMessage(c: Context, id: String, name: String, text: String) {
        if (!Show.on(c, "nt_msg")) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(MESSAGES_CHANNEL, tr("Messages"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("New messages from your friends")
            }
        )
        val nid = id.hashCode() * 31 + 23
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_CHAT, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, MESSAGES_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(name.ifBlank { tr("Someone") })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(nid, n)
    }

    /** The chat with [id] was seen: its message notification goes. */
    fun clearMessage(c: Context, id: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id.hashCode() * 31 + 23)
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
