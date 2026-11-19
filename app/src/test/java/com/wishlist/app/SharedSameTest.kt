package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of shared wishlists in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`,
 * "The data rebuild"). A pretend server ([PretendSharedServer]) holds the lists as the real one does (it writes only the
 * fields sent, Rule 3) and can be offline or slow on purpose. A phone goes through everything Shared does: the offline
 * copy, two people changing one wish, a failed save being undone, an old refresh answer being ignored, adding, the bin
 * and Undo, the list's details and categories, reservations (also taken by someone else, and offline), names, making,
 * joining, resetting and deleting lists, a list that was deleted by someone else, leaving, and a restart.
 * After each step it writes down what was sent, what the app shows, what is saved and the server's copy. The result must
 * match `src/test/resources/shared-same.txt` word for word: recorded with Shared.kt as it was before the rebuild (2.257).
 * A MEANT change: delete the file, run once to record, read the difference in git.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class SharedSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/shared-same.txt")
    private val out = StringBuilder()
    private val server = PretendSharedServer()
    private val start = System.currentTimeMillis()

    @After fun noPretendServer() { Cloud.pretendServer = null }

    /** Waits until everything Shared started in the background has finished. */
    private fun idle() = runBlocking {
        val scope = Shared::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(Shared) as CoroutineScope
        repeat(50) {
            val kids = scope.coroutineContext.job.children.toList()
            if (kids.isEmpty()) return@runBlocking
            kids.forEach { it.join() }
        }
    }

    private fun wishText(x: Wish) = wishToJson(x).let { o -> o.keys().asSequence().sorted().joinToString(",") { "$it=${o.get(it)}" } }

    private fun note(step: String) {
        idle()
        val s = Shared
        out.append("## ").append(step).append('\n')
        server.calls.forEach { out.append("sent ").append(it).append('\n') }; server.calls.clear()
        out.append("lists=${s.lists}\nactive=${s.active} gone=${s.gone} busy=${s.busy()} notice='${s.notice}' me=${s.me} creator=${s.isCreator}\n")
        out.append("content name='${s.content.name}' about='${s.content.about}' cats=${s.content.cats} occ=${s.content.occ}\n")
        s.content.wishes.forEach { out.append("  wish ").append(wishText(it)).append('\n') }
        out.append("reserved=${s.reserved.toSortedMap()} ask=${s.askCount?.id}\n")
        Bin.entries.filter { it.listCode != null }.forEach { out.append("bin ${it.listCode} ${it.wish.id} by=${it.wish.binBy} reserved=${it.reserved}\n") }
        prefs.all.toSortedMap().filterKeys { it.startsWith("sh") }.forEach { (k, v) ->
            out.append("pref $k=${if (v is Set<*>) v.map { it.toString() }.sorted() else v}\n")
        }
        out.append(server.describe())
        Shared.clearNotice()
    }

    private fun normal(s: String): String {
        var t = s
        server.randoms.forEachIndexed { i, r -> t = t.replace(r, "<R${i + 1}>") }
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(t) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    private fun w(id: Long, title: String, cat: String, price: Double? = 10.0) =
        Wish(id, "https://shop.example/p/$id", title, "", "", price, null, "EUR", cat)

    private fun shown(id: Long) = Shared.content.wishes.first { it.id == id }

    @Test fun sharedListsDoExactlyTheSame() {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        Cloud.pretendServer = server::answer
        server.add("LISTAAAA", "KEYA", "Gifts for Mum", listOf("Home", "Fashion"), "For Mum's birthday",
            listOf(w(101, "Teapot", "Home"), w(102, "Scarf", "Fashion", 25.0).copy(qty = 2), w(104, "Book", "", null)))
        server.add("LISTBBBB", "KEYB", "Dad", listOf("Tools"), "", listOf(w(201, "Drill", "Tools")))
        server.reservedBy[104L] = "someone-else"
        // A phone from an older version: on list A, with an old copy of it saved for offline use.
        prefs.edit().putString("device", "device-1").putString("sh_me", "Ann")
            .putString("shared", JSONArray().put(JSONObject().put("code", "LISTAAAA").put("key", "KEYA").put("name", "Mum")
                .put("owner", "").put("me", "Ann")).toString())
            .putString("shc_LISTAAAA", JSONObject().put("name", "Mum").put("cats", JSONArray(listOf("Home")))
                .put("about", "").put("items", JSONArray().put(wishToJson(w(101, "Old teapot", "Home")))).toString())
            .commit()
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
        note("start: one list from an older version")

        server.offline = true
        Shared.open(c, "LISTAAAA")
        out.append("refresh offline=${runBlocking { Shared.refresh(c) }}\n")
        note("offline: the copy on the phone shows")
        server.offline = false
        out.append("refresh=${runBlocking { Shared.refresh(c) }}\n")
        note("online: the list as it is online")

        // Two people, one wish: someone else renames the teapot while this phone changes its price.
        val base = shown(101)
        server.change("LISTAAAA", 101) { it.put("title", "Blue teapot").put("note", "from Bob") }
        Shared.put(c, base.copy(price = 12.0), base)
        note("two people, one wish: both changes stay")

        server.offline = true
        val scarf = shown(102)
        Shared.put(c, scarf.copy(price = 15.0), scarf)
        note("a failed save is undone with a message")
        server.offline = false

        // A refresh that started before this phone's change answers late, with the old list: it is ignored.
        server.slowNextGet()
        val late = CoroutineScope(Dispatchers.IO).async { Shared.refresh(c) }
        server.waitForSlowGet()
        Shared.put(c, shown(102).copy(title = "Red scarf"), shown(102))
        idle()
        server.release()
        out.append("late refresh=${runBlocking { late.await() }}\n")
        note("an old refresh answer is ignored")

        Shared.put(c, w(103, "Mug", "Home"))
        note("a wish added: 'by' is your name")
        val t = Shared.trash(c, 101)!!
        note("in the bin for everyone")
        idle(); Shared.untrash(c, t)
        note("Undo")
        server.offline = true
        Shared.trash(c, 103)
        note("the bin offline: it comes back")
        server.offline = false
        Shared.delete(c, 103)
        note("deleted for good")

        // One at a time, so the background refreshes after each save come in a fixed order.
        Shared.setName(c, "Gifts for Mum!"); idle(); Shared.setAbout(c, "Size M"); idle(); Shared.setOccasion(c, null); idle()
        Shared.addCat(c, "Garden"); idle(); Shared.renameCat(c, "Home", "Kitchen"); idle(); Shared.removeCat(c, "Fashion")
        note("name, note, occasion and categories")

        Shared.reserve(shown(101), true); idle()
        Shared.reserve(shown(104), true); idle()
        Shared.reserve(shown(102), true)
        out.append("asks how many for ${Shared.askCount?.id}\n")
        Shared.reserveCount(shown(102), 2); idle()
        Shared.reserve(shown(101), false); idle()
        server.offline = true
        Shared.reserve(shown(101), true); idle()
        server.offline = false
        note("reservations: yours, taken, how many, taken away, offline")

        Shared.setAppName(c, "  Annie  "); Shared.setMe(c, "LISTAAAA", "")
        Shared.setForName(c, "LISTAAAA", "  Mum Smith  ")
        out.append("for='${Shared.forName(c, "LISTAAAA")}' isFor=${Shared.isFor("Mum Smith", "mum")} ${Shared.isFor("Mum", "Mumford")} ${Shared.isFor("", "x")}\n")
        for (text in listOf(null, "hello", "https://x.example/?j=KEYA", "https://x.example/join/?j=ABCDEFGHJKLMNPQR2345", "J=" + "x".repeat(600)))
            out.append("invite ${text?.take(40)} -> ${Shared.inviteFromClipboard(c, text)}\n")
        out.append("cached=${Shared.cachedWishes(c, "LISTAAAA").map { it.id }} other=${Shared.cachedWishes(c, "LISTBBBB").map { it.id }}\n")
        out.append("cats=${Shared.catsOf(c, "LISTAAAA")} owns=${Shared.ownsPhoto("LISTAAAA", Cloud.photoUrl("LISTAAAA", "a.jpg"))} ${Shared.ownsPhoto("LISTAAAA", "https://x/a.jpg")}\n")
        note("names, invites and the copy on the phone")

        runBlocking {
            val b = Shared.join(c, "KEYB", "")!!
            out.append("joined ${b.code}\n")
            out.append("join a dead link=${Shared.join(c, "NOPE", "Ann")}\n")
            val moved = Shared.addTo(c, b, w(301, "Saw", "Tools"))
            out.append("added elsewhere ${moved.id}\n")
            val away = Shared.sendAway(c, b, moved)
            out.append("sent away ${away.wish.id} bin=${away.wish.bin > 0} list=${away.listCode}\n")
        }
        note("joining, and moving wishes to a list that isn't open")

        runBlocking {
            val mine = Shared.create(c, "   ", "Ann")
            Shared.open(c, mine.code)
            out.append("made '${mine.name}' creator=${Shared.isCreator}\n")
            Shared.put(c, w(401, "Kite", ""))
            idle()
            val nk = Shared.resetKey(c)
            out.append("new key works=${server.keyWorks(nk)}\n")
            Shared.becameOwner(c, "LISTBBBB", "tokB")
            Shared.deleteForEveryone(c)
        }
        note("made, link reset, deleted for everyone")

        Shared.open(c, "LISTAAAA")
        server.remove("LISTAAAA")
        out.append("refresh=${runBlocking { Shared.refresh(c) }}\n")
        note("deleted by someone else: gone")
        Shared.leave(c, "LISTAAAA")
        note("left")
        Shared.load(c)
        note("started again")

        val now = normal(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}

/**
 * The shared-list part of the server, as supabase-shared.sql and supabase-setup.sql do it, for the checks: lists by
 * edit key, items written field by field (a field that is sent but missing is cleared), categories, reservations.
 */
class PretendSharedServer {
    class L(var code: String, var key: String, var name: String, var cats: MutableList<String>, var about: String, val items: MutableList<JSONObject>, var owner: String = "", var occ: Any = JSONObject.NULL)
    private val lists = mutableListOf<L>()
    val calls = mutableListOf<String>()
    /** Codes, keys and tokens the phone made up (different every run). */
    val randoms = mutableListOf<String>()
    val reservedBy = HashMap<Long, String>()
    val counts = HashMap<Long, Int>()
    @Volatile var offline = false
    @Volatile private var slow = false
    private var gotSlow = CountDownLatch(1)
    private var go = CountDownLatch(1)

    fun add(code: String, key: String, name: String, cats: List<String>, about: String, wishes: List<Wish>) {
        lists += L(code, key, name, cats.toMutableList(), about, wishes.map { wishToJson(it) }.toMutableList())
    }
    fun change(code: String, id: Long, f: (JSONObject) -> Unit) = f(lists.first { it.code == code }.items.first { it.getLong("id") == id })
    fun remove(code: String) { lists.removeAll { it.code == code } }
    fun keyWorks(key: String) = lists.any { it.key == key }
    fun slowNextGet() { slow = true; gotSlow = CountDownLatch(1); go = CountDownLatch(1) }
    fun waitForSlowGet() { gotSlow.await(10, TimeUnit.SECONDS) }
    fun release() = go.countDown()

    private fun remember(vararg v: String) = v.forEach { if (it !in randoms) randoms += it }

    fun describe(): String = lists.joinToString("") { l ->
        "server ${l.code} key=${l.key} '${l.name}' cats=${l.cats} about='${l.about}' occ=${l.occ} owner=${l.owner}\n" +
            l.items.joinToString("") { o -> "  item " + o.keys().asSequence().sorted().joinToString(",") { "$it=${o.get(it)}" } + "\n" }
    } + "server reservations=${reservedBy.toSortedMap()} counts=${counts.toSortedMap()}\n"

    @Synchronized private fun byKey(k: String) = lists.firstOrNull { it.key == k }
    @Synchronized private fun byCode(k: String) = lists.firstOrNull { it.code == k }

    fun answer(fn: String, b: JSONObject): String {
        when (fn) {
            "wl_sh_create" -> remember(b.getString("p_code"), b.getString("p_key"), b.getString("p_token"))
            "wl_sh_reset_key" -> remember(b.getString("p_new_key"))
        }
        synchronized(this) { calls += fn + " " + b.keys().asSequence().sorted().joinToString(",") { "$it=${b.get(it)}" } }
        if (offline) throw java.io.IOException("offline")
        if (fn == "wl_sh_get") {
            val l = byKey(b.getString("p_key"))
            val answer = if (l == null) "null" else synchronized(this) {
                val items = JSONArray()
                l.items.forEach { items.put(JSONObject(it.toString())) }
                items.put(JSONObject().put("id", 0).put("kind", "about").put("about", l.about).put("occ", l.occ))
                JSONObject().put("code", l.code).put("name", l.name).put("cats", JSONArray(l.cats)).put("items", items).toString()
            }
            if (slow) { slow = false; gotSlow.countDown(); go.await(10, TimeUnit.SECONDS) }
            return answer
        }
        synchronized(this) {
            val l = if (b.has("p_key")) byKey(b.getString("p_key")) else byCode(b.optString("p_code"))
            return when (fn) {
                "wl_sh_create" -> { lists += L(b.getString("p_code"), b.getString("p_key"), b.getString("p_name"), mutableListOf(), "", mutableListOf(), b.getString("p_token")); "" }
                "wl_sh_put_item" -> {
                    val item = b.getJSONObject("p_item")
                    val have = l!!.items.firstOrNull { it.getLong("id") == item.getLong("id") }
                    val fields = b.optJSONArray("p_fields")
                    if (have == null) l.items.add(0, item)
                    else if (fields == null) l.items[l.items.indexOf(have)] = item
                    else for (i in 0 until fields.length()) {
                        val f = fields.getString(i)
                        if (item.has(f)) have.put(f, item.get(f)) else have.remove(f)
                    }
                    ""
                }
                "wl_sh_del_item" -> { l!!.items.removeAll { it.getLong("id") == b.getLong("p_id") }; "" }
                "wl_sh_set_name" -> { l!!.name = b.getString("p_name"); "" }
                "wl_sh_set_about" -> { l!!.about = b.getString("p_about"); "" }
                "wl_sh_set_occasion" -> { l!!.occ = b.get("p_occ"); "" }
                "wl_sh_cat_add" -> { l!!.cats.add(b.getString("p_name")); "" }
                "wl_sh_cat_rename" -> {
                    val old = b.getString("p_old"); val n = b.getString("p_new")
                    l!!.cats.replaceAll { if (it == old) n else it }
                    l.items.forEach { if (it.optString("cat") == old) it.put("cat", n) }; ""
                }
                "wl_sh_cat_remove" -> {
                    val n = b.getString("p_name")
                    l!!.cats.remove(n); l.items.forEach { if (it.optString("cat") == n) it.put("cat", "") }; ""
                }
                "wl_sh_reset_key" -> { l!!.key = b.getString("p_new_key"); "" }
                "wl_delete_list" -> { lists.remove(l); "" }
                "wl_get_reservations" -> {
                    val me = b.getString("p_device")
                    JSONArray(reservedBy.map { (id, who) ->
                        JSONObject().put("id", id).put("mine", who == me).put("my", if (who == me) counts[id] ?: 1 else 0).put("n", counts[id] ?: 1).put("full", true)
                    }).toString()
                }
                "wl_reserve_item" -> {
                    val id = b.getLong("p_item")
                    if (reservedBy[id] != null && reservedBy[id] != b.getString("p_device")) "false"
                    else { reservedBy[id] = b.getString("p_device"); "true" }
                }
                "wl_reserve_count" -> {
                    val id = b.getLong("p_item"); val n = b.getInt("p_n")
                    if (n == 0) { reservedBy.remove(id); counts.remove(id) } else { reservedBy[id] = b.getString("p_device"); counts[id] = n }
                    "true"
                }
                "wl_unreserve_item" -> { if (reservedBy[b.getLong("p_item")] == b.getString("p_device")) reservedBy.remove(b.getLong("p_item")); "" }
                else -> ""
            }
        }
    }
}
