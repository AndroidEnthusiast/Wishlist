package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File

/**
 * A friend's list with everything a card can carry at once (owner, 20 November 2026, after screenshots of labels that
 * overlapped or didn't fit): a price rise on a gift you reserved, a drop on one someone else reserved, Love this, a
 * reservation of 2 of 3, long names, a wish without a photo. Every view, Quiet and Gen Z chaos, light and dark; the
 * class below does the same on a narrow phone with large text. Pictures in app/build/outputs/roborazzi/stress_*.png;
 * only with -Pwl.screens.
 */
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
open class CardStressScreenshotTest : NavigationBase() {
    protected open val tag = "normal"
    private fun settle() = repeat(14) { Thread.sleep(40); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private fun photo(name: String, tall: Boolean, col: Int): String {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val bmp = android.graphics.Bitmap.createBitmap(if (tall) 400 else 600, if (tall) 600 else 450, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        cv.drawColor(0xFFECE8E3.toInt())
        val p = android.graphics.Paint().apply { color = col }
        cv.drawRoundRect(bmp.width * 0.25f, bmp.height * 0.15f, bmp.width * 0.75f, bmp.height * 0.85f, 30f, 30f, p)
        val f = File(app.cacheDir, "$name.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + f.absolutePath
    }

    private fun wishes() = listOf(
        Wish(9, "https://www.zalando.nl/dress", "Barbour MAIA MINI DRESS – Spijkerjurk – rinse wash/blauw denim", "", photo("dress", true, 0xFF1F3A68.toInt()), 111.95, 62.95, "EUR", ""),
        Wish(2, "https://www.zalando.nl/bag", "Barbour CROSSBODY BAG – Schoudertas – burgundy", "", photo("bag", false, 0xFF7A1F35.toInt()), 159.95, null, "EUR", ""),
        Wish(3, "https://www.zalando.nl/jas", "Barbour BAITON – Jas – weathered marine/blauw", "", photo("jas", true, 0xFF2B3550.toInt()), 263.95, 349.95, "EUR", "", star = true),
        Wish(4, "https://www.example.com/socks", "Wool socks", "", "", 12.5, null, "EUR", "", qty = 3),
        Wish(5, "", "A wish with a really very long name that goes on and on without a shop", "", "", null, null, "EUR", "")
    )

    @After fun noServer() { Cloud.pretendServer = null }

    private fun show(view: String, style: String, mode: String) {
        Cloud.pretendServer = { fn, _ ->
            if (fn == "wl_get_reservations") """[{"id":9,"mine":true},{"id":3,"mine":false},{"id":4,"mine":true,"my":2,"n":2,"full":false}]""" else "[]"
        }
        compose.runOnUiThread { Repo.setView(compose.activity, view) }
        val list = RemoteList("Magdalena’s wishlist", emptyList(), wishes())
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor(style, "blue", mode)) {
                FriendScreen(Friend("FRIENDCODE1", "Magdalena’s wishlist"), SnackbarHostState(), onRemoved = {}, fetch = { list }, onBack = {}, ownerName = "Magdalena", chatWith = "p2")
            }
        } }
        settle(); settle()
        compose.guard("build/outputs/roborazzi/stress_${tag}_${view}_${style}_$mode.png"); captureScreenRoboImage("build/outputs/roborazzi/stress_${tag}_${view}_${style}_$mode.png")
    }

    @Test fun rowsQuiet() { show("rows", "quiet", "dark") }
    @Test fun gridQuiet() { show("grid", "quiet", "dark") }
    @Test fun cardsQuiet() { show("cards", "quiet", "dark") }
    @Test fun rowsChaos() { show("rows", "chaos", "light") }
    @Test fun gridChaos() { show("grid", "chaos", "light") }
    @Test fun cardsChaos() { show("cards", "chaos", "light") }
}

/** The same on a narrow phone (360 dp) with the phone's text at 130 %. */
@Config(qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class CardStressLargeTextScreenshotTest : CardStressScreenshotTest() {
    override val tag = "large"
}

/**
 * Your own lists with everything a card can carry (owner, 20 November 2026: "if the views are ever affected by a
 * choice the implementation and UI remains flawless"): long names, price drops and rises, Love this, a private wish, a
 * quantity, no photo and no price; every view, Quiet and Gen Z chaos, on a narrow phone with the phone's text at 130 %.
 * The layout inspector runs on each.
 */
@Config(qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class OwnListStressScreenshotTest : NavigationBase() {
    private fun settle() = repeat(14) { Thread.sleep(30); compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private fun fill() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread {
            listOf(
                Wish(201, "https://www.zalando.nl/candle", "Yankee Candle SIGNATURE MEDIUM JAR WARM CASHMERE – Geurkaars – multi", "", "", 33.99, 33.19, "EUR", "Home"),
                Wish(202, "https://www.zalando.nl/candle2", "Yankee Candle LARGE JAR – Geurkaars – pink sands", "", "", 1299.95, 1414.99, "EUR", "Home", star = true),
                Wish(203, "https://example.com/socks", "Wool socks", "", "", 12.5, null, "EUR", "", qty = 3, priv = true),
                Wish(204, "", "A wish with a really very long name that goes on and on without a shop", "", "", null, null, "EUR", "")
            ).filter { w -> Repo.myWishes.none { it.id == w.id } }.forEach { Repo.addMine(app, it) }
        }
    }

    private fun show(view: String, style: String, mode: String) {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread { Repo.setLook(app, style, mode, Repo.palette); Repo.setView(app, view) }
        fill()
        // Opened as a tapped notification would (with the clock running, so the screen change happens), then held still.
        send(open(MainActivity.OPEN_NOTE, "m:" + Repo.mineIds.first()))
        compose.mainClock.autoAdvance = false
        settle(); settle()
        val name = "build/outputs/roborazzi/ownstress_${view}_${style}_$mode.png"
        compose.guard(name); com.github.takahirom.roborazzi.captureScreenRoboImage(name)
    }

    @Test fun rowsQuiet() = show("rows", "quiet", "dark")
    @Test fun gridQuiet() = show("grid", "quiet", "light")
    @Test fun cardsQuiet() = show("cards", "quiet", "dark")
    @Test fun rowsChaos() = show("rows", "chaos", "light")
    @Test fun gridChaos() = show("grid", "chaos", "dark")
    @Test fun cardsChaos() = show("cards", "chaos", "light")
}
