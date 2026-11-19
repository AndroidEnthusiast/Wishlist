package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import kotlin.math.roundToInt
import org.json.JSONObject

/*
 * The photo editor (split up in the rebuild, owner 19 November 2026; MAP "How a screen is built"): what an edit is
 * ([HeroEdit]) and cutting out the finished picture ([renderCover], [renderWishPhoto]). The photo in its frame is
 * HeroPicture.kt, the editor HeroEditor.kt with its state holder HeroEditorState.kt, its tools HeroEditorParts.kt.
 */

/**
 * How the cover photo (the picture at the top of the dashboard) is shown: [scale] (1 = the photo just covers the
 * frame), how far it is moved ([x], [y], as parts of the frame), [rotation] (0, 90, 180 or 270), [bright] (-1 darker to
 * 1 lighter) and an optional [text] with its [color], a [box] behind it or not, and the place of its middle ([tx], [ty],
 * 0 to 1). Kept as numbers (not a new picture), so the photo can be edited again later.
 */
data class HeroEdit(
    val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f,
    val text: String = "", val tx: Float = 0.5f, val ty: Float = 0.75f,
    val rotation: Int = 0, val bright: Float = 0f, val color: Long = 0xFFFFFFFF, val box: Boolean = false,
    /** The text's height as a part of the frame's height (0.16 is about the size of a heading). */
    val textSize: Float = 0.16f,
    /**
     * The part of the (turned) photo in the frame, as parts of its width and height: left, top, width, height
     * (since 2.75; -1 = not chosen, then the middle). The editor sets them; the cover picture is cut out with them.
     */
    val cropL: Float = -1f, val cropT: Float = -1f, val cropW: Float = -1f, val cropH: Float = -1f
) {
    fun toJson(): String = JSONObject().put("scale", scale.toDouble()).put("x", x.toDouble()).put("y", y.toDouble())
        .put("text", text).put("tx", tx.toDouble()).put("ty", ty.toDouble())
        .put("rot", rotation).put("bright", bright.toDouble()).put("color", color).put("box", box).put("tsize", textSize.toDouble())
        .put("cl", cropL.toDouble()).put("ct", cropT.toDouble()).put("cw", cropW.toDouble()).put("ch", cropH.toDouble()).toString()

    companion object {
        fun fromJson(s: String?): HeroEdit = try {
            val o = JSONObject(s ?: "{}")
            HeroEdit(
                o.optDouble("scale", 1.0).toFloat(), o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.0).toFloat(),
                o.optString("text", ""), o.optDouble("tx", 0.5).toFloat(), o.optDouble("ty", 0.75).toFloat(),
                o.optInt("rot", 0), o.optDouble("bright", 0.0).toFloat(), o.optLong("color", 0xFFFFFFFF), o.optBoolean("box"),
                o.optDouble("tsize", 0.16).toFloat(),
                o.optDouble("cl", -1.0).toFloat(), o.optDouble("ct", -1.0).toFloat(),
                o.optDouble("cw", -1.0).toFloat(), o.optDouble("ch", -1.0).toFloat()
            )
        } catch (e: Exception) {
            HeroEdit()
        }
    }
}

internal val heroTextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/** Text colours to choose from. */
internal val TEXT_COLOURS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFFE14D, 0xFFFF7EB6, 0xFF7EEDC0, 0xFF8FB8FF)

/** Lighter or darker: [b] from -1 to 1 adds or takes away up to 40% of white. */
internal fun brightness(b: Float): ColorFilter? {
    if (b == 0f) return null
    val o = b * 0.4f * 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f))
    )
}


/**
 * Makes the cover picture: exactly the part of [image] that was in the frame ([edit], frame shape [fa]), turned and
 * lightened or darkened, as a JPEG at most 1400 pixels wide. Returns its address, or null when it couldn't be made
 * (the dashboard then works it out from [edit] itself).
 */
suspend fun renderCover(c: android.content.Context, image: String, edit: HeroEdit, fa: Float): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val path = android.net.Uri.parse(image).path ?: return@withContext null
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 2400) sample *= 2
            var bmp = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            if (edit.rotation % 360 != 0) {
                val m = android.graphics.Matrix().apply { postRotate(edit.rotation.toFloat()) }
                bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val raw = bmp.width.toFloat() / bmp.height
            // The part chosen in the editor; when nothing was moved, the middle that fills the frame.
            val cw: Float
            val ch: Float
            val cl: Float
            val ct: Float
            if (edit.cropW > 0f && edit.cropH > 0f) {
                cw = edit.cropW; ch = edit.cropH; cl = edit.cropL; ct = edit.cropT
            } else {
                if (raw > fa) { ch = 1f; cw = fa / raw } else { cw = 1f; ch = raw / fa }
                cl = (1f - cw) / 2f; ct = (1f - ch) / 2f
            }
            val left = (cl * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val top = (ct * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val w = (cw * bmp.width).roundToInt().coerceIn(1, bmp.width - left)
            val h = (ch * bmp.height).roundToInt().coerceIn(1, bmp.height - top)
            val outW = minOf(w, 1400)
            val outH = (outW * h.toFloat() / w).toInt().coerceAtLeast(1)
            val out = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            if (edit.bright != 0f) {
                val k = edit.bright * 0.4f * 255f
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(
                    floatArrayOf(1f, 0f, 0f, 0f, k, 0f, 1f, 0f, 0f, k, 0f, 0f, 1f, 0f, k, 0f, 0f, 0f, 1f, 0f)
                )
            }
            canvas.drawBitmap(bmp, android.graphics.Rect(left, top, left + w, top + h), android.graphics.Rect(0, 0, outW, outH), paint)
            val dir = java.io.File(c.filesDir, "images").apply { mkdirs() }
            val file = java.io.File(dir, "cover_" + System.currentTimeMillis() + ".jpg")
            java.io.FileOutputStream(file).use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        }
    }



/**
 * A wish's photo from the editor (owner, 7 November 2026): exactly the square that was in the frame, turned and lighter or
 * darker, on white where a fitted photo doesn't reach. A JPEG of at most 1200 × 1200. Null when it couldn't be made.
 */
suspend fun renderWishPhoto(c: android.content.Context, image: String, edit: HeroEdit): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val path = android.net.Uri.parse(image).path ?: return@withContext null
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 2400) sample *= 2
            var bmp = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            if (edit.rotation % 360 != 0) {
                val m = android.graphics.Matrix().apply { postRotate(edit.rotation.toFloat()) }
                bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val raw = bmp.width.toFloat() / bmp.height
            // The part in the frame, as parts of the photo (it may reach past the photo when it was fitted).
            val cw: Float
            val ch: Float
            val cl: Float
            val ct: Float
            if (edit.cropW > 0f && edit.cropH > 0f) {
                cw = edit.cropW; ch = edit.cropH; cl = edit.cropL; ct = edit.cropT
            } else {
                if (raw > 1f) { ch = 1f; cw = 1f / raw } else { cw = 1f; ch = raw }
                cl = (1f - cw) / 2f; ct = (1f - ch) / 2f
            }
            // As sharp as the photo allows, at most 1200 pixels.
            val side = (cw * bmp.width).coerceAtLeast(ch * bmp.height).toInt().coerceIn(200, 1200)
            val out = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            if (edit.bright != 0f) {
                val k = edit.bright * 0.4f * 255f
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(
                    floatArrayOf(1f, 0f, 0f, 0f, k, 0f, 1f, 0f, 0f, k, 0f, 0f, 1f, 0f, k, 0f, 0f, 0f, 1f, 0f)
                )
            }
            val dst = android.graphics.RectF(-cl / cw * side, -ct / ch * side, (1f - cl) / cw * side, (1f - ct) / ch * side)
            canvas.drawBitmap(bmp, null, dst, paint)
            val dir = java.io.File(c.filesDir, "images").apply { mkdirs() }
            val file = java.io.File(dir, "wish_" + System.currentTimeMillis() + ".jpg")
            java.io.FileOutputStream(file).use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        }
    }
