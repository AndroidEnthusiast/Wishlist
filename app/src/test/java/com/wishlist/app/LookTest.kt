package com.wishlist.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/**
 * The design styles: Quiet and Gen Z chaos (owner, 14 November 2026: every other style was removed; a new one is
 * made to the standard in docs/DESIGN-STYLES.md), each in light and dark and readable, and nobody stuck on a style
 * that was removed.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class LookTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    private val modes = listOf("light", "dark")

    /** Every removed style: Cyberpunk, Neobrutalism, Neumorphism (13 November 2026) and the twelve of 2.199–2.205. */
    private val removed = listOf(
        "neo", "neu", "cyber", "y2k", "retro", "pop", "grunge", "flat", "bauhaus", "doodle", "cottage", "japandi", "riso",
        "synth", "anthro", "swiss", "punk"
    )

    private fun every(check: (String, String, Look) -> Unit) {
        for (st in STYLES) for (m in modes) for (ac in ACCENTS.map { it.key }) check(st.key, "$m/$ac", lookFor(st.key, ac, m))
    }

    @Test fun theStylesAreTheOwnersChoice() {
        assertEquals(listOf("quiet", "chaos"), STYLES.map { it.key })
        for (old in removed) assertEquals(old, "quiet", styleFor(old).key)
        // Both take the accent you pick.
        assertNotEquals(lookFor("quiet", "pink", "light").accent, lookFor("quiet", "green", "light").accent)
    }

    @Test fun everyStyleHasItsOwnLightAndDark() {
        for (st in STYLES) {
            val l = lookFor(st.key, "blue", "light"); val d = lookFor(st.key, "blue", "dark")
            assertEquals(st.key, l.design); assertFalse(l.dark); assertTrue(d.dark)
            assertTrue("${st.key} light page is light", l.scheme.background.luminance() > 0.5f)
            assertTrue("${st.key} dark page is dark", d.scheme.background.luminance() < 0.1f)
            assertNotEquals(l.scheme.surfaceVariant, d.scheme.surfaceVariant)
            assertEquals(st.key, l.blobs.size, d.blobs.size)
            assertEquals(st.key, l.tints.size, d.tints.size)
        }
    }

    /** Text is at least 4.5 : 1 against what it sits on (docs/STYLE.md), in every style, mode and accent. */
    @Test fun textIsReadableEverywhere() {
        val bad = mutableListOf<String>()
        every { st, m, look ->
            val cs = look.scheme
            fun need(what: String, fg: Color, bg: Color, min: Double = 4.5) {
                val c = contrast(fg, bg)
                if (c < min) bad += "$st $m $what: ${"%.2f".format(c)}"
            }
            need("text on page", cs.onBackground, cs.background)
            need("text on card", cs.onSurface, cs.surfaceVariant)
            need("grey text on page", cs.onSurfaceVariant, cs.background)
            need("grey text on card", cs.onSurfaceVariant, cs.surfaceVariant)
            need("message bar", cs.inverseOnSurface, cs.inverseSurface)
            need("error on card", cs.error, cs.surfaceVariant)
            for (f in look.fills) {
                need("text on a card colour", look.cardInk(0), f.compositeOver(cs.background))
                need("grey text on a card colour", look.cardMuted(0), f.compositeOver(cs.background))
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** Lines that frame fields, tick boxes and chips stand out from the card (3 : 1 for parts you use). */
    @Test fun linesOfControlsAreVisible() {
        val bad = mutableListOf<String>()
        every { st, m, look ->
            val edge = if (st == "quiet") look.scheme.outline else look.line
            val c = contrast(edge, look.scheme.surfaceVariant)
            if (c < 3.0) bad += "$st $m: ${"%.2f".format(c)}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** A new start of the app (Repo loads once per start). */
    private fun fresh() {
        ListStore.reset(); WishlistDb.reset()
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
    }

    /** Someone who had a removed style gets Quiet, keeps light or dark and their accent; chaos stays chaos. */
    @Test fun aRemovedStyleBecomesQuiet() {
        val c = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        for (old in removed) {
            prefs.edit().clear().putBoolean("look_v2", true).putString("style", old).putString("theme", "dark")
                .putString("palette", "green").putString("colour_sets", "{\"$old\":\"x\"}").commit()
            fresh(); Repo.load(c)
            assertEquals(old, "quiet", Repo.design)
            assertEquals("dark", Repo.theme)
            assertEquals("green", Repo.palette)
            assertEquals("quiet", prefs.getString("style", null))
        }
        Repo.setLook(c, "chaos", "light", "pink")
        fresh(); Repo.load(c)
        assertEquals("chaos", Repo.design); assertEquals("light", Repo.theme); assertEquals("pink", Repo.palette)
    }
}
