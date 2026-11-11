package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/**
 * The database is the only source of your lists (ListStore.kt, ROADMAP S2.5, 2.171): the first start moves the
 * settings store in, checks it and freezes it; afterwards changes go from memory to the database only, and the
 * settings store is written again only when the database can't be used.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class ListStoreTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val dao get() = WishlistDb.get(c).dao()
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Room refuses the screen's thread (as the app never uses it for the database): the checks read on their own. */
    private fun <T> db(f: () -> T): T = bg.submit(java.util.concurrent.Callable { f() }).get()

    private fun w(id: Long, title: String) = Wish(id, "https://shop.example/$id", title, "", "", 10.0, null, "EUR", "Home")
    private fun arr(vararg ws: Wish) = JSONArray().apply { ws.forEach { put(wishToJson(it)) } }.toString()

    /** Stands in for Repo.memory: what "the app" holds now. */
    private var memory: Snapshot? = null

    @Before fun fresh() { ListStore.reset(); WishlistDb.reset() }
    @After fun done() { ListStore.reset(); WishlistDb.reset() }

    private fun start() {
        ListStore.reset(); ListStore.start(c)
        ListStore.source = { memory!! }
    }

    private fun titles() = db { dao.wishes(MAIN_LIST).map { it.w.title } }

    @Test fun theFirstStartMovesChecksAndFreezes() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"), w(2, "Lamp"))).putString("cats", "Home").commit()
        start()
        assertTrue(ListStore.live)
        assertEquals(true, ListStore.matched)
        assertEquals("1", db { dao.meta("store_frozen") })
        assertEquals(listOf("Teapot", "Lamp"), ListStore.get(MAIN_LIST)!!.wishes.map { it.title })
        assertEquals(listOf("Teapot", "Lamp"), titles())
    }

    @Test fun aLostDatabaseNeverSendsTheOldCopyOnline() {
        // 2.178: the database was the only source, then its file is damaged. Whatever Android does with it (refuse it,
        // or start an empty one), the lists that come back are the settings store's old copy: marked, never sent online.
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home").commit()
        start()
        assertTrue(ListStore.live)
        assertTrue(!ListStore.stale)
        ListStore.reset(); WishlistDb.reset()
        val f = c.getDatabasePath("wishlist.db")
        java.io.File(f.path + "-wal").delete(); java.io.File(f.path + "-shm").delete()
        f.writeBytes(ByteArray(8192) { 7 })
        ListStore.start(c)
        assertTrue("an old copy is marked", ListStore.stale)
        // And it stays marked at the next start.
        ListStore.reset(); WishlistDb.reset()
        ListStore.start(c)
        assertTrue("still marked after a restart", ListStore.stale)
        // "Get my lists back" worked: the mark goes, and stays gone at the next start.
        assertTrue("no restart needed with a working database", !ListStore.rescued(c))
        assertTrue(!ListStore.stale)
        ListStore.reset(); WishlistDb.reset()
        ListStore.start(c)
        assertTrue("the rescue is remembered", !ListStore.stale)
        // An Import does the same.
        ListStore.forImport(c)
        WishlistDb.reset()
        ListStore.start(c)
        assertTrue(!ListStore.stale)
    }

    @Test fun afterwardsTheDatabaseIsTheOnlySource() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home").commit()
        start()
        val before = prefs.all.toMap()
        // A change in the app: one wish added. Only the database gets it, one row.
        memory = ListStore.same(DbMove.snapshot(prefs)).let { s -> s.copy(lists = s.lists.map { it.copy(wishes = it.wishes + w(3, "Book")) }) }
        ListStore.changed()
        ListStore.flush()
        assertEquals(listOf("Teapot", "Book"), titles())
        assertEquals(DbDiff.Written(1, 0), ListStore.written)
        // The settings store keeps its old copy, untouched.
        assertEquals(before, prefs.all.toMap())
        // The next start reads the database and doesn't compare with that old copy (it would "repair" it back).
        start()
        assertEquals(null, ListStore.matched)
        assertEquals(listOf("Teapot", "Book"), ListStore.get(MAIN_LIST)!!.wishes.map { it.title })
    }

    @Test fun writingAListGoesToTheDatabaseNotTheSettingsStore() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home").putString("name", "My wishlist").commit()
        start()
        memory = ListStore.same(DbMove.snapshot(prefs))
        val e = prefs.edit(); MyLists.write(e, MyLists.read(prefs, MAIN_LIST).copy(name = "Birthday")); e.commit()
        // MyLists.write keeps it in memory for the app and leaves the settings store alone.
        assertEquals("Birthday", MyLists.read(prefs, MAIN_LIST).name)
        assertEquals("My wishlist", prefs.getString("name", null))
    }

    @Test fun withoutTheDatabaseTheSettingsStoreIsWrittenAgain() {
        // Nothing from the database (it couldn't be used): every reader falls back, and writing goes to the store.
        prefs.edit().clear().commit()
        assertEquals(null, ListStore.bin()); assertEquals(null, ListStore.history())
        assertEquals(null, ListStore.friends()); assertEquals(null, ListStore.notes())
        assertEquals(null, ListStore.get(MAIN_LIST)); assertEquals(null, ListStore.ids())
        val e = prefs.edit(); MyLists.write(e, MineData(MAIN_LIST, "Mine", "", listOf("Home"), listOf(w(1, "Teapot")), null, null, emptySet())); e.commit()
        assertTrue(prefs.getString("wishes", "")!!.contains("Teapot"))
    }

    @Test fun aDifferenceAtTheLastComparingStartIsRepaired() {
        // A phone on 2.167–2.170 whose database fell behind: the settings store wins once, then it freezes.
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"), w(2, "Lamp"))).putString("cats", "Home").commit()
        db {
            DbMove.copy(WishlistDb.get(c), ListStore.same(Snapshot(listOf(MineData(MAIN_LIST, "My wishlist", "", listOf("Home"), listOf(w(1, "Teapot")), null, null, emptySet())), emptyList(), listOf("Home"), emptyMap(), emptyList(), emptyList())), "t")
            dao.putMeta(MetaRow("lists_live", "1"))
        }
        start()
        assertEquals(false, ListStore.matched)
        assertEquals(listOf("Teapot", "Lamp"), titles())
        assertEquals("1", db { dao.meta("store_frozen") })
    }

    @Test fun binHistoryFriendsAndNotesComeFromTheDatabase() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home")
            .putString("bin", arr(w(20, "Old lamp").copy(bin = 100L)))
            .putString("bin_owner", JSONObject().put("20", "gone_1").toString())
            .putString("history", JSONObject()
                .put("1", JSONArray().put(JSONArray().put(200).put(10.0)).put(JSONArray().put(100).put(12.0)))
                .put("5", JSONArray()).toString())
            .putString("friends", JSONArray()
                .put(JSONObject().put("code", "WL-ABCD2345").put("name", "Robin"))
                .put(JSONObject().put("code", "WL-ABCD2345").put("name", "Robin again")).toString())
            .putString("notes", JSONArray().put(JSONObject().put("id", 1).put("list", "m:main").put("title", "Teapot").put("pct", -10.0).put("time", 5L)).toString())
            .commit()
        start()
        assertEquals(true, ListStore.matched)
        assertEquals(listOf("Old lamp"), ListStore.bin()!!.map { it.title })
        assertEquals(mapOf(20L to "gone_1"), ListStore.binOwners())
        // In time order, without the empty history.
        assertEquals(mapOf(1L to listOf(PricePoint(100, 12.0), PricePoint(200, 10.0))), ListStore.history())
        assertEquals(listOf(Friend("WL-ABCD2345", "Robin")), ListStore.friends())
        assertEquals(listOf("Teapot"), ListStore.notes()!!.map { it.title })
        assertEquals(listOf(MAIN_LIST), ListStore.ids())
    }

    @Test fun severalListsAndTheirOrder() {
        val b = MineData("b7", "Birthday", "", listOf("Home"), listOf(w(9, "Novel"), w(1, "Copied teapot")), null, null, emptySet())
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home")
            .putString("mine_ids", JSONArray(listOf("main", "b7")).toString()).commit()
        // Written before the database starts, so into the settings store, as an older version did.
        val e = prefs.edit(); MyLists.write(e, b); e.commit()
        start()
        assertEquals(listOf("Novel", "Copied teapot"), ListStore.get("b7")!!.wishes.map { it.title })
        assertEquals(listOf("main", "b7"), ListStore.ids())
    }
}
