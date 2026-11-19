package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/**
 * Each part of Repo on its own (the data rebuild, owner 19 November 2026): your lists, the bin, categories, App
 * settings and friends' lists. DataSameTest checks them all together against the record made before the rebuild; these
 * say in plain words which rule broke. Copies have their own check (CopiesTest).
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class RepoPartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun w(id: Long, title: String, cat: String) = Wish(id, "https://shop.example/$id", title, "", "", 10.0, null, "EUR", cat)

    /** A new phone, or ([keep]) the same phone started again. */
    private fun start(keep: Boolean = false) {
        if (!keep) {
            c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().clear().commit()
            ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        } else {
            ListStore.flush(); ListStore.reset(); WishlistDb.reset()
        }
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
    }

    // ---- your lists (RepoLists.kt) ----

    @Test fun theOnlyListCannotBeDeleted() {
        start()
        assertFalse(Repo.deleteMine(c, MAIN_LIST))
        assertEquals(listOf(MAIN_LIST), Repo.mineIds)
    }

    @Test fun aNewListGetsATidyNameAndIsNotOpened() {
        start()
        val id = Repo.createMine(c, "   ", "  note  ")
        assertEquals("Wishlist", Repo.mineName(id))
        assertEquals("note", Repo.mine(c, id).about)
        assertEquals(MAIN_LIST, Repo.openMine)
    }

    @Test fun deletingTheOriginalListMovesTheNextIntoItsPlace() {
        start()
        Repo.addMine(c, w(11, "Teapot", "Home"))
        val next = Repo.createMine(c, "Birthday", "")
        Repo.updateMine(c, next) { listOf(w(12, "Cake tin", "Home")) }
        assertTrue(Repo.deleteMine(c, MAIN_LIST))
        assertEquals(listOf(MAIN_LIST), Repo.mineIds)
        assertEquals("Birthday", Repo.mineName(MAIN_LIST))
        assertEquals(listOf(12L), Repo.myWishes.map { it.id })
        // The deleted list's wish waits in the bin, with the deleted list's name.
        assertEquals(listOf(11L), Repo.myBin.map { it.id })
        assertEquals("My wishlist", Repo.binListName(11))
    }

    @Test fun listsComeBackAfterAStart() {
        start()
        Repo.addMine(c, w(13, "Lamp", "Home"))
        val other = Repo.createMine(c, "Garden", "")
        Repo.openMine(c, other)
        Repo.addMine(c, w(14, "Spade", "Home"))
        start(keep = true)
        assertEquals(other, Repo.openMine)
        assertEquals(listOf(14L), Repo.myWishes.map { it.id })
        assertEquals(listOf(13L), Repo.mine(c, MAIN_LIST).wishes.map { it.id })
    }

    // ---- the bin (RepoBin.kt) ----

    @Test fun undoPutsAWishBackInItsPlace() {
        start()
        Repo.addMine(c, w(21, "C", "Home")); Repo.addMine(c, w(22, "B", "Home")); Repo.addMine(c, w(23, "A", "Home"))
        val t = Repo.trash(c, 22)!!
        assertEquals(listOf(23L, 21L), Repo.myWishes.map { it.id })
        assertTrue(Repo.myBin.single().bin > 0)
        Repo.untrash(c, t)
        assertEquals(listOf(23L, 22L, 21L), Repo.myWishes.map { it.id })
        assertTrue(Repo.myBin.isEmpty())
        assertEquals(0L, Repo.myWishes[1].bin)
    }

    @Test fun binningACopyLeavesTheOriginal() {
        start()
        Repo.addMine(c, w(24, "Mug", "Home"))
        val other = Repo.createMine(c, "Office", "")
        Repo.copyInto(c, other, listOf(w(24, "Mug", "Home")))
        Repo.openMine(c, other)
        Repo.trash(c, 24)
        assertEquals(listOf(24L), Repo.mine(c, MAIN_LIST).wishes.map { it.id })
    }

    @Test fun aReceivedGiftIsKeptAndCanGoBack() {
        start()
        Repo.addMine(c, w(25, "Scarf", "Fashion"))
        Repo.receive(c, MAIN_LIST, setOf(25L))
        val got = Repo.myBin.single()
        assertTrue(got.got); assertTrue(got.recv > 0); assertTrue(Repo.myWishes.isEmpty())
        assertTrue(Repo.unreceive(c, 25))
        assertEquals(listOf(25L), Repo.myWishes.map { it.id })
        assertFalse(Repo.myWishes.single().got)
    }

    @Test fun emptyingTheBinForgetsTheWish() {
        start()
        Repo.addMine(c, w(26, "Vase", "Home"))
        Repo.trash(c, 26)
        Repo.dropFromBin(c, setOf(26L))
        assertTrue(Repo.myBin.isEmpty())
        assertFalse(Repo.restoreMine(c, 26))
        start(keep = true)
        assertTrue(Repo.myBin.isEmpty())
    }

    // ---- categories (RepoCats.kt) ----

    @Test fun categoryNamesStayUniqueAndTidy() {
        start()
        assertNull(Repo.addCat(c, "All"))
        assertNull(Repo.addCat(c, "  "))
        assertEquals("Tech", Repo.addCat(c, "tech"))
        assertEquals("Garden tools", Repo.addCat(c, " Garden \n tools "))
        assertFalse(Repo.renameCat(c, "Garden tools", "TECH"))
    }

    @Test fun categoriesChangeOnEveryList() {
        start()
        Repo.addMine(c, w(31, "Pan", "Home"))
        val other = Repo.createMine(c, "Kitchen", "")
        Repo.updateMine(c, other) { listOf(w(32, "Pot", "Home")) }
        assertTrue(Repo.renameCat(c, "Home", "House"))
        assertEquals("House", Repo.mine(c, other).wishes.single().category)
        assertTrue("House" in Repo.mine(c, other).cats)
        assertEquals(2 to 2, Repo.catUse(c, "House"))
        Repo.removeCat(c, "House")
        assertEquals("", Repo.myWishes.single().category)
        assertEquals("", Repo.mine(c, other).wishes.single().category)
        assertFalse("House" in Repo.mine(c, other).cats)
    }

    // ---- App settings (RepoPrefs.kt) and friends' lists (RepoFriendLists.kt) ----

    @Test fun settingsComeBackAfterAStart() {
        start()
        Repo.setLook(c, "chaos", "dark", "teal"); Repo.setView(c, "grid"); Repo.setSort(c, "low")
        Repo.setInterval(c, 12); Repo.setInstantDelete(c, true); Repo.setNotifyRises(c, true)
        Repo.touchRecent(c, "m:$MAIN_LIST")
        start(keep = true)
        assertEquals(listOf("chaos", "dark", "teal"), listOf(Repo.design, Repo.theme, Repo.palette))
        assertEquals(listOf("grid", "low"), listOf(Repo.view, Repo.sort))
        assertEquals(12, Repo.intervalHours)
        assertTrue(Repo.instantDelete); assertTrue(Repo.notifyRises)
        assertTrue("m:$MAIN_LIST" in Repo.recent)
    }

    @Test fun aFriendsListIsFollowedOnce() {
        start()
        Repo.addFriend(c, Friend("FRIENDCC", "Sam"))
        Repo.addFriend(c, Friend("FRIENDCC", "Sammy"))
        assertEquals(listOf(Friend("FRIENDCC", "Sammy")), Repo.friends)
        start(keep = true)
        assertEquals(listOf(Friend("FRIENDCC", "Sammy")), Repo.friends)
        Repo.removeFriend(c, "FRIENDCC")
        assertTrue(Repo.friends.isEmpty())
    }
}
