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
    private const val EXCHANGE_CHANNEL = "exchange"
    private const val BIRTHDAY_CHANNEL = "birthdays"
    private const val OCC_CHANNEL = "occasions"
    private const val GIFTLIST_CHANNEL = "gift_lists"
    private const val REACT_CHANNEL = "reactions"

    /** Where a tapped notification sits on the Notifications page: it is marked as read (owner, 2.126: no red dot). */
    const val NOTE_KIND = "note_kind"
    const val NOTE_LIST = "note_list"
    const val NOTE_ID = "note_id"

    private fun Intent.note(kind: String, list: String = "", id: Long = 0L): Intent =
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
        ch(GIFTLIST_CHANNEL, tr("Collab wishlist invitations"), tr("A Friend invites you to plan gifts together"))
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
            if (off(GIFTLIST_CHANNEL)) tr("Collab wishlist invitations are off. You’ll miss invitations from Friends.") else null,
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

    /** One price change waiting to be told (see [collectPrices]). */
    private class PriceAlert(val drop: Boolean, val id: Int, val item: String, val old: Double, val now: Double, val currency: String, val heading: String, val wishId: Long = 0L)
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
                        l.size == 1 -> l[0].let { if (drop) postDrop(c, it.id, it.item, it.old, it.now, it.currency, it.heading, it.wishId) else postRise(c, it.id, it.item, it.old, it.now, it.currency, it.heading, it.wishId) }
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
    fun notifyDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price drop on your wishlist"), wishId: Long = 0L) {
        synchronized(pending) { if (collecting > 0) { pending.add(PriceAlert(true, id, item, old, now, currency, heading, wishId)); return } }
        postDrop(c, id, item, old, now, currency, heading, wishId)
    }

    /** Tapping one price change of your own wish opens its price page (owner, 7 November 2026); others open the app. */
    private fun priceIntent(c: Context, wishId: Long): Intent {
        val open = Intent(c, MainActivity::class.java).note("price")
        if (wishId != 0L) open.putExtra(MainActivity.HISTORY_ID, wishId)
        return open
    }

    private fun postDrop(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String, wishId: Long = 0L) {
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
    fun notifyRise(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String = tr("Price increase on your wishlist"), wishId: Long = 0L) {
        synchronized(pending) { if (collecting > 0) { pending.add(PriceAlert(false, id, item, old, now, currency, heading, wishId)); return } }
        postRise(c, id, item, old, now, currency, heading, wishId)
    }

    private fun postRise(c: Context, id: Int, item: String, old: Double, now: Double, currency: String, heading: String, wishId: Long = 0L) {
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

    /**
     * A gift this phone reserved is no longer on the list it was on (owner, 2.126: one notification for all three
     * cases, as for the giver the result is the same): taken off or made private by a friend, marked as owned by them
     * ([owned]), or moved to the bin by someone else on a gift list ([gift]). Always told (no switch). [place] is
     * "Sam’s wishlist" or “Mum’s 60th”. Tapping, here or on the Notifications page, opens that list to pick another gift.
     */
    fun notifyGiftLost(c: Context, code: String, gift: Boolean, id: Long, title: String, image: String, place: String, owned: Boolean) {
        val listKey = (if (gift) "s:" else "c:") + code
        Notifications.add(c, "gift_lost", listKey, title, image, id, amount = if (owned) 1.0 else 0.0, extra = place)
        ensureChannel(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // One notification per list (owner, 7 November 2026): "3 gifts you reserved are no longer on Sam’s wishlist".
        val nid = listKey.hashCode() * 31 + 11
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, listKey).note("gift_lost", listKey, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = giftLostText(title, owned)
        val n = NotificationCompat.Builder(c, BIN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(giftLostTitle(place))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            "lost:$listKey", if (owned) tr("“%s” is already owned", title) else tr("“%s” was removed", title),
            { k -> tr("%1\$d gifts you reserved are no longer on %2\$s", k, place) }
        ))
    }

    private fun post(c: Context, channel: String, nid: Int, open: Intent, title: String, text: String, bundle: NotifyPace.Bundle? = null) {
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

    /** "7 days to Sam’s birthday 🎈" (owner, 2.128, playful): [what] is "Sam’s birthday" or “Mum’s 60th”. */
    fun reminderTitle(days: Int, what: String) = if (days == 1) tr("1 day to %s 🎈", what) else tr("%1\$d days to %2\$s 🎈", days, what)

    /** "Got the AirPods yet?" or "Got your 2 gifts yet?". */
    fun reminderText(titles: List<String>) =
        if (titles.size == 1) tr("Got the %s yet?", titles[0]) else tr("Got your %d gifts yet?", titles.size)

    /**
     * #1 (owner, 2.128): a giver reserved something on a list whose occasion is near. One push and a line on the
     * Notifications page; tapping opens the list. Always told (it is about gifts you reserved).
     */
    fun notifyReminder(c: Context, code: String, gift: Boolean, days: Int, what: String, titles: List<String>) {
        val listKey = (if (gift) "s:" else "c:") + code
        val title = reminderTitle(days, what)
        Notifications.add(c, "reminder", listKey, title, extra = reminderText(titles), amount = days.toDouble())
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, listKey).note("reminder", listKey)
        post(c, OCC_CHANNEL, code.hashCode() * 31 + 41, open, title, reminderText(titles))
    }

    /** "Nailed it 🎉 · Sam got the AirPods you picked." (owner, 2.128): the owner received a gift you reserved. */
    fun notifyThanks(c: Context, code: String, id: Long, gift: String, who: String) {
        val text = tr("%1\$s got the %2\$s you picked.", who, gift)
        Notifications.add(c, "thanks", "c:$code", tr("Nailed it 🎉"), wishId = id, extra = text)
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, "c:$code").note("thanks", "c:$code", id)
        post(c, BIN_CHANNEL, code.hashCode() * 31 + 43, open, tr("Nailed it 🎉"), text,
            NotifyPace.Bundle("thanks:$code", text, { k -> tr("Nailed it 🎉 %d gifts you picked were received", k) }))
    }

    /** #6 (owner, 2.128): the day after your list's occasion, "Did your wishes come true? ✨". Opens "What did you get?". */
    fun notifyReceivedAsk(c: Context, listId: String, listName: String) {
        val key = "q:$listId"
        Notifications.add(c, "recv_ask", key, listName, extra = tr("Tick what you received."))
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, key).note("recv_ask", key)
        post(c, OCC_CHANNEL, listId.hashCode() * 31 + 47, open, tr("Did your wishes come true? ✨"), tr("Tick what you received."))
    }

    /** "A gift you reserved is no longer on Sam’s wishlist" (push and Notifications page). */
    fun giftLostTitle(place: String) = tr("A gift you reserved is no longer on %s", place)

    /** "“AirPods” was removed. Tap to pick another gift." (or "…is already owned. …"). */
    fun giftLostText(title: String, owned: Boolean) =
        if (owned) tr("“%s” is already owned. Tap to pick another gift.", title) else tr("“%s” was removed. Tap to pick another gift.", title)

    /**
     * Friends: someone sent you a friend request ([request]), or approved yours. Tapping opens the Friends tab.
     */
    fun notifyFriend(c: Context, id: String, name: String, request: Boolean) {
        if (request && !Show.on(c, "nt_req")) return
        ensureChannel(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Requests in one notification, approvals in another (owner, 7 November 2026: bundled per kind).
        val nid = if (request) 7_000_201 else 7_000_202
        val who = name.ifBlank { tr("Someone") }
        val title = if (request) tr("%s wants to be your friend", who) else tr("%s approved your friend request", who)
        val text = if (request) tr("Open Friends to approve or decline.") else tr("You can now see the wishlists %s shares with friends.", who)
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_FRIENDS, true)
            .note(if (request) "friend_req" else "friend_ok", (if (request) "r:" else "p:") + id)
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
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            if (request) "fr_req" else "fr_ok", title,
            { k -> if (request) tr("%d people want to be your friend", k) else tr("%d friend requests approved", k) }
        ))
    }

    /** A friend's birthday is in [days] days (2.95). Tapping opens that friend's page. */
    fun notifyBirthday(c: Context, id: String, name: String, days: Int) {
        if (!Show.on(c, "nt_bday")) return
        ensureChannel(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val nid = 7_000_205
        val who = name.ifBlank { tr("Someone") }
        val title = tr("%1\$s’s birthday is in %2\$d days", who, days)
        val text = tr("Have a look at %s’s wishlist for a gift they’ll love.", who)
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_FRIEND, id).note("birthday", "p:$id")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, BIRTHDAY_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            "bday", title, { k -> tr("%d friends have a birthday coming up", k) },
            Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_FRIENDS, true)
        ))
    }

    /**
     * Wishlist groups (2.108): an invite, "No thanks" or "You are removed from …". Tapping opens group [gid] (or its
     * join screen for an invite); an empty [gid] only opens the app.
     */
    fun notifyGroup(c: Context, gid: String, title: String, text: String, noteKind: String = "", noteList: String = "") {
        // Always told (owner, 2.126: invitations can't be switched off).
        ensureChannel(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // All group news in one notification while it shows (owner, 7 November 2026).
        val nid = 7_000_203
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_GROUP, gid).note(noteKind, noteList)
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
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            "groups", title, { k -> tr("%d updates about your wishlist groups", k) }, NotifyPace.notesIntent(c)
        ))
    }

    /**
     * Gift exchange news (2.129): an invitation, names drawn, an anonymous message, the exchange day. Always told, like
     * group invitations. Tapping opens the exchange ([gx]; empty: the list of exchanges).
     */
    fun notifyExchange(c: Context, gx: String, title: String, text: String, noteKind: String = "", noteList: String = "", msg: Boolean = false) {
        ensureChannel(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Per exchange: its messages in one notification, its other news in another (owner, 7 November 2026).
        val nid = (gx + (if (msg) "msg" else "news")).hashCode() * 31 + 37
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_EXCHANGE, gx).note(noteKind, noteList)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, EXCHANGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            "gx:$gx:" + (if (msg) "msg" else "news"), if (msg) text else title,
            { k -> if (msg) tr("%d new anonymous messages", k) else tr("%d updates in your gift exchange", k) }
        ))
    }

    /**
     * A Friend invited you to a gift list (owner, 7 November 2026: "inviting and fun"): a colourful gift picture, the
     * list's name with 🎁, and tapping opens the invitation (Join / No thanks). Always told, like group invitations.
     */
    fun notifyGiftInvite(c: Context, code: String, who: String, name: String) {
        ensureChannel(c)
        val nid = 7_000_204
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, "i:$code").note("gl_invite", "i:$code")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = tr("🎁 %1\$s invited you to “%2\$s”", who, name)
        val text = tr("Plan gifts together and keep it a surprise. Tap to join.")
        val n = NotificationCompat.Builder(c, GIFTLIST_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setLargeIcon(giftPicture(c))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFFFF7AA2.toInt())
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            "gl_inv", title, { k -> tr("%d invitations to collab wishlists", k) }, NotifyPace.notesIntent(c)
        ))
    }

    /** People reacted to your wishes (owner: bundled, at most one a day, in the evening). Tapping opens the list. */
    fun notifyReactions(c: Context, listKey: String, title: String, text: String) {
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, listKey).note("reacts", listKey)
        ensureChannel(c)
        val pi = PendingIntent.getActivity(c, 59, open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, REACT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
        NotifyPace.post(c, 59, n)
    }

    /**
     * Stuck reservations (owner, 7 November 2026): [kind] "ask" ("Still buying …?" with the two answers as buttons),
     * "ask_sent", "ask_yes", "ask_free" or "ask_ended". [open] is where a tap goes ("v:" Gifts I reserved, or the list
     * with the wish). Always told: it is about gifts you reserved or want.
     */
    fun notifyAsk(c: Context, kind: String, ask: Long, open: String, wishId: Long, title: String, text: String) {
        ensureChannel(c)
        val nid = (kind + ask).hashCode() * 31 + 61
        val i = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, open).note(kind, open, wishId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, nid, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(c, BIN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        if (kind == "ask") {
            fun answer(yes: Boolean): PendingIntent {
                val a = Intent(c, MainActivity::class.java).putExtra(MainActivity.ASK_ID, ask).putExtra(MainActivity.ASK_YES, yes)
                    .putExtra(MainActivity.ASK_NID, nid).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                return PendingIntent.getActivity(c, nid + (if (yes) 1 else 2), a, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            b.addAction(0, tr("Yes, I’m buying it"), answer(true))
            b.addAction(0, tr("Let someone else"), answer(false))
        }
        NotifyPace.post(c, nid, b)
    }

    fun cancel(c: Context, nid: Int) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(nid)
    }

    /** A colourful round gift picture for a notification, drawn here (our own drawing). */
    private fun giftPicture(c: Context): android.graphics.Bitmap {
        val px = (64 * c.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val u = px / 24f
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, px.toFloat(), px.toFloat(),
            intArrayOf(0xFF7C4DFF.toInt(), 0xFFFF7AA2.toInt(), 0xFFFFB86B.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        cv.drawCircle(px / 2f, px / 2f, px / 2f, p)
        p.shader = null
        // Confetti dots.
        p.color = 0xCCFFFFFF.toInt()
        listOf(4.5f to 6f, 19f to 5f, 20f to 17f, 5f to 18f).forEach { (x, y) -> cv.drawCircle(x * u, y * u, 0.9f * u, p) }
        // The box, the lid, the ribbon and the bow, in white.
        p.color = 0xFFFFFFFF.toInt()
        cv.drawRoundRect(android.graphics.RectF(6f * u, 11f * u, 18f * u, 19f * u), 1.2f * u, 1.2f * u, p)
        cv.drawRoundRect(android.graphics.RectF(5f * u, 8f * u, 19f * u, 11.4f * u), 1.2f * u, 1.2f * u, p)
        p.color = 0xFFFF7AA2.toInt()
        cv.drawRect(11f * u, 8f * u, 13f * u, 19f * u, p)
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = 1.6f * u
        p.color = 0xFFFFFFFF.toInt()
        cv.drawOval(android.graphics.RectF(7.6f * u, 4.6f * u, 12f * u, 8.2f * u), p)
        cv.drawOval(android.graphics.RectF(12f * u, 4.6f * u, 16.4f * u, 8.2f * u), p)
        return bmp
    }

    /** A new message from friend [id] (2.98). Tapping opens the conversation. */
    fun notifyMessage(c: Context, id: String, name: String, text: String) {
        if (!Show.on(c, "nt_msg")) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(c)
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
        NotifyPace.post(c, nid, n, NotifyPace.Bundle(
            "msg:$id", text, { k -> tr("%1\$s · %2\$d new messages", name.ifBlank { tr("Someone") }, k) }
        ))
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
