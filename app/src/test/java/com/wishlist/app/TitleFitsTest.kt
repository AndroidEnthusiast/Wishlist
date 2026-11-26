package com.wishlist.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A list's name stays whole in the small top bar after scrolling (owner, 26 November 2026: "Clothing Magdal…" next to
 * search, settings and share on a narrow phone): the title gets smaller to fit and is only cut off when it's absurdly
 * long. Pictures in app/build/outputs/roborazzi/title_*.png with -Pwl.screens.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class TitleFitsTest : NavigationBase() {
    private fun settle() = repeat(6) { compose.mainClock.advanceTimeBy(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private fun bar(name: String) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.setContent {
                LookTheme(lookFor("quiet", "blue", "dark")) {
                    TopAppBar(
                        title = { ListTitle(name, 0) }, modifier = Modifier.width(360.dp), large = false,
                        navigationIcon = { IconButton({}) { androidx.compose.material3.Icon(AppIcons.ArrowBack, null) } },
                        actions = {
                            IconButton({}) { androidx.compose.material3.Icon(AppIcons.Search, null) }
                            IconButton({}) { androidx.compose.material3.Icon(AppIcons.Settings, null) }
                            IconButton({}) { androidx.compose.material3.Icon(AppIcons.Share, null) }
                        }
                    )
                }
            }
        }
        settle()
    }

    private fun cut(name: String): Boolean {
        val out = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.single().hasVisualOverflow
    }

    @Test fun aNormalNameIsWhole() {
        bar("Clothing Magdalena")
        assertFalse("“Clothing Magdalena” is cut off in the small bar", cut("Clothing Magdalena"))
        captureScreenRoboImage("build/outputs/roborazzi/title_fits.png")
    }

    @Test fun anAbsurdNameIsCut() {
        val n = "Everything I could ever possibly want for my birthday and Christmas"
        bar(n)
        assertTrue(cut(n))
        captureScreenRoboImage("build/outputs/roborazzi/title_absurd.png")
    }
}
