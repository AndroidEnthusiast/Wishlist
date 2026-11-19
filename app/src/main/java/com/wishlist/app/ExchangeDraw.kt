package com.wishlist.app

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

// Rules, the draw, opening your name, the reveal, the anonymous chats, links (a part of [Exchanges] since the data rebuild, owner 19 November 2026; callers keep writing Exchanges.…).


private val LINK = Regex("[?&]X=([A-HJ-NP-Z2-9]{20})(?![A-Z0-9])")

// ---- rules and the draw ----

/** Adds a rule. Null when saved; otherwise who would be stuck (the rule is not saved). */
suspend fun Exchanges.addRule(c: Context, gx: String, giver: String, receiver: String, both: Boolean): GxStuck? {
    val o = JSONObject(Cloud.rpc("wl_gx_excl_add", base(c).put("p_gx", gx).put("p_giver", giver).put("p_receiver", receiver).put("p_both", both)))
    refresh(c)
    if (o.optBoolean("ok")) return null
    val s = o.optJSONArray("stuck") ?: JSONArray()
    return GxStuck((0 until s.length()).map { s.getString(it) }, o.optInt("left"))
}

suspend fun Exchanges.removeRule(c: Context, gx: String, giver: String, receiver: String, both: Boolean) {
    Cloud.rpc("wl_gx_excl_del", base(c).put("p_gx", gx).put("p_giver", giver).put("p_receiver", receiver).put("p_both", both))
    refresh(c)
}

suspend fun Exchanges.draw(c: Context, gx: String, without: Boolean): Exchanges.DrawResult {
    val o = JSONObject(Cloud.rpc("wl_gx_draw", base(c).put("p_gx", gx).put("p_without", without)))
    refresh(c)
    val pend = o.optJSONArray("pending")
    val stuck = o.optJSONArray("stuck")
    return Exchanges.DrawResult(
        o.optBoolean("ok"), o.optBoolean("few"),
        pend?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        stuck?.let { a -> GxStuck((0 until a.length()).map { a.getString(it) }, o.optInt("left")) }
    )
}

/** You open your name (the reveal moment). */
suspend fun Exchanges.open(c: Context, gx: String): Exchange? {
    val t = Cloud.rpc("wl_gx_open", base(c).put("p_gx", gx)).trim()
    refresh(c)
    return if (t.isEmpty() || t == "null") null else parseOne(JSONObject(t))
}

/** Your giftee's wishlist (null before you opened your name, or when they have none). */
suspend fun Exchanges.gifteeList(c: Context, gx: String): RemoteList? {
    val t = Cloud.rpc("wl_gx_list", base(c).put("p_gx", gx)).trim()
    if (t.isEmpty() || t == "null") return null
    return Cloud.parseRemote(JSONObject(t))
}

suspend fun Exchanges.reveal(c: Context, gx: String): Boolean {
    val ok = Cloud.rpc("wl_gx_reveal", base(c).put("p_gx", gx)).trim() == "true"
    refresh(c)
    return ok
}

// ---- the anonymous chats ("giftee" or "santa") ----

suspend fun Exchanges.chat(c: Context, gx: String, side: String, after: Long): List<GxMsg> {
    val a = JSONArray(Cloud.rpc("wl_gx_chat", base(c).put("p_gx", gx).put("p_side", side).put("p_after", after)))
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        GxMsg(o.getLong("id"), o.optBoolean("mine"), o.optString("body"), o.optString("at"), o.optBoolean("read"))
    }
}

/** Sends a message. False when that chat isn't open (any more). */
suspend fun Exchanges.send(c: Context, gx: String, side: String, body: String): Boolean =
    (Cloud.rpc("wl_gx_send", base(c).put("p_gx", gx).put("p_side", side).put("p_body", body)).trim().toLongOrNull() ?: 0L) > 0L

suspend fun Exchanges.seen(c: Context, gx: String, side: String, upto: Long) {
    Cloud.rpc("wl_gx_chat_seen", base(c).put("p_gx", gx).put("p_side", side).put("p_upto", upto))
}

/** Before a browser member's profile is taken over by the app (Groups.takeover): their exchanges move too. */
suspend fun Exchanges.takeover(c: Context, webId: String, webToken: String) {
    val p = prefs(c)
    try {
        Cloud.rpc(
            "wl_gx_takeover", JSONObject().put("p_web", webId).put("p_web_token", webToken)
                .put("p_id", p.getString("pf_id", "")).put("p_token", p.getString("pf_token", ""))
        )
    } catch (e: CloudException) {
        // Before supabase-exchange.sql there is nothing to move.
    }
}

// ---- links ----

/** The exchange link, through the page exchange/ (its link preview), which opens the exchange page. */
fun Exchanges.link(key: String): String = Config.SHARE_URL.trimEnd('/') + "/exchange/?x=" + key

fun Exchanges.share(c: Context, x: Exchange) {
    // A name first (owner, 2.135): friends see who shares it.
    if (!NameGate.hasName()) { NameGate.ensure { share(c, x) }; return }
    val text = tr("Join my gift exchange “%1\$s”: %2\$s", x.name, link(x.link))
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    c.startActivity(Intent.createChooser(i, tr("Share exchange link")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The exchange link key in text (a web link or wishlist://exchange?x=…), or null. */
fun Exchanges.parseLink(s: String): String? = LINK.find(s.uppercase())?.groupValues?.get(1)
