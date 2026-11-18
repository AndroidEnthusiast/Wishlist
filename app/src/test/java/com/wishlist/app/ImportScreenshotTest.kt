package com.wishlist.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Import sheet's parts (2.226) on the smallest phone width, worst-case data (long names, big amounts, no photo), in
 * every style, light and dark. Pictures in app/build/outputs/roborazzi/import_*.png. Only with -Pwl.screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class ImportScreenshotTest {
    private val read = ImportRead(
        "Sam’s very long birthday wishlist with a name that goes on", "", "gowish",
        listOf(
            Found("Wireless noise-cancelling over-ear headphones in midnight blue with a very long name", "https://www.coolblue.nl/p/1", "", 1299.99, "EUR", love = true),
            Found("", "https://www.bol.com/nl/p/2", "", null, "EUR"),
            Found("Socks", "", "", 12.5, "GBP")
        )
    )

    @Test fun importParts() {
        StillSpinners.on = true
        for (st in STYLES.map { it.key }) for (m in listOf("light", "dark")) {
            captureRoboImage("build/outputs/roborazzi/import_paste_${st}_$m.png") {
                LookTheme(lookFor(st, "indigo", m)) {
                    Box(Modifier.fillMaxWidth().height(640.dp).lookPage().padding(horizontal = 24.dp)) {
                        PastePart("https://gowish.com/s/e04yb6", {}, reading = true, tipFor = "Amazon") {}
                    }
                }
            }
            captureRoboImage("build/outputs/roborazzi/import_check_${st}_$m.png") {
                LookTheme(lookFor(st, "indigo", m)) {
                    Box(Modifier.fillMaxWidth().height(640.dp).lookPage().padding(horizontal = 24.dp)) {
                        CheckPart(read, setOf(0, 2), setOf(1), null, read.name, false, {}, {}, {}, {})
                    }
                }
            }
            captureRoboImage("build/outputs/roborazzi/import_done_${st}_$m.png") {
                LookTheme(lookFor(st, "indigo", m)) {
                    Column(Modifier.fillMaxWidth().height(360.dp).lookPage().padding(horizontal = 24.dp)) {
                        DonePart(24, "Sam’s very long birthday wishlist", "GoWish", true) {}
                    }
                }
            }
            captureRoboImage("build/outputs/roborazzi/import_nothing_${st}_$m.png") {
                LookTheme(lookFor(st, "indigo", m)) {
                    Column(Modifier.fillMaxWidth().height(400.dp).lookPage().padding(horizontal = 24.dp)) {
                        NothingPart("Amazon", {}, {})
                    }
                }
            }
        }
    }
}
