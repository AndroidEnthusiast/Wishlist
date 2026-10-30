package com.wishlist.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CloudException(message: String) : Exception(message)

data class RemoteList(val name: String, val cats: List<String>, val wishes: List<Wish>, val about: String = "")

/** A shared wishlist as an editor sees it: the list plus its public code (the one friends use). */
data class RemoteShared(val code: String, val list: RemoteList)

/** The note for friends travels as one extra "item" (id 0) so the online tables need no change. */
const val ABOUT_ITEM_ID = 0L

/** Talks to the Supabase functions created by supabase-setup.sql. */
object Cloud {
    private suspend fun rpc(fn: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        if (!Config.enabled) throw CloudException(tr("Online sharing isn’t set up in this build."))
        val conn = URL(Config.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/" + fn)
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", Config.SUPABASE_KEY)
            // Old-style anon keys are JWTs and also go in Authorization. New publishable keys only need apikey.
            if (Config.SUPABASE_KEY.startsWith("eyJ")) {
                conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw CloudException("Server said $status: ${text.take(160)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun storageUrl(path: String) = Config.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/" + path

    /** The address friends' browsers load a shared picture from. */
    fun photoUrl(code: String, name: String) = storageUrl("public/" + Config.PHOTO_BUCKET + "/" + code + "/" + name)

    private fun addKeys(conn: HttpURLConnection, secret: String, header: String) {
        conn.setRequestProperty("apikey", Config.SUPABASE_KEY)
        if (Config.SUPABASE_KEY.startsWith("eyJ")) conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY)
        // Proves to Supabase that this phone may change the list's pictures: the owner token, or an editor's edit key.
        conn.setRequestProperty(header, secret)
    }

    suspend fun uploadPhoto(
        code: String, token: String, name: String, bytes: ByteArray, header: String = "x-owner-token"
    ) = withContext(Dispatchers.IO) {
        if (!Config.enabled) throw CloudException(tr("Online sharing isn’t set up in this build."))
        val conn = URL(storageUrl(Config.PHOTO_BUCKET + "/" + code + "/" + name)).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("Cache-Control", "max-age=31536000")
            addKeys(conn, token, header)
            conn.outputStream.use { it.write(bytes) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            // "Duplicate" means this exact picture is already there, which is fine.
            if (status !in 200..299 && !text.contains("Duplicate")) {
                throw CloudException("Photo upload said $status: ${text.take(120)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun deletePhoto(
        code: String, token: String, name: String, header: String = "x-owner-token"
    ) = withContext(Dispatchers.IO) {
        if (!Config.enabled) throw CloudException(tr("Online sharing isn’t set up in this build."))
        val conn = URL(storageUrl(Config.PHOTO_BUCKET + "/" + code + "/" + name)).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            addKeys(conn, token, header)
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            // 404 means it is already gone, which is what we wanted.
            if (status !in 200..299 && status != 404) {
                throw CloudException("Photo delete said $status: ${text.take(120)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun createList(code: String, token: String, name: String) {
        rpc("wl_create_list", JSONObject().put("p_code", code).put("p_token", token).put("p_name", name))
    }

    suspend fun pushList(
        code: String, token: String, name: String, cats: List<String>, wishes: List<Wish>, about: String = ""
    ) {
        val items = JSONArray()
        wishes.forEach { items.put(wishToJson(if (ImageStore.isLocal(it.image)) it.copy(image = "") else it)) }
        if (about.isNotBlank()) {
            items.put(
                JSONObject().put("id", ABOUT_ITEM_ID).put("kind", "about").put("about", about)
                    .put("url", "").put("title", "")
            )
        }
        rpc(
            "wl_sync_list",
            JSONObject()
                .put("p_code", code).put("p_token", token).put("p_name", name)
                .put("p_cats", JSONArray(cats)).put("p_items", items)
        )
    }

    suspend fun deleteList(code: String, token: String) {
        rpc("wl_delete_list", JSONObject().put("p_code", code).put("p_token", token))
    }

    /** Returns null when no list exists for that code. */
    suspend fun fetchList(code: String): RemoteList? {
        val t = rpc("wl_get_list", JSONObject().put("p_code", code)).trim()
        if (t.isEmpty() || t == "null") return null
        return parseList(JSONObject(t))
    }

    private fun parseList(o: JSONObject): RemoteList {
        val a = o.getJSONArray("items")
        val cs = o.optJSONArray("cats")
        var about = ""
        val wishes = ArrayList<Wish>()
        for (i in 0 until a.length()) {
            val item = a.getJSONObject(i)
            if (item.optString("kind") == "about") about = item.optString("about", "")
            else wishes.add(wishFromJson(item))
        }
        return RemoteList(
            o.optString("name", "Wishlist"),
            if (cs == null) emptyList() else (0 until cs.length()).map { cs.getString(it) },
            wishes, about
        )
    }

    // ---- Shared wishlists (supabase-shared.sql). The edit key is the secret from the invite link. ----

    suspend fun shCreate(code: String, key: String, token: String, name: String) {
        rpc(
            "wl_sh_create",
            JSONObject().put("p_code", code).put("p_key", key).put("p_token", token).put("p_name", name)
        )
    }

    /** The whole shared list, or null when the key doesn't work (list deleted, or the invite link was reset). */
    suspend fun shGet(key: String): RemoteShared? {
        val t = rpc("wl_sh_get", JSONObject().put("p_key", key)).trim()
        if (t.isEmpty() || t == "null") return null
        val o = JSONObject(t)
        return RemoteShared(o.getString("code"), parseList(o))
    }

    /** The name of the list an invite is for, or null when the invite doesn't work. */
    suspend fun shInfo(key: String): String? {
        val t = rpc("wl_sh_info", JSONObject().put("p_key", key)).trim()
        if (t.isEmpty() || t == "null") return null
        return JSONObject(t).optString("name", "Shared wishlist")
    }

    /**
     * Adds or changes one wish. With [fields] only those fields are written, on top of what is online now (a field
     * that is missing from the wish is cleared); without, the whole wish is replaced. Pictures that only exist on this phone are never sent.
     */
    suspend fun shPut(key: String, w: Wish, fields: List<String>? = null) {
        val clean = if (ImageStore.isLocal(w.image)) w.copy(image = "") else w
        val body = JSONObject().put("p_key", key).put("p_item", wishToJson(clean))
        if (fields != null) body.put("p_fields", JSONArray(fields))
        rpc("wl_sh_put_item", body)
    }

    suspend fun shDelete(key: String, id: Long) {
        rpc("wl_sh_del_item", JSONObject().put("p_key", key).put("p_id", id))
    }

    suspend fun shMeta(key: String, name: String, cats: List<String>, about: String) {
        rpc(
            "wl_sh_set_meta",
            JSONObject().put("p_key", key).put("p_name", name).put("p_cats", JSONArray(cats)).put("p_about", about)
        )
    }

    suspend fun shName(key: String, name: String) {
        rpc("wl_sh_set_name", JSONObject().put("p_key", key).put("p_name", name))
    }

    suspend fun shAbout(key: String, about: String) {
        rpc("wl_sh_set_about", JSONObject().put("p_key", key).put("p_about", about))
    }

    suspend fun shCatAdd(key: String, name: String) {
        rpc("wl_sh_cat_add", JSONObject().put("p_key", key).put("p_name", name))
    }

    /** Renames the category and moves every wish in it (also wishes this phone doesn't know yet). */
    suspend fun shCatRename(key: String, old: String, name: String) {
        rpc("wl_sh_cat_rename", JSONObject().put("p_key", key).put("p_old", old).put("p_new", name))
    }

    /** Removes the category; wishes in it stay, without a category. */
    suspend fun shCatRemove(key: String, name: String) {
        rpc("wl_sh_cat_remove", JSONObject().put("p_key", key).put("p_name", name))
    }

    suspend fun shReset(code: String, token: String, newKey: String) {
        rpc("wl_sh_reset_key", JSONObject().put("p_code", code).put("p_token", token).put("p_new_key", newKey))
    }

    /** Item id -> true if reserved by this phone, false if reserved by someone else. */
    suspend fun fetchReservations(code: String, device: String): Map<Long, Boolean> {
        val t = rpc("wl_get_reservations", JSONObject().put("p_code", code).put("p_device", device))
        val a = JSONArray(t)
        val m = HashMap<Long, Boolean>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            m[o.getLong("id")] = o.getBoolean("mine")
        }
        return m
    }

    /** False if somebody else got there first. */
    suspend fun reserve(code: String, item: Long, device: String): Boolean =
        rpc(
            "wl_reserve_item",
            JSONObject().put("p_code", code).put("p_item", item).put("p_device", device)
        ).trim() == "true"

    suspend fun unreserve(code: String, item: Long, device: String) {
        rpc(
            "wl_unreserve_item",
            JSONObject().put("p_code", code).put("p_item", item).put("p_device", device)
        )
    }
}
