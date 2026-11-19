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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/**
 * The risks the owner chose for the shared wishlists rebuild (19 November 2026), in plain words; SharedSameTest checks
 * everything together against the record made before the rebuild. With [PretendSharedServer].
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class SharedPartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private lateinit var server: PretendSharedServer

    private fun w(id: Long, title: String, price: Double? = 10.0) = Wish(id, "https://shop.example/$id", title, "", "", price, null, "EUR", "")
    private fun shown(id: Long) = Shared.content.wishes.first { it.id == id }
    private fun onServer(id: Long) = wishFromJson(JSONObject(server.describe().lines().first { it.contains("id=$id,") }
        .removePrefix("  item ").split(",").filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }.let { m ->
            JSONObject().put("id", id).put("url", m["url"]).put("title", m["title"]).put("price", m["price"]?.toDoubleOrNull() ?: JSONObject.NULL)
                .put("note", m["note"] ?: "")
        }.toString()))

    private fun idle() = runBlocking {
        val scope = Shared::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(Shared) as CoroutineScope
        repeat(50) { val kids = scope.coroutineContext.job.children.toList(); if (kids.isEmpty()) return@runBlocking; kids.forEach { it.join() } }
    }

    @Before fun aPhoneOnOneList() {
        prefs.edit().clear().putString("device", "device-1").putString("sh_me", "Ann")
            .putString("shared", JSONArray().put(JSONObject().put("code", "LISTAAAA").put("key", "KEYA").put("name", "Mum")).toString())
            .commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        server = PretendSharedServer()
        server.add("LISTAAAA", "KEYA", "Mum", listOf("Home"), "", listOf(w(1, "Teapot"), w(2, "Scarf", 25.0)))
        Cloud.pretendServer = server::answer
        Shared.load(c)
        Shared.open(c, "LISTAAAA")
        runBlocking { Shared.refresh(c) }
    }

    @After fun noPretendServer() { idle(); Cloud.pretendServer = null }

    @Test fun twoPeopleChangingOneWishBothStay() {
        val base = shown(1)
        server.change("LISTAAAA", 1) { it.put("title", "Blue teapot") } // someone else, a moment ago
        Shared.put(c, base.copy(price = 12.0), base)
        idle()
        assertEquals("only the price is sent", listOf("wl_sh_put_item p_fields=[\"price\"]"), server.calls.filter { it.startsWith("wl_sh_put") }.map { it.substringBefore(",p_item") })
        assertEquals("Blue teapot", shown(1).title)
        assertEquals(12.0, shown(1).price!!, 0.0)
        assertEquals("Blue teapot", onServer(1).title)
    }

    @Test fun aFailedSaveIsUndoneWithAMessage() {
        server.offline = true
        Shared.put(c, shown(2).copy(price = 15.0), shown(2))
        assertEquals("shown at once", 15.0, shown(2).price!!, 0.0)
        idle()
        assertEquals("back as it was", 25.0, shown(2).price!!, 0.0)
        assertTrue(Shared.notice.isNotEmpty())
        Shared.clearNotice()
        Shared.trash(c, 1)
        idle()
        assertTrue("the bin offline: it comes back", Shared.content.wishes.any { it.id == 1L })
        assertFalse(Bin.entries.any { it.listCode == "LISTAAAA" && it.wish.id == 1L })
    }

    @Test fun anOldRefreshAnswerIsIgnored() {
        server.slowNextGet()
        val late = CoroutineScope(Dispatchers.IO).async { Shared.refresh(c) }
        server.waitForSlowGet()
        Shared.put(c, shown(2).copy(title = "Red scarf"), shown(2))
        idle()
        server.release()
        runBlocking { late.await() }
        assertEquals("Red scarf", shown(2).title)
    }

    @Test fun offlineShowsTheCopyAndAGoneListSaysSo() {
        server.offline = true
        Shared.close(); Shared.open(c, "LISTAAAA")
        assertFalse(runBlocking { Shared.refresh(c) })
        assertEquals(listOf(1L, 2L), Shared.content.wishes.map { it.id })
        server.offline = false
        server.remove("LISTAAAA")
        runBlocking { Shared.refresh(c) }
        assertTrue(Shared.gone)
        server.calls.clear()
        Shared.leave(c, "LISTAAAA")
        idle()
        assertTrue(Shared.lists.isEmpty())
        assertFalse(prefs.contains("shc_LISTAAAA"))
        assertTrue("nothing is sent for a list you left", server.calls.none { it.startsWith("wl_sh_put") })
    }
}
