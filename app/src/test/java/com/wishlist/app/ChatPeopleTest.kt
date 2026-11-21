package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Chat about this wish" on a shared wishlist (owner, 21 November 2026): who is on the list, friends first; the picker
 * always shows except with exactly one other person who is your friend; the answer is kept for offline; the menu row
 * only on a shared list that is open, with the server set up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatPeopleTest {
    private val ben = ListPerson("B", "Ben", "yes")
    private val cleo = ListPerson("C", "Cleo", "no")

    @Test fun readsTheServersAnswer() {
        val p = SharedPeople.parse("""[{"id":"B","name":" Ben ","friend":"yes"},{"id":"C","name":"Cleo","friend":"asked"},{"id":"","name":"x"}]""")
        assertEquals(listOf(ListPerson("B", "Ben", "yes"), ListPerson("C", "Cleo", "asked")), p)
        assertEquals(emptyList<ListPerson>(), SharedPeople.parse("not json"))
        assertEquals(p, SharedPeople.parse(SharedPeople.toJson(p)))
    }

    @Test fun straightToTheChatOnlyWithOneFriend() {
        assertEquals("B", SharedPeople.direct(listOf(ben)))
        assertNull(SharedPeople.direct(listOf(cleo)))
        assertNull(SharedPeople.direct(listOf(ben, ListPerson("D", "Dee", "yes"))))
        assertNull(SharedPeople.direct(listOf(ben, cleo)))
        assertNull(SharedPeople.direct(emptyList()))
    }

    @Test fun friendsComeFirstAndNewFriendsCount() {
        assertEquals(listOf(ben, cleo), SharedPeople.withFriends(listOf(cleo, ben), emptySet()))
        // Cleo became a friend since the last answer: she can be picked at once.
        assertEquals(listOf(ben, cleo.copy(friend = "yes")).map { it.id }.toSet(), SharedPeople.withFriends(listOf(cleo, ben), setOf("C")).map { it.id }.toSet())
        assertTrue(SharedPeople.withFriends(listOf(cleo), setOf("C")).single().isFriend)
    }

    @Test fun theAnswerIsKeptForOffline() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        assertNull(SharedPeople.cached(app, "LIST1"))
        app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().putString("shp_LIST1", SharedPeople.toJson(listOf(ben, cleo))).commit()
        assertEquals(listOf(ben, cleo), SharedPeople.cached(app, "LIST1"))
    }

    @Test fun theMenuRowIsOnlyOnAnOpenSharedList() {
        val w = Wish(42L, "https://shop.example/t", "Teapot", "", "", 39.0, null, "EUR", "")
        val before = Shared.active
        try {
            Shared.active = null
            assertNull(chatOnShared(w))
            Shared.active = "LIST1"
            if (com.wishlist.app.Config.enabled) {
                chatOnShared(w)!!.invoke()
                assertEquals("LIST1", ChatAboutFlow.asking!!.first)
                assertEquals(42L, ChatAboutFlow.asking!!.second.id)
                assertEquals("LIST1", ChatAboutFlow.asking!!.second.code)
            } else assertNull(chatOnShared(w))
        } finally { Shared.active = before; ChatAboutFlow.asking = null }
    }
}
