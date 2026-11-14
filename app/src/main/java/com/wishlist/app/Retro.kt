package com.wishlist.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Retro vintage, rebuilt as a deep theme (owner, 14 November 2026, the fourth). Three sub-themes, each with its own
 * page, colours, shapes and drawings, in light and dark:
 *  - diner:   50s diner: cherry red and turquoise, a checkerboard, starbursts and boomerangs; the diner at night in dark.
 *  - groovy:  70s groovy: orange, mustard and brown, rainbow arcs, daisies and waves; wood panelling in dark.
 *  - travel:  Vintage travel poster: faded teal, coral and sand, sun rays, waves, fold lines; a night sea in dark.
 * Own parts: ticket buttons with notched ends, luggage-tag chips, stamps and badges on cards (a starburst price tag, a
 * daisy, a postmark), sign-painter icons (RetroIcons). Card styles: ticket, postcard (white border, a stamp), enamel sign
 * (a rim and four screws). Everything drawn on cards is made once per size (drawWithCache).
 */

private fun c(v: Long) = Color(v)

/** Retro's look for sub-theme [set], decoration [deco] (0 calm, 1 normal, 2 wild) and card style [cards]. */
internal fun retroLook(dark: Boolean, set: String, deco: Int, cards: String): Look {
    class P(
        val bg: Long, val surface: Long, val ink: Long, val muted: Long, val primary: Long, val onPrimary: Long,
        val container: Long, val fill: Long, val onFill: Long, val outlineVariant: Long, val notes: List<Long>,
        val tints: List<Long>, val blobs: List<Pair<Long, Float>>, val art: List<Long>, val shadow: Long
    )
    val p = when (set) {
        "groovy" -> if (!dark) P(
            0xFFF6E9D2, 0xFFFFF6E6, 0xFF3A2312, 0xFF6A4A30, 0xFFA8460F, 0xFFFFF6E6, 0xFFF7D9BC, 0xFFB5541A, 0xFFFFF6E6, 0xFFE2CDAE,
            listOf(0xFFFFF6E6, 0xFFFBE6C8, 0xFFF3EBC8), listOf(0xFFF7D9BC, 0xFFF3E1A8, 0xFFE6E2C2, 0xFFEBD6C4),
            listOf(0xFFE07A2E to 0.18f, 0xFFE5A93A to 0.22f, 0xFF7A4A22 to 0.10f, 0xFF8A8A2E to 0.14f),
            listOf(0xFFE07A2E, 0xFFE5A93A, 0xFFB5541A, 0xFF7A4A22, 0xFF8A8A2E), 0xFFE07A2E
        ) else P(
            0xFF2A1C12, 0xFF3A281A, 0xFFF6E9D2, 0xFFD2BFA3, 0xFFF2A65A, 0xFF2A1C12, 0xFF4A3220, 0xFFE5A93A, 0xFF2A1C12, 0xFF4A3828,
            listOf(0xFF3A281A, 0xFF44301C, 0xFF3A341C), listOf(0xFF4A3220, 0xFF4A3E1C, 0xFF3A3A24, 0xFF4A2E22),
            listOf(0xFFE07A2E to 0.14f, 0xFFE5A93A to 0.12f, 0xFF120A04 to 0.25f, 0xFF8A8A2E to 0.12f),
            listOf(0xFFE07A2E, 0xFFE5A93A, 0xFFB5541A, 0xFFF6E9D2, 0xFF8A8A2E), 0xFFE07A2E
        )
        "travel" -> if (!dark) P(
            0xFFEFE6D2, 0xFFFBF6EA, 0xFF1F3A44, 0xFF4A5E62, 0xFF9C3B28, 0xFFFBF6EA, 0xFFD6E6E2, 0xFF2F6F73, 0xFFFBF6EA, 0xFFD9CDB2,
            listOf(0xFFFBF6EA, 0xFFEAF2EE, 0xFFF6E8DC), listOf(0xFFD6E6E2, 0xFFF4DCCD, 0xFFEFE0BE, 0xFFDCE8EA),
            listOf(0xFFE07A5F to 0.14f, 0xFF6FA8A0 to 0.16f, 0xFFD9C7A0 to 0.5f, 0xFF1F3A44 to 0.06f),
            listOf(0xFFE07A5F, 0xFF2F6F73, 0xFFE3C48C, 0xFF9CC7C9, 0xFF1F3A44), 0xFFE07A5F
        ) else P(
            0xFF102A30, 0xFF183A42, 0xFFEFE6D2, 0xFFBFC9C2, 0xFFF2A08F, 0xFF102A30, 0xFF24464C, 0xFFE3C48C, 0xFF102A30, 0xFF24464E,
            listOf(0xFF183A42, 0xFF1C4048, 0xFF243A3E), listOf(0xFF24464C, 0xFF3A2E2A, 0xFF3A3A26, 0xFF1E4248),
            listOf(0xFFE07A5F to 0.12f, 0xFF6FA8A0 to 0.14f, 0xFF0A1E22 to 0.4f, 0xFFEFE6D2 to 0.06f),
            listOf(0xFFE07A5F, 0xFF6FA8A0, 0xFFE3C48C, 0xFF9CC7C9, 0xFFEFE6D2), 0xFFE07A5F
        )
        else -> if (!dark) P(
            0xFFF4EEDF, 0xFFFFFDF6, 0xFF2A1A12, 0xFF5A463A, 0xFFA3201A, 0xFFFFF6E5, 0xFFF6D8D2, 0xFFC62828, 0xFFFFF6E5, 0xFFE0D4BC,
            listOf(0xFFFFFDF6, 0xFFE6F4F2, 0xFFFBE6E2), listOf(0xFFF6D8D2, 0xFFD3EEEB, 0xFFF4E3B4, 0xFFEFE6D6),
            listOf(0xFF2A1A12 to 0.05f, 0xFF2A9D96 to 0.18f, 0xFFC62828 to 0.12f, 0xFFE5B54A to 0.25f),
            listOf(0xFFC62828, 0xFF2A9D96, 0xFFE5B54A, 0xFFFFFDF6, 0xFF2A1A12), 0xFF2A9D96
        ) else P(
            0xFF14181C, 0xFF1F262C, 0xFFF6EEDF, 0xFFC9BFAE, 0xFFFF8A80, 0xFF14181C, 0xFF3A2226, 0xFFFF5A5F, 0xFF14181C, 0xFF2E363C,
            listOf(0xFF1F262C, 0xFF1E3236, 0xFF3A2226), listOf(0xFF3A2226, 0xFF1E3A3A, 0xFF3A3420, 0xFF262C30),
            listOf(0xFFF6EEDF to 0.04f, 0xFF4FD1C5 to 0.16f, 0xFFFF5A5F to 0.16f, 0xFFE5B54A to 0.12f),
            listOf(0xFFFF5A5F, 0xFF4FD1C5, 0xFFE5B54A, 0xFF1F262C, 0xFFF6EEDF), 0xFF4FD1C5
        )
    }
    val ink = c(p.ink)
    return Look(
        "retro", dark,
        lookScheme(dark, c(p.primary), c(p.onPrimary), c(p.container), ink, c(p.bg), c(p.surface), ink, c(p.muted), ink, c(p.outlineVariant)),
        // Postcards and enamel signs take the sub-theme's paper colours; tickets are plain card.
        if (cards == "ticket") listOf(c(p.surface)) else p.notes.map(::c),
        p.tints.map(::c), ink, listOf(c(p.shadow)),
        p.blobs.map { (h, a) -> c(h).copy(alpha = a) },
        retroDisplay, if (dark) c(0xFF4CD98A) else c(0xFF1F8A4C), if (dark) c(0xFFFF7B6E) else c(0xFFC4321F),
        accent = c(p.fill), onAccent = c(p.onFill), art = p.art.map(::c),
        set = set, deco = deco, cardStyle = cards
    )
}

/** Retro's card shape: a notched ticket, a postcard's small corners, an enamel sign's round plate. */
internal fun Look.retroCardShape(small: Boolean): Shape = when (cardStyle) {
    "postcard" -> RoundedCornerShape(4.dp)
    "enamel" -> RoundedCornerShape(if (small) 16.dp else 20.dp)
    else -> TicketShape(if (small) 12.dp else 14.dp, if (small) 7.dp else 8.dp)
}

/**
 * A luggage tag (Retro's chosen chips): the left end cut to a point, the other corners round. Its hole is drawn by
 * [Modifier.retroTagHole].
 */
class TagShape(private val cut: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val k = with(density) { cut.toPx() }.coerceAtMost(size.height / 2)
        val r = size.height * 0.2f
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(k, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, h - r); quadraticTo(w, h, w - r, h)
            lineTo(k, h); lineTo(0f, h / 2); close()
        }
        return Outline.Generic(p)
    }
    override fun equals(other: Any?) = other is TagShape && other.cut == cut
    override fun hashCode() = cut.hashCode()
}

/** The hole of a luggage tag, near its pointed end, with a little string. */
internal fun Modifier.retroTagHole(look: Look): Modifier = drawWithCache {
    val u = 1.dp.toPx()
    val c0 = Offset(7 * u, size.height / 2)
    onDrawWithContent {
        drawContent()
        drawCircle(look.scheme.background, 2.4f * u, c0)
        drawCircle(look.line, 2.4f * u, c0, style = Stroke(1.1f * u))
    }
}

/** The shadow under a Retro surface: the misprinted colour shadow (the sub-theme's own colour). */
internal fun Modifier.retroShadow(look: Look, shape: Shape, raised: Boolean, depth: Float): Modifier =
    if (!raised) this else hardShadow(look.shadows[0], shape, (depth * 0.5f).coerceIn(2f, 4f).dp)

/**
 * What goes over a Retro surface: the line, then the card style's details (a ticket's printed keyline, a postcard's
 * white border and stamp, an enamel sign's rim and screws); buttons get a printed keyline. Made once per size.
 */
internal fun Modifier.retroFinish(look: Look, shape: Shape, card: Boolean): Modifier = drawWithCache {
    val u = 1.dp.toPx()
    val w = look.edge.toPx()
    val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    val i = 4 * u
    val inner = shape.createOutline(Size(size.width - 2 * i, size.height - 2 * i), layoutDirection, this)
    val keyline = Stroke(1 * u)
    val style = if (card) look.cardStyle else "button"
    val keyCol = look.line.copy(alpha = 0.55f)
    onDrawWithContent {
        if (style == "postcard") {
            // A white border round the card, inside its edge, before the content.
            drawRect(Color.White.copy(alpha = if (look.dark) 0.12f else 0.9f), Offset.Zero, size, style = Stroke(10 * u))
        }
        drawContent()
        translate(w / 2, w / 2) { drawOutline(outline, look.line, style = Stroke(w)) }
        when (style) {
            "postcard" -> {
                // A stamp sticking out over the top right corner (so it never covers text): perforated edge, a colour and
                // a wavy postmark across it.
                val s0 = Offset(size.width - 28 * u, -12 * u)
                val st = Size(20 * u, 24 * u)
                drawRect(Color.White, s0 - Offset(2 * u, 2 * u), Size(st.width + 4 * u, st.height + 4 * u))
                drawRect(look.art[1], s0, st)
                drawRect(look.line, s0 - Offset(2 * u, 2 * u), Size(st.width + 4 * u, st.height + 4 * u),
                    style = Stroke(1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * u, 2 * u))))
                for (k in 0 until 3) {
                    val y = s0.y + 6 * u + k * 5 * u
                    val p = Path().apply { moveTo(s0.x - 14 * u, y); quadraticTo(s0.x - 6 * u, y - 3 * u, s0.x + 2 * u, y); quadraticTo(s0.x + 10 * u, y + 3 * u, s0.x + 18 * u, y) }
                    drawPath(p, look.line.copy(alpha = 0.55f), style = Stroke(1.2f * u))
                }
            }
            "enamel" -> {
                // An inner rim and four screws.
                translate(i, i) { drawOutline(inner, look.line.copy(alpha = 0.7f), style = Stroke(1.5f * u)) }
                for (sc in listOf(Offset(12 * u, 12 * u), Offset(size.width - 12 * u, 12 * u), Offset(12 * u, size.height - 12 * u), Offset(size.width - 12 * u, size.height - 12 * u))) {
                    drawCircle(Color(0xFFB8B2A6), 3.5f * u, sc)
                    drawLine(Color(0xFF5E584E), sc - Offset(2.2f * u, 1 * u), sc + Offset(2.2f * u, 1 * u), 1 * u)
                }
            }
            else -> if (size.minDimension >= 36 * u) translate(i, i) { drawOutline(inner, keyCol, style = keyline) }
        }
    }
}

/**
 * Stamps and badges on Retro cards: a starburst price tag (diner), a daisy (groovy), a round postmark (travel); Wild adds
 * a second on the other side. Calm: none. Made once per size.
 */
internal fun Modifier.retroStamps(look: Look, id: Long): Modifier = if (look.deco == 0) this else drawWithCache {
    val u = 1.dp.toPx()
    val right = Math.floorMod(id, 2L) == 0L && look.cardStyle != "postcard"
    val first = Offset(if (right) size.width - 22 * u else 22 * u, 6 * u)
    val second = Offset(if (right) 24 * u else size.width - 24 * u, size.height - 10 * u)
    onDrawWithContent {
        drawContent()
        retroBadge(look, first, 16 * u, Math.floorMod(id, 3L).toInt())
        if (look.deco == 2) retroBadge(look, second, 13 * u, Math.floorMod(id + 1, 3L).toInt())
    }
}

private fun DrawScope.retroBadge(look: Look, c: Offset, r: Float, k: Int) {
    val ink = look.line
    when (look.set) {
        "groovy" -> {
            // A daisy: white petals round a mustard middle.
            for (i in 0 until 8) rotate(i * 45f, c) { drawOval(Color.White, Offset(c.x - r * 0.22f, c.y - r), Size(r * 0.44f, r * 0.7f)) }
            for (i in 0 until 8) rotate(i * 45f, c) { drawOval(ink, Offset(c.x - r * 0.22f, c.y - r), Size(r * 0.44f, r * 0.7f), style = Stroke(r * 0.06f)) }
            drawCircle(look.art[1], r * 0.32f, c); drawCircle(ink, r * 0.32f, c, style = Stroke(r * 0.07f))
        }
        "travel" -> {
            // A round postmark: two rings and wavy lines.
            val col = ink.copy(alpha = 0.6f)
            drawCircle(col, r, c, style = Stroke(r * 0.1f)); drawCircle(col, r * 0.7f, c, style = Stroke(r * 0.06f))
            drawLine(col, c - Offset(r * 0.5f, 0f), c + Offset(r * 0.5f, 0f), r * 0.08f)
            drawCircle(look.art[k % 2], r * 0.18f, c - Offset(0f, r * 0.35f))
        }
        else -> {
            // A starburst price tag.
            val p = Path().apply {
                for (i in 0 until 24) {
                    val a = Math.toRadians(i * 15.0); val rr = if (i % 2 == 0) r else r * 0.72f
                    val q = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat())
                    if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                }
                close()
            }
            drawPath(p, listOf(look.art[2], look.art[1], look.art[0])[k]); drawPath(p, ink, style = Stroke(r * 0.08f))
            drawCircle(Color.White, r * 0.28f, c); drawCircle(ink, r * 0.28f, c, style = Stroke(r * 0.06f))
        }
    }
}

/**
 * Retro's page per sub-theme, made once per size: the diner's checkerboard with starbursts and boomerangs; groovy's
 * rainbow arcs, daisies and waves (wood panels in dark); the travel poster's sun rays, waves and fold lines. Calm: only
 * the pattern or paper; Wild: more.
 */
internal fun Modifier.retroBackdrop(look: Look): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val u = 1.dp.toPx()
    val n = look.deco
    val grain = run {
        val out = ArrayList<Offset>(2200); var k = 7
        fun r(): Float { k++; val v = kotlin.math.sin(k * 12.9898 + 31.7) * 43758.5453; return (v - kotlin.math.floor(v)).toFloat() }
        repeat(2200) { out.add(Offset(r() * w, r() * h)) }; out
    }
    fun star(c: Offset, r: Float) = Path().apply {
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0); val rr = if (i % 2 == 0) r else r * 0.25f
            val q = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat())
            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
        close()
    }
    fun boomerang(c: Offset, s: Float) = Path().apply {
        moveTo(c.x - s, c.y); quadraticTo(c.x, c.y - s * 0.9f, c.x + s, c.y - s * 0.2f); quadraticTo(c.x, c.y - s * 0.4f, c.x - s, c.y); close()
    }
    onDrawBehind {
        when (look.set) {
            "groovy" -> {
                if (look.dark) { var x = 0f; while (x < w) { drawLine(look.blobs[2], Offset(x, 0f), Offset(x, h), 2 * u); x += 56 * u } }
                // Rainbow arcs in two corners.
                val cols = listOf(look.blobs[0], look.blobs[1], look.blobs[3])
                if (n >= 1) for ((ci, col) in cols.withIndex()) {
                    val r = (150 - ci * 26) * u
                    drawArc(col, 180f, 90f, false, Offset(w - r, h * 0.12f - r + 120 * u), Size(2 * r, 2 * r), style = Stroke(22 * u))
                    drawArc(col, 270f, 90f, false, Offset(-r, h * 0.78f - r), Size(2 * r, 2 * r), style = Stroke(22 * u))
                }
                if (n >= 2) for (k in 0 until 3) {
                    val y = h * (0.3f + k * 0.2f)
                    val p = Path().apply { moveTo(0f, y); var x = 0f; while (x < w) { quadraticTo(x + 20 * u, y - 10 * u, x + 40 * u, y); quadraticTo(x + 60 * u, y + 10 * u, x + 80 * u, y); x += 80 * u } }
                    drawPath(p, cols[k], style = Stroke(5 * u))
                }
                // Daisies at the edges.
                if (n >= 1) for (c0 in listOf(Offset(w * 0.08f, h * 0.2f), Offset(w * 0.92f, h * 0.62f))) {
                    for (i in 0 until 8) rotate(i * 45f, c0) { drawOval(look.blobs[1], Offset(c0.x - 5 * u, c0.y - 18 * u), Size(10 * u, 14 * u)) }
                    drawCircle(look.blobs[0], 6 * u, c0)
                }
                drawRect(look.art[0], Offset(0f, 0f), Size(4 * u, h)); drawRect(look.art[1], Offset(6 * u, 0f), Size(4 * u, h))
                drawRect(look.art[3], Offset(12 * u, 0f), Size(2 * u, h))
            }
            "travel" -> {
                // Sun rays from the top right, waves at the bottom, the poster's fold lines and grain.
                val c0 = Offset(w * 0.92f, 30 * u)
                if (n >= 1) for (i in 0 until 14) {
                    val a0 = Math.toRadians(90.0 + i * 8.0); val a1 = Math.toRadians(90.0 + i * 8.0 + 4.0)
                    val r = maxOf(w, h)
                    drawPath(Path().apply {
                        moveTo(c0.x, c0.y)
                        lineTo(c0.x + (kotlin.math.cos(a0) * r).toFloat(), c0.y + (kotlin.math.sin(a0) * r).toFloat())
                        lineTo(c0.x + (kotlin.math.cos(a1) * r).toFloat(), c0.y + (kotlin.math.sin(a1) * r).toFloat()); close()
                    }, look.blobs[0])
                }
                drawCircle(look.blobs[0], 46 * u, c0)
                for (k in 0 until (if (n >= 2) 5 else if (n >= 1) 3 else 0)) {
                    val y = h - 30 * u - k * 22 * u
                    val p = Path().apply { moveTo(0f, y); var x = 0f; while (x < w) { quadraticTo(x + 15 * u, y - 7 * u, x + 30 * u, y); quadraticTo(x + 45 * u, y + 7 * u, x + 60 * u, y); x += 60 * u } }
                    drawPath(p, look.blobs[1], style = Stroke(3 * u))
                }
                drawLine(look.blobs[3], Offset(w / 2, 0f), Offset(w / 2, h), 1.5f * u)
                drawLine(look.blobs[3], Offset(0f, h / 2), Offset(w, h / 2), 1.5f * u)
                drawPoints(grain, PointMode.Points, look.blobs[3], strokeWidth = 1.4f * u)
                drawRect(look.art[1], Offset(0f, 0f), Size(5 * u, h)); drawRect(look.art[0], Offset(7 * u, 0f), Size(2 * u, h))
            }
            else -> {
                // The diner: a checkerboard, starbursts and boomerangs, a cherry and turquoise edge.
                val sq = 28 * u
                var y = 0f; var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) 0f else sq
                    while (x < w) { drawRect(look.blobs[0], Offset(x, y), Size(sq, sq)); x += 2 * sq }
                    y += sq; row++
                }
                val stars = buildList {
                    if (n >= 1) addAll(listOf(Offset(w * 0.88f, 80 * u) to 18f, Offset(w * 0.1f, h * 0.45f) to 14f, Offset(w * 0.9f, h * 0.75f) to 16f))
                    if (n >= 2) addAll(listOf(Offset(w * 0.5f, 40 * u) to 10f, Offset(w * 0.2f, h * 0.88f) to 12f, Offset(w * 0.7f, h * 0.35f) to 9f))
                }
                stars.forEach { (c0, r) -> drawPath(star(c0, r * u), look.blobs[3]) }
                if (n >= 1) { drawPath(boomerang(Offset(w * 0.15f, 130 * u), 30 * u), look.blobs[1]); drawPath(boomerang(Offset(w * 0.8f, h * 0.55f), 26 * u), look.blobs[2]) }
                if (n >= 2) drawPath(boomerang(Offset(w * 0.4f, h * 0.7f), 24 * u), look.blobs[1])
                drawRect(look.art[0], Offset(0f, 0f), Size(5 * u, h)); drawRect(look.art[1], Offset(7 * u, 0f), Size(3 * u, h))
            }
        }
    }
}

/** Retro's emblem per sub-theme: a starburst with a cherry, a daisy, a luggage label with sun and sea. */
internal fun DrawScope.retroEmblem(look: Look) {
    val s = size.minDimension
    val c = center
    val a = look.art
    val ink = look.line
    when (look.set) {
        "groovy" -> {
            for (i in 0 until 10) rotate(i * 36f, c) { drawOval(a[1], Offset(c.x - s * 0.1f, c.y - s * 0.48f), Size(s * 0.2f, s * 0.32f)) }
            drawCircle(a[0], s * 0.22f, c)
        }
        "travel" -> {
            drawCircle(a[3], s * 0.44f, c)
            drawCircle(a[0], s * 0.16f, Offset(c.x, c.y - s * 0.06f))
            drawRect(a[1], Offset(c.x - s * 0.44f, c.y + s * 0.14f), Size(s * 0.88f, s * 0.12f))
            drawCircle(ink, s * 0.44f, c, style = Stroke(s * 0.035f))
        }
        else -> {
            val p = Path().apply {
                for (i in 0 until 24) {
                    val ang = Math.toRadians(i * 15.0); val rr = if (i % 2 == 0) s * 0.48f else s * 0.36f
                    val q = Offset(c.x + (kotlin.math.cos(ang) * rr).toFloat(), c.y + (kotlin.math.sin(ang) * rr).toFloat())
                    if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                }
                close()
            }
            drawPath(p, a[2]); drawPath(p, ink, style = Stroke(s * 0.03f))
            drawCircle(a[3], s * 0.26f, c)
        }
    }
}

/** Retro's icons (our own drawings): thick, rounded, in a sign-painter's hand. */
object RetroIcons {
    private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in strokes) b.addPath(addPathNodes(p), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        for (p in fills) b.addPath(addPathNodes(p), fill = SolidColor(Color.Black))
        return b.build()
    }
    val Add by lazy { icon("Add", listOf("M12,5 L12,19", "M5,12 L19,12")) }
    val ArrowBack by lazy { icon("Back", listOf("M19,12 L5.5,12", "M11,6 L5,12 L11,18")) }
    val Close by lazy { icon("Close", listOf("M6,6 L18,18", "M18,6 L6,18")) }
    val Check by lazy { icon("Check", listOf("M5,12.5 L10,17.5 L19,7")) }
    val Home by lazy { icon("Home", listOf("M4,11.5 L12,4.5 L20,11.5", "M6.5,10 L6.5,19.5 L17.5,19.5 L17.5,10"), listOf("M10,19.5 L10,14.5 Q12,13 14,14.5 L14,19.5 Z")) }
    val Favorite by lazy { icon("Heart", emptyList(), listOf("M12,20.5 C3.5,14.8 3,10.3 4.4,7.6 C6,4.6 10,4.5 12,7.6 C14,4.5 18,4.6 19.6,7.6 C21,10.3 20.5,14.8 12,20.5 Z")) }
    val Notifications by lazy { icon("Bell", listOf("M10,19.5 Q12,21 14,19.5"), listOf("M5.5,17.5 Q7,15.5 7,11 Q7,5.5 12,5.5 Q17,5.5 17,11 Q17,15.5 18.5,17.5 Z")) }
    val Person by lazy { icon("Person", emptyList(), listOf("M12,4 A4,4 0 1,1 11.99,4 Z", "M4.5,20.5 Q5,13.5 12,13.5 Q19,13.5 19.5,20.5 Z")) }
    val Search by lazy { icon("Search", listOf("M10.5,4.5 A6,6 0 1,1 10.49,4.5 Z", "M15,15 L20,20")) }
    val Settings by lazy { icon("Settings", listOf("M12,9 A3,3 0 1,1 11.99,9 Z", "M12,3 L12,5.5", "M12,18.5 L12,21", "M3,12 L5.5,12", "M18.5,12 L21,12", "M5.6,5.6 L7.4,7.4", "M16.6,16.6 L18.4,18.4", "M18.4,5.6 L16.6,7.4", "M7.4,16.6 L5.6,18.4")) }
    val MoreVert by lazy { icon("More", emptyList(), listOf("M12,3.5 A2,2 0 1,1 11.99,3.5 Z", "M12,10 A2,2 0 1,1 11.99,10 Z", "M12,16.5 A2,2 0 1,1 11.99,16.5 Z")) }
    val Share by lazy { icon("Share", listOf("M8.5,11 L15.5,7", "M8.5,13 L15.5,17"), listOf("M17.5,3 A2.6,2.6 0 1,1 17.49,3 Z", "M6.5,9.4 A2.6,2.6 0 1,1 6.49,9.4 Z", "M17.5,15.8 A2.6,2.6 0 1,1 17.49,15.8 Z")) }
    val Delete by lazy { icon("Delete", listOf("M4.5,7 L19.5,7", "M9.5,7 Q9.5,4 12,4 Q14.5,4 14.5,7"), listOf("M6.5,8.5 L17.5,8.5 L16.6,19.5 Q16.5,20.5 15.5,20.5 L8.5,20.5 Q7.5,20.5 7.4,19.5 Z")) }
    val Edit by lazy { icon("Edit", emptyList(), listOf("M4,20 L5,15.5 L15,5.5 Q16.5,4 18,5.5 L18.5,6 Q20,7.5 18.5,9 L8.5,19 Z")) }
    val Bag by lazy { icon("Bag", listOf("M9,8 Q9,4.2 12,4.2 Q15,4.2 15,8"), listOf("M5.5,8 L18.5,8 L17.6,19.6 Q17.5,20.6 16.4,20.6 L7.6,20.6 Q6.5,20.6 6.4,19.6 Z")) }
    val Chat by lazy { icon("Chat", emptyList(), listOf("M5,4.5 L19,4.5 Q21,4.5 21,6.5 L21,14 Q21,16 19,16 L10.5,16 L6,20 L6.5,16 L5,16 Q3,16 3,14 L3,6.5 Q3,4.5 5,4.5 Z")) }
}
