package com.wishlist.app

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The levels page and the Iconic prize (owner, 27 November 2026): the page's numbers; Iconic unlocks once (the pop-up
 * once), its prizes stay when the total drops; the three switches start on and go online; rings only for the ids the
 * server gives; an Iconic member's reactions glow; the prizes stay a secret on the page until reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconicTest {
    private val c = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
    private fun w(p: Double) = Wish(System.nanoTime(), "", "x", "", "", p, null, "EUR", "")

    @Before fun fresh() {
        c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().clear().putBoolean("pf_made", true).commit()
        Iconic.reset()
    }

    @Test fun thePagesNumbers() {
        val s = LevelsState.of(listOf(w(2980.0)))
        assertEquals(ValueLevel.MAIN, s.level)
        assertEquals(ValueLevel.ENERGY, s.next)
        assertEquals(2020.0, s.toGo!!, 0.001)
        assertEquals((2980.0 - 2500.0) / 2500.0, s.progress.toDouble(), 0.001)
        val top = LevelsState.of(listOf(w(12000.0)))
        assertNull("nothing after Iconic", top.next); assertNull(top.toGo); assertEquals(1f, top.progress)
        assertEquals("Your journey starts here", levelHeadline(null))
        val home = java.io.File("src/main/java/com/wishlist/app/HomeHero.kt").readText()
        assertTrue("Total value always opens the page (owner), unless levels are off", home.contains("if (Show[ValueLevels.SHOW]) ({ go(Deep.Levels) }) else null"))
        val none = LevelsState.of(emptyList())
        assertNull(none.level); assertEquals(ValueLevel.RISING, none.next)
        assertEquals("All eyes on your wishlist.", levelLine(ValueLevel.MAIN))
        assertEquals("You’re the main character", levelHeadline(ValueLevel.MAIN))
    }

    @Test fun iconicUnlocksOnceAndStays() {
        Iconic.seen(c, ValueLevel.ENERGY)
        assertFalse(Iconic.reached)
        Iconic.seen(c, ValueLevel.ICONIC)
        assertTrue(Iconic.reached); assertTrue("the pop-up", Iconic.celebrate)
        Iconic.celebrated()
        Iconic.seen(c, ValueLevel.ICONIC)
        assertFalse("only once", Iconic.celebrate)
        Iconic.seen(c, ValueLevel.RISING)
        assertTrue("the prize stays when the total drops", Iconic.reached && Iconic.myRing && Iconic.myCard && Iconic.myGlow)
        assertTrue("kept on the phone", c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).getBoolean("ic_reached", false))
    }

    @Test fun theSwitchesGoOnline() = kotlinx.coroutines.runBlocking {
        Iconic.seen(c, ValueLevel.ICONIC)
        val sent = mutableListOf<JSONObject>()
        Cloud.pretendServer = { fn, b -> if (fn == "wl_pf_iconic") { sent += b; "true" } else "[\"FRIEND1\"]" }
        try {
            Iconic.set(c, Iconic.RING, false)
            Iconic.sync(c)
            assertEquals(1, sent.size)
            assertFalse(sent[0].getBoolean("p_ring")); assertTrue(sent[0].getBoolean("p_glow")); assertTrue(sent[0].getBoolean("p_list"))
            Iconic.sync(c)
            assertEquals("only when something changed", 1, sent.size)
            Iconic.loadRings(c)
            assertTrue(Iconic.ringFor("FRIEND1")); assertFalse(Iconic.ringFor("STRANGER")); assertFalse("mine is off", Iconic.ringFor(null))
            Iconic.set(c, Iconic.RING, true)
        } finally { Cloud.pretendServer = null }
    }

    @Test fun anIconicMembersReactionGlows() = kotlinx.coroutines.runBlocking {
        Cloud.pretendServer = { _, _ -> "[{\"i\":1,\"e\":\"😍\",\"n\":\"Ben\",\"m\":false,\"at\":\"2026-11-27T19:00:00Z\",\"g\":true},{\"i\":1,\"e\":\"🔥\",\"n\":\"Sam\",\"m\":false,\"at\":\"2026-11-27T19:00:00Z\"}]" }
        try {
            Reactions.load(c, "GLOWLIST")
            assertEquals(listOf(true, false), Reactions.of("GLOWLIST", 1).map { it.glow })
        } finally { Cloud.pretendServer = null; Reactions.map.remove("GLOWLIST") }
    }

    @Test fun thePrizesStayASecretOnThePage() {
        val page = File("src/main/java/com/wishlist/app/LevelsScreen.kt").readText()
        assertTrue(page.contains("tr(\"Something special unlocks here\")"))
        assertFalse("no prize names before Iconic", page.contains("Holo ring"))
        // Owner, 27 November 2026 ("mine looks flat"): no "Only you can see this", "Your level" instead of "You're here",
        // a tick on passed levels instead of "Reached", locked levels all grey (Iconic too), the golden padlock.
        assertFalse(page.contains("Only you can see this")); assertFalse(page.contains("tr(\"Reached\")) {"))
        assertTrue("the twinkle before the first level", page.contains("?: StarterDisc(64.dp)"))
        assertTrue("one screen: the small top bar (owner, 28 November 2026)", page.contains("large = false"))
        assertFalse("the amount sits on the name's line, no \"From …\" line (owner, 28 November 2026)", page.contains("tr(\"From %s\""))
        assertTrue(page.contains("LevelChip(tr(\"Your level\")") && page.contains("passed = reached") && page.contains("Padlock(0f"))
        val settings = File("src/main/java/com/wishlist/app/Settings.kt").readText()
        assertTrue("App settings › Iconic only once reached", settings.contains("if (Iconic.reached) ValueRow"))
    }
}
