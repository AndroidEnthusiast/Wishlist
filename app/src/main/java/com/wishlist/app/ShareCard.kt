package com.wishlist.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sharing a shared wishlist with a picture that carries the list's name ("Join Jakim's B'day wishlist").
 * A link preview can't show the name: WhatsApp and the like read a fixed page on GitHub. So the picture is drawn here,
 * on the phone, and shared together with a short message and the link. Same picture and words as `shareCard` in
 * web/edit.js: change both together.
 */
object ShareCard {
    enum class Kind { INVITE }

    private const val GROUP = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"

    /** "Join Jakim's B'day wishlist". A name that already ends in "wishlist" doesn't get it twice. */
    fun headline(kind: Kind, listName: String): String {
        val n = listName.trim().replace(Regex("(?i)\\s*(wish\\s?list|verlanglijst(je)?)$"), "").trim()
        return when (kind) {
            Kind.INVITE -> if (n.isEmpty()) tr("Join our wishlist") else tr("Join %s wishlist", n)
        }
    }

    private fun subline(kind: Kind) = when (kind) {
        Kind.INVITE -> tr("Add gift ideas together and see what’s already reserved.")
    }

    /** The picture: 1200 x 630, like the link previews. */
    fun draw(c: Context, kind: Kind, listName: String): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(0xFF4355B9.toInt())
        val icon = PathParser.createPathFromPathData(GROUP)
        cv.save()
        cv.translate(120f, 165f)
        cv.scale(12.5f, 12.5f)
        cv.drawPath(icon, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        cv.restore()

        val face = try {
            ResourcesCompat.getFont(c, R.font.google_sans)
        } catch (e: Exception) {
            null
        } ?: Typeface.DEFAULT
        val width = 600
        fun layout(text: String, paint: TextPaint) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.05f).build()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); typeface = face }
        var size = 92f
        var tl: StaticLayout
        // Long names get a smaller font, up to three lines.
        while (true) {
            title.textSize = size
            tl = layout(headline(kind, listName), title)
            if (tl.lineCount <= 3 || size <= 48f) break
            size -= 4f
        }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E6FF.toInt(); typeface = face; textSize = 31f }
        val sl = layout(subline(kind), sub)
        val gap = 22f
        val top = ((630 - (tl.height + gap + sl.height)) / 2f).coerceAtLeast(24f)
        cv.save()
        cv.translate(516f, top)
        tl.draw(cv)
        cv.translate(0f, tl.height + gap)
        sl.draw(cv)
        cv.restore()
        return bmp
    }

    /** Opens the share sheet with the picture, "Join … wishlist 🎁" and the link. Falls back to the link alone. */
    suspend fun share(c: Context, kind: Kind, listName: String, link: String) {
        val text = headline(kind, listName) + " 🎁\n" + link
        val uri = try {
            withContext(Dispatchers.Default) {
                val dir = File(c.cacheDir, "share").apply { mkdirs() }
                val f = File(dir, "wishlist.png")
                val bmp = draw(c, kind, listName)
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(c, c.packageName + ".files", f)
            }
        } catch (e: Exception) {
            null
        }
        if (uri == null) {
            shareText(c, headline(kind, listName), text)
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(send, headline(kind, listName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
