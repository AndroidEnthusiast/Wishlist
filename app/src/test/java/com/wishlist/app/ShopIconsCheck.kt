package com.wishlist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shop icon check (needs the internet): runs the app's own icon search ([ShopIcons.declared]) for the 20 starter
 * shops, then tries the addresses in the same order as [ShopIcon] (skipping empty-looking and too-small icons, keeping
 * the best small one) and draws what a phone would end up showing into build/outputs/shops/icons.png.
 * Run: gradle testDebugUnitTest --tests '*ShopIconsCheck*' -Pwl.shops
 * From a cloud computer some shops refuse requests that a phone at home gets answered, so a letter tile here is not
 * always a letter tile on the phone. If [ShopIcon]'s order changes, change [pick] too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopIconsCheck {
    private val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun load(url: String): Bitmap? = try {
        val k = URL(url).openConnection() as HttpURLConnection
        k.setRequestProperty("User-Agent", ua); k.connectTimeout = 6000; k.readTimeout = 6000; k.instanceFollowRedirects = true
        if (k.responseCode !in 200..299) null else k.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    data class Pick(val shop: Shop, val icon: Bitmap?, val from: String, val note: String)

    private fun pick(c: Context, s: Shop): Pick {
        val base = java.net.URI(s.url).let { "${it.scheme}://${it.host}" }
        val declared = runBlocking { ShopIcons.declared(c, s) }
        val tries = (declared + listOf("$base/apple-touch-icon.png", ShopIcons.google(s.host))).distinct()
        val notes = ArrayList<String>()
        var bestSmall: Pair<String, Bitmap>? = null
        for (u in tries) {
            val b = load(u)
            val label = when {
                u == ShopIcons.google(s.host) -> "google"
                u.endsWith("/apple-touch-icon.png") && u !in declared -> "apple-touch"
                else -> "page"
            }
            if (b == null) { notes.add("$label: no picture"); continue }
            if (ShopIcons.looksEmpty(b)) { notes.add("$label: empty"); continue }
            if (b.width < ShopIcons.SHARP) {
                notes.add("$label: ${b.width}px")
                if (bestSmall == null || b.width > bestSmall.second.width) bestSmall = label to b
                continue
            }
            return Pick(s, b, "$label ${b.width}px", notes.joinToString(", "))
        }
        bestSmall?.let { return Pick(s, it.second, "${it.first} ${it.second.width}px (small)", notes.joinToString(", ")) }
        return Pick(s, null, "letter tile", notes.joinToString(", "))
    }

    @Test fun icons() {
        Assume.assumeTrue("needs -Pwl.shops", System.getProperty("wl.shops") == "1")
        val c = ApplicationProvider.getApplicationContext<Context>()
        val picks = Shops.starters.map { pick(c, it) }
        val cell = 150; val cols = 4; val rows = (picks.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * cell * 2, rows * cell, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out); cv.drawColor(Color.WHITE)
        val grey = Paint().apply { color = Color.rgb(238, 238, 238); isAntiAlias = true }
        val text = Paint().apply { color = Color.BLACK; textSize = 20f; isAntiAlias = true }
        val small = Paint().apply { color = Color.DKGRAY; textSize = 15f; isAntiAlias = true }
        picks.forEachIndexed { i, p ->
            val x = (i % cols) * cell * 2f + 10; val y = (i / cols) * cell + 10f
            cv.drawRoundRect(RectF(x, y, x + 100, y + 100), 20f, 20f, grey)
            if (p.icon != null) cv.drawBitmap(p.icon, null, RectF(x + 14, y + 14, x + 86, y + 86), Paint().apply { isFilterBitmap = true })
            else cv.drawText(p.shop.name.take(1), x + 40, y + 62, text)
            cv.drawText(p.shop.name, x + 110, y + 30, text)
            cv.drawText(p.from, x + 110, y + 55, small)
            println("SHOP ${p.shop.name}: ${p.from}" + if (p.note.isNotEmpty()) " (tried: ${p.note})" else "")
        }
        val f = File("build/outputs/shops/icons.png"); f.parentFile?.mkdirs()
        f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
