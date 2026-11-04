package com.wishlist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Photos in messages (2.109, with the photos part of supabase-chat.sql; owner, 4 November 2026).
 *  - The photo is shrunk (at most 1024 pixels, about 100–300 KB) and locked with a NEW random key (AES-GCM) on the
 *    phone. Only the locked photo goes online ([Chat.putPhoto]); the key and the photo's name travel inside the
 *    message, which is locked end to end like every message. So the server never sees the photo.
 *  - The server keeps it 30 days; both phones keep their own unlocked copy in noBackupFilesDir/chatphotos, which is
 *    never in Export (Backup.kt walks filesDir only), just like chat keys and messages. A new phone can't read old
 *    photos, as with old messages.
 *  - Deleting the message deletes the photo online (database) and on the sender's phone.
 */
object ChatPhotos {
    private val rnd = SecureRandom()
    private const val MAX_BYTES = 600_000

    private fun dir(c: Context): File = File(c.noBackupFilesDir, "chatphotos").apply { mkdirs() }

    /** This phone's copy of photo [id] (it may not exist yet). Only letters and digits, so it stays in the folder. */
    fun file(c: Context, id: String): File? {
        val safe = id.filter { it.isLetterOrDigit() }.take(64)
        return if (safe.isEmpty()) null else File(dir(c), "$safe.jpg")
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    private fun lock(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return nonce + c.doFinal(plain)
    }

    private fun unlock(key: ByteArray, all: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12))
        c.doFinal(all, 12, all.size - 12)
    } catch (e: Exception) {
        null
    }

    /** The picture (one ImageStore.import made: turned upright, at most 1024 pixels) as JPEG bytes small enough to send. */
    private suspend fun shrink(image: String): Triple<ByteArray, Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val path = Uri.parse(image).path ?: return@withContext null
            val bmp = BitmapFactory.decodeFile(path) ?: return@withContext null
            var q = 82
            var bytes = ByteArray(0)
            while (true) {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
                bytes = out.toByteArray()
                if (bytes.size <= MAX_BYTES || q <= 40) break
                q -= 14
            }
            Triple(bytes, bmp.width, bmp.height)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Locks and uploads the picture for [other]; returns the message to send ([Chat.send] then ties it to the photo).
     * Message fields: text = the photo's name online, img = its key (base64), w/h = its size.
     */
    suspend fun upload(c: Context, other: String, image: String): ChatContent {
        val (bytes, w, h) = shrink(image) ?: throw IllegalStateException("bad photo")
        val key = ByteArray(32).also { rnd.nextBytes(it) }
        val data = withContext(Dispatchers.Default) { b64(lock(key, bytes)) }
        val id = Chat.putPhoto(c, other, data)
        withContext(Dispatchers.IO) { file(c, id)?.writeBytes(bytes) }
        return ChatContent(true, "photo", id, img = b64(key), w = w, h = h)
    }

    /**
     * The photo of a message on this phone: fetched and unlocked the first time. Null when it is no longer available
     * (older than 30 days and not on this phone, or deleted). Throws when the server can't be reached.
     */
    suspend fun load(c: Context, m: ChatContent): File? {
        val f = file(c, m.text) ?: return null
        if (withContext(Dispatchers.IO) { f.exists() }) return f
        val data = Chat.getPhoto(c, m.text) ?: return null
        val plain = withContext(Dispatchers.Default) {
            try { unlock(unb64(m.img), unb64(data)) } catch (e: Exception) { null }
        } ?: return null
        withContext(Dispatchers.IO) { f.writeBytes(plain) }
        return f
    }

    /** Removes this phone's copy (after deleting the message). */
    fun forget(c: Context, m: ChatContent) {
        if (m.kind != "photo") return
        try { file(c, m.text)?.delete() } catch (e: Exception) { }
    }
}
