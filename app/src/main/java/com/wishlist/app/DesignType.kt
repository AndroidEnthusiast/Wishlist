package com.wishlist.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* The styles (see Design.kt): their type scale and weights. */

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

/** Plus Jakarta Sans' own measurements (its font file, per letter height): ascent, descent and capital height. */
internal const val JAKARTA_ASCENT = 1.038f
internal const val JAKARTA_DESCENT = 0.222f
internal const val JAKARTA_CAP = 0.745f

/**
 * How much of a label's spare line height goes above the text so its capitals sit exactly in the middle of the line
 * (owner, 17 November 2026: chip text "perfectly centred" between the chip's top and bottom). The font draws its
 * capitals low in its box (a tall ascent), so an even split left them about 1 px low. [size] and [line] in sp.
 */
internal fun capsTopRatio(size: Int, line: Int): Float {
    val spare = line - size * (JAKARTA_ASCENT + JAKARTA_DESCENT)
    if (spare <= 0f) return 0.5f
    val top = line / 2f - size * (JAKARTA_ASCENT - JAKARTA_CAP / 2f)
    return (top / spare).coerceIn(0f, 1f)
}

private fun TextStyle.capsCentred(size: Int, line: Int) = copy(
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        androidx.compose.ui.text.style.LineHeightStyle.Alignment(capsTopRatio(size, line)),
        androidx.compose.ui.text.style.LineHeightStyle.Trim.None
    )
)

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
        titleMedium = f(d.titleMedium, jakarta, FontWeight.SemiBold, 17, 22).copy(fontFeatureSettings = "tnum"),
        titleSmall = f(d.titleSmall, jakarta, FontWeight.Medium, 16, 21),
        bodyLarge = f(d.bodyLarge, jakarta, FontWeight.Normal, 16, 22), bodyMedium = f(d.bodyMedium, jakarta, FontWeight.Normal, 15, 21),
        bodySmall = f(d.bodySmall, jakarta, FontWeight.Normal, 13, 18),
        labelLarge = f(d.labelLarge, jakarta, FontWeight.SemiBold, 15, 20).capsCentred(15, 20),
        labelMedium = f(d.labelMedium, jakarta, FontWeight.Medium, 13, 18).capsCentred(13, 18),
        labelSmall = f(d.labelSmall, jakarta, FontWeight.Medium, 12, 16, 0.1f).capsCentred(12, 16)
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
