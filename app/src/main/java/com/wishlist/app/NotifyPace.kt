package com.wishlist.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/*
 * How notifications arrive (owner, 7 November 2026, after a friend got a pile of them at once):
 *  - Bundles: news of the same kind about the same thing (gifts you reserved on Sam's wishlist, invitations, an
 *    exchange, messages from one friend) becomes ONE notification that updates while it is still showing: "3 gifts you
 *    reserved are no longer on Sam's wishlist", with a line per item. A catch-up (the phone hadn't checked for a while,
 *    or right after installing) is bundled the same way, per kind.
 *  - One buzz per burst: only the first notification in 3 minutes makes a sound or buzzes; the rest arrive silently.
 *  - Everything from Wishlist is stacked under one heading in the notification shade.
 *  - No quiet hours (owner's choice): anything may buzz at any time, once per burst.
 */
object NotifyPace {
    private const val GROUP = "com.wishlist.app.ALL"
    private const val SUMMARY_ID = 7_000_099
    private const val BURST_MS = 3 * 60_000L
    private const val MAX_LINES = 20

    /**
     * Several news items in one notification. [key] names the bundle (kind and what it is about), [line] is this
     * item's line, [title] the title for n items, [open] where a tap goes once it holds several (null: as for one).
     */
    class Bundle(val key: String, val line: String, val title: (Int) -> String, val open: Intent? = null)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Opens the Notifications page: for bundles whose items lead to different places. */
    fun notesIntent(c: Context): Intent = Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTES, true)

    /** The lines of bundle [key] with [line] added; a bundle that's no longer showing starts again. */
    @Synchronized
    private fun lines(c: Context, nm: NotificationManager, nid: Int, key: String, line: String): List<String> {
        val showing = try { nm.activeNotifications.any { it.id == nid } } catch (e: Exception) { false }
        val old = if (!showing) emptyList() else try {
            val a = JSONObject(prefs(c).getString("nb_$key", null) ?: "{}").optJSONArray("l") ?: JSONArray()
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        val now = (old.filter { it != line } + line).takeLast(MAX_LINES)
        val a = JSONArray()
        now.forEach { a.put(it) }
        prefs(c).edit().putString("nb_$key", JSONObject().put("l", a).put("at", System.currentTimeMillis()).toString()).apply()
        return now
    }

    /** True when something buzzed in the last 3 minutes (then this one comes silently); else remembers this buzz. */
    @Synchronized
    private fun quiet(c: Context): Boolean {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        val last = p.getLong("nt_last_buzz", 0L)
        if (now - last in 0 until BURST_MS) return true
        p.edit().putLong("nt_last_buzz", now).apply()
        return false
    }

    /** Shows notification [nid] built by [b], bundled with [bundle] when given, paced and stacked. */
    fun post(c: Context, nid: Int, b: NotificationCompat.Builder, bundle: Bundle? = null) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (bundle != null) {
            val l = lines(c, nm, nid, bundle.key, bundle.line)
            if (l.size > 1) {
                val style = NotificationCompat.InboxStyle()
                l.takeLast(6).asReversed().forEach { style.addLine(it) }
                if (l.size > 6) style.setSummaryText(tr("and %d more", l.size - 6))
                b.setContentTitle(bundle.title(l.size)).setContentText(l.last()).setStyle(style).setNumber(l.size)
                bundle.open?.let { o ->
                    o.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    b.setContentIntent(PendingIntent.getActivity(c, nid, o, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
            }
        }
        b.setGroup(GROUP).setSilent(quiet(c))
        nm.notify(nid, b.build())
        summary(c, nm, b)
    }

    /** The heading that stacks Wishlist's notifications once there are two or more (it never buzzes itself). */
    private fun summary(c: Context, nm: NotificationManager, child: NotificationCompat.Builder) {
        val n = try { nm.activeNotifications.count { it.id != SUMMARY_ID && it.notification.group == GROUP } } catch (e: Exception) { 0 }
        if (n < 2) return
        val pi = PendingIntent.getActivity(c, SUMMARY_ID, notesIntent(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val s = NotificationCompat.Builder(c, child.build().channelId ?: Alerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("Wishlist"))
            .setContentText(tr("%d updates", n))
            .setGroup(GROUP).setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setContentIntent(pi)
            .setAutoCancel(true)
        nm.notify(SUMMARY_ID, s.build())
    }
}
