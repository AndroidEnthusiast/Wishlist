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
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of wishlist groups in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`,
 * "The data rebuild"). With a pretend server (Cloud.pretendServer) a phone with groups saved by an older version goes
 * through everything Groups does: loading, the background check (twice: news told once), invites and news, making a
 * group with an "Only me" list (it stays private outside the group), previews, joining (also offline and with a dead
 * link), changing the list, No thanks, leaving, removing, settings, a new link, deleting, inviting (one failing), links,
 * a browser member moving into the app (also a wrong link), the photo, and a restart. After each step it writes down
 * what was sent, what the app holds, what is saved, the notifications, the photo files and your lists.
 * The result must match `src/test/resources/groups-same.txt` word for word: recorded with Groups.kt as it was before
 * the rebuild (2.258). A MEANT change: delete the file, run once to record, read the difference in git.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class GroupsSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/groups-same.txt")
    private val out = StringBuilder()
    private val calls = mutableListOf<String>()
    private val randoms = mutableListOf<String>()
    private val start = System.currentTimeMillis()
    private var offline = false
    private var state = JSONObject()
    private var news = JSONArray()
    private var newsSeen = 0L

    @After fun noPretendServer() { Cloud.pretendServer = null }

    private fun member(id: String, name: String, pv: Long = 0, friend: Boolean = false, list: String? = null) =
        JSONObject().put("id", id).put("name", name).put("pv", pv).put("web", false).put("code", "").put("friend", friend)
            .apply { if (list != null) put("list", JSONObject().put("code", list).put("name", "List $list").put("count", 2).put("image", "")) }

    private fun group(id: String, name: String, pv: Long, organiser: String, members: List<JSONObject>, invited: List<String> = emptyList()) =
        JSONObject().put("id", id).put("link", "LINK$id").put("name", name).put("pv", pv).put("organiser", organiser)
            .put("invite_all", false).put("members", JSONArray(members)).put("invited", JSONArray(invited))

    private fun invite(gid: String, name: String, by: String) =
        JSONObject().put("group", gid).put("name", name).put("link", "LINK$gid").put("by", "id-$by").put("by_name", by).put("pv", 0).put("count", 3)

    private fun setState(groups: List<JSONObject>, invites: List<JSONObject>) {
        state = JSONObject().put("groups", JSONArray(groups)).put("invites", JSONArray(invites))
    }

    private fun answer(fn: String, b: JSONObject): String {
        for (k in listOf("p_group", "p_link")) if (fn in setOf("wl_gr_create", "wl_gr_new_link")) b.optString(k).takeIf { it.length == 20 && it !in randoms }?.let { randoms += it }
        if (fn == "wl_create_list") for (k in listOf("p_code", "p_token")) b.optString(k).takeIf { it !in randoms }?.let { randoms += it }
        synchronized(calls) { calls += fn + " " + b.keys().asSequence().sorted().joinToString(",") { "$it=${b.get(it)}" } }
        if (offline) throw java.io.IOException("offline")
        return when (fn) {
            "wl_gr_state" -> JSONObject(state.toString()).put("news", JSONArray((0 until news.length()).map { news.getJSONObject(it) }.filter { it.getLong("id") > newsSeen })).toString()
            "wl_gr_news_seen" -> { newsSeen = b.getLong("p_upto"); "" }
            "wl_gr_photo", "wl_gr_face" -> "\"" + android.util.Base64.encodeToString(byteArrayOf(1, 2, 3), android.util.Base64.NO_WRAP) + "\""
            "wl_gr_info" -> if (b.getString("p_link") == "LINKG1") JSONObject().put("id", "G1").put("name", "Family").put("organiser", "Ann").put("count", 3).put("photo", "").toString() else "null"
            "wl_gr_join" -> if (b.getString("p_link") == "DEADLINK" ) "null" else JSONObject().put("id", "G2").toString()
            "wl_gr_invite" -> if (b.getString("p_friend") == "bad") throw java.io.IOException("refused") else ""
            "wl_gr_web_me" -> if (b.getString("p_id") == "WEBID") JSONObject().put("web", true).put("list", "WEBLIST1").put("list_token", "wtok").put("name", "Webby").toString()
                else JSONObject().put("web", false).toString()
            "wl_get_list" -> JSONObject().put("name", "Web list").put("items", JSONArray()
                .put(wishToJson(Wish(71, "https://shop.example/71", "Lamp", "", "", 5.0, null, "EUR", "")))
                .put(JSONObject().put("id", 0).put("kind", "about").put("about", "from the browser"))).toString()
            "wl_gr_list", "wl_chat_key" -> ""
            else -> ""
        }
    }

    private fun normal(s: String): String {
        var t = s
        randoms.forEachIndexed { i, r -> t = t.replace(r, "<R${i + 1}>") }
        t = t.replace(c.filesDir.absolutePath, "<FILES>")
        // This phone's chat key, made new on every run.
        t = Regex("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE[A-Za-z0-9+/=]+").replace(t, "<CHAT KEY>")
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(t) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    /** Waits until what the app sends in the background (Sync: your lists online) has gone. */
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
        val g = Groups
        out.append("## ").append(step).append('\n')
        // Sorted: some calls run in the background, so only their order may differ between runs.
        synchronized(calls) { calls.sorted().forEach { out.append("sent ").append(it).append('\n') }; calls.clear() }
        g.groups.forEach { out.append("group $it\n") }
        g.invites.forEach { out.append("invite $it\n") }
        out.append("photos=${g.photos.toSortedMap()} faces=${g.faces.toSortedMap()} unsent=${g.unsent.toSortedMap()}\n")
        prefs.all.toSortedMap().filterKeys { k -> listOf("gr_", "link_off", "fr_vis", "photo_moved", "sh_me").any { k.startsWith(it) } }
            .forEach { (k, v) -> out.append("pref $k=${if (v is Set<*>) v.map { it.toString() }.sorted() else v}\n") }
        Notifications.all.forEach { n -> out.append("note ${n.kind} ${n.listKey} '${n.title}' extra='${n.currency}' read=${n.read}\n") }
        shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.forEach { n ->
            out.append("buzz '${n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)}' '${n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)}'\n")
        }
        for (d in listOf("groups", "groupfaces")) File(c.filesDir, d).list()?.sorted()?.let { out.append("$d on the phone=$it\n") }
        for (m in Repo.allMine(c)) out.append("list ${m.id} '${m.name}' about='${m.about}' code=${m.code} token=${m.token} wishes=${m.wishes.map { it.id }} friends=${Friends.isVisible(m.id)} groups=${g.groupsOf(c, m.id).map { it.id }}\n")
    }

    @Test fun groupsDoExactlyTheSame() = runBlocking {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        for (d in listOf("groups", "groupfaces")) File(c.filesDir, d).deleteRecursively()
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Cloud.pretendServer = ::answer
        // A phone from an older version: a profile online, groups saved, an old group photo on the phone.
        setState(listOf(group("G1", "Family", 0, "me1", listOf(member("me1", "Ann", list = "MINECODE")))), emptyList())
        prefs.edit().putString("device", "device-1").putString("pf_id", "me1").putString("pf_token", "tok1").putBoolean("pf_made", true)
            .putString("sh_me", "Ann").putString("gr_state", state.toString()).commit()
        File(c.filesDir, "groups").apply { mkdirs() }.resolve("OLD_1.jpg").writeBytes(byteArrayOf(9))
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
        Repo.setShareFor(c, MAIN_LIST, "MINECODE", "minetok")
        Friends::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Friends, false)
        Friends.load(c)
        Groups::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Groups, false)
        Groups.load(c)
        note("groups saved by an older version, read at a start")

        setState(listOf(group("G1", "Family", 1, "me1", listOf(member("me1", "Ann", list = "MINECODE"), member("m2", "Kim", 2), member("m3", "Sam", 1, friend = true)))),
            listOf(invite("G7", "Book club", "Bob")))
        news = JSONArray().put(JSONObject().put("id", 5).put("group", "G1").put("name", "Family").put("kind", "declined").put("who", "Lee"))
        Groups.refresh(c); note("refresh: an invite and a No thanks, on screen")

        setState(listOf(group("G1", "Family", 1, "me1", listOf(member("me1", "Ann", list = "MINECODE"), member("m2", "Kim", 2)))),
            listOf(invite("G7", "Book club", "Bob"), invite("G8", "Work", "Jo")))
        news.put(JSONObject().put("id", 6).put("group", "G9").put("name", "Old friends").put("kind", "removed"))
        Groups.poll(c); note("background check: a new invite and being removed")
        Groups.poll(c); note("background check again: nothing told twice")

        val onlyMe = Repo.createMine(c, "Secret", "")
        Repo.updateMine(c, onlyMe) { listOf(Wish(61, "https://shop.example/61", "Pen", "", "", 3.0, null, "EUR", "")) }
        out.append("made ${Groups.create(c, "  Family two ", onlyMe).length} long\n")
        note("a group made with an Only me list: online for the group only")

        out.append("preview=${Groups.preview("LINKG1")} dead=${Groups.preview("NOPE")}\n")
        out.append("join=${Groups.join(c, "LINKG2", null, MAIN_LIST)} dead=${Groups.join(c, "DEADLINK", null, MAIN_LIST)}\n")
        offline = true
        out.append("join offline=${runCatching { Groups.join(c, null, "G7", MAIN_LIST) }.exceptionOrNull()?.message}\n")
        out.append("refresh offline=${runCatching { Groups.refresh(c) }.exceptionOrNull()?.message}\n")
        offline = false
        note("preview and join, also dead and offline")

        Groups.setList(c, "G1", onlyMe); Groups.decline(c, "G7"); Groups.leave(c, "G1", "m2"); Groups.remove(c, "G1", "m2")
        Groups.settings(c, "G1", "  Fam ", true); Groups.newLink(c, "G1")
        note("change list, No thanks, leave, remove, settings, new link")
        Groups.invite(c, Groups.group("G1")!!, listOf("f1"))
        out.append("unsent=${Groups.inviteNew(c, "G1", listOf("f1", "bad"))} missing=${Groups.inviteNew(c, "NOPE", listOf("a", "b"))}\n")
        out.append("list=${Groups.fetchList(c, "G1", "X")}\n")
        Groups.delete(c, "G1")
        note("invites (one fails), a brought list, deleted")

        val g1 = Groups.group("G1")
        out.append("organiser=${g1?.let { Groups.isOrganiser(c, it) }} invite=${g1?.let { Groups.canInvite(c, it) }} mine=${g1?.let { Groups.myListId(c, it) }}\n")
        for (s in listOf("https://x.example/group/?g=abcdefghjkmnpqrstuvw", "wishlist://group?G=ABCDEFGHJKMNPQRSTUVW", "g=SHORT", "nothing"))
            out.append("link $s -> ${Groups.parseLink(s)}\n")
        for (s in listOf("https://x/#me=ABCDEFGHJKMNPQRSTUVW.ABCDEFGHJKMNPQRSTUV2.dev-1", "#me=ABCDEFGHJKMNPQRSTUVW.ABCDEFGHJKMNPQRSTUV2", "#me=bad"))
            out.append("me $s -> ${Groups.parseMe(s)}\n")
        out.append("share link ${Groups.link("ABC")}\n")
        Groups.groups.flatMap { it.members }.forEach { m -> out.append("face ${m.id} -> ${Groups.faceOf(c, m)}\n") }
        note("links and faces")

        out.append("wrong link=${runCatching { Groups.takeover(c, "NOTWEB", "t", "d") }.exceptionOrNull()?.message}\n")
        prefs.edit().putString("sh_me", "").commit(); Shared.load(c)
        val moved = Groups.takeover(c, "WEBID", "WEBTOKEN", "webdev")
        out.append("moved in as ${moved?.let { "list" }}\n")
        note("a browser member moves into the app")

        Groups.setPhoto(c, "G1", null, HeroEdit(), 1f); note("group photo removed")

        Groups::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Groups, false)
        Groups.load(c); note("started again")

        val now = normal(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
