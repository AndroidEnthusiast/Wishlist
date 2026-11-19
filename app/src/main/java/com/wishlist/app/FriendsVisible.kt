package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Which of your own lists your friends can see (a part of [Friends] since the data rebuild, owner 19 November 2026). */
internal object FriendVisibility {
    /** Ids of your own lists that friends can see. */
    var visible by mutableStateOf<Set<String>>(emptySet())
        internal set
}

// ---- what you can do with it (callers keep writing Friends.…, as before the rebuild) ----

fun Friends.isVisible(id: String) = id in visible

/** Turns "Visible to friends" on or off for your list [id] (its code and token must be known when on). */
suspend fun Friends.setVisible(c: Context, id: String, on: Boolean) {
    val before = visible
    FriendVisibility.visible = if (on) visible + id else visible - id
    prefs(c).edit().putStringSet("fr_vis", visible).apply()
    try {
        ensure(c)
        pushLists(c)
    } catch (e: Exception) {
        // Not saved online: the switch goes back.
        FriendVisibility.visible = before
        prefs(c).edit().putStringSet("fr_vis", visible).apply()
        throw e
    }
}

/** A list went offline or was deleted: friends can't see it any more. */
fun Friends.forget(c: Context, id: String) {
    load(c)
    if (id !in visible) return
    FriendVisibility.visible = visible - id
    prefs(c).edit().putStringSet("fr_vis", visible).apply()
}

/** Your list [from] now lives under the id [to] (the original list was deleted and the next took its place). */
fun Friends.moveId(c: Context, from: String, to: String) {
    load(c)
    if (from !in visible) return
    FriendVisibility.visible = visible - from + to
    prefs(c).edit().putStringSet("fr_vis", visible).apply()
}

/** Sends which lists friends can see (each with its owner token, so the server knows they are yours). */
suspend fun Friends.pushLists(c: Context) {
    val a = JSONArray()
    for (d in Repo.allMine(c)) {
        if (d.id !in visible) continue
        val code = d.code ?: continue
        val token = d.token ?: continue
        a.put(JSONObject().put("code", code).put("token", token))
    }
    Cloud.rpc("wl_pf_lists", base(c).put("p_lists", a))
}
