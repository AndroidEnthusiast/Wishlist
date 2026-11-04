package com.wishlist.app

import android.content.Context
import java.util.Locale

/**
 * The app's language. Since 2.111 (owner, 4 November 2026) only UK English: Dutch was removed to keep every change
 * small, and the Language setting went with it. Every text on screen still goes through [tr] with its English wording,
 * so a full translation later only needs a dictionary here (the owner plans to ask for one some day). Wishes
 * themselves (titles from shops) are never translated.
 */
object Lang {
    /** Always "en" for now (dates and numbers use the UK style). */
    val code: String = "en"

    @Suppress("UNUSED_PARAMETER")
    fun load(c: Context) {}
}

/** The text on screen: the English wording as it is (the hook for a later translation). */
fun tr(en: String): String = en

/** The same with placeholders: tr("Delete “%s”?", name). */
fun tr(en: String, vararg args: Any?): String = String.format(Locale.ROOT, tr(en), *args)

/** "All" is the category that shows every wish; it is stored as "All" and only shown translated. */
fun catLabel(c: String): String = if (c == "All") tr("All") else c
