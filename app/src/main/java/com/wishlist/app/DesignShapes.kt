package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* The styles (see Design.kt): shapes, card colours, shadows, lines and the surfaces drawn behind things. */

/** The Material shapes (dialogs, menus, text fields, snackbars) of each style. */
fun shapesFor(design: String): Shapes = when (design) {
    "chaos" -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
    )
    // Quiet (2.210): smooth squircle corners everywhere (owner, 15 November 2026, docs/styles/identity.md).
    else -> Shapes(
        extraSmall = Squircle(10.dp), small = Squircle(12.dp), medium = Squircle(16.dp),
        large = Squircle(22.dp), extraLarge = Squircle(28.dp)
    )
}

/** The shape of dialogs: the style's largest shape. */
val Look.dialogShape: Shape get() = shapesFor(design).extraLarge

/** The shape of a card. */
@Suppress("UNUSED_PARAMETER")
fun Look.cardShape(id: Long, small: Boolean): Shape =
    if (design == "chaos") RoundedCornerShape(if (small) 14.dp else 18.dp) else Squircle(20.dp)

/** Space around the picture inside a card (none since the neumorphic style went). */
@Suppress("UNUSED_PARAMETER")
fun Look.inset(small: Boolean): Dp = 0.dp

@Suppress("UNUSED_PARAMETER")
fun Look.pictureShape(id: Long, small: Boolean): Shape = RectangleShape

/** Behind a picture without a photo: a soft colour of the look (a photo gets its own Blur stage, PhotoStage). */
fun Look.pictureBg(id: Long, hasImage: Boolean): Color =
    // Quiet dark: a small icon without a photo (Notifications, Prices) keeps a dark mat; photos get the light surface.
    if (design == "quiet" && dark && !hasImage) raised else tints[pick(id, tints.size)]

/**
 * The surface every wish photo sits on (Quiet 2.207: one calm light surface in light and dark, the owner's choice, so
 * white shop photos blend in and dark products stay visible), and the colour of what is drawn on it without a photo.
 */
fun Look.photoSurface(id: Long): Color = tints[pick(id, tints.size)]
val Look.photoInk: Color get() = if (design == "quiet") c(0xFF5E5E66) else scheme.onSurfaceVariant

/** A line under the picture: chaos's thick ink. */
val Look.pictureLine: Dp get() = edge

fun Look.cardFill(id: Long): Color = fills[pick(id, fills.size)]

/** The text colour on a card. */
fun Look.cardInk(@Suppress("UNUSED_PARAMETER") id: Long): Color = scheme.onSurface

/** The quieter text (shop, description, icons) on a card. */
fun Look.cardMuted(@Suppress("UNUSED_PARAMETER") id: Long): Color = scheme.onSurfaceVariant

/** Small labels on a card (price change, "Owned"). */
fun Look.badgeShape(small: Boolean): Shape =
    if (design == "chaos") RoundedCornerShape(10.dp) else RoundedCornerShape(if (small) 6.dp else 8.dp)

/** Small buttons such as the category button and the view switch. */
val Look.chipShape: Shape get() = if (design == "chaos") RoundedCornerShape(12.dp) else CircleShape

/** The Add wish button (and the + in the bottom bar). */
val Look.fabShape: Shape get() = if (design == "chaos") RoundedCornerShape(16.dp) else CircleShape

/** The shape of ordinary buttons. */
val Look.buttonShape: Shape get() = if (design == "chaos") RoundedCornerShape(14.dp) else CircleShape

/** Gen Z chaos tilts cards a little, each its own way (by its id). */
fun Look.tilt(id: Long): Float = if (design == "chaos") listOf(-1.6f, 1.2f, -0.8f, 1.8f, -1.2f, 0.9f)[pick(id, 6)] else 0f

/** A hard shadow: the shape again, moved down and to the right, in one flat colour (or a gradient). */
fun Modifier.hardShadow(color: Color, shape: Shape, offset: Dp = 4.dp, brush: Brush? = null): Modifier = drawWithCache {
    // The outline is made once per size, not on every frame (2.202: scrolling lagged).
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind { translate(o, o) { if (brush != null) drawOutline(outline, brush) else drawOutline(outline, color) } }
}

/**
 * A line along the inside of [shape]'s edge, like Modifier.border, but drawn from an outline made once per size.
 * (Modifier.border paints every card with an irregular shape, torn, wobbly or ticket, into a picture of its own first;
 * with a list of cards that made scrolling lag, 2.202. PerformanceRulesTest keeps Modifier.border out of the styles.)
 */
fun Modifier.edgeLine(width: Dp, color: Color, shape: Shape): Modifier = if (width <= 0.dp || color.alpha == 0f) this else drawWithCache {
    val w = width.toPx()
    val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    val stroke = Stroke(w)
    onDrawWithContent { drawContent(); translate(w / 2, w / 2) { drawOutline(outline, color, style = stroke) } }
}

/**
 * Any surface in the chosen style: a card, a dialog, a button, a chip, the bottom bar. [fill] its colour; [raised]
 * false = without the hard shadow (chaos). [depth] is kept for callers (Quiet draws no shadow here). The content is
 * clipped to [shape].
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.lookSurface(look: Look, shape: Shape, fill: Color, raised: Boolean = true, depth: Dp = 6.dp): Modifier =
    if (look.design == "chaos") (if (raised) hardShadow(look.shadows[0], shape, 5.dp) else this).clip(shape).background(fill).edgeLine(3.dp, look.line, shape)
    // Quiet: just the card colour; in light a hairline so white cards don't melt into each other.
    else clip(shape).background(fill).then(if (!look.dark) Modifier.edgeLine(1.dp, Color.Black.copy(alpha = 0.04f), shape) else Modifier)

/** The frame of a wish card (or any card) in the chosen style: its colour, lines, shadow, shape and (chaos) tilt. */
fun Modifier.lookCard(look: Look, id: Long, shape: Shape): Modifier =
    (if (look.design == "chaos") rotate(look.tilt(id)) else this).lookSurface(look, shape, look.cardFill(id), depth = 8.dp)

/** The mat a picture sits on inside a card. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.lookMat(look: Look, shape: Shape, fill: Color, over: Boolean = false): Modifier = clip(shape).background(fill)

/** The frame of a small button (category, view switch, a chip). [on] = chosen. */
fun Modifier.lookChip(look: Look, on: Boolean = false): Modifier {
    val cs = look.scheme
    val shape = look.chipShape
    return if (look.design == "chaos") (if (on) hardShadow(look.shadows[0], shape, 3.dp) else this)
        .clip(shape).background(if (on) look.tints[0] else cs.surfaceVariant).edgeLine(2.dp, look.line, shape)
    // Quiet (2.207): a white pill with a hairline; the chosen one in a soft wash of your colour (no black pills).
    else clip(shape).background(if (on) cs.primaryContainer else cs.surfaceVariant)
        .then(if (on) Modifier else Modifier.edgeLine(1.dp, cs.outlineVariant, shape))
}

/** The colour of the text and icons on a chip made with [lookChip]. */
fun Look.chipContent(on: Boolean): Color = when {
    !on -> scheme.onSurface
    design == "quiet" -> scheme.onPrimaryContainer
    else -> Color(0xFF0D0D0D)
}

/**
 * What is drawn behind the screens: chaos's polka dots and big coloured circles. Quiet stays plain.
 */
fun Modifier.lookBackdrop(look: Look): Modifier = when (look.design) {
    "chaos" -> drawBehind {
        val dots = look.blobs[3]
        val step = 18.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) { drawCircle(dots, 1.2.dp.toPx(), Offset(x, y)); x += step }
            y += step
        }
        drawCircle(look.blobs[0].copy(alpha = 0.85f), 70.dp.toPx(), Offset(size.width * 0.92f, 60.dp.toPx()))
        drawCircle(look.blobs[1].copy(alpha = 0.85f), 40.dp.toPx(), Offset(size.width * 0.06f, 340.dp.toPx()))
        drawCircle(look.blobs[2].copy(alpha = 0.85f), 56.dp.toPx(), Offset(size.width * 0.98f, 620.dp.toPx()))
    }
    else -> this
}

/** The accent as a colour for text, icons and focus rings (your accent in both styles). */
val Look.textAccent: Color get() = accent

/** Quiet's warm neutrals (2.210, docs/styles/identity.md), in one place for the parts that draw them directly. */
object QuietColours {
    val pageLight = Color(0xFFF7F5F2)
    val pageDark = Color(0xFF121014)
    /** Cards in dark: a step lighter than the page. */
    val cardDark = Color(0xFF1E1A20)
    /** What floats in dark (bottom bar, panels, menus, dialogs): a step lighter again. */
    val raisedDark = Color(0xFF2B262D)
}
