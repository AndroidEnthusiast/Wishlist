package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

    /** "Delete “Twst” for everyone?" (owner's screenshot, 17 November 2026): Delete was pink on blue. */
    @Test fun aDangerousActionIsWhiteOnRed() {
        var look by androidx.compose.runtime.mutableStateOf(lookFor("quiet", "blue", "dark"))
        compose.runOnUiThread { compose.activity.setContent {
            LookTheme(look) {
                AlertDialog(
                    onDismissRequest = {}, title = { Text("Delete “Twst” for everyone?") }, text = { Text("This can’t be undone.") },
                    confirmButton = { TextButton({}, danger = true) { Text("Delete") } },
                    dismissButton = { TextButton({}) { Text("Cancel") } }
                )
            }
        } }
        for (m in listOf("light", "dark")) {
            compose.runOnIdle { look = lookFor("quiet", "blue", m) }
            compose.waitForIdle()
            assertEquals("$m: Delete", Color.White, ink("Delete"))
            assertEquals("$m: Cancel", look.scheme.onSurface, ink("Cancel"))
        }
        // White on the red fill reads at least 4.5 : 1.
        val l = DangerFill.luminance()
        org.junit.Assert.assertTrue((1.05f / (l + 0.05f)) >= 4.5f)
    }
}

/**
 * No button colours its own label (owner, 17 November 2026: "Delete" was pink on a blue filled button, "be more
 * thorough"): a red action says `danger = true` and the button picks readable colours for where it sits. Every
 * Button / TextButton / OutlinedButton in the app's code is checked; only these three keep a colour on purpose.
 */
class NoColouredButtonLabelsTest {
    private val allowed = setOf(
        "HomeShare.kt:Copied",     // green "Copied" on the page, not in a dialog
        "ChatParts.kt:Cancel",     // white on the photo being sent (always dark)
        "Shops.kt:Add anyway"      // on the dark message bar (inverse colours)
    )

    private fun close(s: String, from: Int, open: Char, shut: Char): Int {
        var d = 1; var i = from
        while (d > 0 && i < s.length) { if (s[i] == open) d++ else if (s[i] == shut) d--; i++ }
        return i
    }

    @Test fun buttonsNeverColourTheirLabel() {
        val found = mutableListOf<String>()
        java.io.File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.endsWith(".kt") }.forEach { f ->
            val s = f.readText()
            Regex("""\b(TextButton|OutlinedButton|FilledTonalButton|Button)\s*\(""").findAll(s).forEach { m ->
                if (m.range.first > 0 && s[m.range.first - 1] == '.') return@forEach
                if (s.substring(maxOf(0, m.range.first - 4), m.range.first) == "fun ") return@forEach
                var j = close(s, m.range.last + 1, '(', ')')
                while (j < s.length && (s[j] == ' ' || s[j] == '\t')) j++
                if (j >= s.length || s[j] != '{') return@forEach
                val body = s.substring(j, close(s, j + 1, '{', '}'))
                Regex("""\bText\(""").findAll(body).forEach { t ->
                    val args = body.substring(t.range.last + 1, close(body, t.range.last + 1, '(', ')'))
                    var depth = 0
                    val top = buildString { args.forEach { c -> if (c in "({[") depth++; if (c in ")}]") depth--; append(if (depth <= 0) c else ' ') } }
                    if (Regex("""\bcolor\s*=""").containsMatchIn(top)) {
                        val label = Regex("""tr\("([^"]*)"""").findAll(args).map { it.groupValues[1] }.toList()
                        if (label.none { "${f.name}:$it" in allowed }) found += "${f.name}: ${args.take(60)}"
                    }
                }
            }
        }
        org.junit.Assert.assertTrue("buttons that colour their own label (use danger = true):\n" + found.joinToString("\n"), found.isEmpty())
    }
}
