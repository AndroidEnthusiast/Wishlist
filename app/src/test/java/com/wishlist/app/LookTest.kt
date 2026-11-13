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
 * The styles (owner, 13 November 2026): Quiet, Gen Z chaos and six new ones, each in light and dark, every one
 * readable ("without compromising accessibility and readability"), and nobody stuck on a style that was removed.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class LookTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    private val modes = listOf("light", "dark")

    private fun every(check: (String, String, Look) -> Unit) {
        for (st in STYLES) for (m in modes) for (ac in if (usesAccent(st.key)) ACCENTS.map { it.key } else listOf("blue")) {
            check(st.key, "$m/$ac", lookFor(st.key, ac, m))
        }
    }

    @Test fun theStylesAreTheOwnersChoice() {
        assertEquals(
            listOf("quiet", "chaos", "y2k", "retro", "pop", "grunge", "flat", "bauhaus", "doodle", "cottage", "japandi", "riso", "synth", "anthro"),
            STYLES.map { it.key }
        )
        assertEquals("quiet", STYLES.first().key)
        // Cyberpunk, Neobrutalism and Neumorphism are gone: their keys mean Quiet now.
        for (old in listOf("neo", "neu", "cyber")) assertEquals("quiet", styleFor(old).key)
        // Only Quiet and Gen Z chaos take the accent you pick; the others have their own colours.
        assertEquals(setOf("quiet", "chaos"), STYLES.filter { usesAccent(it.key) }.map { it.key }.toSet())
        assertEquals(lookFor("bauhaus", "pink", "light").accent, lookFor("bauhaus", "green", "light").accent)
    }

    @Test fun everyStyleHasItsOwnLightAndDark() {
        for (st in STYLES) {
            val l = lookFor(st.key, "blue", "light"); val d = lookFor(st.key, "blue", "dark")
            assertEquals(st.key, l.design); assertFalse(l.dark); assertTrue(d.dark)
            assertTrue("${st.key} light page is light", l.scheme.background.luminance() > 0.5f)
            assertTrue("${st.key} dark page is dark", d.scheme.background.luminance() < 0.1f)
            assertNotEquals(l.scheme.surfaceVariant, d.scheme.surfaceVariant)
            // The same set of drawings in both modes, so neither looks emptier.
            assertEquals(st.key, l.blobs.size, d.blobs.size)
            assertEquals(st.key, l.tints.size, d.tints.size)
            assertEquals(st.key, l.art.size, d.art.size)
            assertEquals(st.key, l.accentBrush == null, d.accentBrush == null)
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
            if (!usesAccent(st)) {
                // Quiet's and chaos's accent is whatever you pick (a warning shows in the Themer when it is hard to
                // read; chaos kept its 2.198 look, owner).
                need("coloured text on page", look.textAccent, cs.background)
                need("coloured text on card", look.textAccent, cs.surfaceVariant)
                need("text on primary", cs.onPrimary, cs.primary)
            }
            if (!usesAccent(st)) {
                need("button text", look.onAccent, look.accent)
                need("chip text", look.chipContent(true), look.accent)
                need("text in a filled container", cs.onPrimaryContainer, cs.primaryContainer)
                need("price drop on card", look.drop, cs.surfaceVariant, 3.0)
                need("price rise on card", look.rise, cs.surfaceVariant, 3.0)
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** Gradients on buttons (Y2K, Pop futurism, Synthwave): the button text is readable on every colour of the gradient. */
    @Test fun buttonTextIsReadableOnGradients() {
        val stops = mapOf(
            "y2k" to listOf(Color(0xFFFFB8E6), Color(0xFFD3C4FF), Color(0xFFA8F0FF)),
            "pop" to listOf(Color(0xFFFFC23D), Color(0xFFFF8A3D), Color(0xFFFF6A5B)),
            "synth" to listOf(Color(0xFFFFD23F), Color(0xFFFF8A3D), Color(0xFFFF4FA8))
        )
        for ((st, cols) in stops) for (m in modes) {
            val look = lookFor(st, "blue", m)
            assertTrue(look.accentBrush != null)
            for (c in cols) assertTrue("$st $m on $c", contrast(look.onAccent, c) >= 4.5)
        }
    }

    /**
     * Nothing drawn behind the screens makes text hard to read (owner: "never behind text"): text and grey text stay at
     * least 4.5 : 1 over every colour of the drawing. (Gen Z chaos keeps its circles as they were in 2.198, owner.)
     */
    @Test fun drawingsBehindTheTextKeepItReadable() {
        val bad = mutableListOf<String>()
        every { st, m, look ->
            if (st == "chaos") return@every
            val bg = look.scheme.background
            for (b in look.blobs) {
                val under = b.compositeOver(bg)
                for ((what, fg) in listOf("text" to look.scheme.onBackground, "grey text" to look.scheme.onSurfaceVariant)) {
                    val c = contrast(fg, under)
                    if (c < 4.5) bad += "$st $m $what over $b: ${"%.2f".format(c)}"
                }
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** Lines that frame fields, tick boxes and chips stand out from the page (3 : 1 for parts you use). */
    @Test fun linesOfControlsAreVisible() {
        val bad = mutableListOf<String>()
        every { st, m, look ->
            val edge = if (st == "quiet" || st == "flat") look.scheme.outline else look.line
            val c = contrast(edge, look.scheme.surfaceVariant)
            if (c < 3.0) bad += "$st $m: ${"%.2f".format(c)}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /**
     * Risograph's misaligned copy and Synthwave's glow are on big titles only (owner, 13 November 2026): the wordmark
     * and headings have it, prices, numbers (titleLarge) and everything you read don't.
     */
    @Test fun titleEffectsStayOnTitles() {
        for (st in listOf("riso", "synth")) {
            val t = typographyFor(st)
            assertTrue(st, t.headlineSmall.shadow != null)
            assertTrue(st, lookFor(st, "blue", "light").titleShadow != null)
            for (plain in listOf(t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall))
                assertEquals(st, null, plain.shadow)
        }
        for (st in STYLES.map { it.key } - setOf("riso", "synth")) {
            assertEquals(st, null, typographyFor(st).headlineSmall.shadow)
            assertEquals(st, null, lookFor(st, "blue", "light").titleShadow)
        }
    }

    /** A new start of the app (Repo loads once per start). */
    private fun fresh() {
        ListStore.reset(); WishlistDb.reset()
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
    }

    /** Someone who had Cyberpunk, Neobrutalism or Neumorphism gets Quiet, keeps light or dark and their accent. */
    @Test fun aRemovedStyleBecomesQuiet() {
        val c = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        for (old in listOf("neo", "neu", "cyber")) {
            prefs.edit().clear().putBoolean("look_v2", true).putString("style", old).putString("theme", "dark")
                .putString("palette", "green").commit()
            fresh(); Repo.load(c)
            assertEquals(old, "quiet", Repo.design)
            assertEquals("dark", Repo.theme)
            assertEquals("green", Repo.palette)
            assertEquals("quiet", prefs.getString("style", null))
        }
        // A new style is kept as it is.
        prefs.edit().clear().putBoolean("look_v2", true).putString("style", "bauhaus").putString("theme", "light").commit()
        fresh(); Repo.load(c)
        assertEquals("bauhaus", Repo.design)
    }
}
