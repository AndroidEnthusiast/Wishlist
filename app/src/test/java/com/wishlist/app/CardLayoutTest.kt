package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Labels on wish cards (owner, 20 November 2026, after screenshots of labels that overlapped, weren't lined up or
 * didn't fit): on a narrow phone with large text, in every view, the price label and the Love label never overlap and
 * share one line, a reservation is never on the photo, and the price and "Reserved by you" are never cut off.
 */
@Config(qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class CardLayoutTest : NavigationBase() {
    private fun settle() = repeat(14) { Thread.sleep(30); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private val wishes = listOf(
        Wish(9, "https://www.zalando.nl/dress", "Barbour MAIA MINI DRESS – Spijkerjurk – rinse wash/blauw denim", "", "", 111.95, 62.95, "EUR", ""),
        Wish(3, "https://www.zalando.nl/jas", "Barbour BAITON – Jas – weathered marine/blauw", "", "", 263.95, 349.95, "EUR", "", star = true)
    )

    @After fun noServer() { Cloud.pretendServer = null }

    private fun show(view: String) {
        Cloud.pretendServer = { fn, _ -> if (fn == "wl_get_reservations") """[{"id":9,"mine":true},{"id":3,"mine":false}]""" else "[]" }
        compose.runOnUiThread { Repo.setView(compose.activity, view) }
        val list = RemoteList("Magdalena’s wishlist", emptyList(), wishes)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "blue", "dark")) {
                FriendScreen(Friend("FRIENDCODE1", "Magdalena’s wishlist"), SnackbarHostState(), onRemoved = {}, fetch = { list }, onBack = {}, ownerName = "Magdalena", chatWith = "p2")
            }
        } }
        settle(); settle()
    }

    private fun box(text: String, substring: Boolean = false): Rect? =
        compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun layoutOf(text: String): TextLayoutResult? {
        val n = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        val out = ArrayList<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.firstOrNull()
    }

    /** Narrower than the text wants (Compose's own overflow flag is also set for text that fits, so measure). */
    private fun cut(r: TextLayoutResult) = r.multiParagraph.intrinsics.maxIntrinsicWidth > r.size.width + 1f

    private fun check(view: String) {
        show(view)
        // The price never cut off, on one line.
        val price = layoutOf("€111.95")
        assertTrue("$view: the price shows", price != null)
        assertFalse("$view: the price is cut off", cut(price!!) || price.lineCount > 1)
        // "Reserved by you" whole (two lines at most, never "…") and under the photo, not on it.
        val mine = layoutOf("Reserved by you")
        assertTrue("$view: Reserved by you shows", mine != null)
        assertFalse("$view: Reserved by you is cut off", mine!!.isLineEllipsized(mine.lineCount - 1) || mine.lineCount > 2)
        val rise = box("+78%")
        val status = box("Reserved by you", substring = true)
        if (rise != null && status != null) assertTrue("$view: the reservation is on the photo", status.top >= rise.bottom)
        // In the words, under the name (Rows had it on the photo, next to the words).
        val name = box("Barbour MAIA", substring = true)
        assertTrue("$view: the reservation isn't under the name", name != null && status != null && status.left >= name.left - 2f && status.top >= name.bottom - 2f)
        // The price label and Love never overlap and sit on one line.
        val drop = box("−25%")
        val love = box("loves it", substring = true) ?: box("Loves it", substring = true)
        if (drop != null && love != null) {
            assertFalse("$view: the price label and Love overlap", drop.overlaps(love))
            assertTrue("$view: the price label and Love aren't on one line", kotlin.math.abs(drop.center.y - love.center.y) < 2f)
        }
    }

    @Test fun rows() = check("rows")
    @Test fun twoPerRow() = check("grid")
    @Test fun largeCards() = check("cards")
}
