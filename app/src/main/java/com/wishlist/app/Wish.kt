package com.wishlist.app

import org.json.JSONObject
import java.text.NumberFormat
import java.util.Currency

// Wishes as the app holds them and as they are stored (owner's Rule 1 in CLAUDE.md). Moved here from Data.kt in the
// data rebuild (owner, 19 November 2026).

// A new field here must ALSO be added to wishToJson / wishFromJson below and to changedFields / applyFields in
// Shared.kt (and to the web pages), or it won't be saved when someone edits a shared wish. (Like "by", the bin fields
// and "chk" are not edited in the form: only moving to the bin, restoring and the price check write them.)
/**
 * What a wish is called on screen: its name, or a grey-worthy "Unnamed wish" (owner, 7 November 2026: a wish without
 * a link may be saved without a name; it needs a name, a photo or a link).
 */
val Wish.label: String get() = title.ifBlank { tr("Unnamed wish") }

data class Wish(
    val id: Long,
    val url: String,
    val title: String,
    val desc: String,
    val image: String,
    val price: Double?,
    val oldPrice: Double?,
    val currency: String,
    val category: String,
    val got: Boolean = false,
    /** "Most wanted": shown with a star, so givers know where to look first. */
    val star: Boolean = false,
    /** A note for givers: colour, details, anything they should know. */
    val note: String = "",
    /** One of SIZES ("XS" to "XL"), or empty for no size. */
    val size: String = "",
    /** On a shared wishlist: the name of the person who added it. Empty on My wishlist. */
    val by: String = "",
    /** In the bin since this moment (milliseconds), or 0 when it is on the list. See Bin.kt. */
    val bin: Long = 0L,
    /** On a shared wishlist: the name of the person who moved it to the bin. */
    val binBy: String = "",
    /** On a shared wishlist: when a phone last asked the shop for its price (ms), so the other phones can skip it. */
    val checked: Long = 0L,
    /**
     * "Private wish" (My wishlist only): it stays on this phone and never goes online, so friends never see it. It is
     * stored as "priv" on the phone only: [Cloud.pushList] leaves such wishes out, and shared lists never have them.
     */
    val priv: Boolean = false,
    /** How many the person would like (owner, 2.128); 1 = not shown. Stored as "qty" only when more than 1. */
    val qty: Int = 1,
    /** "exact" ("Exactly this"), "similar" ("Similar is fine") or "" (owner, 2.128). Stored as "exact". */
    val exact: String = "",
    /**
     * Received after the occasion (owner, 2.128, "What did you get?"): when it was ticked (ms), else 0. Such a wish
     * is in the "Received gifts" archive, not on the list; it stays online with "bin" too, so givers' phones learn
     * it was received and say thank you instead of "pick another gift". Own lists only (not on gift lists).
     */
    val recv: Long = 0L
)

/** A wishlist that someone else shared with you. */
data class Friend(val code: String, val name: String)

fun wishToJson(w: Wish): JSONObject = JSONObject().apply {
    put("id", w.id); put("url", w.url); put("title", w.title)
    put("desc", w.desc); put("image", w.image)
    put("price", w.price ?: JSONObject.NULL)
    put("old", w.oldPrice ?: JSONObject.NULL)
    put("cur", w.currency); put("cat", w.category); put("got", w.got)
    if (w.star) put("star", true)
    if (w.note.isNotBlank()) put("note", w.note)
    if (w.size.isNotBlank()) put("size", w.size)
    if (w.by.isNotBlank()) put("by", w.by)
    if (w.bin > 0L) put("bin", w.bin)
    if (w.binBy.isNotBlank()) put("binBy", w.binBy)
    if (w.checked > 0L) put("chk", w.checked)
    if (w.priv) put("priv", true)
    if (w.qty > 1) put("qty", w.qty)
    if (w.exact.isNotBlank()) put("exact", w.exact)
    if (w.recv > 0L) put("recv", w.recv)
}

fun wishFromJson(o: JSONObject): Wish = Wish(
    o.getLong("id"), o.getString("url"), o.getString("title"),
    o.optString("desc"), o.optString("image"),
    if (o.isNull("price")) null else o.getDouble("price"),
    if (o.isNull("old")) null else o.getDouble("old"),
    o.optString("cur", "USD"), o.optString("cat", ""), o.optBoolean("got"),
    o.optBoolean("star"), o.optString("note", ""), o.optString("size", ""), o.optString("by", ""),
    o.optLong("bin", 0L), o.optString("binBy", ""), o.optLong("chk", 0L), o.optBoolean("priv"),
    o.optInt("qty", 1).coerceIn(1, 99), o.optString("exact", "").takeIf { it == "exact" || it == "similar" }.orEmpty(),
    o.optLong("recv", 0L)
)

fun money(p: Double, cur: String): String = try {
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(cur) }.format(p)
} catch (e: Exception) {
    "%.2f".format(p)
}
