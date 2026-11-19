package com.wishlist.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test
import java.io.File
import androidx.activity.compose.setContent

/**
 * Pictures of the photo editor (added for the rebuild, 19 November 2026, so the before/after comparison covers it):
 * a cover photo with text, its Lighter or darker and Text tools, a turned photo, the round profile frame and a wish's
 * square frame. The photo is drawn here (stripes), so it is the same every time. Pictures in
 * app/build/outputs/roborazzi/hero_*.png; only with -Pwl.screens (the class name has "Screenshot").
 */
class HeroEditorScreenshotTest : NavigationBase() {
    // The photo loads in the background in real time: a short real wait each step, so it is always there.
    private fun settle() = repeat(10) { Thread.sleep(60); compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private fun photo(): String {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        val cols = intArrayOf(0xFF2E7D32.toInt(), 0xFFF9A825.toInt(), 0xFF1565C0.toInt(), 0xFFC62828.toInt())
        for (i in 0 until 6) { p.color = cols[i % 4]; cv.drawRect(i * 100f, 0f, i * 100f + 100f, 400f, p) }
        p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(300f, 200f, 90f, p)
        val f = File(app.cacheDir, "hero_test.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun show(start: HeroEdit, round: Boolean = false, wish: Boolean = false) {
        val img = photo()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "berry", "light")) { HeroEditor(img, start, onSave = { _, _ -> }, onCancel = {}, round = round, wish = wish) }
        } }
        // On this test computer the photo is only drawn after a tool changes the layout (Lighter or darker, Text,
        // Fit whole photo); the first picture of each test shows the frame and tools without it. Same every run.
        settle()
    }

    private fun shot(name: String) { settle(); captureScreenRoboImage("build/outputs/roborazzi/hero_$name.png") }
    private fun tool(label: String) {
        // The tool buttons are icons named by their click label.
        compose.onAllNodes(hasClickAction() and androidx.compose.ui.test.SemanticsMatcher(label) {
            (if (androidx.compose.ui.semantics.SemanticsActions.OnClick in it.config) it.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label else null) == label
        }).onFirst().performClick()
        settle()
    }

    @Test fun cover() {
        show(HeroEdit(text = "Sam's birthday", box = true, bright = 0.2f))
        shot("cover")
        tool("Lighter or darker"); shot("light")
        tool("Text"); shot("text")
    }

    @Test fun coverTurned() {
        show(HeroEdit(rotation = 90, text = "Hi", color = 0xFFFFE14D))
        shot("turned")
    }

    @Test fun profile() { show(HeroEdit(), round = true); shot("round") }

    @Test fun wishPhoto() { show(HeroEdit(), wish = true); shot("wish"); tool("Fit whole photo"); shot("fit") }
}
