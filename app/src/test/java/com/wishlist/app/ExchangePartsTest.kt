package com.wishlist.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import org.robolectric.annotation.Config as Robo

/**
 * The risks the owner chose for the gift exchange rebuild (19 November 2026), in plain words; ExchangeSameTest checks
 * everything together against the record made before the rebuild. With a pretend server (Cloud.pretendServer). The
 * draw's secrecy itself is kept by the server (supabase-exchange.sql, checked in tests/sql/exchange_test.sql); these
 * check that the app keeps and shows only what is yours.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class ExchangePartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private var offline = false
    private var state = "{}"
    private val sent = mutableListOf<String>()

    private fun restart(o: Any) = o::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(o, false)
    /** What the notifications on the phone say (exchange news is gathered into one: "2 updates in your gift exchange"). */
    private fun buzz() = shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications
        .map { it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString() + " / " + it.extras.getCharSequence(android.app.Notification.EXTRA_TEXT) }.sorted()

    /** Exchange X1 on [day]: drawn, you have Kim; Sam and Kim are in it too. */
    private fun x1(day: LocalDate?, unreadSanta: Int = 0, unreadGiftee: Int = 0) = JSONObject().put("exchanges", JSONArray().put(
        JSONObject().put("id", "X1").put("name", "Santa").put("organiser", "me1").put("day", day?.toString() ?: JSONObject.NULL)
            .put("drawn", true).put("revealed", false)
            .put("members", JSONArray().put(JSONObject().put("id", "me1").put("name", "Ann").put("seen", true))
                .put(JSONObject().put("id", "m2").put("name", "Kim").put("seen", false)).put(JSONObject().put("id", "m3").put("name", "Sam").put("seen", true)))
            .put("me", JSONObject().put("seen", true).put("has_santa", true).put("giftee", JSONObject().put("id", "m2").put("name", "Kim"))
                .put("unread_santa", unreadSanta).put("unread_giftee", unreadGiftee))
    )).put("invites", JSONArray()).toString()

    @Before fun aPhoneInOneExchange() {
        prefs.edit().clear().putString("device", "device-1").putString("pf_id", "me1").putString("pf_token", "tok1").putBoolean("pf_made", true).commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        restart(Repo); Repo.load(c)
        restart(Exchanges); Exchanges.load(c)
        Cloud.pretendServer = { fn, b ->
            synchronized(sent) { sent += fn + " " + b }
            if (offline) throw java.io.IOException("offline")
            when (fn) {
                "wl_gx_state" -> state
                "wl_gx_draw" -> JSONObject().put("ok", true).toString()
                "wl_gx_send" -> "5"
                else -> ""
            }
        }
    }

    @After fun noPretendServer() { Cloud.pretendServer = null }

    @Test fun onlyYourOwnPairIsKept() = runBlocking {
        state = x1(null)
        Exchanges.poll(c)
        val x = Exchanges.get("X1")!!
        assertEquals("Kim", x.me.giftee?.name)
        assertTrue("nobody else's pair before the reveal", x.members.all { it.gaveTo == null })
        assertFalse("not in what the phone saved either", prefs.getString("gx_state", "")!!.contains("gave_to\":\"m"))
        assertTrue("never sent anywhere", sent.none { "Kim" in it })
    }

    @Test fun newsAndRemindersAreToldOnceAlsoAfterARestart() = runBlocking {
        state = x1(LocalDate.now().plusDays(5))
        Exchanges.poll(c)
        val week = buzz()
        assertTrue(week.toString(), week.any { "5 days to" in it })
        Exchanges.poll(c); restart(Exchanges); Exchanges.load(c); Exchanges.poll(c)
        assertEquals("nothing told again", week, buzz())
        state = x1(LocalDate.now())
        Exchanges.poll(c)
        val day = buzz()
        assertTrue(day.toString(), day.any { "Today is the exchange" in it })
        restart(Exchanges); Exchanges.load(c); Exchanges.poll(c)
        assertEquals("the day itself, once", day, buzz())
    }

    @Test fun offlineKeepsTheExchangeAndAFailedDrawChangesNothing() = runBlocking {
        state = x1(null)
        Exchanges.refresh(c)
        offline = true
        assertTrue(runCatching { Exchanges.refresh(c) }.isFailure)
        assertTrue(runCatching { Exchanges.draw(c, "X1", false) }.isFailure)
        assertEquals(listOf("X1"), Exchanges.list.map { it.id })
        restart(Exchanges); Exchanges.load(c)
        assertEquals("the copy on the phone after a restart", "Kim", Exchanges.get("X1")?.me?.giftee?.name)
    }

    @Test fun anonymousMessagesNotifyOncePerNewMessageOnTheRightSide() = runBlocking {
        state = x1(null)
        Exchanges.poll(c)
        assertTrue(buzz().isEmpty())
        state = x1(null, unreadSanta = 1)
        Exchanges.poll(c)
        val one = buzz()
        assertTrue(one.toString(), one.any { it.startsWith("Your Secret Santa wrote") })
        Exchanges.poll(c)
        assertEquals("the same message isn't told again", one, buzz())
        state = x1(null, unreadSanta = 1, unreadGiftee = 1)
        Exchanges.poll(c)
        assertTrue(buzz().toString(), buzz() != one)
        sent.clear()
        assertTrue(Exchanges.send(c, "X1", "giftee", "Thanks!"))
        assertTrue("sent to the giftee's side", sent.single().contains("\"p_side\":\"giftee\""))
        assertNull(Exchanges.get("NOPE"))
    }
}
