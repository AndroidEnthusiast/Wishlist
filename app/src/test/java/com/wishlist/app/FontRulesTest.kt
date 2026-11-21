package com.wishlist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * One font for numbers across the whole app (owner, 21 November 2026, after the list summary showed "€470.00" in the
 * title font while Home showed it in the text font): titles may use the heading font, but a number or amount in a
 * big (title, headline or display) style must use [number] or `look.display`. This reads the app's code: a text whose
 * value is a number (money(…), a count's toString(), a total or price) and whose style is a big one, without the
 * number font in the same few lines, fails.
 */
class FontRulesTest {
    private val dir = File("src/main/java/com/wishlist/app")
    private val big = Regex("""typography\.(titleLarge|headline\w+|display\w+)""")
    private val numeric = Regex("""money\(|\w+\.size\.toString\(\)|count\.toString\(\)|\btotal\b|\bprice\b|homeTotal\(""")
    private val numberFont = Regex("""\.number\(look\)|fontFamily = look\.display""")

    /** The places in [code] (file name, lines) where a number is drawn in a big style without the number font. */
    internal fun offenders(name: String, code: List<String>): List<String> = code.indices.mapNotNull { i ->
        if (!big.containsMatchIn(code[i])) return@mapNotNull null
        // The call's own few lines: the value is usually on the line before or the same line, the font after.
        val window = code.subList(maxOf(0, i - 2), minOf(code.size, i + 3)).joinToString("\n")
        if (numeric.containsMatchIn(window) && !numberFont.containsMatchIn(window)) "$name:${i + 1}: ${code[i].trim()}" else null
    }

    @Test fun numbersUseTheNumberFont() {
        val bad = dir.listFiles { f -> f.name.endsWith(".kt") }!!.sortedBy { it.name }.flatMap { offenders(it.name, it.readLines()) }
        assertTrue("A number in a title style without .number(look) (see DesignType.kt):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun theCheckCatchesTheOldListSummary() {
        // The list summary as it was before 2.269 (CoverViews.kt).
        val before = listOf(
            "FitText(total ?: \"–\", MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), look.cardInk(id))"
        )
        assertTrue(offenders("CoverViews.kt", before).size == 1)
        val after = listOf(
            "FitText(total ?: \"–\", MaterialTheme.typography.titleLarge.number(look).copy(fontWeight = FontWeight.Bold), look.cardInk(id))"
        )
        assertTrue(offenders("CoverViews.kt", after).isEmpty())
    }
}
