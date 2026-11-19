package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


/** A category name without line breaks or doubled spaces. */
fun Repo.cleanCatName(name: String): String = name.replace(Regex("\\s+"), " ").trim()

/** Adds a category and returns its name. If one with that name exists (in any capitals) that one is returned. Null for an empty name or "All". */
fun Repo.addCat(c: Context, name: String): String? = synchronized(this) {
    val n = cleanCatName(name)
    if (n.isEmpty() || n.equals("All", ignoreCase = true)) return null
    val existing = cats.firstOrNull { it.equals(n, ignoreCase = true) }
    if (existing != null) return existing
    if (Shared.active != null) return Shared.addCat(c, n)
    OwnLists.myCats = myCats + n
    commit(c)
    // Every list of your own gets it (2.95).
    otherLists(c) { d -> if (d.cats.any { it.equals(n, ignoreCase = true) }) d else d.copy(cats = d.cats + n) }
    return n
}

/** Renames a category. Wishes in it move along. False if the new name is empty, "All" or taken by another category. */
fun Repo.renameCat(c: Context, old: String, name: String): Boolean = synchronized(this) {
    val n = cleanCatName(name)
    if (old !in cats || n.isEmpty() || n.equals("All", ignoreCase = true)) return false
    if (cats.any { it != old && it.equals(n, ignoreCase = true) }) return false
    if (n == old) return true
    if (Shared.active != null) return Shared.renameCat(c, old, n)
    OwnLists.myCats = myCats.map { if (it == old) n else it }
    OwnLists.myWishes = myWishes.map { if (it.category == old) it.copy(category = n) else it }
    OwnBin.myBin = myBin.map { if (it.category == old) it.copy(category = n) else it }
    commit(c)
    // On all your own lists (2.95).
    otherLists(c) { d ->
        d.copy(cats = d.cats.map { if (it == old) n else it }, wishes = d.wishes.map { if (it.category == old) it.copy(category = n) else it })
    }
    return true
}

/** Removes a category. Wishes that were in it stay, just without a category. */
fun Repo.removeCat(c: Context, name: String): Unit = synchronized(this) {
    if (Shared.active != null) {
        Shared.removeCat(c, name)
        return
    }
    OwnLists.myCats = myCats.filter { it != name }
    OwnLists.myWishes = myWishes.map { if (it.category == name) it.copy(category = "") else it }
    OwnBin.myBin = myBin.map { if (it.category == name) it.copy(category = "") else it }
    commit(c)
    // Gone from all your own lists; their wishes stay, without a category (2.95).
    otherLists(c) { d ->
        d.copy(cats = d.cats.filter { it != name }, wishes = d.wishes.map { if (it.category == name) it.copy(category = "") else it })
    }
}

/** How many wishes on how many of your own lists are in category [name] (for the delete question). */
fun Repo.catUse(c: Context, name: String): Pair<Int, Int> {
    val per = allMine(c).map { d -> d.wishes.count { it.category == name } }.filter { it > 0 }
    return per.sum() to per.size
}
