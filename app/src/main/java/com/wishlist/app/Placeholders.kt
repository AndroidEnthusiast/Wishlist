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
    when (kind) {
        "group", "friend" -> Icon(AppIcons.Person, null, tint = tint, modifier = Modifier.size(size))
        "birthday" -> Icon(AppIcons.Notifications, null, tint = tint, modifier = Modifier.size(size))
        "hello" -> Icon(AppIcons.Favorite, null, tint = tint, modifier = Modifier.size(size))
        "secret" -> Icon(AppIcons.Lock, null, tint = tint, modifier = Modifier.size(size))
        else -> GiftIcon(tint, size)
    }
}
