package com.wishlist.app

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Themes (App settings > Theme, and the Themer).
 *
 * The standard ("classic") themes are the original look: Light, Dark and AMOLED black (Repo.theme), indigo,
 * with Google Sans. Their look is "classic" below and must stay exactly as it was.
 *
 * Next to them are four designs, each a complete style of its own with its own colour sets (one of them dark), fonts,
 * icons, buttons, switches, dialogs, top bar, side menu, labels and wish cards. The font from App settings is not used:
 *  - material: Material 3 Expressive (Android 16): tonal colour blocks, big round shapes, Google Sans, rounded icons.
 *  - organic:  warm paper, pebble shapes, blobs, Fraunces + Nunito, thin outlined icons (docs/designs/organic).
 *  - neo:      neobrutalism: thick ink lines, hard offset shadows, bright blocks, dot grid, Lexend, sharp icons.
 *  - clay:     claymorphism: puffy cards with soft coloured shadows and a light top edge, pastel glows,
 *              Plus Jakarta Sans + Inter, rounded icons.
 */

/**
 * A colour set: the main colour (buttons, highlights), two accents, the paper (background) and the ink (text).
 * A dark set gives the dark background as paper and light text as ink.
 */
class ColourSet(
    val key: String, val name: String, val main: Long, val a2: Long, val a3: Long, val paper: Long, val ink: Long,
    val dark: Boolean = false
)

class Design(val key: String, val name: String, val about: String, val sets: List<ColourSet>)

val DESIGNS: List<Design> = listOf(
    Design(
        "material", "Material 3", "Android 16’s expressive look: tonal colour blocks, big round shapes, bold numbers.",
        listOf(
            ColourSet("lilac", "Lilac", 0xFF7B52C9, 0xFFF07B6B, 0xFFE6A6F2, 0xFFFCF3FD, 0xFF1F1424),
            ColourSet("coral", "Coral", 0xFFB8473A, 0xFFFFB59E, 0xFF8C6BD6, 0xFFFFF5F2, 0xFF2B1512),
            ColourSet("ocean", "Ocean", 0xFF1F6FB2, 0xFF8FD3F0, 0xFFFFC27A, 0xFFF3F8FD, 0xFF0E1C2B),
            ColourSet("forest", "Forest", 0xFF2E7D4F, 0xFFB7E4A8, 0xFFF2C14E, 0xFFF3F9F3, 0xFF10241A),
            ColourSet("sunshine", "Sunshine", 0xFF8A5A00, 0xFFFFD66B, 0xFFF07B6B, 0xFFFFF8EC, 0xFF261A00),
            ColourSet("plum", "Plum night", 0xFFD7B4FF, 0xFFFF9C8F, 0xFFF2B8F5, 0xFF1A111D, 0xFFF3E8F5, dark = true)
        )
    ),
    Design(
        "organic", "Organic", "Warm and natural: soft paper, pebble shapes and a friendly serif.",
        listOf(
            ColourSet("sage", "Sage", 0xFF4F6B4A, 0xFFC9A574, 0xFF9C88B5, 0xFFF3EEE4, 0xFF2A2B24),
            ColourSet("terracotta", "Terracotta", 0xFFA0583C, 0xFF7C8B5A, 0xFFD9A55B, 0xFFF6EEE6, 0xFF2E241F),
            ColourSet("lavender", "Lavender", 0xFF6B5B8E, 0xFF8FA383, 0xFFD3A67A, 0xFFF2EFF2, 0xFF29262F),
            ColourSet("seaglass", "Sea glass", 0xFF3D6E73, 0xFFC6A27A, 0xFF8DAE9B, 0xFFEEF1EC, 0xFF1F2A2B),
            ColourSet("berry", "Berry", 0xFF8A4560, 0xFFB98A62, 0xFF7E9171, 0xFFF6EEEC, 0xFF2E2226),
            ColourSet("night", "Forest night", 0xFFA9C59E, 0xFFD7B98A, 0xFFB9A6D3, 0xFF191A15, 0xFFECE7DB, dark = true)
        )
    ),
    Design(
        "neo", "Neobrutalism", "Bold and playful: thick lines, hard shadows and bright blocks of colour.",
        listOf(
            ColourSet("lemon", "Lemon and lilac", 0xFFF2F98E, 0xFFC4F7C4, 0xFFF0D0F8, 0xFFFFFFFF, 0xFF14183B),
            ColourSet("bubblegum", "Bubblegum", 0xFFFF9BD2, 0xFF9BD7FF, 0xFFFFE57A, 0xFFFFF7FB, 0xFF111111),
            ColourSet("tangerine", "Tangerine", 0xFFFFA552, 0xFF7FE3CF, 0xFFFFE9A8, 0xFFFFF6E9, 0xFF1A1A1A),
            ColourSet("electric", "Electric", 0xFF7AA2FF, 0xFFD4FF5C, 0xFFFFB8B8, 0xFFF4F6FF, 0xFF0B0B1A),
            ColourSet("mint", "Mint and coral", 0xFF7EEDC0, 0xFFFF9C8A, 0xFFFFF0A6, 0xFFF2FFF8, 0xFF102018),
            ColourSet("midnight", "Midnight", 0xFFF2F98E, 0xFF9BD7FF, 0xFFFF9BD2, 0xFF141416, 0xFFF4F4EE, dark = true)
        )
    ),
    Design(
        "clay", "Claymorphism", "Soft and puffy: inflated clay shapes with inner glows, soft shadows and pastels.",
        listOf(
            ColourSet("lavender", "Lavender", 0xFF7C6AED, 0xFFA7EFC2, 0xFFFFC4AE, 0xFFF4F1FF, 0xFF1E1B4B),
            ColourSet("mint", "Mint", 0xFF2FA37A, 0xFFFFD39E, 0xFFB9C6FF, 0xFFEFFAF5, 0xFF12332A),
            ColourSet("peach", "Peach", 0xFFE0663A, 0xFFB7A7F7, 0xFF9FE3D0, 0xFFFFF4EE, 0xFF3A1E14),
            ColourSet("sky", "Sky", 0xFF3D8BF2, 0xFFFFD27A, 0xFFF7A8C8, 0xFFEFF5FF, 0xFF12264A),
            ColourSet("bubblegum", "Bubblegum", 0xFFE35D9C, 0xFF9ED8F5, 0xFFFFE08A, 0xFFFFF0F6, 0xFF3D1229),
            ColourSet("dusk", "Dusk", 0xFF9D8CFF, 0xFF7FD6A8, 0xFFFFB199, 0xFF1B1830, 0xFFECE9FF, dark = true)
        )
    )
)

/** The design with this key, or null for the classic themes. */
fun designFor(key: String): Design? = DESIGNS.firstOrNull { it.key == key }

fun colourSetFor(d: Design, key: String): ColourSet = d.sets.firstOrNull { it.key == key } ?: d.sets[0]

/** Everything the screens need to draw the chosen theme. Get it with LocalLook.current. */
class Look(
    /** "classic", "material", "organic", "neo" or "clay". */
    val design: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    /** Card colours, used in turn (neobrutalism has several; the others one). */
    val fills: List<Color>,
    /** Soft colours behind pictures, used in turn. */
    val tints: List<Color>,
    /** Thin lines (organic) and the thick lines of neobrutalism. */
    val line: Color,
    /** Hard shadow colours of neobrutalism, used in turn; the soft shadow colour of clay is the first. */
    val shadows: List<Color>,
    /** The shapes, glows or dots drawn behind the list. */
    val blobs: List<Color>,
    /** The font of names, headings and prices, or null for the font chosen in App settings (classic themes). */
    val display: FontFamily?,
    /** A cheaper and a dearer price. */
    val drop: Color,
    val rise: Color,
    /**
     * The colour of filled blocks (buttons, the chosen chip, Add wish, the + buttons) and what goes on them. Usually the
     * main colour; in Neobrutalism's light sets the main colour is too light for text, so there the scheme's primary
     * (used for text, titles, radio buttons, links) is the dark ink and the bright colour is only used here (2.71).
     */
    val accent: Color = scheme.primary,
    val onAccent: Color = scheme.onPrimary
) {
    val classic: Boolean get() = design == "classic"
}

val LocalLook = staticCompositionLocalOf { lookFor("classic", "", "light") }

private fun c(v: Long) = Color(v)
private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
/** Dark ink on light colours, white on dark ones. */
private fun on(bg: Color, ink: Color) = if (bg.luminance() > 0.4f) ink else Color.White
private fun pick(id: Long, n: Int) = Math.floorMod(id, n.toLong()).toInt()

private val fraunces by lazy { google("Fraunces") }
private val nunito by lazy { google("Nunito") }
private val lexend by lazy { google("Lexend") }
private val jakarta by lazy { google("Plus Jakarta Sans") }
private val inter by lazy { google("Inter") }

/** All the colours of a scheme from a few, plus the surface container steps that menus and dialogs use. */
private fun scheme(
    dark: Boolean, primary: Color, onPrimary: Color, container: Color, onContainer: Color, accent: Color,
    bg: Color, surface: Color, text: Color, muted: Color, variant: Color, outline: Color, outlineVariant: Color
): ColorScheme {
    val step = { t: Float -> mix(bg, text, t) }
    return if (!dark) lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = accent, onTertiary = on(accent, text), tertiaryContainer = accent, onTertiaryContainer = on(accent, text),
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = variant, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
        surfaceTint = primary, inverseSurface = text, inverseOnSurface = bg, inversePrimary = container,
        surfaceBright = surface, surfaceDim = step(0.08f),
        surfaceContainerLowest = surface, surfaceContainerLow = mix(bg, surface, 0.5f), surfaceContainer = step(0.03f),
        surfaceContainerHigh = surface, surfaceContainerHighest = step(0.08f)
    ) else darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = accent, onTertiary = on(accent, bg), tertiaryContainer = accent, onTertiaryContainer = on(accent, bg),
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = variant, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
        surfaceTint = primary, inverseSurface = text, inverseOnSurface = bg, inversePrimary = container,
        surfaceBright = step(0.16f), surfaceDim = bg,
        surfaceContainerLowest = mix(bg, Color.Black, 0.5f), surfaceContainerLow = step(0.04f), surfaceContainer = step(0.06f),
        surfaceContainerHigh = surface, surfaceContainerHighest = step(0.12f)
    )
}

/**
 * The look for a theme. [style]: "classic" or a design key. [setKey]: the design's colour set.
 * [classicMode]: for the classic themes, "light", "dark" or "amoled".
 */
fun lookFor(style: String, setKey: String, classicMode: String): Look {
    val d = designFor(style) ?: return classicLook(classicMode, setKey)
    val s = colourSetFor(d, setKey)
    val dark = s.dark
    val main = c(s.main); val a2 = c(s.a2); val a3 = c(s.a3); val paper = c(s.paper); val ink = c(s.ink)
    val white = Color.White; val black = Color.Black
    val greenL = c(0xFF1E7B45); val redL = c(0xFFB3261E)
    val greenD = c(0xFF6FD39A); val redD = c(0xFFFF8A80)
    val drop = if (dark) greenD else greenL
    val rise = if (dark) redD else redL
    return when (d.key) {
        "organic" -> {
            val bg = paper
            val surface = if (dark) mix(paper, main, 0.06f) else mix(paper, c(0xFFFFFDF8), 0.75f)
            val text = ink
            val muted = mix(ink, paper, if (dark) 0.3f else 0.32f)
            val line = if (dark) mix(paper, main, 0.18f) else mix(paper, ink, 0.12f)
            val onPrimary = if (dark) mix(main, black, 0.75f) else on(main, ink)
            val container = if (dark) mix(main, paper, 0.72f) else mix(main, white, 0.76f)
            val onContainer = if (dark) mix(main, white, 0.6f) else mix(main, ink, 0.6f)
            val tints = listOf(main, a2, a3).map { if (dark) mix(it, paper, 0.82f) else mix(it, white, 0.78f) }
            Look(
                "organic", dark,
                scheme(dark, main, onPrimary, container, onContainer, a2, bg, surface, text, muted, surface, mix(line, text, 0.35f), line),
                listOf(surface), tints, line, listOf(line),
                listOf(mix(main, paper, if (dark) 0.85f else 0.84f), mix(a2, paper, if (dark) 0.88f else 0.78f)),
                fraunces, drop, rise
            )
        }
        "neo" -> {
            val bg = paper
            val surface = if (dark) c(0xFF1F1F23) else white
            val muted = if (dark) mix(ink, bg, 0.3f) else mix(ink, white, 0.28f)
            val accents = listOf(main, a2, a3)
            // The neobrutalist colours are all bright, so the text on them is always the dark ink.
            val inkOnBright = if (dark) c(0xFF111111) else ink
            Look(
                "neo", dark,
                // Light sets: text and small controls in ink (the bright colours are unreadable as text on white);
                // the bright main colour fills the blocks (accent).
                scheme(dark, if (dark) main else ink, if (dark) inkOnBright else white, a2, inkOnBright, a3, bg, surface, ink, muted, surface, ink, mix(ink, bg, 0.7f)),
                // Midnight too has bright cards (with dark text, see cardInk) on the dark background, and light shadows.
                if (dark) accents else listOf(main, white, a2, a3),
                accents.map { if (dark) mix(it, surface, 0.15f) else mix(it, white, 0.35f) },
                ink,
                listOf(ink),
                listOf(mix(ink, bg, if (dark) 0.86f else 0.9f)),
                lexend,
                // Prices are written on the bright cards, so they are dark green and red in Midnight too.
                c(0xFF00873C), c(0xFFD62828),
                accent = main, onAccent = inkOnBright
            )
        }
        "clay" -> {
            // A slightly deeper pastel background, so the pale clay pillows stand out from it.
            val bg = if (dark) paper else mix(paper, main, 0.06f)
            val surface = if (dark) mix(paper, main, 0.12f) else mix(paper, white, 0.78f)
            val muted = mix(ink, paper, if (dark) 0.32f else 0.38f)
            val onPrimary = if (dark) mix(main, black, 0.75f) else on(main, ink)
            val container = if (dark) mix(main, paper, 0.62f) else mix(main, white, 0.82f)
            val onContainer = if (dark) mix(main, white, 0.7f) else mix(main, ink, 0.5f)
            val tints = listOf(a2, main, a3).map { if (dark) mix(it, paper, 0.72f) else mix(it, white, if (it == main) 0.8f else 0.55f) }
            Look(
                "clay", dark,
                scheme(dark, main, onPrimary, container, onContainer, a2, bg, surface, ink, muted, surface, mix(ink, bg, 0.6f), mix(ink, bg, 0.85f)),
                listOf(surface), tints, mix(ink, bg, 0.85f),
                listOf(if (dark) black.copy(alpha = 0.65f) else mix(main, ink, 0.35f).copy(alpha = 0.35f)),
                listOf(mix(main, if (dark) paper else white, if (dark) 0.7f else 0.72f), mix(a2, if (dark) paper else white, if (dark) 0.75f else 0.45f)),
                jakarta, drop, rise
            )
        }
        else -> {
            // Material 3 Expressive: tonal surfaces made from the main colour, a strong container colour.
            val bg = paper
            val surface = if (dark) mix(paper, main, 0.1f) else mix(paper, white, 0.6f)
            val card = if (dark) mix(paper, main, 0.14f) else mix(paper, main, 0.09f)
            val muted = mix(ink, paper, if (dark) 0.3f else 0.35f)
            val onPrimary = if (dark) mix(main, black, 0.75f) else on(main, ink)
            val container = if (dark) mix(main, paper, 0.55f) else mix(main, white, 0.7f)
            val onContainer = if (dark) mix(main, white, 0.75f) else mix(main, ink, 0.65f)
            val tints = listOf(a2, main, a3).map { if (dark) mix(it, paper, 0.7f) else mix(it, white, if (it == main) 0.78f else 0.62f) }
            Look(
                "material", dark,
                scheme(dark, main, onPrimary, container, onContainer, a2, bg, surface, ink, muted, card, mix(ink, bg, 0.5f), mix(ink, bg, 0.82f)),
                listOf(card), tints, mix(ink, bg, 0.82f), listOf(mix(ink, bg, 0.5f)), emptyList(),
                GoogleSans, drop, rise
            )
        }
    }
}

/** The classic themes, exactly as before: indigo (PALETTES[0]) in light, dark or AMOLED black. */
/**
 * The standard themes: Light, Dark or AMOLED black with one of the colour sets in PALETTES (Indigo is the original;
 * the sets are picked in the Themer since 2.65). Each set has tones made for light and for dark; AMOLED black uses the
 * dark tones on pure black.
 */
private fun classicLook(mode: String, palette: String = "indigo"): Look {
    val p = PALETTES.firstOrNull { it.key == palette } ?: PALETTES[0]
    var scheme = if (mode == "light") p.light else p.dark
    // AMOLED black: the dark colours on pure black, with the cards just off black.
    if (mode == "amoled") scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF0E0E11))
    // Behind photos: the light container colour in Light; in Dark and AMOLED a quiet mix of the card colour with a
    // little of it (the full container colour was too bright there).
    val mat = when (mode) {
        "light" -> scheme.primaryContainer
        "amoled" -> lerp(Color(0xFF0E0E11), scheme.primaryContainer, 0.28f)
        else -> lerp(scheme.surfaceVariant, scheme.primaryContainer, 0.38f)
    }
    return Look(
        "classic", mode != "light", scheme,
        listOf(scheme.surfaceVariant), listOf(mat), scheme.outline, listOf(scheme.outline), emptyList(),
        null, Color(0xFF0B7A3E), Color(0xFFBA1A1A)
    )
}

private fun TextStyle.styled(f: FontFamily, w: FontWeight, extraSpacing: Float = 0f) =
    copy(fontFamily = f, fontWeight = w, letterSpacing = if (extraSpacing == 0f) letterSpacing else (letterSpacing.value + extraSpacing).sp)

/** The type of a design: a heading font (display, headlines, big titles, prices) and a text font. */
private fun designType(
    head: FontFamily, body: FontFamily, headW: FontWeight, titleW: FontWeight, smallTitleFont: FontFamily,
    smallTitleW: FontWeight, bodyW: FontWeight, labelW: FontWeight, labelSpacing: Float = 0f
): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.styled(head, headW), displayMedium = d.displayMedium.styled(head, headW),
        displaySmall = d.displaySmall.styled(head, headW), headlineLarge = d.headlineLarge.styled(head, headW),
        headlineMedium = d.headlineMedium.styled(head, headW), headlineSmall = d.headlineSmall.styled(head, headW),
        titleLarge = d.titleLarge.styled(head, titleW),
        titleMedium = d.titleMedium.styled(smallTitleFont, smallTitleW), titleSmall = d.titleSmall.styled(smallTitleFont, smallTitleW),
        bodyLarge = d.bodyLarge.styled(body, bodyW), bodyMedium = d.bodyMedium.styled(body, bodyW), bodySmall = d.bodySmall.styled(body, bodyW),
        labelLarge = d.labelLarge.styled(body, labelW, labelSpacing), labelMedium = d.labelMedium.styled(body, labelW, labelSpacing),
        labelSmall = d.labelSmall.styled(body, labelW, labelSpacing)
    )
}

/** The fonts of a design. The classic themes use [classic] (the font chosen in App settings). */
fun typographyFor(design: String, classic: Typography): Typography = when (design) {
    "material" -> designType(GoogleSans, GoogleSans, FontWeight.Medium, FontWeight.Medium, GoogleSans, FontWeight.SemiBold, FontWeight.Normal, FontWeight.Medium)
    "organic" -> designType(fraunces, nunito, FontWeight.SemiBold, FontWeight.SemiBold, nunito, FontWeight.Bold, FontWeight.Normal, FontWeight.SemiBold, 0.2f)
    "neo" -> designType(lexend, lexend, FontWeight.ExtraBold, FontWeight.Bold, lexend, FontWeight.SemiBold, FontWeight.Normal, FontWeight.SemiBold)
    "clay" -> designType(jakarta, inter, FontWeight.ExtraBold, FontWeight.ExtraBold, jakarta, FontWeight.Bold, FontWeight.Normal, FontWeight.Medium)
    else -> classic
}

/** The weight of screen titles in the top bar (classic: Medium, as before). */
val Look.titleWeight: FontWeight
    get() = when (design) {
        "neo" -> FontWeight.Bold
        "clay" -> FontWeight.ExtraBold
        "organic" -> FontWeight.SemiBold
        else -> FontWeight.Medium
    }

/** The weight of prices. */
val Look.priceWeight: FontWeight
    get() = when (design) {
        "material", "neo" -> FontWeight.Bold
        "clay" -> FontWeight.ExtraBold
        else -> FontWeight.SemiBold
    }

/** Icon style: "filled" (classic), "rounded", "outlined" or "sharp". */
val Look.iconStyle: String
    get() = when (design) {
        "material", "clay" -> "rounded"
        "organic" -> "outlined"
        "neo" -> "sharp"
        else -> "filled"
    }

/** The Material shapes (dialogs, menus, text fields, snackbars) of each design. */
fun shapesFor(design: String): Shapes = when (design) {
    "material" -> Shapes(
        extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
    )
    "organic" -> Shapes(
        extraSmall = RoundedCornerShape(14.dp, 14.dp, 14.dp, 6.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp, 30.dp, 30.dp, 12.dp)
    )
    "neo" -> Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(14.dp)
    )
    "clay" -> Shapes(
        extraSmall = RoundedCornerShape(16.dp), small = RoundedCornerShape(18.dp), medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)
    )
    else -> Shapes()
}

/** The shape of a wish card. Organic cards are pebbles: one corner less round, in turn left or right. */
fun Look.cardShape(id: Long, small: Boolean): Shape = when (design) {
    "organic" -> {
        val r = if (small) 24.dp else 30.dp
        val k = if (small) 10.dp else 12.dp
        if (pick(id, 2) == 0) RoundedCornerShape(r, r, r, k) else RoundedCornerShape(r, k, r, r)
    }
    "neo" -> RoundedCornerShape(if (small) 12.dp else 14.dp)
    "clay" -> RoundedCornerShape(if (small) 26.dp else 32.dp)
    "material" -> RoundedCornerShape(if (small) 24.dp else 28.dp)
    else -> RoundedCornerShape(if (small) 16.dp else 20.dp)
}

/** Space around the picture inside a card: pictures sit on a mat, except in classic and neobrutalism. */
fun Look.inset(small: Boolean): Dp = when (design) {
    "organic", "clay" -> if (small) 8.dp else 10.dp
    "material" -> if (small) 6.dp else 8.dp
    else -> 0.dp
}

fun Look.pictureShape(id: Long, small: Boolean): Shape = when (design) {
    "organic" -> {
        val r = if (small) 18.dp else 24.dp
        val k = if (small) 6.dp else 8.dp
        if (pick(id, 2) == 0) RoundedCornerShape(r, r, r, k) else RoundedCornerShape(r, k, r, r)
    }
    "clay" -> RoundedCornerShape(if (small) 20.dp else 24.dp)
    "material" -> RoundedCornerShape(if (small) 18.dp else 22.dp)
    else -> RectangleShape
}

/**
 * Behind a picture: a soft colour of the theme, with the photo as a small card on it (PhotoCard), or the emoji when
 * there is no photo.
 */
fun Look.pictureBg(id: Long, hasImage: Boolean): Color = when {
    // Photos sit as a small card on a soft colour of the theme (2.63); neobrutalism Midnight keeps them on dark.
    hasImage && design == "neo" && dark -> scheme.background
    else -> tints[pick(id, tints.size)]
}

/** Neobrutalism draws a thick line under the picture. */
val Look.pictureLine: Dp get() = if (design == "neo") 2.dp else 0.dp

fun Look.cardFill(id: Long): Color = fills[pick(id, fills.size)]

/** The text colour on a wish card: dark ink on the bright cards of neobrutalism Midnight, the theme's text otherwise. */
fun Look.cardInk(id: Long): Color = if (design == "neo" && dark) Color(0xFF111111) else scheme.onSurface

/** The quieter text (shop, description, icons) on a wish card. */
fun Look.cardMuted(id: Long): Color = if (design == "neo" && dark) Color(0xFF3A3A40) else scheme.onSurfaceVariant

/** Small labels on a card (price change, "Got it"). */
fun Look.badgeShape(small: Boolean): Shape = when (design) {
    "organic" -> RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
    "neo" -> RoundedCornerShape(6.dp)
    "clay", "material" -> CircleShape
    else -> RoundedCornerShape(if (small) 6.dp else 8.dp)
}

/** Buttons such as the category button and the view switch. */
val Look.chipShape: Shape
    get() = when (design) {
        "organic", "clay", "material" -> CircleShape
        "neo" -> RoundedCornerShape(10.dp)
        else -> RoundedCornerShape(12.dp)
    }

/** The Add wish button. */
val Look.fabShape: Shape
    get() = when (design) {
        "organic" -> RoundedCornerShape(22.dp, 30.dp, 26.dp, 30.dp)
        "neo" -> RoundedCornerShape(12.dp)
        "clay" -> RoundedCornerShape(28.dp)
        "material" -> RoundedCornerShape(20.dp)
        else -> RoundedCornerShape(16.dp)
    }

/** The shape of ordinary buttons. */
val Look.buttonShape: Shape
    get() = when (design) {
        "organic" -> RoundedCornerShape(18.dp, 22.dp, 20.dp, 22.dp)
        "neo" -> RoundedCornerShape(10.dp)
        else -> CircleShape
    }

/** A hard shadow: the shape again, moved down and to the right, in one flat colour. */
fun Modifier.hardShadow(color: Color, shape: Shape, offset: Dp = 4.dp): Modifier = drawBehind {
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { drawOutline(outline, color) }
}

/** The soft light edge at the top of a clay shape. */
fun Modifier.clayEdge(look: Look, shape: Shape, width: Dp = 1.5.dp): Modifier = border(
    width,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (look.dark) 0.16f else 0.95f), Color.Transparent)),
    shape
)

/** The frame of a wish card in the chosen theme: its colour, lines, shadow and shape. */
fun Modifier.lookCard(look: Look, id: Long, shape: Shape): Modifier = when (look.design) {
    "neo" -> hardShadow(look.shadows[pick(id, look.shadows.size)], shape, 5.dp)
        .clip(shape).background(look.cardFill(id)).border(2.dp, look.line, shape)
    "organic" -> clip(shape).background(look.cardFill(id)).border(1.dp, look.line, shape)
    "clay" -> clay(look, shape, look.cardFill(id), 12.dp)
    else -> clip(shape).background(look.cardFill(id))
}

/** The mat a picture sits on inside a card. Clay presses it in; the others simply fill it. */
fun Modifier.lookMat(look: Look, shape: Shape, fill: Color): Modifier =
    if (look.design == "clay") clay(look, shape, fill, 8.dp, pressed = true) else clip(shape).background(fill)

/** Android 9 and newer can blur the clay shadows; Android 8 gets the flatter clay of 2.52. */
private val clayBlur = Build.VERSION.SDK_INT >= 28

/**
 * Claymorphism: a puffy clay pillow. A soft, coloured shadow below it, a light glow inside its top-left edge and a
 * soft shade inside its bottom-right edge, on a gently shaded fill. [depth] sets how puffy it is. [pressed]: pressed
 * in instead (picture mats): the shade top-left, the light bottom-right and no shadow below. The content is clipped
 * to [shape]. Material components on top should have a transparent container.
 */
fun Modifier.clay(look: Look, shape: Shape, fill: Color, depth: Dp = 10.dp, pressed: Boolean = false): Modifier {
    if (!clayBlur) {
        val glow = look.shadows[0]
        return (if (pressed) this else shadow(depth, shape, clip = false, ambientColor = glow, spotColor = glow))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(fill, Color.White, if (look.dark) 0.06f else 0.5f), fill)))
            .then(if (pressed) Modifier else Modifier.clayEdge(look, shape))
    }
    val light = Color.White.copy(alpha = if (look.dark) 0.14f else 0.85f)
    val shade = if (look.dark) Color.Black.copy(alpha = 0.6f) else look.shadows[0]
    val drop = look.shadows[0]
    return drawWithCache {
        val d = depth.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val shapePath = Path().apply { addOutline(outline) }.asAndroidPath()
        fun paint(col: Color, blur: Float) = android.graphics.Paint().apply {
            isAntiAlias = true
            color = col.toArgb()
            maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        // Everything outside the shape moved by (dx, dy): drawn blurred inside the shape it becomes an inner shadow
        // along the opposite edges.
        fun frame(dx: Float, dy: Float) = android.graphics.Path().apply {
            val m = d * 4
            addRect(-m, -m, size.width + m, size.height + m, android.graphics.Path.Direction.CW)
            addPath(shapePath, dx, dy)
            fillType = android.graphics.Path.FillType.EVEN_ODD
        }
        val o = d * 0.5f
        val lightFrame = if (pressed) frame(-o, -o) else frame(o, o)
        val shadeFrame = if (pressed) frame(o, o) else frame(-o, -o)
        val lightPaint = paint(light, d * 0.8f)
        val shadePaint = paint(shade, d * 0.9f)
        val dropPaint = paint(drop, d * 1.4f)
        val dropPath = android.graphics.Path(shapePath).apply { offset(d * 0.3f, d * 0.75f) }
        val fillBrush = if (pressed) SolidColor(lerp(fill, Color.Black, if (look.dark) 0.08f else 0.03f))
        else Brush.linearGradient(
            listOf(lerp(fill, Color.White, if (look.dark) 0.05f else 0.4f), fill, lerp(fill, Color.Black, if (look.dark) 0.08f else 0.03f)),
            Offset.Zero, Offset(size.width, size.height)
        )
        onDrawWithContent {
            val canvas = drawContext.canvas.nativeCanvas
            if (!pressed) canvas.drawPath(dropPath, dropPaint)
            canvas.save()
            canvas.clipPath(shapePath)
            drawRect(fillBrush)
            canvas.drawPath(lightFrame, lightPaint)
            canvas.drawPath(shadeFrame, shadePaint)
            canvas.restore()
            drawContent()
        }
    }.clip(shape)
}

/** The frame of a small button (category, view switch). [on] = chosen. */
fun Modifier.lookChip(look: Look, on: Boolean = false): Modifier {
    val cs = look.scheme
    val shape = look.chipShape
    return when (look.design) {
        "neo" -> (if (on) hardShadow(look.line, shape, 3.dp) else this)
            .clip(shape).background(if (on) look.accent else cs.surface).border(2.dp, look.line, shape)
        "organic" -> clip(shape).background(if (on) look.accent else Color.Transparent).border(1.dp, look.line, shape)
        "clay" -> clay(look, shape, if (on) look.accent else look.cardFill(0), if (on) 6.dp else 5.dp)
        "material" -> clip(shape).background(if (on) look.accent else cs.surfaceVariant)
        else -> clip(shape).background(if (on) cs.primaryContainer else Color.Transparent)
    }
}

/** The colour of the text and icons on a chip made with [lookChip]. */
fun Look.chipContent(on: Boolean): Color = when {
    !on -> if (design == "classic") scheme.onSurfaceVariant else scheme.onSurface
    design == "classic" -> scheme.onPrimaryContainer
    else -> onAccent
}

/** What is drawn behind the list: organic blobs, clay glows, a neobrutalist dot grid. Classic and Material stay plain. */
fun Modifier.lookBackdrop(look: Look): Modifier = when (look.design) {
    "organic" -> drawBehind {
        blob(Offset(size.width * 0.86f, 34.dp.toPx()), 110.dp.toPx(), look.blobs[0], 0.4f)
        blob(Offset(size.width * 0.08f, 190.dp.toPx()), 80.dp.toPx(), look.blobs[1], 2.1f)
    }
    "clay" -> drawBehind {
        // A soft pastel gradient from top to bottom, with two glows.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, look.blobs[0].copy(alpha = 0.7f))))
        fun glow(center: Offset, r: Float, col: Color) =
            drawCircle(Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center, r), r, center)
        glow(Offset(size.width * 0.92f, 20.dp.toPx()), 150.dp.toPx(), look.blobs[0])
        glow(Offset(size.width * 0.05f, size.height * 0.55f), 130.dp.toPx(), look.blobs[1])
    }
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
    else -> this
}

/** A soft, irregular pebble: a smooth curve through eight points at slightly different distances from the centre. */
private fun DrawScope.blob(center: Offset, r: Float, color: Color, seed: Float) {
    val n = 8
    val pts = (0 until n).map { i ->
        val a = (i * 2 * Math.PI / n).toFloat()
        val k = 1f + 0.16f * sin(seed + i * 1.7f) + 0.08f * cos(seed * 2 + i * 2.9f)
        Offset(center.x + cos(a) * r * k, center.y + sin(a) * r * k * 0.86f)
    }
    fun mid(p: Offset, q: Offset) = Offset((p.x + q.x) / 2, (p.y + q.y) / 2)
    val path = Path()
    val start = mid(pts[n - 1], pts[0])
    path.moveTo(start.x, start.y)
    for (i in 0 until n) {
        val p = pts[i]
        val m = mid(p, pts[(i + 1) % n])
        path.quadraticBezierTo(p.x, p.y, m.x, m.y)
    }
    path.close()
    drawPath(path, color)
}
