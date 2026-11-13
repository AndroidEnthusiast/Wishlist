package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * British Punk, rebuilt as a deep theme (owner, 13 November 2026, the third after Doodle and Y2K). Three sub-themes,
 * each with its own page, colours, shapes and drawings, in light and dark:
 *  - zine:   Photocopy zine: black-and-white photocopy, newsprint scraps, halftone, staples, one red ink.
 *  - gig:    Gig poster: fluorescent flyers wheat-pasted on a wall (concrete by day, a night wall in dark), torn layers.
 *  - tartan: Safety pin & tartan: red tartan, safety pins and studs, leather-black panels in dark.
 * Own parts: cut-out buttons (paper strips taped down, a little crooked), stamped chips, safety pins, staples and tape
 * on cards, ransom-note screen titles. Card styles: cut-out (torn paper with a shadow), taped (a flyer taped at its
 * corners), badge (a bold block, a thick frame and a stud). Everything drawn on cards is made once per size.
 */

private fun c(v: Long) = Color(v)

/** Punk's look for sub-theme [set], decoration [deco] (0 calm, 1 normal, 2 wild) and card style [cards]. */
internal fun punkLook(dark: Boolean, set: String, deco: Int, cards: String): Look {
    class P(
        val bg: Long, val surface: Long, val ink: Long, val muted: Long, val primary: Long, val onPrimary: Long,
        val container: Long, val fill: Long, val onFill: Long, val outlineVariant: Long,
        val notes: List<Long>, val tints: List<Long>, val blobs: List<Pair<Long, Float>>, val art: List<Long>, val shadow: Long
    )
    val p = when (set) {
        "gig" -> if (!dark) P(
            0xFFDAD7D1, 0xFFFFFFFF, 0xFF111111, 0xFF383838, 0xFFA3005F, 0xFFFFFFFF, 0xFFFFD6EC, 0xFFFF3EA5, 0xFF111111, 0xFFC4C0B8,
            listOf(0xFFFFF04D, 0xFFFF9AD0, 0xFF9DF27A, 0xFFFFB84D), listOf(0xFFFFF7A8, 0xFFFFD6EC, 0xFFD8F7C8, 0xFFFFE0B3),
            listOf(0xFF111111 to 0.10f, 0xFFFF3EA5 to 0.18f, 0xFFFFF04D to 0.35f, 0xFFC9C5BE to 1f),
            listOf(0xFFFF3EA5, 0xFF111111, 0xFFFFF04D, 0xFF2D7DFF, 0xFFFFFFFF), 0xFF111111
        ) else P(
            0xFF1A1A1C, 0xFF262628, 0xFFF2F0EA, 0xFFBDBAB2, 0xFFFF7AC4, 0xFF1A1A1C, 0xFF4A2038, 0xFFFF3EA5, 0xFF111111, 0xFF3A3A3C,
            listOf(0xFF4A4416, 0xFF4A2038, 0xFF2A4420, 0xFF4A3416), listOf(0xFF3A3A1C, 0xFF3A2030, 0xFF26381E, 0xFF3A2E1C),
            listOf(0xFFF2F0EA to 0.08f, 0xFFFF3EA5 to 0.22f, 0xFFFFF04D to 0.14f, 0xFF242426 to 1f),
            listOf(0xFFFF3EA5, 0xFFF2F0EA, 0xFFFFF04D, 0xFF2D7DFF, 0xFF262628), 0xFFFF3EA5
        )
        "tartan" -> if (!dark) P(
            0xFFF1ECE6, 0xFFFFFFFF, 0xFF141414, 0xFF3E3A36, 0xFFA3141C, 0xFFFFFFFF, 0xFFF3D3D3, 0xFFB3141C, 0xFFFFFFFF, 0xFFD8D0C6,
            listOf(0xFFFFFFFF, 0xFFF7EAEA, 0xFFEDF1EA, 0xFFF4EEDD), listOf(0xFFF3D3D3, 0xFFDCE6D8, 0xFFEFE6CC, 0xFFE2DED8),
            listOf(0xFFB3141C to 0.08f, 0xFF1E5A32 to 0.08f, 0xFF141414 to 0.06f, 0xFF141414 to 0.10f),
            listOf(0xFFB3141C, 0xFF141414, 0xFFC9CDD3, 0xFF1E5A32, 0xFFFFFFFF), 0xFF141414
        ) else P(
            0xFF141414, 0xFF1F1D1C, 0xFFF2EEE8, 0xFFC2BCB4, 0xFFFF6B6B, 0xFF141414, 0xFF3A1C1E, 0xFFC8202A, 0xFFFFFFFF, 0xFF333130,
            listOf(0xFF1F1D1C, 0xFF2E1E1E, 0xFF1E2A22, 0xFF2C2820), listOf(0xFF3A1C1E, 0xFF1E3326, 0xFF33301E, 0xFF2A2827),
            listOf(0xFFC8202A to 0.14f, 0xFF2E7A48 to 0.12f, 0xFFF2EEE8 to 0.05f, 0xFFF2EEE8 to 0.10f),
            listOf(0xFFC8202A, 0xFFF2EEE8, 0xFFC9CDD3, 0xFF2E7A48, 0xFF1F1D1C), 0xFFC8202A
        )
        else -> if (!dark) P(
            0xFFECEAE4, 0xFFFAFAF7, 0xFF111111, 0xFF3D3D3D, 0xFFB5101A, 0xFFFFFFFF, 0xFFF3D3D3, 0xFFD7141A, 0xFFFFFFFF, 0xFFD5D2CA,
            listOf(0xFFF2F0E8, 0xFFE8E4D8, 0xFFEFEDE6, 0xFFF6F3EA), listOf(0xFFE2E0DA, 0xFFF1D9D6, 0xFFE7E4D3, 0xFFDADDE8),
            listOf(0xFF111111 to 0.10f, 0xFFD7141A to 0.16f, 0xFFD9D6CE to 0.6f, 0xFF111111 to 0.06f),
            listOf(0xFFD7141A, 0xFF111111, 0xFFFFD60A, 0xFF1B4DFF, 0xFFFAFAF7), 0xFF111111
        ) else P(
            0xFF121212, 0xFF1E1E1E, 0xFFF2F0EA, 0xFFBDBAB2, 0xFFFF6B6B, 0xFF121212, 0xFF3A1A1C, 0xFFD7141A, 0xFFFFFFFF, 0xFF333333,
            listOf(0xFF1E1E1E, 0xFF262420, 0xFF22221E, 0xFF2A2622), listOf(0xFF2A2A2A, 0xFF3A1E20, 0xFF33301E, 0xFF22263A),
            listOf(0xFFF2F0EA to 0.10f, 0xFFD7141A to 0.25f, 0xFF2A2A2A to 0.8f, 0xFFF2F0EA to 0.06f),
            listOf(0xFFD7141A, 0xFFF2F0EA, 0xFFFFD60A, 0xFF1B4DFF, 0xFF1E1E1E), 0xFFD7141A
        )
    }
    val ink = c(p.ink)
    return Look(
        "punk", dark,
        lookScheme(dark, c(p.primary), c(p.onPrimary), c(p.container), ink, c(p.bg), c(p.surface), ink, c(p.muted), ink, c(p.outlineVariant)),
        // Taped flyers and badges take the sub-theme's paper colours; cut-outs are plain paper.
        if (cards == "cutout") listOf(c(p.surface)) else p.notes.map(::c),
        p.tints.map(::c), ink, listOf(c(p.shadow).copy(alpha = 0.85f)),
        p.blobs.map { (h, a) -> c(h).copy(alpha = a) },
        punkDisplay, if (dark) c(0xFF4CD98A) else c(0xFF1F8A4C), if (dark) c(0xFFFF7B6E) else c(0xFFC4321F),
        accent = c(p.fill), onAccent = c(p.onFill), art = p.art.map(::c),
        set = set, deco = deco, cardStyle = cards
    )
}

/** Punk's card shape: torn paper (cut-out), a straight sheet (taped flyer), a squared block (badge). */
internal fun Look.punkCardShape(id: Long, small: Boolean): Shape = when (cardStyle) {
    "taped" -> RoughShape(Math.floorMod(id, 97L).toInt() + 31, 0.8.dp, 14.dp)
    "badge" -> androidx.compose.foundation.shape.RoundedCornerShape(if (small) 4.dp else 6.dp)
    else -> RoughShape(Math.floorMod(id, 97L).toInt() + 71, if (small) 2.6.dp else 3.dp, 6.dp)
}

/**
 * The shadow under a Punk surface: a hard shadow for cut-outs, badges and buttons; a taped flyer curls a little at its
 * bottom corners instead. Made once per size.
 */
internal fun Modifier.punkShadow(look: Look, shape: Shape, raised: Boolean, depth: Float, card: Boolean): Modifier {
    if (!raised) return this
    val o = (depth * 0.6f).coerceIn(2.5f, 5f).dp
    if (!(card && look.cardStyle == "taped")) return hardShadow(look.shadows[0], shape, o)
    return drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val col = Color.Black.copy(alpha = if (look.dark) 0.5f else 0.22f)
        val left = Path().apply { moveTo(4 * u, h - 14 * u); lineTo(w * 0.3f, h - 2 * u); lineTo(w * 0.3f, h + 3 * u); lineTo(2 * u, h + 5 * u); close() }
        val right = Path().apply { moveTo(w - 4 * u, h - 14 * u); lineTo(w * 0.7f, h - 2 * u); lineTo(w * 0.7f, h + 3 * u); lineTo(w - 2 * u, h + 5 * u); close() }
        onDrawBehind { drawPath(left, col); drawPath(right, col) }
    }
}

/** A Punk surface's line: thick for badges (with a stud), the ink line for everything else. Made once per size. */
internal fun Modifier.punkFinish(look: Look, shape: Shape, card: Boolean): Modifier = drawWithCache {
    val badge = card && look.cardStyle == "badge"
    val w = (if (badge) 3.5.dp else look.edge).toPx()
    val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    val st = Stroke(w, join = StrokeJoin.Round)
    val u = 1.dp.toPx()
    val button = !card && size.height < 64.dp.toPx() && size.width > 60.dp.toPx()
    onDrawWithContent {
        drawContent()
        translate(w / 2, w / 2) { drawOutline(outline, look.line, style = st) }
        // Cut-out buttons: a bit of tape across their left end.
        if (button) tape(Offset(3 * u, size.height * 0.25f), -55f, Color(0xFFF2E6B8).copy(alpha = 0.8f), 22.dp, 9.dp)
        if (badge) {
            // A stud in the top right corner.
            val at = Offset(size.width - 12 * u, 12 * u)
            drawCircle(Color(0xFF8E939A), 5 * u, at)
            drawCircle(Color.White.copy(alpha = 0.8f), 1.8f * u, at - Offset(1.5f * u, 1.5f * u))
            drawCircle(look.line, 5 * u, at, style = Stroke(1.2f * u))
        }
    }
}

/**
 * What holds a Punk card on the page: staples (zine), tape (gig poster; a taped flyer at all four corners) or a safety
 * pin (tartan). Wild: more of them. Calm: only a taped flyer's own tape. Made once per size.
 */
internal fun Modifier.punkFasteners(look: Look, id: Long): Modifier = drawWithCache {
    val u = 1.dp.toPx()
    val k = Math.floorMod(id, 5L).toInt()
    val w = size.width; val h = size.height
    val wild = look.deco == 2
    val taped = look.cardStyle == "taped"
    val tapeCol = Color(0xFFF2E6B8).copy(alpha = 0.78f)
    onDrawWithContent {
        drawContent()
        if (taped) {
            tape(Offset(8 * u, 4 * u), -40f, tapeCol); tape(Offset(w - 8 * u, 4 * u), 40f, tapeCol)
            tape(Offset(8 * u, h - 4 * u), 40f, tapeCol); tape(Offset(w - 8 * u, h - 4 * u), -40f, tapeCol)
        }
        if (look.deco == 0) return@onDrawWithContent
        when (look.set) {
            "gig" -> if (!taped) {
                tape(Offset(w * (0.3f + k * 0.08f), 0f), listOf(-5f, 4f, -2f, 6f, -4f)[k], tapeCol)
                if (wild) tape(Offset(w * 0.85f, h), 8f, tapeCol)
            }
            "tartan" -> {
                safetyPin(Offset(18 * u, 2 * u), -12f, u * 1.4f)
                if (wild) safetyPin(Offset(w - 60 * u, h - 4 * u), 14f, u * 1.4f)
            }
            else -> {
                staple(Offset(14 * u + k * 4 * u, 8 * u), -8f, u)
                if (wild) { staple(Offset(w - 30 * u, 8 * u), 10f, u); tape(Offset(w * 0.5f, h), -3f, tapeCol) }
            }
        }
    }
}

/** A staple: a short grey bar with its two legs. */
private fun DrawScope.staple(at: Offset, angle: Float, u: Float) = rotate(angle, at) {
    drawLine(Color.Black.copy(alpha = 0.35f), at + Offset(1f * u, 2f * u), at + Offset(23 * u, 2f * u), 2.6f * u, StrokeCap.Round)
    drawLine(Color(0xFF5E636B), at, at + Offset(22 * u, 0f), 3f * u, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.6f), at + Offset(2 * u, -0.6f * u), at + Offset(20 * u, -0.6f * u), 0.8f * u, StrokeCap.Round)
}

/** A safety pin: a long loop with a coiled end and a clasp. */
internal fun DrawScope.safetyPin(at: Offset, angle: Float, u: Float) = rotate(angle, at) {
    val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val metal = Color(0xFF6E737A)
    val p = Path().apply {
        moveTo(at.x + 4 * u, at.y)
        lineTo(at.x + 30 * u, at.y)
        quadraticTo(at.x + 36 * u, at.y + 3 * u, at.x + 30 * u, at.y + 6 * u)
        lineTo(at.x + 4 * u, at.y + 6 * u)
    }
    drawPath(p, metal, style = st)
    drawCircle(metal, 3 * u, Offset(at.x + 2 * u, at.y + 3 * u), style = Stroke(1.6f * u))
    drawRect(Color(0xFF6E7379), Offset(at.x + 28 * u, at.y - 2 * u), Size(6 * u, 4 * u))
}

/**
 * Punk's page per sub-theme, made once per size: zine photocopy specks, halftone and newsprint scraps with red marks;
 * gig poster a wall (concrete bricks) with torn fluorescent flyer layers; tartan a faint tartan with safety pins and
 * studs along the edge. Calm: only the paper or wall; Wild: more.
 */
internal fun Modifier.punkBackdrop(look: Look): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val u = 1.dp.toPx()
    val n = look.deco
    val specks = if (look.set == "tartan") emptyList() else grainPoints(w, h, if (n == 2) 4200 else 2800, 83)
    fun scrap(x: Float, y: Float, sw: Float, sh: Float, seed: Int): Path {
        val o = RoughShape(seed, 4.dp, 9.dp).createOutline(Size(sw, sh), androidx.compose.ui.unit.LayoutDirection.Ltr, this)
        return Path().apply { addOutline(o); translate(Offset(x, y)) }
    }
    val scraps = buildList {
        if (n >= 1) { add(scrap(w * 0.62f, -20 * u, 180 * u, 90 * u, 3)); add(scrap(-40 * u, h * 0.46f, 150 * u, 120 * u, 5)) }
        if (n >= 2) { add(scrap(w * 0.55f, h - 110 * u, 200 * u, 80 * u, 9)); add(scrap(w * 0.7f, h * 0.3f, 120 * u, 70 * u, 13)) }
    }
    onDrawBehind {
        when (look.set) {
            "gig" -> {
                // Bricks, then torn flyer layers.
                val bh = 26 * u; val bw = 64 * u
                var y = 0f; var row = 0
                while (y < h) {
                    drawLine(look.blobs[3], Offset(0f, y), Offset(w, y), 1.2f * u)
                    var x = if (row % 2 == 0) 0f else bw / 2
                    while (x < w) { drawLine(look.blobs[3], Offset(x, y), Offset(x, y + bh), 1.2f * u); x += bw }
                    y += bh; row++
                }
                scraps.forEachIndexed { i, s -> rotate(if (i % 2 == 0) 6f else -7f, Offset(w / 2, h / 2)) { drawPath(s, if (i % 2 == 0) look.blobs[2] else look.blobs[1]) } }
                drawPoints(specks, PointMode.Points, look.blobs[0], strokeWidth = 2f * u)
            }
            "tartan" -> {
                // A faint tartan: red and green bands, thin dark lines across them.
                var x = 0f; var i = 0
                while (x < w) { drawRect(if (i % 2 == 0) look.blobs[0] else look.blobs[1], Offset(x, 0f), Size(22 * u, h)); x += 48 * u; i++ }
                var y = 0f; i = 0
                while (y < h) { drawRect(if (i % 2 == 0) look.blobs[0] else look.blobs[1], Offset(0f, y), Size(w, 22 * u)); y += 48 * u; i++ }
                x = 11 * u; while (x < w) { drawLine(look.blobs[2], Offset(x, 0f), Offset(x, h), 1f * u); x += 24 * u }
                y = 11 * u; while (y < h) { drawLine(look.blobs[2], Offset(0f, y), Offset(w, y), 1f * u); y += 24 * u }
                if (n >= 1) { safetyPin(Offset(w * 0.7f, 40 * u), 20f, u * 1.4f); safetyPin(Offset(14 * u, h * 0.6f), -30f, u * 1.4f) }
                if (n >= 2) safetyPin(Offset(w * 0.5f, h * 0.9f), 8f, u * 1.4f)
                // Studs along the right edge (where text never goes).
                var sy = 30 * u
                while (sy < h) { drawCircle(Color(0xFF8E939A).copy(alpha = 0.9f), 2.6f * u, Offset(w - 5 * u, sy)); sy += 22 * u }
            }
            else -> {
                scraps.forEachIndexed { i, s -> rotate(if (i % 2 == 0) 6f else -8f, Offset(w / 2, h / 2)) { drawPath(s, look.blobs[2]) } }
                drawPoints(specks, PointMode.Points, look.blobs[0], strokeWidth = 2.4f * u)
                if (n >= 1) {
                    // Red marker crosses and a circled spot, like marks on a zine page.
                    for ((cx, cy) in listOf(w * 0.9f to h * 0.2f, w * 0.08f to h * 0.82f)) {
                        drawLine(look.blobs[1], Offset(cx - 10 * u, cy - 10 * u), Offset(cx + 10 * u, cy + 10 * u), 3 * u, StrokeCap.Round)
                        drawLine(look.blobs[1], Offset(cx + 10 * u, cy - 10 * u), Offset(cx - 10 * u, cy + 10 * u), 3 * u, StrokeCap.Round)
                    }
                }
                // A red strip and a black line down the right edge (where text never goes).
                drawRect(look.art[0], Offset(w - 7 * u, 0f), Size(4 * u, h))
                drawRect(look.art[1], Offset(w - 12 * u, 0f), Size(2 * u, h))
            }
        }
    }
}

private fun grainPoints(w: Float, h: Float, n: Int, seed: Int): List<Offset> {
    val out = ArrayList<Offset>(n)
    var k = seed
    fun r(): Float { k++; val v = kotlin.math.sin(k * 12.9898 + seed * 4.1414) * 43758.5453; return (v - kotlin.math.floor(v)).toFloat() }
    while (out.size < n) {
        val x = r() * w; val y = r() * h
        val dx = minOf(x, w - x) / (w / 2); val dy = minOf(y, h - y) / (h / 2)
        if (r() < minOf(dx, dy) * 1.6f) continue
        out.add(Offset(x, y))
    }
    return out
}

/** Punk's emblem per sub-theme: a stapled newsprint scrap, a taped flyer, a safety pin through tartan. */
internal fun DrawScope.punkEmblem(look: Look) {
    val s = size.minDimension
    val c = center
    val u = s / 48f
    when (look.set) {
        "gig" -> {
            rotate(-7f, c) {
                drawRect(look.art[2], Offset(c.x - s * 0.36f, c.y - s * 0.4f), Size(s * 0.72f, s * 0.8f))
                drawRect(Color(0xFF111111), Offset(c.x - s * 0.36f, c.y - s * 0.4f), Size(s * 0.72f, s * 0.8f), style = Stroke(s * 0.03f))
            }
            tape(Offset(c.x, c.y - s * 0.4f), 6f, Color(0xFFF2E6B8).copy(alpha = 0.85f))
        }
        "tartan" -> {
            rotate(-6f, c) {
                drawRect(look.art[0], Offset(c.x - s * 0.38f, c.y - s * 0.38f), Size(s * 0.76f, s * 0.76f))
                for (i in 0 until 3) {
                    drawRect(Color.Black.copy(alpha = 0.3f), Offset(c.x - s * 0.38f + i * s * 0.28f, c.y - s * 0.38f), Size(s * 0.08f, s * 0.76f))
                    drawRect(Color.Black.copy(alpha = 0.3f), Offset(c.x - s * 0.38f, c.y - s * 0.38f + i * s * 0.28f), Size(s * 0.76f, s * 0.08f))
                }
            }
            safetyPin(Offset(c.x - s * 0.36f, c.y - s * 0.46f), -15f, u * 1.6f)
        }
        else -> {
            rotate(-8f, c) {
                drawRect(Color(0xFFF2F0E8), Offset(c.x - s * 0.38f, c.y - s * 0.34f), Size(s * 0.76f, s * 0.7f))
                for (i in 0 until 5) drawLine(Color(0xFF9A978F), Offset(c.x - s * 0.3f, c.y - s * 0.2f + i * s * 0.11f), Offset(c.x + s * 0.3f, c.y - s * 0.2f + i * s * 0.11f), s * 0.03f)
                drawRect(Color(0xFF111111), Offset(c.x - s * 0.38f, c.y - s * 0.34f), Size(s * 0.76f, s * 0.7f), style = Stroke(s * 0.03f))
            }
            staple(Offset(c.x - s * 0.3f, c.y - s * 0.36f), -8f, u * 1.6f)
        }
    }
}

/** Punk's emblem's icon colour: dark ink on its paper. */
internal fun punkEmblemInk(look: Look): Color = if (look.set == "tartan") Color.White else Color(0xFF111111)

/**
 * A screen title in the chosen style. Punk (owner, 13 November 2026): a ransom note, every letter cut from another
 * page; read out as the plain words. Long titles (over 18 letters) stay plain, so they stay readable. Other styles:
 * the title as before.
 */
@Composable
fun ScreenTitle(text: String) {
    val look = LocalLook.current
    if (look.design != "punk" || text.length > 18) {
        Text(text, fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    RansomNote(text, LocalTextStyle.current.fontSize.let { MaterialTheme.typography.titleLarge })
}

/** Letters cut out of different pages: each on its own scrap, turned a little, in a different type. */
@Composable
fun RansomNote(text: String, style: androidx.compose.ui.text.TextStyle) {
    val look = LocalLook.current
    val papers = listOf(look.art[0], Color(0xFFFAFAF7), Color(0xFF111111), look.art[2], Color(0xFFF2F0E8), look.art[3], Color(0xFF111111), look.art[0])
    val faces: List<FontFamily?> = listOf(null, FontFamily.Serif, FontFamily.Monospace, FontFamily.SansSerif)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = text; heading() }, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            if (ch == ' ') { Text(" ", style = style); return@forEachIndexed }
            val paper = papers[(i * 3 + text.length) % papers.size]
            Text(
                ch.toString(), color = if (paper.luminance() > 0.5f) Color(0xFF111111) else Color.White,
                style = style.copy(fontFamily = faces[i % faces.size] ?: style.fontFamily, fontWeight = if (i % 2 == 0) FontWeight.Bold else FontWeight.Normal),
                modifier = Modifier.padding(horizontal = 0.5.dp).rotate(listOf(-6f, 4f, -3f, 7f, -5f, 3f, -7f, 5f)[i % 8])
                    .background(paper).padding(horizontal = 2.dp)
            )
        }
    }
}
