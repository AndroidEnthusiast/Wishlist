package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's language: "en" (UK English) or "nl" (Dutch), chosen in App settings. The first time it follows the phone.
 * Every text on screen goes through [tr] with its English wording; the Dutch wording is in [NL] (NlStrings.kt).
 * A new text: write it with tr("…") and add its Dutch line to NL. Wishes themselves (titles from shops) are never translated.
 */
object Lang {
    var code by mutableStateOf("en")
        private set

    fun load(c: Context) {
        val p = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        code = p.getString("lang", null) ?: if (Locale.getDefault().language == "nl") "nl" else "en"
    }

    fun set(c: Context, lang: String) {
        code = if (lang == "nl") "nl" else "en"
        c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().putString("lang", code).apply()
    }
}

/** [en] in the chosen language. Reading it inside a screen redraws that screen when the language changes. */
fun tr(en: String): String = if (Lang.code == "nl") NL[en] ?: en else en

/** The same with placeholders: tr("Delete “%s”?", name). Use %1$s, %2$s when the Dutch order differs. */
fun tr(en: String, vararg args: Any?): String = String.format(Locale.ROOT, tr(en), *args)

/** "All" is the category that shows every wish; it is stored as "All" and only shown translated. */
fun catLabel(c: String): String = if (c == "All") tr("All") else c
