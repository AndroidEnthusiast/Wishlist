package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reservation button (Reserve, "Reserved by you" and someone else's "Reserved", each with a lock), the purple "NEW"
 * ribbon and the red "new wishes" badge on a friend's list (owner, 23 November 2026), in Quiet and Gen Z chaos, light
 * and dark: build/outputs/roborazzi/reserve_*.png. Only with -Pwl.screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class ReserveScreenshotTest {
    private val ws = listOf(
        Wish(1, "https://example.com/1", "Lancôme La Vie Est Belle", "", "", 97.95, null, "EUR", ""),
        Wish(2, "https://example.com/2", "Lancôme La Vie Est Belle Intense", "", "", 101.95, null, "EUR", ""),
        Wish(3, "https://example.com/3", "Wool socks", "", "", 12.5, null, "EUR", "")
    )

    @Test fun cards() {
        StillSpinners.on = true
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) {
            captureRoboImage("build/outputs/roborazzi/reserve_grid_${st}_$m.png") {
                LookTheme(lookFor(st, "blue", m)) {
                    CompositionLocalProvider(LocalWishDeco provides WishDeco("X", canReact = true, onBadge = {})) {
                        Column(Modifier.fillMaxWidth().lookPage().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) {
                                    CompactCard(ws[0], readOnly = true, reserve = Reserving(null) {}) {}
                                    NewRibbon(LocalLook.current.cardShape(ws[0].id, true))
                                }
                                Box(Modifier.weight(1f)) { CompactCard(ws[1], readOnly = true, reserve = Reserving(true) {}) {} }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) { CompactCard(ws[2], readOnly = true, reserve = Reserving(false) {}) {} }
                                Box(Modifier.weight(1f)) {
                                    ListBlock(HubItem("f:A", "Birthday", 7, null, false, "", emptyList(), fresh = 3) {}, emptyList(), {}, Modifier.fillMaxWidth(), small = false)
                                }
                            }
                        }
                    }
                }
            }
            captureRoboImage("build/outputs/roborazzi/reserve_rows_${st}_$m.png") {
                LookTheme(lookFor(st, "blue", m)) {
                    CompositionLocalProvider(LocalWishDeco provides WishDeco("X", canReact = true, onBadge = {})) {
                        Column(Modifier.fillMaxWidth().lookPage().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf<Boolean?>(null, true, false).forEachIndexed { i, r ->
                                val w = ws[i]
                                val state = reserveState(w, null, r)!!
                                Box {
                                    RowCard(w, dim = state == "taken") {
                                        CardButtons(spread = true, reserve = { ReserveButton(w, state, reserveButtonText(w, null, state), {}, onTaken = {}) }, icons = {}) {}
                                    }
                                    if (i == 0) NewRibbon(LocalLook.current.cardShape(w.id, false))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
