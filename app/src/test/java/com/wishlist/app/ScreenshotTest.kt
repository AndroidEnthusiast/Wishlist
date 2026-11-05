package com.wishlist.app

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * Screenshot tests: the real app, drawn on a computer (Robolectric) with sample wishes, saved as pictures
 * (Roborazzi) in app/build/outputs/roborazzi. Run by Claude before a push that changes how the app looks
 * (owner, 5 November 2026: default style only, light and dark). Not run by the GitHub build.
 *
 * Run: gradle testDebugUnitTest --tests '*ScreenshotTest*'
 * Only the default style; the picture is a close copy, not the real phone (fonts from Google Fonts don't download,
 * shop icons and photos from the internet may be missing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreenshotBase(private val mode: String) {
    private val setup = object : TestRule {
        override fun apply(base: Statement, d: Description) = object : Statement() {
            override fun evaluate() { prepare(); base.evaluate() }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    private fun prepare() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("asked_background", true).commit()
        Repo.load(app)
        Repo.setTheme(app, mode)
        if (Repo.myWishes.isEmpty()) samples(app).forEach { Repo.addMine(app, it) }
    }

    /** Plain coloured squares stand in for product photos (no pictures from the internet). */
    private fun photo(c: Context, name: String, colour: Int): String {
        val f = File(c.cacheDir, "$name.png")
        val b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(colour)
        cv.drawCircle(150f, 150f, 80f, Paint().apply { color = Color.WHITE; alpha = 120 })
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.toURI().toString()
    }

    private fun samples(c: Context): List<Wish> = listOf(
        Wish(1, "https://example.com/a", "Wireless headphones", "Noise cancelling, black", photo(c, "a", 0xFF37474F.toInt()),
            199.0, 249.0, "EUR", "Tech", star = true),
        Wish(2, "https://example.com/b", "Ceramic teapot", "Hand-made, 1 litre", photo(c, "b", 0xFFE0A96D.toInt()),
            34.95, null, "EUR", "Home", note = "The blue one please"),
        Wish(3, "https://example.com/c", "Rain jacket", "", photo(c, "c", 0xFF2E7D32.toInt()),
            89.0, null, "EUR", "Fashion", size = "M"),
        Wish(4, "https://example.com/d", "A very long book title that does not fit on one line at all", "",
            photo(c, "d", 0xFF8E24AA.toInt()), 22.5, null, "EUR", "Books"),
        Wish(5, "https://example.com/e", "Board game", "", "", null, null, "EUR", "Home"),
    )

    /** The whole screen, pop-ups included. The clock is moved on by hand: some screens animate for ever. */
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(2000)
        captureScreenRoboImage("build/outputs/roborazzi/${name}_$mode.png")
    }

    /** Taps the button or card that holds this text (or has it as its label). */
    private fun tap(text: String, exact: Boolean = false, not: String? = null) {
        var m = (hasText(text, substring = !exact) or hasContentDescription(text)) and hasClickAction()
        if (not != null) m = m and !hasText(not)
        val node = compose.onAllNodes(m).onFirst()
        node.assertExists()
        compose.mainClock.autoAdvance = false
        node.performClick()
    }

    @Test fun home() { compose.waitForIdle(); compose.mainClock.autoAdvance = false; shot("home") }
    // The list's card on Home shows its number of wishes ("5"); its name is under the card. "5 Wishes" at the top
    // opens All your wishes.
    @Test fun list() {
        val all = compose.onAllNodes(hasText("My wishlist", substring = false), useUnmergedTree = true)
        val name = all.onFirst()
        name.assertExists()
        compose.mainClock.autoAdvance = false
        name.performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, top - 150f)) }
        shot("list")
    }
    @Test fun allWishes() { tap("Wishes"); shot("all") }
    @Test fun addWish() { tap("Add wish"); shot("add") }
    @Test fun priceWatch() { tap("Price watch"); shot("prices") }
    @Test fun friends() { tap("Friends"); shot("friends") }
    @Test fun messages() { tap("Messages"); shot("messages") }
}

class ScreenshotLight : ScreenshotBase("light")
class ScreenshotDark : ScreenshotBase("dark")
