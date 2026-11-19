package com.wishlist.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of gift exchanges in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`,
 * "The data rebuild"). With a pretend server (Cloud.pretendServer) a phone with an exchange saved by an older version
 * goes through everything Exchanges does: loading, invites and news, the draw, anonymous messages, the reminder a week
 * before and on the day (the background check run twice, and after a restart: told once), making an exchange with an
 * "Only me" list, previews, joining (dead link, offline), every organiser action, rules (also one that can't be kept),
 * the draw (ok, too few, stuck), opening your name, the reveal, the chats, a browser member's exchanges moving in,
 * links, helpers, the photo and a restart. After each step it writes down what was sent, what the app holds, what is
 * saved, the notifications and the photo files. Dates are written relative to today ("<today+5>").
 * The result must match `src/test/resources/exchange-same.txt` word for word: recorded with Exchange.kt as it was before
 * the rebuild (2.259). A MEANT change: delete the file, run once to record, read the difference in git.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class ExchangeSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/exchange-same.txt")
    private val out = StringBuilder()
    private val calls = mutableListOf<String>()
    private val randoms = mutableListOf<String>()
    private val start = System.currentTimeMillis()
    private val today = LocalDate.now()
    private var offline = false
    private var state = JSONObject()
    private val news = JSONArray()
    private var newsSeen = 0L

    @After fun noPretendServer() { Cloud.pretendServer = null }

    private fun member(id: String, name: String, pv: Long = 0, seen: Boolean? = null, gaveTo: String? = null) =
        JSONObject().put("id", id).put("name", name).put("pv", pv).put("web", false).put("code", "").put("friend", false)
            .put("seen", seen ?: JSONObject.NULL).put("waiting", false).put("gave_to", gaveTo ?: JSONObject.NULL)

    private fun exchange(id: String, day: LocalDate?, drawn: Boolean, giftee: String? = null, unreadSanta: Int = 0, unreadGiftee: Int = 0, pv: Long = 0) =
        JSONObject().put("id", id).put("link", "LINK$id").put("name", "Santa $id").put("organiser", "me1").put("organiser_name", "Ann")
            .put("budget", 20).put("currency", "EUR").put("day", day?.toString() ?: JSONObject.NULL).put("theme", "xmas").put("message", "Be kind")
            .put("pv", pv).put("drawn", drawn).put("revealed", false)
            .put("members", JSONArray().put(member("me1", "Ann", seen = drawn)).put(member("m2", "Kim", seen = false)).put(member("m3", "Sam", seen = drawn)))
            .put("invited", JSONArray().put(JSONObject().put("id", "f9").put("name", "Lee")))
            .put("excl", JSONArray().put(JSONArray().put("m2").put("m3")))
            .put("me", JSONObject().put("list", JSONObject().put("code", "MINECODE").put("name", "Mine").put("count", 1).put("image", ""))
                .put("seen", drawn).put("waiting", false).put("has_santa", drawn)
                .put("giftee", if (giftee == null) JSONObject.NULL else JSONObject().put("id", "m2").put("name", giftee).put("pv", 0).put("web", false))
                .put("unread_giftee", unreadGiftee).put("unread_santa", unreadSanta))

    private fun invite(gx: String) = JSONObject().put("gx", gx).put("name", "Office $gx").put("link", "LINK$gx").put("by", "id-bob")
        .put("by_name", "Bob").put("theme", "office").put("budget", 10).put("currency", "EUR").put("day", today.plusDays(30).toString()).put("count", 4)

    private fun setState(xs: List<JSONObject>, ins: List<JSONObject>) { state = JSONObject().put("exchanges", JSONArray(xs)).put("invites", JSONArray(ins)) }

    private fun answer(fn: String, b: JSONObject): String {
        if (fn == "wl_gx_create") for (k in listOf("p_gx", "p_link")) b.optString(k).let { if (it !in randoms) randoms += it }
        if (fn == "wl_create_list") for (k in listOf("p_code", "p_token")) b.optString(k).let { if (it !in randoms) randoms += it }
        synchronized(calls) { calls += fn + " " + b.keys().asSequence().sorted().joinToString(",") { "$it=${b.get(it)}" } }
        if (offline) throw java.io.IOException("offline")
        return when (fn) {
            "wl_gx_state" -> JSONObject(state.toString()).put("news", JSONArray((0 until news.length()).map { news.getJSONObject(it) }.filter { it.getLong("id") > newsSeen })).toString()
            "wl_gx_news_seen" -> { newsSeen = b.getLong("p_upto"); "" }
            "wl_gx_photo" -> "\"" + android.util.Base64.encodeToString(byteArrayOf(1, 2, 3), android.util.Base64.NO_WRAP) + "\""
            "wl_gx_info" -> if (b.getString("p_link") == "LINKX1") JSONObject().put("id", "X1").put("name", "Santa X1").put("organiser", "Ann").put("count", 3)
                .put("theme", "sint").put("budget", 15).put("currency", "EUR").put("day", today.plusDays(9).toString()).put("drawn", false).toString() else "null"
            "wl_gx_join" -> if (b.getString("p_link") == "DEAD") "null" else JSONObject().put("id", "X2").toString()
            "wl_gx_invite" -> if (b.getString("p_friend") == "bad") throw java.io.IOException("refused") else ""
            "wl_gx_excl_add" -> if (b.getString("p_giver") == "m3") JSONObject().put("ok", false).put("stuck", JSONArray().put("Kim").put("Sam")).put("left", 1).toString()
                else JSONObject().put("ok", true).toString()
            "wl_gx_draw" -> when (b.getString("p_gx")) {
                "X1" -> JSONObject().put("ok", true)
                "FEW" -> JSONObject().put("ok", false).put("few", true)
                else -> JSONObject().put("ok", false).put("pending", JSONArray().put("Lee")).put("stuck", JSONArray().put("Kim")).put("left", 0)
            }.toString()
            "wl_gx_open" -> exchange("X1", today.plusDays(5), true, giftee = "Kim").toString()
            "wl_gx_reveal" -> "true"
            "wl_gx_chat" -> JSONArray().put(JSONObject().put("id", 7).put("mine", false).put("body", "Hi!").put("at", "2020-01-02T10:00:00Z").put("read", false)).toString()
            "wl_gx_send" -> if (b.getString("p_side") == "santa") "12" else "0"
            "wl_gx_list", "wl_chat_key" -> ""
            else -> ""
        }
    }

    private fun normal(s: String): String {
        var t = s
        randoms.forEachIndexed { i, r -> t = t.replace(r, "<R${i + 1}>") }
        t = t.replace(c.filesDir.absolutePath, "<FILES>")
        t = Regex("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE[A-Za-z0-9+/=]+").replace(t, "<CHAT KEY>")
        for (n in 40 downTo -2) t = t.replace(today.plusDays(n.toLong()).toString(), "<today${if (n < 0) "" else "+"}$n>")
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(t) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    private fun idle() = runBlocking {
        val scope = Sync::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(Sync) as kotlinx.coroutines.CoroutineScope
        repeat(50) {
            val kids = scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
            if (kids.isEmpty()) return@runBlocking
            kids.forEach { it.join() }
        }
    }

    private fun note(step: String) {
        idle()
        val x = Exchanges
        out.append("## ").append(step).append('\n')
        // Sorted: some calls run in the background, so only their order may differ between runs.
        synchronized(calls) { calls.sorted().forEach { out.append("sent ").append(it).append('\n') }; calls.clear() }
        x.list.forEach { out.append("exchange $it\n") }
        x.invites.forEach { out.append("invite $it\n") }
        out.append("photos=${x.photos.toSortedMap()} unread=${x.unread}\n")
        prefs.all.toSortedMap().filterKeys { k -> listOf("gx_", "link_off", "photo_moved").any { k.startsWith(it) } }
            .forEach { (k, v) -> out.append("pref $k=${if (v is Set<*>) v.map { it.toString() }.sorted() else v}\n") }
        Notifications.all.forEach { n -> out.append("note ${n.kind} ${n.listKey} '${n.title}' extra='${n.currency}' read=${n.read}\n") }
        shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.forEach { n ->
            out.append("buzz '${n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)}' '${n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)}'\n")
        }
        File(c.filesDir, "exchanges").list()?.sorted()?.let { out.append("photo files=$it\n") }
        for (m in Repo.allMine(c)) out.append("list ${m.id} '${m.name}' code=${m.code} token=${m.token} friends=${Friends.isVisible(m.id)}\n")
    }

    private fun restartExchanges() {
        Exchanges::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Exchanges, false)
        Exchanges.load(c)
    }

    @Test fun exchangesDoExactlyTheSame() = runBlocking {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        File(c.filesDir, "exchanges").deleteRecursively()
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Cloud.pretendServer = ::answer
        setState(listOf(exchange("X1", null, false)), emptyList())
        prefs.edit().putString("device", "device-1").putString("pf_id", "me1").putString("pf_token", "tok1").putBoolean("pf_made", true)
            .putString("sh_me", "Ann").putString("gx_state", state.toString()).commit()
        File(c.filesDir, "exchanges").apply { mkdirs() }.resolve("OLD_1.jpg").writeBytes(byteArrayOf(9))
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
        Repo.setShareFor(c, MAIN_LIST, "MINECODE", "minetok")
        Friends::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Friends, false)
        Friends.load(c)
        restartExchanges(); note("an exchange saved by an older version, read at a start")

        setState(listOf(exchange("X1", today.plusDays(5), false, pv = 1)), listOf(invite("X7")))
        news.put(JSONObject().put("id", 1).put("gx", "X1").put("name", "Santa X1").put("kind", "declined").put("who", "Lee"))
        Exchanges.refresh(c); note("refresh on screen: an invite and a No thanks")

        setState(listOf(exchange("X1", today.plusDays(5), true, giftee = "Kim", unreadSanta = 1, pv = 1)), listOf(invite("X7"), invite("X8")))
        for ((id, kind) in listOf(2 to "drawn", 3 to "settings", 4 to "removed", 5 to "cancelled", 6 to "organiser", 7 to "waiting", 8 to "rules", 9 to "changed", 10 to "unknown"))
            news.put(JSONObject().put("id", id).put("gx", "X1").put("name", "Santa X1").put("kind", kind).put("who", "Jo"))
        Exchanges.poll(c); note("background check: drawn, every kind of news, a message, a week to go")
        Exchanges.poll(c); note("background check again: nothing told twice")
        setState(listOf(exchange("X1", today, true, giftee = "Kim", unreadSanta = 1, unreadGiftee = 2, pv = 1)), emptyList())
        Exchanges.poll(c); restartExchanges(); Exchanges.poll(c); note("the day itself, and a giftee's answer, after a restart: once each")

        val onlyMe = Repo.createMine(c, "Secret", "")
        out.append("made ${Exchanges.create(c, " Office ", onlyMe, 15.0, "EUR", today.plusDays(20), "office", " hi ").length} long\n")
        out.append("preview=${Exchanges.preview("LINKX1")} dead=${Exchanges.preview("NOPE")}\n")
        out.append("join=${Exchanges.join(c, "LINKX2", null, MAIN_LIST)} dead=${Exchanges.join(c, "DEAD", null, MAIN_LIST)}\n")
        offline = true
        out.append("offline join=${runCatching { Exchanges.join(c, null, "X7", MAIN_LIST) }.exceptionOrNull()?.message} draw=${runCatching { Exchanges.draw(c, "X1", false) }.exceptionOrNull()?.message} refresh=${runCatching { Exchanges.refresh(c) }.exceptionOrNull()?.message}\n")
        offline = false
        note("made with an Only me list, preview, join (dead, offline)")

        Exchanges.setList(c, "X1", onlyMe); Exchanges.decline(c, "X7"); Exchanges.remove(c, "X1", "m3"); Exchanges.handover(c, "X1", "m2")
        Exchanges.saveSettings(c, "X1", " Santa! ", null, "EUR", null, "plain", " x ")
        out.append("invite failed=${Exchanges.invite(c, Exchanges.get("X1")!!, listOf("f1", "bad"))}\n")
        Exchanges.uninvite(c, "X1", "f9"); Exchanges.addLate(c, "X1", "m4")
        note("organiser actions and invites")

        out.append("rule ok=${Exchanges.addRule(c, "X1", "me1", "m2", true)} stuck=${Exchanges.addRule(c, "X1", "m3", "m2", false)}\n")
        Exchanges.removeRule(c, "X1", "m2", "m3", true)
        out.append("draw=${Exchanges.draw(c, "X1", false)} few=${Exchanges.draw(c, "FEW", true)} stuck=${Exchanges.draw(c, "X9", false)}\n")
        out.append("open=${Exchanges.open(c, "X1")?.me?.giftee?.name} giftee list=${Exchanges.gifteeList(c, "X1")} reveal=${Exchanges.reveal(c, "X1")}\n")
        out.append("chat=${Exchanges.chat(c, "X1", "santa", 0)} sent=${Exchanges.send(c, "X1", "santa", "Thanks")} closed=${Exchanges.send(c, "X1", "giftee", "Hey")}\n")
        Exchanges.seen(c, "X1", "santa", 7); Exchanges.takeover(c, "WEB", "WT")
        note("rules, the draw, your name, reveal, chats, a browser member")

        val x1 = Exchanges.get("X1")!!
        out.append("organiser=${Exchanges.isOrganiser(c, x1)} mine=${Exchanges.myListId(c, x1)} budget=${Exchanges.budgetText(x1)}\n")
        for (n in listOf(-1L, 0L, 1L, 12L)) out.append("countdown $n=${Exchanges.countdown(x1.copy(day = today.plusDays(n)), today)}\n")
        out.append("countdown none=${Exchanges.countdown(x1.copy(day = null))}\n")
        val w = Wish(1, "", "Mug", "", "", 25.0, null, "EUR", "")
        out.append("over=${Exchanges.overBudget(x1.copy(budget = 20.0, currency = "EUR"), w)} ${Exchanges.overBudget(x1.copy(budget = 20.0, currency = "GBP"), w)} ${Exchanges.overBudget(x1.copy(budget = 30.0), w)} ${Exchanges.overBudget(x1.copy(budget = null), w)}\n")
        for (s in listOf("https://x/exchange/?x=abcdefghjkmnpqrstuvw", "wishlist://exchange?X=ABCDEFGHJKMNPQRSTUVW", "x=SHORT")) out.append("link $s -> ${Exchanges.parseLink(s)}\n")
        out.append("share link ${Exchanges.link("ABC")} themes=${GxThemes.all.map { GxThemes.emoji(it) + GxThemes.label(it) }}\n")
        note("helpers and links")

        Exchanges.setPhoto(c, "X1", null, HeroEdit(), 1f); note("photo removed")
        Exchanges.leave(c, "X1", "m2"); Exchanges.cancel(c, "X2"); note("left and cancelled")
        restartExchanges(); note("started again")

        val now = normal(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
