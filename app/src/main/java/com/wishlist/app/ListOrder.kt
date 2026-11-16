package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * "My order" and a sort per list (owner, 16 November 2026). Drag a wish (hold and move) on any list you see in the
 * app, your own or a gift list: the list switches to "My order" and keeps the order you made. Only on this phone (in
 * the settings store, so an Export and the recovery key's copy bring it back); friends and the others on a gift list
 * keep their own sort. Each list remembers its own sort; a list never sorted uses the last sort you chose anywhere
 * ([Repo.sort]). New wishes go at the top of "My order"; owned wishes stay at the bottom, as in every sort.
 */
object ListOrder {
    const val MINE = "mine"
    private const val PREFS = "wishlist"

    /** Changes when a sort or an order changes, so screens that read them redraw. */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The key of the open list: a gift list by its code, your own by its id. */
    fun openKey(): String = Shared.active?.let { "sh_$it" } ?: "mine_${Repo.openMine}"

    fun sortFor(c: Context, key: String): String {
        @Suppress("UNUSED_EXPRESSION") version
        val s = prefs(c).getString("sort_$key", null)
        return if (s == MINE || SORT_OPTIONS.any { it.first == s }) s!! else Repo.sort
    }

    fun setSort(c: Context, key: String, mode: String) {
        prefs(c).edit().putString("sort_$key", mode).apply()
        // Lists you never sorted follow your latest choice ("My order" only makes sense where you dragged).
        if (mode != MINE) Repo.setSort(c, mode)
        version++
    }

    fun order(c: Context, key: String): List<Long> {
        @Suppress("UNUSED_EXPRESSION") version
        return prefs(c).getString("order_$key", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    }

    /** Saves [ids] as the list's order and switches it to "My order". */
    fun save(c: Context, key: String, ids: List<Long>) {
        prefs(c).edit().putString("order_$key", ids.joinToString(",")).putString("sort_$key", MINE).apply()
        version++
    }
}

/**
 * The wishes in "My order": wishes that aren't in [order] yet (new ones) first, newest first, then the rest as you
 * placed them. Owned wishes at the bottom, as in every sort ([sortWishes]).
 */
fun sortByOrder(list: List<Wish>, order: List<Long>): List<Wish> {
    val at = order.withIndex().associate { (i, id) -> id to i }
    val fresh = list.filter { it.id !in at }.sortedByDescending { it.id }
    val placed = list.filter { it.id in at }.sortedBy { at[it.id] }
    return (fresh + placed).sortedBy { it.got }
}

/** Moves the wish [id] to where [target] is in [ids] (the order on screen while dragging). */
fun moveInOrder(ids: List<Long>, id: Long, target: Long): List<Long> {
    val from = ids.indexOf(id)
    val to = ids.indexOf(target)
    if (from < 0 || to < 0 || from == to) return ids
    val m = ids.toMutableList()
    m.removeAt(from)
    m.add(to, id)
    return m
}
