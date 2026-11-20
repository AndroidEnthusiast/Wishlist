package com.wishlist.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

// Notifications about people: friend requests, birthdays, wishlist groups, gift exchanges, messages (a part of
// [Alerts] since the data rebuild, owner 19 November 2026; callers keep writing Alerts.…).

/**
 * Friends: someone sent you a friend request ([request]), or approved yours. Tapping opens the Friends tab.
 */
fun Alerts.notifyFriend(c: Context, id: String, name: String, request: Boolean) {
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

/** "Sam’s birthday is in 5 days"; "is tomorrow" for 1 day (the review of 20 November 2026 found "in 1 days"). */
internal fun Alerts.birthdayTitle(who: String, days: Int): String =
    if (days == 1) tr("%s’s birthday is tomorrow", who) else tr("%1\$s’s birthday is in %2\$d days", who, days)

/** A friend's birthday is in [days] days (2.95). Tapping opens that friend's page. */
fun Alerts.notifyBirthday(c: Context, id: String, name: String, days: Int) {
    if (!Show.on(c, "nt_bday")) return
    ensureChannel(c)
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val nid = 7_000_205
    val who = name.ifBlank { tr("Someone") }
    val title = birthdayTitle(who, days)
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
fun Alerts.notifyGroup(c: Context, gid: String, title: String, text: String, noteKind: String = "", noteList: String = "") {
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
fun Alerts.notifyExchange(c: Context, gx: String, title: String, text: String, noteKind: String = "", noteList: String = "", msg: Boolean = false) {
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

/** A new message from friend [id] (2.98). Tapping opens the conversation. */
fun Alerts.notifyMessage(c: Context, id: String, name: String, text: String) {
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
fun Alerts.clearMessage(c: Context, id: String) {
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(id.hashCode() * 31 + 23)
}
