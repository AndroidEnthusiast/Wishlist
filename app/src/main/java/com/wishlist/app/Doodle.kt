package com.wishlist.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Doodle, rebuilt (owner, 13 November 2026, 2.202: "not doodly enough"; "a theme within a theme"; options to
 * personalise). Three sub-themes, each with its own paper, colours, card shapes and drawings, in light and dark:
 *  - notebook: ruled paper with a margin, a black marker and a yellow highlighter; cards taped in.
 *  - sticky:   graph paper with sticky notes (a folded corner) pinned to it.
 *  - board:    a whiteboard with marker colours (light), a blackboard with chalk (dark); cards held by magnets.
 * Everything is hand-drawn: icons (DoodleIcons), tick boxes, choice circles, switches, dividers, the spinner, the
 * bottom bar. Options: the amount of decoration (calm, normal, wild) and the card style (outlined, filled, sticker).
 * Everything drawn on cards is made once per size (drawWithCache), so scrolling stays smooth.
 */

private fun c(v: Long) = Color(v)

/** Doodle's look for [set], [deco] (0 calm, 1 normal, 2 wild) and card style [cards]. */
internal fun doodleLook(dark: Boolean, set: String, deco: Int, cards: String): Look {
    class P(
        val bg: Long, val surface: Long, val ink: Long, val muted: Long, val primary: Long, val onPrimary: Long,
        val container: Long, val fill: Long, val onFill: Long, val outlineVariant: Long,
        val notes: List<Long>, val tints: List<Long>, val blobs: List<Pair<Long, Float>>
    )
    val p = when (set) {
        "sticky" -> if (!dark) P(
            0xFFF5F8FB, 0xFFFFFFFF, 0xFF1B1B1B, 0xFF3F3F3F, 0xFF1F45C9, 0xFFFFFFFF, 0xFFFFE0EA, 0xFFFF8FB1, 0xFF1B1B1B, 0xFFD3DCE6,
            listOf(0xFFFFF3A3, 0xFFFFD1E0, 0xFFCFE8FF, 0xFFD6F5C9), listOf(0xFFFFF3A3, 0xFFFFD1E0, 0xFFCFE8FF),
            listOf(0xFF1B1B1B to 0.10f, 0xFFFF8FB1 to 0.25f, 0xFFDCE6F2 to 1f, 0xFFC3D3E8 to 1f)
        ) else P(
            0xFF1B1E24, 0xFF262A31, 0xFFF2F2EE, 0xFFC8C8C0, 0xFF9DB8FF, 0xFF1B1B1B, 0xFF4A2A36, 0xFFF7A8C4, 0xFF1B1B1B, 0xFF3A404A,
            listOf(0xFF4A4320, 0xFF4A2A36, 0xFF213A52, 0xFF2A4426), listOf(0xFF4A4320, 0xFF4A2A36, 0xFF213A52),
            listOf(0xFFF2F2EE to 0.10f, 0xFFF7A8C4 to 0.22f, 0xFF262B33 to 1f, 0xFF2F3640 to 1f)
        )
        "board" -> if (!dark) P(
            0xFFF7F8F6, 0xFFFFFFFF, 0xFF1B1B1B, 0xFF454545, 0xFFC62828, 0xFFFFFFFF, 0xFFE3F1E4, 0xFF2E7D32, 0xFFFFFFFF, 0xFFD9DBD6,
            listOf(0xFFE8F0FA, 0xFFFBE3E3, 0xFFE3F1E4, 0xFFFFF4D6), listOf(0xFFE8F0FA, 0xFFFBE3E3, 0xFFE3F1E4),
            listOf(0xFF1B1B1B to 0.08f, 0xFFC62828 to 0.18f, 0xFFE4E6E2 to 1f, 0xFFECEEEA to 1f)
        ) else P(
            0xFF1F2A24, 0xFF28352E, 0xFFF2F2EC, 0xFFC9CFC7, 0xFFF7A1B5, 0xFF1F2A24, 0xFF3A3A26, 0xFFF4E58A, 0xFF1F2A24, 0xFF3A4A40,
            listOf(0xFF33402E, 0xFF3A2E33, 0xFF2C3A44, 0xFF403A26), listOf(0xFF2C3A33, 0xFF3A2E33, 0xFF33402E),
            listOf(0xFFF2F2EC to 0.10f, 0xFFF7A1B5 to 0.20f, 0xFF2A3830 to 1f, 0xFF26332C to 1f)
        )
        else -> if (!dark) P(
            0xFFFFFEF8, 0xFFFFFFFF, 0xFF141414, 0xFF474747, 0xFF1F45C9, 0xFFFFFFFF, 0xFFFFF6BF, 0xFFFFE45C, 0xFF141414, 0xFFD6D6D0,
            listOf(0xFFFFF6BF, 0xFFDDE6FF, 0xFFFFE0D6, 0xFFE2F5D8), listOf(0xFFFFF6BF, 0xFFDDE6FF, 0xFFFFE0D6),
            listOf(0xFF141414 to 0.10f, 0xFFFF8A3D to 0.25f, 0xFFC9D8F0 to 1f, 0xFFF2A0A0 to 0.7f)
        ) else P(
            0xFF161616, 0xFF222222, 0xFFF5F5EF, 0xFFBDBDB5, 0xFF8FB0FF, 0xFF141414, 0xFF3F3A1A, 0xFFFFE45C, 0xFF141414, 0xFF3A3A3A,
            listOf(0xFF3F3A1A, 0xFF22304F, 0xFF4A2A24, 0xFF26402A), listOf(0xFF2C2C2C, 0xFF22304F, 0xFF3A2A20),
            listOf(0xFFF5F5EF to 0.12f, 0xFFFF8A3D to 0.25f, 0xFF2B2B2B to 1f, 0xFF6B2A2A to 0.7f)
        )
    }
    val ink = c(p.ink)
    val fill = c(p.fill)
    val scheme = doodleScheme(dark, c(p.primary), c(p.onPrimary), c(p.container), ink, c(p.bg), c(p.surface), c(p.muted), c(p.outlineVariant))
    return Look(
        "doodle", dark, scheme,
        // Filled cards take the sub-theme's note colours; outlined and sticker cards are plain paper.
        if (cards == "filled") p.notes.map(::c) else listOf(c(p.surface)),
        p.tints.map(::c), ink, listOf(ink),
        p.blobs.map { (h, a) -> c(h).copy(alpha = a) },
        doodleDisplay, if (dark) c(0xFF4CD98A) else c(0xFF1F8A4C), if (dark) c(0xFFFF7B6E) else c(0xFFC4321F),
        accent = fill, onAccent = c(p.onFill),
        art = listOf(fill, ink, c(p.surface), c(p.primary)),
        set = set, deco = deco, cardStyle = cards
    )
}

/** Thick line for sticker cards, the marker line for everything else. */
internal fun Look.doodleEdge(card: Boolean): Dp = if (card && cardStyle == "sticker") 3.5.dp else edge

/**
 * The shadow under a Doodle surface (drawn before it): a hatched pencil shadow; sticker cards a solid ink one. Made
 * once per size.
 */
internal fun Modifier.doodleSurface(look: Look, shape: Shape, raised: Boolean, depth: Float, card: Boolean): Modifier {
    if (!raised) return this
    val o = (depth * 0.6f).coerceIn(3f, 6f).dp
    if (card && look.cardStyle == "sticker") return hardShadow(look.line, shape, o)
    return drawWithCache {
        val off = o.toPx()
        val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)); translate(Offset(off, off)) }
        val step = 5.dp.toPx()
        val lines = ArrayList<Pair<Offset, Offset>>()
        var x = -size.height
        while (x < size.width + off) { lines += Offset(x, size.height + off) to Offset(x + size.height + off, 0f); x += step }
        val col = look.line.copy(alpha = 0.5f)
        val w = 1.2.dp.toPx()
        onDrawBehind { clipPath(clip) { for ((a, b) in lines) drawLine(col, a, b, w) } }
    }
}

/** The marker line, drawn twice, the second a little off, like a quick sketch. Made once per size. */
internal fun Modifier.doodleLine(look: Look, shape: Shape, card: Boolean = false): Modifier = drawWithCache {
    val w = look.doodleEdge(card).toPx()
    val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    val again = shape.createOutline(size, layoutDirection, this)
    val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(1.dp.toPx())
    val ghost = look.line.copy(alpha = 0.45f)
    val dx = 1.2.dp.toPx(); val dy = -1.dp.toPx()
    onDrawWithContent {
        drawContent()
        translate(w / 2, w / 2) { drawOutline(outline, look.line, style = st) }
        translate(dx, dy) { drawOutline(again, ghost, style = thin) }
    }
}

/**
 * What holds a Doodle card on the page: tape (notebook), a push pin (sticky notes) or a magnet (board); Wild adds a
 * paper clip on every other card. Calm: nothing. Made once per size.
 */
internal fun Modifier.doodlePin(look: Look, id: Long): Modifier = if (look.deco == 0) this else drawWithCache {
    val u = 1.dp.toPx()
    val k = Math.floorMod(id, 5L).toInt()
    val clip = look.deco == 2 && k % 2 == 0
    onDrawWithContent {
        drawContent()
        when (look.set) {
            "sticky" -> {
                val at = Offset(size.width * (0.42f + k * 0.04f), 4 * u)
                drawCircle(Color.Black.copy(alpha = 0.25f), 6 * u, at + Offset(1.5f * u, 2 * u))
                drawCircle(listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF8E24AA))[k], 6 * u, at)
                drawCircle(Color.White.copy(alpha = 0.7f), 2 * u, at - Offset(2 * u, 2 * u))
            }
            "board" -> {
                val at = Offset(size.width * (0.5f + (k - 2) * 0.05f), 2 * u)
                drawCircle(Color.Black.copy(alpha = 0.25f), 8 * u, at + Offset(1.5f * u, 2 * u))
                drawCircle(listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFFFF7043))[k], 8 * u, at)
                drawCircle(Color.White.copy(alpha = 0.35f), 8 * u, at, style = Stroke(1.5f * u))
            }
            else -> tape(Offset(size.width * (0.18f + k * 0.03f), 0f), listOf(-8f, 5f, -4f, 7f, -6f)[k], Color(0xFFF7EBC0).copy(alpha = 0.8f))
        }
        if (clip) paperClip(Offset(size.width - 14 * u, -6 * u), u)
    }
}

/** A paper clip, drawn: two nested rounded loops. */
private fun DrawScope.paperClip(at: Offset, u: Float) {
    val st = Stroke(1.8f * u, cap = StrokeCap.Round)
    val col = Color(0xFF8A8F98)
    val p = Path().apply {
        moveTo(at.x + 3 * u, at.y + 26 * u)
        lineTo(at.x + 3 * u, at.y + 4 * u)
        quadraticTo(at.x + 3 * u, at.y, at.x + 6.5f * u, at.y)
        quadraticTo(at.x + 10 * u, at.y, at.x + 10 * u, at.y + 4 * u)
        lineTo(at.x + 10 * u, at.y + 22 * u)
        quadraticTo(at.x + 10 * u, at.y + 25 * u, at.x + 7.5f * u, at.y + 25 * u)
        quadraticTo(at.x + 5.5f * u, at.y + 25 * u, at.x + 5.5f * u, at.y + 22 * u)
        lineTo(at.x + 5.5f * u, at.y + 7 * u)
    }
    drawPath(p, col, style = st)
}

/**
 * Doodle's page: the sub-theme's paper (ruled lines and a margin, graph paper, or a board with eraser smudges), always;
 * marker doodles at Normal; more of them at Wild. Calm: only the paper.
 */
internal fun Modifier.doodleBackdrop(look: Look): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val u = 1.dp.toPx()
    val pen = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val doodles = when (look.deco) { 0 -> emptyList(); 1 -> doodleShapes(w, h, u, false); else -> doodleShapes(w, h, u, true) }
    val smudges = listOf(Rect(Offset(w * 0.1f, h * 0.15f), Size(w * 0.5f, 70 * u)), Rect(Offset(w * 0.45f, h * 0.55f), Size(w * 0.55f, 90 * u)),
        Rect(Offset(-20 * u, h * 0.8f), Size(w * 0.4f, 60 * u)))
    onDrawBehind {
        when (look.set) {
            "sticky" -> {
                val step = 16 * u
                var x = 0f; var i = 0
                while (x < w) { drawLine(if (i % 4 == 0) look.blobs[3] else look.blobs[2], Offset(x, 0f), Offset(x, h), 1f * u); x += step; i++ }
                var y = 0f; i = 0
                while (y < h) { drawLine(if (i % 4 == 0) look.blobs[3] else look.blobs[2], Offset(0f, y), Offset(w, y), 1f * u); y += step; i++ }
            }
            "board" -> {
                smudges.forEachIndexed { i, r -> drawOval(if (i % 2 == 0) look.blobs[2] else look.blobs[3], r.topLeft, r.size) }
                // The board's frame along the edges (where text never goes).
                drawRect(look.blobs[2], Offset(0f, 0f), Size(6 * u, h))
                drawRect(look.blobs[2], Offset(w - 6 * u, 0f), Size(6 * u, h))
            }
            else -> {
                var y = 56 * u
                while (y < h) { drawLine(look.blobs[2], Offset(0f, y), Offset(w, y), 1f * u); y += 28 * u }
                drawLine(look.blobs[3], Offset(10 * u, 0f), Offset(10 * u, h), 1.5f * u)
            }
        }
        doodles.forEachIndexed { i, (path, filled) ->
            if (filled) drawPath(path, look.blobs[1]) else drawPath(path, if (i % 3 == 2) look.blobs[1] else look.blobs[0], style = pen)
        }
    }
}

/** The marker doodles: stars, a spiral, a smiley, a flower, squiggles; [more]: arrows, hearts, a cloud and more stars. */
private fun doodleShapes(w: Float, h: Float, u: Float, more: Boolean): List<Pair<Path, Boolean>> {
    fun star(c: Offset, r: Float) = Path().apply {
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0); val rr = if (i % 2 == 0) r else r * 0.45f
            val p = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    fun spiral(c: Offset, r: Float) = Path().apply {
        moveTo(c.x, c.y)
        for (i in 1..60) { val a = i * 0.33; val rr = r * i / 60f; lineTo(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat()) }
    }
    fun squiggle(x: Float, y: Float, len: Float) = Path().apply {
        moveTo(x, y); var k = 0f
        while (k < len) { quadraticTo(x + k + 6 * u, y - 6 * u, x + k + 12 * u, y); quadraticTo(x + k + 18 * u, y + 6 * u, x + k + 24 * u, y); k += 24 * u }
    }
    fun smiley(c: Offset) = Path().apply {
        addOval(Rect(c, 16 * u)); addOval(Rect(Offset(c.x - 5 * u, c.y - 4 * u), 1.6f * u)); addOval(Rect(Offset(c.x + 5 * u, c.y - 4 * u), 1.6f * u))
        moveTo(c.x - 8 * u, c.y + 3 * u); quadraticTo(c.x, c.y + 11 * u, c.x + 8 * u, c.y + 3 * u)
    }
    fun flower(c: Offset) = Path().apply {
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0)
            addOval(Rect(Offset(c.x + (kotlin.math.cos(a) * 11 * u).toFloat(), c.y + (kotlin.math.sin(a) * 11 * u).toFloat()), 6 * u))
        }
    }
    fun heart(c: Offset, r: Float) = Path().apply {
        moveTo(c.x, c.y + r); cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.4f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.4f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r)
    }
    fun arrow(a: Offset, b: Offset) = Path().apply {
        moveTo(a.x, a.y); quadraticTo((a.x + b.x) / 2, a.y - 24 * u, b.x, b.y)
        moveTo(b.x - 8 * u, b.y - 7 * u); lineTo(b.x, b.y); lineTo(b.x - 10 * u, b.y + 3 * u)
    }
    fun cloud(c: Offset) = Path().apply {
        moveTo(c.x - 26 * u, c.y + 8 * u)
        quadraticTo(c.x - 30 * u, c.y - 8 * u, c.x - 14 * u, c.y - 8 * u); quadraticTo(c.x - 8 * u, c.y - 22 * u, c.x + 6 * u, c.y - 12 * u)
        quadraticTo(c.x + 24 * u, c.y - 16 * u, c.x + 24 * u, c.y); quadraticTo(c.x + 30 * u, c.y + 10 * u, c.x + 16 * u, c.y + 10 * u)
        lineTo(c.x - 26 * u, c.y + 8 * u)
    }
    val out = mutableListOf(
        star(Offset(w * 0.88f, 90 * u), 14 * u) to false, spiral(Offset(w * 0.07f, 230 * u), 18 * u) to false,
        squiggle(w * 0.5f, 30 * u, 70 * u) to false, smiley(Offset(w * 0.9f, h * 0.48f)) to false,
        flower(Offset(w * 0.08f, h * 0.36f)) to false, star(Offset(w * 0.1f, h * 0.7f), 10 * u) to true,
        squiggle(w * 0.6f, h * 0.9f, 90 * u) to false
    )
    if (more) out += listOf(
        heart(Offset(w * 0.93f, h * 0.27f), 10 * u) to true, arrow(Offset(w * 0.62f, h * 0.62f), Offset(w * 0.9f, h * 0.66f)) to false,
        cloud(Offset(w * 0.25f, 60 * u)) to false, star(Offset(w * 0.55f, h * 0.43f), 8 * u) to false,
        star(Offset(w * 0.07f, h * 0.55f), 7 * u) to true, spiral(Offset(w * 0.92f, h * 0.8f), 14 * u) to false,
        heart(Offset(w * 0.3f, h * 0.96f), 8 * u) to false, squiggle(w * 0.05f, h * 0.12f, 50 * u) to false
    )
    return out
}

/** Doodle's scheme: the same steps as the other styles (Design.kt's scheme), with Doodle's colours. */
private fun doodleScheme(dark: Boolean, primary: Color, onPrimary: Color, container: Color, ink: Color, bg: Color, surface: Color, muted: Color, outlineVariant: Color) =
    lookScheme(dark, primary, onPrimary, container, ink, bg, surface, ink, muted, ink, outlineVariant)

/**
 * A sticky note: a square with its bottom-right corner folded over ([fold] wide). The fold is drawn by
 * [Modifier.doodleFold].
 */
class DogEarShape(private val corner: Dp = 2.dp, private val fold: Dp = 16.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val f = with(density) { fold.toPx() }.coerceAtMost(minOf(size.width, size.height) / 3)
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r)
            lineTo(w, h - f); lineTo(w - f, h)
            lineTo(r, h); quadraticTo(0f, h, 0f, h - r); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); close()
        }
        return Outline.Generic(p)
    }
    override fun equals(other: Any?) = other is DogEarShape && other.corner == corner && other.fold == fold
    override fun hashCode() = corner.hashCode() * 31 + fold.hashCode()
}

/** The folded-over corner of a sticky note: a small triangle in a deeper shade with the marker line. */
internal fun Modifier.doodleFold(look: Look, fold: Dp = 16.dp): Modifier = drawWithCache {
    val f = fold.toPx().coerceAtMost(minOf(size.width, size.height) / 3)
    val w = size.width; val h = size.height
    val tri = Path().apply { moveTo(w, h - f); lineTo(w - f, h - f); lineTo(w - f, h); close() }
    val shade = Color.Black.copy(alpha = 0.12f)
    val st = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round)
    onDrawWithContent { drawContent(); drawPath(tri, shade); drawPath(tri, look.line, style = st) }
}

/** Doodle's hand-drawn icons (our own drawings, allowed by the icon rule), stroked like a marker. */
object DoodleIcons {
    private fun icon(name: String, vararg d: String, w: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in d) b.addPath(addPathNodes(p), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = w,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        return b.build()
    }
    val Add by lazy { icon("Add", "M12,4.5 Q11.6,12 12.3,19.5", "M4.5,12.2 Q12,11.6 19.5,12") }
    val ArrowBack by lazy { icon("Back", "M19.5,12.3 Q12,11.6 5,12", "M10.5,6 Q7,9.5 4.8,12 Q7.5,14.8 10.8,18") }
    val ArrowDropDown by lazy { icon("Drop", "M7,10 Q12,10.4 17,9.8 Q14.5,13 12.2,15.5 Q9.6,12.8 7,10 Z") }
    val Check by lazy { icon("Check", "M4.5,12.5 Q7.5,15.5 9.5,18 Q14,10 19.8,5.5") }
    val Close by lazy { icon("Close", "M5.5,5.5 Q12,12.3 18.5,18.8", "M18.6,5.3 Q11.8,12 5.4,18.6") }
    val Delete by lazy {
        icon("Delete", "M5,7 Q12,6.4 19,7", "M9.5,7 Q9.6,4.5 12,4.6 Q14.4,4.5 14.5,7",
            "M6.5,7.2 Q7,14 7.8,19.6 Q12,20.4 16.4,19.5 Q17,13.8 17.6,7.2", "M10.2,10.5 L10.6,17", "M13.8,10.5 L13.4,17")
    }
    val Edit by lazy { icon("Edit", "M5,19 Q5.4,16.6 6,15.4 L15.8,5.6 Q17.4,4.4 18.6,5.6 Q19.6,6.8 18.4,8.2 L8.6,18 Q6.8,18.6 5,19 Z", "M14.2,7.2 L16.8,9.8") }
    val Favorite by lazy { icon("Heart", "M12,19.5 Q4,13.8 4.6,9 Q5.2,5.2 8.6,5.4 Q11,5.6 12,8 Q13,5.6 15.4,5.4 Q18.8,5.2 19.4,9 Q20,13.8 12,19.5 Z") }
    val Home by lazy {
        icon("Home", "M4.5,11.5 Q8,8 12,4.8 Q16,8.2 19.5,11.6", "M6.5,10 Q6.6,15 6.8,19.4 Q12,19.8 17.2,19.3 Q17.5,14.6 17.4,10",
            "M10.2,19.4 Q10,16 10.4,14 Q12,13.6 13.8,14 Q14,16.5 13.9,19.4")
    }
    val Lock by lazy { icon("Lock", "M6,11 Q12,10.6 18,11 Q18.4,15.5 17.8,19.6 Q12,20 6.2,19.5 Q5.6,15 6,11 Z", "M8.5,10.8 Q8.4,5.2 12,5 Q15.6,5.2 15.5,10.8", "M12,14.5 L12,16.5") }
    val ListIcon by lazy { icon("List", "M4.5,7 Q12,6.6 19.5,7.1", "M4.5,12 Q12,12.4 19.5,11.9", "M4.5,17 Q12,16.6 19.5,17.2") }
    val Notifications by lazy { icon("Bell", "M6,17 Q7.2,14.5 7,10.5 Q7.2,6 12,5.6 Q16.8,6 17,10.5 Q16.8,14.5 18,17 Q12,17.6 6,17 Z", "M10.2,19 Q12,20.6 13.8,19", "M12,5.6 L12,4") }
    val MoreVert by lazy { icon("More", "M12,5 L12.1,5.2", "M12,12 L12.1,12.2", "M12,19 L12.1,19.2", w = 3.2f) }
    val Person by lazy { icon("Person", "M12,4.8 Q15.2,4.9 15.1,8.4 Q15,11.6 12,11.7 Q8.9,11.6 8.9,8.3 Q9,4.9 12,4.8 Z", "M5,19.8 Q5.6,14.2 12,14 Q18.4,14.2 19,19.8") }
    val Refresh by lazy { icon("Refresh", "M18.8,11.5 Q18.4,5.4 12,5.4 Q6,5.6 5.4,11.8 Q5.6,18.4 12,18.6 Q16.2,18.4 17.8,15", "M18.8,5.6 L19,11.4 L13.4,11") }
    val Search by lazy { icon("Search", "M10.5,4.8 Q16.2,5 16.2,10.5 Q16,16.2 10.4,16.2 Q4.8,16 4.8,10.4 Q5,4.9 10.5,4.8 Z", "M14.6,14.6 Q17,17.2 19.4,19.6") }
    val Settings by lazy {
        icon("Settings", "M12,8.6 Q15.4,8.7 15.4,12 Q15.3,15.4 12,15.4 Q8.6,15.3 8.6,12 Q8.7,8.7 12,8.6 Z", "M12,3.8 L12,6.4", "M12,17.6 L12,20.2",
            "M3.8,12 L6.4,12", "M17.6,12 L20.2,12", "M6.2,6.2 L8,8", "M16,16 L17.8,17.8", "M17.8,6.2 L16,8", "M8,16 L6.2,17.8")
    }
    val Share by lazy {
        icon("Share", "M17,3.6 Q19,3.6 19,5.6 Q19,7.6 17,7.6 Q15,7.6 15,5.6 Q15,3.6 17,3.6 Z", "M7,10 Q9,10 9,12 Q9,14 7,14 Q5,14 5,12 Q5,10 7,10 Z",
            "M17,16.4 Q19,16.4 19,18.4 Q19,20.4 17,20.4 Q15,20.4 15,18.4 Q15,16.4 17,16.4 Z", "M8.8,11 Q12,9 15.2,6.6", "M8.8,13 Q12,15 15.2,17.4")
    }
    val Gift by lazy {
        icon("Gift", "M4.5,9 Q12,8.6 19.5,9 L19.4,12 Q12,12.3 4.6,12 Z", "M6,12.2 Q6.2,16.5 6.4,19.8 Q12,20.2 17.6,19.7 Q17.8,16 18,12.2",
            "M12,9 L12,19.9", "M12,9 Q9,4 7.6,6.6 Q7.4,8.6 12,9 Q15,4 16.4,6.6 Q16.6,8.6 12,9")
    }
    val Bag by lazy { icon("Bag", "M5.6,8 Q12,7.6 18.4,8 Q18.6,14 18,19.8 Q12,20.3 6,19.8 Q5.4,14 5.6,8 Z", "M9,8 Q9,4.6 12,4.5 Q15,4.6 15,8") }
    val Swap by lazy { icon("Swap", "M5,8.5 Q12,8.2 18.6,8.6", "M15.4,5.4 L18.8,8.6 L15.4,11.6", "M19,15.5 Q12,15.8 5.4,15.4", "M8.6,12.4 L5.2,15.4 L8.6,18.6") }
    val Chevron by lazy { icon("Chevron", "M9.5,5.8 Q13,9 15,12 Q13,15 9.5,18.2") }
    val Group by lazy {
        icon("Group", "M8.6,6 Q11,6 11,8.6 Q11,11 8.6,11 Q6.2,11 6.2,8.6 Q6.2,6 8.6,6 Z", "M2.8,18.8 Q3.4,13.6 8.6,13.4 Q13.6,13.6 14.2,18.8",
            "M15.6,6.4 Q17.8,6.4 17.8,8.8 Q17.6,11 15.6,11", "M16.2,13.6 Q20.4,14 21,18.6")
    }
    val Chat by lazy { icon("Chat", "M5,6 Q12,5.4 19,6 Q19.6,10 19,14 Q14,14.6 10,14.2 L6.4,18 L7,14 Q5,14 4.8,10 Q4.6,7.6 5,6 Z") }
}

/** A hand-drawn tick box: a wobbly square; ticked, a highlighter scribble and a marker tick. */
internal fun DrawScope.doodleCheckbox(look: Look, checked: Boolean) {
    val s = size.minDimension
    val u = s / 20f
    val box = Path().apply {
        moveTo(2.5f * u, 3f * u); quadraticTo(10 * u, 2f * u, 17.5f * u, 2.8f * u); quadraticTo(18.4f * u, 10 * u, 17.6f * u, 17.4f * u)
        quadraticTo(10 * u, 18.4f * u, 2.8f * u, 17.6f * u); quadraticTo(1.8f * u, 10 * u, 2.5f * u, 3f * u); close()
    }
    if (checked) {
        // A highlighter zigzag inside, then the tick.
        val zig = Path().apply {
            moveTo(4 * u, 6 * u); lineTo(8 * u, 4 * u); lineTo(4 * u, 11 * u); lineTo(13 * u, 4 * u); lineTo(4 * u, 16 * u); lineTo(16 * u, 5 * u)
            lineTo(8 * u, 16 * u); lineTo(16 * u, 10 * u); lineTo(13 * u, 16 * u)
        }
        drawPath(zig, look.accent, style = Stroke(3.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawPath(box, look.line, style = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (checked) drawPath(Path().apply { moveTo(4.5f * u, 10.5f * u); quadraticTo(7 * u, 13 * u, 8.6f * u, 15.5f * u); quadraticTo(12 * u, 8 * u, 18.5f * u, 2.5f * u) },
        look.line, style = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A hand-drawn choice circle: a loop that overshoots its start; chosen, a scribbled dot. */
internal fun DrawScope.doodleRadio(look: Look, selected: Boolean) {
    val s = size.minDimension
    val c = center
    val r = s * 0.4f
    val loop = Path().apply {
        for (i in 0..40) {
            val a = Math.toRadians(-60.0 + i * 9.5)
            val rr = r * (1f + 0.06f * kotlin.math.sin(i * 0.7).toFloat())
            val p = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }
    drawPath(loop, look.line, style = Stroke(s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (selected) {
        val dot = Path().apply {
            for (i in 0..24) {
                val a = i * 0.9; val rr = r * 0.45f * (i / 24f + 0.3f)
                val p = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(dot, look.textAccent, style = Stroke(s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A hand-drawn switch: a wobbly pill; on, filled with the highlighter, the knob a scribbled circle on the right. */
internal fun DrawScope.doodleSwitch(look: Look, t: Float) {
    val w = size.width; val h = size.height
    val u = h / 32f
    val pill = Path().apply {
        moveTo(14 * u, 3 * u); quadraticTo(w / 2, 1.8f * u, w - 14 * u, 3.2f * u); quadraticTo(w - 1.5f * u, h / 2, w - 14 * u, h - 2.6f * u)
        quadraticTo(w / 2, h - 1.4f * u, 14 * u, h - 3 * u); quadraticTo(1.2f * u, h / 2, 14 * u, 3 * u); close()
    }
    if (t > 0f) drawPath(pill, look.accent.copy(alpha = t))
    drawPath(pill, look.line, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val kx = 15 * u + (w - 30 * u) * t
    val knob = Offset(kx, h / 2)
    drawCircle(look.scheme.surfaceVariant, 10 * u, knob)
    drawCircle(look.line, 10 * u, knob, style = Stroke(2.2f * u))
    drawArc(look.line, 200f, 80f, false, Offset(knob.x - 6 * u, knob.y - 6 * u), Size(12 * u, 12 * u), style = Stroke(1.4f * u, cap = StrokeCap.Round))
}

/** A hand-drawn divider: a wavy marker line. */
internal fun DrawScope.doodleDivider(look: Look, colour: Color) {
    val w = size.width; val y = size.height / 2
    val u = 1.dp.toPx()
    val p = Path().apply { moveTo(0f, y); var x = 0f; while (x < w) { quadraticTo(x + 12 * u, y - 2.5f * u, x + 24 * u, y); quadraticTo(x + 36 * u, y + 2.5f * u, x + 48 * u, y); x += 48 * u } }
    drawPath(p, colour, style = Stroke(1.8f * u, cap = StrokeCap.Round))
}

/** Doodle's spinner: a scribbled arc drawn twice, turning. */
internal fun DrawScope.doodleSpinner(look: Look, sw: Float) {
    val inset = sw + 2f
    val s = Size(size.width - 2 * inset, size.height - 2 * inset)
    drawArc(look.accent, 0f, 300f, false, Offset(inset, inset), s, style = Stroke(sw * 1.6f, cap = StrokeCap.Round))
    drawArc(look.line, 10f, 120f, false, Offset(inset + 1.5f, inset - 1f), s, style = Stroke(sw, cap = StrokeCap.Round))
    drawArc(look.line, 190f, 90f, false, Offset(inset - 1f, inset + 1.5f), s, style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
}

/** Doodle's emblem (a wish without a photo, an empty page) for each sub-theme: a doodled sun, a pinned note, a star. */
internal fun DrawScope.doodleEmblem(look: Look) {
    val s = size.minDimension
    val c = center
    val pen = Stroke(s * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (look.set) {
        "sticky" -> {
            rotate(-6f, c) {
                drawRect(look.tints[0], Offset(c.x - s * 0.38f, c.y - s * 0.36f), Size(s * 0.76f, s * 0.74f))
                drawRect(look.line, Offset(c.x - s * 0.38f, c.y - s * 0.36f), Size(s * 0.76f, s * 0.74f), style = pen)
            }
            drawCircle(Color(0xFFE53935), s * 0.08f, Offset(c.x, c.y - s * 0.36f))
        }
        "board" -> {
            val star = Path().apply {
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0); val rr = if (i % 2 == 0) s * 0.46f else s * 0.22f
                    val p = Offset(c.x + (kotlin.math.cos(a) * rr).toFloat(), c.y + (kotlin.math.sin(a) * rr).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(star, look.accent.copy(alpha = 0.5f)); drawPath(star, look.line, style = pen)
        }
        else -> {
            drawCircle(look.accent.copy(alpha = 0.6f), s * 0.3f, c)
            drawCircle(look.line, s * 0.3f, c, style = pen)
            for (i in 0 until 8) rotate(i * 45f + 10f, c) { drawLine(look.line, Offset(c.x, c.y - s * 0.38f), Offset(c.x, c.y - s * 0.48f), s * 0.045f, StrokeCap.Round) }
        }
    }
}
