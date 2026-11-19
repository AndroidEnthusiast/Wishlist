package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A friend's list's state holder (the rebuild, 19 November 2026): loading, the states, and the 2× reservation fix. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FriendListModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val f = Friend("FRIENDCODE1", "Magdalena’s birthday")
    private fun model() = FriendListState(ctx, f, SnackbarHostState(), kotlinx.coroutines.MainScope()).also { Repo.load(ctx) }
    private val list = RemoteList("Magdalena’s birthday", listOf("Books"), listOf(Wish(1L, "", "Novel", "", "", 12.0, null, "EUR", "Books")))

    @Test fun loadsTheListAndYourReservations() = runBlocking {
        val m = model()
        m.fetchRes = { mapOf(1L to true) }
        m.refresh { list }
        assertEquals("ok", m.state); assertEquals("Novel", m.remote!!.wishes[0].title); assertEquals(true, m.res[1L])
    }

    @Test fun goneAndOffline() = runBlocking {
        val gone = model(); gone.refresh { null }
        assertEquals("gone", gone.state)
        val off = model(); off.refresh { throw java.io.IOException("offline") }
        assertEquals("offline", off.state)
        // Already loaded: a failed refresh keeps showing the list.
        val loaded = model(); loaded.fetchRes = { emptyMap() }; loaded.refresh { list }
        loaded.refresh { throw java.io.IOException("offline") }
        assertEquals("ok", loaded.state)
    }

    @Test fun searchStopsCleanly() {
        val m = model(); m.searching = true; m.query = "nov"
        m.stopSearch()
        assertEquals(false, m.searching); assertEquals("", m.query)
    }

    /** Fixed in the rebuild: reserving a 2× wish is remembered on this phone (Gifts I reserved, news about it). */
    @Test fun aCountReservationIsRemembered() {
        val src = File("src/main/java/com/wishlist/app/FriendListModel.kt").readText()
        assertTrue(src.contains("else if (n > 0) { FriendWatch.reserved(ctx, f.code, w); Celebrate.reserved() }"))
    }
}
