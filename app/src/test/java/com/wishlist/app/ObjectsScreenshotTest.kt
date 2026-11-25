package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * The app's little real objects (owner, 24 November 2026; Objects.kt): the empty pages with the open padlock and the
 * stamp; a card with the "Got it!" stamp (the price tag was taken out again on 25 November 2026). Both styles, light, dark and
 * large text, checked by the layout inspector. Pictures in app/build/outputs/roborazzi/objects_*.png; only with
 * -Pwl.screens (animations are off there, so each object is shown at rest).
 */
class ObjectsScreenshotTest : NavigationBase() {
    private fun each(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark", "big")) {
            compose.runOnUiThread { compose.activity.setContent {} }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            compose.runOnUiThread { compose.activity.setContent {
                val look = lookFor(st, "blue", if (m == "big") "light" else m)
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, if (m == "big") 1.3f else d.fontScale)
                ) { LookTheme(look) { androidx.compose.material3.Surface { Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) { content() } } } }
            } }
            repeat(8) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val path = "build/outputs/roborazzi/objects_${name}_${st}_$m.png"
            compose.guard(path); captureScreenRoboImage(path)
        }
    }

    @Test fun emptyPages() = each("empty") {
        EmptyState(AppIcons.Gift, tr("Nothing reserved yet. Open a friend’s wishlist, tap Reserve on a gift you’ll buy, and it shows here. They never see who reserved it."), tr("Go to Friends"), {}, art = { RestingPadlock() })
        EmptyState(AppIcons.Gift, tr("No received gifts yet. After a wishlist’s occasion you can mark the wishes you got, and they show up here."), tr("Back"), {}, art = { RestingStamp() })
    }

    @Test fun stampedCard() {
        val w = Wish(78, "https://example.com/v", "A very long wish name to see the stamp over a full card", "", "", 25.0, null, "EUR", "")
        each("stamp") {
            val look = LocalLook.current
            Box(Modifier.padding(16.dp)) {
                RowCard(w) {}
                StampOverlay(Modifier.matchParentSize().clip(look.cardShape(w.id, small = false)))
            }
        }
    }

    // "Unreserve?" (owner, 25 November 2026): the padlock prominent at the top, then the gift's photo and name.
    @Test fun unreserveDialog() {
        val w = Wish(79, "https://example.com/m", "Mugler Fragrances ALIEN - Eau de parfum with a long name", "", "", 89.0, null, "EUR", "")
        each("unreserve") { UnreserveDialog(w, onYes = {}, onNo = {}) }
    }

    // Received gifts with the gifts' photos (owner, 25 November 2026: just text made it unclear).
    @Test fun receivedWithPhotos() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread {
            Repo.addMine(app, Wish(81, "https://example.com/j", "Mackinaw Wool G9", "", "", 470.0, null, "EUR", ""))
            Repo.addMine(app, Wish(82, "https://example.com/s", "Barbour ICELOCH TAILORED TARTAN - Overhemd - forest mist", "", "", 99.0, null, "EUR", ""))
            Repo.receive(app, Repo.openMine, setOf(81L, 82L))
        }
        each("received") { ReceivedList() }
    }

    // Gifts I reserved with each gift's photo (owner, 25 November 2026).
    @Test fun reservedWithPhotos() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        FriendWatch.reserved(app, "FRIENDC1", Wish(91, "https://example.com/c", "Yankee Candle SIGNATURE Warm Cashmere large jar", "", "", 29.95, null, "EUR", ""))
        FriendWatch.reserved(app, "FRIENDC1", Wish(92, "https://example.com/d", "Mugler Fragrances ALIEN", "", "", 89.0, null, "EUR", ""))
        each("reserved") { ReservedScreen(onClose = {}, onOpen = {}) }
    }
}
