package com.wishlist.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * Screenshot to wish (owner, 20 November 2026; docs/decisions/screenshots.md): the state holder (MAP "How a screen is
 * built"); ShotsFlow.kt only draws. One picture: the photo editor, then Add a wish. Several (at most 10): a pick screen
 * (which ones, which list), then each one through the same editor and form, "2 of 5", Skip; every wish is saved when
 * its form is saved, a note says so, and leaving halfway asks first.
 */

/** One shared picture: its copy on the phone (made small by [ImageStore.import]), or [failed] when it can't be read. */
@Stable
internal class Shot(val source: String) {
    var file by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    var picked by mutableStateOf(true)
}

internal enum class ShotStep { LOADING, PICK, PHOTO, FORM, DONE }

@Stable
internal class ShotsState(private val ctx: Context, val pictures: List<String>, val link: String?, val more: Boolean) {
    val shots = pictures.map { Shot(it) }
    val several get() = shots.size > 1
    var step by mutableStateOf(ShotStep.LOADING)
        private set
    // Where the wishes go: a save key ("mine", "mine:…", or a shared list's code), as in Add a wish's "Save to".
    var target by mutableStateOf(Repo.lastSaveTo)
    // The screenshots being added, in order, and which one is on screen.
    var queue by mutableStateOf(listOf<Shot>())
        private set
    var index by mutableStateOf(0)
        private set
    // The photo as cut in the editor, for the form (deleted again when skipped or stopped).
    var cut by mutableStateOf<String?>(null)
        private set
    // How many were saved, the last one's note ("“Shoes” added to My wishlist") and the lists they went to.
    var added by mutableStateOf(0)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var lists by mutableStateOf(listOf<Pair<String, String>>())
        private set
    // "Stop adding? 2 added, 3 left." is open.
    var askStop by mutableStateOf(false)

    val current: Shot? get() = queue.getOrNull(index)
    val usable: List<Shot> get() = shots.filter { it.file != null }
    val pickedCount: Int get() = shots.count { it.picked && it.file != null }
    val left: Int get() = (queue.size - index).coerceAtLeast(0)

    /** Copies every picture into the app, made small (owner: never too big). One picture goes straight to the editor. */
    suspend fun load(import: suspend (Uri) -> String? = { ImageStore.import(ctx, it) }) {
        for (s in shots) {
            val f = try { import(Uri.parse(s.source)) } catch (e: Exception) { null }
            if (f == null) { s.failed = true; s.picked = false } else s.file = f
        }
        step = when {
            usable.isEmpty() -> ShotStep.DONE
            !several -> { begin(); step }
            else -> ShotStep.PICK
        }
    }

    /** Tick or untick a screenshot on the pick screen (one that couldn't be read stays unticked). */
    fun toggle(s: Shot) { if (s.file != null) s.picked = !s.picked }

    fun pickAll(all: Boolean) = usable.forEach { it.picked = all }

    /** "Add N wishes": the picked ones, one by one; the others are deleted from the phone. */
    fun begin() {
        queue = shots.filter { it.picked && it.file != null }
        shots.filter { it !in queue }.forEach { drop(it) }
        index = 0
        step = if (queue.isEmpty()) ShotStep.DONE else ShotStep.PHOTO
    }

    /** The editor's tick: [out] is the cut photo (renderWishPhoto), made from [chosen] (the screenshot or a new photo). */
    fun photoDone(out: String) {
        cut = out
        step = ShotStep.FORM
    }

    /** The editor's tick on screenshot [file]: [chosen] (it, or a new photo picked inside the editor) is cut as [e]. */
    suspend fun photoEdited(file: String, chosen: String, e: HeroEdit) {
        val out = renderWishPhoto(ctx, chosen, e) ?: chosen
        // A new photo picked inside the editor was only needed to make the cut.
        if (out != chosen && chosen != file) ImageStore.delete(ctx, chosen)
        photoDone(out)
    }

    /** Add a wish was saved: to the list open now (the form opened the one chosen in "Save to"). */
    fun savedHere(name: String) {
        target = Shared.active ?: MyLists.saveKey(Repo.openMine, false)
        saved(name, target, Repo.listName)
    }

    /** Skip this screenshot: its files go, the next one comes. */
    fun skip() {
        current?.let { drop(it) }
        cut?.let { ImageStore.delete(ctx, it) }; cut = null
        next()
    }

    /** The form was saved: the wish now owns the cut photo; the screenshot's own copy goes. */
    fun saved(name: String, listKey: String, listName: String) {
        added++
        notice = tr("“%s” added to %s", name, listName)
        if (lists.none { it.first == listKey }) lists = lists + (listKey to listName)
        current?.let { s -> if (s.file != cut) drop(s) }
        cut = null
        next()
    }

    private fun next() {
        index++
        step = if (index >= queue.size) ShotStep.DONE else ShotStep.PHOTO
    }

    /** "Stop" (or the only screenshot cancelled): what is saved stays, the rest is deleted from the phone. */
    fun stop() {
        askStop = false
        cut?.let { ImageStore.delete(ctx, it) }; cut = null
        queue.drop(index).forEach { drop(it) }
        shots.filter { it !in queue }.forEach { drop(it) }
        index = queue.size
        step = ShotStep.DONE
    }

    /** Cancel on the editor or the form: with several, ask first; with one, stop at once. */
    fun cancel() { if (several) askStop = true else stop() }

    /** The message at the end, or null when nothing was added. */
    fun doneMessage(): String? = when {
        added == 0 -> null
        added == 1 -> notice
        lists.size == 1 -> tr("%d wishes added to %s", added, lists[0].second)
        else -> tr("%d wishes added", added)
    }

    private fun drop(s: Shot) { s.file?.let { ImageStore.delete(ctx, it) }; s.file = null }
}
