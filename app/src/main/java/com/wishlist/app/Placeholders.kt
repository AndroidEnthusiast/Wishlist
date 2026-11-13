package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box

/*
 * Pictures for empty places (2.97, owner: Material icons instead of emoji). Google's core Material icons have no
 * gift, so the gift is drawn here in the same outline style (our own drawing, allowed by the licence rule); the
 * others are Material icons from [AppIcons].
 */

/** A gift box with a ribbon and a bow, as a line icon in [tint]. */
@Composable
fun GiftIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // The lid and the box.
        drawRoundRect(tint, Offset(3f * u, 8f * u), Size(18f * u, 4.5f * u), CornerRadius(1.2f * u), style = st)
        drawRoundRect(tint, Offset(4.5f * u, 12.5f * u), Size(15f * u, 8.5f * u), CornerRadius(1.2f * u), style = st)
        // The ribbon down the middle.
        drawLine(tint, Offset(12f * u, 8f * u), Offset(12f * u, 21f * u), 1.8f * u, StrokeCap.Round)
        // The bow: two loops.
        val bow = Path().apply {
            moveTo(12f * u, 8f * u)
            cubicTo(9.5f * u, 3.2f * u, 5.8f * u, 4.6f * u, 7.4f * u, 7f * u)
            cubicTo(8.2f * u, 8.1f * u, 10.4f * u, 8f * u, 12f * u, 8f * u)
            moveTo(12f * u, 8f * u)
            cubicTo(14.5f * u, 3.2f * u, 18.2f * u, 4.6f * u, 16.6f * u, 7f * u)
            cubicTo(15.8f * u, 8.1f * u, 13.6f * u, 8f * u, 12f * u, 8f * u)
        }
        drawPath(bow, tint, style = st)
    }
}

/**
 * The picture of an empty place: "gift" (a wish without a photo, an empty list), "group" (a wishlist for someone),
 * "friend", "birthday", "hello" or "secret". [size] the icon's size, [tint] its colour (the quieter text colour by
 * default).
 */
@Composable
fun PlaceholderIcon(kind: String = "gift", size: Dp = 24.dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val look = LocalLook.current
    // From 32 dp (a wish without a photo, an empty page): the style's own emblem, with the icon a little smaller on it
    // (owner, 13 November 2026: decoration in every style). The emblem stays inside the icon's own room.
    // Anthropomorphic: the gift box with a face itself (owner, 13 November 2026); other kinds get their icon beside it.
    if (size >= 32.dp && look.design == "anthro") {
        Box(Modifier.size(size).drawBehind { giftBuddy(look, wave = kind == "hello") }) {
            if (kind != "gift") Box(
                Modifier.align(Alignment.TopEnd).size(size * 0.36f).clip(androidx.compose.foundation.shape.CircleShape).background(look.scheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { PlainPlaceholder(kind, size * 0.24f, look.scheme.onSurface) }
        }
        return
    }
    if (size >= 32.dp && look.design !in setOf("quiet", "chaos")) {
        Box(Modifier.size(size).drawBehind { emblem(look) }, contentAlignment = Alignment.Center) {
            PlainPlaceholder(kind, size * 0.5f, emblemInk(look))
        }
        return
    }
    PlainPlaceholder(kind, size, tint)
}

/** The colour of the icon on a style's emblem (dark on light colours, light on dark ones). */
private fun emblemInk(look: Look): Color = when (look.design) {
    "y2k" -> Color(0xFF17122B)
    "retro" -> look.scheme.onSurface
    "pop" -> Color(0xFF1B1A3A)
    "grunge" -> Color(0xFFF4EFE8)
    "flat" -> Color.White
    "doodle", "japandi", "cottage" -> look.scheme.onSurface
    "riso" -> Color(0xFF232056)
    "swiss" -> Color.White
    "punk" -> punkEmblemInk(look)
    "synth" -> Color(0xFF1A0B33)
    else -> Color(0xFF14161F)
}

/** The style's emblem, drawn in the whole of this room (our own drawings). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.emblem(look: Look) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val a = look.art
    when (look.design) {
        "y2k" -> y2kEmblem(look)
        "retro" -> {
            // A sunburst: mustard rays around a cream disc with a cherry ring.
            for (i in 0 until 16) rotate(i * 22.5f, c) {
                drawPath(Path().apply {
                    moveTo(c.x - s * 0.05f, c.y); lineTo(c.x, c.y - s * 0.5f); lineTo(c.x + s * 0.05f, c.y); close()
                }, a[1])
            }
            drawCircle(a[0], s * 0.32f, c)
            drawCircle(look.scheme.surfaceVariant, s * 0.27f, c)
        }
        "pop" -> {
            // A sunset planet with stripes cut through its lower half and a teal ring.
            drawCircle(Brush.verticalGradient(listOf(a[0], a[1], a[2]), c.y - s * 0.4f, c.y + s * 0.4f), s * 0.4f, c)
            for (i in 0 until 3) drawRect(look.scheme.surfaceVariant, Offset(c.x - s * 0.4f, c.y + s * (0.14f + i * 0.09f)), Size(s * 0.8f, s * (0.025f + i * 0.012f)))
            rotate(-15f, c) { drawOval(a[3], Offset(c.x - s * 0.5f, c.y - s * 0.08f), Size(s, s * 0.16f), style = Stroke(s * 0.04f)) }
        }
        "grunge" -> {
            // A splat of red ink with drops around it.
            drawCircle(a[0], s * 0.36f, c)
            for (k in 0 until 10) {
                val ang = k * 0.63 + 0.3
                val d = s * (0.36f + (k % 3) * 0.05f)
                drawCircle(a[0], s * (0.04f + (k % 4) * 0.015f), Offset(c.x + (kotlin.math.cos(ang) * d).toFloat(), c.y + (kotlin.math.sin(ang) * d).toFloat()))
            }
        }
        "flat" -> {
            // A teal disc with two flat hills in it.
            drawCircle(a[0], s * 0.46f, c)
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, s * 0.46f)) }
            clipPath(clip) {
                drawCircle(a[1], s * 0.5f, Offset(c.x - s * 0.3f, c.y + s * 0.62f))
                drawCircle(a[4], s * 0.45f, Offset(c.x + s * 0.35f, c.y + s * 0.66f))
            }
        }
        "doodle" -> doodleEmblem(look)
        "cottage" -> {
            // A little wreath: a sage ring of leaves around a cream middle, two terracotta berries.
            drawCircle(a[0], s * 0.42f, c, style = Stroke(s * 0.05f))
            for (i in 0 until 10) rotate(i * 36f, c) { drawOval(a[0], Offset(c.x - s * 0.05f, c.y - s * 0.5f), Size(s * 0.1f, s * 0.16f)) }
            drawCircle(look.scheme.surfaceVariant, s * 0.33f, c)
            drawCircle(a[1], s * 0.05f, Offset(c.x + s * 0.3f, c.y + s * 0.3f)); drawCircle(a[1], s * 0.05f, Offset(c.x + s * 0.38f, c.y + s * 0.22f))
        }
        "japandi" -> {
            // An ensō (an open brushed circle) around a soft oat disc.
            drawCircle(a[2].copy(alpha = 0.6f), s * 0.36f, c)
            drawArc(look.scheme.onSurface, 120f, 310f, false, Offset(c.x - s * 0.44f, c.y - s * 0.44f), Size(s * 0.88f, s * 0.88f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
        }
        "riso" -> {
            // A pink and a blue disc printed off each other.
            drawCircle(a[1], s * 0.36f, Offset(c.x + s * 0.06f, c.y + s * 0.05f))
            drawCircle(a[0], s * 0.36f, c)
            drawCircle(a[2], s * 0.12f, Offset(c.x + s * 0.32f, c.y - s * 0.32f))
        }
        "synth" -> {
            // A striped sunset sun over a little neon grid.
            drawCircle(Brush.verticalGradient(listOf(a[0], a[1], a[2]), c.y - s * 0.4f, c.y + s * 0.4f), s * 0.4f, c)
            for (i in 0 until 4) drawRect(look.scheme.surfaceVariant, Offset(c.x - s * 0.4f, c.y + s * (0.08f + i * 0.08f)), Size(s * 0.8f, s * (0.015f + i * 0.01f)))
            drawLine(a[3], Offset(c.x - s * 0.5f, c.y + s * 0.42f), Offset(c.x + s * 0.5f, c.y + s * 0.42f), s * 0.03f)
        }
        "punk" -> punkEmblem(look)
        "swiss" -> {
            // A square on the grid: a black frame, a quarter in the strong colour and the icon on a colour block.
            drawRect(look.scheme.onSurface, Offset(c.x - s * 0.42f, c.y - s * 0.42f), Size(s * 0.84f, s * 0.84f), style = Stroke(s * 0.04f))
            drawArc(a[0].copy(alpha = 0.35f), 180f, 90f, true, Offset(c.x - s * 0.42f, c.y - s * 0.42f), Size(s * 0.84f * 2, s * 0.84f * 2))
            drawRect(a[0], Offset(c.x - s * 0.24f, c.y - s * 0.24f), Size(s * 0.48f, s * 0.48f))
        }
        else -> {
            // Bauhaus: a red quarter circle, a blue square corner and a yellow circle in the middle.
            drawArc(a[0], 180f, 90f, true, Offset(c.x - s * 0.5f, c.y - s * 0.5f), Size(s, s))
            drawRect(a[1], Offset(c.x, c.y), Size(s * 0.5f, s * 0.5f))
            drawCircle(a[2], s * 0.3f, c)
        }
    }
}

@Composable
private fun PlainPlaceholder(kind: String, size: Dp, tint: Color) {
    when (kind) {
        "group", "friend" -> Icon(AppIcons.Person, null, tint = tint, modifier = Modifier.size(size))
        "birthday" -> Icon(AppIcons.Notifications, null, tint = tint, modifier = Modifier.size(size))
        "hello" -> Icon(AppIcons.Favorite, null, tint = tint, modifier = Modifier.size(size))
        "secret" -> Icon(AppIcons.Lock, null, tint = tint, modifier = Modifier.size(size))
        else -> GiftIcon(tint, size)
    }
}

/** A camera, drawn in the same lines as [GiftIcon] (our own drawing): the chat's photo button (2.109). */
@Composable
fun PhotoIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // The body with the bump on top.
        val body = Path().apply {
            moveTo(4.5f * u, 7.5f * u)
            lineTo(7.6f * u, 7.5f * u)
            lineTo(9.2f * u, 5f * u)
            lineTo(14.8f * u, 5f * u)
            lineTo(16.4f * u, 7.5f * u)
            lineTo(19.5f * u, 7.5f * u)
            quadraticBezierTo(21f * u, 7.5f * u, 21f * u, 9f * u)
            lineTo(21f * u, 17.5f * u)
            quadraticBezierTo(21f * u, 19f * u, 19.5f * u, 19f * u)
            lineTo(4.5f * u, 19f * u)
            quadraticBezierTo(3f * u, 19f * u, 3f * u, 17.5f * u)
            lineTo(3f * u, 9f * u)
            quadraticBezierTo(3f * u, 7.5f * u, 4.5f * u, 7.5f * u)
            close()
        }
        drawPath(body, tint, style = st)
        // The lens.
        drawCircle(tint, 3.6f * u, Offset(12f * u, 13f * u), style = st)
    }
}

/**
 * Anthropomorphic's gift box with a face (owner, 13 November 2026), our own drawing: a box with a ribbon, a lid and a
 * bow on its head, big eyes looking up, rosy cheeks, a smile and little arms ([wave]: one arm up, saying hello).
 * Outlined in cartoon ink, so it reads on light and dark alike.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.giftBuddy(look: Look, wave: Boolean = false, side: Float = size.minDimension, at: Offset? = null) {
    val s = side
    val x0 = at?.x ?: ((size.width - s) / 2); val y0 = at?.y ?: ((size.height - s) / 2)
    fun p(x: Float, y: Float) = Offset(x0 + x * s, y0 + y * s)
    val ink = Color(0xFF1A1A2E)
    val st = Stroke(s * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val body = look.art[0]; val lid = look.art[2]; val ribbon = look.art[3]
    // Arms (behind the box).
    drawLine(ink, p(0.22f, 0.66f), p(0.08f, 0.76f), s * 0.05f, StrokeCap.Round)
    if (wave) drawLine(ink, p(0.78f, 0.62f), p(0.93f, 0.42f), s * 0.05f, StrokeCap.Round)
    else drawLine(ink, p(0.78f, 0.66f), p(0.92f, 0.76f), s * 0.05f, StrokeCap.Round)
    drawCircle(Color.White, s * 0.045f, p(0.08f, 0.76f)); drawCircle(ink, s * 0.045f, p(0.08f, 0.76f), style = st)
    val hand = if (wave) p(0.93f, 0.42f) else p(0.92f, 0.76f)
    drawCircle(Color.White, s * 0.045f, hand); drawCircle(ink, s * 0.045f, hand, style = st)
    // The box, its ribbon and lid.
    drawRoundRect(body, p(0.2f, 0.42f), Size(s * 0.6f, s * 0.5f), CornerRadius(s * 0.08f))
    drawRect(ribbon, p(0.46f, 0.42f), Size(s * 0.08f, s * 0.5f))
    drawRoundRect(ink, p(0.2f, 0.42f), Size(s * 0.6f, s * 0.5f), CornerRadius(s * 0.08f), style = st)
    drawRoundRect(lid, p(0.15f, 0.3f), Size(s * 0.7f, s * 0.14f), CornerRadius(s * 0.05f))
    drawRect(ribbon, p(0.46f, 0.3f), Size(s * 0.08f, s * 0.14f))
    drawRoundRect(ink, p(0.15f, 0.3f), Size(s * 0.7f, s * 0.14f), CornerRadius(s * 0.05f), style = st)
    // The bow on its head.
    for (side in listOf(-1f, 1f)) {
        val bow = Path().apply {
            moveTo(x0 + 0.5f * s, y0 + 0.3f * s)
            cubicTo(x0 + (0.5f + side * 0.05f) * s, y0 + 0.1f * s, x0 + (0.5f + side * 0.24f) * s, y0 + 0.12f * s, x0 + (0.5f + side * 0.16f) * s, y0 + 0.28f * s)
            close()
        }
        drawPath(bow, ribbon); drawPath(bow, ink, style = st)
    }
    // The face: eyes looking up, cheeks, a smile.
    for (ex in listOf(0.37f, 0.63f)) {
        drawOval(Color.White, p(ex - 0.065f, 0.52f), Size(s * 0.13f, s * 0.15f))
        drawOval(ink, p(ex - 0.065f, 0.52f), Size(s * 0.13f, s * 0.15f), style = Stroke(s * 0.025f))
        drawCircle(ink, s * 0.035f, p(ex + 0.01f, 0.57f))
        drawCircle(Color.White, s * 0.012f, p(ex + 0.022f, 0.555f))
    }
    drawCircle(look.art[2].copy(alpha = 0.7f), s * 0.04f, p(0.29f, 0.74f)); drawCircle(look.art[2].copy(alpha = 0.7f), s * 0.04f, p(0.71f, 0.74f))
    drawArc(ink, 20f, 140f, false, p(0.41f, 0.68f), Size(s * 0.18f, s * 0.12f), style = st)
}

/**
 * Anthropomorphic: the gift box peeks over the top edge of a card (Home's profile card, a list's header card; owner,
 * 13 November 2026), its hands on the edge. Room is kept above the card for it; other styles: nothing.
 */
fun Modifier.lookPeek(look: Look): Modifier = if (look.design != "anthro") this else
    padding(top = 38.dp).drawBehind {
        // Behind the card: only the bow, the lid and the eyes show above its edge.
        // The eyes sit just above the edge, the box below it (hidden by the card).
        val s = 56.dp.toPx()
        giftBuddy(look, side = s, at = Offset(size.width - s - 18.dp.toPx(), -s * 0.76f))
    }.drawWithContent {
        drawContent()
        // The hands on the card's edge, over the card.
        val ink = Color(0xFF1A1A2E)
        val s = 56.dp.toPx()
        val cx = size.width - s / 2 - 18.dp.toPx()
        for (dx in listOf(-0.3f, 0.3f)) {
            val h = Offset(cx + dx * s, 0f)
            drawCircle(Color.White, s * 0.06f, h)
            drawCircle(ink, s * 0.06f, h, style = Stroke(s * 0.035f))
        }
    }
