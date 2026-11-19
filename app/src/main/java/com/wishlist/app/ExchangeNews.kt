package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// News, messages and reminders of gift exchanges, each told once (a part of [Exchanges] since the data rebuild, owner 19 November 2026; callers keep writing Exchanges.…).

internal fun Exchanges.notify(c: Context, gx: String, title: String, text: String, kind: String, onScreen: Boolean) {
    if (!onScreen) Alerts.notifyExchange(c, gx, title, text, kind, "e:$gx")
}

/** Invites and news are told once: a notification (only in the background) and a line on the Notifications page. */
internal suspend fun Exchanges.news(c: Context, news: JSONArray, ins: List<GxInvite>, onScreen: Boolean) {
    val p = prefs(c)
    val told = p.getStringSet("gx_inv_told", null)?.toSet() ?: emptySet()
    for (v in ins) if (v.gx !in told) {
        val who = v.byName.ifBlank { tr("Someone") }
        notify(c, v.gx, tr("%1\$s invited you to “%2\$s”", who, v.name), tr("A gift exchange. Pick a wishlist and join."), "gx_invite", onScreen)
        Notifications.add(c, "gx_invite", "e:" + v.gx, v.name, extra = who, read = onScreen)
    }
    p.edit().putStringSet("gx_inv_told", ins.map { it.gx }.toSet()).apply()
    var upto = 0L
    for (i in 0 until news.length()) {
        val n = news.getJSONObject(i)
        upto = maxOf(upto, n.optLong("id"))
        val gx = n.optString("gx")
        val name = n.optString("name")
        val who = n.optString("who").ifBlank { tr("Someone") }
        val pair: Pair<String, String> = when (n.optString("kind")) {
            "drawn" -> tr("Names are drawn in “%s”", name) to tr("Open yours to see who you’re buying for.")
            "changed" -> tr("Your name changed in “%s”", name) to tr("Someone left or joined. Open your new name.")
            "removed" -> tr("You are removed from “%s”", name) to tr("The organiser removed you from this gift exchange.")
            "cancelled" -> tr("“%s” was cancelled", name) to tr("The organiser cancelled this gift exchange.")
            "settings" -> tr("“%s” changed", name) to tr("The budget or the date changed. Take a look.")
            "organiser" -> tr("You organise “%s” now", name) to tr("You can draw names, set rules and invite people.")
            "declined" -> tr("%s said no thanks", who) to tr("To “%s”.", name)
            "waiting" -> tr("%1\$s joined “%2\$s”", who, name) to tr("Everyone has seen their name. Add them to the draw?")
            "rules" -> tr("A rule couldn’t be kept in “%s”", name) to tr("Someone left, so one rule had to give way. Nobody has themselves.")
            else -> null
        } ?: continue
        val title = pair.first
        val text = pair.second
        val kind = "gx_" + n.optString("kind")
        // "Removed" and "cancelled" go to the list of exchanges: the exchange itself is gone.
        val key = if (kind == "gx_removed" || kind == "gx_cancelled") "e:" else "e:$gx"
        if (!onScreen) Alerts.notifyExchange(c, if (key == "e:") "" else gx, title, text, kind, key)
        Notifications.add(c, kind, key, title, extra = text, read = onScreen)
    }
    if (upto > 0) Cloud.rpc("wl_gx_news_seen", base(c).put("p_upto", upto))
}

/** A new anonymous message (when the phone wasn't looking): one notification per chat. */
internal fun Exchanges.messages(c: Context, before: List<Exchange>, now: List<Exchange>, onScreen: Boolean) {
    if (onScreen) return
    for (x in now) {
        val old = before.firstOrNull { it.id == x.id }?.me
        if (x.me.unreadSanta > (old?.unreadSanta ?: 0) && !ChatOnScreen.quiet(x.id + ":santa", GxChatNav.open, Live.inFront))
            Alerts.notifyExchange(c, x.id, tr("Your Secret Santa wrote"), tr("In “%s”. Open to read and answer.", x.name), "gx_msg", "e:${x.id}", msg = true)
        if (x.me.unreadGiftee > (old?.unreadGiftee ?: 0) && !ChatOnScreen.quiet(x.id + ":giftee", GxChatNav.open, Live.inFront))
            Alerts.notifyExchange(c, x.id, tr("%s answered", x.me.giftee?.name ?: tr("Your giftee")), tr("In “%s”. Open to read.", x.name), "gx_msg", "e:${x.id}", msg = true)
    }
}

/** 7 days before the exchange day, and on the day itself: once each. */
internal fun Exchanges.days(c: Context, now: List<Exchange>) {
    val p = prefs(c)
    val today = LocalDate.now()
    for (x in now) {
        val d = x.day ?: continue
        val n = ChronoUnit.DAYS.between(today, d)
        val key = when {
            n == 0L -> "gx_day_${x.id}_$d"
            n in 4L..7L && x.drawn -> "gx_week_${x.id}_$d"
            else -> null
        } ?: continue
        if (p.getBoolean(key, false)) continue
        p.edit().putBoolean(key, true).apply()
        val e = GxThemes.emoji(x.theme)
        if (n == 0L) Alerts.notifyExchange(c, x.id, tr("Today is the exchange! %s", e), tr("Enjoy “%s”.", x.name), "gx_day", "e:${x.id}")
        else Alerts.notifyExchange(
            c, x.id, tr("%1\$d days to “%2\$s” %3\$s", n.toInt(), x.name, e),
            x.me.giftee?.let { tr("Got something for %s yet?", it.name) } ?: tr("Open your name to see who you’re buying for."),
            "gx_week", "e:${x.id}"
        )
    }
}
