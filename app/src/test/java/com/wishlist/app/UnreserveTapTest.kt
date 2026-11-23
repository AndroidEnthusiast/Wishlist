package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Reserved by you" → one tap: "Unreserve?" with an open padlock; a tap anywhere else puts it back; a second tap on it
 * unreserves (owner, 23 November 2026: no pop-up, and it waits until you tap elsewhere).
 */
class UnreserveTapTest : NavigationBase() {
    @Test fun twoTapsUnreserveATapElsewhereKeepsIt() {
        val calls = ArrayList<Boolean>()
        val w = Wish(7, "", "Perfume", "", "", 97.95, null, "EUR", "")
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(lookFor("quiet", "blue", "dark")) {
                Box(Modifier.fillMaxSize().dismissReserveAsk()) {
                    Column {
                        Text("Somewhere else", Modifier.height(80.dp))
                        ReserveButton(w, "mine", "Reserved by you", { calls.add(it) })
                    }
                }
            }
        } }
        compose.waitForIdle()
        compose.onAllNodes(hasText("Reserved by you")).onFirst().performClick(); compose.waitForIdle()
        compose.onAllNodes(hasText("Unreserve?")).onFirst().assertExists()
        assertEquals(emptyList<Boolean>(), calls)
        compose.onAllNodes(hasText("Somewhere else")).onFirst().performClick(); compose.waitForIdle()
        compose.onAllNodes(hasText("Reserved by you")).onFirst().assertExists()
        assertEquals(emptyList<Boolean>(), calls)
        compose.onAllNodes(hasText("Reserved by you")).onFirst().performClick(); compose.waitForIdle()
        compose.onAllNodes(hasText("Unreserve?")).onFirst().performClick(); compose.waitForIdle()
        assertEquals(listOf(false), calls)
    }
}
