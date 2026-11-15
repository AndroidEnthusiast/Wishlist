package com.wishlist.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import kotlin.math.hypot

/** The app's own character (2.210, owner 15 November 2026, docs/styles/identity.md). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class IdentityTest {
    /** A smoothed corner joins its circle exactly: the arc's points lie on the corner's circle, the curve is continuous. */
    @Test fun squircleCornerIsSmoothAndExact() {
        val k = cornerCurve(radius = 40f, smoothing = 0.6f, limit = 100f)
        assertEquals(40f, k.r)
        assertEquals(64f, k.p, 0.01f) // (1 + 0.6) × 40: the corner starts further along the edge than a plain one
        // Start and end of the arc are on the circle around (r, r).
        val si = k.p - k.a - k.b - k.c; val so = k.d
        assertEquals(k.r, hypot(si - k.r, so - k.r), 0.01f)
        // A plain corner (smoothing 0) is a quarter circle.
        assertEquals(90f, cornerCurve(40f, 0f, 100f).arc, 0.001f)
        // Too small a side: the smoothing gives way, the corner never passes the middle of the edge.
        assertTrue(cornerCurve(40f, 0.6f, 44f).p <= 44.01f)
        val path = squirclePath(Size(200f, 120f), 30f, 30f, 30f, 30f, 0.6f)
        val b = path.getBounds()
        assertEquals(0f, b.left, 0.5f); assertEquals(0f, b.top, 0.5f)
        assertEquals(200f, b.right, 0.5f); assertEquals(120f, b.bottom, 0.5f)
    }

    /** Berry is the app's own colour: the first accent and readable as text and on buttons, light and dark. */
    @Test fun berryIsFirstAndReadable() {
        assertEquals("berry", ACCENTS[0].key)
        for (m in listOf("light", "dark")) {
            val look = lookFor("quiet", "berry", m)
            fun contrast(a: Color, b: Color): Float {
                val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
                return if (la > lb) la / lb else lb / la
            }
            assertTrue("$m: Berry as text", contrast(look.scheme.primary, look.scheme.background) >= 4.5f)
            assertTrue("$m: text on Berry", contrast(look.onAccent, look.accent) >= 4.5f)
        }
        // Not Android's own colours any more (the robot's green, the default purple's dark tone).
        assertNotEquals(0xFF3DDC84, ACCENTS.first { it.key == "green" }.dark)
        assertNotEquals(0xFF8585FF, ACCENTS.first { it.key == "indigo" }.dark)
    }

    /** Warm neutrals: not the phone-settings grey and not pure black. */
    @Test fun warmSurfaces() {
        assertEquals(QuietColours.pageLight, lookFor("quiet", "blue", "light").scheme.background)
        assertEquals(QuietColours.pageDark, lookFor("quiet", "blue", "dark").scheme.background)
        assertNotEquals(Color.Black, lookFor("quiet", "blue", "dark").scheme.background)
    }

    /** A new phone starts in Berry; a phone that already chose a colour keeps it (also the old default, never saved). */
    @Test fun berryOnlyForNewPhones() {
        assertEquals("berry", startPalette(hasPalette = false, hasLook = false, saved = null))
        assertEquals("green", startPalette(hasPalette = true, hasLook = true, saved = "green"))
        assertEquals("indigo", startPalette(hasPalette = false, hasLook = true, saved = null))
    }

    /** A wish's sheet in groups: doing, switches, information, in the sheet's own order. */
    @Test fun sheetGroupsKeepOrder() {
        assertEquals(
            listOf(listOf("edit", "share", "move"), listOf("private", "have"), listOf("history")),
            sheetGroups(listOf("edit", "share", "move", "private", "have", "history"))
        )
        assertEquals(listOf(listOf("reserve", "edit", "share", "move")), sheetGroups(listOf("reserve", "edit", "share", "move")))
    }
}
