package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * The rest of the animations of 25 November 2026, frame by frame (see MotionFramesScreenshotTest): the eye's lid, the
 * heart filling, the old price's line, someone else's reservation while you look, two friends' pictures meeting, and
 * cards gliding when the view or the order changes. Pictures in app/build/outputs/roborazzi/motion_<name>_<ms>.png.
 */
class MotionFrames2ScreenshotTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.setContent {
                LookTheme(lookFor("quiet", "blue", "light")) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp)) { content() } }
                }
            }
        }
        repeat(6) { compose.mainClock.advanceTimeBy(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }

    private fun frames(name: String, at: List<Long>, start: () -> Unit) {
        compose.runOnUiThread(start)
        var now = 0L
        for (t in at) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            repeat(2) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeByFrame() }
            captureScreenRoboImage("build/outputs/roborazzi/motion_${name}_$t.png")
        }
    }

    private val old = Wish(System.currentTimeMillis() - 90_000, "https://example.com/c", "Yankee Candle SIGNATURE Warm Cashmere", "", "", 29.95, 38.0, "EUR", "")

    @Test fun eyeShuts() {
        var priv by mutableStateOf(false)
        show { Box(Modifier.size(120.dp)) { PrivateBadge(Modifier.padding(8.dp), size = 40.dp, shown = priv) } }
        frames("eye", listOf(0, 60, 140, 220, 320, 450)) { priv = true }
        frames("eyeopen", listOf(0, 120, 240, 360, 520)) { priv = false }
    }

    @Test fun heartFills() {
        var on by mutableStateOf(false)
        show { StarToggle(on, { on = !on }, size = 80.dp) }
        frames("heart", listOf(0, 50, 100, 160, 240, 360)) { on = true }
    }

    @Test fun oldPriceIsStruckThrough() {
        var shown by mutableStateOf(false)
        show { if (shown) Box(Modifier.width(220.dp)) { PriceLine(old.copy(id = System.currentTimeMillis() - 120_000), androidx.compose.material3.MaterialTheme.typography.headlineSmall, stacked = true) } }
        frames("strike", listOf(0, 200, 330, 450, 560, 760)) { shown = true }
    }

    @Test fun reservedWhileYouLook() {
        var state by mutableStateOf("reserve")
        show { Box(Modifier.width(220.dp)) { ReserveButton(old, state, if (state == "reserve") "Reserve" else "Reserved", {}) } }
        // The list has settled (a change while it is loading isn't "live").
        repeat(25) { compose.mainClock.advanceTimeBy(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        Thread.sleep(2100)
        frames("taken", listOf(0, 80, 180, 300, 420, 600, 900)) { state = "taken" }
    }

    @Test fun friendsMeet() {
        var go by mutableStateOf(false)
        show { PersonRow(Person("p9", "Magdalena", 0L), "wants to be your friend", meeting = if (go) ({}) else null) {} }
        frames("meet", listOf(0, 100, 200, 300, 380, 460, 640)) { go = true }
    }

    private val many = (1..4).map { i -> old.copy(id = 1_000L + i, title = "Wish number $i", price = 10.0 * i) }

    @Test fun viewChangeFadesThrough() {
        var view by mutableStateOf("grid")
        show {
            LazyVerticalGrid(
                columns = if (view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                modifier = Modifier.fadeThroughOn(view),
                contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(many, key = { it.id }) { w ->
                    Box(Modifier.animateItem(placementSpec = glideUnless(view))) { if (view == "grid") CompactCard(w, readOnly = true) {} else RowCard(w) {} }
                }
            }
        }
        frames("view", listOf(0, 60, 120, 200, 300, 500)) { view = "rows" }
    }

    @Test fun sortSlides() {
        var order by mutableStateOf(many)
        show {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(order, key = { it.id }) { w -> Box(Modifier.animateItem()) { CompactCard(w, readOnly = true) {} } }
            }
        }
        frames("sort", listOf(0, 80, 160, 260, 400, 700)) { order = many.reversed() }
    }
}
