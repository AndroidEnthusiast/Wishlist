package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/*
 * The Iconic prize (owner, 27 November 2026; docs/decisions/home-and-kinds.md, last sections). Reaching the Iconic
 * value level on Home unlocks three things, kept even when the total drops later (the badge follows the total):
 *  - a holo ring round your photo, seen by you, your friends and the people in a group with you;
 *  - glowing reactions: your reactions on friends' wishes glow (app and web pages);
 *  - a holo share card: sharing one of your lists sends a holographic picture with the link, and friends who open
 *    the list see a holo edge at its top (app and the friends' web page).
 * All three start on and are switched in App settings > Iconic. The server only keeps your three switches
 * (wl_pf_iconic in supabase-together.sql); it can't check the level (worked out here), so it is decoration only.
 */
object Iconic {
    /** Show's keys of the three switches (on at first). */
    const val RING = "ic_ring"
    const val CARD = "ic_card"
    const val GLOW = "ic_glow"

    /** True once Iconic was reached on this phone (kept for good). */
    var reached by mutableStateOf(false)
        private set
    /** The "You're Iconic" pop-up is waiting to be shown (once). */
    var celebrate by mutableStateOf(false)
        private set
    /** Friends and group mates whose photo has the ring (ids), from the server. */
    var rings by mutableStateOf(setOf<String>())
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** A new pretend phone in the checks (the app keeps objects between checks in one process). */
    internal fun reset() { loaded = false; reached = false; celebrate = false; currency = ""; rings = emptySet() }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        reached = p.getBoolean("ic_reached", false)
        currency = p.getString("ic_cur", "") ?: ""
        rings = p.getStringSet("ic_rings", null)?.toSet() ?: emptySet()
        Show.load(c)
    }

    val myRing: Boolean get() = reached && Show[RING]
    val myCard: Boolean get() = reached && Show[CARD]
    val myGlow: Boolean get() = reached && Show[GLOW]

    /** The currency of your total when Iconic was reached (the pop-up's "just hit €10,000"). */
    var currency by mutableStateOf("")
        private set

    /** Home's level now ([cur]: its currency): reaching Iconic the first time unlocks the prize and asks for the pop-up. */
    fun seen(c: Context, level: ValueLevel?, cur: String = "") {
        load(c)
        if (level != ValueLevel.ICONIC || reached) return
        reached = true
        celebrate = true
        currency = cur
        prefs(c).edit().putBoolean("ic_reached", true).putBoolean("ic_dirty", true).putString("ic_cur", cur).apply()
    }

    /** The pop-up was closed. */
    fun celebrated() { celebrate = false }

    /** "See your prizes" on the levels page: the pop-up again. */
    fun showPrizes() { if (reached) celebrate = true }

    /** A prize switched on or off (App settings > Iconic, or "Turn on" in its preview); saved online next. */
    fun set(c: Context, key: String, on: Boolean) {
        Show.set(c, key, on)
        prefs(c).edit().putBoolean("ic_dirty", true).apply()
    }

    /** Tells the server your three switches when they changed (quiet when offline or on an older server). */
    suspend fun sync(c: Context) {
        load(c)
        val p = prefs(c)
        if (!p.getBoolean("ic_dirty", false) || !p.getBoolean("pf_made", false)) return
        try {
            val body = Friends.base(c).put("p_ring", myRing).put("p_glow", myGlow).put("p_list", myCard)
            if (Cloud.rpc("wl_pf_iconic", body).trim() == "true") p.edit().putBoolean("ic_dirty", false).apply()
        } catch (e: Exception) {
            // Tried again at the next refresh.
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /** Whose photo gets the ring (your friends and group mates); kept for offline. Quiet on an older server. */
    suspend fun loadRings(c: Context) {
        load(c)
        if (!prefs(c).getBoolean("pf_made", false)) return
        try {
            val a = JSONArray(Cloud.rpc("wl_ic_people", Friends.base(c)))
            rings = (0 until a.length()).map { a.getString(it) }.toSet()
            prefs(c).edit().putStringSet("ic_rings", rings).apply()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /** Whether someone's photo shows the ring: yours ([id] null) or a friend's / group mate's. */
    fun ringFor(id: String?): Boolean = if (id == null) myRing else id in rings

    /** Whether list [code] belongs to an Iconic member who shows the holo edge. False when unknown or offline. */
    suspend fun listHolo(code: String): Boolean = try {
        Cloud.rpc("wl_ic_list", JSONObject().put("p_code", code)).trim() == "true"
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        false
    }
}
