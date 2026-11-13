package com.wishlist.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Y2K, rebuilt as a deep theme (owner, 13 November 2026, the second after Doodle). Three sub-themes, each with its own
 * page, colours, shapes and drawings, in light and dark:
 *  - holo:   Holographic: pastel holo gradients, iridescent sheen, sparkles, orbit rings, star stickers.
 *  - chrome: Liquid chrome: silver and chrome, liquid-metal blobs, sparkle glints, a dark space backdrop in dark.
 *  - aero:   Bubbly (Frutiger Aero): glossy aqua and lime, bubbles and droplets, a sky with clouds, glassy panels.
 * Own parts: glossy jelly buttons, chips and switch, gel tick boxes and orb choice circles, star-and-sparkle icons
 * (Y2kIcons), stickers on cards. Card styles: glass (see-through frosted), glossy (solid with a shine), sticker (white,
 * a thick outline and a holo shadow). Everything drawn on cards is made once per size (drawWithCache).
 */

private fun c(v: Long) = Color(v)

/** Y2K's look for sub-theme [set], decoration [deco] (0 calm, 1 normal, 2 wild) and card style [cards]. */
internal fun y2kLook(dark: Boolean, set: String, deco: Int, cards: String): Look {
    class P(
        val bg: Long, val surface: Long, val ink: Long, val muted: Long, val line: Long, val primary: Long, val onPrimary: Long,
        val container: Long, val outlineVariant: Long, val gradient: List<Long>, val onFill: Long,
        val glossy: List<Long>, val tints: List<Long>, val blobs: List<Pair<Long, Float>>, val art: List<Long>
    )
    val p = when (set) {
        "chrome" -> if (!dark) P(
            0xFFE9ECF1, 0xFFF7F8FA, 0xFF12151C, 0xFF474D5A, 0xFF12151C, 0xFF3A4A63, 0xFFFFFFFF, 0xFFDDE3EC, 0xFFCDD3DD,
            listOf(0xFFF4F6FA, 0xFFB8C2D0, 0xFFE3E8F0, 0xFFAEB9C9), 0xFF12151C,
            listOf(0xFFF1F3F7, 0xFFE6EBF2, 0xFFEDEFF3), listOf(0xFFDDE3EC, 0xFFE6F6FF, 0xFFEDE6F7),
            listOf(0xFFB8C2D0 to 0.5f, 0xFFD6DEE8 to 0.6f, 0xFFAEE6FF to 0.35f, 0xFF12151C to 0.18f),
            listOf(0xFFC9D3E0, 0xFFAEE6FF, 0xFFE3E8F0, 0xFFF0D9FF, 0xFF12151C)
        ) else P(
            0xFF0B0D12, 0xFF1A1E27, 0xFFEEF1F6, 0xFFB3BAC6, 0xFFC9D3E0, 0xFFC9D3E0, 0xFF12151C, 0xFF2A3142, 0xFF2E3442,
            listOf(0xFFF4F6FA, 0xFFB8C2D0, 0xFFE3E8F0, 0xFFAEB9C9), 0xFF12151C,
            listOf(0xFF222733, 0xFF1F2430, 0xFF262A36), listOf(0xFF2A3142, 0xFF1B3040, 0xFF2E2840),
            listOf(0xFFC9D3E0 to 0.14f, 0xFF8C97A8 to 0.18f, 0xFFAEE6FF to 0.14f, 0xFFEEF1F6 to 0.25f),
            listOf(0xFFC9D3E0, 0xFFAEE6FF, 0xFFE3E8F0, 0xFFF0D9FF, 0xFFEEF1F6)
        )
        "aero" -> if (!dark) P(
            0xFFDFF4FF, 0xFFFFFFFF, 0xFF0E2A3A, 0xFF3D5B6B, 0xFF0E5A7A, 0xFF0A6E9C, 0xFFFFFFFF, 0xFFD4F0FF, 0xFFBFE3F2,
            listOf(0xFFB6F09C, 0xFF7FE3C4, 0xFF5CCFF0), 0xFF0E2A3A,
            listOf(0xFFF0FBFF, 0xFFEFFCE8, 0xFFE6F9F6), listOf(0xFFD4F0FF, 0xFFE3F9D6, 0xFFD6F6EE),
            listOf(0xFFFFFFFF to 0.7f, 0xFF7FD8FF to 0.3f, 0xFFB6F09C to 0.35f, 0xFF0E5A7A to 0.15f),
            listOf(0xFF7FD8FF, 0xFFB6F09C, 0xFF3FD0C9, 0xFFFFFFFF, 0xFF0E2A3A)
        ) else P(
            0xFF06223A, 0xFF0E3350, 0xFFE8F8FF, 0xFFA9CDE0, 0xFF7FD8FF, 0xFF7FE0FF, 0xFF06223A, 0xFF15445F, 0xFF1C4A66,
            listOf(0xFFB6F09C, 0xFF7FE3C4, 0xFF5CCFF0), 0xFF06223A,
            listOf(0xFF103A58, 0xFF12404A, 0xFF113A52), listOf(0xFF15445F, 0xFF1E4A38, 0xFF17484A),
            listOf(0xFF7FD8FF to 0.18f, 0xFF3FD0C9 to 0.20f, 0xFFB6F09C to 0.16f, 0xFFE8F8FF to 0.20f),
            listOf(0xFF7FD8FF, 0xFFB6F09C, 0xFF3FD0C9, 0xFFE8F8FF, 0xFFE8F8FF)
        )
        else -> if (!dark) P(
            0xFFF5F1FF, 0xFFFFFFFF, 0xFF17122B, 0xFF4E4766, 0xFF17122B, 0xFF5B2FD6, 0xFFFFFFFF, 0xFFEDE6FF, 0xFFD9D2EA,
            listOf(0xFFFFB8E6, 0xFFD3C4FF, 0xFFA8F0FF), 0xFF17122B,
            listOf(0xFFFFF0FA, 0xFFF3EEFF, 0xFFEDFBFF), listOf(0xFFFFE3F4, 0xFFDDF8FF, 0xFFEDE6FF),
            listOf(0xFFFFC2E9 to 0.6f, 0xFFB5F2FF to 0.6f, 0xFFD6C6FF to 0.6f, 0xFF17122B to 0.22f),
            listOf(0xFFFFB8E6, 0xFFA8F0FF, 0xFFD3C4FF, 0xFFFFD3C2, 0xFF17122B)
        ) else P(
            0xFF0F0C1D, 0xFF1C1733, 0xFFF4F0FF, 0xFFBEB5DD, 0xFFD9CCFF, 0xFFC8B4FF, 0xFF17122B, 0xFF2E2552, 0xFF3A3160,
            listOf(0xFFFFB8E6, 0xFFD3C4FF, 0xFFA8F0FF), 0xFF17122B,
            listOf(0xFF241D40, 0xFF1E2140, 0xFF2A1F3E), listOf(0xFF3A2350, 0xFF173A47, 0xFF2B2457),
            listOf(0xFFFF5FD2 to 0.22f, 0xFF39D9FF to 0.20f, 0xFF8B6CFF to 0.25f, 0xFFE9E1FF to 0.25f),
            listOf(0xFFFFB8E6, 0xFFA8F0FF, 0xFFD3C4FF, 0xFFFFD3C2, 0xFFF4F0FF)
        )
    }
    val ink = c(p.ink)
    val grad = p.gradient.map(::c)
    // A gradient along the button (chrome: a metal band across it).
    val brush = if (set == "chrome") Brush.verticalGradient(grad) else Brush.linearGradient(grad, Offset.Zero, Offset.Infinite)
    val surface = c(p.surface)
    val fills = when (cards) {
        // Glass: the card colour, see-through (LookTest checks text on it over the page).
        "glass" -> listOf(surface.copy(alpha = if (dark) 0.78f else 0.72f))
        "glossy" -> p.glossy.map(::c)
        // Sticker: white paper in light, the card colour in dark (so text keeps its colour).
        else -> listOf(if (dark) surface else Color.White)
    }
    return Look(
        "y2k", dark,
        lookScheme(dark, c(p.primary), c(p.onPrimary), c(p.container), ink, c(p.bg), surface, ink, c(p.muted), c(p.line), c(p.outlineVariant)),
        fills, p.tints.map(::c), c(p.line), listOf(grad[1]),
        p.blobs.map { (h, a) -> c(h).copy(alpha = a) },
        y2kDisplay, if (dark) c(0xFF4CD98A) else c(0xFF1F8A4C), if (dark) c(0xFFFF7B6E) else c(0xFFC4321F),
        accent = grad[1], onAccent = c(p.onFill), art = p.art.map(::c), accentBrush = brush, shadowBrush = brush,
        set = set, deco = deco, cardStyle = cards
    )
}

/** Y2K's card shape: rounded in Holographic, liquid (two big corners) in Chrome, very round in Bubbly. */
internal fun Look.y2kCardShape(small: Boolean): Shape = when (set) {
    "chrome" -> androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (small) 26.dp else 32.dp, topEnd = 10.dp, bottomEnd = if (small) 26.dp else 32.dp, bottomStart = 10.dp
    )
    "aero" -> androidx.compose.foundation.shape.RoundedCornerShape(if (small) 26.dp else 30.dp)
    else -> androidx.compose.foundation.shape.RoundedCornerShape(if (small) 22.dp else 26.dp)
}

/**
 * The shadow under a Y2K surface: a holographic (or chrome, or aqua) hard shadow for sticker cards and buttons; glass
 * and glossy cards a soft coloured glow instead. Made once per size.
 */
internal fun Modifier.y2kShadow(look: Look, shape: Shape, raised: Boolean, depth: Float, card: Boolean): Modifier {
    if (!raised) return this
    val o = (depth * 0.6f).coerceIn(2f, 5f).dp
    if (!card || look.cardStyle == "sticker") return hardShadow(look.shadows[0], shape, o, look.shadowBrush)
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val off = o.toPx()
        val glow = look.art[1].copy(alpha = if (look.dark) 0.35f else 0.45f)
        onDrawBehind { translate(0f, off) { drawOutline(outline, glow) } }
    }
}

/**
 * What goes over a Y2K surface: its line (thick for sticker cards, a white rim for glass), and the gloss: a shine over
 * the top half of buttons and glossy cards, a highlight along the top of glass. Made once per size.
 */
internal fun Modifier.y2kFinish(look: Look, shape: Shape, card: Boolean): Modifier = drawWithCache {
    val small = size.height < 64.dp.toPx()
    val sticker = card && look.cardStyle == "sticker"
    val glass = card && look.cardStyle == "glass"
    val w = (if (sticker) 3.dp else if (glass) 1.5.dp else look.edge).toPx()
    val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    val rim = if (glass) Color.White.copy(alpha = if (look.dark) 0.35f else 0.85f) else look.line
    // Buttons and chips: a shine over their top half. Cards: only a thin highlight along the top edge, so text never
    // sits on a lighter band (2.203: in dark the card shine read as a grey bar behind names).
    val gloss = !sticker && (small || look.cardStyle == "glossy" || glass)
    val band = if (small) size.height * 0.42f else minOf(8.dp.toPx(), size.height * 0.1f)
    val shine = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (!small) (if (look.dark) 0.18f else 0.7f) else if (look.dark) 0.3f else 0.6f), Color.White.copy(alpha = 0f)),
        size.height * 0.04f, size.height * 0.04f + band
    )
    val inner = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(
            Rect(Offset(size.width * (if (small) 0.05f else 0.08f), size.height * 0.04f), Size(size.width * (if (small) 0.9f else 0.84f), band)),
            CornerRadius(if (small) size.height * 0.25f else band / 2)
        ))
    }
    val st = Stroke(w)
    onDrawWithContent {
        if (gloss) drawPath(inner, shine)
        drawContent()
        translate(w / 2, w / 2) { drawOutline(outline, rim, style = st) }
    }
}

/**
 * Stickers on Y2K cards (owner, 13 November 2026): a star, a heart or a planet on a top corner, white-edged like a real
 * sticker; Wild: a second one on the other corner. Calm: none. Made once per size.
 */
internal fun Modifier.y2kStickers(look: Look, id: Long): Modifier = if (look.deco == 0) this else drawWithCache {
    val u = 1.dp.toPx()
    val k = Math.floorMod(id, 3L).toInt()
    val r = 15 * u
    val right = Math.floorMod(id, 2L) == 0L
    val first = Offset(if (right) size.width - 16 * u else 16 * u, 4 * u)
    val second = Offset(if (right) 18 * u else size.width - 18 * u, size.height - 8 * u)
    val cols = listOf(look.art[0], look.art[1], look.art[2])
    onDrawWithContent {
        drawContent()
        sticker(k, first, r, cols[k])
        if (look.deco == 2) sticker((k + 1) % 3, second, r * 0.85f, cols[(k + 1) % 3])
    }
}

private fun DrawScope.sticker(kind: Int, c: Offset, r: Float, col: Color) {
    val ink = Color(0xFF17122B)
    val path = when (kind) {
        0 -> sparklePath(c, r)
        1 -> Path().apply {
            moveTo(c.x, c.y + r * 0.8f)
            cubicTo(c.x - r * 1.5f, c.y - r * 0.1f, c.x - r * 0.6f, c.y - r * 1.2f, c.x, c.y - r * 0.35f)
            cubicTo(c.x + r * 0.6f, c.y - r * 1.2f, c.x + r * 1.5f, c.y - r * 0.1f, c.x, c.y + r * 0.8f); close()
        }
        else -> Path().apply { addOval(Rect(c, r * 0.62f)) }
    }
    // The white edge of a sticker, then the colour, then a thin ink line.
    drawPath(path, Color.White, style = Stroke(r * 0.38f, join = StrokeJoin.Round))
    drawPath(path, col)
    drawPath(path, ink, style = Stroke(r * 0.09f, join = StrokeJoin.Round))
    if (kind == 2) rotate(-20f, c) { drawOval(ink, Offset(c.x - r, c.y - r * 0.25f), Size(r * 2, r * 0.5f), style = Stroke(r * 0.1f)) }
}

/** A four-pointed sparkle around [c] with arm length [r]. */
internal fun sparklePath(c: Offset, r: Float): Path = Path().apply {
    val t = r * 0.18f
    moveTo(c.x, c.y - r)
    quadraticTo(c.x + t, c.y - t, c.x + r, c.y); quadraticTo(c.x + t, c.y + t, c.x, c.y + r)
    quadraticTo(c.x - t, c.y + t, c.x - r, c.y); quadraticTo(c.x - t, c.y - t, c.x, c.y - r); close()
}

/**
 * Y2K's page per sub-theme, made once per size: Holographic glows, orbit rings and sparkles; Chrome liquid-metal blobs
 * and glints; Bubbly a sky with clouds, bubbles and droplets. Calm: only the soft glow; Wild: more of everything.
 */
internal fun Modifier.y2kBackdrop(look: Look): Modifier = drawWithCache {
    val w = size.width; val h = size.height
    val u = 1.dp.toPx()
    val n = when (look.deco) { 0 -> 0; 1 -> 1; else -> 2 }
    val sparks = buildList {
        if (n >= 1) addAll(listOf(Offset(w * 0.86f, 120 * u) to 10 * u, Offset(w * 0.1f, 260 * u) to 7 * u, Offset(w * 0.9f, h * 0.62f) to 8 * u,
            Offset(w * 0.18f, h * 0.84f) to 6 * u))
        if (n >= 2) addAll(listOf(Offset(w * 0.55f, 40 * u) to 6 * u, Offset(w * 0.06f, h * 0.48f) to 9 * u, Offset(w * 0.7f, h * 0.36f) to 5 * u,
            Offset(w * 0.94f, h * 0.9f) to 7 * u, Offset(w * 0.32f, h * 0.7f) to 5 * u))
    }.map { sparklePath(it.first, it.second) }
    val bubbles = buildList {
        if (n >= 1) addAll(listOf(Offset(w * 0.9f, 140 * u) to 26 * u, Offset(w * 0.08f, h * 0.42f) to 18 * u, Offset(w * 0.85f, h * 0.72f) to 22 * u))
        if (n >= 2) addAll(listOf(Offset(w * 0.2f, 70 * u) to 12 * u, Offset(w * 0.6f, h * 0.88f) to 16 * u, Offset(w * 0.05f, h * 0.8f) to 10 * u,
            Offset(w * 0.95f, h * 0.45f) to 9 * u))
    }
    val ring = Stroke(1.3f * u)
    onDrawBehind {
        // The soft glows every sub-theme shares (its own colours).
        drawCircle(Brush.radialGradient(listOf(look.blobs[0], Color.Transparent), Offset(0f, 0f), w * 0.8f), w * 0.8f, Offset(0f, 0f))
        drawCircle(Brush.radialGradient(listOf(look.blobs[1], Color.Transparent), Offset(w, h * 0.45f), w * 0.75f), w * 0.75f, Offset(w, h * 0.45f))
        drawCircle(Brush.radialGradient(listOf(look.blobs[2], Color.Transparent), Offset(w * 0.3f, h), w * 0.85f), w * 0.85f, Offset(w * 0.3f, h))
        when (look.set) {
            "chrome" -> {
                // Liquid-metal blobs at the edges, with a glint.
                if (n >= 1) {
                    listOf(Offset(w * 1.02f, 90 * u) to 60 * u, Offset(-10 * u, h * 0.55f) to 46 * u).forEach { (c0, r) ->
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = look.blobs[1].alpha), look.blobs[0]), c0 - Offset(r * 0.3f, r * 0.3f), r), r, c0)
                    }
                }
                sparks.forEach { drawPath(it, look.blobs[3]) }
            }
            "aero" -> {
                // Clouds, then bubbles with a highlight, then sparkly droplets.
                if (n >= 1) listOf(Offset(w * 0.25f, 50 * u), Offset(w * 0.78f, h * 0.28f)).forEach { c0 ->
                    for ((dx, r) in listOf(-28f to 18f, 0f to 24f, 26f to 16f)) drawCircle(look.blobs[0], r * u, Offset(c0.x + dx * u, c0.y))
                }
                bubbles.forEach { (c0, r) ->
                    drawCircle(look.blobs[3], r, c0, style = Stroke(1.4f * u))
                    drawArc(look.blobs[0], 200f, 70f, false, Offset(c0.x - r * 0.7f, c0.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(2f * u, cap = StrokeCap.Round))
                }
                sparks.forEach { drawPath(it, look.blobs[3]) }
            }
            else -> {
                // Holographic: an orbit ring, a ring and sparkles.
                if (n >= 1) {
                    rotate(-18f, Offset(w * 0.82f, 110 * u)) { drawOval(look.blobs[3], Offset(w * 0.82f - 70 * u, 88 * u), Size(140 * u, 44 * u), style = ring) }
                    drawCircle(look.blobs[3], 34 * u, Offset(w * 0.1f, h * 0.5f), style = ring)
                }
                if (n >= 2) rotate(14f, Offset(w * 0.3f, h * 0.9f)) { drawOval(look.blobs[3], Offset(w * 0.3f - 60 * u, h * 0.9f - 18 * u), Size(120 * u, 36 * u), style = ring) }
                sparks.forEach { drawPath(it, look.blobs[3]) }
            }
        }
    }
}

/** A Y2K tick box: a gel bubble; ticked, filled with the sub-theme's gradient and a white tick with a dark edge. */
internal fun DrawScope.y2kCheckbox(look: Look, checked: Boolean) {
    val s = size.minDimension
    val r = CornerRadius(s * 0.3f)
    val tl = Offset((size.width - s) / 2, (size.height - s) / 2)
    if (checked) drawRoundRect(look.accentBrush!!, tl, Size(s, s), r) else drawRoundRect(look.scheme.surfaceVariant, tl, Size(s, s), r)
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), tl.y, tl.y + s * 0.5f),
        Offset(tl.x + s * 0.12f, tl.y + s * 0.08f), Size(s * 0.76f, s * 0.36f), CornerRadius(s * 0.18f))
    drawRoundRect(look.line, tl, Size(s, s), r, style = Stroke(s * 0.08f))
    if (checked) {
        val tick = Path().apply { moveTo(tl.x + s * 0.24f, tl.y + s * 0.52f); lineTo(tl.x + s * 0.43f, tl.y + s * 0.7f); lineTo(tl.x + s * 0.78f, tl.y + s * 0.3f) }
        drawPath(tick, look.onAccent, style = Stroke(s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A Y2K choice circle: a glossy orb; chosen, filled with the gradient and a sparkle in it. */
internal fun DrawScope.y2kRadio(look: Look, selected: Boolean) {
    val s = size.minDimension
    val c = center
    if (selected) drawCircle(look.accentBrush!!, s * 0.45f, c) else drawCircle(look.scheme.surfaceVariant, s * 0.45f, c)
    drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), c.y - s * 0.4f, c.y),
        Offset(c.x - s * 0.28f, c.y - s * 0.38f), Size(s * 0.56f, s * 0.3f))
    drawCircle(look.line, s * 0.45f, c, style = Stroke(s * 0.08f))
    if (selected) drawPath(sparklePath(c, s * 0.22f), look.onAccent)
}

/** A Y2K switch: a glossy capsule (the gradient when on) and a pearl knob with a shine. */
internal fun DrawScope.y2kSwitch(look: Look, t: Float) {
    val w = size.width; val h = size.height
    val r = CornerRadius(h / 2)
    drawRoundRect(look.scheme.surfaceVariant, Offset.Zero, size, r)
    if (t > 0f) drawRoundRect(look.accentBrush!!, Offset.Zero, size, r, alpha = t)
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0f)), 0f, h * 0.5f),
        Offset(h * 0.3f, h * 0.08f), Size(w - h * 0.6f, h * 0.38f), CornerRadius(h * 0.2f))
    drawRoundRect(look.line, Offset.Zero, size, r, style = Stroke(1.5.dp.toPx()))
    val k = Offset(h / 2 + (w - h) * t, h / 2)
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE6E6F0)), k - Offset(h * 0.12f, h * 0.12f), h * 0.42f), h * 0.36f, k)
    drawCircle(look.line, h * 0.36f, k, style = Stroke(1.5.dp.toPx()))
}

/** Y2K's divider: a thin line in the sub-theme's gradient with a sparkle in the middle. */
internal fun DrawScope.y2kDivider(look: Look) {
    val y = size.height / 2
    drawLine(look.accentBrush!!, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
    drawPath(sparklePath(Offset(size.width / 2, y), 5.dp.toPx()), look.line)
}

/** Y2K's emblem per sub-theme: a ringed planet (Holographic), a chrome orb with a glint (Chrome), a bubble (Bubbly). */
internal fun DrawScope.y2kEmblem(look: Look) {
    val s = size.minDimension
    val c = center
    val a = look.art
    when (look.set) {
        "chrome" -> {
            drawCircle(Brush.radialGradient(listOf(Color.White, a[0], Color(0xFF7E8898)), c - Offset(s * 0.12f, s * 0.14f), s * 0.5f), s * 0.38f, c)
            drawCircle(Color(0xFF12151C), s * 0.38f, c, style = Stroke(s * 0.03f))
            drawPath(sparklePath(Offset(c.x + s * 0.3f, c.y - s * 0.3f), s * 0.14f), Color.White)
            drawPath(sparklePath(Offset(c.x + s * 0.3f, c.y - s * 0.3f), s * 0.14f), Color(0xFF12151C), style = Stroke(s * 0.02f))
        }
        "aero" -> {
            drawCircle(Brush.radialGradient(listOf(a[1].copy(alpha = 0.6f), a[0].copy(alpha = 0.9f)), c, s * 0.45f), s * 0.4f, c)
            drawArc(Color.White, 200f, 70f, false, Offset(c.x - s * 0.3f, c.y - s * 0.3f), Size(s * 0.6f, s * 0.6f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
            drawCircle(look.line, s * 0.4f, c, style = Stroke(s * 0.025f))
        }
        else -> {
            drawCircle(Brush.linearGradient(listOf(a[0], a[2], a[1])), s * 0.34f, c)
            drawCircle(look.line, s * 0.34f, c, style = Stroke(s * 0.025f))
            rotate(-22f, c) { drawOval(look.line, Offset(c.x - s * 0.5f, c.y - s * 0.13f), Size(s, s * 0.26f), style = Stroke(s * 0.03f)) }
            drawPath(sparklePath(Offset(c.x + s * 0.38f, c.y - s * 0.36f), s * 0.11f), look.line)
        }
    }
}

/** Y2K's icons (our own drawings): rounded and bold, with sparkle accents; the + is a star-burst. */
object Y2kIcons {
    private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in strokes) b.addPath(addPathNodes(p), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        for (p in fills) b.addPath(addPathNodes(p), fill = SolidColor(Color.Black))
        return b.build()
    }
    /** A small sparkle at (x, y) with arm r, as path data. */
    private fun spark(x: Float, y: Float, r: Float): String {
        val t = r * 0.2f
        return "M$x,${y - r} Q${x + t},${y - t} ${x + r},$y Q${x + t},${y + t} $x,${y + r} Q${x - t},${y + t} ${x - r},$y Q${x - t},${y - t} $x,${y - r} Z"
    }
    val Add by lazy { icon("Add", emptyList(), listOf("M12,2.5 Q13.2,10.8 21.5,12 Q13.2,13.2 12,21.5 Q10.8,13.2 2.5,12 Q10.8,10.8 12,2.5 Z")) }
    val Home by lazy { icon("Home", listOf("M4.5,11 L12,4.8 L19.5,11", "M6.5,9.5 L6.5,18.5 Q6.5,19.5 7.5,19.5 L16.5,19.5 Q17.5,19.5 17.5,18.5 L17.5,9.5"), listOf(spark(12f, 14.2f, 2.8f))) }
    val Favorite by lazy { icon("Heart", listOf("M12,19.5 C4.5,14.5 4,10.5 5.2,8 C6.5,5.5 10,5.4 12,8.2 C14,5.4 17.5,5.5 18.8,8 C20,10.5 19.5,14.5 12,19.5 Z"), listOf(spark(18.8f, 4.4f, 2.4f))) }
    val Notifications by lazy { icon("Bell", listOf("M6.5,16.5 Q7.5,14.5 7.5,10.5 Q7.5,6 12,6 Q16.5,6 16.5,10.5 Q16.5,14.5 17.5,16.5 Z", "M10.3,19 Q12,20.5 13.7,19"), listOf(spark(19.2f, 5f, 2.4f))) }
    val Person by lazy { icon("Person", listOf("M12,4.8 A3.4,3.4 0 1,1 11.99,4.8 Z", "M5.2,19.5 Q6,14 12,14 Q18,14 18.8,19.5"), listOf(spark(19.5f, 4.5f, 2.2f))) }
    val Search by lazy { icon("Search", listOf("M10.5,4.8 A5.7,5.7 0 1,1 10.49,4.8 Z", "M14.8,14.8 L19.5,19.5"), listOf(spark(8.6f, 8.8f, 1.8f))) }
    val Settings by lazy { icon("Settings", listOf("M12,8.8 A3.2,3.2 0 1,1 11.99,8.8 Z"), listOf("M12,1.8 Q12.9,6.6 15.6,4.4 Q14.6,7.6 19.5,6.4 Q16.4,9.4 22.2,12 Q16.4,14.6 19.5,17.6 Q14.6,16.4 15.6,19.6 Q12.9,17.4 12,22.2 Q11.1,17.4 8.4,19.6 Q9.4,16.4 4.5,17.6 Q7.6,14.6 1.8,12 Q7.6,9.4 4.5,6.4 Q9.4,7.6 8.4,4.4 Q11.1,6.6 12,1.8 Z M12,9.2 A2.8,2.8 0 1,0 12.01,9.2 Z")) }
    val Chat by lazy { icon("Chat", listOf("M5,5.5 L19,5.5 Q20.5,5.5 20.5,7 L20.5,14 Q20.5,15.5 19,15.5 L10,15.5 L6,19 L6.5,15.5 L5,15.5 Q3.5,15.5 3.5,14 L3.5,7 Q3.5,5.5 5,5.5 Z"), listOf(spark(12f, 10.5f, 2.6f))) }
    val Bag by lazy { icon("Bag", listOf("M6,8 L18,8 L17.2,19.5 Q17.1,20.5 16,20.5 L8,20.5 Q6.9,20.5 6.8,19.5 Z", "M9,8 Q9,4.5 12,4.5 Q15,4.5 15,8"), listOf(spark(12f, 14.2f, 2.6f))) }
    val MoreVert by lazy { icon("More", emptyList(), listOf(spark(12f, 5f, 2.6f), spark(12f, 12f, 2.6f), spark(12f, 19f, 2.6f))) }
    val Share by lazy { icon("Share", listOf("M17,3.8 A2,2 0 1,1 16.99,3.8 Z", "M7,10 A2,2 0 1,1 6.99,10 Z", "M17,16.2 A2,2 0 1,1 16.99,16.2 Z", "M8.8,11 L15.2,7", "M8.8,13 L15.2,17")) }
    val Delete by lazy { icon("Delete", listOf("M5,7 L19,7", "M9.5,7 Q9.5,4.5 12,4.5 Q14.5,4.5 14.5,7", "M6.8,7 L7.6,19 Q7.7,20 8.7,20 L15.3,20 Q16.3,20 16.4,19 L17.2,7"), listOf(spark(12f, 13.5f, 2.4f))) }
}
