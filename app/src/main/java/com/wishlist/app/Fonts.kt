package com.wishlist.app

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * The fonts to pick from in App settings. Google Sans is in the app; the others are Google Fonts that the phone
 * downloads once through Google Play services the first time they are used (so the app stays small). Until a font has
 * arrived, or on a phone without Google Play services, the text simply shows in the phone's standard font.
 * The certificates for that download are in res/values/font_certs.xml (Google's own file).
 */
class AppFont(val key: String, val name: String, val family: FontFamily)

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun google(name: String): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it)
    }
)

val FONTS: List<AppFont> = listOf(
    AppFont("google_sans", "Google Sans", GoogleSans),
    AppFont("roboto", "Roboto", google("Roboto")),
    AppFont("inter", "Inter", google("Inter")),
    AppFont("nunito", "Nunito", google("Nunito")),
    AppFont("fraunces", "Fraunces", google("Fraunces")),
    AppFont("poppins", "Poppins", google("Poppins")),
    AppFont("montserrat", "Montserrat", google("Montserrat")),
    AppFont("lato", "Lato", google("Lato")),
    AppFont("open_sans", "Open Sans", google("Open Sans")),
    AppFont("dm_sans", "DM Sans", google("DM Sans")),
    AppFont("manrope", "Manrope", google("Manrope")),
    AppFont("quicksand", "Quicksand", google("Quicksand")),
    AppFont("merriweather", "Merriweather", google("Merriweather")),
    AppFont("playfair", "Playfair Display", google("Playfair Display"))
)

/** The chosen font, or Google Sans (the original) when nothing was chosen. */
fun fontFor(key: String): AppFont = FONTS.firstOrNull { it.key == key } ?: FONTS[0]
