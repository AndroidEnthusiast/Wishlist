package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Buttons inside a dialog's text keep their own label colour (owner's screenshot, 17 November 2026: in the Share dialog
 * "Send invite" and "Copy" were grey on blue, because the dialog's grey for its text was put in the text style). The
 * dialog's own text stays grey. Every style, light and dark.
 */
class DialogButtonInkTest : NavigationBase() {

    private fun ink(label: String): Color {
        val node = compose.onAllNodes(hasText(label)).onFirst().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test fun buttonsInADialogKeepTheirInk() {
        var look by androidx.compose.runtime.mutableStateOf(lookFor("quiet", "blue", "dark"))
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(look) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Share “Twst”") },
                    text = {
                        Column {
                            Text("They can edit and reserve wishes.")
                            Button({}) { Text("Send invite") }
                            OutlinedButton({}) { Text("Copy") }
                            TextButton({}) { Text("Plain") }
                        }
                    },
                    confirmButton = { TextButton({}) { Text("Done") } }
                )
            }
        } }
        for (st in STYLES.map { it.key }) for (m in listOf("light", "dark")) {
            compose.runOnIdle { look = lookFor(st, "blue", m) }
            compose.waitForIdle()
            val muted = look.scheme.onSurfaceVariant
            val where = "$st $m"
            assertEquals("$where: Send invite", look.onAccent, ink("Send invite"))
            for (l in listOf("Copy", "Plain", "Done")) assertNotEquals("$where: $l is the dialog's grey", muted, ink(l))
            if (st == "quiet") assertEquals("$where: the dialog's text", muted, ink("They can edit and reserve wishes."))
        }
    }
}
