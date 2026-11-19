package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * The open list's state holder (the rebuild, owner 19 November 2026; MAP "How a screen is built"): what the list
 * screen ([HomeScreen] in ListScreen.kt) shows besides the wishes themselves (the category, the search, which wishes
 * are selected, which dialog is open), and everything a tap there changes (Reserve, ♥ Love it, the view, the sort).
 * Which wishes show, in which order, is decided by [shownWishes] and [keptOrder], plain functions the checks test.
 * The data stays in Repo (your lists) and Shared (wishlists you make with others).
 */
@Stable
internal class ListModel(private val ctx: Context) {
    /** The chosen category ("All" for every wish). */
    var cat by mutableStateOf("All")
    var catMenu by mutableStateOf(false)
    /** "+ New category" from the category menu. */
    var catDlg by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    /** The wish whose form is open, and the wish whose price history is open. */
    var editing by mutableStateOf<Wish?>(null)
    var historyId by mutableStateOf<Long?>(null)
    /** On a shared wishlist: "Is it still being bought?" for someone else's reservation, and "Reserve it now?". */
    var askBought by mutableStateOf<Wish?>(null)
    var reserveNow by mutableStateOf<Wish?>(null)
    /** Selecting several wishes (owner, 7 and 12 November 2026): the ticked ones. */
    var picked by mutableStateOf(setOf<Long>())
    val selecting: Boolean get() = picked.isNotEmpty()

    fun toggle(w: Wish) { picked = if (w.id in picked) picked - w.id else picked + w.id }
    fun stopSearch() { searching = false; query = "" }

    // ---- what a tap changes ----
    /** Reserve (or with false let go of) a wish on a shared wishlist. */
    fun reserve(w: Wish, on: Boolean) = Shared.reserve(w, on)
    /** ♥ Love it on your own list. */
    fun toggleLove(w: Wish) = Repo.replace(ctx, w.copy(star = !w.star))
    /** Rows, Two per row or Large cards, for all your lists. */
    fun setView(v: String) = Repo.setView(ctx, v)
    /** This list's sort ("My order" included). */
    fun setSort(listKey: String, s: String) = ListOrder.setSort(ctx, listKey, s)
}

/** The chosen category, or "All" when it no longer exists on this list. */
internal fun shownCategory(cat: String, cats: List<String>): String = if (cat == "All" || cat in cats) cat else "All"

/**
 * The wishes on screen: in the chosen category and matching the search, in the list's sort ("My order" keeps the
 * order you dragged them into, [order]).
 */
internal fun shownWishes(source: List<Wish>, cat: String, query: String, sortMode: String, order: List<Long>): List<Wish> {
    val shown = source.filter { (cat == "All" || it.category == cat) && matchesQuery(it, query) }
    return if (sortMode == ListOrder.MINE) sortByOrder(shown, order) else sortWishes(shown, sortMode)
}

/**
 * The whole list's order after a drag (owner, 16 November 2026): [dragged] is the new order of the wishes on screen;
 * wishes hidden by a category or a search ([all] has every wish in the list's sort) keep their place around them.
 */
internal fun keptOrder(dragged: List<Long>, onScreen: List<Long>, all: List<Long>): List<Long> {
    val shown = dragged.toSet()
    val rest = onScreen.filter { it !in shown }
    if (shown.size + rest.size == all.size) return dragged + rest
    val queue = dragged.iterator()
    return all.map { if (it in shown) queue.next() else it }
}
