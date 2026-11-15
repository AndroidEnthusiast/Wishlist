package com.wishlist.app

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text inside Quiet's own dialog takes the page's ink (2.211, owner's screenshot of 15 November 2026: "Household" was
 * black on the dark dialog in Move to…). Checked in the real app, dark mode: Move to… with a second list, as in the screenshot.
 */
class DialogInkTest : NavigationBase() {
    @Test fun dialogTextIsLightInDark() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.runOnUiThread {
            Repo.setLook(app, "quiet", "dark", "berry")
            Repo.addMine(app, Wish(1, "https://example.com/a", "Ceramic teapot", "", "", 20.0, null, "EUR", "Home"))
            Repo.createMine(app, "Household", "")
        }
        send(open(MainActivity.OPEN_NOTE, "m:" + Repo.mineIds.first()))
        compose.waitForIdle()
        compose.onAllNodes(hasText("Ceramic teapot", substring = true)).onFirst().performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodes(hasText("Move to…") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        val node = compose.onAllNodes(hasText("Household")).onFirst().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val ink = layouts.single().layoutInput.style.color
        assertTrue("dialog text is $ink, not light on the dark dialog", ink.luminance() > 0.4f)
    }
}
