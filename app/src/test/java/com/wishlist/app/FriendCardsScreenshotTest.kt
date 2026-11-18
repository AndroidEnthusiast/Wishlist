package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A friend's wishes as a giver sees them (Reserve on the card, the reaction pill), in Rows and Two per row, so the
 * size of the Reserve button can be judged against the card. Pictures in app/build/outputs/roborazzi/friendcards_*.png.
 * Only with -Pwl.screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class FriendCardsScreenshotTest {
    private val ws = listOf(
        Wish(1, "https://example.com/1", "Wireless headphones", "", "", 89.99, null, "EUR", ""),
        Wish(2, "https://example.com/2", "A very long name of a cosy wool blanket in moss green", "", "", 129.0, null, "EUR", ""),
        Wish(3, "https://example.com/3", "Socks", "", "", 12.5, null, "EUR", "")
    )

    @Test fun friendCards() {
        StillSpinners.on = true
        for (m in listOf("light", "dark")) {
            captureRoboImage("build/outputs/roborazzi/friendcards_rows_$m.png") {
                LookTheme(lookFor("quiet", "blue", m)) {
                    CompositionLocalProvider(LocalWishDeco provides WishDeco("X", canReact = true)) {
                        Column(Modifier.fillMaxWidth().lookPage().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ws.forEachIndexed { i, w ->
                                RowCard(w, badge = if (i == 2) "Reserved by you" else null) {
                                    WishMoreButton(w, LocalLook.current.cardMuted(w.id), 24.dp, WishActions(reserve = Reserving(if (i == 2) true else null) {}, shareAsIdea = true))
                                    IconButton(onClick = {}) { Icon(AppIcons.CardShare, null) }
                                    CompositionLocalProvider(LocalCardButton provides true) {
                                        if (i == 2) OutlinedButton(onClick = {}) { Text("Unreserve", maxLines = 1) } else Button(onClick = {}) { Text("Reserve", maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            captureRoboImage("build/outputs/roborazzi/friendcards_grid_$m.png") {
                LookTheme(lookFor("quiet", "blue", m)) {
                    CompositionLocalProvider(LocalWishDeco provides WishDeco("X", canReact = true)) {
                        Row(Modifier.fillMaxWidth().lookPage().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ws.take(2).forEach { w ->
                                Column(Modifier.weight(1f)) { CompactCard(w, readOnly = true, reserve = Reserving(null) {}) {} }
                            }
                        }
                    }
                }
            }
        }
    }
}
