package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/*
 * A cover photo for one wishlist (2.89, owner): optional, none by default. Set in the list's settings with the same
 * editor as the profile photo (rectangular frame), with a Preview of how it looks at the top of the list. Kept on this
 * phone ("cover_m:<id>" for your own lists, "cover_s:<code>" for group wishlists). Since 2.105 (owner) the cover of
 * your own list also goes online with it (Sync.pushData), so friends see it in the app and on the friends' page.
 * Gift lists keep theirs on the phone. Since the rebuild (19 November 2026) this file keeps the covers; what shows them
 * is CoverViews.kt, what changes them from a screen CoverModel.kt.
 */

data class ListCover(val image: String, val edit: HeroEdit, val cut: String)

object ListCovers {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Goes up on every change, so screens show the new cover. */
    var version by mutableIntStateOf(0)
        private set

    /** The key of the list that is open: your own ("m:<id>") or a group wishlist ("s:<code>"). */
    fun openKey(): String = Shared.active?.let { "s:$it" } ?: "m:${Repo.openMine}"

    fun get(c: Context, key: String): ListCover? {
        @Suppress("UNUSED_VARIABLE") val v = version
        val t = prefs(c).getString("cover_$key", null) ?: return null
        return try {
            val o = JSONObject(t)
            val img = o.optString("img", "")
            if (img.isEmpty()) null else ListCover(img, HeroEdit.fromJson(o.optString("edit", "{}")), o.optString("cut", ""))
        } catch (e: Exception) {
            null
        }
    }

    /** Saves the cover of [key]: the whole photo, how it is cut, and the cut-out picture. The old pictures go. */
    fun set(c: Context, key: String, image: String, edit: HeroEdit, cut: String) {
        val old = get(c, key)
        if (old != null) {
            if (old.image != image) ImageStore.delete(c, old.image)
            if (old.cut.isNotEmpty() && old.cut != cut) ImageStore.delete(c, old.cut)
        }
        val o = JSONObject().put("img", image).put("edit", edit.toJson()).put("cut", cut)
        prefs(c).edit().putString("cover_$key", o.toString()).apply()
        version++
        sendOnline(c, key)
    }

    /** A cover of your own list changed: the list goes online again with it (when it is online). */
    private fun sendOnline(c: Context, key: String) {
        if (!key.startsWith("m:")) return
        val id = key.removePrefix("m:")
        if (id == Repo.openMine) Sync.push()
        else Repo.mine(c, id).takeIf { it.code != null }?.let { Sync.pushStored(c, it, Repo.binnedOf(id)) }
    }

    fun remove(c: Context, key: String) {
        get(c, key)?.let { ImageStore.delete(c, it.image); if (it.cut.isNotEmpty()) ImageStore.delete(c, it.cut) }
        prefs(c).edit().remove("cover_$key").apply()
        version++
        if (key.startsWith("m:") && key.removePrefix("m:") in Repo.mineIds) sendOnline(c, key)
    }

    /**
     * Takes the cover of [key] off the list but keeps its pictures, for Undo (2.110). Returns what [restore] needs, or
     * null when there was none. [forget] then deletes the pictures for good.
     */
    fun detach(c: Context, key: String): String? {
        val raw = prefs(c).getString("cover_$key", null) ?: return null
        prefs(c).edit().remove("cover_$key").apply()
        version++
        if (key.startsWith("m:") && key.removePrefix("m:") in Repo.mineIds) sendOnline(c, key)
        return raw
    }

    fun restore(c: Context, key: String, raw: String) {
        prefs(c).edit().putString("cover_$key", raw).apply()
        version++
        sendOnline(c, key)
    }

    fun forget(c: Context, raw: String) {
        try {
            val o = JSONObject(raw)
            ImageStore.delete(c, o.optString("img", ""))
            o.optString("cut", "").takeIf { it.isNotEmpty() }?.let { ImageStore.delete(c, it) }
        } catch (e: Exception) {
        }
    }

    /** Your list [from] now lives under the id [to] (the original list was deleted and the next took its place). */
    fun move(c: Context, from: String, to: String) {
        val p = prefs(c)
        val t = p.getString("cover_$from", null) ?: return
        p.edit().putString("cover_$to", t).remove("cover_$from").apply()
        version++
    }
}
