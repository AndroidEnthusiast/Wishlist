package com.wishlist.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/*
 * Several wishlists of your own (since 2.64). The one that is open lives in Repo's fields (myWishes, myName, myCats,
 * myAbout, shareCode, ownerToken, uploadedPhotos), so every screen keeps working as before. The others are stored:
 *  - "main", the original My wishlist, keeps its old preference keys ("wishes", "name", "cats", "about", "code",
 *    "token", "photosUp"), so nothing changes for it;
 *  - each other list is one JSON text under "mine_<id>".
 * The bin is one for all of them ("bin"); "bin_owner" remembers which list a wish in the bin came from.
 * Each list can have its own friends' link (code and token), like My wishlist always had.
 */

const val MAIN_LIST = "main"

/** One of your own wishlists as stored on the phone. */
data class MineData(
    val id: String,
    val name: String,
    val about: String,
    val cats: List<String>,
    val wishes: List<Wish>,
    val code: String?,
    val token: String?,
    val photos: Set<String>
)

val DEFAULT_CATS = listOf("Tech", "Home", "Fashion", "Books")

object MyLists {
    fun blobKey(id: String) = "mine_$id"

    private fun wishesFrom(a: JSONArray?): List<Wish> {
        if (a == null) return emptyList()
        val out = ArrayList<Wish>()
        for (i in 0 until a.length()) {
            try {
                out.add(wishFromJson(a.getJSONObject(i)))
            } catch (e: Exception) {
            }
        }
        return out
    }

    private fun stringsFrom(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    /** Reads a list from the phone's storage (not the open one: that one is in Repo). */
    fun read(p: SharedPreferences, id: String, keep: Boolean = true): MineData {
        // Since 2.167 your lists come from the database (ListStore); [keep] false reads the settings store itself.
        if (keep) ListStore.get(id)?.let { return it }
        if (id == MAIN_LIST) {
            return MineData(
                MAIN_LIST,
                p.getString("name", null)?.takeIf { it.isNotBlank() } ?: "My wishlist",
                p.getString("about", "") ?: "",
                p.getString("cats", null)?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_CATS,
                SafeStore.readList(p, "wishes", keep) { wishFromJson(it) },
                p.getString("code", null), p.getString("token", null),
                p.getStringSet("photosUp", null)?.toSet() ?: emptySet()
            )
        }
        val o = try {
            JSONObject(p.getString(blobKey(id), null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        return MineData(
            id,
            o.optString("name", "").ifBlank { "Wishlist" },
            o.optString("about", ""),
            if (o.has("cats")) stringsFrom(o.optJSONArray("cats")) else DEFAULT_CATS,
            wishesFrom(o.optJSONArray("wishes")),
            o.optString("code", "").ifEmpty { null },
            o.optString("token", "").ifEmpty { null },
            stringsFrom(o.optJSONArray("photos")).toSet()
        )
    }

    /** Writes a list into the editor (the caller applies it). */
    fun write(e: SharedPreferences.Editor, d: MineData) {
        ListStore.put(d)
        val arr = JSONArray()
        d.wishes.forEach { arr.put(wishToJson(it)) }
        if (d.id == MAIN_LIST) {
            e.putString("wishes", arr.toString())
                .putString("name", d.name)
                .putString("about", d.about)
                .putString("cats", d.cats.joinToString("\n"))
                .putString("code", d.code)
                .putString("token", d.token)
                .putStringSet("photosUp", d.photos.toSet())
            return
        }
        val o = JSONObject()
            .put("name", d.name).put("about", d.about)
            .put("cats", JSONArray(d.cats)).put("wishes", arr)
            .put("code", d.code ?: "").put("token", d.token ?: "")
            .put("photos", JSONArray(d.photos.toList()))
        e.putString(blobKey(d.id), o.toString())
    }

    /** The key a wish's "Save to" choice uses: "mine" / "private" for the original list, "mine:<id>" otherwise. */
    fun saveKey(id: String, priv: Boolean): String {
        val base = if (priv) "private" else "mine"
        return if (id == MAIN_LIST) base else "$base:$id"
    }

    /** The list id inside a "Save to" key, or null when the key is a group wishlist's code. */
    fun idOfSaveKey(key: String): String? = when {
        key == "mine" || key == "private" -> MAIN_LIST
        key.startsWith("mine:") -> key.substringAfter(':')
        key.startsWith("private:") -> key.substringAfter(':')
        else -> null
    }
}
