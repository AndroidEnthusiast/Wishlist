package com.wishlist.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** Saving only what changed (DbDiff.kt, 2.169): the database ends up exactly like the new data, writing little. */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class DbDiffTest {
    private lateinit var db: WishlistDb

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WishlistDb::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun w(id: Long, title: String) = Wish(id, "https://shop.example/$id", title, "", "", 10.0, null, "EUR", "Home")
    private fun list(id: String, vararg ws: Wish) = MineData(id, "List $id", "", listOf("Home"), ws.toList(), null, null, emptySet())

    private val start = Snapshot(
        listOf(list("main", w(1, "Teapot"), w(2, "Lamp"), w(3, "Book")), list("b", w(9, "Novel"))),
        listOf("main" to w(20, "Old").copy(bin = 5L)),
        listOf("Home"),
        mapOf(1L to listOf(PricePoint(100, 12.0))),
        listOf(Friend("WL-ABCD2345", "Robin")),
        emptyList()
    )

    /** Writes [from] whole, then the step to [to]; the database must then read back as [to]. */
    private fun step(from0: Snapshot, to0: Snapshot): DbDiff.Written {
        // As the app keeps them: one set of categories on every list (ListStore.same).
        val from = ListStore.same(from0)
        val to = ListStore.same(to0)
        DbMove.copy(db, from, "t")
        val written = DbDiff.apply(db, from, to)
        assertEquals(to, ListStore.read(db.dao()))
        return written
    }

    @Test fun editingOneWishWritesOneRow() {
        val to = start.copy(lists = start.lists.map { l -> if (l.id == "main") l.copy(wishes = l.wishes.map { if (it.id == 2L) it.copy(title = "Desk lamp") else it }) else l })
        assertEquals(DbDiff.Written(1, 0), step(start, to))
    }

    @Test fun addingAndRemovingAtTheEnd() {
        val added = start.copy(lists = start.lists.map { l -> if (l.id == "b") l.copy(wishes = l.wishes + w(10, "Poster")) else l })
        assertEquals(DbDiff.Written(1, 0), step(start, added))
        assertEquals(DbDiff.Written(0, 1), step(added, start))
    }

    @Test fun deletingAWishMovesItToTheBin() {
        // Delete with the bin (Bin.kt): off the list, into the bin; the wishes after it move up one place.
        val to = start.copy(
            lists = start.lists.map { l -> if (l.id == "main") l.copy(wishes = l.wishes.filter { it.id != 2L }) else l },
            bin = listOf("main" to w(2, "Lamp").copy(bin = 9L)) + start.bin
        )
        step(start, to)
    }

    @Test fun aNewListADeletedListAndAReorder() {
        val to = start.copy(lists = listOf(
            list("main", w(3, "Book"), w(1, "Teapot"), w(2, "Lamp")),
            list("c", w(1, "Teapot copy"))
        ))
        step(start, to)
    }

    @Test fun historyCategoriesFriendsAndNotes() {
        val to = start.copy(
            categories = listOf("Home", "Garden"),
            history = mapOf(1L to listOf(PricePoint(100, 12.0), PricePoint(200, 9.0)), 2L to listOf(PricePoint(150, 30.0))),
            friends = emptyList(),
            notes = listOf(PriceNote(1L, "m:main", "Teapot", "", -25.0, 200L, false))
        )
        step(start, to)
    }

    @Test fun nothingChangedWritesNothing() {
        assertEquals(DbDiff.Written(0, 0), step(start, start))
    }
}
