package com.wishlist.app

// What wishes add up to (Home's Total value, a list's cover). No screens here, so it is checked by plain JUnit
// (app/src/test/.../TotalsTest.kt).

/**
 * What all wishes with a price on a list add up to, for example "€245.50". Wishes in different currencies are added
 * up per currency ("€120.00 + $30.00"). Null when no wish has a price.
 */
fun totalValue(wishes: List<Wish>): String? {
    val sums = wishes.filter { it.price != null }.groupBy { it.currency }
        .map { (cur, ws) -> cur to ws.sumOf { it.price ?: 0.0 } }
    if (sums.isEmpty()) return null
    return sums.sortedByDescending { it.second }.joinToString(" + ") { (cur, sum) -> money(sum, cur) }
}

/**
 * Home's "Total value" (owner, 7 November 2026): the wishes your own wishlists still wish for (not owned, a 2× wish
 * twice, private wishes too, a copied wish once; collab wishlists are not in [ws]). Amounts in other currencies can't
 * be added up without exchange rates: the tile shows the currency with the biggest amount and a "+" when there are
 * others. No prices at all: 0 in your currency.
 */
internal fun homeTotal(ws: List<Wish>): String {
    val sums = ws.filter { !it.got && it.bin == 0L && it.price != null }.groupBy { it.currency }
        .map { (cur, l) -> cur to l.sumOf { (it.price ?: 0.0) * it.qty.coerceAtLeast(1) } }
        .sortedByDescending { it.second }
    // With cents (owner, 7 November 2026).
    if (sums.isEmpty()) return money(0.0, localCurrency())
    return money(sums[0].second, sums[0].first) + if (sums.size > 1) "+" else ""
}

/** An amount without cents ("€1,245"), so it fits a small tile. */
internal fun wholeMoney(p: Double, cur: String): String = try {
    java.text.NumberFormat.getCurrencyInstance().apply {
        currency = java.util.Currency.getInstance(cur); maximumFractionDigits = 0; minimumFractionDigits = 0
    }.format(Math.round(p))
} catch (e: Exception) {
    Math.round(p).toString()
}
