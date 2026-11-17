package com.wishlist.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five themes and Gen Z chaos (owner, 17 November 2026, plan step 5): every theme tints the page, cards and panels,
 * and all text stays readable (4.5 : 1) on each of them in light and dark. Old colours move to the closest theme.
 */
class ThemesTest {
    private fun ratio(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
        return if (la > lb) la / lb else lb / la
    }

    @Test fun fiveThemesWithTheirNames() {
        assertEquals(listOf("Berry", "Ocean", "Forest", "Amber", "Lilac"), ACCENTS.map { it.name })
        assertEquals("berry", ACCENTS.first().key)
    }

    @Test fun everyThemeReadsWellInLightAndDark() {
        for (ac in ACCENTS) for (m in listOf("light", "dark")) {
            val l = lookFor("quiet", ac.key, m)
            val s = l.scheme
            for ((where, bg) in listOf("page" to l.page, "card" to l.card, "panel" to l.raised)) {
                assertTrue("${ac.name} $m: text on $where", ratio(s.onSurface, bg) >= 4.5f)
                assertTrue("${ac.name} $m: grey text on $where", ratio(s.onSurfaceVariant, bg) >= 4.5f)
            }
            assertTrue("${ac.name} $m: coloured text on the page", ratio(s.primary, l.page) >= 4.5f)
            assertTrue("${ac.name} $m: button text", ratio(l.onAccent, l.accent) >= 4.5f)
        }
    }

    @Test fun themesReallyTintThePage() {
        val pages = ACCENTS.map { lookFor("quiet", it.key, "dark").page }.toSet()
        assertEquals("each theme its own page colour", ACCENTS.size, pages.size)
    }

    @Test fun oldColoursMoveToTheClosestTheme() {
        assertEquals("berry", closestTheme("pink"))
        assertEquals("blue", closestTheme("#1E88E5"))
        assertEquals("green", closestTheme("#2E7D32"))
        assertEquals("orange", closestTheme("#FB8C00"))
        assertEquals("indigo", closestTheme("#8E24AA"))
        assertEquals("green", closestTheme("green"))
        assertEquals("berry", closestTheme("nonsense"))
    }
}
