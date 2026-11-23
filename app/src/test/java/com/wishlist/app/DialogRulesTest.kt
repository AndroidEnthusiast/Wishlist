package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * One rule for every dialog, in every style (owner, 21 November 2026, after Move was invisible in Quiet and his friend's
 * Gen Z chaos showed words in a row; docs/STYLE.md "Dialogs"): the buttons stacked and full width, the main one filled,
 * a second choice outlined (`secondButton`), and the way out (Cancel, Close, …) plain words at the bottom. And Move or
 * copy asks the action first, with the wish in sight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialogRulesTest {
    private val dir = File("src/main/java/com/wishlist/app")
    private val wayOut = setOf("Cancel", "Close", "Not now", "Wait", "Back", "Keep it", "No, keep it", "Stay", "No", "Keep going", "Keep editing", "Skip")

    /** Every dialog slot's code in the app: (file, slot name, its code). */
    private fun slots(): List<Triple<String, String, String>> = dir.listFiles()!!.filter { it.name.endsWith(".kt") }.flatMap { f ->
        val s = f.readText()
        Regex("""(confirmButton|dismissButton|secondButton)\s*=\s*(\(\{|\{)""").findAll(s).map { m ->
            var i = m.range.last + 1; var depth = 1
            while (depth > 0 && i < s.length) { if (s[i] == '{') depth++ else if (s[i] == '}') depth--; i++ }
            Triple(f.name, m.groupValues[1], s.substring(m.range.last + 1, i - 1))
        }.toList()
    }

    @Test fun neverSeveralButtonsInOneSpot() {
        // Two buttons side by side in one spot made Move invisible (21 November 2026).
        val bad = slots().filter { (_, _, code) -> Regex("""\b(Row|Column)\s*[({]""").containsMatchIn(code) }
        assertTrue("a row of buttons in a dialog spot: " + bad.joinToString { "${it.first} ${it.second}" }, bad.isEmpty())
    }

    @Test fun theBottomSpotIsAlwaysAWayOut() {
        val bad = slots().filter { it.second == "dismissButton" }.flatMap { (f, _, code) ->
            Regex("""tr\("([^"]+)"""").findAll(code).map { it.groupValues[1] }.filter { it !in wayOut }.map { "$f: $it" }.toList()
        }
        assertTrue("an action in the Cancel spot (use secondButton): $bad", bad.isEmpty())
    }

    @Test fun everyStyleUsesTheStackedDialog() {
        val bars = File(dir, "StyledBars.kt").readText()
        val dialog = bars.substring(bars.indexOf("fun AlertDialog("), bars.indexOf("val LocalInTopBar"))
        assertTrue(dialog.contains("QuietDialog(") && !dialog.contains("M3AlertDialog("))
        val buttons = File(dir, "StyledButtons.kt").readText()
        assertTrue(buttons.contains("when (LocalDialogSlot.current) {") && buttons.contains("\"second\" -> { OutlinedButton("))
    }

    @Test fun moveOrCopyAsksTheActionFirst() {
        MoveFlow.choosing = null; MoveFlow.picking = null
        MoveFlow.ask(setOf(7L))
        assertEquals(setOf(7L), MoveFlow.choosing)
        assertEquals(null, MoveFlow.picking)
        MoveFlow.choose(copy = true)
        assertEquals(null, MoveFlow.choosing)
        assertEquals(setOf(7L), MoveFlow.picking)
        assertTrue(MoveFlow.copying)
        // ← or Back on "Move to…" goes back to Move or Copy (owner, 22 November 2026: one sheet, two steps).
        MoveFlow.back()
        assertEquals(setOf(7L), MoveFlow.choosing)
        assertEquals(null, MoveFlow.picking)
        MoveFlow.choosing = null
        // The wish is shown in both steps, which are one bottom sheet (MoveChoose.kt) since 22 November 2026.
        val choose = File(dir, "MoveChoose.kt").readText()
        assertEquals(2, Regex("top = \\{ MovingWishes\\(ids\\) \\}").findAll(choose).count())
        assertTrue(choose.contains("if (copy) tr(\"Copy to…\") else tr(\"Move to…\")"))
    }
}
