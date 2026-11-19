package com.wishlist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

// Making, joining and leaving gift exchanges, the organiser's settings, invites, your list and the photo
// (a part of [Exchanges] since the data rebuild, owner 19 November 2026; callers keep writing Exchanges.…).

/** Exchange photos on this phone (a part of [Exchanges] since the data rebuild, owner 19 November 2026; callers keep writing Exchanges.…). */
internal object ExchangePhotos {
    /** Exchange photos on this phone, by id (a file address). */
    var photos by mutableStateOf<Map<String, String>>(emptyMap())
        internal set
}

// ---- what you can do with it (callers keep writing Exchanges.…, as before the rebuild) ----

// ---- photos ----

internal fun Exchanges.dir(c: Context) = File(c.filesDir, "exchanges").apply { mkdirs() }

internal suspend fun Exchanges.loadPhotos(c: Context) {
    val d = dir(c)
    val want = list.filter { it.pv > 0 }.associate { it.id to "${it.id}_${it.pv}.jpg" }
    val out = HashMap<String, String>()
    for ((id, name) in want) {
        val f = File(d, name)
        if (!f.exists()) try {
            val bytes = PeoplePhotos.bytes(Cloud.rpc("wl_gx_photo", base(c).put("p_gx", id))) ?: continue
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        } catch (e: Exception) {
            continue
        }
        out[id] = Uri.fromFile(f).toString()
    }
    (d.listFiles() ?: emptyArray()).filter { it.name !in want.values }.forEach { it.delete() }
    ExchangePhotos.photos = out
}

/**
 * The organiser's own photo instead of the drawn cover, after the same editor as a wishlist's cover (owner, 2.130):
 * [src] cut as [edit] in the frame [fa], sent as a small JPEG. Null [src]: back to the theme's cover.
 */
suspend fun Exchanges.setPhoto(c: Context, gx: String, src: String?, edit: HeroEdit, fa: Float) {
    val jpeg: ByteArray? = if (src == null) null else {
        val cut = renderCover(c, src, edit, fa) ?: throw CloudException("photo")
        withContext(Dispatchers.IO) {
            val f = File(Uri.parse(cut).path ?: throw CloudException("photo"))
            val bmp = BitmapFactory.decodeFile(f.path) ?: throw CloudException("photo")
            f.delete()
            val w = minOf(900, bmp.width)
            val small = Bitmap.createScaledBitmap(bmp, w, (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1), true)
            val out = java.io.ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }
    // As a file (2.185, PeoplePhotos), or the old way before the scripts are run.
    if (!PeoplePhotos.save("x/" + gx, base(c), "wl_gx_photo_url", base(c).put("p_gx", gx), jpeg)) {
        Cloud.rpc("wl_gx_set_photo", base(c).put("p_gx", gx).put("p_photo", jpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""))
    }
    refresh(c)
}

// ---- your list ----

/** Your list [listId] online for an exchange: its code and owner token ("Only me" stays "Only me" for friends). */
internal suspend fun Exchanges.listOnline(c: Context, listId: String): Pair<String, String> {
    if (Repo.mine(c, listId).code == null) {
        Sync.publishFor(c, listId)
        ListLink.set(c, listId, false)
    }
    val d = Repo.mine(c, listId)
    return (d.code ?: throw CloudException("no code")) to (d.token ?: throw CloudException("no token"))
}

// ---- making, joining, leaving ----

private fun JSONObject.settings(budget: Double?, currency: String, day: LocalDate?, theme: String, message: String) =
    put("p_budget", budget ?: JSONObject.NULL).put("p_currency", currency).put("p_day", day?.toString() ?: JSONObject.NULL)
        .put("p_theme", theme).put("p_message", message.trim())

/** A new exchange: you organise it and join with your list [listId]. Returns its id. */
suspend fun Exchanges.create(
    c: Context, name: String, listId: String, budget: Double?, currency: String, day: LocalDate?, theme: String, message: String
): String {
    Friends.ensure(c)
    val (code, token) = listOnline(c, listId)
    val id = Sync.newKey()
    Cloud.rpc(
        "wl_gx_create", base(c).put("p_gx", id).put("p_link", Sync.newKey()).put("p_name", name.trim())
            .put("p_list", code).put("p_list_token", token).settings(budget, currency, day, theme, message)
    )
    refresh(c)
    return id
}

suspend fun Exchanges.preview(link: String): GxPreview? {
    val t = Cloud.rpc("wl_gx_info", JSONObject().put("p_link", link)).trim()
    if (t.isEmpty() || t == "null") return null
    val o = JSONObject(t)
    return GxPreview(
        o.optString("id"), o.optString("name"), o.optString("organiser"), o.optInt("count"), o.optString("theme", "xmas"),
        num(o, "budget"), o.optString("currency"), day(o.optString("day")), o.optBoolean("drawn")
    )
}

/** Join with link [link] or invite [gx], with your list [listId]. The exchange's id, or null when it doesn't work any more. */
suspend fun Exchanges.join(c: Context, link: String?, gx: String?, listId: String): String? {
    Friends.ensure(c)
    val (code, token) = listOnline(c, listId)
    val t = Cloud.rpc(
        "wl_gx_join", base(c).put("p_link", link ?: "").put("p_gx", gx ?: "").put("p_list", code).put("p_list_token", token)
    ).trim()
    if (t.isEmpty() || t == "null") return null
    val id = JSONObject(t).optString("id")
    refresh(c)
    return id
}

suspend fun Exchanges.setList(c: Context, gx: String, listId: String) {
    val (code, token) = listOnline(c, listId)
    Cloud.rpc("wl_gx_set_list", base(c).put("p_gx", gx).put("p_list", code).put("p_list_token", token))
    refresh(c)
}

suspend fun Exchanges.decline(c: Context, gx: String) {
    Cloud.rpc("wl_gx_decline", base(c).put("p_gx", gx))
    invites = invites.filter { it.gx != gx }
    refresh(c)
}

suspend fun Exchanges.leave(c: Context, gx: String, next: String?) {
    Cloud.rpc("wl_gx_leave", base(c).put("p_gx", gx).put("p_next", next ?: ""))
    list = list.filter { it.id != gx }
    // Done online: a reload that fails now doesn't make it look as if leaving failed (owner, 2.133).
    try { refresh(c) } catch (e: Exception) {}
}

suspend fun Exchanges.remove(c: Context, gx: String, member: String) {
    Cloud.rpc("wl_gx_remove", base(c).put("p_gx", gx).put("p_member", member))
    refresh(c)
}

suspend fun Exchanges.handover(c: Context, gx: String, next: String) {
    Cloud.rpc("wl_gx_handover", base(c).put("p_gx", gx).put("p_next", next))
    refresh(c)
}

suspend fun Exchanges.saveSettings(
    c: Context, gx: String, name: String, budget: Double?, currency: String, day: LocalDate?, theme: String, message: String
) {
    Cloud.rpc("wl_gx_settings", base(c).put("p_gx", gx).put("p_name", name.trim()).settings(budget, currency, day, theme, message))
    refresh(c)
}

suspend fun Exchanges.cancel(c: Context, gx: String) {
    Cloud.rpc("wl_gx_cancel", base(c).put("p_gx", gx))
    list = list.filter { it.id != gx }
    // Done online: a reload that fails now doesn't make it look as if cancelling failed (owner, 2.133: the page
    // said "Couldn't do that" and stayed on the cancelled exchange).
    try { refresh(c) } catch (e: Exception) {}
}

/** Invites friends [ids] (organiser): online, plus a chat message with the link. Returns how many failed. */
suspend fun Exchanges.invite(c: Context, x: Exchange, ids: List<String>): Int {
    var failed = 0
    for (id in ids) {
        try {
            Cloud.rpc("wl_gx_invite", base(c).put("p_gx", x.id).put("p_friend", id))
        } catch (e: Exception) {
            failed++
            continue
        }
        try {
            Chat.send(c, id, ChatContent(true, "exchange", x.id, x.name, url = link(x.link)))
        } catch (e: Exception) {
        }
    }
    try { refresh(c) } catch (e: Exception) {}
    return failed
}

suspend fun Exchanges.uninvite(c: Context, gx: String, id: String) {
    Cloud.rpc("wl_gx_uninvite", base(c).put("p_gx", gx).put("p_friend", id))
    refresh(c)
}

suspend fun Exchanges.addLate(c: Context, gx: String, member: String) {
    Cloud.rpc("wl_gx_add_late", base(c).put("p_gx", gx).put("p_member", member))
    refresh(c)
}
