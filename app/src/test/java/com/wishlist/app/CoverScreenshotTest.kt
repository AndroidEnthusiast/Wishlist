package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test
import java.io.File

/**
 * Pictures of a list's cover photo parts (added for the rebuild, 19 November 2026): the top of a list with a cover and
 * its ⋮, the summary card with an occasion, the cover part of the list's settings without and with a cover, and the
 * editor's Preview. The photo is drawn here (stripes), the same every time. Pictures in
 * app/build/outputs/roborazzi/cover_*.png; only with -Pwl.screens (the class name has "Screenshot").
 */
class CoverScreenshotTest : NavigationBase() {
    private fun settle() = repeat(12) { Thread.sleep(40); compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun shot(name: String) { settle(); captureScreenRoboImage("build/outputs/roborazzi/cover_$name.png") }
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun photo(): String {
        val bmp = android.graphics.Bitmap.createBitmap(600, 200, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        val cols = intArrayOf(0xFF2E7D32.toInt(), 0xFFF9A825.toInt(), 0xFF1565C0.toInt(), 0xFFC62828.toInt())
        for (i in 0 until 6) { p.color = cols[i % 4]; cv.drawRect(i * 100f, 0f, i * 100f + 100f, 200f, p) }
        val f = File(app.filesDir, "cover_test.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return android.net.Uri.fromFile(f).toString()
    }

    /** A cover on the open list, written where ListCovers keeps it (no sending online in a check). */
    private fun withCover() {
        val img = photo()
        val o = org.json.JSONObject().put("img", img).put("edit", HeroEdit().toJson()).put("cut", img)
        app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().putString("cover_" + ListCovers.openKey(), o.toString()).commit()
    }

    private fun show(mode: String = "light", content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "berry", mode)) {
            Column(Modifier.fillMaxSize().lookPage().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        } } }
        settle()
    }

    @Test fun headerWithCover() {
        withCover()
        show { ListHeader(Repo.wishes, SnackbarHostState()); ListSummaryCard(Repo.wishes, occasion = "Birthday · in 3 weeks") }
        shot("header")
    }

    @Test fun headerDark() {
        withCover()
        show("dark") { ListHeader(Repo.wishes, SnackbarHostState()); ListSummaryCard(Repo.wishes, occasion = "Birthday · in 3 weeks") }
        shot("header_dark")
    }

    @Test fun settingsWithout() { show { CoverSection(SnackbarHostState()) }; shot("settings_none") }

    @Test fun settingsWith() { withCover(); show { CoverSection(SnackbarHostState()) }; shot("settings_cover") }

    @Test fun preview() {
        val img = photo()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "berry", "light")) { CoverPreview(img, HeroEdit()) {} } } }
        shot("preview")
    }
}
