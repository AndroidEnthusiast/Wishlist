package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * No dead ends from a notification (owner, 20 November 2026; a price drop of a deleted wish opened an empty list):
 * a wish still there opens, one in the bin says so (Restore), one moved to another list opens there, one deleted for
 * good says so and its notifications go, a list that is gone says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteCheckTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun wish(id: Long, title: String) = Wish(id, "https://shop.example/$id", title, "", "", 20.0, null, "EUR", "")

    private fun setUp(): Wish {
        ListStore.reset(); WishlistDb.reset()
        Repo.load(ctx)
        Notifications.clear(ctx)
        val w = wish(5001L, "Teapot")
        Repo.add(ctx, w)
        Notifications.priceChanged(ctx, "m:" + Repo.openMine, w, 30.0, 20.0)
        return w
    }

    private fun note() = Notifications.all.first { it.kind == "price" && it.wishId == 5001L }

    @Test fun aWishStillThereOpens() {
        setUp()
        assertEquals(NoteWhere.Open, NoteCheck.where(ctx, note()))
    }

    @Test fun aWishInTheBinSaysSoThenGoesForGood() {
        setUp()
        val n = note()
        Repo.trash(ctx, 5001L)
        assertEquals(NoteWhere.InBin(5001L), NoteCheck.where(ctx, n))
        assertTrue("kept while in the bin", Notifications.all.any { it.wishId == 5001L })
        Repo.dropFromBin(ctx, setOf(5001L))
        assertTrue("deleted for good: its notification goes", Notifications.all.none { it.wishId == 5001L })
        assertTrue(NoteCheck.where(ctx, n) is NoteWhere.Gone)
    }

    @Test fun aMovedWishOpensWhereItIsNow() {
        setUp()
        val n = note()
        val other = Repo.createMine(ctx, "Kitchen", "")
        Repo.moveMine(ctx, Repo.openMine, other, setOf(5001L))
        assertEquals(NoteWhere.Moved(other), NoteCheck.where(ctx, n))
    }

    @Test fun aListThatIsGoneSaysSo() {
        setUp()
        val w = NoteCheck.where(ctx, "price", "s:NOTHERE1", 5001L)
        assertTrue(w is NoteWhere.Gone)
        assertEquals("That wishlist isn’t on this phone any more.", (w as NoteWhere.Gone).message)
        assertTrue(NoteCheck.where(ctx, "price", "m:nolist", 77L) is NoteWhere.Gone)
    }

    @Test fun otherNotificationsAreLeftAlone() {
        setUp()
        assertEquals(NoteWhere.Open, NoteCheck.where(ctx, "friend_req", "r:abc", 0L))
        Notifications.add(ctx, "friend_req", "r:abc", "Sam")
        Repo.trash(ctx, 5001L); Repo.dropFromBin(ctx, setOf(5001L))
        assertTrue(Notifications.all.any { it.kind == "friend_req" })
    }

    // The review of 20 November 2026: a shared wishlist's price drop only opened the app; now the list, on the wish.
    @Test fun aSharedPriceDropOpensItsListOnTheWish() {
        val i = Alerts.priceIntent(ctx, 77L, "s:ABCDEFGH")
        assertEquals("s:ABCDEFGH", i.getStringExtra(MainActivity.OPEN_NOTE))
        assertEquals(77L, i.getLongExtra(Alerts.NOTE_ID, 0L))
        assertEquals("s:ABCDEFGH", i.getStringExtra(Alerts.NOTE_LIST))
        val own = Alerts.priceIntent(ctx, 77L)
        assertEquals("your own wish: its price page", 77L, own.getLongExtra(MainActivity.HISTORY_ID, 0L))
    }
}
