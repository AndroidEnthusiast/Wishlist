package com.wishlist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's own icons (2.210, owner 15 November 2026: Google's filled icons made it feel like "a standard Android
 * visual experience"; docs/styles/identity.md).
 */
class OwnIconsTest {
    @Test fun everyIconIsDrawn() {
        for (set in listOf(QuietIcons, ChaosIcons)) for (i in set.all) {
            assertTrue("${i.name} has no drawing", i.root.size > 0)
            assertTrue("${i.name} is on the 24 grid", i.viewportWidth == 24f && i.viewportHeight == 24f)
        }
    }

    /** No screen uses Google's Material icon set directly any more: every icon goes through AppIcons (OwnIcons). */
    @Test fun noGoogleIconsInTheApp() {
        val src = File("src/main/java/com/wishlist/app").listFiles { f -> f.extension == "kt" }!!
        val bad = src.filter { f -> f.readText().contains("androidx.compose.material.icons") }.map { it.name }
        assertTrue("Google's icon set is used in: $bad", bad.isEmpty())
    }
}
