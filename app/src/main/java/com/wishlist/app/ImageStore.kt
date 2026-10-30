package com.wishlist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/** Keeps pictures you pick yourself inside the app, so they stay even if you delete the original photo. */
object ImageStore {
    private const val MAX_SIDE = 1024

    private fun dir(c: Context): File = File(c.filesDir, "images").apply { mkdirs() }

    /** True for a picture stored by this app (as opposed to a web link). */
    fun isLocal(image: String): Boolean = image.startsWith("file:")

    private fun rotationOf(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    /** Copies the picked picture into the app, shrunk to at most 1024 pixels and turned the right way up. Returns its address, or null on failure. */
    suspend fun import(c: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val cr = c.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val m = Matrix()
            val longest = max(bmp.width, bmp.height)
            if (longest > MAX_SIDE) {
                val f = MAX_SIDE.toFloat() / longest
                m.postScale(f, f)
            }
            val rot = cr.openInputStream(uri)?.use { rotationOf(it) } ?: 0
            if (rot != 0) m.postRotate(rot.toFloat())
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

            // JPEG has no transparency, so put see-through pictures on white (the cards show them on white too).
            if (bmp.hasAlpha()) {
                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flat)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                bmp = flat
            }

            val file = File(dir(c), "wish_" + System.currentTimeMillis() + ".jpg")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        }
    }

    /** A smaller JPEG (at most 600 pixels, about 50 KB) of a stored picture, for the page friends see. */
    suspend fun shareCopy(image: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val path = Uri.parse(image).path ?: return@withContext null
            if (!File(path).exists()) return@withContext null
            val src = BitmapFactory.decodeFile(path) ?: return@withContext null
            val longest = max(src.width, src.height)
            val bmp = if (longest > 600) {
                val f = 600f / longest
                Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
            } else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes a picture this app stored. Web links and anything outside the app's own folder are left alone. */
    fun delete(c: Context, image: String) {
        if (!isLocal(image)) return
        try {
            val f = File(Uri.parse(image).path ?: return)
            if (f.parentFile?.canonicalPath == dir(c).canonicalPath) f.delete()
        } catch (e: Exception) {
        }
    }
}
