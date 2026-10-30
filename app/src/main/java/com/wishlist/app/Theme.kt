package com.wishlist.app

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

@OptIn(ExperimentalTextApi::class)
val GoogleSans = FontFamily(
    Font(R.font.google_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.google_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.google_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.google_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

private fun typo(f: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f), displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f), headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f), headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f), titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f), bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f), bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f), labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f)
    )
}

/** One set of colours: the light and the dark version. AMOLED black is the dark one on pure black. */
class Palette(val key: String, val name: String, val light: ColorScheme, val dark: ColorScheme)

private fun light(primary: Long, container: Long, onContainer: Long, bg: Long, onBg: Long, variant: Long, onVariant: Long, outline: Long) =
    lightColorScheme(
        primary = Color(primary), onPrimary = Color.White,
        primaryContainer = Color(container), onPrimaryContainer = Color(onContainer),
        secondaryContainer = Color(container), onSecondaryContainer = Color(onContainer),
        background = Color(bg), onBackground = Color(onBg),
        surface = Color(bg), onSurface = Color(onBg),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(onVariant),
        outline = Color(outline)
    )

private fun dark(primary: Long, onPrimary: Long, container: Long, onContainer: Long, bg: Long, onBg: Long, variant: Long, onVariant: Long, outline: Long) =
    darkColorScheme(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(container), onPrimaryContainer = Color(onContainer),
        secondaryContainer = Color(container), onSecondaryContainer = Color(onContainer),
        background = Color(bg), onBackground = Color(onBg),
        surface = Color(bg), onSurface = Color(onBg),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(onVariant),
        outline = Color(outline)
    )

/**
 * The colour choices in App settings, each with Material contrast (tones 40/90/10 in light, 80/30/90 in dark).
 * Indigo is the original look. "wallpaper" (Material You, Android 12+) is not in this list: it comes from the phone.
 */
val PALETTES: List<Palette> = listOf(
    Palette(
        "indigo", "Indigo",
        light(0xFF4355B9, 0xFFDEE0FF, 0xFF00105C, 0xFFFBF8FF, 0xFF1B1B21, 0xFFF0EDF7, 0xFF46464F, 0xFF777680),
        dark(0xFFBAC3FF, 0xFF08218A, 0xFF2C3FA0, 0xFFDEE0FF, 0xFF121318, 0xFFE4E1E9, 0xFF1F1F25, 0xFFC7C5D0, 0xFF91909A)
    ),
    Palette(
        "sage", "Sage",
        light(0xFF4A6741, 0xFFCCEDBE, 0xFF072106, 0xFFF8FAF0, 0xFF1A1C18, 0xFFECEFE3, 0xFF43483F, 0xFF73796E),
        dark(0xFFB0D1A3, 0xFF1D361A, 0xFF33502D, 0xFFCCEDBE, 0xFF11140F, 0xFFE1E4DA, 0xFF1D201B, 0xFFC3C8BB, 0xFF8D9387)
    ),
    Palette(
        "rose", "Rose",
        light(0xFF8C4A5D, 0xFFFFD9E0, 0xFF3A071B, 0xFFFFF8F8, 0xFF22191B, 0xFFF5EBEC, 0xFF524345, 0xFF847375),
        dark(0xFFFFB1C3, 0xFF551D2F, 0xFF713346, 0xFFFFD9E0, 0xFF191113, 0xFFF0DEE0, 0xFF261D1F, 0xFFD6C2C4, 0xFF9F8C8E)
    ),
    Palette(
        "ocean", "Ocean",
        light(0xFF006A6A, 0xFF9CF1F0, 0xFF002020, 0xFFF4FBFA, 0xFF161D1D, 0xFFE7EFEE, 0xFF3F4948, 0xFF6F7979),
        dark(0xFF80D5D4, 0xFF003737, 0xFF004F4F, 0xFF9CF1F0, 0xFF0E1514, 0xFFDDE4E3, 0xFF1A2121, 0xFFBEC9C8, 0xFF889392)
    ),
    Palette(
        "amber", "Amber",
        light(0xFF7C5800, 0xFFFFDEA6, 0xFF271900, 0xFFFFF8F2, 0xFF1F1B13, 0xFFF4ECDF, 0xFF4E4639, 0xFF807667),
        dark(0xFFF7BD48, 0xFF412D00, 0xFF5E4200, 0xFFFFDEA6, 0xFF17130B, 0xFFEBE1D4, 0xFF241F17, 0xFFD1C5B4, 0xFF9A8F80)
    ),
    Palette(
        "lavender", "Lavender",
        light(0xFF6750A4, 0xFFEADDFF, 0xFF21005D, 0xFFFEF7FF, 0xFF1D1B20, 0xFFF3EDF7, 0xFF49454F, 0xFF79747E),
        dark(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF, 0xFF141218, 0xFFE6E0E9, 0xFF211F26, 0xFFCAC4D0, 0xFF938F99)
    )
)

/** Material You (colours from the wallpaper) exists from Android 12 on. */
val wallpaperColours: Boolean get() = Build.VERSION.SDK_INT >= 31

/** The colour scheme for a palette key and mode ("light", "dark" or "amoled"). */
fun schemeFor(c: Context, palette: String, mode: String): ColorScheme {
    val darkMode = mode != "light"
    val base = if (palette == "wallpaper" && Build.VERSION.SDK_INT >= 31) {
        if (darkMode) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
    } else {
        val p = PALETTES.firstOrNull { it.key == palette } ?: PALETTES[0]
        if (darkMode) p.dark else p.light
    }
    // AMOLED black: the dark colours on pure black, with the cards just off black.
    return if (mode == "amoled") base.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF0E0E11)) else base
}

@Composable
fun isDarkMode(): Boolean {
    val sys = isSystemInDarkTheme()
    return when (Repo.theme) { "light" -> false; "dark", "amoled" -> true; else -> sys }
}

/** The theme actually in use: "light", "dark" or "amoled". */
@Composable
fun effectiveTheme(): String {
    val sys = isSystemInDarkTheme()
    return if (Repo.theme == "system") (if (sys) "dark" else "light") else Repo.theme
}

@Composable
fun WishTheme(content: @Composable () -> Unit) {
    val mode = effectiveTheme()
    val ctx = LocalContext.current
    val scheme = schemeFor(ctx, Repo.palette, mode)
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            w.statusBarColor = scheme.background.toArgb()
            w.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = mode == "light"
                isAppearanceLightNavigationBars = mode == "light"
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = typo(fontFor(Repo.font).family), content = content)
}
