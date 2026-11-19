package com.wishlist.app

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
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
 * The before/after check of Friends in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`, "The data
 * rebuild"). With a pretend server ([Cloud.pretendServer]) a phone with an old saved Friends tab goes through
 * everything Friends does: the first refresh, requests coming in and being approved (the background check twice, so
 * nothing is told twice), adding, removing, a new code, birthday and About me (also offline, sent later), "Visible to
 * friends" (also offline: the switch goes back), a private note, links, photos, and a restart. After each step it writes
 * down what was sent to the server, what the app holds, what is saved, the notifications and the photo files.
 * The result must match `src/test/resources/friends-same.txt` word for word: recorded with Friends.kt as it was before
 * the rebuild (2.256). A MEANT change: delete the file, run once to record, read the difference in git.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class FriendsSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/friends-same.txt")
    private val out = StringBuilder()
    private val calls = mutableListOf<String>()
    private val start = System.currentTimeMillis()
    /** Sam's birthday, 10 days from today (so the birthday reminder comes); written as <in 10 days> in the record. */
    private val bday = java.time.LocalDate.now().plusDays(10).let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }
    private var offline = false
    private var server = JSONObject()

    @After fun noPretendServer() { Cloud.pretendServer = null }

    private fun person(id: String, name: String, pv: Long = 0, birthday: String = "", lists: JSONArray = JSONArray()) =
        JSONObject().put("id", id).put("name", name).put("pv", pv).put("birthday", birthday).put("bio", "bio of $name").put("lists", lists)

    private fun state(code: String, friends: List<JSONObject>, incoming: List<JSONObject>, outgoing: List<JSONObject>) =
        JSONObject().put("code", code).put("friends", JSONArray(friends)).put("incoming", JSONArray(incoming)).put("outgoing", JSONArray(outgoing))

    private fun answer(fn: String, body: JSONObject): String {
        calls += fn + " " + body.keys().asSequence().sorted().joinToString(",") { "$it=${body.get(it)}" }
        if (offline) throw java.io.IOException("offline")
        return when (fn) {
            "wl_fr_state" -> server.toString()
            "wl_fr_photo" -> "\"" + android.util.Base64.encodeToString(byteArrayOf(1, 2, 3, 4), android.util.Base64.NO_WRAP) + "\""
            "wl_fr_add" -> JSONObject().put("status", "sent").put("name", "Lee").toString()
            "wl_get_reservations" -> "[]"
            "wl_fr_list" -> "null"
            else -> ""
        }
    }

    private fun normal(s: String): String {
        var t = s
        prefs.getString("pf_id", null)?.let { t = t.replace(it, "<ID>") }
        prefs.getString("pf_token", null)?.let { t = t.replace(it, "<TOKEN>") }
        t = t.replace(c.filesDir.absolutePath, "<FILES>").replace(c.cacheDir.absolutePath, "<CACHE>")
        t = Regex("(bd(?:14|7)_[A-Za-z0-9]+_)\\d{4}").replace(t, "$1<YEAR>")
        t = t.replace("birthday=$bday", "birthday=<in 10 days>").replace("\"birthday\":\"$bday\"", "\"birthday\":\"<in 10 days>\"")
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(t) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    private fun note(step: String) {
        val f = Friends
        out.append("## ").append(step).append('\n')
        calls.forEach { out.append("sent ").append(it).append('\n') }; calls.clear()
        out.append("state=${f.state}\nwaiting=${f.waiting} visible=${f.visible.sorted()} photos=${f.photos.toSortedMap()}\n")
        out.append("me photo=${f.myPhoto} src=${f.photoSrc} edit=${f.photoEdit} birthday=${f.myBirthday} bio=${f.myBio}\n")
        prefs.all.toSortedMap().filterKeys { k -> listOf("pf_", "fr_", "fnote_", "bd", "photo_moved").any { k.startsWith(it) } }
            .forEach { (k, v) -> out.append("pref $k=${if (v is Set<*>) v.map { it.toString() }.sorted() else v}\n") }
        Notifications.all.forEach { n -> out.append("note ${n.kind} ${n.listKey} '${n.title}' read=${n.read} amount=${n.amount} image=${n.image}\n") }
        val nm = c.getSystemService(NotificationManager::class.java)
        shadowOf(nm).allNotifications.forEach { n ->
            out.append("buzz '${n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)}' '${n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)}'\n")
        }
        File(c.filesDir, "friends").listFiles()?.map { it.name }?.sorted()?.let { out.append("friend photos on the phone=$it\n") }
        out.append("own photo files=${c.filesDir.listFiles()!!.map { it.name }.filter { it.startsWith("profile_") }.sorted()}\n")
    }

    private fun restartFriends() {
        Friends::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Friends, false)
        Friends.load(c)
    }

    @Test fun friendsDoExactlyTheSame() = runBlocking {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        File(c.filesDir, "friends").deleteRecursively()
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Cloud.pretendServer = ::answer
        val samLists = JSONArray().put(JSONObject().put("code", "SAMLIST1").put("name", "Sam's list").put("count", 3))
        // A phone from an older version: a saved Friends tab, sizes that were removed, a list visible to friends.
        prefs.edit().putString("device", "device-1")
            .putString("fr_state", state("OLDCOD", listOf(person("f1", "Sam", 1)), emptyList(), emptyList()).toString())
            .putString("pf_sz_c", "M").putStringSet("fr_vis", setOf(MAIN_LIST))
            .putString("pf_bday", "01-02").putString("pf_bio", "Hi there").commit()
        File(c.filesDir, "friends").apply { mkdirs() }.resolve("x9_1.jpg").writeBytes(byteArrayOf(9))
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
        restartFriends(); note("old saved Friends tab, read at a start")

        server = state("K7Q4MZ", listOf(person("f1", "Sam", 2, bday, samLists)), emptyList(), listOf(person("o1", "Kim")))
        Friends.refresh(c); note("first refresh: the profile goes online")

        // Kim approved, Robin asks: the background check, twice.
        server = state("K7Q4MZ", listOf(person("f1", "Sam", 2, bday, samLists), person("o1", "Kim", 1)), listOf(person("r1", "Robin")), emptyList())
        Friends.poll(c); note("background check: a request, an approval, a birthday")
        Friends.poll(c); note("background check again: nothing told twice")

        server = state("K7Q4MZ", listOf(person("f1", "Sam", 2, bday, samLists), person("o1", "Kim", 1), person("r1", "Robin")), emptyList(), listOf(person("n1", "Lee")))
        Friends.answer(c, "r1", true)
        out.append("add=${Friends.add(c, "ABCDEF")}\n")
        Friends.remove(c, "n1"); Friends.newCode(c); Friends.nameChanged(c)
        out.append("list=${Friends.fetchList(c, "f1", "SAMLIST1")}\n")
        note("answer, add, remove, new code")

        Friends.setAbout(c, "03-04", "  Books and tea  ")
        offline = true
        out.append("offline about=${runCatching { Friends.setAbout(c, "05-06", "Later") }.exceptionOrNull()?.message}\n")
        note("About me, online and offline")
        offline = false
        Friends.refresh(c); note("online again: About me sent")

        val other = Repo.createMine(c, "Garden", "")
        Repo.setShareFor(c, other, "GARDENLK", "gtoken")
        Friends.setVisible(c, other, true)
        offline = true
        out.append("offline visible=${runCatching { Friends.setVisible(c, MAIN_LIST, false) }.exceptionOrNull()?.message}\n")
        offline = false
        note("visible to friends, and offline the switch goes back")
        Friends.forget(c, other); Friends.moveId(c, MAIN_LIST, "moved"); note("a list forgotten and moved")

        Friends.setNote(c, "f1", "  likes blue  ")
        out.append("note f1='${Friends.note(c, "f1")}'\n")
        Friends.setNote(c, "f1", "   ")
        out.append("note f1 after clearing='${Friends.note(c, "f1")}'\n")
        for (l in listOf("https://x.example/friend/?f=ab12cd&n=Sam%20B", "wishlist://friend?f=ZZZZZZ", "f=TOOLONG1", "nothing"))
            out.append("link $l -> ${Friends.parseFriendLink(l)}\n")
        out.append("pretty ${Friends.pretty("K7Q4MZ")} ${Friends.pretty("ABC")}\n")
        val day = java.time.LocalDate.of(2027, 2, 27)
        for (b in listOf("02-27", "02-28", "02-29", "01-01", "13-40", "")) out.append("days $b -> ${Friends.daysUntil(b, day)}\n")
        note("notes, links, codes, birthdays")

        val src = File(c.filesDir, "src.png").apply {
            outputStream().use { Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        out.append("photo saved online=${Friends.setPhoto(c, android.net.Uri.fromFile(src).toString(), HeroEdit())}\n")
        Friends.movePhoto(c)
        note("own photo")
        Friends.removePhoto(c); note("own photo removed")

        restartFriends(); note("started again")

        val now = normal(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
