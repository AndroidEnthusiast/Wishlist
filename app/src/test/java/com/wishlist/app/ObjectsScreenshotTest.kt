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
 * The app's little real objects (owner, 24 November 2026; Objects.kt): the empty pages with the open padlock, the
 * price tag and the stamp; the price page after a drop; a card with the "Got it!" stamp. Both styles, light, dark and
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
        EmptyState(AppIcons.Bag, tr("No prices yet. Wishes with a price show up here, with how much they changed since you added them."), tr("Back"), {}, art = { RestingTag() })
        EmptyState(AppIcons.Gift, tr("No received gifts yet. After a wishlist’s occasion you can mark the wishes you got, and they show up here."), tr("Back"), {}, art = { RestingStamp() })
    }

    @Test fun pricePageAfterADrop() {
        val w = Wish(77, "https://example.com/p", "Perfume", "", "", 79.95, 99.95, "EUR", "")
        val pts = listOf(PricePoint(1_700_000_000L, 99.95), PricePoint(1_700_500_000L, 89.95), PricePoint(1_701_000_000L, 79.95))
        each("price") { Column(Modifier.padding(16.dp)) { HistoryBody(pts, "EUR", 1_701_100_000L, w = w) } }
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
}
