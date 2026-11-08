package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** Your lists come from the database (ListStore.kt, ROADMAP Stage 2 C1); the settings store stays the fallback. */
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

    @Before fun fresh() { ListStore.reset(); WishlistDb.reset() }
    @After fun done() { ListStore.reset(); WishlistDb.reset() }

    private fun restart() { ListStore.reset(); ListStore.start(c) }

    @Test fun theFirstStartMovesTheDataIn() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"), w(2, "Lamp"))).putString("cats", "Home").commit()
        ListStore.start(c)
        assertTrue(ListStore.live)
        assertEquals(true, ListStore.matched)
        assertEquals("1", db { dao.meta("lists_live") })
        assertEquals(listOf("Teapot", "Lamp"), ListStore.get(MAIN_LIST)!!.wishes.map { it.title })
        assertEquals(listOf("Teapot", "Lamp"), db { dao.wishes(MAIN_LIST).map { it.w.title } })
    }

    @Test fun aChangeReachesTheDatabaseAndTheFallback() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home").commit()
        ListStore.start(c)
        val e = prefs.edit()
        MyLists.write(e, MyLists.read(prefs, MAIN_LIST).let { it.copy(wishes = it.wishes + w(3, "Book")) })
        e.apply()
        ListStore.flush()
        assertEquals(listOf("Teapot", "Book"), db { dao.wishes(MAIN_LIST).map { it.w.title } })
        assertEquals(listOf("Teapot", "Book"), MyLists.read(prefs, MAIN_LIST, keep = false).wishes.map { it.title })
        // The next start reads the database and finds it matches.
        restart()
        assertEquals(true, ListStore.matched)
        assertEquals(listOf("Teapot", "Book"), ListStore.get(MAIN_LIST)!!.wishes.map { it.title })
    }

    @Test fun aDifferenceIsRepairedFromTheFallback() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"), w(2, "Lamp"))).putString("cats", "Home").commit()
        ListStore.start(c)
        // Something went wrong: the database lost a wish.
        db { WishlistDb.get(c).runInTransaction { dao.clearWishes() } }
        restart()
        assertEquals(false, ListStore.matched)
        assertEquals(listOf("Teapot", "Lamp"), ListStore.get(MAIN_LIST)!!.wishes.map { it.title })
        ListStore.flush()
        assertEquals(2, db { dao.countWishes() })
    }

    @Test fun theAppReadsItsListsFromTheDatabase() {
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home").commit()
        ListStore.start(c)
        // Only in the database (as if the settings store had fallen behind): the app's reads show the database.
        val e = prefs.edit(); MyLists.write(e, MyLists.read(prefs, MAIN_LIST).copy(name = "Birthday")); // not applied
        assertEquals("Birthday", MyLists.read(prefs, MAIN_LIST).name)
        assertEquals("My wishlist", MyLists.read(prefs, MAIN_LIST, keep = false).name)
    }

    @Test fun severalListsAndTheirOrder() {
        val b = MineData("b7", "Birthday", "", listOf("Home"), listOf(w(9, "Novel"), w(1, "Copied teapot")), null, null, emptySet())
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home")
            .putString("mine_ids", JSONArray(listOf("main", "b7")).toString()).commit()
        val e = prefs.edit(); MyLists.write(e, b); e.commit()
        ListStore.start(c)
        assertEquals(listOf("Novel", "Copied teapot"), ListStore.get("b7")!!.wishes.map { it.title })
        assertEquals(2, db { dao.countLists() })
    }

    @Test fun binHistoryFriendsAndNotesComeFromTheDatabase() {
        // Version C2 (2.168): read once at start from the database, after the same check.
        prefs.edit().clear().putString("wishes", arr(w(1, "Teapot"))).putString("cats", "Home")
            .putString("bin", arr(w(20, "Old lamp").copy(bin = 100L)))
            .putString("history", org.json.JSONObject()
                .put("1", JSONArray().put(JSONArray().put(200).put(10.0)).put(JSONArray().put(100).put(12.0)))
                .put("5", JSONArray()).toString())
            .putString("friends", JSONArray()
                .put(org.json.JSONObject().put("code", "WL-ABCD2345").put("name", "Robin"))
                .put(org.json.JSONObject().put("code", "WL-ABCD2345").put("name", "Robin again")).toString())
            .putString("notes", JSONArray().put(org.json.JSONObject().put("id", 1).put("list", "m:main").put("title", "Teapot").put("pct", -10.0).put("time", 5L)).toString())
            .commit()
        ListStore.start(c)
        assertEquals(true, ListStore.matched)
        assertEquals(listOf("Old lamp"), ListStore.bin()!!.map { it.title })
        // In time order, without the empty history.
        assertEquals(mapOf(1L to listOf(PricePoint(100, 12.0), PricePoint(200, 10.0))), ListStore.history())
        assertEquals(listOf(Friend("WL-ABCD2345", "Robin")), ListStore.friends())
        assertEquals(listOf("Teapot"), ListStore.notes()!!.map { it.title })
        // A lost bin row is noticed and repaired from the fallback.
        db { WishlistDb.get(c).runInTransaction { dao.clearBin() } }
        restart()
        assertEquals(false, ListStore.matched)
        assertEquals(listOf("Old lamp"), ListStore.bin()!!.map { it.title })
    }

    @Test fun withoutTheDatabaseTheAppReadsTheSettingsStore() {
        // Nothing comes from the database when it couldn't be used: every reader falls back.
        ListStore.reset()
        assertEquals(null, ListStore.bin()); assertEquals(null, ListStore.history())
        assertEquals(null, ListStore.friends()); assertEquals(null, ListStore.notes())
        assertEquals(null, ListStore.get(MAIN_LIST))
    }
}
