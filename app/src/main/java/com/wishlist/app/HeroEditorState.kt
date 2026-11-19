package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * The photo editor's state holder (the rebuild, owner 19 November 2026; MAP "How a screen is built"): the photo and
 * the edit being made, which tool is open, and the photos picked here with the arrow (the ones not kept are deleted
 * again). The editor (HeroEditor.kt) only draws; nothing is saved until the tick.
 */
@Stable
internal class HeroEditorState(private val ctx: Context, image: String, start: HeroEdit) {
    var img by mutableStateOf(image)
    var edit by mutableStateOf(start)
    var tool by mutableStateOf("crop")
    var previewing by mutableStateOf(false)
    // The text is selected (tapped): it shows corners to make it bigger or smaller.
    var textOn by mutableStateOf(false)
    // "Fit whole photo" was tapped (a counter, so it works again after moving the photo).
    var fitAsk by mutableStateOf(0)
    // Photos picked here with the arrow: the ones not kept are deleted again.
    var picked by mutableStateOf(listOf<String>())
        private set

    /** A new photo (the arrow up): it starts unturned, as it is, filling the frame; the text stays. */
    fun usePhoto(file: String) {
        picked = picked + file
        img = file
        edit = edit.copy(scale = 1f, x = 0f, y = 0f, rotation = 0, bright = 0f, cropL = -1f, cropT = -1f, cropW = -1f, cropH = -1f)
    }

    /** Turn the photo by [degrees] (90 right, 270 left); it then fills the frame again. */
    fun rotate(degrees: Int) {
        edit = edit.copy(rotation = (edit.rotation + degrees) % 360, scale = 1f, x = 0f, y = 0f, cropL = -1f, cropT = -1f, cropW = -1f, cropH = -1f)
    }

    /** Cancel: every photo picked here goes again. */
    fun dropPicked() = picked.forEach { ImageStore.delete(ctx, it) }

    /** Save: the photos picked here but not kept go. */
    fun dropUnused() = picked.filter { it != img }.forEach { ImageStore.delete(ctx, it) }
}
