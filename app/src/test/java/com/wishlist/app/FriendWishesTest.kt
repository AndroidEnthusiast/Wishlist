package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Owner, 18 November 2026: Add to my wishlist, compact Reserve pills, the + menu order, + React hiding once you reacted. */
class FriendWishesTest {
    private fun src(f: String) = File("src/main/java/com/wishlist/app/$f").readText()

    @Test fun addToMyWishlistOnlyOnSomeoneElsesWish() {
        val w = Wish(1, "https://shop.example/a", "Lamp", "", "", 20.0, null, "EUR", "")
        assertTrue("addmine" in wishSheetRows(w, WishActions(shareAsIdea = true)))
        assertFalse("addmine" in wishSheetRows(w, WishActions(own = true, onEdit = {})))
    }

    @Test fun theCopyKeepsOnlyWhatDescribesTheThing() {
        val theirs = Wish(5, "https://shop.example/a", "Lamp", "A lamp", "https://img.example/a.jpg", 20.0, 25.0, "EUR", "Home",
            star = true, note = "In blue please", size = "M", qty = 2, exact = "exact", by = "Sam")
        val mine = AddToMine.copyOf(theirs, 99)
        assertEquals(99L, mine.id)
        assertEquals(listOf("https://shop.example/a", "Lamp", "https://img.example/a.jpg"), listOf(mine.url, mine.title, mine.image))
        assertEquals(20.0, mine.price!!, 0.0); assertEquals("EUR", mine.currency)
        assertEquals(null, mine.oldPrice)
        assertFalse(mine.star); assertEquals("", mine.note); assertEquals("", mine.size); assertEquals(1, mine.qty)
        assertEquals("", mine.exact); assertEquals("", mine.by); assertEquals("", mine.category)
        assertEquals("a photo on their phone isn't copied", "", AddToMine.copyOf(theirs.copy(image = "file:///x.jpg"), 1).image)
    }

    @Test fun reserveIsACompactPillOnEveryCard() {
        for (f in listOf("WishCards.kt")) assertTrue(f, src(f).contains("LocalCardButton provides true"))
    }

    /** Owner, 18 November 2026: Reserve left, the ⋮ last on every card; on shared wishlists Share goes into the ⋮. */
    @Test fun cardButtonsInOneOrder() {
        for (f in listOf("ListScreen.kt", "Friend.kt")) assertTrue(f, src(f).contains("CardButtons(") && src(f).contains("cardIcon("))
        assertTrue(src("ListScreen.kt").contains("if (!(inShared && r != false)) IconButton(onClick = { shareItem("))
        val cb = src("WishCards.kt")
        assertTrue(cb.indexOf("reserve()") < cb.indexOf("icons()") && cb.indexOf("icons()") < cb.indexOf("more()"))
    }

    @Test fun importIsLastInThePlusMenu() {
        val b = src("BottomBar.kt")
        assertTrue(b.indexOf("tr(\"Import a list\")") > b.indexOf("tr(\"New gift exchange\")"))
    }

    @Test fun reactHidesOnceYouReacted() {
        val r = src("Reactions.kt")
        assertTrue(r.contains("if (deco.canReact && (!reacted || again)) Row("))
        assertTrue(r.contains("else if (mine && !again) again = true"))
    }
}
