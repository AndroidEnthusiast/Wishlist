package com.wishlist.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

// Notifications about your lists and gifts: a reserved gift gone, reminders, thank-yous, invitations to gift lists,
// reactions, stuck reservations (a part of [Alerts] since the data rebuild, owner 19 November 2026; callers keep
// writing Alerts.…).

/**
 * A gift this phone reserved is no longer on the list it was on (owner, 2.126: one notification for all three
 * cases, as for the giver the result is the same): taken off or made private by a friend, marked as owned by them
 * ([owned]), or moved to the bin by someone else on a gift list ([gift]). Always told (no switch). [place] is
 * "Sam’s wishlist" or “Mum’s 60th”. Tapping, here or on the Notifications page, opens that list to pick another gift.
 */
fun Alerts.notifyGiftLost(c: Context, code: String, gift: Boolean, id: Long, title: String, image: String, place: String, owned: Boolean) {
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

/**
 * The clean-up's warning (ROADMAP S4.3, 2.186): the app hasn't been opened for 11 months; a month later the lists
 * are removed online (the phone keeps them). Opening the app is all it takes; tapping this opens it.
 */
fun Alerts.notifyCleanup(c: Context) {
    val open = Intent(c, MainActivity::class.java)
    post(c, GROUPS_CHANNEL, 7_000_301, open, tr("Keep your wishlists online"),
        tr("Open Wishlist to keep your lists online. Without a visit they’re removed online in a month; they stay on your phone."))
}

/** "7 days to Sam’s birthday 🎈" (owner, 2.128, playful): [what] is "Sam’s birthday" or “Mum’s 60th”. */
fun Alerts.reminderTitle(days: Int, what: String) = if (days == 1) tr("1 day to %s 🎈", what) else tr("%1\$d days to %2\$s 🎈", days, what)

/** "Got the AirPods yet?" or "Got your 2 gifts yet?". */
fun Alerts.reminderText(titles: List<String>) =
    if (titles.size == 1) tr("Got the %s yet?", titles[0]) else tr("Got your %d gifts yet?", titles.size)

/**
 * #1 (owner, 2.128): a giver reserved something on a list whose occasion is near. One push and a line on the
 * Notifications page; tapping opens the list. Always told (it is about gifts you reserved).
 */
fun Alerts.notifyReminder(c: Context, code: String, gift: Boolean, days: Int, what: String, titles: List<String>) {
    val listKey = (if (gift) "s:" else "c:") + code
    val title = reminderTitle(days, what)
    Notifications.add(c, "reminder", listKey, title, extra = reminderText(titles), amount = days.toDouble())
    val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, listKey).note("reminder", listKey)
    post(c, OCC_CHANNEL, code.hashCode() * 31 + 41, open, title, reminderText(titles))
}

/** "Nailed it 🎉 · Sam got the AirPods you picked." (owner, 2.128): the owner received a gift you reserved. */
fun Alerts.notifyThanks(c: Context, code: String, id: Long, gift: String, who: String) {
    val text = tr("%1\$s got the %2\$s you picked.", who, gift)
    Notifications.add(c, "thanks", "c:$code", tr("Nailed it 🎉"), wishId = id, extra = text)
    val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, "c:$code").note("thanks", "c:$code", id)
    post(c, BIN_CHANNEL, code.hashCode() * 31 + 43, open, tr("Nailed it 🎉"), text,
        NotifyPace.Bundle("thanks:$code", text, { k -> tr("Nailed it 🎉 %d gifts you picked were received", k) }))
}

/** #6 (owner, 2.128): the day after your list's occasion, "Did your wishes come true? ✨". Opens "What did you get?". */
fun Alerts.notifyReceivedAsk(c: Context, listId: String, listName: String) {
    val key = "q:$listId"
    Notifications.add(c, "recv_ask", key, listName, extra = tr("Tick what you received."))
    val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTE, key).note("recv_ask", key)
    post(c, OCC_CHANNEL, listId.hashCode() * 31 + 47, open, tr("Did your wishes come true? ✨"), tr("Tick what you received."))
}

/** "A gift you reserved is no longer on Sam’s wishlist" (push and Notifications page). */
fun Alerts.giftLostTitle(place: String) = tr("A gift you reserved is no longer on %s", place)

/** "“AirPods” was removed. Tap to pick another gift." (or "…is already owned. …"). */
fun Alerts.giftLostText(title: String, owned: Boolean) =
    if (owned) tr("“%s” is already owned. Tap to pick another gift.", title) else tr("“%s” was removed. Tap to pick another gift.", title)

/**
 * A Friend invited you to a gift list (owner, 7 November 2026: "inviting and fun"): a colourful gift picture, the
 * list's name with 🎁, and tapping opens the invitation (Join / No thanks). Always told, like group invitations.
 */
fun Alerts.notifyGiftInvite(c: Context, code: String, who: String, name: String) {
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
        "gl_inv", title, { k -> tr("%d invitations to wishlists", k) }, NotifyPace.notesIntent(c)
    ))
}

/** People reacted to your wishes (owner: bundled, at most one a day, in the evening). Tapping opens the list. */
fun Alerts.notifyReactions(c: Context, listKey: String, title: String, text: String) {
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
fun Alerts.notifyAsk(c: Context, kind: String, ask: Long, open: String, wishId: Long, title: String, text: String) {
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

/** A colourful round gift picture for a notification, drawn here (our own drawing). */
internal fun Alerts.giftPicture(c: Context): android.graphics.Bitmap {
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
