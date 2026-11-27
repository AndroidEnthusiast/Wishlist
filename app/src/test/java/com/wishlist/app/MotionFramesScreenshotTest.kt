package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * The animations frame by frame (owner, 25 November 2026: "make sure all of them are proper quality animations"):
 * each is played with animations on and photographed at several moments, for a person to look at before release.
 * Pictures in app/build/outputs/roborazzi/motion_<name>_<ms>.png; only with -Pwl.screens.
 */
class MotionFramesScreenshotTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var motion: CardMotion
    private var arrivingId by androidx.compose.runtime.mutableLongStateOf(0L)

    private fun frames(name: String, at: List<Long>, start: () -> Unit) {
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.mainClock.autoAdvance = false
        repeat(6) { compose.mainClock.advanceTimeBy(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        compose.runOnUiThread(start)
        var now = 0L
        for (t in at) {
            compose.mainClock.advanceTimeBy(t - now); now = t
            repeat(2) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeByFrame() }
            captureScreenRoboImage("build/outputs/roborazzi/motion_${name}_$t.png")
        }
    }

    private val wish = Wish(System.currentTimeMillis() - 60_000, "https://example.com/c", "Yankee Candle SIGNATURE Warm Cashmere", "", "", 29.95, 38.0, "EUR", "")

    /** A small stage: a list with one card and its bin, and a bottom bar with the Home icon. */
    private fun stage(showCard: () -> Boolean, mode: String = "light") = compose.runOnUiThread {
        compose.activity.setContent {
            LookTheme(lookFor("quiet", "blue", mode)) {
                motion = rememberCardMotion()
                androidx.compose.runtime.CompositionLocalProvider(LocalCardMotion provides motion) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.fillMaxWidth()) {
                                    val w = if (arrivingId != 0L) wish.copy(id = arrivingId) else wish
                                    if (showCard()) Box(Modifier.width(180.dp).motionCard(w.id)) {
                                        CompactCard(w, readOnly = false) {}
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Icon(AppIcons.Home, "Home", Modifier.size(30.dp).motionHome().homeBounce())
                                    Icon(AppIcons.Friends, "Friends", Modifier.size(30.dp))
                                }
                            }
                            FlightLayer(motion)
                        }
                    }
                }
            }
        }
    }

    @Test fun movedToHome() {
        var shown by androidx.compose.runtime.mutableStateOf(true)
        stage({ shown })
        frames("move", listOf(0, 100, 200, 300, 400, 460, 600)) { motion.fly(listOf(wish.id)) { shown = false } }
    }

    @Test fun copyPeelsOff() {
        stage({ true })
        frames("copy", listOf(0, 70, 140, 260, 380, 500, 600)) { motion.fly(listOf(wish.id), peel = true) {} }
    }

    @Test fun newWishArrives() {
        var shown by androidx.compose.runtime.mutableStateOf(false)
        stage({ shown })
        // A wish made just now (its id is the moment it was made) drops in when it first shows.
        frames("arrive", listOf(0, 60, 120, 200, 320, 500)) { arrivingId = System.currentTimeMillis(); shown = true }
    }
}
