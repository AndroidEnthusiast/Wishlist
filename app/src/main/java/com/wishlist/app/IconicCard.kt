package com.wishlist.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The holo share card of the Iconic prize (owner, 27 November 2026; Iconic.kt): with it on, sharing one of your own
 * lists sends this picture with the link (before, and with it off, only the link). A holographic frame, a white card
 * with the list's name, how many wishes, and a crown. Never an amount. Drawn on the phone, like ShareCard.kt.
 */
object IconicCard {
    private const val CROWN = "M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5z"

    /** The picture: 1200 x 630, like link previews. */
    fun draw(c: Context, listName: String, wishes: Int): Bitmap {
        val w = 1200f
        val h = 630f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val holo = HoloColours.map { it.toArgb() }.toIntArray()
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w, h, holo, null, Shader.TileMode.CLAMP) }
        cv.drawRect(0f, 0f, w, h, frame)
        val inner = RectF(36f, 36f, w - 36f, h - 36f)
        cv.drawRoundRect(inner, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        // The crown, on a holo disc at the top right.
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(w - 260f, 70f, w - 90f, 240f, holo, null, Shader.TileMode.CLAMP) }
        cv.drawCircle(w - 175f, 155f, 85f, disc)
        cv.save()
        cv.translate(w - 175f - 60f, 155f - 62f)
        cv.scale(5f, 5f)
        cv.drawPath(PathParser.createPathFromPathData(CROWN), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        cv.restore()

        val face = try { ResourcesCompat.getFont(c, R.font.google_sans) } catch (e: android.content.res.Resources.NotFoundException) { null } ?: Typeface.DEFAULT
        val width = 760
        fun layout(text: String, paint: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.05f).setMaxLines(3).build()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1F2A.toInt(); typeface = Typeface.create(face, Typeface.BOLD) }
        var size = 96f
        var tl: StaticLayout
        // Long names get a smaller font, up to three lines.
        while (true) {
            title.textSize = size
            tl = layout(listName, title)
            if (tl.lineCount <= 2 || size <= 52f) break
            size -= 4f
        }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5D6475.toInt(); typeface = face; textSize = 40f }
        val sl = layout(wishesLabel(wishes), sub)
        val gap = 24f
        val top = ((h - (tl.height + gap + sl.height)) / 2f).coerceAtLeast(60f)
        cv.save()
        cv.translate(110f, top)
        tl.draw(cv)
        cv.translate(0f, tl.height + gap)
        sl.draw(cv)
        cv.restore()
        return bmp
    }

    /** Opens the share sheet with the picture and [text] (the link). Falls back to the text alone. */
    suspend fun share(c: Context, listName: String, wishes: Int, text: String) {
        val uri = try {
            withContext(Dispatchers.Default) {
                val dir = File(c.cacheDir, "share").apply { mkdirs() }
                val f = File(dir, "iconic-wishlist.png")
                f.outputStream().use { draw(c, listName, wishes).compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(c, c.packageName + ".files", f)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        if (uri == null) { shareText(c, tr("Share wishlist"), text); return }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(send, tr("Share wishlist")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
