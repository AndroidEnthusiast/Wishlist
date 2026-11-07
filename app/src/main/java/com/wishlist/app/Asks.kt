package com.wishlist.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/*
 * Stuck reservations (owner, 7 November 2026 evening; supabase-together.sql):
 *  - A reserved a gift, B wants it too. B taps the "Reserved" label → "Ask if it's still being bought" (only while the
 *    list's occasion hasn't passed). B sees a short confirmation on screen and gets a short push.
 *  - A gets a push "Still buying X?" with [Yes, I'm buying it] [Let someone else]; the question also waits at the top of
 *    Gifts I reserved with the time left.
 *  - Yes: B is told it's still being bought; nobody can ask about it again for 7 days. Let go, or no answer in 2 days:
 *    the gift is free at once (no hold). B gets "X is free. Reserve it now?" (opens it with Reserve ready); when time ran
 *    out, A gets "Your reservation of X has ended".
 *  - Only the first ask counts ("Someone already asked"). A 2× wish asks everyone who reserved part of it (owner).
 *  - Every push comes at once from the server; the 2-day deadline is a timer on the server (pg_cron, every 10 minutes).
 *    The list's owner never sees any of it.
 */

/** A question waiting for you: [until] is when the gift becomes free without an answer (milliseconds). */
data class AskItem(
    val ask: Long, val code: String, val item: Long, val title: String, val list: String, val owner: String,
    val gift: Boolean, val until: Long
)

/** A list to open on a wish ([reserve]: offer "Reserve it now?" when it is free). */
object AskNav {
    var focus by mutableStateOf<Triple<String, Long, Boolean>?>(null)
}

object Asks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The questions you can answer (this phone's reservations), newest first. */
    var waiting by mutableStateOf<List<AskItem>>(emptyList())
        private set

    fun title(t: String) = t.ifBlank { tr("Unnamed wish") }

    /** "Mum’s 60th" for a gift list, "Sam’s wishlist" for a friend's wishlist. */
    fun place(list: String, owner: String, gift: Boolean) =
        if (gift) "“$list”" else if (owner.isNotBlank()) tr("%s’s wishlist", owner) else tr("a friend’s wishlist")

    private fun time(s: String): Long = try { Instant.parse(s).toEpochMilli() } catch (e: Exception) {
        try { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (e2: Exception) { 0L }
    }

    /** Loads the questions waiting for this phone. Throws when offline. */
    suspend fun refresh(): List<AskItem> {
        val a = JSONArray(Cloud.rpc("wl_ask_mine", JSONObject().put("p_device", Repo.deviceId).put("p_code", "")))
        waiting = (0 until a.length()).map { a.getJSONObject(it) }.map {
            AskItem(
                it.optLong("ask"), it.optString("code"), it.optLong("item"), it.optString("title"), it.optString("list"),
                it.optString("owner"), it.optBoolean("gift"), time(it.optString("until"))
            )
        }
        return waiting
    }

    /** "1 day 5 hours left", "3 hours left", "Less than an hour left". */
    fun left(until: Long, now: Long = System.currentTimeMillis()): String {
        val h = ((until - now) / 3_600_000L).toInt()
        return when {
            h >= 24 -> if (h / 24 == 1 && h % 24 == 0) tr("1 day left") else if (h % 24 == 0) tr("%d days left", h / 24)
                else if (h / 24 == 1) tr("1 day %d hours left", h % 24) else tr("%1\$d days %2\$d hours left", h / 24, h % 24)
            h >= 2 -> tr("%d hours left", h)
            h == 1 -> tr("1 hour left")
            else -> tr("Less than an hour left")
        }
    }

    /** True when the list's own occasion (with its own date) is over: then nobody asks (owner). */
    fun passed(o: Occasion?, today: LocalDate = LocalDate.now()): Boolean {
        if (o == null || o.kind != "own") return false
        return try { LocalDate.parse(o.date).isBefore(today) } catch (e: Exception) { false }
    }

    /** B asks. Returns what the server said: ok, asked, recent, free, mine, passed (see supabase-together.sql). */
    suspend fun ask(c: Context, code: String, item: Long): String {
        val body = JSONObject().put("p_code", code).put("p_item", item).put("p_device", Repo.deviceId)
        val auth = Friends.auth(c)
        body.put("p_id", auth?.optString("p_id").orEmpty()).put("p_token", auth?.optString("p_token").orEmpty())
        return Cloud.rpc("wl_ask", body).trim().removeSurrounding("\"")
    }

    /** The words for [ask]'s answer, shown on screen. */
    fun said(r: String): String = when (r) {
        "ok" -> tr("Asked. You’ll hear within 2 days.")
        "asked" -> tr("Someone already asked. You’ll hear if it becomes free.")
        "recent" -> tr("It’s still being bought. You can ask again in a week.")
        "free" -> tr("It’s free now. You can reserve it.")
        "mine" -> tr("You reserved this one yourself.")
        "passed" -> tr("The occasion has passed.")
        else -> tr("Couldn’t ask. Check your connection.")
    }

    /** A answers. False when there is nothing to answer any more. */
    suspend fun answer(ask: Long, yes: Boolean): Boolean {
        val r = Cloud.rpc("wl_ask_answer", JSONObject().put("p_device", Repo.deviceId).put("p_ask", ask).put("p_yes", yes)).trim()
        waiting = waiting.filter { it.ask != ask }
        return r == "true"
    }

    /** An answer from the notification's buttons: saved, then a short message. */
    fun answerFromNotification(c: Context, ask: Long, yes: Boolean, nid: Int) {
        val app = c.applicationContext
        Alerts.cancel(app, nid)
        scope.launch {
            val msg = try {
                if (!answer(ask, yes)) tr("This question was already answered.")
                else if (yes) tr("Thanks! They’ll hear you’re still buying it.") else tr("Done. Someone else can reserve it now.")
            } catch (e: Exception) {
                tr("Couldn’t answer. Check your connection and answer in Gifts I reserved.")
            }
            Notifications.markRead(app, "ask", "v:", ask)
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Where a tap on news about wish [item] goes: the gift list (when it is on this phone) or the friend's list. */
    fun listKey(code: String, gift: Boolean) = if (gift && Shared.lists.any { it.code == code }) "s:$code" else "c:$code"

    /** News from the server (News.poll): a push and a line on the Notifications page. */
    fun told(c: Context, kind: String, code: String, d: JSONObject) {
        val t = title(d.optString("title"))
        val ask = d.optLong("ask")
        val item = d.optLong("item")
        val gift = d.optBoolean("gift")
        val where = place(d.optString("list"), d.optString("owner"), gift)
        val key = listKey(code, gift)
        when (kind) {
            "ask" -> {
                Notifications.add(c, "ask", "v:", t, wishId = ask, extra = where)
                Alerts.notifyAsk(c, kind, ask, "v:", ask, tr("Still buying “%s”?", t),
                    tr("Someone else would like to buy it too. Answer within 2 days, or it becomes free for them."))
            }
            "ask_sent" -> {
                Notifications.add(c, kind, key, t, wishId = item, extra = where, read = true)
                Alerts.notifyAsk(c, kind, ask, key, item, tr("You asked about “%s”", t), tr("You’ll hear within 2 days whether it’s still being bought."))
            }
            "ask_yes" -> {
                Notifications.add(c, kind, key, t, wishId = item, extra = where)
                Alerts.notifyAsk(c, kind, ask, key, item, tr("“%s” is still being bought", t), tr("Pick another gift, or ask again in a week."))
            }
            "ask_free" -> {
                Notifications.add(c, kind, key, t, wishId = item, extra = where)
                Alerts.notifyAsk(c, kind, ask, key, item, tr("“%s” is free. Reserve it now?", t), tr("Tap to open it on %s.", where))
            }
            "ask_ended" -> {
                Notifications.add(c, kind, key, t, wishId = item, extra = where)
                Alerts.notifyAsk(c, kind, ask, key, item, tr("Your reservation of “%s” has ended", t),
                    tr("There was no answer within 2 days, so someone else can reserve it now. Tap to see the gift."))
            }
        }
    }

    /** The second line of a Notifications page entry of [kind] (the first line is the gift). */
    fun noteLine(kind: String, where: String): String = when (kind) {
        "ask" -> tr("Still buying it? Someone else would like to buy it. Tap to answer.")
        "ask_sent" -> tr("You asked if it’s still being bought (%s).", where)
        "ask_yes" -> tr("It’s still being bought (%s).", where)
        "ask_free" -> tr("It’s free now. Reserve it on %s?", where)
        "ask_ended" -> tr("Your reservation on %s has ended.", where)
        else -> where
    }
}

/**
 * B taps a "Reserved" label of someone else's reservation: "Ask if it's still being bought". [occ]: the list's occasion
 * (an own occasion that has passed: nothing to ask).
 */
@Composable
fun AskDialog(code: String, w: Wish, occ: Occasion?, onClose: () -> Unit, onSaid: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val passed = Asks.passed(occ)
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Someone reserved this")) },
        text = {
            Text(
                if (passed) tr("The occasion has passed, so you can’t ask about it any more.")
                else tr("Want to buy “%s” too? Ask if it’s still being bought. They have 2 days to answer; without an answer it becomes free.", Asks.title(w.title))
            )
        },
        confirmButton = {
            if (!passed) TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val r = try { Asks.ask(ctx, code, w.id) } catch (e: Exception) {
                        if ((e.message ?: "").contains("wl_ask")) "missing" else "offline"
                    }
                    busy = false
                    onClose()
                    onSaid(if (r == "missing") tr("Asking needs an update in Supabase first.") else Asks.said(r))
                    // The short push comes from the server; the news is fetched now so it doesn't wait.
                    if (r == "ok") try { News.poll(ctx) } catch (e: Exception) {}
                }
            }) { Text(if (busy) tr("Asking…") else tr("Ask if it’s still being bought")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(if (passed) tr("Close") else tr("Cancel")) } }
    )
}

/** "Reserve “X” now?" after a tapped "X is free" (the list opens on that wish). */
@Composable
fun ReserveNowDialog(w: Wish, onReserve: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Reserve “%s” now?", Asks.title(w.title))) },
        text = { Text(tr("It’s free again. Reserve it before someone else does.")) },
        confirmButton = { TextButton(onClick = { onClose(); onReserve() }) { Text(tr("Reserve")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Not now")) } }
    )
}

/** The questions at the top of Gifts I reserved: the gift, where, the time left and the two answers. */
@Composable
fun AskBlock(a: AskItem, onDone: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .lookCard(look, a.ask, look.cardShape(a.ask, small = true)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(tr("Still buying “%s”?", Asks.title(a.title)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = look.cardInk(a.ask))
        Text(
            tr("Someone else would like to buy it, on %s.", Asks.place(a.list, a.owner, a.gift)) + " " + Asks.left(a.until),
            style = MaterialTheme.typography.bodySmall, color = look.cardMuted(a.ask)
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val msg = try { if (Asks.answer(a.ask, true)) tr("Thanks! They’ll hear you’re still buying it.") else tr("This question was already answered.") }
                    catch (e: Exception) { busy = false; tr("Couldn’t answer. Check your connection.") }
                    onDone(msg)
                }
            }) { Text(tr("Yes, I’m buying it")) }
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val msg = try { if (Asks.answer(a.ask, false)) tr("Done. Someone else can reserve it now.") else tr("This question was already answered.") }
                    catch (e: Exception) { busy = false; tr("Couldn’t answer. Check your connection.") }
                    onDone(msg)
                }
            }) { Text(tr("Let someone else")) }
        }
    }
}

/**
 * A wish's status label ("Reserved", "Owned" …). On a list where someone else's reservation can be asked about
 * ([LocalWishDeco] onBadge), tapping it asks "Is it still being bought?".
 */
@Composable
fun TappableBadge(w: Wish, text: String, small: Boolean, modifier: Modifier = Modifier) {
    val on = LocalWishDeco.current?.onBadge
    if (on == null) StatusBadge(text, small, modifier)
    else androidx.compose.foundation.layout.Box(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).clickable { on(w) }
    ) { StatusBadge(text, small) }
}
