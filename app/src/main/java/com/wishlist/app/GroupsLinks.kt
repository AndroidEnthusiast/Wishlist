package com.wishlist.app

import android.content.Context
import android.content.Intent
import org.json.JSONObject

// Group links, sharing them, and a browser member moving into the app (a part of [Groups] since the data rebuild,
// owner 19 November 2026; callers keep writing Groups.…).

private val LINK = Regex("[?&]G=([A-HJ-NP-Z2-9]{20})(?![A-Z0-9])")
private val ME = Regex("ME=([A-HJ-NP-Z2-9]{20})\\.([A-HJ-NP-Z2-9]{20})(?:\\.([A-Za-z0-9-]{1,64}))?")


/** The group link, through the page group/ (its link preview), which opens the group page. */
fun Groups.link(key: String): String = Config.SHARE_URL.trimEnd('/') + "/group/?g=" + key

fun Groups.share(c: Context, g: Group) {
    // A name first (owner, 2.135): friends see who shares it.
    if (!NameGate.hasName()) { NameGate.ensure { share(c, g) }; return }
    val text = tr("Join my wishlist group “%1\$s”: %2\$s", g.name, link(g.link))
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    c.startActivity(Intent.createChooser(i, tr("Share group link")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The group link key in text (a web link or wishlist://group?g=…), or null. */
fun Groups.parseLink(s: String): String? = LINK.find(s.uppercase())?.groupValues?.get(1)

/** A browser member's "My link" (…#me=ID.TOKEN.DEVICE): their profile id, token and device id, or null. */
fun Groups.parseMe(s: String): Triple<String, String, String>? {
    val m = ME.find(s.replace("me=", "ME=")) ?: return null
    return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
}

// ---- a browser member moves to the app ----

/**
 * Takes over a browser member's wishlist and groups ("My link" opened in the app; owner: now, not later).
 * Their list becomes one of your own lists (same code, so reservations stay), "Only me" outside the groups; their
 * places in groups (also as organiser) and the reservations made in that browser move to this phone. Returns the
 * new list's id (null when they had no list).
 */
suspend fun Groups.takeover(c: Context, webId: String, webToken: String, webDevice: String): String? {
    Friends.ensure(c)
    val me = JSONObject(Cloud.rpc("wl_gr_web_me", JSONObject().put("p_id", webId).put("p_token", webToken)))
    if (!me.optBoolean("web")) throw CloudException("not a browser profile")
    val code = me.optString("list")
    val token = me.optString("list_token")
    val name = me.optString("name")
    // Read the list before the browser profile goes.
    val remote = if (code.isNotEmpty()) Cloud.fetchList(code) else null
    val p = prefs(c)
    // Their gift exchanges move first (2.129): the browser profile is deleted by wl_gr_takeover.
    Exchanges.takeover(c, webId, webToken)
    Cloud.rpc(
        "wl_gr_takeover", JSONObject().put("p_web", webId).put("p_web_token", webToken).put("p_web_device", webDevice)
            .put("p_id", p.getString("pf_id", "")).put("p_token", p.getString("pf_token", "")).put("p_device", Repo.deviceId)
    )
    // Your name on the app, when you hadn't given one yet.
    if (Shared.lastName.isBlank() && name.isNotBlank()) {
        Shared.setAppName(c, name)
        try { Friends.nameChanged(c) } catch (e: Exception) {}
    }
    var id: String? = null
    if (remote != null && token.isNotEmpty()) {
        val newId = Repo.createMine(c, remote.name, remote.about)
        Repo.setShareFor(c, newId, code, token)
        val wishes = remote.wishes.filter { it.id != ABOUT_ITEM_ID }
        Repo.updateMine(c, newId) { wishes }
        // Only the groups see it (its friends' link off); "Visible to" can open it to friends later.
        try { ListLink.set(c, newId, false) } catch (e: Exception) {}
        id = newId
    }
    refresh(c)
    return id
}
