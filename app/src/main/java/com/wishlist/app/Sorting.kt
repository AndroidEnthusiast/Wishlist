package com.wishlist.app

import java.net.URI

/** Longest note for friends (shown at the top of the shared list). */
const val ABOUT_MAX = 300

/** The ways to order the wishes: key to the name shown in the menu. "new" is the normal order. */
val SORT_OPTIONS: List<Pair<String, String>> = listOf(
    "new" to "Newest first",
    "star" to "Most wanted first",
    "low" to "Price: low to high",
    "high" to "Price: high to low",
    "az" to "Name A–Z"
)

/** Short names for the sort button when a sort is active. */
fun sortShortName(mode: String): String = when (mode) {
    "star" -> "Most wanted"
    "low" -> "Price: low"
    "high" -> "Price: high"
    "az" -> "A–Z"
    else -> "Newest"
}

/** Orders [list] by [mode]. Equal wishes keep their order. Wishes without a price go last in the price orders. */
fun sortWishes(list: List<Wish>, mode: String): List<Wish> = when (mode) {
    "star" -> list.sortedByDescending { it.star && !it.got }
    "low" -> list.sortedWith(compareBy<Wish>({ it.price == null }, { it.price }))
    "high" -> list.sortedWith(compareBy<Wish>({ it.price == null }, { -(it.price ?: 0.0) }))
    "az" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    else -> list.sortedByDescending { it.id }
}

private fun hostOfUrl(url: String): String = try {
    URI(url.trim()).host?.removePrefix("www.") ?: ""
} catch (e: Exception) {
    ""
}

/** True when every word of [query] appears in the wish's title, description, note, category or shop name. */
fun matchesQuery(w: Wish, query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = listOf(w.title, w.desc, w.note, w.category, hostOfUrl(w.url)).joinToString(" ").lowercase()
    return words.all { it in text }
}
