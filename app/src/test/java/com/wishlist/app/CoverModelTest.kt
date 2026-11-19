package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A list's cover photo state holder (the rebuild, 19 November 2026): the editor's new photo, cancel, remove. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val key = "s:TESTCOVER"   // a gift list's key: nothing is sent online
    private fun model() = CoverModel(ctx, kotlinx.coroutines.MainScope(), SnackbarHostState(), key, 2.5f)
    // In the folder where the app keeps its pictures (only those are ever deleted).
    private fun file(name: String) = File(File(ctx.filesDir, "images").apply { mkdirs() }, name).apply { writeText("x") }

    @Test fun aChosenPhotoOpensTheEditorAndCancelDeletesIt() {
        val m = model()
        val f = file("cover_a.jpg")
        m.picked(android.net.Uri.fromFile(f).toString())
        assertEquals(android.net.Uri.fromFile(f).toString(), m.pending)
        m.dropPending(m.pending!!)
        assertNull(m.pending)
        assertEquals(false, f.exists())
    }

    @Test fun nothingChosenOpensNothing() {
        val m = model(); m.picked(null)
        assertNull(m.pending)
    }

    @Test fun removeTakesTheCoverOff() {
        val o = org.json.JSONObject().put("img", "").put("edit", HeroEdit().toJson()).put("cut", "")
        ctx.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().putString("cover_$key", o.toString()).commit()
        model().remove()
        assertNull(ListCovers.get(ctx, key))
    }
}
