package com.wishlist.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App settings, the look, the cover picture and the lists you opened recently (a part of [Repo] since the data rebuild,
 * owner 19 November 2026). Kept in the settings store only.
 */
internal object AppPrefs {
    var theme by mutableStateOf("light")
        internal set
    /** The accent colour (2.97): one of ACCENTS in Design.kt. (Before: the colour set of the standard themes.) */
    var palette by mutableStateOf("berry")
        internal set
    /**
     * The style (2.97): one of STYLES in Design.kt ("quiet" or "chaos"; until 2.198 also "neo", "neu" and "cyber",
     * from 2.199 to 2.205 twelve more styles that were removed, owner 14 November 2026), with light or dark in
     * [theme] and the accent in [palette]. ([colours] is the old designs' colour set, only read to move people over.)
     */
    var design by mutableStateOf("quiet")
        internal set
    var colours by mutableStateOf("")
        internal set
    /** The font of the app: one of FONTS in Fonts.kt. Google Sans is the original. */
    var font by mutableStateOf("google_sans")
        internal set
    /** On: wishes are deleted without asking first (Undo is still offered). Off (the default): "Delete this wish?" is asked. */
    var instantDelete by mutableStateOf(false)
        internal set
    /** Notifications when a price drops (on unless switched off) or goes up (off unless switched on). */
    var notifyDrops by mutableStateOf(true)
        internal set
    var notifyRises by mutableStateOf(false)
        internal set
    /** How the wishes are shown: "list" (large cards) or "grid" (two per row). */
    var view by mutableStateOf("rows")
        internal set
    /** How the wishes are ordered: "new", "star", "low" or "high" (see Sorting.kt; a saved "az" opens as "new"). */
    var sort by mutableStateOf("new")
        internal set

    /** How often the background price check should run, in hours. */
    var intervalHours by mutableStateOf(6)
        internal set
    /** When the last background price check finished (0 = never). */
    var lastCheck by mutableStateOf(0L)
        internal set
    /** Where the last wish shared in from the browser was saved: "mine", "private" or the code of a shared list. */
    var lastSaveTo = "mine"
        internal set
    /** Recently opened lists: "m:<id>" for your own, "s:<code>" for a group wishlist, with the moment opened. */
    var recent by mutableStateOf(mapOf<String, Long>())
        internal set
    /** The picture at the top of the dashboard (a file: address), or empty for the theme's own colours. */
    var hero by mutableStateOf("")
        internal set
    /** How that picture sits in its frame (zoom, position) and its optional text: see HeroEditor.kt. */
    var heroEdit by mutableStateOf(HeroEdit())
        internal set
    /** The finished cover picture (the part in the frame, turned and lightened), made when it was saved; empty for older ones. */
    var heroCut by mutableStateOf("")
        internal set

    /** Reads the settings at a start, moving old choices over (part of Repo.load, in the same order as before). */
    internal fun load(c: Context, p: SharedPreferences) {
        // There is no "System default" any more (2.53): the first start (and a phone that had it) quietly takes
        // Light or Dark to match the phone's setting at that moment.
        theme = p.getString("theme", null)?.takeIf { it == "light" || it == "dark" || it == "black" || it == "amoled" || it == "system" }
            ?: if ((c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        // A new phone with nothing saved yet starts in Berry, the app's own colour (owner, 15 November 2026); everyone
        // who already has a colour keeps it (also the "indigo" default of before when it was never saved).
        val fresh = !p.contains("palette") && !p.contains("look_v2")
        palette = startPalette(p.contains("palette"), p.contains("look_v2"), p.getString("palette", null))
        // Stored as "style" since 2.52. Since 2.97 (owner) it is one of STYLES (Design.kt), with an accent in
        // "palette" and light or dark in "theme". The first time, the old choice moves over: Neobrutalism stays
        // (Midnight becomes dark), the other old designs and the standard themes become Quiet; AMOLED black becomes
        // dark; the old colour sets become the closest accent.
        val oldStyle = p.getString("style", "classic") ?: "classic"
        colours = p.getString("style_colours", "") ?: ""
        if (!p.getBoolean("look_v2", false)) {
            // (Neobrutalism stayed here until 13 November 2026; it was removed then, so it becomes Quiet too.)
            design = "quiet"
            if (oldStyle != "classic") {
                // The old designs had their own dark sets (light or dark came from the set, not from "theme").
                theme = if (colours in setOf("midnight", "plum", "night", "dusk")) "dark" else "light"
            }
            if (theme == "amoled") theme = "dark"
            if (!fresh) palette = when (if (oldStyle == "classic") palette else colours) {
                "indigo", "lavender", "lilac", "plum", "dusk" -> "indigo"
                "sage", "forest", "night", "mint", "seaglass" -> "green"
                "rose", "berry", "bubblegum", "coral" -> "pink"
                "amber", "sunshine", "terracotta", "tangerine", "peach", "lemon" -> "orange"
                else -> "blue"
            }
            p.edit().putBoolean("look_v2", true).apply()
        } else {
            // A style that was removed (Cyberpunk, Neobrutalism, Neumorphism on 13 November 2026; the twelve styles of
            // 2.199–2.205 on 14 November 2026, owner) becomes Quiet
            // (styleFor), light or dark and the accent stay; saved at once so it is settled.
            design = styleFor(oldStyle).key
            if (design != oldStyle) p.edit().putString("style", design).apply()
            // (An unknown colour becomes the closest theme just below, and is saved.)
        }
        // The five themes (owner, 17 November 2026): Pink and a colour from the old colour wheel become the closest
        // theme, once, saved at once.
        closestTheme(palette).let { t -> if (t != palette) { palette = t; p.edit().putString("palette", t).apply() } }
        font = p.getString("font", "google_sans") ?: "google_sans"
        instantDelete = p.getBoolean("instant_delete", false)
        notifyDrops = p.getBoolean("notify_drops", true)
        notifyRises = p.getBoolean("notify_rises", false)
        // "gallery" (images only) was removed in 2.34: it falls back to the large cards.
        // Two per row is the default since 2.100 (owner); the large cards are the "details" view. Everyone who used an
        // older version has "view" saved already, so they keep what they had.
        // Rows is the default for new installs since 2.126 (owner); a saved choice is kept (owner: nobody's screen
        // changes by surprise).
        view = p.getString("view", null)?.let { v -> if (v == "list" || v == "rows") v else "grid" } ?: "rows"
        sort = p.getString("sort", "new")?.takeIf { s -> SORT_OPTIONS.any { it.first == s } } ?: "new"
        intervalHours = p.getInt("interval", 6).let { if (it in listOf(1, 3, 6, 12, 24)) it else 6 }
        lastCheck = p.getLong("lastCheck", 0L)
        lastSaveTo = p.getString("last_save_to", "mine") ?: "mine"
        recent = try {
            val o = JSONObject(p.getString("recent", null) ?: "{}")
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        hero = p.getString("hero", "") ?: ""
        heroEdit = HeroEdit.fromJson(p.getString("hero_edit", null))
        heroCut = p.getString("hero_cut", "") ?: ""
    }

    /** Writes the settings with every save (part of Repo.persist). */
    internal fun write(e: SharedPreferences.Editor) {
        val rec = JSONObject()
        recent.forEach { (k, v) -> rec.put(k, v) }
        e.putString("recent", rec.toString())
            .putString("hero", hero)
            .putString("hero_edit", heroEdit.toJson())
            .putString("hero_cut", heroCut)
            .putString("theme", theme)
            .putString("palette", palette)
            .putString("style", design)
            .putString("style_colours", colours)
            .putString("font", font)
            .putString("view", view)
            .putString("sort", sort)
            .putInt("interval", intervalHours)
            .putBoolean("instant_delete", instantDelete)
            .putBoolean("notify_drops", notifyDrops)
            .putBoolean("notify_rises", notifyRises)
            .putLong("lastCheck", lastCheck)
            .putString("last_save_to", lastSaveTo)
    }
}

// ---- what you can do with it (callers keep writing Repo.…, as before the rebuild) ----

/**
 * Remembers that a list ("m:"/"s:") or a wishlist group ("g:") was opened: Home shows the one opened last first in
 * its section (2.110; "Recently opened" was removed).
 */
fun Repo.touchRecent(c: Context, key: String): Unit = synchronized(this) {
    AppPrefs.recent = (recent + (key to System.currentTimeMillis())).entries
        .sortedByDescending { it.value }.take(200).associate { it.key to it.value }
    persist(c)
}

fun Repo.forgetRecent(c: Context, key: String): Unit = synchronized(this) {
    if (key !in recent) return
    AppPrefs.recent = recent - key
    persist(c)
}

fun Repo.setHero(c: Context, image: String, edit: HeroEdit = HeroEdit(), cut: String = ""): Unit = synchronized(this) {
    val old = hero
    val oldCut = heroCut
    AppPrefs.hero = image
    AppPrefs.heroEdit = edit
    AppPrefs.heroCut = cut
    if (oldCut.isNotEmpty() && oldCut != cut) ImageStore.delete(c, oldCut)
    persist(c)
    if (old.isNotEmpty() && old != image) ImageStore.delete(c, old)
}

fun Repo.setTheme(c: Context, t: String): Unit = synchronized(this) { AppPrefs.theme = t; persist(c) }
/** The accent colour: one of ACCENTS (Design.kt). Kept as "palette" (it held the old colour sets). */
fun Repo.setPalette(c: Context, p: String) { AppPrefs.palette = p; persist(c) }
/** The look (2.97): a style of STYLES, "light" or "dark", and an accent of ACCENTS. */
fun Repo.setLook(c: Context, style: String, mode: String, accent: String) {
    AppPrefs.design = styleFor(style).key; AppPrefs.theme = mode; AppPrefs.palette = accentFor(accent).key
    persist(c)
}
fun Repo.setFont(c: Context, f: String) { AppPrefs.font = f; persist(c) }

fun Repo.setInstantDelete(c: Context, on: Boolean): Unit = synchronized(this) { AppPrefs.instantDelete = on; persist(c) }
fun Repo.setNotifyDrops(c: Context, on: Boolean) { AppPrefs.notifyDrops = on; persist(c) }
fun Repo.setNotifyRises(c: Context, on: Boolean) { AppPrefs.notifyRises = on; persist(c) }

fun Repo.setView(c: Context, v: String): Unit = synchronized(this) { AppPrefs.view = v; persist(c) }

fun Repo.setSort(c: Context, s: String): Unit = synchronized(this) { AppPrefs.sort = s; persist(c) }

fun Repo.setInterval(c: Context, hours: Int): Unit = synchronized(this) { AppPrefs.intervalHours = hours; persist(c) }

fun Repo.setLastCheck(c: Context, t: Long): Unit = synchronized(this) { AppPrefs.lastCheck = t; persist(c) }

fun Repo.setLastSaveTo(c: Context, to: String): Unit = synchronized(this) { AppPrefs.lastSaveTo = to; persist(c) }

/**
 * The accent colour at start (2.210): Berry, the app's own colour, on a new phone with nothing saved (no colour and no
 * look saved); otherwise the saved colour, or "indigo", the old default, for a phone that never saved one.
 */
internal fun startPalette(hasPalette: Boolean, hasLook: Boolean, saved: String?): String =
    if (!hasPalette && !hasLook) "berry" else saved ?: "indigo"
