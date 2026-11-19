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
import org.robolectric.annotation.Config as Robo

/**
 * The risks the owner chose for the groups rebuild (19 November 2026), in plain words; GroupsSameTest checks
 * everything together against the record made before the rebuild. With a pretend server (Cloud.pretendServer).
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class GroupsPartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private var offline = false
    private var state = JSONObject().put("groups", JSONArray()).put("invites", JSONArray())
    private val sent = mutableListOf<String>()

    private fun restart(o: Any, name: String = "loaded") = o::class.java.getDeclaredField(name).apply { isAccessible = true }.setBoolean(o, false)
    private fun buzzes() = shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.size
    private fun group(id: String) = JSONObject().put("id", id).put("link", "L$id").put("name", "Group $id").put("pv", 0).put("organiser", "me1")
        .put("members", JSONArray().put(JSONObject().put("id", "me1").put("name", "Ann"))).put("invited", JSONArray())

    @Before fun aPhoneWithAProfile() {
        prefs.edit().clear().putString("device", "device-1").putString("pf_id", "me1").putString("pf_token", "tok1").putBoolean("pf_made", true).commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        restart(Repo); Repo.load(c)
        restart(Friends); Friends.load(c)
        restart(Groups); Groups.load(c)
        Cloud.pretendServer = { fn, b ->
            synchronized(sent) { sent += fn + " " + b }
            if (offline) throw java.io.IOException("offline")
            when (fn) {
                "wl_gr_state" -> state.toString()
                "wl_gr_join" -> JSONObject().put("id", "G2").toString()
                "wl_gr_web_me" -> JSONObject().put("web", b.getString("p_id") == "WEBID").toString()
                else -> ""
            }
        }
    }

    @After fun noPretendServer() { Cloud.pretendServer = null }

    @Test fun anOnlyMeListStaysPrivateOutsideTheGroup() = runBlocking {
        val secret = Repo.createMine(c, "Secret", "")
        Groups.create(c, "Family", secret)
        assertTrue("it went online for the group", Repo.mine(c, secret).code != null)
        assertTrue("its friends' link is switched off", sent.any { it.startsWith("wl_set_link") && "\"p_on\":false" in it })
        assertTrue(secret in ListLink.off)
        assertFalse("not visible to friends", Friends.isVisible(secret))
    }

    @Test fun anInviteIsToldOnceAlsoAfterARestart() = runBlocking {
        state.put("invites", JSONArray().put(JSONObject().put("group", "G7").put("name", "Book club").put("by_name", "Bob")))
        Groups.poll(c)
        assertEquals(1, buzzes())
        Groups.poll(c)
        restart(Groups); Groups.load(c)
        Groups.poll(c)
        assertEquals(1, buzzes())
        assertEquals(1, Notifications.all.count { it.kind == "group_invite" })
    }

    @Test fun offlineKeepsTheGroupsAndChangesNothing() = runBlocking {
        state.put("groups", JSONArray().put(group("G1")))
        Groups.refresh(c)
        offline = true
        assertTrue(runCatching { Groups.refresh(c) }.isFailure)
        assertTrue(runCatching { Groups.join(c, "LG2", null, MAIN_LIST) }.isFailure)
        assertEquals(listOf("G1"), Groups.groups.map { it.id })
        restart(Groups); Groups.load(c)
        assertEquals("the copy on the phone after a restart", listOf("G1"), Groups.groups.map { it.id })
    }

    @Test fun aWrongMyLinkChangesNothing() = runBlocking {
        val before = Repo.mineIds
        assertTrue(runCatching { Groups.takeover(c, "NOTWEB", "t", "d") }.isFailure)
        assertEquals(before, Repo.mineIds)
        assertFalse("nothing was moved", sent.any { it.startsWith("wl_gr_takeover") })
        assertNull(Groups.parseMe("#me=too-short"))
    }
}
