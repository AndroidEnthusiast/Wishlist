package com.wishlist.app

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Profile, group and gift exchange photos as files (ROADMAP S4.1, 2.185; owner, 11 November 2026). They used to be
 * kept as text inside the database; now they live in Supabase Storage (bucket "people-photos": p/<profile>/,
 * g/<group>/, x/<exchange>/) under a random name that changes with every new photo, and the database keeps the
 * address (supabase-friends.sql "Photos as files"). The server's checks still decide who is given the address
 * (friends, group and exchange members), as with wish photos.
 *
 * Reading accepts both forms: an address, or the old embedded photo (base64) that an older app saved ([bytes]).
 * Saving uploads a file, sets its address and deletes the file it replaced ([save]); before the scripts of 2.185 are
 * run, the old way is used. [moveOld] moves photos that are still embedded, once, from the phone that owns them.
 */
object PeoplePhotos {
    private const val BUCKET = "people-photos"
    private val rnd = SecureRandom()

    private fun publicPrefix() = Config.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/public/$BUCKET/"

    /** A new random file name: 32 letters and digits, so an address can't be guessed. */
    fun newName(): String {
        val abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..32).map { abc[rnd.nextInt(abc.length)] }.joinToString("") + ".jpg"
    }

    /** True for an address in our own photo bucket (nothing else is ever downloaded). */
    fun isOurs(v: String): Boolean = v.startsWith(publicPrefix()) && !v.contains("..") && !v.contains('?')

    /**
     * The photo's bytes from what the server handed out: our own file address (downloaded), or an embedded photo
     * (decoded). Null for "", a foreign address, or a failure.
     */
    suspend fun bytes(v: String): ByteArray? {
        val s = v.trim().removeSurrounding("\"")
        if (s.isEmpty()) return null
        if (s.startsWith("https://")) {
            if (!isOurs(s)) return null
            return withContext(Dispatchers.IO) {
                try {
                    val h = URL(s).openConnection() as HttpURLConnection
                    h.connectTimeout = 15000; h.readTimeout = 20000
                    try { if (h.responseCode == 200) h.inputStream.use { it.readBytes() }.takeIf { it.size in 1..2_000_000 } else null } finally { h.disconnect() }
                } catch (e: Exception) {
                    null
                }
            }
        }
        return try { Base64.decode(s, Base64.DEFAULT) } catch (e: Exception) { null }
    }

    private suspend fun storage(method: String, path: String, auth: JSONObject, bytes: ByteArray?) = withContext(Dispatchers.IO) {
        Cloud.noServerInChecks()
        Calls.count("people photo " + method.lowercase())
        val conn = URL(Config.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/$BUCKET/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.setRequestProperty("apikey", Config.SUPABASE_KEY)
            if (Config.SUPABASE_KEY.startsWith("eyJ")) conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY)
            // Proves to Storage's rules who is asking (wl_people_photo_ok).
            conn.setRequestProperty("x-profile-id", auth.getString("p_id"))
            conn.setRequestProperty("x-profile-token", auth.getString("p_token"))
            if (bytes != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.setRequestProperty("Content-Type", "image/jpeg")
                conn.setRequestProperty("Cache-Control", "max-age=31536000")
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw CloudException("Photo $method said $status: ${text.take(120)}")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Saves a photo for [folder] ("p/<profile>", "g/<group>", "x/<exchange>") with [setter] (wl_pf_photo_url,
     * wl_gr_photo_url or wl_gx_photo_url, given [args] plus p_url): [jpeg] null removes it. Returns false when the
     * scripts of 2.185 aren't run yet, so the caller saves the old way.
     */
    suspend fun save(folder: String, auth: JSONObject, setter: String, args: JSONObject, jpeg: ByteArray?): Boolean {
        var url = ""
        if (jpeg != null) {
            val name = newName()
            try {
                storage("POST", "$folder/$name", auth, jpeg)
            } catch (e: Exception) {
                // No bucket or no rules yet (supabase-photos.sql not run): the old way.
                if ((e.message ?: "").let { it.contains("Bucket not found") || it.contains("said 404") || it.contains("said 400") || it.contains("said 403") }) return false
                throw e
            }
            url = publicPrefix() + "$folder/$name"
        }
        val old = try {
            Cloud.rpc(setter, args.put("p_url", url)).trim().removeSurrounding("\"")
        } catch (e: Exception) {
            if (MineSync.missingFunction(e)) {
                if (url.isNotEmpty()) try { storage("DELETE", url.removePrefix(publicPrefix()), auth, null) } catch (x: Exception) {}
                return false
            }
            throw e
        }
        // The file this one replaced: deleted, so its address stops working. A failure here only leaves a file behind.
        if (old.isNotEmpty() && isOurs(old) && old != url) try { storage("DELETE", old.removePrefix(publicPrefix()), auth, null) } catch (e: Exception) {}
        return true
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /**
     * Photos that are still embedded in the database are moved to files, once, by the phone that owns them: your
     * profile photo, and the photos of the groups and exchanges you organise. [what] names it ("p", "g:<id>",
     * "x:<id>"); [current] reads what the server hands out now; [save] saves bytes the new way. Done for good once it
     * worked, or when there is nothing embedded.
     */
    suspend fun moveOld(c: Context, what: String, current: suspend () -> String, save: suspend (ByteArray) -> Boolean) {
        val key = "photo_moved_$what"
        if (prefs(c).getBoolean(key, false)) return
        try {
            val v = current().trim().removeSurrounding("\"")
            val done = when {
                v.isEmpty() || v.startsWith("https://") -> true
                else -> bytes(v)?.let { save(it) } ?: true
            }
            if (done) prefs(c).edit().putBoolean(key, true).apply()
        } catch (e: Exception) {
            // Offline or a hiccup: tried again at the next start.
        }
    }
}
