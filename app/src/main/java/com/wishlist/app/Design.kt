package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Theme designs (App settings > Theme > Themer). A design is a whole look: shapes, lines, shadows, the font of names
 * and prices, and what is drawn behind the list. Each design has its own colour sets. The light, dark and AMOLED black
 * modes (Repo.theme) work with every design and every colour set.
 *
 *  - material: Google's Material 3 (the original look of the app; Indigo is the default).
 *  - organic:  warm paper, pebble-shaped cards, soft blobs, Fraunces for names and prices (docs/designs/organic).
 *  - neo:      neobrutalism: thick black lines, hard offset shadows, bright blocks of colour, Space Grotesk.
 *  - clay:     claymorphism: puffy rounded cards with soft coloured shadows and a light edge, pastels, Plus Jakarta Sans.
 */

/** A colour set: the main colour (buttons, highlights), two accents, the paper (light background) and the ink (text). */
class ColourSet(val key: String, val name: String, val main: Long, val a2: Long, val a3: Long, val paper: Long, val ink: Long)

class Design(val key: String, val name: String, val about: String, val sets: List<ColourSet>)

val DESIGNS: List<Design> = listOf(
    Design(
        "material", "Material 3", "Google’s Android look: calm, rounded and tonal.",
        listOf(
            // The Material sets use the tonal schemes in PALETTES (Theme.kt); main/a2/a3 are for the swatches and tints.
            ColourSet("indigo", "Indigo", 0xFF4355B9, 0xFF5C5D72, 0xFF78536B, 0xFFFBF8FF, 0xFF1B1B21),
            ColourSet("lavender", "Lavender", 0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFFFEF7FF, 0xFF1D1B20),
            ColourSet("rose", "Rose", 0xFF8C4A5D, 0xFF74565C, 0xFF7B5733, 0xFFFFF8F8, 0xFF22191B),
            ColourSet("ocean", "Ocean", 0xFF006A6A, 0xFF4A6363, 0xFF4B607C, 0xFFF4FBFA, 0xFF161D1D),
            ColourSet("sage", "Sage", 0xFF4A6741, 0xFF54634D, 0xFF386568, 0xFFF8FAF0, 0xFF1A1C18),
            ColourSet("amber", "Amber", 0xFF7C5800, 0xFF6C5D3F, 0xFF4A6547, 0xFFFFF8F2, 0xFF1F1B13)
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
            ColourSet("walnut", "Walnut", 0xFF6F5236, 0xFFA7B08A, 0xFFD8B27A, 0xFFF4EEE5, 0xFF2B241D)
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
            ColourSet("mono", "Black and yellow", 0xFFFFE14D, 0xFFFFFFFF, 0xFFE6E6E6, 0xFFF5F5F0, 0xFF000000)
        )
    ),
    Design(
        "clay", "Claymorphism", "Soft and puffy: rounded clay shapes, gentle shadows and pastels.",
        listOf(
            ColourSet("lavender", "Lavender", 0xFF7C6AED, 0xFFA7EFC2, 0xFFFFC4AE, 0xFFF4F1FF, 0xFF1E1B4B),
            ColourSet("mint", "Mint", 0xFF2FA37A, 0xFFFFD39E, 0xFFB9C6FF, 0xFFEFFAF5, 0xFF12332A),
            ColourSet("peach", "Peach", 0xFFE0663A, 0xFFB7A7F7, 0xFF9FE3D0, 0xFFFFF4EE, 0xFF3A1E14),
            ColourSet("sky", "Sky", 0xFF3D8BF2, 0xFFFFD27A, 0xFFF7A8C8, 0xFFEFF5FF, 0xFF12264A),
            ColourSet("bubblegum", "Bubblegum", 0xFFE35D9C, 0xFF9ED8F5, 0xFFFFE08A, 0xFFFFF0F6, 0xFF3D1229),
            ColourSet("lime", "Lime", 0xFF5E9E25, 0xFFFFB98A, 0xFFC1B2FF, 0xFFF5FBEC, 0xFF1F3311)
        )
    )
)

fun designFor(key: String): Design = DESIGNS.firstOrNull { it.key == key } ?: DESIGNS[0]

fun colourSetFor(d: Design, key: String): ColourSet = d.sets.firstOrNull { it.key == key } ?: d.sets[0]

/** Everything the screens need to draw the chosen design. Get it with LocalLook.current. */
class Look(
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
    /** The two shapes or glows drawn behind the list. */
    val blobs: List<Color>,
    /** The font for list names, headings and prices, or null for the font chosen in App settings. */
    val display: FontFamily?
)

val LocalLook = staticCompositionLocalOf { lookFor("material", "indigo", "light") }

private fun c(v: Long) = Color(v)
private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
/** Dark ink on light colours, white on dark ones. */
private fun on(bg: Color, ink: Color) = if (bg.luminance() > 0.4f) ink else Color.White
private fun pick(id: Long, n: Int) = Math.floorMod(id, n.toLong()).toInt()

private val fraunces by lazy { google("Fraunces") }
private val spaceGrotesk by lazy { google("Space Grotesk") }
private val jakarta by lazy { google("Plus Jakarta Sans") }

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
        surfaceContainerHigh = step(0.05f), surfaceContainerHighest = step(0.08f)
    ) else darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = accent, onTertiary = on(accent, bg), tertiaryContainer = accent, onTertiaryContainer = on(accent, bg),
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = variant, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
        surfaceTint = primary, inverseSurface = text, inverseOnSurface = bg, inversePrimary = container,
        surfaceBright = step(0.16f), surfaceDim = bg,
        surfaceContainerLowest = mix(bg, Color.Black, 0.5f), surfaceContainerLow = step(0.04f), surfaceContainer = step(0.06f),
        surfaceContainerHigh = step(0.09f), surfaceContainerHighest = step(0.12f)
    )
}

/** The look for a design, colour set and mode ("light", "dark" or "amoled"). */
fun lookFor(designKey: String, setKey: String, mode: String): Look {
    val d = designFor(designKey)
    val s = colourSetFor(d, setKey)
    val dark = mode != "light"
    val amoled = mode == "amoled"
    val main = c(s.main); val a2 = c(s.a2); val a3 = c(s.a3); val paper = c(s.paper); val ink = c(s.ink)
    val black = Color.Black; val white = Color.White
    return when (d.key) {
        "organic" -> {
            val night = c(0xFF121210)
            val bg = if (amoled) black else if (dark) mix(main, night, 0.88f) else paper
            val surface = if (amoled) mix(main, black, 0.93f) else if (dark) mix(main, night, 0.81f) else mix(paper, c(0xFFFFFDF8), 0.75f)
            val text = if (dark) mix(paper, white, 0.2f) else ink
            val muted = if (dark) mix(text, bg, 0.3f) else mix(ink, paper, 0.32f)
            val line = if (dark) mix(main, if (amoled) black else night, if (amoled) 0.8f else 0.68f) else mix(paper, ink, 0.12f)
            val primary = if (dark) mix(main, white, 0.45f) else main
            val container = if (dark) mix(main, black, if (amoled) 0.55f else 0.45f) else mix(main, white, 0.76f)
            val onContainer = if (dark) mix(main, white, 0.8f) else mix(main, ink, 0.6f)
            val tints = listOf(main, a2, a3).map { if (dark) mix(it, bg, if (amoled) 0.86f else 0.8f) else mix(it, white, 0.78f) }
            Look(
                "organic", dark,
                scheme(dark, primary, if (dark) mix(main, black, 0.65f) else on(main, ink), container, onContainer, a2,
                    bg, surface, text, muted, surface, mix(line, text, 0.35f), line),
                listOf(surface), tints, line, listOf(line),
                listOf(
                    if (dark) mix(main, bg, if (amoled) 0.9f else 0.82f) else mix(main, paper, 0.84f),
                    if (dark) mix(a2, bg, if (amoled) 0.92f else 0.85f) else mix(a2, paper, 0.78f)
                ),
                fraunces
            )
        }
        "neo" -> {
            val bg = if (amoled) black else if (dark) c(0xFF141416) else paper
            val surface = if (amoled) c(0xFF0D0D0F) else if (dark) c(0xFF1F1F23) else white
            val text = if (dark) c(0xFFF4F4EE) else ink
            val muted = if (dark) c(0xFFB9B9B0) else mix(ink, white, 0.28f)
            val accents = listOf(main, a2, a3)
            Look(
                "neo", dark,
                // The neobrutalist colours are all bright, so the text on them is always the dark ink.
                scheme(dark, main, if (dark) c(0xFF111111) else ink, a2, if (dark) c(0xFF111111) else ink, a3,
                    bg, surface, text, muted, surface, text, mix(text, bg, 0.7f)),
                if (dark) listOf(surface) else listOf(main, white, a2, a3),
                accents.map { if (dark) mix(it, surface, 0.15f) else mix(it, white, 0.35f) },
                text,
                if (dark) accents else listOf(ink),
                listOf(mix(text, bg, if (dark) 0.88f else 0.9f)),
                spaceGrotesk
            )
        }
        "clay" -> {
            val night = c(0xFF0E0E14)
            val bg = if (amoled) black else if (dark) mix(main, night, 0.87f) else paper
            val surface = if (amoled) mix(main, black, 0.88f) else if (dark) mix(main, night, 0.76f) else mix(paper, white, 0.78f)
            val text = if (dark) mix(paper, white, 0.3f) else ink
            val muted = if (dark) mix(text, bg, 0.32f) else mix(ink, paper, 0.38f)
            val primary = if (dark) mix(main, white, 0.3f) else main
            val container = if (dark) mix(main, black, 0.42f) else mix(main, white, 0.8f)
            val onContainer = if (dark) mix(main, white, 0.82f) else mix(main, ink, 0.55f)
            val tints = listOf(a2, main, a3).map { if (dark) mix(it, bg, 0.72f) else mix(it, white, if (it == main) 0.78f else 0.55f) }
            Look(
                "clay", dark,
                scheme(dark, primary, on(primary, ink), container, onContainer, a2,
                    bg, surface, text, muted, surface, mix(text, bg, 0.6f), mix(text, bg, 0.85f)),
                listOf(surface), tints, mix(text, bg, 0.85f),
                listOf(if (dark) black.copy(alpha = 0.6f) else mix(main, ink, 0.35f).copy(alpha = 0.35f)),
                listOf(
                    if (dark) mix(main, bg, 0.7f) else mix(main, white, 0.72f),
                    if (dark) mix(a2, bg, 0.75f) else mix(a2, white, 0.45f)
                ),
                jakarta
            )
        }
        else -> {
            // Material 3: the tonal schemes of PALETTES, with the surface container steps for menus and dialogs.
            val p = PALETTES.firstOrNull { it.key == s.key } ?: PALETTES[0]
            var base = if (dark) p.dark else p.light
            if (amoled) base = base.copy(background = black, surface = black, surfaceVariant = c(0xFF0E0E11))
            val bg = base.background; val text = base.onBackground
            val sc = base.copy(
                outlineVariant = mix(base.outline, bg, 0.55f),
                surfaceContainerLowest = if (dark) mix(bg, black, 0.5f) else white,
                surfaceContainerLow = mix(bg, base.primary, 0.03f),
                surfaceContainer = mix(mix(bg, text, 0.03f), base.primary, 0.03f),
                surfaceContainerHigh = mix(mix(bg, text, if (dark) 0.08f else 0.05f), base.primary, 0.04f),
                surfaceContainerHighest = mix(mix(bg, text, if (dark) 0.12f else 0.08f), base.primary, 0.04f),
                surfaceBright = if (dark) mix(bg, text, 0.16f) else bg, surfaceDim = if (dark) bg else mix(bg, text, 0.08f)
            )
            Look(
                "material", dark, sc,
                listOf(sc.surfaceVariant), listOf(sc.primaryContainer), sc.outlineVariant, listOf(sc.outline), emptyList(), null
            )
        }
    }
}

/** The Material shapes (dialogs, menus, text fields, buttons that use them) of each design. */
fun shapesFor(design: String): Shapes = when (design) {
    "organic" -> Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp, 30.dp, 30.dp, 12.dp)
    )
    "neo" -> Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(14.dp)
    )
    "clay" -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
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
    "clay" -> RoundedCornerShape(if (small) 24.dp else 28.dp)
    else -> RoundedCornerShape(if (small) 18.dp else 24.dp)
}

/** Space around the picture inside a card: organic and clay pictures sit on a mat, the others fill the top. */
fun Look.inset(small: Boolean): Dp = when (design) {
    "organic", "clay" -> if (small) 8.dp else 10.dp
    else -> 0.dp
}

fun Look.pictureShape(id: Long, small: Boolean): Shape = when (design) {
    "organic" -> {
        val r = if (small) 18.dp else 24.dp
        val k = if (small) 6.dp else 8.dp
        if (pick(id, 2) == 0) RoundedCornerShape(r, r, r, k) else RoundedCornerShape(r, k, r, r)
    }
    "clay" -> RoundedCornerShape(if (small) 18.dp else 22.dp)
    else -> RectangleShape
}

/** Behind a picture: white for shop photos in Material and neobrutalism, a soft tint otherwise and when there is none. */
fun Look.pictureBg(id: Long, hasImage: Boolean): Color = when {
    hasImage && (design == "material" || design == "neo") -> Color.White
    else -> tints[pick(id, tints.size)]
}

/** Neobrutalism draws a thick line under the picture. */
val Look.pictureLine: Dp get() = if (design == "neo") 2.dp else 0.dp

fun Look.cardFill(id: Long): Color = fills[pick(id, fills.size)]

/** Small labels on a card (price change, "Got it"). */
val Look.badgeShape: Shape
    get() = when (design) {
        "organic" -> RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
        "neo" -> RoundedCornerShape(6.dp)
        "clay" -> RoundedCornerShape(12.dp)
        else -> RoundedCornerShape(8.dp)
    }

/** Buttons such as the category button and the view switch. */
val Look.chipShape: Shape
    get() = when (design) {
        "organic", "clay" -> CircleShape
        "neo" -> RoundedCornerShape(10.dp)
        else -> RoundedCornerShape(12.dp)
    }

/** The Add wish button. */
val Look.fabShape: Shape
    get() = when (design) {
        "organic" -> RoundedCornerShape(22.dp, 30.dp, 26.dp, 30.dp)
        "neo" -> RoundedCornerShape(12.dp)
        "clay" -> RoundedCornerShape(26.dp)
        else -> RoundedCornerShape(16.dp)
    }

/** A hard shadow: the shape again, moved down and to the right, in one flat colour. */
fun Modifier.hardShadow(color: Color, shape: Shape, offset: Dp = 4.dp): Modifier = drawBehind {
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { drawOutline(outline, color) }
}

/** The frame of a wish card in the chosen design: its colour, lines, shadow and shape. */
fun Modifier.lookCard(look: Look, id: Long, shape: Shape): Modifier = when (look.design) {
    "neo" -> hardShadow(look.shadows[pick(id, look.shadows.size)], shape, 5.dp)
        .clip(shape).background(look.cardFill(id)).border(2.dp, look.line, shape)
    "organic" -> clip(shape).background(look.cardFill(id)).border(1.dp, look.line, shape)
    "clay" -> {
        val fill = look.cardFill(id)
        val glow = look.shadows[0]
        shadow(14.dp, shape, clip = false, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(fill, Color.White, if (look.dark) 0.06f else 0.6f), fill)))
            .border(
                1.5.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = if (look.dark) 0.16f else 0.95f), Color.Transparent)),
                shape
            )
    }
    else -> clip(shape).background(look.cardFill(id))
}

/** The frame of a small button (category, view switch). [on] = chosen, filled with the container colour. */
fun Modifier.lookChip(look: Look, on: Boolean = false): Modifier {
    val cs = look.scheme
    val shape = look.chipShape
    val fill = if (on) cs.primaryContainer else Color.Transparent
    return when (look.design) {
        "neo" -> (if (on) hardShadow(look.line, shape, 3.dp) else this)
            .clip(shape).background(if (on) cs.primary else cs.surface).border(2.dp, look.line, shape)
        "organic" -> clip(shape).background(if (on) cs.primary else fill).border(1.dp, look.line, shape)
        "clay" -> shadow(if (on) 6.dp else 4.dp, shape, clip = false, ambientColor = look.shadows[0], spotColor = look.shadows[0])
            .clip(shape).background(if (on) cs.primary else look.cardFill(0))
        else -> clip(shape).background(fill)
    }
}

/** The colour of the text and icons on a chip made with [lookChip]. */
fun Look.chipContent(on: Boolean): Color = when {
    !on -> scheme.onSurfaceVariant
    design == "material" -> scheme.onPrimaryContainer
    else -> scheme.onPrimary
}

/** What is drawn behind the list: organic blobs, clay glows, a neobrutalist dot grid. Material keeps it plain. */
fun Modifier.lookBackdrop(look: Look): Modifier = when (look.design) {
    "organic" -> drawBehind {
        blob(Offset(size.width * 0.86f, 34.dp.toPx()), 110.dp.toPx(), look.blobs[0], 0.4f)
        blob(Offset(size.width * 0.08f, 190.dp.toPx()), 80.dp.toPx(), look.blobs[1], 2.1f)
    }
    "clay" -> drawBehind {
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
