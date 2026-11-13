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
    else -> Color(0xFF14161F)
}

/** The style's emblem, drawn in the whole of this room (our own drawings). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.emblem(look: Look) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val a = look.art
    when (look.design) {
        "y2k" -> {
            // A disc with an orbit ring around it and two sparkles.
            drawCircle(Brush.linearGradient(listOf(a[0], a[2], a[1])), s * 0.34f, c)
            drawCircle(look.line, s * 0.34f, c, style = Stroke(s * 0.025f))
            rotate(-22f, c) { drawOval(look.line, Offset(c.x - s * 0.5f, c.y - s * 0.13f), Size(s, s * 0.26f), style = Stroke(s * 0.03f)) }
            listOf(Offset(c.x + s * 0.38f, c.y - s * 0.36f) to s * 0.11f, Offset(c.x - s * 0.4f, c.y + s * 0.34f) to s * 0.08f).forEach { (p, r) ->
                val t = r * 0.18f
                drawPath(Path().apply {
                    moveTo(p.x, p.y - r); quadraticTo(p.x + t, p.y - t, p.x + r, p.y); quadraticTo(p.x + t, p.y + t, p.x, p.y + r)
                    quadraticTo(p.x - t, p.y + t, p.x - r, p.y); quadraticTo(p.x - t, p.y - t, p.x, p.y - r); close()
                }, look.line)
            }
        }
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
