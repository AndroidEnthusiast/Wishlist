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
        assertEquals(listOf("Berry", "Ocean", "Forest", "Teal", "Lilac"), ACCENTS.map { it.name })
        assertEquals("berry", ACCENTS.first().key)
    }

    @Test fun everyThemeReadsWellInLightAndDark() {
        for (ac in ACCENTS) for (m in listOf("light", "dark", "black")) {
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

    /** Owner, 18 November 2026: the backdrop is grey (only a hint of the theme), the colour is on the elements. */
    @Test fun backdropIsGreyAndColourIsOnTheElements() {
        fun chroma(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
        for (ac in ACCENTS) for (m in listOf("light", "dark", "black")) {
            val l = lookFor("quiet", ac.key, m)
            for ((where, bg) in listOf("page" to l.page, "card" to l.card, "panel" to l.raised))
                assertTrue("${ac.name} $m: $where is grey, not coloured (${chroma(bg)})", chroma(bg) <= 0.025f)
            assertTrue("${ac.name} $m: the button is in colour", chroma(l.accent) >= 0.3f)
        }
        assertEquals("Black is a pure black page", Color.Black, lookFor("quiet", "berry", "black").page)
        assertEquals(Color.Black, lookFor("chaos", "berry", "black").page)
        assertTrue("Dark is dark grey, not black", lookFor("quiet", "berry", "dark").page.luminance() > 0.004f)
        assertTrue("Light is a soft grey page, not white", lookFor("quiet", "berry", "light").page.luminance() < 0.93f)
    }

    @Test fun oldColoursMoveToTheClosestTheme() {
        assertEquals("berry", closestTheme("pink"))
        assertEquals("blue", closestTheme("#1E88E5"))
        assertEquals("green", closestTheme("#2E7D32"))
        assertEquals("berry", closestTheme("#FB8C00"))
        assertEquals("an Amber choice becomes Teal", "teal", closestTheme("orange"))
        assertEquals("indigo", closestTheme("#8E24AA"))
        assertEquals("green", closestTheme("green"))
        assertEquals("berry", closestTheme("nonsense"))
    }
}
