package com.wishlist.app

import android.graphics.BlurMaskFilter
import android.os.Build
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The look of the app (since 2.97, owner): one signature look, **Quiet** (calm and precise, like Apple Wallet and
 * Photos: one clean typeface, bold tight headings, grouped white cards on a soft grey page, true black in dark), and
 * four styles that dress the same layout up in their own way:
 *  - neo:   Neobrutalism: thick ink lines, hard offset shadows (in dark they take the accent), a dot grid.
 *  - neu:   Neumorphism: everything in one colour, shaped by light and shade (raised cards, pressed-in pictures);
 *           text, prices and buttons keep strong contrast.
 *  - cyber: Cyberpunk: neon on near-black (a pale daylight terminal in light), cut-off corners, thin lines, scan lines,
 *           Rajdhani with monospace labels.
 *  - chaos: Gen Z chaos maximalism: clashing hot pink, lime and yellow, tilted cards, thick lines, Bungee headings,
 *           handwritten Caveat labels, polka dots.
 * Every style has light and dark ([Repo.theme]) and the same five accent colours ([ACCENTS], [Repo.palette]).
 * The old designs (Standard, Material 3, Organic, Claymorphism) and their colour sets are gone; see Repo.load for how
 * people who used them move over. Preview the owner chose from: https://claude.ai/artifact/NiJ5D8P4Xk8JQiJLwBMtKe
 */

/** A style: its key ([Repo.design]), its name and one line about it (the Themer). */
class StyleInfo(val key: String, val name: String, val about: String)

val STYLES: List<StyleInfo> = listOf(
    StyleInfo("quiet", "Quiet", "Calm and precise: clean cards on a soft grey page, bold headings, true black in dark."),
    StyleInfo("neo", "Neobrutalism", "Thick ink lines and hard shadows; in dark the shadows take your colour."),
    StyleInfo("neu", "Neumorphism", "One colour, shaped by light and shade: raised cards, pictures pressed in."),
    StyleInfo("cyber", "Cyberpunk", "Neon on near-black, cut-off corners, scan lines and monospace labels."),
    StyleInfo("chaos", "Gen Z chaos", "Full chaos: clashing colours, tilted cards, stickers and handwriting.")
)

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
    /** "quiet", "neo", "neu", "cyber" or "chaos". */
    val design: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    /** Card colours, used in turn. */
    val fills: List<Color>,
    /** Soft colours behind pictures, used in turn. */
    val tints: List<Color>,
    /** Lines: hairlines (Quiet), thick ink (Neobrutalism, chaos), neon (Cyberpunk). */
    val line: Color,
    /** Shadow colours: the hard shadow (neo, chaos); for neumorphism the shade and the light. */
    val shadows: List<Color>,
    /** Colours of what is drawn behind the screens (dots, neon, chaos circles). */
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

val figtree by lazy { google("Figtree") }
private val rajdhani by lazy { google("Rajdhani") }
private val techMono by lazy { google("Share Tech Mono") }
private val bungee by lazy { google("Bungee") }
private val bricolage by lazy { google("Bricolage Grotesque") }
private val caveat by lazy { google("Caveat") }

/** All the colours of a scheme from a few, plus the surface steps that menus and dialogs use. */
private fun scheme(
    dark: Boolean, primary: Color, onPrimary: Color, container: Color, onContainer: Color,
    bg: Color, surface: Color, text: Color, muted: Color, outline: Color, outlineVariant: Color
): ColorScheme {
    val step = { t: Float -> mix(bg, text, t) }
    return if (!dark) lightColorScheme(
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
}

/**
 * The look for a style ([style], one of [STYLES]), an accent ([accentKey], one of [ACCENTS]) and a mode ("light" or
 * "dark"; anything else counts as dark).
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
        "neo" -> {
            val bg = if (dark) c(0xFF141414) else c(0xFFF4F1EA)
            val surface = if (dark) c(0xFF1F1F1F) else white
            val ink = if (dark) c(0xFFFAFAFA) else c(0xFF111111)
            val muted = if (dark) c(0xFFC8C8C8) else c(0xFF454545)
            Look(
                "neo", dark,
                // Text and small controls in ink; the accent only fills blocks.
                scheme(dark, ink, if (dark) c(0xFF111111) else white, mix(a, surface, 0.7f), ink, bg, surface, ink, muted, ink, mix(ink, bg, 0.75f)),
                listOf(surface), listOf(mix(a, surface, if (dark) 0.72f else 0.74f)), ink,
                listOf(if (dark) a else ink), listOf(mix(ink, bg, 0.88f)),
                figtree, drop, rise, accent = a, onAccent = onA
            )
        }
        "neu" -> {
            val bg = if (dark) c(0xFF23262D) else c(0xFFE6E9EF)
            val text = if (dark) c(0xFFEEF0F5) else c(0xFF1A1C22)
            val muted = if (dark) c(0xFFA3A8B5) else c(0xFF555A66)
            val shade = if (dark) c(0xFF16181D) else c(0xFFC0C5CF)
            val light = if (dark) c(0xFF31353E) else white
            Look(
                "neu", dark,
                scheme(dark, a, onA, mix(a, bg, 0.8f), mix(a, text, 0.5f), bg, bg, text, muted, mix(text, bg, 0.6f), mix(text, bg, 0.88f)),
                listOf(bg), listOf(bg), mix(text, bg, 0.88f), listOf(shade, light), emptyList(),
                figtree, drop, rise
            )
        }
        "cyber" -> {
            val bg = if (dark) c(0xFF06070C) else c(0xFFEEF2F4)
            val surface = if (dark) c(0xFF0C1018) else white
            val text = if (dark) c(0xFFE8FBFF) else c(0xFF0A0D12)
            val muted = if (dark) c(0xFF7F98A6) else c(0xFF4C5A63)
            val neon = if (dark) c(0xFF00F0FF) else c(0xFF008EA3)
            val line = if (dark) neon.copy(alpha = 0.45f) else text
            Look(
                "cyber", dark,
                scheme(dark, a, onA, mix(a, surface, 0.8f), mix(a, text, 0.4f), bg, surface, text, muted, line, mix(text, bg, 0.85f)),
                listOf(surface), listOf(if (dark) c(0xFF0A0D14) else c(0xFFF6F8F9)), line,
                listOf(neon), listOf(neon),
                rajdhani, drop, rise
            )
        }
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
            // Quiet.
            val bg = if (dark) Color.Black else c(0xFFF2F2F7)
            val surface = if (dark) c(0xFF1C1C1E) else white
            val text = if (dark) c(0xFFF5F5F7) else c(0xFF111114)
            val muted = if (dark) c(0xFF98989F) else c(0xFF6C6C74)
            val hair = if (dark) c(0xFF2C2C2E) else c(0xFFE5E5EA)
            Look(
                "quiet", dark,
                scheme(dark, a, onA, mix(a, surface, if (dark) 0.78f else 0.86f), mix(a, text, 0.45f), bg, surface, text, muted,
                    if (dark) c(0xFF48484A) else c(0xFFC7C7CC), hair),
                listOf(surface), listOf(if (dark) c(0xFF2A2A2D) else c(0xFFF7F7FA)), hair,
                listOf(Color.Black.copy(alpha = if (dark) 0.5f else 0.06f)), emptyList(),
                figtree, drop, rise
            )
        }
    }
}

private fun TextStyle.styled(f: FontFamily, w: FontWeight, spacing: Float? = null) =
    copy(fontFamily = f, fontWeight = w, letterSpacing = if (spacing == null) letterSpacing else spacing.sp)

/** The type of a style: a heading font (display, headlines, big titles) and a text font, and the labels' font. */
private fun styleType(
    head: FontFamily, headW: FontWeight, headSpacing: Float?, body: FontFamily, titleW: FontWeight, bodyW: FontWeight,
    label: FontFamily, labelW: FontWeight, small: FontFamily = label, smallW: FontWeight = labelW
): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.styled(head, headW, headSpacing), displayMedium = d.displayMedium.styled(head, headW, headSpacing),
        displaySmall = d.displaySmall.styled(head, headW, headSpacing), headlineLarge = d.headlineLarge.styled(head, headW, headSpacing),
        headlineMedium = d.headlineMedium.styled(head, headW, headSpacing), headlineSmall = d.headlineSmall.styled(head, headW, headSpacing),
        titleLarge = d.titleLarge.styled(head, headW, headSpacing),
        titleMedium = d.titleMedium.styled(body, titleW), titleSmall = d.titleSmall.styled(body, titleW),
        bodyLarge = d.bodyLarge.styled(body, bodyW), bodyMedium = d.bodyMedium.styled(body, bodyW), bodySmall = d.bodySmall.styled(body, bodyW),
        labelLarge = d.labelLarge.styled(label, labelW), labelMedium = d.labelMedium.styled(label, labelW),
        labelSmall = d.labelSmall.styled(small, smallW)
    )
}

/**
 * The fonts of each style. Quiet, Neobrutalism and Neumorphism: Figtree (bold, tight headings). Cyberpunk: Rajdhani
 * with Share Tech Mono labels. Chaos: Bungee headings, Bricolage Grotesque text, handwritten Caveat for small labels.
 * (Fonts with one weight, Bungee and Share Tech Mono, are only asked for in that weight.)
 */
fun typographyFor(design: String, @Suppress("UNUSED_PARAMETER") classic: Typography? = null): Typography = when (design) {
    "neo" -> styleType(figtree, FontWeight.ExtraBold, -0.4f, figtree, FontWeight.Bold, FontWeight.Normal, figtree, FontWeight.Bold)
    "neu" -> styleType(figtree, FontWeight.Bold, -0.3f, figtree, FontWeight.SemiBold, FontWeight.Normal, figtree, FontWeight.SemiBold)
    "cyber" -> styleType(rajdhani, FontWeight.Bold, 0.6f, rajdhani, FontWeight.Bold, FontWeight.Medium, techMono, FontWeight.Normal)
    "chaos" -> styleType(bungee, FontWeight.Normal, 0f, bricolage, FontWeight.Bold, FontWeight.Normal, bricolage, FontWeight.Bold, caveat, FontWeight.Bold)
    else -> styleType(figtree, FontWeight.ExtraBold, -0.5f, figtree, FontWeight.SemiBold, FontWeight.Normal, figtree, FontWeight.SemiBold)
}

/** The weight of screen titles in the top bar. */
val Look.titleWeight: FontWeight
    get() = when (design) {
        "chaos" -> FontWeight.Normal
        "cyber", "neu" -> FontWeight.Bold
        else -> FontWeight.ExtraBold
    }

/** The weight of prices. */
val Look.priceWeight: FontWeight
    get() = when (design) {
        "chaos" -> FontWeight.Normal
        else -> FontWeight.Bold
    }

/** Icon style: "rounded" (Quiet, Neumorphism, chaos) or "sharp" (Neobrutalism, Cyberpunk). */
val Look.iconStyle: String
    get() = when (design) {
        "neo", "cyber" -> "sharp"
        else -> "rounded"
    }

/** The Material shapes (dialogs, menus, text fields, snackbars) of each style. */
fun shapesFor(design: String): Shapes = when (design) {
    "neo" -> Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(14.dp)
    )
    "neu" -> Shapes(
        extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)
    )
    "cyber" -> Shapes(
        extraSmall = CutCornerShape(4.dp), small = CutCornerShape(6.dp), medium = CutCornerShape(8.dp),
        large = CutCornerShape(10.dp), extraLarge = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
    )
    "chaos" -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
    )
    else -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
    )
}

/** The shape of a card. Cyberpunk cuts two corners off. */
fun Look.cardShape(id: Long, small: Boolean): Shape = when (design) {
    "neo" -> RoundedCornerShape(if (small) 12.dp else 14.dp)
    "neu" -> RoundedCornerShape(if (small) 20.dp else 24.dp)
    "cyber" -> CutCornerShape(topEnd = if (small) 12.dp else 16.dp, bottomStart = if (small) 12.dp else 16.dp)
    "chaos" -> RoundedCornerShape(if (small) 14.dp else 18.dp)
    else -> RoundedCornerShape(if (small) 16.dp else 20.dp)
}

/** Space around the picture inside a card: Neumorphism presses the picture into the card; the others fill the top. */
fun Look.inset(small: Boolean): Dp = if (design == "neu") (if (small) 8.dp else 10.dp) else 0.dp

fun Look.pictureShape(id: Long, small: Boolean): Shape = when (design) {
    "neu" -> RoundedCornerShape(if (small) 14.dp else 18.dp)
    else -> RectangleShape
}

/** Behind a picture without a photo: a soft colour of the look (a photo gets its own Blur stage, PhotoStage). */
fun Look.pictureBg(id: Long, hasImage: Boolean): Color = tints[pick(id, tints.size)]

/** A line under the picture: thick ink (Neobrutalism, chaos) or a thin neon line (Cyberpunk). */
val Look.pictureLine: Dp
    get() = when (design) {
        "neo" -> 2.dp
        "chaos" -> 3.dp
        "cyber" -> 1.dp
        else -> 0.dp
    }

fun Look.cardFill(id: Long): Color = fills[pick(id, fills.size)]

/** The text colour on a card. */
fun Look.cardInk(@Suppress("UNUSED_PARAMETER") id: Long): Color = scheme.onSurface

/** The quieter text (shop, description, icons) on a card. */
fun Look.cardMuted(@Suppress("UNUSED_PARAMETER") id: Long): Color = scheme.onSurfaceVariant

/** Small labels on a card (price change, "Owned"). */
fun Look.badgeShape(small: Boolean): Shape = when (design) {
    "neo" -> RoundedCornerShape(6.dp)
    "neu" -> CircleShape
    "cyber" -> CutCornerShape(topEnd = 5.dp, bottomStart = 5.dp)
    "chaos" -> RoundedCornerShape(10.dp)
    else -> RoundedCornerShape(if (small) 6.dp else 8.dp)
}

/** Small buttons such as the category button and the view switch. */
val Look.chipShape: Shape
    get() = when (design) {
        "neo" -> RoundedCornerShape(10.dp)
        "cyber" -> CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
        "chaos" -> RoundedCornerShape(12.dp)
        else -> CircleShape
    }

/** The Add wish button. */
val Look.fabShape: Shape
    get() = when (design) {
        "neo" -> RoundedCornerShape(12.dp)
        "cyber" -> CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
        "chaos" -> RoundedCornerShape(16.dp)
        else -> CircleShape
    }

/** The shape of ordinary buttons. */
val Look.buttonShape: Shape
    get() = when (design) {
        "neo" -> RoundedCornerShape(10.dp)
        "cyber" -> CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp)
        "chaos" -> RoundedCornerShape(14.dp)
        else -> CircleShape
    }

/** Gen Z chaos tilts cards a little, each its own way (by its id). */
fun Look.tilt(id: Long): Float = if (design == "chaos") listOf(-1.6f, 1.2f, -0.8f, 1.8f, -1.2f, 0.9f)[pick(id, 6)] else 0f

/** A hard shadow: the shape again, moved down and to the right, in one flat colour. */
fun Modifier.hardShadow(color: Color, shape: Shape, offset: Dp = 4.dp): Modifier = drawBehind {
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { drawOutline(outline, color) }
}

/**
 * Any surface in the chosen style: a card, a dialog, a button, a chip, the bottom bar. [fill] its colour; [raised]
 * false = pressed in (Neumorphism) or without the hard shadow (Neobrutalism, chaos). The content is clipped to [shape].
 */
fun Modifier.lookSurface(look: Look, shape: Shape, fill: Color, raised: Boolean = true, depth: Dp = 6.dp): Modifier = when (look.design) {
    "neo" -> (if (raised) hardShadow(look.shadows[0], shape, 4.dp) else this).clip(shape).background(fill).border(2.dp, look.line, shape)
    "chaos" -> (if (raised) hardShadow(look.shadows[0], shape, 5.dp) else this).clip(shape).background(fill).border(3.dp, look.line, shape)
    "neu" -> neu(look, shape, fill, depth, pressed = !raised)
    "cyber" -> clip(shape).background(fill).border(1.dp, look.line, shape)
    // Quiet: just the card colour; in light a hairline so white cards don't melt into each other.
    else -> clip(shape).background(fill).then(if (!look.dark) Modifier.border(1.dp, Color.Black.copy(alpha = 0.04f), shape) else Modifier)
}

/** The frame of a wish card (or any card) in the chosen style: its colour, lines, shadow, shape and (chaos) tilt. */
fun Modifier.lookCard(look: Look, id: Long, shape: Shape): Modifier =
    (if (look.design == "chaos") rotate(look.tilt(id)) else this).lookSurface(look, shape, look.cardFill(id), depth = 8.dp)

/** The mat a picture sits on inside a card. Neumorphism presses it in; the others simply fill it. */
fun Modifier.lookMat(look: Look, shape: Shape, fill: Color, over: Boolean = false): Modifier =
    if (look.design == "neu") neu(look, shape, fill, 5.dp, pressed = true, over = over) else clip(shape).background(fill)

/** Android 9 and newer can blur the neumorphic shadows; Android 8 gets a plain soft shadow. */
private val canBlur = Build.VERSION.SDK_INT >= 28

/**
 * Neumorphism: a shape in the background colour, raised by a light shadow top-left and a shade bottom-right, or
 * [pressed] in (the light and shade inside its edges instead). The content is clipped to [shape]. [over]: the pressed-in
 * light and shade are drawn over the content (the Blur stage of a wish photo fills the dent, which still looks pressed in).
 */
fun Modifier.neu(look: Look, shape: Shape, fill: Color, depth: Dp = 6.dp, pressed: Boolean = false, over: Boolean = false): Modifier {
    val shade = look.shadows[0]
    val light = look.shadows.getOrElse(1) { Color.White }
    if (!canBlur) {
        return (if (pressed) this else shadow(depth, shape, clip = false, ambientColor = shade, spotColor = shade))
            .clip(shape).background(if (pressed) lerp(fill, shade, 0.25f) else fill)
    }
    return drawWithCache {
        val d = depth.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val shapePath = Path().apply { addOutline(outline) }.asAndroidPath()
        fun paint(col: Color, blur: Float) = android.graphics.Paint().apply {
            isAntiAlias = true
            color = col.toArgb()
            maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        // Everything outside the shape moved by (dx, dy): drawn blurred inside the shape it becomes an inner shadow.
        fun frame(dx: Float, dy: Float) = android.graphics.Path().apply {
            val m = d * 4
            addRect(-m, -m, size.width + m, size.height + m, android.graphics.Path.Direction.CW)
            addPath(shapePath, dx, dy)
            fillType = android.graphics.Path.FillType.EVEN_ODD
        }
        val shadePaint = paint(shade, d * 1.2f)
        val lightPaint = paint(light, d * 1.2f)
        val down = android.graphics.Path(shapePath).apply { offset(d, d) }
        val up = android.graphics.Path(shapePath).apply { offset(-d, -d) }
        val innerShade = frame(d * 0.6f, d * 0.6f)
        val innerLight = frame(-d * 0.6f, -d * 0.6f)
        onDrawWithContent {
            val canvas = drawContext.canvas.nativeCanvas
            if (!pressed) {
                canvas.drawPath(down, shadePaint)
                canvas.drawPath(up, lightPaint)
            }
            canvas.save()
            canvas.clipPath(shapePath)
            drawRect(fill)
            if (pressed && !over) {
                canvas.drawPath(innerShade, shadePaint)
                canvas.drawPath(innerLight, lightPaint)
            }
            canvas.restore()
            drawContent()
            if (pressed && over) {
                canvas.save()
                canvas.clipPath(shapePath)
                canvas.drawPath(innerShade, shadePaint)
                canvas.drawPath(innerLight, lightPaint)
                canvas.restore()
            }
        }
    }.clip(shape)
}

/** The frame of a small button (category, view switch, a chip). [on] = chosen. */
fun Modifier.lookChip(look: Look, on: Boolean = false): Modifier {
    val cs = look.scheme
    val shape = look.chipShape
    return when (look.design) {
        "neo" -> (if (on) hardShadow(look.line, shape, 3.dp) else this)
            .clip(shape).background(if (on) look.accent else cs.surfaceVariant).border(2.dp, look.line, shape)
        "chaos" -> (if (on) hardShadow(look.shadows[0], shape, 3.dp) else this)
            .clip(shape).background(if (on) look.tints[0] else cs.surfaceVariant).border(2.dp, look.line, shape)
        "neu" -> if (on) neu(look, shape, look.accent, 4.dp) else neu(look, shape, cs.background, 4.dp)
        "cyber" -> clip(shape).background(if (on) look.accent else cs.surfaceVariant).border(1.dp, look.line, shape)
        // Quiet: a white pill, the chosen one dark (light in dark).
        else -> clip(shape).background(if (on) cs.onSurface else cs.surfaceVariant)
    }
}

/** The colour of the text and icons on a chip made with [lookChip]. */
fun Look.chipContent(on: Boolean): Color = when {
    !on -> scheme.onSurface
    design == "quiet" -> if (dark) Color.Black else Color.White
    design == "chaos" -> Color(0xFF0D0D0D)
    else -> onAccent
}

/**
 * What is drawn behind the screens: Neobrutalism's dot grid, Cyberpunk's scan lines (dark) or grid (light), chaos's
 * polka dots and big coloured circles. Quiet and Neumorphism stay plain.
 */
fun Modifier.lookBackdrop(look: Look): Modifier = when (look.design) {
    "neo" -> drawBehind {
        val step = 22.dp.toPx()
        val r = 1.2.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) { drawCircle(look.blobs[0], r, Offset(x, y)); x += step }
            y += step
        }
    }
    "cyber" -> drawBehind {
        val col = look.blobs[0]
        if (look.dark) {
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) { drawRect(col.copy(alpha = 0.035f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += step }
        } else {
            val step = 24.dp.toPx()
            val lineCol = look.scheme.onBackground.copy(alpha = 0.05f)
            var x = 0f
            while (x < size.width) { drawRect(lineCol, Offset(x, 0f), androidx.compose.ui.geometry.Size(1f, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawRect(lineCol, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += step }
        }
    }
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
