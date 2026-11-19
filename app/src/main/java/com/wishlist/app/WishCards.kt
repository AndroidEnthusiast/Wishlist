package com.wishlist.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// A wish on a list: the three card styles (Rows, Two per row, Large), its price line and labels. Moved out of
// MainActivity.kt in 2.163.

// Split in the rebuild (owner, 19 November 2026): the cards are CompactCard.kt (Two per row), WishCard.kt (Large
// cards) and RowCard.kt (Rows), with WishCardParts.kt and WishPriceLine.kt; this file keeps what they share.
private val Green = Color(0xFF0B7A3E)
/** A price that went up: the opposite of the green of a drop. */
private val Rise = Color(0xFFBA1A1A)

/** True when a price check found a higher price than before (old price lower than the price now). */
fun priceRose(w: Wish): Boolean {
    val p = w.price
    val o = w.oldPrice
    return p != null && o != null && p > o + 0.005
}

internal fun host(url: String): String = try {
    java.net.URI(url).host.removePrefix("www.")
} catch (e: Exception) {
    url
}

/** Reserving a wish on a shared list. [state]: true = reserved on this phone, false = by someone else, null = still free. */
class Reserving(val state: Boolean?, val label: String? = null, val set: (Boolean) -> Unit)

/**
 * The Reserve button under a Two per row card (owner, 12 November 2026): "reserve" when it is free, "unreserve" when
 * you reserved it, null when it is owned, reserved by someone else, or not a list you reserve on.
 */
fun reserveButton(w: Wish, reserve: Reserving?): String? = when {
    reserve == null || w.got -> null
    reserve.state == null -> "reserve"
    reserve.state == true -> "unreserve"
    else -> null
}

/**
 * "2×" (owner, 2.128), or "". "Exactly this" / "Similar is fine" is no longer shown anywhere (owner, 16 November
 * 2026: removed from the form and the cards); the saved value stays in the data, so old app versions keep working.
 */
fun wishTags(w: Wish): String = if (w.qty > 1) "${w.qty}×" else ""

/** The label for a reservation on a shared list, or null when the wish is still free. */
fun reservedLabel(r: Boolean?): String? = when (r) {
    true -> tr("Reserved by you")
    false -> tr("Reserved")
    else -> null
}
