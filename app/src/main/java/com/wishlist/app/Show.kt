package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What you see and which notifications you get (owner, 2.101): App settings > "What you see" and "Notifications".
 * For all your lists, only on this phone. Price drops and increases keep their own settings in [Repo]
 * (notifyDrops, notifyRises).
 *
 * The switches are read straight from the preferences too ([on]), because the background checks can run before the
 * app's screens have loaded them.
 */
object Show {
    /** Key, default. Everything to see is on by default; birthdays are off (owner). */
    private val DEFAULTS = mapOf(
        "show_badge" to true,    // the price change badge on a picture (−55% / +3%)
        "show_desc" to true,     // the description on the large cards
        "show_summary" to true,  // the Wishes / Total value tiles at the top of a list
        "nt_bin" to true,        // a gift you reserved was moved to the bin
        "nt_gone" to true,       // a gift you reserved was taken off a list
        "nt_req" to true,        // friend requests
        "nt_ok" to true,         // your friend request was accepted
        "nt_bday" to false,      // friends' birthdays
        "nt_msg" to true,        // new messages
        "nt_group" to true       // wishlist groups (2.108): invites, "No thanks", removed from a group
    )

    private val state = HashMap<String, androidx.compose.runtime.MutableState<Boolean>>()
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        DEFAULTS.forEach { (k, d) -> state.getOrPut(k) { mutableStateOf(d) }.value = p.getBoolean(k, d) }
    }

    /** For screens (follows changes). Call [load] first; before that, the default. */
    operator fun get(key: String): Boolean = state.getOrPut(key) { mutableStateOf(DEFAULTS[key] ?: true) }.value

    fun set(c: Context, key: String, on: Boolean) {
        load(c)
        state.getOrPut(key) { mutableStateOf(on) }.value = on
        prefs(c).edit().putBoolean(key, on).apply()
    }

    /** For the background checks and alerts. */
    fun on(c: Context, key: String): Boolean = prefs(c).getBoolean(key, DEFAULTS[key] ?: true)

    /** Which notification switch covers a kind on the Notifications page (null: none, always shown). */
    fun keyFor(kind: String): String? = when (kind) {
        "gift_binned" -> "nt_bin"
        "gift_gone" -> "nt_gone"
        "friend_req" -> "nt_req"
        "friend_ok" -> "nt_ok"
        "birthday" -> "nt_bday"
        "group_invite", "group_declined", "group_removed" -> "nt_group"
        else -> null
    }
}
