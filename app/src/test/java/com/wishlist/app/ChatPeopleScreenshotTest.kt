package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test

/**
 * Pictures of "Chat about this wish with…" on a shared wishlist (owner, 21 November 2026), checked by the layout
 * inspector: friends, someone not your friend yet (Add friend), a waiting request and a very long name; asking;
 * offline; nobody yet. Both styles, light and dark. Pictures in app/build/outputs/roborazzi/chatpeople_*.png; only
 * with -Pwl.screens (the class name has "Screenshot").
 */
class ChatPeopleScreenshotTest : NavigationBase() {
    private fun settle() = repeat(8) { compose.mainClock.advanceTimeBy(250); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private val people = listOf(
        ListPerson("B", "Ben", "yes"),
        ListPerson("L", "Magdalena Alexandra van der Berg-Huisman", "yes"),
        ListPerson("C", "Cleo", "no"),
        ListPerson("D", "Dee", "asked")
    )

    private fun shot(name: String, style: String, mode: String, people: List<ListPerson>?, status: String, sent: Set<String> = emptySet()) {
        compose.mainClock.autoAdvance = false
        StillSpinners.on = true
        compose.runOnUiThread { compose.activity.setContent { LookTheme(lookFor(style, "indigo", mode)) {
            // On the sheet's own surface, as on the phone.
            Column(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)) { ChatPeopleList(people, status, sent, {}, {}, {}) }
        } } }
        settle()
        val path = "build/outputs/roborazzi/chatpeople_${name}_${style}_$mode.png"
        compose.guard(path); captureScreenRoboImage(path)
    }

    @Test fun thePicker() {
        for (st in listOf("quiet", "chaos")) for (m in listOf("light", "dark")) {
            shot("people", st, m, people, "ok", sent = setOf("C"))
            shot("people_fresh", st, m, people, "ok")
        }
        shot("asking", "quiet", "light", null, "loading")
        shot("offline", "quiet", "light", null, "offline")
        shot("nobody", "quiet", "dark", emptyList(), "ok")
    }
}
