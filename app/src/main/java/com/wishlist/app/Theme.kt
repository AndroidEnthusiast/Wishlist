package com.wishlist.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

/** Google Sans, built into the app (still offered in Fonts.kt; the styles use their own fonts since 2.97). */
@OptIn(ExperimentalTextApi::class)
val GoogleSans = FontFamily(
    Font(R.font.google_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.google_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.google_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.google_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** The mode in use: "light" or "dark" ("system", from before 2.53, follows the phone). */
@Composable
fun effectiveTheme(): String {
    val sys = isSystemInDarkTheme()
    return when (Repo.theme) {
        "system" -> if (sys) "dark" else "light"
        "light" -> "light"
        else -> "dark"
    }
}

@Composable
fun WishTheme(content: @Composable () -> Unit) {
    // The style (Quiet or one of the four), its accent colour and light or dark (Design.kt, picked in the Themer).
    val look = lookFor(Repo.design, Repo.palette, effectiveTheme())
    val scheme = look.scheme
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            w.statusBarColor = scheme.background.toArgb()
            w.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !look.dark
                isAppearanceLightNavigationBars = !look.dark
            }
        }
    }
    LookTheme(look, content)
}

/** Everything inside drawn with [look]: its colours, fonts, shapes and LocalLook (the Themer's preview uses this too). */
@Composable
fun LookTheme(look: Look, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(
            colorScheme = look.scheme,
            typography = typographyFor(look.design),
            shapes = shapesFor(look.design),
            content = content
        )
    }
}
