package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The owner's three wishes of 23 November 2026 (docs/decisions/wish-cards.md, last section):
 *  - the reservation is on the button ("Reserved by you" / "Reserved" with a lock), never an extra line; someone
 *    else's darkens the card and sinks below the free ones; "Reserved by you" asks before unreserving;
 *  - new wishes of a friend are counted per list (a red badge on that list) and get a purple "NEW" ribbon until you
 *    leave the list; opening the friend's page no longer clears them; no red dot on the friend's row;
 *  - every list picker shows one person (Personal) or two people (Co-op).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReserveAndNewsTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun src(f: String) = File("src/main/java/com/wishlist/app/$f").readText()
    private fun wish(id: Long, qty: Int = 1, got: Boolean = false) =
        Wish(id, "", "Wish $id", "", "", 10.0, null, "EUR", "").copy(qty = qty, got = got)

    @Before fun clean() { ctx.getSharedPreferences("wishlist", 0).edit().clear().commit() }

    @Test fun theButtonSaysTheReservation() {
        val w = wish(1)
        assertEquals("reserve", reserveState(w, null, null))
        assertEquals("mine", reserveState(w, null, true))
        assertEquals("taken", reserveState(w, null, false))
        assertNull(reserveState(wish(2, got = true), null, false))
        assertEquals("Reserved by you", reserveButtonText(w, null, "mine"))
        assertEquals("Reserved", reserveButtonText(w, null, "taken"))
        assertEquals("Reserve", reserveButtonText(w, null, "reserve"))
        // 2× wishes: yours on the button, "1 of 3 reserved" as the only line while some are free.
        val three = wish(3, qty = 3)
        val some = ResCount(my = 0, n = 1, full = false)
        assertEquals("reserve", reserveState(three, some, null))
        assertEquals("1 of 3 reserved", reserveLine(three, some))
        val mine = ResCount(my = 2, n = 3, full = true)
        assertEquals("mine", reserveState(three, mine, true))
        assertEquals("You reserved 2", reserveButtonText(three, mine, "mine"))
        assertNull(reserveLine(three, mine))
        assertEquals("taken", reserveState(three, ResCount(my = 0, n = 3, full = true), false))
        assertNull(reserveLine(w, null))
    }

    @Test fun reservedOnesSinkBelowTheFreeOnes() {
        val ws = listOf(wish(1), wish(2), wish(3, got = true), wish(4))
        val taken = setOf(2L)
        val out = reservedLast(ws) { w -> reserveState(w, null, if (w.id in taken) false else null) }
        assertEquals(listOf(1L, 4L, 2L, 3L), out.map { it.id })
    }

    @Test fun everyCardUsesTheOneButton() {
        for (f in listOf("Friend.kt", "ListWishItem.kt", "CompactCard.kt")) {
            val s = src(f)
            assertTrue("$f: ReserveButton", s.contains("ReserveButton("))
            assertTrue("$f: no Unreserve button any more", !s.contains("Text(tr(\"Unreserve\")"))
        }
        val parts = src("WishCardParts.kt")
        // "Unreserve?" with an open padlock on the button itself (owner, 23 November 2026), no pop-up.
        assertTrue(parts.contains("tr(\"Unreserve?\")") && parts.contains("AppIcons.LockOpen") && !parts.contains("AlertDialog("))
        assertTrue(src("MainActivity.kt").contains(".dismissReserveAsk()"))
        // Someone else's darkens the card in every view, and sinks on a friend's list.
        assertTrue(src("Friend.kt").contains("dim = w.got || rs == \"taken\"") && src("Friend.kt").contains("reservedLast("))
        assertTrue(src("CompactCard.kt").contains("reserveButton(w, reserve) == \"taken\""))
    }

    private fun remote(vararg ids: Long) = RemoteList("Birthday", emptyList(), ids.map { wish(it) })

    @Test fun newWishesAreCountedPerList() {
        // The first time everything counts as seen.
        assertEquals(0, FriendNews.start(ctx, "F1", listOf(remote(1, 2), remote(10))))
        assertEquals(emptySet<Long>(), FriendNews.newIds(ctx, "F1", remote(1, 2)))
        // Two new wishes on list A, one on list B.
        val a = remote(1, 2, 3, 4); val b = remote(10, 11)
        assertEquals(3, FriendNews.start(ctx, "F1", listOf(a, b)))
        assertEquals(setOf(3L, 4L), FriendNews.newIds(ctx, "F1", a))
        assertEquals(setOf(11L), FriendNews.newIds(ctx, "F1", b))
        // Opening list A makes only A's wishes seen.
        FriendNews.markListSeen(ctx, "F1", a)
        assertEquals(emptySet<Long>(), FriendNews.newIds(ctx, "F1", a))
        assertEquals(setOf(11L), FriendNews.newIds(ctx, "F1", b))
        assertEquals(1, FriendNews.start(ctx, "F1", listOf(a, b)))
    }

    @Test fun theRibbonStaysWhileYouAreOnTheList() = runBlocking {
        FriendNews.start(ctx, "F2", listOf(remote(1)))
        Repo.load(ctx)
        val st = FriendListState(ctx, Friend("CODE2", "Birthday"), SnackbarHostState(), kotlinx.coroutines.MainScope(), friendId = "F2")
        st.fetchRes = { emptyMap() }
        st.refresh { remote(1, 2) }
        assertEquals(setOf(2L), st.fresh)
        // Seen now: the next refresh keeps the ribbon, and coming back later there is none.
        st.refresh { remote(1, 2) }
        assertEquals(setOf(2L), st.fresh)
        assertEquals(emptySet<Long>(), FriendNews.newIds(ctx, "F2", remote(1, 2)))
        // Leaving the list and opening it again: no ribbon any more (owner, 23 November 2026).
        val again = FriendListState(ctx, Friend("CODE2", "Birthday"), SnackbarHostState(), kotlinx.coroutines.MainScope(), friendId = "F2")
        again.fetchRes = { emptyMap() }
        again.refresh { remote(1, 2) }
        assertEquals(emptySet<Long>(), again.fresh)
    }

    @Test fun theFriendPageNoLongerClearsEverything() {
        assertTrue(!src("FriendPage.kt").contains("FriendNews.markSeen("))
        assertTrue(src("FriendPage.kt").contains("fresh = fresh"))
        assertTrue(src("HomeBlocks.kt").contains("NewCountBadge(item.fresh"))
        assertTrue("no red dot on the friend's row", !src("FriendsRows.kt").contains("RedDot()"))
        assertTrue(src("Friend.kt").contains("if (w.id in st.fresh) NewRibbon("))
        // The ribbon and the count on the list are one colour (owner, 23 November 2026: red).
        assertEquals(NewDot, NewRibbonColour)
    }

    @Test fun listPickersShowTheKind() {
        assertTrue(src("FormParts.kt").contains("if (coop) AppIcons.Group else AppIcons.Person"))
        for (f in listOf("MoveChoose.kt", "OverviewScreen.kt", "AddToMine.kt", "Reminders.kt")) {
            assertTrue("$f: list rows use listKindIcon", src(f).contains("listKindIcon("))
            assertTrue("$f: no plain list icon on a wishlist row", !Regex("Icon\\(AppIcons.ListIcon[^)]*\\) \\}, Repo.mineName").containsMatchIn(src(f)))
        }
        assertTrue(src("SaveTo.kt").contains("listKindIcon(targets.firstOrNull { it.key == k }?.code != null)"))
    }
}
