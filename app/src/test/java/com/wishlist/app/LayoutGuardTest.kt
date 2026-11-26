package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout inspector itself (LayoutGuard.kt, owner 20 November 2026): it must catch each kind of mistake the owner
 * found (a cut-off text, a price broken over two lines, labels on top of each other, text off the screen) and must
 * not complain about a layout that is right.
 */
class LayoutGuardTest : NavigationBase() {
    private fun found(content: @Composable () -> Unit): List<String> {
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor("quiet", "blue", "light")) { content() } } }
        compose.waitForIdle()
        return LayoutGuard.problems(compose)
    }

    @Test fun aRightLayoutIsClean() {
        val p = found {
            Column {
                Text("€33.99", fontSize = 20.sp)
                Text("A long name that wraps over several lines of the card quite nicely", Modifier.width(160.dp))
                Text("Barbour MAIA MINI DRESS – Spijkerjurk – rinse wash", Modifier.width(120.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        assertTrue(p.toString(), p.isEmpty())
    }

    @Test fun catchesACutOffText() {
        val p = found { Text("Reserved by you", Modifier.width(40.dp), maxLines = 1, softWrap = false) }
        assertTrue(p.toString(), p.any { it.contains("is cut off") })
    }

    @Test fun catchesAPriceBrokenOverTwoLines() {
        // The owner's screenshot: "€33.1" / "9".
        val p = found { Text("€33.19", Modifier.width(52.dp), fontSize = 20.sp) }
        assertTrue(p.toString(), p.any { it.contains("breaks") })
    }

    @Test fun catchesAWordBrokenInHalf() {
        val p = found { Text("Decathlon", Modifier.width(48.dp), fontSize = 16.sp) }
        assertTrue(p.toString(), p.any { it.contains("breaks") })
    }

    @Test fun catchesLabelsOnTopOfEachOther() {
        val p = found {
            Box {
                Text("+78%")
                Text("Reserved by you", Modifier.offset(x = 10.dp))
            }
        }
        assertTrue(p.toString(), p.any { it.contains("overlaps") })
    }

    @Test fun catchesTextOffTheScreen() {
        val p = found { Row { Text("Sticks out", Modifier.offset(x = 400.dp)) } }
        assertTrue(p.toString(), p.any { it.contains("sticks out") })
    }

    // "Share" and "Add a friend" touched (owner, 26 November 2026).
    @Test fun catchesButtonsThatTouch() {
        val p = found { Row { TextButton(onClick = {}) { Text("Share") }; TextButton(onClick = {}) { Text("Add a friend") } } }
        assertTrue(p.toString(), p.any { it.contains("touch") })
        val ok = found {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {}) { Text("Share") }; TextButton(onClick = {}) { Text("Add a friend") }
            }
        }
        assertTrue(ok.toString(), ok.none { it.contains("touch") })
    }
}
