package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The photo editor's state holder (the rebuild, 19 November 2026) and the edit's saved form. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeroEditorStateTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val moved = HeroEdit(scale = 2f, x = 0.1f, y = -0.1f, text = "Hi", bright = 0.3f, cropL = 0.1f, cropT = 0.2f, cropW = 0.5f, cropH = 0.5f)

    @Test fun turningGoesRoundAndFillsTheFrameAgain() {
        val s = HeroEditorState(ctx, "a", moved)
        s.rotate(90); assertEquals(90, s.edit.rotation)
        assertEquals(1f, s.edit.scale); assertEquals(-1f, s.edit.cropW)
        s.rotate(270); assertEquals(0, s.edit.rotation)
        s.rotate(270); assertEquals(270, s.edit.rotation)
        // The text and the light stay.
        assertEquals("Hi", s.edit.text); assertEquals(0.3f, s.edit.bright)
    }

    @Test fun aNewPhotoStartsAfreshButKeepsTheText() {
        val s = HeroEditorState(ctx, "a", moved.copy(rotation = 90))
        s.usePhoto("b")
        assertEquals("b", s.img); assertEquals(listOf("b"), s.picked)
        assertEquals(0, s.edit.rotation); assertEquals(0f, s.edit.bright); assertEquals(-1f, s.edit.cropL)
        assertEquals("Hi", s.edit.text)
    }

    @Test fun anEditIsKeptAsWritten() {
        val e = moved.copy(rotation = 270, color = 0xFFFFE14D, box = true, textSize = 0.2f)
        assertEquals(e, HeroEdit.fromJson(e.toJson()))
        assertEquals(HeroEdit(), HeroEdit.fromJson("not json"))
    }
}
