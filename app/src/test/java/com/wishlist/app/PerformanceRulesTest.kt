package com.wishlist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Speed rules for the styles (owner, 13 November 2026: "scrolling lags and doesn't go smoothly" in Doodle, 2.201).
 * The cause: Modifier.border paints every card with an irregular shape (torn, wobbly, ticket) into a picture of its own,
 * and outlines were made again on every frame. These checks keep that from coming back.
 */
class PerformanceRulesTest {
    private fun src(name: String) = File("src/main/java/com/wishlist/app/$name").readText()
    private val styleFiles = listOf("Design.kt", "Styled.kt", "Themed.kt", "Doodle.kt", "Placeholders.kt")

    @Test fun stylesDrawLinesWithEdgeLineNotBorder() {
        for (f in styleFiles) assertTrue("$f uses Modifier.border; use edgeLine (made once per size)", !Regex("""\.border\(""").containsMatchIn(src(f)))
    }

    @Test fun shadowsAndCardDrawingsAreMadeOncePerSize() {
        val d = src("Design.kt")
        val shadow = d.substringAfter("fun Modifier.hardShadow(").substringBefore("\n}")
        assertTrue("hardShadow must use drawWithCache", shadow.contains("drawWithCache"))
        val edge = d.substringAfter("fun Modifier.edgeLine(").substringBefore("\n}")
        assertTrue("edgeLine must use drawWithCache", edge.contains("drawWithCache"))
        val doodle = src("Doodle.kt")
        for (fn in listOf("fun Modifier.doodleLine(", "fun Modifier.doodlePin(", "fun Modifier.doodleBackdrop(")) {
            assertTrue("$fn must use drawWithCache", doodle.substringAfter(fn).substringBefore("\n}").contains("drawWithCache"))
        }
        // No outline is made inside a plain drawBehind in the card surfaces (that runs on every frame).
        val surface = d.substringAfter("fun Modifier.lookSurface(").substringBefore("\n}\n")
        assertTrue("lookSurface makes an outline on every frame", !Regex("""drawBehind \{[^}]*createOutline""").containsMatchIn(surface))
    }
}
