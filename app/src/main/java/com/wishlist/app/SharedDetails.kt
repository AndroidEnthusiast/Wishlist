package com.wishlist.app

import android.content.Context

// A shared wishlist's name, note, occasion and categories (a part of [Shared] since the data rebuild, owner 19 November 2026; callers keep writing Shared.…).

fun Shared.setName(c: Context, name: String) {
    val l = current ?: return
    val before = content
    val after = before.copy(name = name)
    lists = lists.map { if (it.code == l.code) it.copy(name = name) else it }
    saveLists(c)
    write(c, l, before, after) { Cloud.shName(l.key, name) }
}

fun Shared.setAbout(c: Context, about: String) {
    val l = current ?: return
    val before = content
    val after = before.copy(about = about)
    write(c, l, before, after) { Cloud.shAbout(l.key, about) }
}

/** The occasion of the open gift list (null removes it); everyone on it sees it. */
fun Shared.setOccasion(c: Context, occ: Occasion?) {
    val l = current ?: return
    val before = content
    val after = before.copy(occ = occ)
    // Without the script of 5 November 2026 the online function doesn't exist yet: say so.
    val noScript: (Exception) -> String? = { e ->
        if ((e.message ?: "").contains("wl_sh_set_occasion")) tr("Occasions need an update in Supabase first: run supabase-shared.sql again.") else null
    }
    write(c, l, before, after, noScript) { Cloud.shOccasion(l.key, occ) }
}

/** The categories collab wishlist [code] has, as this phone last saw them. */
fun Shared.catsOf(c: Context, code: String): List<String> = if (active == code) content.cats else readCache(c, code)?.cats.orEmpty()

/** [name] is already cleaned and known to be new (Repo.addCat checks that). */
fun Shared.addCat(c: Context, name: String): String? {
    val l = current ?: return null
    val before = content
    val after = before.copy(cats = before.cats + name)
    write(c, l, before, after) { Cloud.shCatAdd(l.key, name) }
    return name
}

fun Shared.renameCat(c: Context, old: String, name: String): Boolean {
    val l = current ?: return false
    val before = content
    val after = before.copy(
        cats = before.cats.map { if (it == old) name else it },
        wishes = before.wishes.map { if (it.category == old) it.copy(category = name) else it }
    )
    // The online copy renames the category and moves every wish in it, including ones this phone hasn't seen yet.
    write(c, l, before, after) { Cloud.shCatRename(l.key, old, name) }
    return true
}

fun Shared.removeCat(c: Context, name: String) {
    val l = current ?: return
    val before = content
    val after = before.copy(
        cats = before.cats.filter { it != name },
        wishes = before.wishes.map { if (it.category == name) it.copy(category = "") else it }
    )
    write(c, l, before, after) { Cloud.shCatRemove(l.key, name) }
}
