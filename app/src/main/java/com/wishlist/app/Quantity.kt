package com.wishlist.app

import androidx.compose.runtime.mutableStateMapOf

/*
 * Quantity (owner, 2.128): a wish can be wanted more than once ("2×", the wish's qty). Givers reserve how many they'll
 * buy ("How many will you buy?"); the label says "1 of 2 reserved" until all are taken, then "Reserved"; your own
 * part shows as "You reserved 1". Older apps reserve all that is still free (supabase-setup.sql, wl_reserve_item).
 */

/** How many of a wish this phone reserved ([my]), how many all givers reserved ([n]), and whether none is left. */
data class ResCount(val my: Int, val n: Int, val full: Boolean)

object ResCounts {
    private val map = mutableStateMapOf<String, ResCount>()

    fun put(code: String, counts: Map<Long, ResCount>) {
        map.keys.filter { it.startsWith("$code:") }.forEach { map.remove(it) }
        counts.forEach { (id, c) -> map["$code:$id"] = c }
    }

    fun of(code: String, id: Long): ResCount? = map["$code:$id"]

    /** How many of [w] this phone may still reserve (its own part counts as free for itself). */
    fun free(code: String, w: Wish): Int = freeToReserve(w, of(code, w.id))
}

/** How many of [w] a giver may still reserve, given the reservations [c] (null: none known). Their own part counts as free. */
fun freeToReserve(w: Wish, c: ResCount?): Int = if (c == null) w.qty else (w.qty - (c.n - c.my)).coerceAtLeast(0)

/**
 * The label of a reserved wish for givers: "You reserved 1", "1 of 2 reserved" or "Reserved" for a 2× wish, else
 * "Reserved by you" / "Reserved". Null when it is free.
 */
fun reserveLabel(code: String, w: Wish, r: Boolean?): String? = reserveLabelOf(w, ResCounts.of(code, w.id), r)

/** [reserveLabel] without the stored counts: [c] the reservations of [w], [r] true = reserved by you, false = by someone else. */
fun reserveLabelOf(w: Wish, c: ResCount?, r: Boolean?): String? {
    if (w.qty > 1 && c != null) {
        if (c.my > 0) return tr("You reserved %d", c.my)
        return if (c.full) tr("Reserved") else tr("%1\$d of %2\$d reserved", c.n, w.qty)
    }
    return when (r) {
        true -> tr("Reserved by you")
        false -> tr("Reserved")
        else -> null
    }
}

/**
 * What a card's reservation button shows (owner, 23 November 2026: the button says it, no extra line above it):
 * "reserve" (free), "mine" ("Reserved by you" or "You reserved 2", with a lock), "taken" ("Reserved" with a lock, the
 * card darkened), or null (owned: no button). [c]: the counts of a 2× wish, [r]: true = you, false = someone else.
 */
fun reserveState(w: Wish, c: ResCount?, r: Boolean?): String? = when {
    w.got -> null
    w.qty > 1 && c != null -> if (c.my > 0) "mine" else if (c.full) "taken" else "reserve"
    r == true -> "mine"
    r == false -> "taken"
    else -> "reserve"
}

/** The words on the reservation button for [reserveState]. */
fun reserveButtonText(w: Wish, c: ResCount?, state: String): String = when (state) {
    "mine" -> if (w.qty > 1 && c != null && c.my > 0) tr("You reserved %d", c.my) else tr("Reserved by you")
    "taken" -> tr("Reserved")
    else -> tr("Reserve")
}

/**
 * The line under the photo about reservations: only "1 of 3 reserved" while some of a 2× wish are still free and you
 * have none (everything else is on the button, owner 23 November 2026).
 */
fun reserveLine(w: Wish, c: ResCount?): String? =
    if (w.qty > 1 && c != null && c.my == 0 && !c.full && c.n > 0) tr("%1\$d of %2\$d reserved", c.n, w.qty) else null

/** On a list where you reserve: wishes someone else reserved go to the bottom (owner, 23 November 2026), owned last. */
fun reservedLast(ws: List<Wish>, state: (Wish) -> String?): List<Wish> =
    ws.sortedBy { w -> when { w.got -> 2; state(w) == "taken" -> 1; else -> 0 } }

