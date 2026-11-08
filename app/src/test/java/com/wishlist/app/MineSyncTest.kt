package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** Your own lists go online in small saves (MineSync.kt, ROADMAP S3.1). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class MineSyncTest {
    /** A pretend server that writes down every call; [down] makes calls fail like a phone without internet. */
    private class Fake : MineSync.Server {
        val calls = ArrayList<String>()
        var down = false
        var noPut = false
        override suspend fun full(code: String, token: String, name: String, cats: List<String>, items: JSONArray) {
            if (down) throw java.io.IOException("offline")
            calls += "full $name ${items.length()}"
        }
        override suspend fun put(code: String, token: String, name: String?, cats: List<String>?, items: List<JSONObject>, del: List<Long>) {
            if (down) throw java.io.IOException("offline")
            if (noPut) throw CloudException("Server said 404: {\"code\":\"PGRST202\",\"message\":\"Could not find the function public.wl_mine_put\"}")
            calls += "put ${name ?: "-"} ${cats ?: "-"} ${items.map { it.getLong("id") }} del $del"
        }
        override suspend fun owner(code: String, token: String, name: String) {
            if (down) throw java.io.IOException("offline")
            calls += "owner $name"
        }
    }

    private lateinit var c: Context
    private lateinit var fake: Fake
    private var now = 1_000_000_000_000L

    @Before fun setUp() {
        c = ApplicationProvider.getApplicationContext()
        c.getSharedPreferences("sent", Context.MODE_PRIVATE).edit().clear().commit()
        fake = Fake()
        MineSync.server = fake
        MineSync.clock = { now }
        MineSync.reset()
    }

    private fun items(vararg ws: Wish, about: String = "") = Cloud.listItems(ws.toList(), about)
    private fun send(i: JSONArray, name: String = "Mine", cats: List<String> = listOf("Home"), me: String = "") =
        runBlocking { MineSync.send(c, "CODE0001", "token", name, cats, i, me) }

    @Test fun theFirstSaveSendsTheWholeListThenOnlyChanges() {
        send(items(wish(1), wish(2)))
        assertEquals(listOf("full Mine 2"), fake.calls)
        // Nothing changed: no call at all.
        send(items(wish(1), wish(2)))
        assertEquals(1, fake.calls.size)
        // One wish edited, one added: one call with only those two.
        send(items(wish(1, price = 9.0), wish(2), wish(3)))
        assertEquals("put - - [1, 3] del []", fake.calls.last())
        // One removed.
        send(items(wish(1, price = 9.0), wish(3)))
        assertEquals("put - - [] del [2]", fake.calls.last())
        assertEquals(3, fake.calls.size)
    }

    @Test fun nameCategoriesAndTheNoteItem() {
        send(items(wish(1)))
        send(items(wish(1)), name = "Birthday")
        assertEquals("put Birthday - [] del []", fake.calls.last())
        send(items(wish(1)), name = "Birthday", cats = listOf("Home", "Books"))
        assertEquals("put - [Home, Books] [] del []", fake.calls.last())
        send(items(wish(1), about = "Thank you!"), name = "Birthday", cats = listOf("Home", "Books"))
        assertEquals("put - - [0] del []", fake.calls.last())
        send(items(wish(1)), name = "Birthday", cats = listOf("Home", "Books"))
        assertEquals("put - - [] del [0]", fake.calls.last())
    }

    @Test fun aFailedSaveKeepsTheChangeAndTheNextTrySendsEverythingMissing() {
        send(items(wish(1), wish(2)))
        fake.down = true
        try { send(items(wish(1, price = 5.0), wish(2))); throw AssertionError("should fail") } catch (e: java.io.IOException) {}
        try { send(items(wish(1, price = 5.0))); throw AssertionError("should fail") } catch (e: java.io.IOException) {}
        fake.down = false
        // Back online (next change, next start or the background check): both changes in one call.
        send(items(wish(1, price = 5.0)))
        assertEquals("put - - [1] del [2]", fake.calls.last())
        send(items(wish(1, price = 5.0)))
        assertEquals(2, fake.calls.size)
    }

    @Test fun anOlderServerWithoutTheNewFunctionGetsTheWholeList() {
        send(items(wish(1)))
        fake.noPut = true
        send(items(wish(1), wish(2)))
        assertEquals("full Mine 2", fake.calls.last())
        // For the rest of this run: whole lists, without asking for the missing function again.
        send(items(wish(1), wish(2), wish(3)))
        assertEquals("full Mine 3", fake.calls.last())
    }

    @Test fun onceAWeekTheWholeListAsARepair() {
        send(items(wish(1)))
        now += MineSync.WEEK - 1000
        send(items(wish(1), wish(2)))
        assertTrue(fake.calls.last().startsWith("put"))
        now += 2000
        send(items(wish(1), wish(2)))
        assertEquals("full Mine 2", fake.calls.last())
    }

    @Test fun theOwnerNameOnlyWhenItChanged() {
        send(items(wish(1)), me = "Anna")
        send(items(wish(1, price = 1.0)), me = "Anna")
        assertEquals(listOf("full Mine 1", "owner Anna", "put - - [1] del []"), fake.calls)
        send(items(wish(1, price = 1.0)), me = "Anna B")
        assertEquals("owner Anna B", fake.calls.last())
    }

    @Test fun privateWishesAndPhonePicturesNeverGoOnline() {
        val i = Cloud.listItems(listOf(wish(1), wish(2).copy(priv = true), wish(3).copy(image = "file:///data/x.jpg")))
        assertEquals(2, i.length())
        assertEquals("", i.getJSONObject(1).getString("image"))
    }

    @Test fun fingerprintsSurviveARestartAndForget() {
        send(items(wish(1)))
        assertEquals(setOf(1L), MineSync.load(c, "CODE0001")!!.items.keys)
        MineSync.forget(c, "CODE0001")
        assertNull(MineSync.load(c, "CODE0001"))
        assertNull(SentState.fromJson("not json"))
    }
}
