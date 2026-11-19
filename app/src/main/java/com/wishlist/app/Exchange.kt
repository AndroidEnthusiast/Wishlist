package com.wishlist.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Gift exchange (Secret Santa), 2.129, with supabase-exchange.sql. Decided with the owner: docs/DECISIONS.md "Side
 * menu, Gifts I reserved and Gift exchange". A stand-alone feature in the side menu.
 *  - Everyone joins with one of their wishlists; only the person who drew you can open it (and reserve on it, like any
 *    friend). A list that is "Only me" goes online for it with its friends' link off (as for wishlist groups).
 *  - The server draws, all at once and never failing when a draw is possible; nobody can read the pairs, not even the
 *    organiser, until the organiser presses Reveal after the exchange date. Rules are checked when they are set.
 *  - The anonymous chats: "Your giftee" (you are hidden) and "Your Secret Santa" (they are hidden).
 *  - News told once (notification + Notifications page): invited, names drawn, your name changed, removed, cancelled,
 *    budget or date changed, you're the organiser now, someone said no thanks, someone waits to be added, a rule
 *    couldn't be kept. Plus the reminder 7 days before the exchange day and a notification on the day.
 *
 * Since the data rebuild (owner, 19 November 2026) this file is the front door (loading, dates and budget helpers);
 * the rest is in parts while callers keep writing Exchanges.…: ExchangeTypes.kt (the data shapes), ExchangeNews.kt
 * (news, messages, reminders), ExchangeMembership.kt (making, joining, settings, invites, photo; [ExchangePhotos]),
 * ExchangeDraw.kt (rules, the draw, reveal, chats, links). ExchangeSameTest proves it all does what it did before.
 */

object Exchanges {
    internal fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    var list by mutableStateOf<List<Exchange>>(emptyList())
        internal set
    var invites by mutableStateOf<List<GxInvite>>(emptyList())
        internal set

    // Photos (ExchangeMembership.kt), read as before.
    val photos: Map<String, String> get() = ExchangePhotos.photos

    private var loaded = false

    /** Something new: an invite, or an unread anonymous message (the red dot on the menu item). */
    val unread: Boolean get() = invites.isNotEmpty() || list.any { it.me.unreadGiftee + it.me.unreadSanta > 0 }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        prefs(c).getString("gx_state", null)?.let { parse(it) }?.let { (x, i) -> list = x; invites = i }
        ExchangePhotos.photos = (dir(c).listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
            .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }
    }

    fun myId(c: Context): String = prefs(c).getString("pf_id", "") ?: ""

    internal fun base(c: Context): JSONObject {
        val p = prefs(c)
        return JSONObject().put("p_id", p.getString("pf_id", "")).put("p_token", p.getString("pf_token", ""))
    }

    fun get(id: String?): Exchange? = list.firstOrNull { it.id == id }
    fun isOrganiser(c: Context, x: Exchange) = x.organiser == myId(c)

    /** The id of your own list that you joined [x] with (null: none, or it was deleted). */
    fun myListId(c: Context, x: Exchange): String? {
        val code = x.me.list?.code ?: return null
        return Repo.allMine(c).firstOrNull { it.code == code }?.id
    }

    /** "12 days to the exchange 🎄", "Tomorrow is the exchange 🎄", "Today is the exchange! 🎄", or null. */
    fun countdown(x: Exchange, today: LocalDate = LocalDate.now()): String? {
        val d = x.day ?: return null
        val n = ChronoUnit.DAYS.between(today, d)
        val e = GxThemes.emoji(x.theme)
        return when {
            n < 0 -> null
            n == 0L -> tr("Today is the exchange! %s", e)
            n == 1L -> tr("Tomorrow is the exchange %s", e)
            else -> tr("%1\$d days to the exchange %2\$s", n.toInt(), e)
        }
    }

    fun budgetText(x: Exchange): String? = x.budget?.let { tr("Budget: %s", money(it, x.currency.ifBlank { "EUR" })) }

    /** A wish costs more than the budget (only when both are in the same currency, or the wish has none). */
    fun overBudget(x: Exchange, w: Wish): Boolean {
        val b = x.budget ?: return false
        val p = w.price ?: return false
        if (w.currency.isNotBlank() && x.currency.isNotBlank() && w.currency != x.currency) return false
        return p > b + 0.005
    }

    // ---- loading ----

    internal fun day(s: String?): LocalDate? = try {
        if (s.isNullOrBlank() || s == "null") null else LocalDate.parse(s.take(10))
    } catch (e: Exception) {
        null
    }

    internal fun num(o: JSONObject, k: String): Double? = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() && it > 0 }

    internal fun card(o: JSONObject?): GroupList? =
        o?.let { GroupList(it.optString("code"), it.optString("name"), it.optInt("count"), it.optString("image")) }

    fun parseOne(g: JSONObject): Exchange {
        val ma = g.optJSONArray("members") ?: JSONArray()
        val ia = g.optJSONArray("invited") ?: JSONArray()
        val ea = g.optJSONArray("excl") ?: JSONArray()
        val me = g.optJSONObject("me") ?: JSONObject()
        val gf = me.optJSONObject("giftee")
        val bl = g.optJSONObject("blocked")
        return Exchange(
            g.getString("id"), g.optString("link"), g.optString("name"), g.optString("organiser"), g.optString("organiser_name"),
            num(g, "budget"), g.optString("currency"), day(g.optString("day")), g.optString("theme", "xmas"), g.optString("message"),
            g.optLong("pv"), g.optBoolean("drawn"), g.optBoolean("revealed"),
            (0 until ma.length()).map { j ->
                val m = ma.getJSONObject(j)
                GxMember(
                    m.getString("id"), m.optString("name"), m.optLong("pv"), m.optBoolean("web"), m.optString("code"),
                    m.optBoolean("friend"), if (m.isNull("seen")) null else m.optBoolean("seen"), m.optBoolean("waiting"),
                    if (m.isNull("gave_to")) null else m.optString("gave_to")
                )
            },
            (0 until ia.length()).map { ia.getJSONObject(it).let { o -> o.optString("id") to o.optString("name") } },
            (0 until ea.length()).map { ea.getJSONArray(it).let { a -> a.getString(0) to a.getString(1) } },
            bl?.let { b ->
                val s = b.optJSONArray("stuck") ?: JSONArray()
                GxStuck((0 until s.length()).map { s.getString(it) }, b.optInt("left"))
            },
            GxMe(
                card(me.optJSONObject("list")), me.optBoolean("seen"), me.optBoolean("waiting"), me.optBoolean("has_santa"),
                gf?.let { GxGiftee(it.optString("id"), it.optString("name"), it.optLong("pv"), it.optBoolean("web"), card(it.optJSONObject("list"))) },
                me.optInt("unread_giftee"), me.optInt("unread_santa")
            )
        )
    }

    internal fun parse(t: String): Pair<List<Exchange>, List<GxInvite>>? = try {
        val o = JSONObject(t)
        val xa = o.optJSONArray("exchanges") ?: JSONArray()
        val xs = (0 until xa.length()).mapNotNull { i -> xa.optJSONObject(i)?.let { parseOne(it) } }
        val ia = o.optJSONArray("invites") ?: JSONArray()
        val ins = (0 until ia.length()).map { i ->
            val v = ia.getJSONObject(i)
            GxInvite(
                v.getString("gx"), v.optString("name"), v.optString("link"), v.optString("by"), v.optString("by_name"),
                v.optString("theme", "xmas"), num(v, "budget"), v.optString("currency"), day(v.optString("day")), v.optInt("count")
            )
        }
        xs to ins
    } catch (e: Exception) {
        null
    }

    /** Loads your exchanges and invites. Throws when offline (the last state stays). */
    suspend fun refresh(c: Context) = lock.withLock {
        load(c)
        Friends.ensure(c)
        fetchLocked(c, onScreen = true)
    }

    /** The background check (and a push): notifications for invites, news, messages and the exchange day. */
    suspend fun poll(c: Context) {
        load(c)
        if (!prefs(c).getBoolean("pf_made", false)) return
        lock.withLock { fetchLocked(c, onScreen = false) }
    }

    internal suspend fun fetchLocked(c: Context, onScreen: Boolean) {
        val t = Cloud.rpc("wl_gx_state", base(c))
        val (x, i) = parse(t) ?: return
        val before = list
        list = x
        invites = i
        prefs(c).edit().putString("gx_state", t).apply()
        news(c, JSONObject(t).optJSONArray("news") ?: JSONArray(), i, onScreen)
        messages(c, before, x, onScreen)
        days(c, x)
        loadPhotos(c)
        // Photos of exchanges you organise move from the database to files, once (PeoplePhotos, 2.185).
        if (onScreen) for (gx in x) if (gx.organiser == myId(c) && gx.pv > 0) {
            PeoplePhotos.moveOld(c, "x:" + gx.id, { Cloud.rpc("wl_gx_photo", base(c).put("p_gx", gx.id)) }) { bytes ->
                PeoplePhotos.save("x/" + gx.id, base(c), "wl_gx_photo_url", base(c).put("p_gx", gx.id), bytes)
            }
        }
    }

    /** The server's answer to Draw: ok, too few people, people still invited, or stuck (rules). */
    data class DrawResult(val ok: Boolean, val few: Boolean = false, val pending: List<String> = emptyList(), val stuck: GxStuck? = null)
}
