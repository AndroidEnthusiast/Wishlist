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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import org.robolectric.annotation.Config as Robo

/**
 * The risks the owner chose for the Friends rebuild (19 November 2026), in plain words; FriendsSameTest checks
 * everything together against the record made before the rebuild. With a pretend server (Cloud.pretendServer).
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class FriendsPartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private var offline = false
    private var server = "{}"
    private val sent = mutableListOf<String>()

    private fun person(id: String, name: String, pv: Long = 0) = JSONObject().put("id", id).put("name", name).put("pv", pv)
    private fun state(friends: List<JSONObject> = emptyList(), incoming: List<JSONObject> = emptyList(), outgoing: List<JSONObject> = emptyList()) =
        JSONObject().put("code", "K7Q4MZ").put("friends", JSONArray(friends)).put("incoming", JSONArray(incoming)).put("outgoing", JSONArray(outgoing)).toString()

    private fun restartFriends() {
        Friends::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Friends, false)
        Friends.load(c)
    }

    @Before fun freshPhone() {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        File(c.filesDir, "friends").deleteRecursively()
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
        restartFriends()
        Cloud.pretendServer = { fn, _ ->
            sent += fn
            if (offline) throw java.io.IOException("offline")
            when (fn) {
                "wl_fr_state" -> server
                "wl_fr_photo" -> "\"" + android.util.Base64.encodeToString(byteArrayOf(1, 2, 3), android.util.Base64.NO_WRAP) + "\""
                else -> ""
            }
        }
    }

    @After fun noPretendServer() { Cloud.pretendServer = null }

    private fun buzzes() = shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.size

    @Test fun aRequestIsToldOnceAlsoAfterARestart() = runBlocking {
        Friends.refresh(c)
        server = state(incoming = listOf(person("r1", "Robin")))
        Friends.poll(c)
        assertEquals(1, buzzes())
        Friends.poll(c)
        restartFriends()
        Friends.poll(c)
        assertEquals("told once, however often the check runs", 1, buzzes())
        assertEquals(1, Notifications.all.count { it.kind == "friend_req" })
        assertEquals(1, Friends.waiting)
    }

    @Test fun anApprovalIsKeptOnceWithoutABuzz() = runBlocking {
        server = state(outgoing = listOf(person("o1", "Kim")))
        Friends.refresh(c)
        server = state(friends = listOf(person("o1", "Kim")))
        Friends.poll(c); Friends.poll(c)
        assertEquals(1, Notifications.all.count { it.kind == "friend_ok" })
        assertEquals("an approval only goes on the Notifications page", 0, buzzes())
    }

    @Test fun visibleToFriendsGoesBackWhenOffline() = runBlocking {
        val list = Repo.createMine(c, "Garden", "")
        Repo.setShareFor(c, list, "GARDENLK", "gtoken")
        offline = true
        val error = runCatching { Friends.setVisible(c, list, true) }.exceptionOrNull()
        assertTrue("the screen is told it failed", error != null)
        assertFalse(Friends.isVisible(list))
        restartFriends()
        assertFalse("not saved either", Friends.isVisible(list))
        offline = false
        Friends.setVisible(c, list, true)
        assertTrue(Friends.isVisible(list))
        restartFriends()
        assertTrue(Friends.isVisible(list))
    }

    @Test fun friendsPhotosStayAfterARestartAndOldOnesGo() = runBlocking {
        server = state(friends = listOf(person("f1", "Sam", 2)))
        Friends.refresh(c)
        assertTrue(File(c.filesDir, "friends/f1_2.jpg").exists())
        restartFriends()
        assertEquals(setOf("f1"), Friends.photos.keys)
        sent.clear()
        Friends.refresh(c)
        assertFalse("a photo on the phone isn't fetched again", "wl_fr_photo" in sent)
        // Sam changed their photo: the new one comes, the old file goes.
        server = state(friends = listOf(person("f1", "Sam", 3)))
        Friends.refresh(c)
        assertEquals(listOf("f1_3.jpg"), File(c.filesDir, "friends").list()!!.toList())
    }

    @Test fun anOlderSavedFriendsTabStillReads() {
        prefs.edit().putString("fr_state", state(friends = listOf(person("f1", "Sam"))))
            .putString("pf_sz_c", "M").putString("pf_bday", "01-02").putString("pf_bio", "Hi").commit()
        restartFriends()
        assertEquals(listOf("Sam"), Friends.state!!.friends.map { it.name })
        assertEquals("01-02", Friends.myBirthday); assertEquals("Hi", Friends.myBio)
        // Sizes were removed: gone from the phone, and cleared online at the next refresh.
        assertFalse(prefs.contains("pf_sz_c"))
        assertTrue(prefs.getBoolean("pf_sizes_dirty", false))
    }
}
