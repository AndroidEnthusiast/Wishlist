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
 * seven styles that change every part of the app (owner, 13 November 2026: "a theme must change absolutely every
 * element of the app without compromising accessibility and readability"; Cyberpunk, Neobrutalism and Neumorphism
 * were removed then, people who had one moved to Quiet):
 *  - chaos:   Gen Z chaos maximalism: clashing hot pink, lime and yellow, tilted cards, thick lines, Bungee headings,
 *             handwritten Caveat labels, polka dots. (Saved as it was in 2.198 in themes/gen-z-chaos.)
 *  - y2k:     Y2K evolution: holographic pastels, ink line-art, sparkles and orbit rings, chrome-like gradients.
 *  - retro:   Retro vintage: sun-faded paper, cherry red and diner teal, misprinted shadows, script headings, grain.
 *  - pop:     Pop futurism: a seventies space poster, sunset planets, racing stripes, rounded retro-future letters.
 *  - grunge:  Grunge: rough torn edges, ink speckles, deep red on black, typewriter labels.
 *  - flat:    Flat design: bold flat colour, no shadows or lines, layered teal hills.
 *  - bauhaus: Bauhaus: red, blue and yellow circles and squares, square corners, a geometric sans.
 *  - doodle:  Conceptual sketch (doodle art): black marker on white, wobbly hand-drawn shapes, doodles.
 *  - cottage: Farmhouse or cottage-core: cream paper, sage, terracotta and mustard, flowers and gingham, serif type.
 *  - japandi: Japandi: warm neutrals, sage and clay, hairlines, a Japanese serif, seigaiha waves and an ensō.
 *  - riso:    Risograph: fluorescent pink, riso blue and yellow, grain, misregistered shadows and titles.
 *  - synth:   Synthwave: eighties neon, a striped sunset, a perspective grid, palms, glowing titles.
 *  - anthro:  Anthropomorphic: a gift box with a face (Placeholders.kt) on bold flat colours, cartoon outlines.
 * References the owner chose: https://www.magier.com/blog/graphic-design-styles (5, 6, 9, 13, 18, 20) and
 * https://www.kittl.com/blogs/design-styles-name-stl/ (the last six, 13 November 2026).
 * Every style has light and dark ([Repo.theme]), each designed to look as good as the other. Quiet and Gen Z chaos
 * use the accent colour you pick ([ACCENTS], [Repo.palette]); the others have their own fixed colours ([usesAccent]).
 * Text always sits on a calm, solid colour: drawn decorations stay faint enough behind text (LookTest checks the
 * contrast) or stay in the outer edge of the screen, in pictures without a photo and in the wordmark.
 */

/** A style: its key ([Repo.design]), its name and one line about it (the Themer). */
class StyleInfo(val key: String, val name: String, val about: String)

val STYLES: List<StyleInfo> = listOf(
    StyleInfo("quiet", "Quiet", "Calm and precise: clean cards on a soft grey page, bold headings, true black in dark."),
    StyleInfo("chaos", "Gen Z chaos", "Full chaos: clashing colours, tilted cards, stickers and handwriting."),
    StyleInfo("y2k", "Y2K", "Holographic pastels, chrome shine, sparkles and orbit rings, straight from the year 2000."),
    StyleInfo("retro", "Retro vintage", "Sun-faded paper, cherry red and diner teal, script lettering and a little film grain."),
    StyleInfo("pop", "Pop futurism", "A seventies space poster: sunset planets, racing stripes and bold rounded letters."),
    StyleInfo("grunge", "Grunge", "Rough edges, ink speckles and deep red on black, like a photocopied gig poster."),
    StyleInfo("flat", "Flat", "Clean flat colour and no shadows: layered teal hills and simple, clear shapes."),
    StyleInfo("bauhaus", "Bauhaus", "Red, blue and yellow circles and squares on a strict grid: form follows function."),
    StyleInfo("doodle", "Doodle", "Black marker doodles on white paper: wobbly hand-drawn lines, stars and squiggles."),
    StyleInfo("cottage", "Cottage-core", "Cream paper, sage and terracotta, little flowers and gingham: the simple country life."),
    StyleInfo("japandi", "Japandi", "Calm Japanese and Scandinavian: warm neutrals, fine lines, wave and fan patterns."),
    StyleInfo("riso", "Risograph", "Fluorescent pink, blue and yellow ink, grainy paper and slightly misaligned layers."),
    StyleInfo("synth", "Synthwave", "Eighties neon: a striped sunset, a glowing grid, palm trees and chrome letters."),
    StyleInfo("anthro", "Anthropomorphic", "A little gift box with a face keeps you company: bold flat colours and round shapes."),
    StyleInfo("swiss", "Swiss", "A strict grid, bold sans-serif type, black and white with one strong colour."),
    StyleInfo("punk", "British Punk", "Photocopied cut-outs, torn paper taped down crooked, ransom-note letters.")
)

fun styleFor(key: String): StyleInfo = STYLES.firstOrNull { it.key == key } ?: STYLES[0]

/** A colour set of a style (owner, 13 November 2026: at least three per style, so a style isn't one fixed picture). */
class ColourSet(val key: String, val name: String)

/** The colour sets of each style that has its own colours (the first is the one it starts with). */
val COLOUR_SETS: Map<String, List<ColourSet>> = mapOf(
    "y2k" to listOf(ColourSet("holo", "Holographic"), ColourSet("chrome", "Liquid chrome"), ColourSet("aero", "Bubbly")),
    "retro" to listOf(ColourSet("diner", "50s diner"), ColourSet("groovy", "70s groovy"), ColourSet("travel", "Travel poster")),
    "pop" to listOf(ColourSet("sunset", "Sunset"), ColourSet("lagoon", "Lagoon"), ColourSet("cosmic", "Cosmic")),
    "grunge" to listOf(ColourSet("blood", "Blood red"), ColourSet("toxic", "Toxic green"), ColourSet("bruise", "Bruise purple")),
    "flat" to listOf(ColourSet("lagoon", "Lagoon"), ColourSet("forest", "Forest"), ColourSet("sunset", "Sunset")),
    "bauhaus" to listOf(ColourSet("primary", "Primary"), ColourSet("earth", "Earth"), ColourSet("night", "Night blue")),
    "doodle" to listOf(ColourSet("notebook", "Notebook"), ColourSet("sticky", "Sticky notes"), ColourSet("board", "Board")),
    "cottage" to listOf(ColourSet("garden", "Garden"), ColourSet("lavender", "Lavender"), ColourSet("berry", "Berry")),
    "japandi" to listOf(ColourSet("oat", "Oat"), ColourSet("matcha", "Matcha"), ColourSet("clay", "Clay")),
    "riso" to listOf(ColourSet("pinkblue", "Pink and blue"), ColourSet("orangeteal", "Orange and teal"), ColourSet("greenpurple", "Green and purple")),
    "synth" to listOf(ColourSet("sunset", "Sunset"), ColourSet("outrun", "Outrun"), ColourSet("miami", "Miami")),
    "anthro" to listOf(ColourSet("royal", "Royal blue"), ColourSet("mint", "Mint"), ColourSet("tangerine", "Tangerine")),
    "swiss" to listOf(ColourSet("red", "Swiss red"), ColourSet("klein", "Klein blue"), ColourSet("signal", "Signal yellow")),
    "punk" to listOf(ColourSet("zine", "Photocopy zine"), ColourSet("gig", "Gig poster"), ColourSet("tartan", "Safety pin & tartan"))
)

/** The colour set [key] of [style], or its first set. */
fun setFor(style: String, key: String): ColourSet {
    val sets = COLOUR_SETS[styleFor(style).key] ?: return ColourSet("", "")
    return sets.firstOrNull { it.key == key } ?: sets.first()
}

/** Whether the style takes the accent colour you pick (Quiet and Gen Z chaos); the others have their own colours. */
fun usesAccent(style: String): Boolean = styleFor(style).key == "quiet" || styleFor(style).key == "chaos"

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
    /** One of [STYLES]: "quiet", "chaos", "y2k", "retro", "pop", "grunge", "flat" or "bauhaus". */
    val design: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    /** Card colours, used in turn. */
    val fills: List<Color>,
    /** Soft colours behind pictures, used in turn. */
    val tints: List<Color>,
    /** Lines: hairlines (Quiet), ink (chaos, Y2K, Retro, Grunge, Bauhaus). */
    val line: Color,
    /** Shadow colours: the hard shadow (chaos, Retro, Grunge, Bauhaus), the two stripes (Pop futurism). */
    val shadows: List<Color>,
    /** Colours of what is drawn behind the screens (chaos circles, Y2K's glow, Pop's planets, Bauhaus shapes…). */
    val blobs: List<Color>,
    /** The font of names, headings and prices. */
    val display: FontFamily?,
    /** A cheaper and a dearer price. */
    val drop: Color,
    val rise: Color,
    /** The colour of filled blocks (buttons, the chosen chip, Add wish, the + buttons) and what goes on them. */
    val accent: Color = scheme.primary,
    val onAccent: Color = scheme.onPrimary,
    /** The bold colours of drawings (pictures without a photo, the wordmark, the edges of the screen). */
    val art: List<Color> = emptyList(),
    /** A gradient for filled blocks instead of [accent] (Y2K's holographic shine, Pop futurism's sunset). */
    val accentBrush: Brush? = null,
    /** A gradient for the hard shadow instead of [shadows] (Y2K). */
    val shadowBrush: Brush? = null,
    /** The colour set in use ([COLOUR_SETS]); "" for Quiet and chaos. */
    val set: String = "",
    /** How much decoration (owner, 13 November 2026): 0 calm, 1 normal, 2 wild ([DECO_LEVELS]). */
    val deco: Int = 1,
    /** The card style ([CARD_STYLES]), "" when the style has none. */
    val cardStyle: String = ""
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

/*
 * The fonts (all SIL Open Font License, credited in Licences.kt). Each has a close font of the phone's own as a
 * fallback, shown until the download has finished (owner, 13 November 2026).
 */
val figtree by lazy { google("Figtree") }
/** Headings of Quiet (owner, 7 November 2026: "a heading font with character"): a soft serif. */
private val fraunces by lazy { google("Fraunces") }
private val bungee by lazy { google("Bungee") }
private val bricolage by lazy { google("Bricolage Grotesque") }
private val caveat by lazy { google("Caveat") }
private val one = listOf(FontWeight.Normal)
// Y2K: Unbounded (wide, rounded, futuristic) headings, Space Grotesk text, Space Mono for the smallest labels.
private val unbounded by lazy { google("Unbounded", "sans-serif", listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
private val spaceGrotesk by lazy { google("Space Grotesk", "sans-serif", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
private val spaceMono by lazy { google("Space Mono", "monospace", listOf(FontWeight.Normal, FontWeight.Bold)) }
// Retro vintage: Lobster (a fifties diner script) headings, Bitter (a slab serif from print) text, Oswald labels.
private val lobster by lazy { google("Lobster", "cursive", one) }
private val bitter by lazy { google("Bitter", "serif") }
private val oswald by lazy { google("Oswald", "sans-serif-condensed", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
// Pop futurism: Righteous (rounded seventies future) headings, Exo 2 (a soft technical sans) text.
private val righteous by lazy { google("Righteous", "sans-serif", one) }
private val exo2 by lazy { google("Exo 2", "sans-serif") }
// Grunge: Rubik Dirt (distressed) headings, Barlow text, Courier Prime (a typewriter) labels.
private val rubikDirt by lazy { google("Rubik Dirt", "sans-serif-condensed", one) }
private val barlow by lazy { google("Barlow", "sans-serif") }
private val courierPrime by lazy { google("Courier Prime", "monospace", listOf(FontWeight.Normal, FontWeight.Bold)) }
// Flat: Poppins (geometric) headings and labels, Inter text.
private val poppins by lazy { google("Poppins", "sans-serif") }
private val inter by lazy { google("Inter", "sans-serif") }
// Bauhaus: Jost, a geometric sans in the spirit of Futura (1927).
private val jost by lazy { google("Jost", "sans-serif") }
// Doodle: Caveat Brush (a marker) headings, Shantell Sans (handwritten, made to be easy to read) text.
private val caveatBrush by lazy { google("Caveat Brush", "casual", one) }
private val shantell by lazy { google("Shantell Sans", "casual") }
// Cottage-core: DM Serif Display (a farmers' market poster serif) headings, Lora (a warm book serif) text.
private val dmSerif by lazy { google("DM Serif Display", "serif", one) }
private val lora by lazy { google("Lora", "serif", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
// Japandi: Shippori Mincho (a Japanese serif) headings, Zen Kaku Gothic New (a calm Japanese sans) text.
private val shippori by lazy { google("Shippori Mincho", "serif", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)) }
private val zenKaku by lazy { google("Zen Kaku Gothic New", "sans-serif", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)) }
// Risograph: Anton (a tall poster face) headings, Work Sans text.
private val anton by lazy { google("Anton", "sans-serif-condensed", one) }
private val workSans by lazy { google("Work Sans", "sans-serif") }
// Synthwave: Audiowide (eighties chrome) headings, Chakra Petch (a squared techno sans) text.
private val audiowide by lazy { google("Audiowide", "sans-serif", one) }
private val chakra by lazy { google("Chakra Petch", "sans-serif", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
// Anthropomorphic: Fredoka (round and friendly) headings and labels, Nunito text.
private val fredoka by lazy { google("Fredoka", "sans-serif", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
private val nunito by lazy { google("Nunito", "sans-serif") }
// Swiss: Archivo, a grotesque in the spirit of Helvetica and Akzidenz.
private val archivo by lazy { google("Archivo", "sans-serif") }
// British Punk: Sedgwick Ave Display (a marker scrawl) headings, Archivo Narrow text, IBM Plex Mono (typewriter) labels.
private val sedgwick by lazy { google("Sedgwick Ave Display", "casual", one) }
private val archivoNarrow by lazy { google("Archivo Narrow", "sans-serif-condensed", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }
private val plexMono by lazy { google("IBM Plex Mono", "monospace", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) }

/** Retro's font for prices (Retro.kt). */
internal val retroDisplay: FontFamily get() = oswald

/** Punk's text font (Punk.kt). */
internal val punkDisplay: FontFamily get() = archivoNarrow

/** Y2K's heading font (Y2k.kt). */
internal val y2kDisplay: FontFamily get() = unbounded

/** Doodle's heading font (Doodle.kt). */
internal val doodleDisplay: FontFamily get() = caveatBrush

/** [scheme] for the styles built in their own file (Doodle.kt). */
internal fun lookScheme(
    dark: Boolean, primary: Color, onPrimary: Color, container: Color, onContainer: Color,
    bg: Color, surface: Color, text: Color, muted: Color, outline: Color, outlineVariant: Color
): ColorScheme = scheme(dark, primary, onPrimary, container, onContainer, bg, surface, text, muted, outline, outlineVariant)

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

/** A gradient across the whole drawing (corner to corner). */
private fun sweep(cols: List<Color>): Brush = Brush.linearGradient(cols, Offset.Zero, Offset.Infinite)

/**
 * The look for a style ([style], one of [STYLES]), an accent ([accentKey], one of [ACCENTS]; only Quiet and chaos use
 * it) and a mode ("light" or "dark"; anything else counts as dark).
 */
fun lookFor(style: String, accentKey: String, mode: String, set: String = "", deco: String = "", cards: String = ""): Look {
    val key = styleFor(style).key
    val setKey = setFor(key, set).key
    val level = DECO_LEVELS.indexOfFirst { it.key == deco }.takeIf { it >= 0 } ?: 1
    if (key == "doodle") return doodleLook(mode != "light", setKey, level, cardStyleFor(key, setKey, cards))
    if (key == "y2k") return y2kLook(mode != "light", setKey, level, cardStyleFor(key, setKey, cards))
    if (key == "punk") return punkLook(mode != "light", setKey, level, cardStyleFor(key, setKey, cards))
    if (key == "retro") return retroLook(mode != "light", setKey, level, cardStyleFor(key, setKey, cards))
    val base = baseLook(style, accentKey, mode)
    return if (usesAccent(base.design)) base else base.withSet(setKey)
}

/** One choice of a theme's options (owner, 13 November 2026: options to personalise a theme). */
class ThemeOption(val key: String, val name: String)

/** How much decoration: drawings behind the screens and how much cards tilt. */
val DECO_LEVELS = listOf(ThemeOption("calm", "Calm"), ThemeOption("normal", "Normal"), ThemeOption("wild", "Wild"))

/** The card styles of the styles that have them (Doodle first; the others follow when they are rebuilt). */
val CARD_STYLES: Map<String, List<ThemeOption>> = mapOf(
    "doodle" to listOf(ThemeOption("outlined", "Outlined"), ThemeOption("filled", "Filled"), ThemeOption("sticker", "Sticker")),
    "y2k" to listOf(ThemeOption("glass", "Glass"), ThemeOption("glossy", "Glossy"), ThemeOption("sticker", "Sticker")),
    "punk" to listOf(ThemeOption("cutout", "Cut-out"), ThemeOption("taped", "Taped flyer"), ThemeOption("badge", "Badge")),
    "retro" to listOf(ThemeOption("ticket", "Ticket"), ThemeOption("postcard", "Postcard"), ThemeOption("enamel", "Enamel sign"))
)

/** Whether the style has options (decoration amount, card style) in the Themer. */
fun hasOptions(style: String): Boolean = CARD_STYLES.containsKey(styleFor(style).key)

/** The card style: the one chosen, or the colour set's own (Doodle's sticky notes are filled). */
fun cardStyleFor(style: String, set: String, chosen: String): String {
    val styles = CARD_STYLES[style] ?: return ""
    return styles.firstOrNull { it.key == chosen }?.key ?: when {
        style == "doodle" && set == "sticky" -> "filled"
        style == "y2k" -> if (set == "aero") "glass" else "glossy"
        style == "punk" -> when (set) { "gig" -> "taped"; "tartan" -> "badge"; else -> "cutout" }
        style == "retro" -> when (set) { "groovy" -> "enamel"; "travel" -> "postcard"; else -> "ticket" }
        else -> styles.first().key
    }
}

/** The style's own look in its first colour set. */
private fun baseLook(style: String, accentKey: String, mode: String): Look {
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
                bungee, drop, rise, accent = a, onAccent = onA, art = listOf(hot, sun, lime)
            )
        }
        "pop" -> {
            // A seventies space poster: sand and navy, a sunset of yellow, orange and pink, teal planets.
            val bg = if (dark) c(0xFF15173A) else c(0xFFF7E8D0)
            val surface = if (dark) c(0xFF22265A) else c(0xFFFFF7EA)
            val ink = if (dark) c(0xFFFFF2DE) else c(0xFF1B1A3A)
            val muted = if (dark) c(0xFFCFC6DD) else c(0xFF4A4566)
            val navy = c(0xFF1B1A3A)
            val sun = c(0xFFFFC23D); val orange = c(0xFFFF8A3D); val pink = c(0xFFFF6A5B); val teal = c(0xFF2A9D8F)
            val sunset = sweep(listOf(sun, orange, pink))
            Look(
                "pop", dark,
                scheme(dark, if (dark) c(0xFFFFB35C) else c(0xFFA9360F), if (dark) navy else c(0xFFFFF7EA), if (dark) c(0xFF3A3D72) else c(0xFFFFE2C2), ink,
                    bg, surface, ink, muted, ink, if (dark) c(0xFF3A3D72) else c(0xFFE6D3B3)),
                listOf(surface),
                if (dark) listOf(c(0xFF5A3460), c(0xFF1E5A62), c(0xFF5C4A2A), c(0xFF3A3D72))
                else listOf(c(0xFFFFE0B8), c(0xFFCDEBE6), c(0xFFFFD2CC), c(0xFFF3DDB4)),
                ink,
                listOf(if (dark) c(0xFF2BB3A3) else teal, if (dark) c(0xFFFF7A3D) else c(0xFFF26A2E)),
                if (dark) listOf(sun.copy(alpha = 0.3f), orange.copy(alpha = 0.3f), c(0xFFE8607E).copy(alpha = 0.3f), c(0xFF2BB3A3).copy(alpha = 0.3f))
                else listOf(sun.copy(alpha = 0.55f), orange.copy(alpha = 0.55f), c(0xFFE8607E).copy(alpha = 0.45f), teal.copy(alpha = 0.35f)),
                righteous, drop, rise, accent = orange, onAccent = navy,
                art = listOf(sun, orange, pink, teal, c(0xFF9B3D6E)),
                accentBrush = sunset
            )
        }
        "grunge" -> {
            // Black, grey and deep red (dark), or a photocopy on off-white paper (light).
            val bg = if (dark) c(0xFF131211) else c(0xFFECE8E1)
            val surface = if (dark) c(0xFF1F1D1B) else c(0xFFF8F6F2)
            val ink = if (dark) c(0xFFEEEAE3) else c(0xFF171514)
            val muted = if (dark) c(0xFFB3ACA3) else c(0xFF4A4540)
            val line = if (dark) c(0xFF8E877E) else c(0xFF2A2623)
            val red = if (dark) c(0xFFA3221A) else c(0xFF9E1B14)
            val onRed = if (dark) c(0xFFF4EFE8) else c(0xFFF8F6F2)
            Look(
                "grunge", dark,
                scheme(dark, if (dark) c(0xFFEE6A5F) else red, if (dark) c(0xFF131211) else onRed, if (dark) c(0xFF3A1E1B) else c(0xFFEBD3CF), ink,
                    bg, surface, ink, muted, line, if (dark) c(0xFF3A3632) else c(0xFFCFC8BE)),
                listOf(surface),
                if (dark) listOf(c(0xFF2A2724), c(0xFF3A1E1B), c(0xFF33302B), c(0xFF24221F))
                else listOf(c(0xFFDDD7CE), c(0xFFE8CFCB), c(0xFFD3CDC3), c(0xFFE6E1D8)),
                line,
                listOf(if (dark) Color.Black.copy(alpha = 0.85f) else c(0xFF171514).copy(alpha = 0.55f)),
                if (dark) listOf(ink.copy(alpha = 0.08f), red.copy(alpha = 0.30f)) else listOf(ink.copy(alpha = 0.12f), red.copy(alpha = 0.18f)),
                barlow, drop, rise, accent = red, onAccent = onRed,
                art = listOf(red, ink, mix(ink, bg, 0.5f))
            )
        }
        "flat" -> {
            // Teal, green and sand, flat: no lines, no shadows; colour does all the work.
            val bg = if (dark) c(0xFF0B2530) else c(0xFFE6F0EC)
            val surface = if (dark) c(0xFF133543) else white
            val ink = if (dark) c(0xFFEAF6F2) else c(0xFF0D2B33)
            val muted = if (dark) c(0xFFA9C7C1) else c(0xFF3E5A60)
            val fill = if (dark) c(0xFF6FD1B5) else c(0xFF0F5E63)
            val onFill = if (dark) c(0xFF0B2530) else white
            Look(
                "flat", dark,
                scheme(dark, if (dark) c(0xFF6FD8BE) else c(0xFF0F6B6E), onFill, if (dark) c(0xFF1D4F5C) else c(0xFFCDE6DE), ink,
                    bg, surface, ink, muted, if (dark) c(0xFF7FA3A0) else c(0xFF5C7A7F), if (dark) c(0xFF1D4652) else c(0xFFCCDDD7)),
                listOf(surface),
                if (dark) listOf(c(0xFF1F5F5A), c(0xFF5C4A2E), c(0xFF22564A), c(0xFF5A3A30), c(0xFF1D4F63))
                else listOf(c(0xFFCDE9E1), c(0xFFF7E7CC), c(0xFFD6EFE6), c(0xFFF6D9CF), c(0xFFD3E4EC)),
                Color.Transparent,
                emptyList(),
                if (dark) listOf(c(0xFF0F3A44), c(0xFF134A52), c(0xFF17565C), c(0xFF0D303A))
                else listOf(c(0xFFD8EFE7), c(0xFFB2DCCD), c(0xFFBFE3D6), c(0xFFF7E7CC)),
                poppins, drop, rise, accent = fill, onAccent = onFill,
                art = listOf(c(0xFF1F7A7A), c(0xFF4FB09A), c(0xFFF2B66D), c(0xFFE07A5F), c(0xFF0F4C5C))
            )
        }
        "bauhaus" -> {
            // Primary colours with black, white and cream; in dark, cream lines on near-black.
            val bg = if (dark) c(0xFF14161F) else c(0xFFF0E7D4)
            val surface = if (dark) c(0xFF1E2130) else c(0xFFFBF6EC)
            val ink = if (dark) c(0xFFF0E7D4) else c(0xFF14161F)
            val muted = if (dark) c(0xFFC3BCAE) else c(0xFF43444F)
            val red = c(0xFFD13A24); val blue = c(0xFF2B3A8C); val yellow = c(0xFFE9A827); val green = c(0xFF3D8048)
            val fill = if (dark) c(0xFFF2B12E) else c(0xFFC8331F)
            val onFill = if (dark) c(0xFF14161F) else white
            Look(
                "bauhaus", dark,
                scheme(dark, if (dark) c(0xFFF2B93B) else c(0xFF263A9E), onFill, if (dark) c(0xFF2E3350) else c(0xFFE3DCF0), ink,
                    bg, surface, ink, muted, ink, if (dark) c(0xFF3A3E52) else c(0xFFD6CCB6)),
                listOf(surface),
                if (dark) listOf(c(0xFF3A2420), c(0xFF222A52), c(0xFF4A3A1C), c(0xFF22382A))
                else listOf(c(0xFFF5D3CB), c(0xFFD5D9EE), c(0xFFF6E1B5), c(0xFFD2E4D3)),
                ink,
                listOf(if (dark) red else blue),
                if (dark) listOf(red.copy(alpha = 0.30f), c(0xFF2E3F99).copy(alpha = 0.40f), c(0xFFD99A1E).copy(alpha = 0.25f), ink.copy(alpha = 0.08f))
                else listOf(red.copy(alpha = 0.25f), blue.copy(alpha = 0.18f), yellow.copy(alpha = 0.35f), ink.copy(alpha = 0.12f)),
                jost, drop, rise, accent = fill, onAccent = onFill,
                art = listOf(red, blue, yellow, c(0xFF14161F), green, c(0xFFF0E7D4))
            )
        }
        "cottage" -> {
            // Cream paper, sage, terracotta, mustard and berry (light); a cottage in the evening (dark).
            val bg = if (dark) c(0xFF1A2117) else c(0xFFF7F1E3)
            val surface = if (dark) c(0xFF252D21) else c(0xFFFFFBF2)
            val ink = if (dark) c(0xFFF1EBDD) else c(0xFF24301F)
            val muted = if (dark) c(0xFFC6BFAE) else c(0xFF566050)
            val line = if (dark) c(0xFFCFC6B0) else c(0xFF3B4A33)
            val sage = c(0xFF5E7D4F); val terracotta = c(0xFFB5523B); val mustard = c(0xFFD8A945); val berry = c(0xFF8C3B5E)
            val fill = if (dark) c(0xFFE2B95A) else c(0xFF4F6E42)
            val onFill = if (dark) c(0xFF1A2117) else c(0xFFFFFBF2)
            Look(
                "cottage", dark,
                scheme(dark, if (dark) c(0xFFE8A48F) else c(0xFF9A3F2B), if (dark) c(0xFF1A2117) else c(0xFFFFFBF2), if (dark) c(0xFF34402E) else c(0xFFE4EBD8), ink,
                    bg, surface, ink, muted, line, if (dark) c(0xFF3A4535) else c(0xFFDCD3BE)),
                listOf(surface),
                if (dark) listOf(c(0xFF2E3B28), c(0xFF4A2E24), c(0xFF4A3F22), c(0xFF3F2734))
                else listOf(c(0xFFE4EBD8), c(0xFFF3D9CF), c(0xFFF5E6C2), c(0xFFEBD5DF)),
                line,
                listOf(line.copy(alpha = 0.3f)),
                if (dark) listOf(c(0xFFA9C79A).copy(alpha = 0.10f), c(0xFFE2B95A).copy(alpha = 0.12f), terracotta.copy(alpha = 0.20f))
                else listOf(sage.copy(alpha = 0.10f), terracotta.copy(alpha = 0.14f), mustard.copy(alpha = 0.25f)),
                dmSerif, drop, rise, accent = fill, onAccent = onFill,
                art = listOf(sage, terracotta, mustard, berry, if (dark) c(0xFFF1EBDD) else c(0xFFFFFBF2))
            )
        }
        "japandi" -> {
            // Warm neutrals, sage and clay, charcoal; calm and quiet, hairlines only.
            val bg = if (dark) c(0xFF1E1D1B) else c(0xFFF4EFE6)
            val surface = if (dark) c(0xFF282724) else c(0xFFFBF8F2)
            val ink = if (dark) c(0xFFEDE7DC) else c(0xFF2B2A27)
            val muted = if (dark) c(0xFFB9B2A6) else c(0xFF5F5B53)
            val line = if (dark) c(0xFF8F887C) else c(0xFF8C857A)
            val sage = c(0xFF8A9A7B); val clay = c(0xFFB07A57); val oat = c(0xFFD9CDB8); val charcoal = c(0xFF2B2A27)
            val fill = if (dark) oat else charcoal
            val onFill = if (dark) c(0xFF1E1D1B) else c(0xFFFBF8F2)
            Look(
                "japandi", dark,
                scheme(dark, if (dark) c(0xFFD9A982) else c(0xFF8C5A3C), onFill, if (dark) c(0xFF35332F) else c(0xFFEAE3D6), ink,
                    bg, surface, ink, muted, line, if (dark) c(0xFF3A3834) else c(0xFFE2DACC)),
                listOf(surface),
                if (dark) listOf(c(0xFF2F3329), c(0xFF3A2E26), c(0xFF33302B)) else listOf(c(0xFFE6E9DF), c(0xFFEFE2D7), c(0xFFEDE6D9)),
                line,
                emptyList(),
                if (dark) listOf(sage.copy(alpha = 0.14f), clay.copy(alpha = 0.14f), ink.copy(alpha = 0.06f))
                else listOf(sage.copy(alpha = 0.12f), clay.copy(alpha = 0.10f), ink.copy(alpha = 0.06f)),
                shippori, drop, rise, accent = fill, onAccent = onFill,
                art = listOf(sage, clay, oat, charcoal, if (dark) c(0xFF1E1D1B) else c(0xFFFBF8F2))
            )
        }
        "riso" -> {
            // Fluorescent pink, riso blue and yellow on warm paper; on dark ink-blue paper in dark.
            val bg = if (dark) c(0xFF17152B) else c(0xFFFBF3EE)
            val surface = if (dark) c(0xFF231F3F) else c(0xFFFFFDF9)
            val ink = if (dark) c(0xFFFFF3F8) else c(0xFF232056)
            val muted = if (dark) c(0xFFCEC4DD) else c(0xFF4D4A78)
            val pink = c(0xFFFF48B0); val blue = c(0xFF3255A4); val yellow = c(0xFFFFE800); val green = c(0xFF00A95C)
            Look(
                "riso", dark,
                scheme(dark, if (dark) c(0xFFFFE45C) else c(0xFF2F4FA0), if (dark) c(0xFF17152B) else white, if (dark) c(0xFF3A2E52) else c(0xFFFFE0F0), ink,
                    bg, surface, ink, muted, ink, if (dark) c(0xFF3A3560) else c(0xFFE8D9E0)),
                listOf(surface),
                if (dark) listOf(c(0xFF4A2350), c(0xFF22305A), c(0xFF4A4520), c(0xFF1E4A3A))
                else listOf(c(0xFFFFD6EC), c(0xFFD6E0F5), c(0xFFFFF6B0), c(0xFFCDEEDC)),
                ink,
                listOf(pink, blue),
                if (dark) listOf(pink.copy(alpha = 0.22f), blue.copy(alpha = 0.30f), yellow.copy(alpha = 0.12f), ink.copy(alpha = 0.07f))
                else listOf(pink.copy(alpha = 0.22f), blue.copy(alpha = 0.14f), yellow.copy(alpha = 0.40f), ink.copy(alpha = 0.07f)),
                anton, drop, rise, accent = pink, onAccent = c(0xFF232056),
                art = listOf(pink, blue, yellow, green, c(0xFF765BA7))
            )
        }
        "synth" -> {
            // Eighties neon: deep purple night, pink and cyan neon, a yellow-orange-pink sunset (dark); the same
            // sunset in a pale pink sky (light).
            val bg = if (dark) c(0xFF120A26) else c(0xFFFDEFF6)
            val surface = if (dark) c(0xFF1E1238) else white
            val ink = if (dark) c(0xFFFBEFFF) else c(0xFF2A0F45)
            val muted = if (dark) c(0xFFC9B6E4) else c(0xFF5C4777)
            val neon = if (dark) c(0xFFFF4FD8) else c(0xFFB0177A)
            val cyan = if (dark) c(0xFF5CF2FF) else c(0xFF3CC9D6)
            val sun = c(0xFFFFD23F); val orange = c(0xFFFF8A3D); val pink = c(0xFFFF4FA8)
            val sunset = Brush.linearGradient(listOf(sun, orange, pink), Offset.Zero, Offset(0f, Float.POSITIVE_INFINITY))
            val deep = c(0xFF1A0B33)
            Look(
                "synth", dark,
                scheme(dark, if (dark) c(0xFF5CF2FF) else c(0xFF8E1066), if (dark) deep else white, if (dark) c(0xFF3A1F5C) else c(0xFFFFDDF0), ink,
                    bg, surface, ink, muted, neon, if (dark) c(0xFF3A2560) else c(0xFFEBD3E3)),
                listOf(surface),
                if (dark) listOf(c(0xFF3A1648), c(0xFF142E4A), c(0xFF4A2A1E)) else listOf(c(0xFFFFE0F1), c(0xFFDDF6F8), c(0xFFFFE7D3)),
                neon,
                listOf(neon, cyan),
                if (dark) listOf(c(0xFFFF4FD8).copy(alpha = 0.22f), c(0xFF5CF2FF).copy(alpha = 0.22f), orange.copy(alpha = 0.25f), c(0xFF7A2BFF).copy(alpha = 0.25f))
                else listOf(c(0xFFFF4FD8).copy(alpha = 0.18f), cyan.copy(alpha = 0.20f), orange.copy(alpha = 0.22f), c(0xFFB488FF).copy(alpha = 0.20f)),
                audiowide, drop, rise, accent = orange, onAccent = deep,
                art = listOf(sun, orange, pink, cyan, c(0xFF7A2BFF)),
                accentBrush = sunset
            )
        }
        "anthro" -> {
            // Bold flat poster colours: cream with royal blue (light); a deep blue night with pink (dark).
            val bg = if (dark) c(0xFF101A40) else c(0xFFF6EEDC)
            val surface = if (dark) c(0xFF1B2757) else c(0xFFFFFAF0)
            val ink = if (dark) c(0xFFF6EEDC) else c(0xFF1A1A2E)
            val muted = if (dark) c(0xFFC8C3D4) else c(0xFF4B4A5E)
            val blue = c(0xFF1D3FA8); val pink = c(0xFFFF6FB5); val red = c(0xFFF2453D); val purple = c(0xFF8E6CD8); val yellow = c(0xFFFFC53D)
            val fill = if (dark) pink else blue
            val onFill = if (dark) c(0xFF101A40) else c(0xFFFFFAF0)
            Look(
                "anthro", dark,
                scheme(dark, if (dark) c(0xFFFF9ACB) else blue, onFill, if (dark) c(0xFF2C3A75) else c(0xFFDCE3F7), ink,
                    bg, surface, ink, muted, ink, if (dark) c(0xFF2C3A75) else c(0xFFE3D8C0)),
                listOf(surface),
                if (dark) listOf(c(0xFF2C3A75), c(0xFF4A2348), c(0xFF4A2A2A), c(0xFF3A2E66))
                else listOf(c(0xFFDCE3F7), c(0xFFFFDCEC), c(0xFFFFE3D9), c(0xFFE8E0F7)),
                ink,
                listOf(ink),
                if (dark) listOf(pink.copy(alpha = 0.18f), ink.copy(alpha = 0.08f), red.copy(alpha = 0.20f), purple.copy(alpha = 0.25f))
                else listOf(pink.copy(alpha = 0.22f), blue.copy(alpha = 0.10f), red.copy(alpha = 0.14f), purple.copy(alpha = 0.18f)),
                fredoka, drop, rise, accent = fill, onAccent = onFill,
                art = listOf(red, blue, pink, yellow, purple, c(0xFF1A1A2E), white)
            )
        }
        "swiss" -> {
            // White, black and one strong colour; in dark a black page with white type and the same colour.
            val bg = if (dark) c(0xFF0E0E0E) else c(0xFFF4F4F2)
            val surface = if (dark) c(0xFF1A1A1A) else white
            val ink = if (dark) c(0xFFF4F4F2) else c(0xFF111111)
            val muted = if (dark) c(0xFFB5B5B5) else c(0xFF4A4A4A)
            val red = c(0xFFE30613)
            Look(
                "swiss", dark,
                scheme(dark, if (dark) c(0xFFFF5247) else c(0xFFC4000F), if (dark) c(0xFF0E0E0E) else white, if (dark) c(0xFF3A1A1A) else c(0xFFFADCDC), ink,
                    bg, surface, ink, muted, ink, if (dark) c(0xFF333333) else c(0xFFDDDDDA)),
                listOf(surface),
                if (dark) listOf(c(0xFF262626), c(0xFF2E1E1E), c(0xFF222222)) else listOf(c(0xFFEDEDED), c(0xFFF6E3E3), c(0xFFE8E8E8)),
                ink,
                listOf(ink),
                listOf(ink.copy(alpha = 0.06f), red.copy(alpha = 0.10f)),
                archivo, drop, rise, accent = red, onAccent = white,
                art = listOf(red, ink, if (dark) c(0xFF0E0E0E) else white, c(0xFF8A8A8A))
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
                // The outline (fields, tick boxes) at least 3 : 1 on the cards, so they can be found (13 November 2026).
                scheme(dark, a, onA, mix(a, surface, if (dark) 0.78f else 0.86f), mix(a, text, 0.45f), bg, surface, text, muted,
                    if (dark) c(0xFF7C7C82) else c(0xFF8E8E93), hair),
                listOf(surface), listOf(if (dark) c(0xFF2A2A2D) else c(0xFFF7F7FA)), hair,
                listOf(Color.Black.copy(alpha = if (dark) 0.5f else 0.06f)), emptyList(),
                figtree, drop, rise, art = listOf(a)
            )
        }
    }
}

private fun TextStyle.styled(f: FontFamily, w: FontWeight, spacing: Float? = null) =
    copy(fontFamily = f, fontWeight = w, letterSpacing = if (spacing == null) letterSpacing else spacing.sp)

/** The type of a style: a heading font (display, headlines, big titles) and a text font, and the labels' font. */
private fun styleType(
    head: FontFamily, headW: FontWeight, headSpacing: Float?, body: FontFamily, titleW: FontWeight, bodyW: FontWeight,
    label: FontFamily, labelW: FontWeight, small: FontFamily = label, smallW: FontWeight = labelW, labelSpacing: Float? = null,
    headShadow: androidx.compose.ui.graphics.Shadow? = null
): Typography {
    // Big titles only (owner, 13 November 2026): Risograph's misaligned copy, Synthwave's glow.
    val d0 = Typography()
    val d = if (headShadow == null) d0 else d0.copy(
        displayLarge = d0.displayLarge.copy(shadow = headShadow), displayMedium = d0.displayMedium.copy(shadow = headShadow),
        displaySmall = d0.displaySmall.copy(shadow = headShadow), headlineLarge = d0.headlineLarge.copy(shadow = headShadow),
        headlineMedium = d0.headlineMedium.copy(shadow = headShadow), headlineSmall = d0.headlineSmall.copy(shadow = headShadow)
        // titleLarge stays plain: it is also used for prices and numbers. Screen titles and section headings get the
        // effect through Look.titleShadow (TopAppBar, HubTitle).
    )
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
 * The fonts of each style: headings in the style's character, the text you read longer in a clean font that fits it
 * (owner, 13 November 2026: "the font needs to be truly fitting the theme").
 */
fun typographyFor(design: String, @Suppress("UNUSED_PARAMETER") classic: Typography? = null): Typography = when (design) {
    "chaos" -> styleType(bungee, FontWeight.Normal, 0f, bricolage, FontWeight.Bold, FontWeight.Normal, bricolage, FontWeight.Bold, caveat, FontWeight.Bold)
    "y2k" -> styleType(unbounded, FontWeight.SemiBold, -0.3f, spaceGrotesk, FontWeight.Bold, FontWeight.Normal, spaceGrotesk, FontWeight.Bold, spaceMono, FontWeight.Bold)
    "retro" -> styleType(lobster, FontWeight.Normal, 0.2f, bitter, FontWeight.Bold, FontWeight.Normal, oswald, FontWeight.Medium, labelSpacing = 0.5f)
    "pop" -> styleType(righteous, FontWeight.Normal, 0.3f, exo2, FontWeight.Bold, FontWeight.Medium, exo2, FontWeight.Bold)
    "grunge" -> styleType(rubikDirt, FontWeight.Normal, 0.4f, barlow, FontWeight.SemiBold, FontWeight.Normal, courierPrime, FontWeight.Bold)
    "flat" -> styleType(poppins, FontWeight.SemiBold, -0.2f, inter, FontWeight.SemiBold, FontWeight.Normal, poppins, FontWeight.Medium)
    "bauhaus" -> styleType(jost, FontWeight.Bold, -0.3f, jost, FontWeight.SemiBold, FontWeight.Normal, jost, FontWeight.SemiBold, labelSpacing = 0.6f)
    "doodle" -> styleType(caveatBrush, FontWeight.Normal, 0.3f, shantell, FontWeight.SemiBold, FontWeight.Normal, shantell, FontWeight.Bold)
    "cottage" -> styleType(dmSerif, FontWeight.Normal, 0f, lora, FontWeight.SemiBold, FontWeight.Normal, lora, FontWeight.SemiBold, labelSpacing = 0.2f)
    "japandi" -> styleType(shippori, FontWeight.SemiBold, 0.2f, zenKaku, FontWeight.Medium, FontWeight.Normal, zenKaku, FontWeight.Medium, labelSpacing = 0.6f)
    "riso" -> styleType(anton, FontWeight.Normal, 0.6f, workSans, FontWeight.SemiBold, FontWeight.Normal, workSans, FontWeight.Bold, headShadow = titleShadowFor("riso"))
    "synth" -> styleType(audiowide, FontWeight.Normal, 0.4f, chakra, FontWeight.SemiBold, FontWeight.Normal, chakra, FontWeight.Bold, headShadow = titleShadowFor("synth"))
    "anthro" -> styleType(fredoka, FontWeight.SemiBold, 0f, nunito, FontWeight.Bold, FontWeight.Normal, fredoka, FontWeight.Medium)
    "swiss" -> styleType(archivo, FontWeight.ExtraBold, -0.6f, archivo, FontWeight.Bold, FontWeight.Normal, archivo, FontWeight.SemiBold)
    "punk" -> styleType(sedgwick, FontWeight.Normal, 0f, archivoNarrow, FontWeight.Bold, FontWeight.Normal, plexMono, FontWeight.Bold)
    else -> styleType(fraunces, FontWeight.SemiBold, -0.2f, figtree, FontWeight.SemiBold, FontWeight.Normal, figtree, FontWeight.SemiBold)
}

/** The effect on big titles (owner, 13 November 2026: titles only): Risograph's misaligned pink, Synthwave's glow. */
fun titleShadowFor(design: String): androidx.compose.ui.graphics.Shadow? = when (design) {
    "riso" -> androidx.compose.ui.graphics.Shadow(Color(0xFFFF48B0).copy(alpha = 0.8f), Offset(7f, 5f), 0f)
    "synth" -> androidx.compose.ui.graphics.Shadow(Color(0xFFFF4FD8).copy(alpha = 0.9f), Offset.Zero, 18f)
    else -> null
}

val Look.titleShadow: androidx.compose.ui.graphics.Shadow? get() = titleShadowFor(design)

/** The weight of screen titles in the top bar (fonts that come in one weight: that weight). */
val Look.titleWeight: FontWeight
    get() = when (design) {
        "chaos", "retro", "pop", "grunge", "doodle", "cottage", "riso", "synth", "punk" -> FontWeight.Normal
        "bauhaus" -> FontWeight.Bold
        "swiss" -> FontWeight.ExtraBold
        // Fraunces headings (7 November 2026) look best a little lighter.
        else -> FontWeight.SemiBold
    }

/** The weight of prices. */
val Look.priceWeight: FontWeight
    get() = when (design) {
        "chaos", "pop", "doodle", "cottage", "riso", "synth" -> FontWeight.Normal
        "retro", "anthro" -> FontWeight.SemiBold
        "japandi" -> FontWeight.Medium
        else -> FontWeight.Bold
    }

/** Icon style: "outlined" (Y2K's line-art), "filled" (Retro, Flat), "sharp" (Grunge, Bauhaus) or "rounded". */
val Look.iconStyle: String
    get() = when (design) {
        "y2k", "japandi", "doodle" -> "outlined"
        "retro", "flat", "cottage", "anthro" -> "filled"
        "grunge", "bauhaus", "riso", "swiss", "punk" -> "sharp"
        else -> "rounded"
    }

/** The thickness of the style's lines around cards, buttons, fields and badges (0: none). */
val Look.edge: Dp
    get() = when (design) {
        "chaos" -> 3.dp
        "bauhaus", "doodle", "anthro", "punk" -> 2.5.dp
        "retro", "grunge", "riso" -> 2.dp
        "y2k", "cottage", "synth" -> 1.5.dp
        "japandi" -> 1.dp
        else -> 0.dp
    }

/**
 * A shape with rough, torn edges (Grunge): the outline wobbles inwards by up to [rough], always the same way for the
 * same [seed] (no flicker), so text inside keeps its room.
 */
class RoughShape(private val seed: Int, private val rough: Dp = 1.8.dp, private val step: Dp = 7.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { rough.toPx() }
        val st = with(density) { step.toPx() }.coerceAtLeast(3f)
        var k = seed * 7919
        fun j(): Float { k++; val v = kotlin.math.sin(k * 12.9898 + seed * 78.233) * 43758.5453; return (v - kotlin.math.floor(v)).toFloat() * r }
        val w = size.width; val h = size.height
        val p = Path()
        p.moveTo(j(), j())
        var x = st; while (x < w - st / 2) { p.lineTo(x, j()); x += st }
        p.lineTo(w - j(), j())
        var y = st; while (y < h - st / 2) { p.lineTo(w - j(), y); y += st }
        p.lineTo(w - j(), h - j())
        x = w - st; while (x > st / 2) { p.lineTo(x, h - j()); x -= st }
        p.lineTo(j(), h - j())
        y = h - st; while (y > st / 2) { p.lineTo(j(), y); y -= st }
        p.close()
        return Outline.Generic(p)
    }
    override fun equals(other: Any?) = other is RoughShape && other.seed == seed && other.rough == rough && other.step == step
    override fun hashCode() = seed * 31 + rough.hashCode()
}

/**
 * A hand-drawn shape (Doodle): a rounded box whose sides bow in and out a little, like a marker line, always the same
 * way for the same [seed].
 */
class WobblyShape(private val seed: Int, private val corner: Dp = 12.dp, private val wobble: Dp = 1.6.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        val wb = with(density) { wobble.toPx() }
        fun f(i: Int): Float { val v = kotlin.math.sin((seed * 13 + i) * 12.9898) * 43758.5453; return ((v - kotlin.math.floor(v)).toFloat() - 0.5f) * 2 * wb }
        val w = size.width; val h = size.height
        val p = Path()
        p.moveTo(r, f(0).coerceAtLeast(0f))
        p.quadraticTo(w / 2, f(1), w - r, f(2).coerceAtLeast(0f))
        p.quadraticTo(w, 0f, w - f(3).coerceAtLeast(0f), r)
        p.quadraticTo(w + f(4).coerceAtMost(0f), h / 2, w - f(5).coerceAtLeast(0f), h - r)
        p.quadraticTo(w, h, w - r, h - f(6).coerceAtLeast(0f))
        p.quadraticTo(w / 2, h + f(7).coerceAtMost(0f), r, h - f(8).coerceAtLeast(0f))
        p.quadraticTo(0f, h, f(9).coerceAtLeast(0f), h - r)
        p.quadraticTo(f(10).coerceAtLeast(0f), h / 2, f(11).coerceAtLeast(0f), r)
        p.quadraticTo(0f, 0f, r, f(0).coerceAtLeast(0f))
        p.close()
        return Outline.Generic(p)
    }
    override fun equals(other: Any?) = other is WobblyShape && other.seed == seed && other.corner == corner && other.wobble == wobble
    override fun hashCode() = seed * 31 + corner.hashCode()
}

/** Retro's ticket: rounded corners and a round notch cut into the middle of each side. */
class TicketShape(private val corner: Dp, private val notch: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }.coerceAtMost(minOf(size.width, size.height) / 3)
        val n = with(density) { notch.toPx() }.coerceAtMost(size.height / 4)
        val w = size.width; val h = size.height
        val p = Path()
        p.moveTo(r, 0f); p.lineTo(w - r, 0f); p.quadraticTo(w, 0f, w, r)
        p.lineTo(w, h / 2 - n); p.arcTo(androidx.compose.ui.geometry.Rect(Offset(w, h / 2), n), 270f, -180f, false)
        p.lineTo(w, h - r); p.quadraticTo(w, h, w - r, h); p.lineTo(r, h); p.quadraticTo(0f, h, 0f, h - r)
        p.lineTo(0f, h / 2 + n); p.arcTo(androidx.compose.ui.geometry.Rect(Offset(0f, h / 2), n), 90f, -180f, false)
        p.lineTo(0f, r); p.quadraticTo(0f, 0f, r, 0f); p.close()
        return Outline.Generic(p)
    }
    override fun equals(other: Any?) = other is TicketShape && other.corner == corner && other.notch == notch
    override fun hashCode() = corner.hashCode() * 31 + notch.hashCode()
}

/** Pop futurism's streamlined shape: two big round corners and two small ones, like a seventies panel. */
private fun stream(big: Dp, small: Dp) = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)
private val pill = RoundedCornerShape(50)

/** The Material shapes (dialogs, menus, text fields, snackbars) of each style. */
fun shapesFor(design: String): Shapes = when (design) {
    "chaos" -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
    )
    "y2k" -> Shapes(
        extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)
    )
    "retro", "flat" -> Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp)
    )
    "pop" -> Shapes(
        extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp), medium = stream(20.dp, 6.dp),
        large = stream(24.dp, 8.dp), extraLarge = stream(30.dp, 8.dp)
    )
    // Grunge: chipped corners (Material's parts need corner shapes; dialogs get the rough edge, [dialogShape]).
    "grunge" -> Shapes(
        extraSmall = CutCornerShape(2.dp), small = CutCornerShape(3.dp), medium = CutCornerShape(4.dp),
        large = CutCornerShape(5.dp), extraLarge = CutCornerShape(6.dp)
    )
    "bauhaus" -> Shapes(
        extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp)
    )
    "japandi" -> Shapes(
        extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp)
    )
    "swiss", "punk" -> Shapes(
        extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp)
    )
    "riso", "synth" -> Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp), extraLarge = RoundedCornerShape(12.dp)
    )
    "doodle", "anthro" -> Shapes(
        extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
    )
    "cottage" -> Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(18.dp)
    )
    else -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
    )
}

/** The shape of dialogs: Material's largest shape, Grunge's rough edge. */
val Look.dialogShape: Shape
    get() = when (design) {
        "grunge" -> RoughShape(7, 2.4.dp, 9.dp)
        "doodle" -> WobblyShape(7, 24.dp, 2.dp)
        "punk" -> RoughShape(8, 3.dp, 8.dp)
        else -> shapesFor(design).extraLarge
    }

/** The shape of a card. */
fun Look.cardShape(id: Long, small: Boolean): Shape = when (design) {
    "chaos" -> RoundedCornerShape(if (small) 14.dp else 18.dp)
    "y2k" -> y2kCardShape(small)
    "pop" -> if (small) stream(22.dp, 6.dp) else stream(28.dp, 8.dp)
    "grunge" -> RoughShape(pick(id, 97) + 11, if (small) 1.8.dp else 2.2.dp)
    "flat" -> RoundedCornerShape(if (small) 10.dp else 12.dp)
    "bauhaus" -> RectangleShape
    "doodle" -> when (set) {
        "sticky" -> DogEarShape(2.dp, 16.dp)
        "board" -> WobblyShape(pick(id, 89) + 3, 5.dp, 1.4.dp)
        else -> WobblyShape(pick(id, 89) + 3, if (small) 14.dp else 18.dp)
    }
    "cottage" -> RoundedCornerShape(if (small) 12.dp else 14.dp)
    "japandi" -> RoundedCornerShape(4.dp)
    "riso" -> RoundedCornerShape(if (small) 6.dp else 8.dp)
    "synth" -> RoundedCornerShape(if (small) 10.dp else 12.dp)
    "anthro" -> RoundedCornerShape(if (small) 20.dp else 24.dp)
    "swiss" -> RectangleShape
    // Punk: paper torn out of a magazine, each piece its own way.
    "punk" -> punkCardShape(id, small)
    // Retro: a ticket with a round notch on each side.
    "retro" -> retroCardShape(small)
    else -> RoundedCornerShape(if (small) 16.dp else 20.dp)
}

/** Space around the picture inside a card (none in any style since the neumorphic one went). */
@Suppress("UNUSED_PARAMETER")
fun Look.inset(small: Boolean): Dp = 0.dp

@Suppress("UNUSED_PARAMETER")
fun Look.pictureShape(id: Long, small: Boolean): Shape = RectangleShape

/** Behind a picture without a photo: a soft colour of the look (a photo gets its own Blur stage, PhotoStage). */
@Suppress("UNUSED_PARAMETER")
fun Look.pictureBg(id: Long, hasImage: Boolean): Color = tints[pick(id, tints.size)]

/** A line under the picture: the style's own line (chaos, Y2K, Retro, Grunge, Bauhaus). */
val Look.pictureLine: Dp get() = edge

fun Look.cardFill(id: Long): Color = fills[pick(id, fills.size)]

/** The text colour on a card. */
fun Look.cardInk(@Suppress("UNUSED_PARAMETER") id: Long): Color = scheme.onSurface

/** The quieter text (shop, description, icons) on a card. */
fun Look.cardMuted(@Suppress("UNUSED_PARAMETER") id: Long): Color = scheme.onSurfaceVariant

/** Small labels on a card (price change, "Owned"). */
fun Look.badgeShape(small: Boolean): Shape = when (design) {
    "chaos" -> RoundedCornerShape(10.dp)
    "y2k", "pop", "bauhaus", "cottage", "anthro" -> pill
    "retro", "flat", "japandi", "riso", "synth" -> RoundedCornerShape(4.dp)
    "doodle" -> WobblyShape(23, 8.dp, 1.dp)
    "swiss" -> RectangleShape
    "punk" -> RoughShape(25, 1.4.dp, 5.dp)
    "grunge" -> RoughShape(21, 1.dp, 5.dp)
    else -> RoundedCornerShape(if (small) 6.dp else 8.dp)
}

/** Small buttons such as the category button and the view switch. */
val Look.chipShape: Shape
    get() = when (design) {
        "chaos" -> RoundedCornerShape(12.dp)
        "retro" -> TagShape(8.dp)
        "riso" -> RoundedCornerShape(6.dp)
        "flat", "synth" -> RoundedCornerShape(8.dp)
        "japandi" -> RoundedCornerShape(4.dp)
        "grunge" -> RoughShape(31, 1.2.dp, 6.dp)
        "doodle" -> WobblyShape(33, 14.dp, 1.2.dp)
        "swiss" -> RectangleShape
        "punk" -> RoughShape(35, 1.4.dp, 6.dp)
        else -> CircleShape
    }

/** The Add wish button (and the + in the bottom bar). */
val Look.fabShape: Shape
    get() = when (design) {
        "chaos" -> RoundedCornerShape(16.dp)
        "retro", "flat" -> RoundedCornerShape(14.dp)
        "japandi" -> RoundedCornerShape(6.dp)
        "riso", "synth" -> RoundedCornerShape(10.dp)
        "grunge" -> RoughShape(41, 1.6.dp, 7.dp)
        "doodle" -> WobblyShape(43, 22.dp, 1.6.dp)
        "swiss" -> RectangleShape
        "punk" -> RoughShape(45, 1.8.dp, 7.dp)
        else -> CircleShape
    }

/** The shape of ordinary buttons. */
val Look.buttonShape: Shape
    get() = when (design) {
        "chaos" -> RoundedCornerShape(14.dp)
        "retro" -> TicketShape(8.dp, 5.dp)
        "flat", "riso", "synth" -> RoundedCornerShape(8.dp)
        "japandi" -> RoundedCornerShape(4.dp)
        "grunge" -> RoughShape(51, 1.4.dp, 7.dp)
        "doodle" -> if (set == "notebook" || set.isEmpty()) WobblyShape(53, 22.dp, 1.4.dp) else WobblyShape(53, 8.dp, 1.4.dp)
        "bauhaus", "swiss" -> RectangleShape
        "punk" -> RoughShape(55, 1.6.dp, 7.dp)
        else -> CircleShape
    }

/** Gen Z chaos tilts cards a little, each its own way (by its id); Grunge only a touch, like pasted-up paper. */
fun Look.tilt(id: Long): Float = when (design) {
    "chaos" -> listOf(-1.6f, 1.2f, -0.8f, 1.8f, -1.2f, 0.9f)[pick(id, 6)]
    // Wild placement (owner, 13 November 2026), each its own way: Punk the most, cut-outs pasted down crooked; Grunge
    // crooked paper; Doodle loose sketches. (Risograph doesn't turn: its layers slide sideways, [nudge].)
    "punk" -> listOf(-1.8f, 1.3f, -0.7f, 2.0f, -1.4f, 0.9f, -2.2f)[pick(id, 7)] * listOf(0.3f, 1f, 1.5f)[deco]
    "grunge" -> listOf(-1.1f, 0.8f, -0.4f, 1.2f, -0.9f)[pick(id, 5)]
    // Doodle: by the amount of decoration (Calm straight, Normal loose, Wild all over the place).
    "doodle" -> listOf(-0.9f, 0.7f, -0.4f, 0.8f)[pick(id, 4)] * listOf(0f, 1f, 2.2f)[deco]
    else -> 0f
}

/** A strip of sticky tape across [at], turned [angle] degrees (Punk, Doodle). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.tape(
    at: Offset, angle: Float, colour: Color = Color(0xFFF2E6B8).copy(alpha = 0.75f), width: Dp = 46.dp, height: Dp = 14.dp
) {
    val t = Size(width.toPx(), height.toPx())
    rotate(angle, at) { drawRect(colour, Offset(at.x - t.width / 2, at.y - t.height / 2), t) }
}

/** Cards slid out of line (wild placement): Punk's pasted-up cut-outs, Risograph's layers printed a little off. */
fun Look.nudge(id: Long): Dp = when (design) {
    "punk" -> (listOf(-6f, 4f, 0f, 7f, -3f, 5f, -7f)[pick(id, 7)] * listOf(0f, 1f, 1.4f)[deco]).dp
    "riso" -> listOf(-5f, 3f, 6f, -2f, 4f)[pick(id, 5)].dp
    "doodle" -> if (deco == 2) listOf(-4f, 3f, 0f, 5f, -2f)[pick(id, 5)].dp else 0.dp
    else -> 0.dp
}

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

/** Retro's printed label: a thin second line just inside the edge (only on bigger things, not on chips). */
private fun Modifier.keyline(look: Look, shape: Shape, stitched: Boolean = false, min: Dp = 56.dp): Modifier = drawWithCache {
    val i = 4.dp.toPx()
    val show = size.minDimension >= min.toPx()
    val outline = shape.createOutline(Size(size.width - 2 * i, size.height - 2 * i), layoutDirection, this)
    // Cottage-core: running stitches, like a quilt; Retro: a printed fine line.
    val st = if (stitched) Stroke(1.3.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx())))
        else Stroke(1.dp.toPx())
    val col = look.line.copy(alpha = if (stitched) 0.7f else 0.55f)
    onDrawBehind { if (show) translate(i, i) { drawOutline(outline, col, style = st) } }
}

/**
 * Any surface in the chosen style: a card, a dialog, a button, a chip, the bottom bar. [fill] its colour (or [brush],
 * a gradient); [raised] false = without the shadow (fields, pressed-in things). [depth] how big the shadow is. The
 * content is clipped to [shape].
 */
fun Modifier.lookSurface(
    look: Look, shape: Shape, fill: Color, raised: Boolean = true, depth: Dp = 6.dp, brush: Brush? = null, shadow: Color? = null,
    card: Boolean = false
): Modifier {
    fun Modifier.paint() = clip(shape).then(if (brush != null) Modifier.background(brush) else Modifier.background(fill))
    val d = depth.value
    return when (look.design) {
        "chaos" -> (if (raised) hardShadow(look.shadows[0], shape, 5.dp) else this).paint().edgeLine(3.dp, look.line, shape)
        // Y2K (Y2k.kt): a holo hard shadow or a soft glow, a white rim on glass, a gloss on buttons and glossy cards.
        "y2k" -> y2kShadow(look, shape, raised, d, card).paint().then(Modifier.y2kFinish(look, shape, card))
        // Retro (Retro.kt): the misprinted colour shadow, the line, a ticket's keyline, a postcard's border and stamp,
        // an enamel sign's rim and screws.
        "retro" -> retroShadow(look, shape, raised, d).paint().then(Modifier.retroFinish(look, shape, card))
        "pop" -> (if (raised) hardShadow(look.shadows[1], shape, (d * 0.9f).coerceIn(3f, 8f).dp)
            .hardShadow(look.shadows[0], shape, (d * 0.45f).coerceIn(1.5f, 4f).dp) else this).paint()
        "grunge" -> (if (raised) hardShadow(look.shadows[0], shape, (d * 0.5f).coerceIn(2f, 4f).dp) else this)
            .paint().edgeLine(look.edge, look.line, shape)
        // Flat: the "long shadow" of flat design on buttons: a darker band running off to the bottom right.
        "flat" -> (if (raised) drawWithCache {
            val show = size.height < 64.dp.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            val col = lerp(fill, Color.Black, 0.28f).copy(alpha = 0.55f)
            val n = 10.dp.toPx()
            onDrawBehind { if (show) { var k = 1f; while (k <= n) { translate(k, k) { drawOutline(outline, col) }; k += 1.5f } } }
        } else this).paint()
        // Doodle: a sketchy marker line drawn twice (the second a little off) and, raised, a hatched pencil shadow; the
        // "Sticker" card style: a thick line and a solid shadow instead (Doodle's options, 2.202).
        "doodle" -> doodleSurface(look, shape, raised, d, card).paint().then(Modifier.doodleLine(look, shape, card))
        // Cottage-core: a soft printed shadow, a fine line and an inner keyline like a seed packet.
        "cottage" -> (if (raised) hardShadow(look.shadows[0], shape, (d * 0.4f).coerceIn(2f, 3f).dp) else this)
            .paint().keyline(look, shape, stitched = true, min = 36.dp).edgeLine(look.edge, look.line, shape)
        // Japandi: a hairline, nothing else.
        "japandi" -> paint().edgeLine(look.edge, look.line.copy(alpha = 0.6f), shape)
        // Risograph: the pink layer printed a little off, under the blue outline.
        // …and the blue outline printed once more, a little off (overprint).
        "riso" -> (if (raised) hardShadow(look.shadows[0], shape, (d * 0.6f).coerceIn(2.5f, 5f).dp) else this)
            .paint().edgeLine(look.edge, look.line, shape).drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val col = look.shadows.getOrElse(1) { look.line }.copy(alpha = 0.6f)
                val st = Stroke(1.5.dp.toPx())
                val dx = -2.dp.toPx(); val dy = 1.5.dp.toPx()
                onDrawWithContent { drawContent(); translate(dx, dy) { drawOutline(outline, col, style = st) } }
            }
        // Swiss: no frame and no shadow; a thick rule along the top of each card, like a column on a grid.
        "swiss" -> paint().drawBehind {
            if (size.height >= 64.dp.toPx()) drawRect(look.line, Offset.Zero, Size(size.width, 4.dp.toPx()))
        }.then(if (size0(look, raised)) Modifier.edgeLine(2.dp, look.line, shape) else Modifier)
        // Punk (Punk.kt): a hard shadow (a taped flyer curls instead), the ink line, a badge's stud; buttons taped down.
        "punk" -> punkShadow(look, shape, raised, d, card).paint().then(Modifier.punkFinish(look, shape, card))
        // Anthropomorphic: a cartoon outline and a hard ink shadow.
        "anthro" -> (if (raised) hardShadow(look.shadows[0], shape, (d * 0.6f).coerceIn(2.5f, 5f).dp) else this)
            .paint().edgeLine(look.edge, look.line, shape)
        "bauhaus" -> (if (raised) hardShadow(shadow ?: look.shadows[0], shape, (d * 0.7f).coerceIn(3f, 6f).dp) else this)
            .paint().edgeLine(look.edge, look.line, shape)
        // Quiet: just the card colour; in light a hairline so white cards don't melt into each other.
        else -> paint().then(if (!look.dark) Modifier.edgeLine(1.dp, Color.Black.copy(alpha = 0.04f), shape) else Modifier)
    }
}

/** The frame of a wish card (or any card) in the chosen style: its colour, lines, shadow, shape and tilt. */
fun Modifier.lookCard(look: Look, id: Long, shape: Shape): Modifier {
    val t = look.tilt(id)
    val n = look.nudge(id)
    // Bauhaus: each card's block behind it in its own primary colour.
    val block = if (look.design == "bauhaus") listOf(look.art[0], look.art[1], look.art[2])[pick(id, 3)] else null
    return (if (n != 0.dp) offset(x = n) else this).then(if (t != 0f) Modifier.rotate(t) else Modifier)
        .lookSurface(look, shape, look.cardFill(id), depth = 8.dp, shadow = block, card = true)
        .then(if (look.design == "doodle" && look.set == "sticky") Modifier.doodleFold(look, if (shape is DogEarShape) 16.dp else 0.dp) else Modifier)
        .then(if (look.design == "doodle") Modifier.doodlePin(look, id) else Modifier)
        .then(if (look.design == "y2k") Modifier.y2kStickers(look, id) else Modifier)
        .then(if (look.design == "punk") Modifier.punkFasteners(look, id) else Modifier)
        .then(if (look.design == "retro") Modifier.retroStamps(look, id) else Modifier)
}

/** Swiss buttons (filled ones excepted) are framed in black; whether this surface is one. */
private fun size0(look: Look, raised: Boolean): Boolean = look.design == "swiss" && !raised

/** The mat a picture sits on inside a card. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.lookMat(look: Look, shape: Shape, fill: Color, over: Boolean = false): Modifier = clip(shape).background(fill)

/** The frame of a small button (category, view switch, a chip). [on] = chosen. */
fun Modifier.lookChip(look: Look, on: Boolean = false): Modifier {
    val cs = look.scheme
    val shape = look.chipShape
    val off = if (look.design == "flat") lerp(cs.surfaceVariant, look.scheme.outline, 0.18f) else cs.surfaceVariant
    return when (look.design) {
        "chaos" -> (if (on) hardShadow(look.shadows[0], shape, 3.dp) else this)
            .clip(shape).background(if (on) look.tints[0] else cs.surfaceVariant).edgeLine(2.dp, look.line, shape)
        "quiet" -> clip(shape).background(if (on) cs.onSurface else cs.surfaceVariant)
        else -> {
            // The other styles: the chosen one filled like a button (gradient included), the others plain, with the
            // style's line (Pop futurism has no lines elsewhere, but a chip needs an edge to read as a button).
            val line = if (look.design == "pop") 1.5.dp else minOf(look.edge, 2.dp)
            // Punk: the chosen chip is a stamp, turned a little.
            val base = if (on && look.design == "bauhaus") hardShadow(look.shadows[0], shape, 3.dp)
                else if (on && look.design == "punk") rotate(-2.5f) else this
            base.clip(shape)
                .then(if (on && look.accentBrush != null) Modifier.background(look.accentBrush) else Modifier.background(if (on) look.accent else off))
                .then(if (line > 0.dp) Modifier.edgeLine(line, look.line, shape) else Modifier)
                // Retro: the chosen chip is a luggage tag with its hole.
                .then(if (look.design == "retro" && on) Modifier.retroTagHole(look) else Modifier)
        }
    }
}

/** The colour of the text and icons on a chip made with [lookChip]. */
fun Look.chipContent(on: Boolean): Color = when {
    !on -> scheme.onSurface
    design == "quiet" -> if (dark) Color.Black else Color.White
    design == "chaos" -> Color(0xFF0D0D0D)
    else -> onAccent
}

/** A few thousand points spread over [w] × [h], the same every time (grain and speckles); [edge] crowds them outwards. */
private fun grain(w: Float, h: Float, n: Int, seed: Int, edge: Boolean = false): List<Offset> {
    val out = ArrayList<Offset>(n)
    var k = seed
    fun r(): Float { k++; val v = kotlin.math.sin(k * 12.9898 + seed * 4.1414) * 43758.5453; return (v - kotlin.math.floor(v)).toFloat() }
    while (out.size < n) {
        val x = r() * w; val y = r() * h
        if (edge) {
            // Keep a point more often the nearer it is to an edge (a photocopy's dirty edges).
            val dx = minOf(x, w - x) / (w / 2); val dy = minOf(y, h - y) / (h / 2)
            if (r() < minOf(dx, dy) * 1.6f) continue
        }
        out.add(Offset(x, y))
    }
    return out
}

/** A four-pointed sparkle (Y2K), centred on [c] with arm length [r]. */
private fun sparkle(c: Offset, r: Float): Path = Path().apply {
    val t = r * 0.18f
    moveTo(c.x, c.y - r)
    quadraticTo(c.x + t, c.y - t, c.x + r, c.y)
    quadraticTo(c.x + t, c.y + t, c.x, c.y + r)
    quadraticTo(c.x - t, c.y + t, c.x - r, c.y)
    quadraticTo(c.x - t, c.y - t, c.x, c.y - r)
    close()
}

/**
 * What is drawn behind the screens (built once per screen size, so scrolling stays smooth). Behind the text it stays
 * faint (LookTest checks the contrast of text over it); bold colour only in the outer edge of the screen, where text
 * never goes. Quiet stays plain.
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
    "y2k" -> y2kBackdrop(look)
    "retro" -> retroBackdrop(look)
    "pop" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val pts = grain(w, h, 1800, 13)
        val big = 120 * u
        val planet = Offset(w * 0.95f, 70 * u)
        onDrawBehind {
            // A sunset planet in the top corner, with stripes cut through its lower half.
            drawCircle(Brush.verticalGradient(listOf(look.blobs[0], look.blobs[1], look.blobs[2]), planet.y - big, planet.y + big), big, planet)
            for (i in 0 until 5) {
                val y = planet.y + big * (0.25f + i * 0.15f)
                drawRect(look.scheme.background, Offset(planet.x - big, y), Size(big * 2, (2 + i) * u))
            }
            // A small teal planet with a ring.
            val small = Offset(w * 0.06f, h * 0.55f)
            drawCircle(look.blobs[3], 36 * u, small)
            drawOval(look.blobs[3], Offset(small.x - 58 * u, small.y - 8 * u), Size(116 * u, 16 * u), style = Stroke(2 * u))
            // Racing stripes down the right edge (where text never goes).
            listOf(look.art[3], look.art[0], look.art[1]).forEachIndexed { i, col ->
                drawRect(col, Offset(w - (11 - i * 4) * u, 0f), Size(3 * u, h))
            }
            drawPoints(pts, PointMode.Points, look.scheme.onBackground.copy(alpha = if (look.dark) 0.07f else 0.05f), strokeWidth = 1.3f * u)
        }
    }
    "grunge" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val specks = grain(w, h, 3200, 29, edge = true)
        val dust = grain(w, h, 500, 31)
        onDrawBehind {
            drawPoints(specks, PointMode.Points, look.blobs[0], strokeWidth = 2.2f * u)
            drawPoints(dust, PointMode.Points, look.blobs[0], strokeWidth = 3.2f * u)
            // Splatters of red ink in two corners.
            listOf(Offset(w * 0.96f, h * 0.08f) to 46f, Offset(w * 0.04f, h * 0.9f) to 38f).forEach { (c, r) ->
                drawCircle(look.blobs[1], r * u, c)
                for (k in 0 until 9) {
                    val a = k * 0.7 + r
                    val d = (r + 10 + (k * 7) % 22) * u
                    drawCircle(look.blobs[1], (2 + k % 4) * u, Offset(c.x + (kotlin.math.cos(a) * d).toFloat(), c.y + (kotlin.math.sin(a) * d).toFloat()))
                }
            }
            // A couple of scratches.
            drawLine(look.blobs[0], Offset(w * 0.12f, h * 0.2f), Offset(w * 0.4f, h * 0.17f), 1.2f * u)
            drawLine(look.blobs[0], Offset(w * 0.6f, h * 0.74f), Offset(w * 0.93f, h * 0.79f), 1.2f * u)
        }
    }
    "flat" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        fun hill(base: Float, amp: Float, phase: Float) = Path().apply {
            moveTo(0f, h)
            lineTo(0f, base)
            var x = 0f
            while (x <= w) { lineTo(x, base - amp * (0.5f + 0.5f * kotlin.math.sin(x / w * 6.2f + phase))); x += 8 * u }
            lineTo(w, h); close()
        }
        val far = hill(h - 150 * u, 50 * u, 0.4f); val mid = hill(h - 105 * u, 38 * u, 2.1f); val near = hill(h - 60 * u, 28 * u, 4f)
        onDrawBehind {
            // A flat sun, a sand band and three layers of hills at the bottom.
            drawCircle(look.blobs[3], 64 * u, Offset(w * 0.86f, 70 * u))
            drawPath(far, look.blobs[0]); drawPath(mid, look.blobs[2]); drawPath(near, look.blobs[1])
        }
    }
    "bauhaus" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        onDrawBehind {
            // Big, faint shapes: a quarter circle, a square, a circle and a triangle.
            drawArc(look.blobs[0], 90f, 90f, true, Offset(w - 150 * u, -150 * u), Size(300 * u, 300 * u))
            drawRect(look.blobs[1], Offset(-70 * u, h * 0.56f), Size(110 * u, 110 * u))
            drawCircle(look.blobs[2], 80 * u, Offset(w * 0.88f, h * 0.8f))
            drawPath(Path().apply { moveTo(w * 0.3f, h); lineTo(w * 0.55f, h - 140 * u); lineTo(w * 0.8f, h); close() }, look.blobs[3])
            // The bold grid along the right edge (where text never goes): red, yellow, blue and black blocks.
            val seg = h / 7
            listOf(look.art[0], look.art[2], look.art[1], look.art[3], look.art[0], look.art[1], look.art[2]).forEachIndexed { i, col ->
                drawRect(col, Offset(w - 8 * u, seg * i), Size(8 * u, seg - 3 * u))
            }
        }
    }
    "doodle" -> doodleBackdrop(look)
    "cottage" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        fun flower(c: Offset, r: Float, col: Color) = Path().apply {
            for (i in 0 until 5) {
                val a = Math.toRadians(i * 72.0 - 90.0)
                addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + (kotlin.math.cos(a) * r).toFloat(), c.y + (kotlin.math.sin(a) * r).toFloat()), r * 0.62f))
            }
        }
        val spots = listOf(Offset(w * 0.9f, 80 * u), Offset(w * 0.08f, h * 0.32f), Offset(w * 0.86f, h * 0.6f), Offset(w * 0.14f, h * 0.88f), Offset(w * 0.6f, 24 * u))
        onDrawBehind {
            // A faint gingham check: each stripe half as strong, so where two cross it is never stronger than the tested
            // colour (LookTest).
            val step = 22 * u
            val half = look.blobs[0].copy(alpha = look.blobs[0].alpha / 2)
            var x = 0f
            while (x < w) { drawRect(half, Offset(x, 0f), Size(step / 2, h)); x += step }
            var y = 0f
            while (y < h) { drawRect(half, Offset(0f, y), Size(w, step / 2)); y += step }
            // Little flowers with leaves.
            spots.forEachIndexed { i, c ->
                val col = if (i % 2 == 0) look.blobs[1] else look.blobs[2]
                drawOval(look.blobs[0], Offset(c.x + 4 * u, c.y + 6 * u), Size(16 * u, 7 * u))
                drawPath(flower(c, 7 * u, col), col)
                drawCircle(look.blobs[2], 3 * u, c)
            }
            // A sprig along the left edge (where text never goes).
            for (i in 0 until 12) {
                val cy = h * 0.15f + i * 22 * u
                drawLine(look.art[0], Offset(5 * u, cy), Offset(5 * u, cy + 22 * u), 1.5f * u)
                drawOval(look.art[0], Offset(5 * u, cy + 4 * u), Size(7 * u, 4 * u))
            }
        }
    }
    "japandi" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val line = Stroke(1.2f * u)
        onDrawBehind {
            // An ensō (a brushed circle, left open) top right, and seigaiha waves along the bottom.
            drawArc(look.blobs[1], 110f, 320f, false, Offset(w - 130 * u, 20 * u), Size(110 * u, 110 * u), style = Stroke(6 * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val r = 28 * u
            var row = 0
            var y = h - 20 * u
            while (row < 4) {
                var x = if (row % 2 == 0) 0f else r
                while (x < w + r) {
                    for (k in 1..3) drawArc(look.blobs[0], 180f, 180f, false, Offset(x - r * k / 3, y - r * k / 3), Size(2 * r * k / 3, 2 * r * k / 3), style = line)
                    x += 2 * r
                }
                y -= r * 0.6f; row++
            }
            drawLine(look.blobs[2], Offset(24 * u, 64 * u), Offset(w * 0.4f, 64 * u), 1f * u)
        }
    }
    "riso" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val pts = grain(w, h, 2600, 41)
        fun dots(c: Offset, r: Float, step: Float): List<Offset> {
            val out = ArrayList<Offset>()
            var y = c.y - r
            while (y < c.y + r) { var x = c.x - r; while (x < c.x + r) { if ((Offset(x, y) - c).getDistance() < r) out += Offset(x, y); x += step }; y += step }
            return out
        }
        val pinkDots = dots(Offset(w * 0.92f, 100 * u), 80 * u, 7 * u)
        val blueDots = dots(Offset(w * 0.06f, h * 0.62f), 70 * u, 7 * u)
        onDrawBehind {
            // Two halftone circles, a yellow block and a pink triangle, printed a little off each other, and grain.
            drawPoints(pinkDots, PointMode.Points, look.blobs[0], strokeWidth = 3.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawPoints(blueDots, PointMode.Points, look.blobs[1], strokeWidth = 3.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawRect(look.blobs[2], Offset(w * 0.7f, h * 0.78f), Size(90 * u, 60 * u))
            drawPath(Path().apply { moveTo(w * 0.08f, h * 0.18f); lineTo(w * 0.3f, h * 0.24f); lineTo(w * 0.1f, h * 0.32f); close() }, look.blobs[0])
            drawPoints(pts, PointMode.Points, look.blobs[3], strokeWidth = 1.5f * u)
            // Riso stripes on the right edge (where text never goes).
            drawRect(look.art[0], Offset(w - 9 * u, 0f), Size(4 * u, h))
            drawRect(look.art[1], Offset(w - 7.5f * u, 0f), Size(4 * u, h), alpha = 0.7f)
        }
    }
    "synth" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val sunC = Offset(w * 0.8f, 110 * u); val sunR = 70 * u
        val horizon = h - 170 * u
        onDrawBehind {
            // A striped sunset sun.
            drawCircle(Brush.verticalGradient(listOf(look.blobs[2], look.blobs[0]), sunC.y - sunR, sunC.y + sunR), sunR, sunC)
            for (i in 0 until 5) drawRect(look.scheme.background, Offset(sunC.x - sunR, sunC.y + sunR * (0.15f + i * 0.17f)), Size(sunR * 2, (2 + i) * u))
            // The neon grid: a horizon and lines running towards the middle.
            drawLine(look.blobs[0], Offset(0f, horizon), Offset(w, horizon), 1.5f * u)
            for (i in -8..8) drawLine(look.blobs[1], Offset(w / 2 + i * 14 * u, horizon), Offset(w / 2 + i * 90 * u, h), 1.2f * u)
            var k = 1f
            while (horizon + 6 * u * k * k < h) { val y = horizon + 6 * u * k * k; drawLine(look.blobs[1], Offset(0f, y), Offset(w, y), 1.2f * u); k += 1f }
            // Palm trees at the edges.
            fun palm(x: Float, flip: Float) {
                val top = Offset(x + 18 * u * flip, horizon - 120 * u)
                drawPath(Path().apply { moveTo(x, h); quadraticTo(x + 4 * u * flip, horizon - 40 * u, top.x, top.y) }, look.blobs[3], style = Stroke(5 * u))
                for (a in listOf(-160f, -120f, -60f, -20f, 20f)) rotate(a * flip, top) {
                    drawPath(Path().apply { moveTo(top.x, top.y); quadraticTo(top.x + 20 * u, top.y - 12 * u, top.x + 44 * u, top.y + 6 * u) }, look.blobs[3], style = Stroke(4 * u))
                }
            }
            palm(6 * u, 1f); palm(w - 6 * u, -1f)
            // A neon line down the left edge (where text never goes).
            drawRect(look.art[2], Offset(3 * u, 0f), Size(2.5f * u, h))
            drawRect(look.art[3], Offset(7 * u, 0f), Size(1.5f * u, h))
        }
    }
    "anthro" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        val confetti = grain(w, h, 40, 57)
        onDrawBehind {
            // Big soft blobs and flat confetti: stars, dots and little squiggles.
            drawCircle(look.blobs[0], 90 * u, Offset(w * 0.95f, 40 * u))
            drawCircle(look.blobs[3], 70 * u, Offset(0f, h * 0.55f))
            drawCircle(look.blobs[2], 60 * u, Offset(w * 0.9f, h * 0.85f))
            confetti.forEachIndexed { i, p ->
                val col = look.blobs[i % look.blobs.size]
                when (i % 3) {
                    0 -> drawCircle(col, 3 * u, p)
                    1 -> rotate(i * 25f, p) { drawRect(col, Offset(p.x - 5 * u, p.y - 1.5f * u), Size(10 * u, 3 * u)) }
                    else -> drawPath(sparkle(p, 6 * u), col)
                }
            }
        }
    }
    "swiss" -> drawWithCache {
        val w = size.width; val h = size.height
        val u = 1.dp.toPx()
        onDrawBehind {
            // The grid: six columns and a baseline every 96 dp, faint; a colour bar down the left edge (where text
            // never goes) and one big circle quarter low on the right.
            val col = (w - 32 * u) / 6
            for (i in 0..6) drawLine(look.blobs[0], Offset(16 * u + i * col, 0f), Offset(16 * u + i * col, h), 1f * u)
            var y = 96 * u
            while (y < h) { drawLine(look.blobs[0], Offset(0f, y), Offset(w, y), 1f * u); y += 96 * u }
            drawArc(look.blobs[1], 180f, 90f, true, Offset(w - 120 * u, h - 120 * u), Size(240 * u, 240 * u))
            drawRect(look.art[0], Offset(0f, 0f), Size(6 * u, h))
        }
    }
    "punk" -> punkBackdrop(look)
    else -> this
}

/**
 * The accent as a colour for text, icons and focus rings, readable on the page and on cards: in Quiet and Gen Z chaos
 * your accent (as before); in the other styles their own text colour ([accent] is for filled blocks there, such as
 * Y2K's pale lilac, and would be too light for text).
 */
val Look.textAccent: Color get() = if (design == "quiet" || design == "chaos") accent else scheme.primary

/**
 * The look in another colour set: its own button colour, text colour, drawing colours and (for gradients) its own
 * gradient; the page, cards, text and lines stay the style's. [mix] keeps each drawing colour's strength (alpha), so
 * what LookTest checked for readability stays true. Every set has a light and a dark version.
 */
private fun Look.recolour(
    primary: Color, onPrimary: Color, container: Color, fill: Color, onFill: Color, art: List<Color>,
    blobs: List<Color>? = null, shadows: List<Color>? = null, brush: List<Color>? = null, tints: List<Color>? = null
): Look {
    val sc = scheme.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = container,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, inversePrimary = container
    )
    val b = blobs?.mapIndexed { i, h -> if (this.blobs.getOrNull(i)?.alpha?.let { it < 1f } == true) h.copy(alpha = this.blobs[i].alpha) else h } ?: this.blobs
    val br = brush?.let { if (design == "synth") Brush.linearGradient(it, Offset.Zero, Offset(0f, Float.POSITIVE_INFINITY)) else Brush.linearGradient(it, Offset.Zero, Offset.Infinite) }
    return Look(
        design, dark, sc, fills, tints ?: this.tints, line, shadows ?: this.shadows, b, display, drop, rise,
        accent = fill, onAccent = onFill, art = art, accentBrush = br ?: accentBrush, shadowBrush = if (design == "y2k") br ?: shadowBrush else shadowBrush
    )
}

/** This look in colour set [set] of its style (the first set is the look itself). */
private fun Look.withSet(set: String): Look {
    val d = dark
    fun c2(l: Long, dk: Long) = Color(if (d) dk else l)
    return when ("$design/$set") {
        "pop/lagoon" -> recolour(
            c2(0xFF0F6E78, 0xFF7FE3D6), c2(0xFFFFF7EA, 0xFF1B1A3A), c2(0xFFCDEFEA, 0xFF1E4A5A), Color(0xFF4DD4C6), Color(0xFF1B1A3A),
            listOf(Color(0xFF9BF0D6), Color(0xFF4DD4C6), Color(0xFF58A6FF), Color(0xFFF26A2E), Color(0xFF1E3A8A)),
            listOf(Color(0xFF9BF0D6), Color(0xFF4DD4C6), Color(0xFF58A6FF), Color(0xFFF26A2E)),
            listOf(Color(0xFF3A86FF), Color(0xFF4DD4C6)), listOf(Color(0xFF9BF0D6), Color(0xFF4DD4C6), Color(0xFF58A6FF))
        )
        "pop/cosmic" -> recolour(
            c2(0xFF8A1F5C, 0xFFF7A8C4), c2(0xFFFFF7EA, 0xFF1B1A3A), c2(0xFFF7DCEB, 0xFF4A2A5A), Color(0xFFF7799A), Color(0xFF1B1A3A),
            listOf(Color(0xFFFFD166), Color(0xFFF7799A), Color(0xFFC39BF2), Color(0xFF3BC4B0), Color(0xFF5B2A86)),
            listOf(Color(0xFFFFD166), Color(0xFFF7799A), Color(0xFFC39BF2), Color(0xFF3BC4B0)),
            listOf(Color(0xFF3BC4B0), Color(0xFFF7799A)), listOf(Color(0xFFFFD166), Color(0xFFF7799A), Color(0xFFC39BF2))
        )
        "grunge/toxic" -> recolour(
            c2(0xFF3F6B12, 0xFFA6D96A), c2(0xFFF8F6F2, 0xFF131211), c2(0xFFDDE8CF, 0xFF24331A), c2(0xFF3F6B12, 0xFF8BC34A), c2(0xFFF8F6F2, 0xFF131211),
            listOf(c2(0xFF3F6B12, 0xFF8BC34A), art[1], art[2]), listOf(blobs[0], c2(0xFF3F6B12, 0xFF6E9E3A))
        )
        "grunge/bruise" -> recolour(
            c2(0xFF5B2A86, 0xFFC9A8EE), c2(0xFFF8F6F2, 0xFF131211), c2(0xFFE4D9F0, 0xFF2E2240), c2(0xFF5B2A86, 0xFFB58CE0), c2(0xFFF8F6F2, 0xFF131211),
            listOf(c2(0xFF5B2A86, 0xFFB58CE0), art[1], art[2]), listOf(blobs[0], c2(0xFF5B2A86, 0xFFB58CE0))
        )
        "flat/forest" -> recolour(
            c2(0xFF2D6A3E, 0xFF9BD8A8), c2(0xFFFFFFFF, 0xFF0B2530), c2(0xFFD3EBD8, 0xFF1E4A33), c2(0xFF2D6A3E, 0xFF8FD19E), c2(0xFFFFFFFF, 0xFF0B2530),
            listOf(Color(0xFF2D6A3E), Color(0xFF6BAF5E), Color(0xFFE9C46A), Color(0xFFD9774A), Color(0xFF1B4332)),
            if (d) listOf(Color(0xFF123322), Color(0xFF17402A), Color(0xFF1C4D32), Color(0xFF0E2A1C))
            else listOf(Color(0xFFDCEFD9), Color(0xFFB7DDB0), Color(0xFFC8E6C2), Color(0xFFF3E9C6))
        )
        "flat/sunset" -> recolour(
            c2(0xFFA63A22, 0xFFF6B07A), c2(0xFFFFFFFF, 0xFF0B2530), c2(0xFFF8DCCF, 0xFF4A2E2A), c2(0xFFC2462B, 0xFFF4A261), c2(0xFFFFFFFF, 0xFF0B2530),
            listOf(Color(0xFFE76F51), Color(0xFFF4A261), Color(0xFFE9C46A), Color(0xFF2A9D8F), Color(0xFF264653)),
            if (d) listOf(Color(0xFF3A1F24), Color(0xFF4A262A), Color(0xFF56302E), Color(0xFF2E181C))
            else listOf(Color(0xFFFBE3D6), Color(0xFFF6CDB6), Color(0xFFF9D9C4), Color(0xFFFFF1D0))
        )
        "bauhaus/earth" -> recolour(
            c2(0xFF7A3A1E, 0xFFE6B866), c2(0xFFFFFFFF, 0xFF14161F), c2(0xFFEED9C4, 0xFF3A2E26), c2(0xFFA8472A, 0xFFD9A441), c2(0xFFFFFFFF, 0xFF14161F),
            listOf(Color(0xFFB5532E), Color(0xFF3E5C4A), Color(0xFFD9A441), Color(0xFF2A2522), Color(0xFF6B8E5A), art[5]),
            listOf(Color(0xFFB5532E), Color(0xFF3E5C4A), Color(0xFFD9A441), blobs[3]), listOf(c2(0xFF3E5C4A, 0xFFB5532E))
        )
        "bauhaus/night" -> recolour(
            c2(0xFF1F3FBF, 0xFF9DB0FF), c2(0xFFFFFFFF, 0xFF14161F), c2(0xFFDCE2FA, 0xFF262E5A), c2(0xFF1F3FBF, 0xFF6F8BFF), c2(0xFFFFFFFF, 0xFF14161F),
            listOf(Color(0xFFFF6B35), Color(0xFF1F3FBF), Color(0xFFF2C14E), Color(0xFF14161F), Color(0xFF2E8B57), art[5]),
            listOf(Color(0xFFFF6B35), Color(0xFF1F3FBF), Color(0xFFF2C14E), blobs[3]), listOf(Color(0xFFFF6B35))
        )
        "cottage/lavender" -> recolour(
            c2(0xFF6B3E7A, 0xFFD9C2EE), c2(0xFFFFFBF2, 0xFF1A2117), c2(0xFFE9DFF2, 0xFF33293F), c2(0xFF6B4E8A, 0xFFC9B2E3), c2(0xFFFFFBF2, 0xFF1A2117),
            listOf(Color(0xFF8C6BB1), Color(0xFFC2577A), Color(0xFFD8A945), Color(0xFF5E7D4F), art[4]),
            listOf(Color(0xFF8C6BB1), Color(0xFFC2577A), Color(0xFFD8A945))
        )
        "cottage/berry" -> recolour(
            c2(0xFF8C2F4E, 0xFFF0A7BD), c2(0xFFFFFBF2, 0xFF1A2117), c2(0xFFF3D9E1, 0xFF402531), c2(0xFF8C2F4E, 0xFFE89AB0), c2(0xFFFFFBF2, 0xFF1A2117),
            listOf(Color(0xFF3F6B3A), Color(0xFFA8324F), Color(0xFFE0A43A), Color(0xFF6B2D5C), art[4]),
            listOf(Color(0xFF3F6B3A), Color(0xFFA8324F), Color(0xFFE0A43A))
        )
        "japandi/matcha" -> recolour(
            c2(0xFF4E6B45, 0xFFB5C9A3), c2(0xFFFBF8F2, 0xFF1E1D1B), c2(0xFFE1E8DA, 0xFF2F3A2A), c2(0xFF4E6B45, 0xFFA7BE93), c2(0xFFFBF8F2, 0xFF1E1D1B),
            listOf(Color(0xFF7E9A6E), Color(0xFFB07A57), Color(0xFFD9CDB8), art[3], art[4]),
            listOf(Color(0xFF7E9A6E), Color(0xFF7E9A6E), blobs[2])
        )
        "japandi/clay" -> recolour(
            c2(0xFF8C4A2E, 0xFFE6AE8C), c2(0xFFFBF8F2, 0xFF1E1D1B), c2(0xFFF0DDD0, 0xFF3E2E25), c2(0xFF9A5536, 0xFFE0A07A), c2(0xFFFBF8F2, 0xFF1E1D1B),
            listOf(Color(0xFF8A9A7B), Color(0xFFC0703F), Color(0xFFE3C9A8), art[3], art[4]),
            listOf(Color(0xFFC0703F), Color(0xFFC0703F), blobs[2])
        )
        "riso/orangeteal" -> recolour(
            c2(0xFF00726F, 0xFFFFB36B), c2(0xFFFFFFFF, 0xFF17152B), c2(0xFFFFE3D1, 0xFF3A2E2A), Color(0xFFFF7A3D), Color(0xFF232056),
            listOf(Color(0xFFFF7A3D), Color(0xFF00A6A0), Color(0xFFFFE800), Color(0xFF765BA7), Color(0xFF3255A4)),
            listOf(Color(0xFFFF7A3D), Color(0xFF00A6A0), Color(0xFFFFE800), blobs[3]), listOf(Color(0xFFFF7A3D), Color(0xFF00A6A0))
        )
        "riso/greenpurple" -> recolour(
            c2(0xFF5B3FA6, 0xFF9BEFC0), c2(0xFFFFFFFF, 0xFF17152B), c2(0xFFE3DAF5, 0xFF2E2852), Color(0xFF3DDC84), Color(0xFF232056),
            listOf(Color(0xFF3DDC84), Color(0xFF765BA7), Color(0xFFFFE800), Color(0xFFFF48B0), Color(0xFF3255A4)),
            listOf(Color(0xFF3DDC84), Color(0xFF765BA7), Color(0xFFFFE800), blobs[3]), listOf(Color(0xFF3DDC84), Color(0xFF765BA7))
        )
        "synth/outrun" -> recolour(
            c2(0xFF8E1066, 0xFF5CF2FF), c2(0xFFFFFFFF, 0xFF1A0B33), c2(0xFFFFDDF0, 0xFF3A1F5C), Color(0xFFFF5C8A), Color(0xFF1A0B33),
            listOf(Color(0xFFFF5C8A), Color(0xFFFF2E63), Color(0xFFB026FF), Color(0xFF08D9D6), Color(0xFF2B0F5C)),
            listOf(Color(0xFFFF2E63), Color(0xFF08D9D6), Color(0xFFFF5C8A), Color(0xFFB026FF)), null,
            listOf(Color(0xFFFF5C8A), Color(0xFFFFA3C2), Color(0xFF5CF2FF))
        )
        "synth/miami" -> recolour(
            c2(0xFF0F7A6B, 0xFF7FFFD4), c2(0xFFFFFFFF, 0xFF1A0B33), c2(0xFFD6F7EE, 0xFF1F3F4A), Color(0xFF7FFFD4), Color(0xFF1A0B33),
            listOf(Color(0xFFFFD1DC), Color(0xFFFF9DE2), Color(0xFFFF77FF), Color(0xFF7FFFD4), Color(0xFF9F7BFF)),
            listOf(Color(0xFFFF77FF), Color(0xFF7FFFD4), Color(0xFFFF9DE2), Color(0xFF9F7BFF)), null,
            listOf(Color(0xFF7FFFD4), Color(0xFF9FE7FF), Color(0xFFFF9DE2))
        )
        "anthro/mint" -> recolour(
            c2(0xFF0B6B58, 0xFF7FE8C4), c2(0xFFFFFAF0, 0xFF101A40), c2(0xFFD4EFE6, 0xFF1E4A4A), c2(0xFF0B6B58, 0xFF5EE0B5), c2(0xFFFFFAF0, 0xFF101A40),
            listOf(Color(0xFFF2453D), Color(0xFF0E7C66), Color(0xFF5EE0B5), Color(0xFFFFC53D), Color(0xFF8E6CD8), art[5], art[6]),
            listOf(Color(0xFF5EE0B5), blobs[1], Color(0xFFFFC53D), Color(0xFF0E7C66))
        )
        "anthro/tangerine" -> recolour(
            c2(0xFFB23C0A, 0xFFFFB27A), c2(0xFFFFFAF0, 0xFF101A40), c2(0xFFFFE2CC, 0xFF4A2E1E), c2(0xFFC2410C, 0xFFFF9F45), c2(0xFFFFFAF0, 0xFF101A40),
            listOf(Color(0xFF1D3FA8), Color(0xFFC2410C), Color(0xFFFF9F45), Color(0xFFFFE066), Color(0xFF8E6CD8), art[5], art[6]),
            listOf(Color(0xFFFF9F45), blobs[1], Color(0xFFFFE066), Color(0xFFC2410C))
        )
        "swiss/klein" -> recolour(
            c2(0xFF1F3FDB, 0xFF8FA3FF), c2(0xFFFFFFFF, 0xFF0E0E0E), c2(0xFFDCE2FA, 0xFF1E2550), Color(0xFF1F3FDB), Color(0xFFFFFFFF),
            listOf(Color(0xFF1F3FDB), art[1], art[2], art[3]), listOf(blobs[0], Color(0xFF1F3FDB))
        )
        "swiss/signal" -> recolour(
            c2(0xFF7A6200, 0xFFFFD400), c2(0xFFFFFFFF, 0xFF0E0E0E), c2(0xFFFFF3B8, 0xFF3A3412), Color(0xFFFFD400), Color(0xFF111111),
            listOf(Color(0xFFFFD400), art[1], art[2], art[3]), listOf(blobs[0], Color(0xFFFFD400))
        )
        else -> this
    }
}
