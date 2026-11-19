package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily

/*
 * The look of the app (since 2.97, owner): one signature look, **Quiet** (calm and precise, like Apple Wallet and
 * Photos: one clean typeface, bold tight headings, grouped white cards on a soft grey page, true black in dark), and
 * **Gen Z chaos** (chaos maximalism: clashing hot pink, lime and yellow, tilted cards, thick lines, Bungee headings,
 * handwritten Caveat labels, polka dots; saved as it was in 2.198 in themes/gen-z-chaos). Both have light and dark
 * ([Repo.theme]) and the accent colour you pick ([ACCENTS], [Repo.palette]).
 * History: Neobrutalism, Neumorphism and Cyberpunk went on 13 November 2026; twelve more styles (2.199–2.205) were
 * removed on 14 November 2026 (owner: not to a professional standard). A new design style is made the way
 * docs/DESIGN-STYLES.md describes, one at a time.
 * Split in the rebuild (owner, 19 November 2026): the type scale is DesignType.kt, the shapes, card colours, shadows
 * and surfaces DesignShapes.kt; this file keeps the styles, the accent colours and [Look].
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

/**
 * The five themes (owner, 17 November 2026, plan step 5): each tints the whole app gently (page, cards, panels, the
 * wordmark) in light and dark; Gen Z chaos is the sixth. The keys stay the old accents' so nobody's choice is lost.
 * Berry, the app's own colour, is first and the default for new people.
 */
val ACCENTS: List<Accent> = listOf(
    Accent("berry", "Berry", 0xFFB0245A, 0xFFF0679A),
    Accent("blue", "Ocean", 0xFF0A6CFF, 0xFF4D94FF),
    Accent("green", "Forest", 0xFF1F9D55, 0xFF4CC08A),
    // Teal replaced Amber (owner, 18 November 2026: no brownish colours); an Amber choice becomes Teal.
    Accent("teal", "Teal", 0xFF0F8A84, 0xFF3FD3C6),
    Accent("indigo", "Lilac", 0xFF7A3FD1, 0xFFB79CFF)
)

/**
 * The theme for any colour stored before 17 November 2026 (Pink, the colour wheel's "#RRGGBB"): the closest of the
 * five by hue, once (Repo.load). A key of the five stays itself.
 */
fun closestTheme(key: String): String {
    if (ACCENTS.any { it.key == key }) return key
    if (key == "pink") return "berry"
    if (key == "orange") return "teal"
    val own = customAccent(key) ?: return ACCENTS[0].key
    fun hue(c: Color): Float {
        val r = c.red; val g = c.green; val b = c.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        if (d < 0.0001f) return 0f
        val h = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        return if (h < 0) h + 360f else h
    }
    val h = hue(Color(own.light))
    return ACCENTS.minBy { val d = kotlin.math.abs(hue(Color(it.light)) - h); minOf(d, 360f - d) }.key
}

fun accentFor(key: String): Accent = ACCENTS.firstOrNull { it.key == key } ?: accentForTheme(closestTheme(key))
private fun accentForTheme(key: String): Accent = ACCENTS.first { it.key == key }

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
    return contrast(Color(ac.light), Color(0xFFF7F5F2)) < 2.5f || contrast(Color(ac.dark), Color(0xFF121014)) < 2.5f
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
    val onAccent: Color = scheme.onPrimary,
    /** Quiet's page, cards and what floats (bars, panels), tinted by the theme (17 November 2026). */
    val page: Color = scheme.background,
    val card: Color = scheme.surface,
    val raised: Color = scheme.surface
) {
    /** The signature look. (Kept under the old name: "classic" meant the plain standard look.) */
    val classic: Boolean get() = design == "quiet"
}

val LocalLook = staticCompositionLocalOf { lookFor("quiet", "blue", "light") }

internal fun c(v: Long) = Color(v)

/** The hue of a colour (0–360°): the one colour a theme is built from. */
internal fun hueOf(col: Color): Float {
    val r = col.red; val g = col.green; val b = col.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    if (d < 0.0001f) return 0f
    val hh = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
    return if (hh < 0) hh + 360f else hh
}

/** A group of rows inside a panel (a sheet's menu): a step lighter than the panel in the theme's own colour. */
fun Look.groupFill(): Color = if (dark) lerp(raised, scheme.onSurface, 0.07f) else page
private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
internal fun pick(id: Long, n: Int) = Math.floorMod(id, n.toLong()).toInt()
/** Dark ink on light colours, white on dark ones. */
private fun inkOn(bg: Color) = if (bg.luminance() > 0.5f) c(0xFF0B0B0F) else Color.White

/** Quiet (2.207): white or near-black on [bg], whichever reads better (the dark accents are mid-light: dark text). */
private fun bestInk(bg: Color): Color {
    fun ratio(a: Color) = (maxOf(a.luminance(), bg.luminance()) + 0.05f) / (minOf(a.luminance(), bg.luminance()) + 0.05f)
    val ink = c(0xFF0B0B1E)
    return if (ratio(Color.White) >= ratio(ink)) Color.White else ink
}

val figtree by lazy { google("Figtree") }
/** The text font of Quiet since 2.210 (owner, 15 November 2026: Figtree sat too close to Google's own fonts). */
val jakarta by lazy { google("Plus Jakarta Sans") }
/** Headings of Quiet (owner, 7 November 2026: "a heading font with character"): a soft serif. */
internal val fraunces by lazy { google("Fraunces") }
internal val bungee by lazy { google("Bungee") }
internal val bricolage by lazy { google("Bricolage Grotesque") }
internal val caveat by lazy { google("Caveat") }

/** All the colours of a scheme from a few, plus the surface steps that menus and dialogs use. */
internal fun scheme(
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
    // Black (owner, 18 November 2026): the pure black page for OLED screens, a 4th choice next to Dark.
    val black = mode == "black" || mode == "amoled"
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
            val bg = if (black) Color.Black else if (dark) c(0xFF0D0D0D) else c(0xFFFFF7E8)
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
            // Warm neutrals (owner, 15 November 2026, docs/styles/identity.md): a warm off-white page and a warm
            // charcoal instead of the phone-settings grey and pure black.
            // The backdrop is neutral and only the elements carry the theme's colour (owner, 18 November 2026, after the
            // deep tint of 2.227 turned Amber brown and Forest green): page, cards and panels are grey leaning only a
            // few per cent towards the theme's hue, so they read as grey but sit in harmony with it. Light: a soft
            // grey page with white cards (not glaring); Dark: dark grey like most apps; Black: a pure black page.
            // Every text stays at least 4.5 : 1 (ThemesTest).
            val h = hueOf(c(ac.light))
            fun t(sat: Float, light: Float) = Color.hsl(h, sat, light)
            val bg = when { black -> Color.Black; dark -> t(0.05f, 0.075f); else -> t(0.12f, 0.953f) }
            // Your colour as text and lines (text buttons, links, chosen tabs): in light the same colour, darkened just
            // enough to read at 4.5 : 1 on the page (green, orange and pink were too light, found 14 November
            // 2026). Filled buttons keep the colour itself ([Look.accent]).
            val inkText = if (dark) t(0.04f, 0.96f) else t(0.08f, 0.10f)
            val readable = (0..20).map { mix(a, inkText, it / 20f) }.first { col ->
                (maxOf(col.luminance(), bg.luminance()) + 0.05f) / (minOf(col.luminance(), bg.luminance()) + 0.05f) >= 4.6f
            }
            val surface = when { black -> t(0.05f, 0.075f); dark -> t(0.05f, 0.12f); else -> white }
            val raised = when { black -> t(0.05f, 0.11f); dark -> t(0.05f, 0.155f); else -> white }
            val text = inkText
            val muted = if (dark) t(0.05f, 0.68f) else t(0.06f, 0.40f)
            val hair = when { black -> t(0.05f, 0.17f); dark -> t(0.05f, 0.21f); else -> t(0.08f, 0.885f) }
            Look(
                "quiet", dark,
                // The outline (fields, tick boxes) at least 3 : 1 on the cards, so they can be found (13 November 2026).
                scheme(dark, readable, bestInk(readable), mix(a, surface, if (dark) 0.78f else 0.88f), mix(a, text, if (dark) 0.25f else 0.35f), bg, surface, text, muted,
                    if (dark) t(0.05f, 0.52f) else t(0.06f, 0.55f), hair,
                    // A real red for Delete and errors (owner, 17 November 2026: Material's red was pink), 4.5 : 1.
                    error = if (dark) c(0xFFFF6B5E) else c(0xFFC62828)),
                listOf(surface), listOf(if (dark) c(0xFFECE8E4) else mix(c(0xFFF4F1ED), a, 0.10f)), hair,
                listOf(Color.Black.copy(alpha = if (dark) 0.5f else 0.06f)), emptyList(),
                jakarta, drop, rise, accent = a, onAccent = onQ, page = bg, card = surface, raised = raised
            )
        }
    }
}
