package com.wishlist.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
 */

data class GxGiftee(val id: String, val name: String, val pv: Long, val web: Boolean, val list: GroupList?)

data class GxMe(
    val list: GroupList?, val seen: Boolean, val waiting: Boolean, val hasSanta: Boolean, val giftee: GxGiftee?,
    val unreadGiftee: Int, val unreadSanta: Int
)

data class GxMember(
    val id: String, val name: String, val pv: Long, val web: Boolean, val code: String, val friend: Boolean,
    /** The draw check (organiser only): opened their name. Null for everyone else. */
    val seen: Boolean?,
    val waiting: Boolean,
    /** After the reveal: whom they had. */
    val gaveTo: String?
)

/** A draw that the rules make impossible: these people would only have [left] others to draw between them. */
data class GxStuck(val names: List<String>, val left: Int)

data class Exchange(
    val id: String, val link: String, val name: String, val organiser: String, val organiserName: String,
    val budget: Double?, val currency: String, val day: LocalDate?, val theme: String, val message: String, val pv: Long,
    val drawn: Boolean, val revealed: Boolean, val members: List<GxMember>,
    /** Open invites: profile id to name. */
    val invited: List<Pair<String, String>>,
    /** The rules (organiser only): giver may not draw receiver. */
    val excl: List<Pair<String, String>>,
    val blocked: GxStuck?, val me: GxMe
) {
    fun nameOf(id: String) = members.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: tr("Someone")
}

data class GxInvite(
    val gx: String, val name: String, val link: String, val by: String, val byName: String, val theme: String,
    val budget: Double?, val currency: String, val day: LocalDate?, val count: Int
)

data class GxPreview(
    val id: String, val name: String, val organiser: String, val count: Int, val theme: String, val budget: Double?,
    val currency: String, val day: LocalDate?, val drawn: Boolean
)

data class GxMsg(val id: Long, val mine: Boolean, val body: String, val at: String, val read: Boolean)

/** The themes: their emoji and the words for the countdown. */
object GxThemes {
    val all = listOf("xmas", "sint", "bday", "office", "plain")
    fun label(t: String) = when (t) {
        "sint" -> tr("Sinterklaas"); "bday" -> tr("Birthday"); "office" -> tr("Office"); "plain" -> tr("Plain"); else -> tr("Christmas")
    }
    fun emoji(t: String) = when (t) { "sint" -> "🎁"; "bday" -> "🎈"; "office" -> "☕"; "plain" -> "✨"; else -> "🎄" }
}

object Exchanges {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    var list by mutableStateOf<List<Exchange>>(emptyList())
        private set
    var invites by mutableStateOf<List<GxInvite>>(emptyList())
        private set
    /** Exchange photos on this phone, by id (a file address). */
    var photos by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var loaded = false

    /** Something new: an invite, or an unread anonymous message (the red dot on the menu item). */
    val unread: Boolean get() = invites.isNotEmpty() || list.any { it.me.unreadGiftee + it.me.unreadSanta > 0 }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        prefs(c).getString("gx_state", null)?.let { parse(it) }?.let { (x, i) -> list = x; invites = i }
        photos = (dir(c).listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
            .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }
    }

    fun myId(c: Context): String = prefs(c).getString("pf_id", "") ?: ""

    private fun base(c: Context): JSONObject {
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

    private fun day(s: String?): LocalDate? = try {
        if (s.isNullOrBlank() || s == "null") null else LocalDate.parse(s.take(10))
    } catch (e: Exception) {
        null
    }

    private fun num(o: JSONObject, k: String): Double? = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() && it > 0 }

    private fun card(o: JSONObject?): GroupList? =
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

    private fun parse(t: String): Pair<List<Exchange>, List<GxInvite>>? = try {
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

    private suspend fun fetchLocked(c: Context, onScreen: Boolean) {
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
    }

    private fun notify(c: Context, gx: String, title: String, text: String, kind: String, onScreen: Boolean) {
        if (!onScreen) Alerts.notifyExchange(c, gx, title, text, kind, "e:$gx")
    }

    /** Invites and news are told once: a notification (only in the background) and a line on the Notifications page. */
    private suspend fun news(c: Context, news: JSONArray, ins: List<GxInvite>, onScreen: Boolean) {
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
    private fun messages(c: Context, before: List<Exchange>, now: List<Exchange>, onScreen: Boolean) {
        if (onScreen) return
        for (x in now) {
            val old = before.firstOrNull { it.id == x.id }?.me
            if (x.me.unreadSanta > (old?.unreadSanta ?: 0) && GxChatNav.open != x.id + ":santa")
                Alerts.notifyExchange(c, x.id, tr("Your Secret Santa wrote"), tr("In “%s”. Open to read and answer.", x.name), "gx_msg", "e:${x.id}", msg = true)
            if (x.me.unreadGiftee > (old?.unreadGiftee ?: 0) && GxChatNav.open != x.id + ":giftee")
                Alerts.notifyExchange(c, x.id, tr("%s answered", x.me.giftee?.name ?: tr("Your giftee")), tr("In “%s”. Open to read.", x.name), "gx_msg", "e:${x.id}", msg = true)
        }
    }

    /** 7 days before the exchange day, and on the day itself: once each. */
    private fun days(c: Context, now: List<Exchange>) {
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

    // ---- photos ----

    private fun dir(c: Context) = File(c.filesDir, "exchanges").apply { mkdirs() }

    private suspend fun loadPhotos(c: Context) {
        val d = dir(c)
        val want = list.filter { it.pv > 0 }.associate { it.id to "${it.id}_${it.pv}.jpg" }
        val out = HashMap<String, String>()
        for ((id, name) in want) {
            val f = File(d, name)
            if (!f.exists()) try {
                val b64 = Cloud.rpc("wl_gx_photo", base(c).put("p_gx", id)).trim().removeSurrounding("\"")
                if (b64.isEmpty()) continue
                withContext(Dispatchers.IO) { f.writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
            } catch (e: Exception) {
                continue
            }
            out[id] = Uri.fromFile(f).toString()
        }
        (d.listFiles() ?: emptyArray()).filter { it.name !in want.values }.forEach { it.delete() }
        photos = out
    }

    /** The organiser's own photo instead of the drawn cover ([uri] from the photo picker), or null to remove it. */
    suspend fun setPhoto(c: Context, gx: String, uri: Uri?) {
        val b64 = if (uri == null) "" else withContext(Dispatchers.IO) {
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw CloudException("photo")
            val w = minOf(900, bmp.width)
            val small = Bitmap.createScaledBitmap(bmp, w, (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1), true)
            val out = java.io.ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
        Cloud.rpc("wl_gx_set_photo", base(c).put("p_gx", gx).put("p_photo", b64))
        refresh(c)
    }

    // ---- your list ----

    /** Your list [listId] online for an exchange: its code and owner token ("Only me" stays "Only me" for friends). */
    private suspend fun listOnline(c: Context, listId: String): Pair<String, String> {
        if (Repo.mine(c, listId).code == null) {
            Sync.publishFor(c, listId)
            ListLink.set(c, listId, false)
        }
        val d = Repo.mine(c, listId)
        return (d.code ?: throw CloudException("no code")) to (d.token ?: throw CloudException("no token"))
    }

    // ---- making, joining, leaving ----

    private fun JSONObject.settings(budget: Double?, currency: String, day: LocalDate?, theme: String, message: String) =
        put("p_budget", budget ?: JSONObject.NULL).put("p_currency", currency).put("p_day", day?.toString() ?: JSONObject.NULL)
            .put("p_theme", theme).put("p_message", message.trim())

    /** A new exchange: you organise it and join with your list [listId]. Returns its id. */
    suspend fun create(
        c: Context, name: String, listId: String, budget: Double?, currency: String, day: LocalDate?, theme: String, message: String
    ): String {
        Friends.ensure(c)
        val (code, token) = listOnline(c, listId)
        val id = Sync.newKey()
        Cloud.rpc(
            "wl_gx_create", base(c).put("p_gx", id).put("p_link", Sync.newKey()).put("p_name", name.trim())
                .put("p_list", code).put("p_list_token", token).settings(budget, currency, day, theme, message)
        )
        refresh(c)
        return id
    }

    suspend fun preview(link: String): GxPreview? {
        val t = Cloud.rpc("wl_gx_info", JSONObject().put("p_link", link)).trim()
        if (t.isEmpty() || t == "null") return null
        val o = JSONObject(t)
        return GxPreview(
            o.optString("id"), o.optString("name"), o.optString("organiser"), o.optInt("count"), o.optString("theme", "xmas"),
            num(o, "budget"), o.optString("currency"), day(o.optString("day")), o.optBoolean("drawn")
        )
    }

    /** Join with link [link] or invite [gx], with your list [listId]. The exchange's id, or null when it doesn't work any more. */
    suspend fun join(c: Context, link: String?, gx: String?, listId: String): String? {
        Friends.ensure(c)
        val (code, token) = listOnline(c, listId)
        val t = Cloud.rpc(
            "wl_gx_join", base(c).put("p_link", link ?: "").put("p_gx", gx ?: "").put("p_list", code).put("p_list_token", token)
        ).trim()
        if (t.isEmpty() || t == "null") return null
        val id = JSONObject(t).optString("id")
        refresh(c)
        return id
    }

    suspend fun setList(c: Context, gx: String, listId: String) {
        val (code, token) = listOnline(c, listId)
        Cloud.rpc("wl_gx_set_list", base(c).put("p_gx", gx).put("p_list", code).put("p_list_token", token))
        refresh(c)
    }

    suspend fun decline(c: Context, gx: String) {
        Cloud.rpc("wl_gx_decline", base(c).put("p_gx", gx))
        invites = invites.filter { it.gx != gx }
        refresh(c)
    }

    suspend fun leave(c: Context, gx: String, next: String?) {
        Cloud.rpc("wl_gx_leave", base(c).put("p_gx", gx).put("p_next", next ?: ""))
        list = list.filter { it.id != gx }
        refresh(c)
    }

    suspend fun remove(c: Context, gx: String, member: String) {
        Cloud.rpc("wl_gx_remove", base(c).put("p_gx", gx).put("p_member", member))
        refresh(c)
    }

    suspend fun handover(c: Context, gx: String, next: String) {
        Cloud.rpc("wl_gx_handover", base(c).put("p_gx", gx).put("p_next", next))
        refresh(c)
    }

    suspend fun saveSettings(
        c: Context, gx: String, name: String, budget: Double?, currency: String, day: LocalDate?, theme: String, message: String
    ) {
        Cloud.rpc("wl_gx_settings", base(c).put("p_gx", gx).put("p_name", name.trim()).settings(budget, currency, day, theme, message))
        refresh(c)
    }

    suspend fun cancel(c: Context, gx: String) {
        Cloud.rpc("wl_gx_cancel", base(c).put("p_gx", gx))
        list = list.filter { it.id != gx }
        refresh(c)
    }

    /** Invites friends [ids] (organiser): online, plus a chat message with the link. Returns how many failed. */
    suspend fun invite(c: Context, x: Exchange, ids: List<String>): Int {
        var failed = 0
        for (id in ids) {
            try {
                Cloud.rpc("wl_gx_invite", base(c).put("p_gx", x.id).put("p_friend", id))
            } catch (e: Exception) {
                failed++
                continue
            }
            try {
                Chat.send(c, id, ChatContent(true, "exchange", x.id, x.name, url = link(x.link)))
            } catch (e: Exception) {
            }
        }
        try { refresh(c) } catch (e: Exception) {}
        return failed
    }

    suspend fun uninvite(c: Context, gx: String, id: String) {
        Cloud.rpc("wl_gx_uninvite", base(c).put("p_gx", gx).put("p_friend", id))
        refresh(c)
    }

    suspend fun addLate(c: Context, gx: String, member: String) {
        Cloud.rpc("wl_gx_add_late", base(c).put("p_gx", gx).put("p_member", member))
        refresh(c)
    }

    // ---- rules and the draw ----

    /** Adds a rule. Null when saved; otherwise who would be stuck (the rule is not saved). */
    suspend fun addRule(c: Context, gx: String, giver: String, receiver: String, both: Boolean): GxStuck? {
        val o = JSONObject(Cloud.rpc("wl_gx_excl_add", base(c).put("p_gx", gx).put("p_giver", giver).put("p_receiver", receiver).put("p_both", both)))
        refresh(c)
        if (o.optBoolean("ok")) return null
        val s = o.optJSONArray("stuck") ?: JSONArray()
        return GxStuck((0 until s.length()).map { s.getString(it) }, o.optInt("left"))
    }

    suspend fun removeRule(c: Context, gx: String, giver: String, receiver: String, both: Boolean) {
        Cloud.rpc("wl_gx_excl_del", base(c).put("p_gx", gx).put("p_giver", giver).put("p_receiver", receiver).put("p_both", both))
        refresh(c)
    }

    /** The server's answer to Draw: ok, too few people, people still invited, or stuck (rules). */
    data class DrawResult(val ok: Boolean, val few: Boolean = false, val pending: List<String> = emptyList(), val stuck: GxStuck? = null)

    suspend fun draw(c: Context, gx: String, without: Boolean): DrawResult {
        val o = JSONObject(Cloud.rpc("wl_gx_draw", base(c).put("p_gx", gx).put("p_without", without)))
        refresh(c)
        val pend = o.optJSONArray("pending")
        val stuck = o.optJSONArray("stuck")
        return DrawResult(
            o.optBoolean("ok"), o.optBoolean("few"),
            pend?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            stuck?.let { a -> GxStuck((0 until a.length()).map { a.getString(it) }, o.optInt("left")) }
        )
    }

    /** You open your name (the reveal moment). */
    suspend fun open(c: Context, gx: String): Exchange? {
        val t = Cloud.rpc("wl_gx_open", base(c).put("p_gx", gx)).trim()
        refresh(c)
        return if (t.isEmpty() || t == "null") null else parseOne(JSONObject(t))
    }

    /** Your giftee's wishlist (null before you opened your name, or when they have none). */
    suspend fun gifteeList(c: Context, gx: String): RemoteList? {
        val t = Cloud.rpc("wl_gx_list", base(c).put("p_gx", gx)).trim()
        if (t.isEmpty() || t == "null") return null
        return Cloud.parseRemote(JSONObject(t))
    }

    suspend fun reveal(c: Context, gx: String): Boolean {
        val ok = Cloud.rpc("wl_gx_reveal", base(c).put("p_gx", gx)).trim() == "true"
        refresh(c)
        return ok
    }

    // ---- the anonymous chats ("giftee" or "santa") ----

    suspend fun chat(c: Context, gx: String, side: String, after: Long): List<GxMsg> {
        val a = JSONArray(Cloud.rpc("wl_gx_chat", base(c).put("p_gx", gx).put("p_side", side).put("p_after", after)))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            GxMsg(o.getLong("id"), o.optBoolean("mine"), o.optString("body"), o.optString("at"), o.optBoolean("read"))
        }
    }

    /** Sends a message. False when that chat isn't open (any more). */
    suspend fun send(c: Context, gx: String, side: String, body: String): Boolean =
        (Cloud.rpc("wl_gx_send", base(c).put("p_gx", gx).put("p_side", side).put("p_body", body)).trim().toLongOrNull() ?: 0L) > 0L

    suspend fun seen(c: Context, gx: String, side: String, upto: Long) {
        Cloud.rpc("wl_gx_chat_seen", base(c).put("p_gx", gx).put("p_side", side).put("p_upto", upto))
    }

    /** Before a browser member's profile is taken over by the app (Groups.takeover): their exchanges move too. */
    suspend fun takeover(c: Context, webId: String, webToken: String) {
        val p = prefs(c)
        try {
            Cloud.rpc(
                "wl_gx_takeover", JSONObject().put("p_web", webId).put("p_web_token", webToken)
                    .put("p_id", p.getString("pf_id", "")).put("p_token", p.getString("pf_token", ""))
            )
        } catch (e: CloudException) {
            // Before supabase-exchange.sql there is nothing to move.
        }
    }

    // ---- links ----

    /** The exchange link, through the page exchange/ (its link preview), which opens the exchange page. */
    fun link(key: String): String = Config.SHARE_URL.trimEnd('/') + "/exchange/?x=" + key

    fun share(c: Context, x: Exchange) {
        val text = tr("Join my gift exchange “%1\$s”: %2\$s", x.name, link(x.link))
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(i, tr("Share exchange link")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val LINK = Regex("[?&]X=([A-HJ-NP-Z2-9]{20})(?![A-Z0-9])")

    /** The exchange link key in text (a web link or wishlist://exchange?x=…), or null. */
    fun parseLink(s: String): String? = LINK.find(s.uppercase())?.groupValues?.get(1)
}

/** Which exchange is open, and requests to open one or to join. */
object ExchangeNav {
    var open by mutableStateOf<String?>(null)
    var joinLink by mutableStateOf<String?>(null)
    var joinGx by mutableStateOf<String?>(null)
    /** A new exchange was just made: its page opens with "Invite friends". */
    var inviteNow by mutableStateOf<String?>(null)
    /** Set when something asked for the Gift exchange screen; the app switches to it and clears this. */
    var show by mutableStateOf(false)

    fun openInvite(gx: String?, link: String?) {
        if (gx != null && Exchanges.get(gx) != null) { open = gx; show = true; return }
        open = null
        joinGx = gx?.takeIf { it.isNotEmpty() }
        joinLink = link
        show = true
    }

    fun openExchange(gx: String?) { open = gx; show = true }
}

/** Which anonymous chat is on screen ("<id>:santa" or "<id>:giftee"), so no notification comes for it. */
object GxChatNav {
    @Volatile
    var open: String? = null
}
