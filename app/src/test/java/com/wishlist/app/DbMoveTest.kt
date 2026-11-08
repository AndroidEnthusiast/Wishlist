package com.wishlist.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import java.io.File

/**
 * Moving the data into the database (DbMove.kt, ROADMAP S2.3): everything read back from the database is exactly what
 * the settings store holds, a failure leaves the database as it was, and the old data is never changed.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class DbMoveTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private lateinit var db: WishlistDb

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(c, WishlistDb::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun w(id: Long, title: String, more: (Wish) -> Wish = { it }) =
        more(Wish(id, "https://shop.example/$id", title, "", "", 10.0 + id, null, "EUR", "Home"))

    private fun arr(vararg ws: Wish) = JSONArray().apply { ws.forEach { put(wishToJson(it)) } }.toString()

    /** Everything that moved comes back exactly, in the same order. */
    private fun assertSame(s: Snapshot) {
        val dao = db.dao()
        assertEquals(s.lists.map { it.id }, dao.lists().map { it.id })
        for (l in s.lists) {
            val row = dao.lists().single { it.id == l.id }
            assertEquals(l.name, row.name); assertEquals(l.about, row.about)
            assertEquals(l.code, row.code); assertEquals(l.token, row.token)
            assertEquals(l.photos, JSONArray(row.photos).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            assertEquals(l.wishes.distinctBy { it.id }, dao.wishes(l.id).map { it.w.toWish(it.id) })
        }
        assertEquals(s.bin.distinctBy { it.first to it.second.id }, dao.bin().map { it.listId to it.w.toWish(it.id) })
        assertEquals(s.categories, dao.categories().map { it.name })
        for ((id, ps) in s.history) assertEquals(ps.distinctBy { it.t }, dao.pricePoints(id).map { PricePoint(it.at, it.price) })
        assertEquals(s.friends.distinctBy { it.code }, dao.friendLists().map { Friend(it.code, it.name) })
        assertEquals(s.notes, dao.notes().map { it.toNote() })
    }

    @Test fun everythingMovesExactly() {
        val second = MineData("b7", "Birthday", "For June", listOf("Books", "Tech"),
            listOf(w(1L, "Copied teapot"), w(9L, "Novel")), "WL-BBBB2345", "tok", setOf("x.jpg"))
        val all = w(5L, "Everything set") {
            it.copy(oldPrice = 12.0, got = true, star = true, note = "Blue", size = "M", checked = 3L, priv = true, qty = 3, exact = "exact", recv = 4L)
        }
        prefs.edit().clear()
            .putString("wishes", arr(w(1L, "Teapot"), all, w(1L, "Teapot twice by mistake")))
            .putString("name", "My wishlist").putString("cats", "Home\nTech")
            .putString("mine_ids", JSONArray(listOf("main", "b7")).toString())
            .putString("bin", arr(w(20L, "Old lamp") { it.copy(bin = 100L) }, w(21L, "From a deleted list") { it.copy(bin = 200L) }))
            .putString("bin_owner", JSONObject().put("21", "gone1").toString())
            .putString("history", JSONObject().put("1", JSONArray().put(JSONArray().put(100).put(12.0)).put(JSONArray().put(200).put(10.0))).toString())
            .putString("friends", JSONArray().put(JSONObject().put("code", "WL-ABCD2345").put("name", "Robin")).toString())
            .putString("notes", JSONArray().put(JSONObject().put("id", 1).put("list", "m:main").put("title", "Teapot").put("pct", -10.0).put("time", 5L)).toString())
            .commit()
        val e = prefs.edit(); MyLists.write(e, second); e.commit()
        val before = prefs.all.toMap()

        val s = DbMove.snapshot(prefs)
        val count = DbMove.copy(db, s, "test")
        assertSame(s)
        assertEquals(1, count.duplicates)
        assertEquals(listOf("Home", "Tech", "Books"), db.dao().categories().map { it.name })
        assertEquals("gone1", db.dao().bin().single { it.id == 21L }.listId)
        assertTrue(db.dao().meta("copied")!!.startsWith("test lists=2 wishes=4"))
        // The old data is only read.
        assertEquals(before, prefs.all.toMap())
    }

    @Test fun aSecondCopyReplacesTheFirst() {
        prefs.edit().clear().putString("wishes", arr(w(1L, "A"), w(2L, "B"))).commit()
        DbMove.copy(db, DbMove.snapshot(prefs), "1")
        prefs.edit().putString("wishes", arr(w(2L, "B changed"))).commit()
        val s = DbMove.snapshot(prefs)
        DbMove.copy(db, s, "2")
        assertSame(s)
    }

    @Test fun aFailureLeavesTheDatabaseAsItWas() {
        prefs.edit().clear().putString("wishes", arr(w(1L, "A"))).commit()
        DbMove.copy(db, DbMove.snapshot(prefs), "1")
        // A wish in a list the database doesn't know breaks the copy halfway: nothing of the second copy stays.
        val bad = DbMove.snapshot(prefs).let { it.copy(lists = it.lists + MineData("x", "X", "", emptyList(), listOf(w(3L, "C")), null, null, emptySet())) }
        val broken = bad.copy(lists = bad.lists.map { if (it.id == "x") it.copy(id = "main") else it })
        val failed = try { DbMove.copy(db, broken, "2"); false } catch (e: Exception) { true }
        assertTrue(failed)
        assertEquals(listOf("A"), db.dao().wishes("main").map { it.w.title })
        assertTrue(db.dao().meta("copied")!!.startsWith("1 "))
    }

    @Test fun anEmptyPhoneMovesToo() {
        prefs.edit().clear().commit()
        val s = DbMove.snapshot(prefs)
        DbMove.copy(db, s, "1")
        assertEquals(listOf("main"), db.dao().lists().map { it.id })
        assertEquals(0, db.dao().countWishes())
    }

    /** The owner's real Export (only on the computer that has it: -Pwl.export=…/prefs.json). */
    @Test fun theOwnersRealDataMovesExactly() {
        val path = System.getProperty("wl.export").orEmpty()
        assumeTrue("no real Export given", path.isNotEmpty() && File(path).exists())
        val v = JSONObject(File(path).readText())
        val e = prefs.edit().clear()
        for (k in v.keys()) {
            val o = v.getJSONObject(k)
            when (o.optString("t")) {
                "s" -> e.putString(k, o.getString("v")); "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v")); "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                "set" -> o.getJSONArray("v").let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        e.commit()
        val before = prefs.all.toMap()
        val s = DbMove.snapshot(prefs)
        val count = DbMove.copy(db, s, "real")
        assertSame(s)
        assertEquals(before, prefs.all.toMap())
        // Repo reads the same lists and wishes as the copy: the screens will see the same thing in version C.
        Repo.load(c)
        assertEquals(Repo.allMine(c).map { it.id to it.wishes.size }, s.lists.map { it.id to it.wishes.size })
        println("REAL EXPORT: $count; lists ${s.lists.map { it.wishes.size }}")
        // Version C1: the app's own start moves it into its database and then reads it back: it must match, twice.
        ListStore.reset(); WishlistDb.reset()
        ListStore.start(c)
        assertEquals(true, ListStore.matched)
        ListStore.reset(); ListStore.start(c)
        assertEquals(true, ListStore.matched)
        assertEquals(s.lists.single { it.id == MAIN_LIST }.wishes, MyLists.read(prefs, MAIN_LIST).wishes)
        ListStore.reset(); WishlistDb.reset()
    }
}
