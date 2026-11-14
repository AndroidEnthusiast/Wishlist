package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The look of the app (since 2.97, owner): one signature look, **Quiet** (calm and precise, like Apple Wallet and
 * Photos: one clean typeface, bold tight headings, grouped white cards on a soft grey page, true black in dark), and
 * **Gen Z chaos** (chaos maximalism: clashing hot pink, lime and yellow, tilted cards, thick lines, Bungee headings,
 * handwritten Caveat labels, polka dots; saved as it was in 2.198 in themes/gen-z-chaos). Both have light and dark
 * ([Repo.theme]) and the accent colour you pick ([ACCENTS], [Repo.palette]).
 * History: Neobrutalism, Neumorphism and Cyberpunk went on 13 November 2026; twelve more styles (2.199–2.205) were
 * removed on 14 November 2026 (owner: not to a professional standard). A new design style is made the way
 * docs/DESIGN-STYLES.md describes, one at a time.
 */

/** A style: its key ([Repo.design]), its name and one line about it (the Themer). */
class StyleInfo(val key: String, val name: String, val about: String)

val STYLES: List<StyleInfo> = listOf(
    StyleInfo("quiet", "Quiet", "Calm and precise: clean cards on a soft grey page, bold headings, true black in dark."),
    StyleInfo("chaos", "Gen Z chaos", "Full chaos: clashing colours, tilted cards, stickers and handwriting.")
)

/** The style with this key, or Quiet (also for the styles that were removed). */
fun styleFor(key: String): StyleInfo = STYLES.firstOrNull { it.key == key } ?: STYLES[0]

/** An accent colour: the tone for light and the (lighter) tone for dark. */
class Accent(val key: String, val name: String, val light: Long, val dark: Long)

val ACCENTS: List<Accent> = listOf(
    Accent("blue", "Blue", 0xFF0A6CFF, 0xFF4D94FF),
    Accent("indigo", "Indigo", 0xFF4C4CD6, 0xFF8585FF),
    Accent("green", "Green", 0xFF1F9D55, 0xFF3DDC84),
    Accent("orange", "Orange", 0xFFF2711C, 0xFFFF9A52),
    Accent("pink", "Pink", 0xFFE5367A, 0xFFFF6FA3)
)

fun accentFor(key: String): Accent = ACCENTS.firstOrNull { it.key == key } ?: customAccent(key) ?: ACCENTS[0]

/**
 * Any colour (owner, 2.101): picked on the colour ball in the Themer and stored as "#RRGGBB". The same colour in light
 * and in dark (owner, 2.102: what you pick is what you get).
 */
fun customAccent(key: String): Accent? {
    if (!Regex("#[0-9A-Fa-f]{6}").matches(key)) return null
    val argb = 0xFF000000L or key.substring(1).toLong(16)
    return Accent(key.uppercase(), "Your colour", argb, argb)
}

/** A colour as "#RRGGBB". */
fun colourKey(col: Color): String = "#%06X".format(col.toArgb() and 0xFFFFFF)

/** Whether the accent [key] may be hard to read on the page in light or dark (contrast under 2.5:1): a warning only. */
fun hardToRead(key: String): Boolean {
    val ac = accentFor(key)
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
        return if (la > lb) la / lb else lb / la
    }
    return contrast(Color(ac.light), Color(0xFFF7F7F9)) < 2.5f || contrast(Color(ac.dark), Color(0xFF121214)) < 2.5f
}


/** Everything the screens need to draw the chosen look. Get it with LocalLook.current. */
class Look(
    /** "quiet" or "chaos". */
    val design: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    /** Card colours, used in turn. */
    val fills: List<Color>,
    /** Soft colours behind pictures, used in turn. */
    val tints: List<Color>,
    /** Lines: hairlines (Quiet), thick ink (chaos). */
    val line: Color,
    /** Shadow colours: the hard shadow (chaos), the soft shadow (Quiet). */
    val shadows: List<Color>,
    /** Colours of what is drawn behind the screens (chaos's circles and dots). */
    val blobs: List<Color>,
    /** The font of names, headings and prices. */
    val display: FontFamily?,
    /** A cheaper and a dearer price. */
    val drop: Color,
    val rise: Color,
    /** The colour of filled blocks (buttons, the chosen chip, Add wish, the + buttons) and what goes on them. */
    val accent: Color = scheme.primary,
    val onAccent: Color = scheme.onPrimary
) {
    /** The signature look. (Kept under the old name: "classic" meant the plain standard look.) */
    val classic: Boolean get() = design == "quiet"
}

val LocalLook = staticCompositionLocalOf { lookFor("quiet", "blue", "light") }

private fun c(v: Long) = Color(v)
private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
private fun pick(id: Long, n: Int) = Math.floorMod(id, n.toLong()).toInt()
/** Dark ink on light colours, white on dark ones. */
private fun inkOn(bg: Color) = if (bg.luminance() > 0.5f) c(0xFF0B0B0F) else Color.White

/** Quiet (2.207): white or near-black on [bg], whichever reads better (the dark accents are mid-light: dark text). */
private fun bestInk(bg: Color): Color {
    fun ratio(a: Color) = (maxOf(a.luminance(), bg.luminance()) + 0.05f) / (minOf(a.luminance(), bg.luminance()) + 0.05f)
    val ink = c(0xFF0B0B1E)
    return if (ratio(Color.White) >= ratio(ink)) Color.White else ink
}

val figtree by lazy { google("Figtree") }
/** Headings of Quiet (owner, 7 November 2026: "a heading font with character"): a soft serif. */
private val fraunces by lazy { google("Fraunces") }
private val bungee by lazy { google("Bungee") }
private val bricolage by lazy { google("Bricolage Grotesque") }
private val caveat by lazy { google("Caveat") }

/** All the colours of a scheme from a few, plus the surface steps that menus and dialogs use. */
private fun scheme(
    dark: Boolean, primary: Color, onPrimary: Color, container: Color, onContainer: Color,
    bg: Color, surface: Color, text: Color, muted: Color, outline: Color, outlineVariant: Color,
    error: Color? = null
): ColorScheme {
    val step = { t: Float -> mix(bg, text, t) }
    val s = if (!dark) lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, onTertiaryContainer = onContainer,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
        surfaceTint = Color.Transparent, inverseSurface = text, inverseOnSurface = bg, inversePrimary = container,
        surfaceBright = surface, surfaceDim = step(0.06f),
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = step(0.06f)
    ) else darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, onTertiaryContainer = onContainer,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
        surfaceTint = Color.Transparent, inverseSurface = text, inverseOnSurface = bg, inversePrimary = container,
        surfaceBright = step(0.12f), surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = step(0.14f)
    )
    return if (error == null) s else s.copy(error = error)
}

/**
 * The look for a style ([style], one of [STYLES]), an accent ([accentKey], one of [ACCENTS] or "#RRGGBB") and a mode
 * ("light" or "dark"; anything else counts as dark).
 */
fun lookFor(style: String, accentKey: String, mode: String): Look {
    val dark = mode != "light"
    val ac = accentFor(accentKey)
    val a = c(if (dark) ac.dark else ac.light)
    val onA = inkOn(a)
    val white = Color.White
    val greenL = c(0xFF1F8A4C); val redL = c(0xFFC4321F)
    val greenD = c(0xFF4CD98A); val redD = c(0xFFFF7B6E)
    val drop = if (dark) greenD else greenL
    val rise = if (dark) redD else redL
    return when (styleFor(style).key) {
        "chaos" -> {
            val bg = if (dark) c(0xFF0D0D0D) else c(0xFFFFF7E8)
            val surface = if (dark) c(0xFF1B1B1B) else white
            val ink = if (dark) white else c(0xFF0D0D0D)
            val muted = if (dark) c(0xFFE6E6E6) else c(0xFF333333)
            val hot = c(0xFFFF2BD6); val sun = c(0xFFFFE600); val lime = c(0xFFC6FF00)
            Look(
                "chaos", dark,
                scheme(dark, ink, if (dark) c(0xFF0D0D0D) else white, mix(a, surface, 0.6f), ink, bg, surface, ink, muted, ink, mix(ink, bg, 0.7f)),
                listOf(surface), listOf(lime, mix(hot, white, 0.35f), sun, mix(a, white, 0.45f)), ink,
                listOf(if (dark) hot else ink), listOf(hot, sun, lime, ink.copy(alpha = if (dark) 0.10f else 0.12f)),
                bungee, drop, rise, accent = a, onAccent = onA
            )
        }
        else -> {
            // Quiet (polished in 2.207, docs/styles/quiet.md): the text on your colour is whichever of white and ink
            // reads better; photos sit on a light surface in light and dark (owner, 14 November 2026).
            val onQ = bestInk(a)
            val bg = if (dark) Color.Black else c(0xFFF2F2F7)
            // Your colour as text and lines (text buttons, links, chosen tabs): in light the same colour, darkened just
            // enough to read at 4.5 : 1 on the grey page (green, orange and pink were too light, found 14 November
            // 2026). Filled buttons keep the colour itself ([Look.accent]).
            val inkText = if (dark) c(0xFFF5F5F7) else c(0xFF111114)
            val readable = (0..20).map { mix(a, inkText, it / 20f) }.first { col ->
                (maxOf(col.luminance(), bg.luminance()) + 0.05f) / (minOf(col.luminance(), bg.luminance()) + 0.05f) >= 4.6f
            }
            val surface = if (dark) c(0xFF1C1C1E) else white
            val text = if (dark) c(0xFFF5F5F7) else c(0xFF111114)
            val muted = if (dark) c(0xFF98989F) else c(0xFF6C6C74)
            val hair = if (dark) c(0xFF2C2C2E) else c(0xFFE5E5EA)
            Look(
                "quiet", dark,
                // The outline (fields, tick boxes) at least 3 : 1 on the cards, so they can be found (13 November 2026).
                scheme(dark, readable, bestInk(readable), mix(a, surface, if (dark) 0.78f else 0.88f), mix(a, text, if (dark) 0.25f else 0.35f), bg, surface, text, muted,
                    if (dark) c(0xFF7C7C82) else c(0xFF8E8E93), hair),
                listOf(surface), listOf(if (dark) c(0xFFE9E9EC) else c(0xFFF4F4F7)), hair,
                listOf(Color.Black.copy(alpha = if (dark) 0.5f else 0.06f)), emptyList(),
                figtree, drop, rise, accent = a, onAccent = onQ
            )
        }
    }
}

private fun TextStyle.styled(f: FontFamily, w: FontWeight, spacing: Float? = null) =
    copy(fontFamily = f, fontWeight = w, letterSpacing = if (spacing == null) letterSpacing else spacing.sp)

/** The type of a style: a heading font (display, headlines, big titles) and a text font, and the labels' font. */
private fun styleType(
    head: FontFamily, headW: FontWeight, headSpacing: Float?, body: FontFamily, titleW: FontWeight, bodyW: FontWeight,
    label: FontFamily, labelW: FontWeight, small: FontFamily = label, smallW: FontWeight = labelW, labelSpacing: Float? = null
): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.styled(head, headW, headSpacing), displayMedium = d.displayMedium.styled(head, headW, headSpacing),
        displaySmall = d.displaySmall.styled(head, headW, headSpacing), headlineLarge = d.headlineLarge.styled(head, headW, headSpacing),
        headlineMedium = d.headlineMedium.styled(head, headW, headSpacing), headlineSmall = d.headlineSmall.styled(head, headW, headSpacing),
        titleLarge = d.titleLarge.styled(head, headW, headSpacing),
        titleMedium = d.titleMedium.styled(body, titleW), titleSmall = d.titleSmall.styled(body, titleW),
        bodyLarge = d.bodyLarge.styled(body, bodyW), bodyMedium = d.bodyMedium.styled(body, bodyW), bodySmall = d.bodySmall.styled(body, bodyW),
        labelLarge = d.labelLarge.styled(label, labelW, labelSpacing), labelMedium = d.labelMedium.styled(label, labelW, labelSpacing),
        labelSmall = d.labelSmall.styled(small, smallW, labelSpacing)
    )
}

/**
 * The fonts of each style. Quiet: Fraunces headings (a soft serif, since 7 November 2026) with Figtree text. Chaos:
 * Bungee headings, Bricolage Grotesque text, handwritten Caveat for small labels. (Bungee comes in one weight.)
 */
fun typographyFor(design: String, @Suppress("UNUSED_PARAMETER") classic: Typography? = null): Typography = when (design) {
    "chaos" -> styleType(bungee, FontWeight.Normal, 0f, bricolage, FontWeight.Bold, FontWeight.Normal, bricolage, FontWeight.Bold, caveat, FontWeight.Bold)
    else -> quietType()
}

/**
 * Quiet's type scale (2.207, docs/styles/quiet.md): Fraunces SemiBold for titles (28 for the wordmark and the main
 * tabs, 20 for screen titles, sections and dialogs), Figtree for the rest with clear steps: prices 17, names 16, text
 * 15, details 13, nothing under 12. Prices use figures of equal width so they line up.
 */
private fun quietType(): Typography {
    val d = Typography()
    fun f(base: TextStyle, family: FontFamily, w: FontWeight, size: Int, line: Int, spacing: Float = 0f) =
        base.copy(fontFamily = family, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)
    val head = FontWeight.SemiBold
    return Typography(
        displayLarge = f(d.displayLarge, fraunces, head, 48, 56, -0.5f), displayMedium = f(d.displayMedium, fraunces, head, 40, 48, -0.4f),
        displaySmall = f(d.displaySmall, fraunces, head, 34, 40, -0.3f), headlineLarge = f(d.headlineLarge, fraunces, head, 30, 36, -0.3f),
        headlineMedium = f(d.headlineMedium, fraunces, head, 28, 34, -0.3f), headlineSmall = f(d.headlineSmall, fraunces, head, 28, 34, -0.3f),
        titleLarge = f(d.titleLarge, fraunces, head, 20, 26, -0.2f),
        titleMedium = f(d.titleMedium, figtree, FontWeight.SemiBold, 17, 22).copy(fontFeatureSettings = "tnum"),
        titleSmall = f(d.titleSmall, figtree, FontWeight.Medium, 16, 21),
        bodyLarge = f(d.bodyLarge, figtree, FontWeight.Normal, 16, 22), bodyMedium = f(d.bodyMedium, figtree, FontWeight.Normal, 15, 21),
        bodySmall = f(d.bodySmall, figtree, FontWeight.Normal, 13, 18),
        labelLarge = f(d.labelLarge, figtree, FontWeight.SemiBold, 15, 20), labelMedium = f(d.labelMedium, figtree, FontWeight.Medium, 13, 18),
        labelSmall = f(d.labelSmall, figtree, FontWeight.Medium, 12, 16, 0.1f)
    )
}

/** The weight of screen titles in the top bar (Bungee comes in one weight; Fraunces looks best a little lighter). */
val Look.titleWeight: FontWeight
    get() = if (design == "chaos") FontWeight.Normal else FontWeight.SemiBold

/** The weight of prices. */
val Look.priceWeight: FontWeight
    get() = if (design == "chaos") FontWeight.Normal else FontWeight.Bold

/** Icon style: rounded Material icons in both styles (AppIcons in Styled.kt). */
@Suppress("unused")
val Look.iconStyle: String get() = "rounded"

/** The thickness of the style's lines around cards, buttons, fields and badges: thick ink in chaos, none in Quiet. */
val Look.edge: Dp get() = if (design == "chaos") 3.dp else 0.dp

/** The Material shapes (dialogs, menus, text fields, snackbars) of each style. */
fun shapesFor(design: String): Shapes = when (design) {
    "chaos" -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
    )
    else -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
    )
}

/** The shape of dialogs: the style's largest shape. */
val Look.dialogShape: Shape get() = shapesFor(design).extraLarge

/** The shape of a card. */
@Suppress("UNUSED_PARAMETER")
fun Look.cardShape(id: Long, small: Boolean): Shape =
    if (design == "chaos") RoundedCornerShape(if (small) 14.dp else 18.dp) else RoundedCornerShape(20.dp)

/** Space around the picture inside a card (none since the neumorphic style went). */
@Suppress("UNUSED_PARAMETER")
fun Look.inset(small: Boolean): Dp = 0.dp

@Suppress("UNUSED_PARAMETER")
fun Look.pictureShape(id: Long, small: Boolean): Shape = RectangleShape

/** Behind a picture without a photo: a soft colour of the look (a photo gets its own Blur stage, PhotoStage). */
fun Look.pictureBg(id: Long, hasImage: Boolean): Color =
    // Quiet dark: a small icon without a photo (Notifications, Prices) keeps a dark mat; photos get the light surface.
    if (design == "quiet" && dark && !hasImage) c(0xFF2A2A2D) else tints[pick(id, tints.size)]

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
