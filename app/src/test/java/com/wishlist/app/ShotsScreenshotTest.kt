package com.wishlist.app

import androidx.activity.compose.setContent
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test
import java.io.File

/**
 * Pictures of Screenshot to wish's pick screen (20 November 2026): five screenshots, one that couldn't be opened, one
 * unticked, and more than ten shared. The pictures are drawn here. In app/build/outputs/roborazzi/shots_*.png; only
 * with -Pwl.screens (the class name has "Screenshot").
 */
class ShotsScreenshotTest : NavigationBase() {
    private fun settle() = repeat(10) { Thread.sleep(60); compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); captureScreenRoboImage("build/outputs/roborazzi/shots_$name.png") }

    private fun picture(i: Int): String {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val bmp = android.graphics.Bitmap.createBitmap(360, 780, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        cv.drawColor(0xFFF4F1EC.toInt())
        p.color = intArrayOf(0xFF2E7D32.toInt(), 0xFFF9A825.toInt(), 0xFF1565C0.toInt(), 0xFFC62828.toInt())[i % 4]
        cv.drawRoundRect(40f, 200f, 320f, 520f, 30f, 30f, p)
        p.color = 0xFF222222.toInt(); cv.drawRect(0f, 0f, 360f, 60f, p)
        val f = File(app.cacheDir, "shot_$i.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + f.absolutePath
    }

    private fun show(style: String, mode: String) {
        val pics = (1..5).map { if (it == 4) "content://gone/$it" else picture(it) }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor(style, "berry", mode)) { ShotsFlow(Deep.Shots(pics, more = true)) { _, _ -> } }
        } }
        settle()
    }

    @Test fun pickQuiet() { show("quiet", "light"); shot("pick_quiet") }

    @Test fun pickChaosDark() { show("chaos", "dark"); shot("pick_chaos_dark") }
}
