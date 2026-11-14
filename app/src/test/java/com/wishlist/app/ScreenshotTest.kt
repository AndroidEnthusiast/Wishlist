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
import androidx.compose.ui.test.longClick
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
abstract class ScreenshotBase(private val mode: String, private val tag: String = "") {
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
        // Animations off, as on a phone with "Remove animations": November's snow on Home (Snow.kt) never stops
        // otherwise, so the pretend phone never settles ("Compose did not get idle", 2.190 and 2.191).
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // No welcome, no background question and a name: without a name "What's your name?" opens at the start, and
        // its blinking cursor never lets the pretend phone settle either.
        app.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("asked_background", true).putString("sh_me", "Sam").commit()
        // A new pretend phone for every picture: its own database (the app keeps it in objects between checks).
        ListStore.reset(); WishlistDb.reset()
        Repo.load(app)
        Repo.setTheme(app, mode)
        // -Pwl.style=… (owner, 13 November 2026: every style is checked, light and dark).
        System.getProperty("wl.style").orEmpty().takeIf { it.isNotEmpty() }?.let {
            Repo.setLook(app, it, mode, Repo.palette)
        }
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
        // In steps, letting Android's own main thread run in between (a tap's screen change is posted there).
        repeat(10) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        val st = System.getProperty("wl.style").orEmpty().ifEmpty { "quiet" }
        captureScreenRoboImage("build/outputs/roborazzi/${st}_${name}_$mode$tag.png")
    }

    /** Taps the button or card that holds this text (or has it as its label). */
    private fun tap(text: String, exact: Boolean = false, not: String? = null) {
        var m = (hasText(text, substring = !exact) or hasContentDescription(text)) and hasClickAction()
        if (not != null) m = m and !hasText(not)
        val node = compose.onAllNodes(m).onFirst()
        node.assertExists()
        compose.mainClock.autoAdvance = false
        node.performTouchInput { click() }
    }

    @Test fun home() { compose.waitForIdle(); compose.mainClock.autoAdvance = false; shot("home") }
    // The list's card on Home shows its number of wishes ("5"); its name is under the card. "5 Wishes" at the top
    // opens All your wishes.
    @Test fun list() { tapAbove("My wishlist", 150f); shot("list") }
    @Test fun allWishes() { tap("Wishes"); shot("all") }
    @Test fun addWish() { tap("Add wish"); shot("add") }

    /** Taps just above a text: the card or tile it is the name of. */
    private fun tapAbove(text: String, px: Float) {
        // With big text on a small phone the list's block is below the screen, out of reach of this tap: those
        // pictures are skipped there (the others still run). Not a fault in the app.
        org.junit.Assume.assumeTrue(tag.isEmpty())
        compose.waitForIdle()
        val name = compose.onAllNodes(hasText(text, substring = false), useUnmergedTree = true).onFirst()
        name.assertExists()
        compose.mainClock.autoAdvance = false
        name.performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, top - px)) }
    }
    // "My shops" took the place of Price watch on the bar (7 November 2026).
    @Test fun shops() { tap("My shops"); shot("shops") }
    // The wish's ⋮ sheet and selecting several wishes (owner, 12 November 2026).
    @Test fun wishSheet() {
        tapAbove("My wishlist", 150f); compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performTouchInput { click() }
        shot("sheet")
    }
    @Test fun selecting() {
        tapAbove("My wishlist", 150f); compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodes(hasText("Ceramic teapot", substring = true)).onFirst().performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodes(hasText("Rain jacket", substring = true)).onFirst().performTouchInput { click() }
        shot("selecting")
    }
    @Test fun friends() { tap("Friends"); shot("friends") }
    @Test fun messages() { tap("Messages"); shot("messages") }
    // App settings (Quiet 2.207: grouped blocks), from the profile picture's sheet.
    @Test fun settings() {
        tap("Profile and settings"); repeat(5) { compose.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        tap("App settings"); shot("settings")
    }
}

class ScreenshotLight : ScreenshotBase("light")
class ScreenshotDark : ScreenshotBase("dark")
/** The smallest common phone (360 dp wide) with the phone's text at 130 % (docs/DESIGN-STYLES.md, step 5). */
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi", fontScale = 1.3f)
class ScreenshotBigText : ScreenshotBase("light", "_big")

/**
 * A filmstrip of the movement (owner, 12 November 2026: "like Signal"): Home → the list and Home → Messages, drawn at
 * 0, 60, 120 and 250 ms with animations on. Only run with -Pwl.screens, like the other pictures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotMotion {
    private val setup = object : TestRule {
        override fun apply(base: Statement, d: Description) = object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<android.app.Application>()
                WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
                Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
                android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                app.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit()
                    .putBoolean("welcomed", true).putBoolean("asked_background", true).putString("sh_me", "Sam").commit()
                ListStore.reset(); WishlistDb.reset()
                Repo.load(app)
                Repo.setTheme(app, "light")
                if (Repo.myWishes.isEmpty()) for (i in 1..4) Repo.addMine(app, Wish(i.toLong(), "https://example.com/$i", "Wish $i", "", "", 10.0 * i, null, "EUR", "Home"))
                base.evaluate()
            }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    private fun film(name: String, start: () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        start()
        var t = 0L
        for (at in listOf(0L, 60L, 120L, 250L)) {
            compose.mainClock.advanceTimeBy(at - t); t = at
            captureScreenRoboImage("build/outputs/roborazzi/motion_${name}_$at.png")
        }
    }

    @Test fun openAList() = film("list") {
        val n = compose.onAllNodes(hasText("My wishlist", substring = false), useUnmergedTree = true).onFirst()
        n.performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, top - 150f)) }
    }

    @Test fun aTabTappedTwiceWhileMovingOpensOnce() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        val tab = compose.onAllNodes(hasContentDescription("Messages") and hasClickAction()).onFirst()
        tab.performClick()
        compose.mainClock.advanceTimeBy(60) // still moving
        tab.performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(600)
        compose.onAllNodes(hasContentDescription("Home") and androidx.compose.ui.test.isSelected()).onFirst().assertExists()
    }

    @Test fun switchToMessages() = film("tab") {
        compose.onAllNodes(hasContentDescription("Messages") and hasClickAction()).onFirst().performClick()
    }
}
