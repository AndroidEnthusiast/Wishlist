package com.wishlist.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** The database's tables (Db.kt, ROADMAP S2.1): everything comes back exactly as it went in, a save is all or nothing. */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class DbTest {
    private lateinit var db: WishlistDb
    private val dao get() = db.dao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WishlistDb::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    /** A wish with every field set, so a column that is lost or mixed up shows. */
    private val full = Wish(
        7L, "https://shop.example/x", "Teapot", "Blue, 1 l", "file:///img.jpg", 34.95, 39.5, "EUR", "Home",
        got = true, star = true, note = "The blue one", size = "M", by = "Sam", bin = 0L, binBy = "", checked = 123L,
        priv = true, qty = 2, exact = "similar", recv = 456L
    )

    private fun list(id: String, pos: Int) = ListRow(id, "List $id", "about", "WL-CODE", "token", "[\"a.jpg\"]", pos)

    @Test fun aWishComesBackExactly() {
        dao.putLists(listOf(list("main", 0)))
        dao.putWishes(listOf(WishRow("main", full.id, 0, full.cols())))
        val back = dao.wishes("main").single()
        assertEquals(full, back.w.toWish(back.id))
        assertEquals(list("main", 0), dao.lists().single())
    }

    @Test fun aCopyIsARowOnEachList() {
        // Owner, 2.90: a wish copied into another list keeps its id.
        dao.putLists(listOf(list("main", 0), list("b", 1)))
        dao.putWishes(listOf(WishRow("main", 7L, 0, full.cols()), WishRow("b", 7L, 0, full.cols())))
        assertEquals(2, dao.countWishes())
        assertEquals(1, dao.wishes("b").size)
    }

    @Test fun orderIsKept() {
        dao.putLists(listOf(list("main", 0)))
        val ws = (1..5).map { i -> WishRow("main", (10 - i).toLong(), i, full.copy(id = (10 - i).toLong(), title = "W$i").cols()) }
        dao.putWishes(ws.shuffled())
        assertEquals(listOf("W1", "W2", "W3", "W4", "W5"), dao.wishes("main").map { it.w.title })
    }

    @Test fun deletingAListKeepsItsBin() {
        // The bin keeps wishes of a deleted list (its name is in "mine_gone"); the list's own wishes go with it.
        dao.putLists(listOf(list("b", 0)))
        dao.putWishes(listOf(WishRow("b", 1L, 0, full.cols())))
        dao.putBin(listOf(BinRow("b", 2L, 0, full.copy(id = 2L, bin = 99L).cols())))
        dao.deleteList("b")
        assertEquals(0, dao.countWishes())
        assertEquals(99L, dao.bin().single().w.bin)
    }

    @Test fun aSaveIsAllOrNothing() {
        // S2.3 moves everything in one transaction: when one part fails, nothing at all is written.
        try {
            db.runInTransaction {
                dao.putLists(listOf(list("main", 0)))
                dao.putWishes(listOf(WishRow("main", 1L, 0, full.cols())))
                throw IllegalStateException("something went wrong halfway")
            }
        } catch (e: IllegalStateException) {
        }
        assertEquals(0, dao.countLists())
        assertEquals(0, dao.countWishes())
    }

    @Test fun aWishOnAListThatDoesNotExistIsRefused() {
        val refused = try { dao.putWishes(listOf(WishRow("nowhere", 1L, 0, full.cols()))); false } catch (e: Exception) { true }
        assertTrue(refused)
    }

    @Test fun theOtherTables() {
        dao.putCategories(listOf(CategoryRow("Home", 1), CategoryRow("Tech", 0)))
        assertEquals(listOf("Tech", "Home"), dao.categories().map { it.name })
        dao.putPricePoints(listOf(PricePointRow(7L, 200L, 30.0), PricePointRow(7L, 100L, 35.0)))
        assertEquals(listOf(35.0, 30.0), dao.pricePoints(7L).map { it.price })
        dao.putFriendLists(listOf(FriendListRow("WL-ABCD2345", "Robin’s wishlist", 0)))
        assertEquals("Robin’s wishlist", dao.friendLists().single().name)
        val n = PriceNote(7L, "m:main", "Teapot", "", -12.5, 1000L, false, "price", 0.0, "EUR")
        dao.putNotes(listOf(n.toRow(0)))
        assertEquals(n, dao.notes().single().toNote())
        assertNull(dao.meta("moved"))
        dao.putMeta(MetaRow("moved", "2.166"))
        assertEquals("2.166", dao.meta("moved"))
    }
}
