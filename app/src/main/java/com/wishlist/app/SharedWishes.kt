package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

// Saving the wishes of a shared wishlist one at a time, only the fields that changed (Rule 3), the bin and Undo,
// moving wishes across, pictures (a part of [Shared] since the data rebuild, owner 19 November 2026; callers keep writing Shared.…).

internal fun Shared.photoNameOf(code: String, url: String): String? {
    val prefix = Cloud.photoUrl(code, "")
    if (!url.startsWith(prefix)) return null
    return url.removePrefix(prefix).takeIf { it.isNotEmpty() && !it.contains('/') }
}

suspend fun Shared.dropPhoto(l: SharedList, url: String) {
    val n = photoNameOf(l.code, url) ?: return
    try {
        Cloud.deletePhoto(l.code, l.key, n, "x-edit-key")
    } catch (e: Exception) {
    }
}

/** The names (as stored online) of the fields that differ between two versions of a wish. ("by" is only set when a wish is made.) */
internal fun Shared.changedFields(a: Wish, b: Wish): List<String> {
    val f = ArrayList<String>()
    if (a.url != b.url) f.add("url")
    if (a.title != b.title) f.add("title")
    if (a.desc != b.desc) f.add("desc")
    if (a.image != b.image) f.add("image")
    if (a.price != b.price) f.add("price")
    if (a.oldPrice != b.oldPrice) f.add("old")
    if (a.currency != b.currency) f.add("cur")
    if (a.category != b.category) f.add("cat")
    if (a.got != b.got) f.add("got")
    if (a.star != b.star) f.add("star")
    if (a.note != b.note) f.add("note")
    if (a.size != b.size) f.add("size")
    if (a.qty != b.qty) f.add("qty")
    if (a.exact != b.exact) f.add("exact")
    return f
}

/** [into] with only the given fields taken from [from]. */
internal fun Shared.applyFields(into: Wish, from: Wish, fields: List<String>): Wish {
    var w = into
    for (f in fields) {
        w = when (f) {
            "url" -> w.copy(url = from.url)
            "title" -> w.copy(title = from.title)
            "desc" -> w.copy(desc = from.desc)
            "image" -> w.copy(image = from.image)
            "price" -> w.copy(price = from.price)
            "old" -> w.copy(oldPrice = from.oldPrice)
            "cur" -> w.copy(currency = from.currency)
            "cat" -> w.copy(category = from.category)
            "got" -> w.copy(got = from.got)
            "star" -> w.copy(star = from.star)
            "note" -> w.copy(note = from.note)
            "size" -> w.copy(size = from.size)
            "qty" -> w.copy(qty = from.qty)
            "exact" -> w.copy(exact = from.exact)
            else -> w
        }
    }
    return w
}

/**
 * Adds a wish, or saves a changed one. [base] is the version the edit form was opened on (leave it out for quick
 * changes like the star). Only the fields that differ from it are written online, on top of what is online now,
 * so a change someone else made to another field of the same wish stays. A picture picked on this phone is uploaded first.
 */
fun Shared.put(c: Context, w: Wish, base: Wish? = null) {
    val l = current ?: return
    val before = content
    val old = before.wishes.firstOrNull { it.id == w.id }
    val mine = if (old == null && w.by.isBlank()) w.copy(by = l.me) else w
    // null = a new wish here: all of it is sent.
    val fields = if (old != null) changedFields(base ?: old, mine) else null
    if (fields != null && fields.isEmpty()) return
    val was = (base ?: old)?.image
    val shown = if (old != null && fields != null) applyFields(old, mine, fields) else mine
    val after = before.copy(
        wishes = if (old != null) before.wishes.map { if (it.id == w.id) shown else it } else listOf(mine) + before.wishes
    )
    write(c, l, before, after) {
        var out = mine
        val sendsImage = fields == null || "image" in fields
        if (sendsImage && ImageStore.isLocal(out.image)) {
            val name = out.image.substringAfterLast('/')
            val bytes = ImageStore.shareCopy(out.image)
            out = if (bytes != null) {
                try {
                    Cloud.uploadPhoto(l.code, l.key, name, bytes, "x-edit-key")
                    out.copy(image = Cloud.photoUrl(l.code, name))
                } catch (e: Exception) {
                    notice = tr("Saved without the picture: it couldn’t be uploaded.")
                    out.copy(image = "")
                }
            } else out.copy(image = "")
        }
        Cloud.shPut(l.key, out, fields)
        if (out.image != mine.image) {
            // The picture now lives online. Show that one here too and free the copy on this phone.
            ImageStore.delete(c, mine.image)
            if (active == l.code) {
                content = content.copy(
                    wishes = content.wishes.map { if (it.id == out.id && it.image == mine.image) out else it }
                )
            }
        }
        // A picture that this save replaced or removed is deleted online too (never one someone else just set).
        if (fields != null && "image" in fields && was != null && was != out.image) dropPhoto(l, was)
    }
}

// ---- moving to and from collab wishlists (MoveAcross.kt, owner 11 November 2026) ----

/**
 * Puts a new wish on collab wishlist [l] (open or not) and waits for the server: throws when it can't be reached,
 * and nothing has changed then. A picture stored on this phone is uploaded to that list first (the file stays,
 * for Undo). Returns the wish as it is online.
 */
suspend fun Shared.addTo(c: Context, l: SharedList, w: Wish): Wish {
    var out = w
    if (ImageStore.isLocal(out.image)) {
        val name = out.image.substringAfterLast('/')
        val bytes = ImageStore.shareCopy(out.image)
        out = if (bytes != null) try {
            Cloud.uploadPhoto(l.code, l.key, name, bytes, "x-edit-key")
            out.copy(image = Cloud.photoUrl(l.code, name))
        } catch (e: Exception) {
            out.copy(image = "")
        } else out.copy(image = "")
    }
    lock.withLock { Cloud.shPut(l.key, out, null) }
    if (active == l.code) {
        if (content.wishes.none { it.id == out.id }) content = content.copy(wishes = listOf(out) + content.wishes)
        stamp.incrementAndGet()
        writeCache(c, l.code, content)
    } else readCache(c, l.code)?.let { ct ->
        // The phone's copy (the list's block on Home shows its newest wishes).
        if (ct.wishes.none { it.id == out.id }) writeCache(c, l.code, ct.copy(wishes = listOf(out) + ct.wishes))
    }
    return out
}

/**
 * A wish leaves collab wishlist [l] for another list: into [l]'s bin (so the others can put it back for 30 days),
 * waiting for the server. Throws when it can't be reached; nothing has changed then.
 */
suspend fun Shared.sendAway(c: Context, l: SharedList, w: Wish): Trashed {
    val at = System.currentTimeMillis()
    val binned = w.copy(bin = at, binBy = l.me)
    lock.withLock { Cloud.shPut(l.key, binned, listOf("bin", "binBy")) }
    var index = 0
    if (active == l.code) {
        index = content.wishes.indexOfFirst { it.id == w.id }.coerceAtLeast(0)
        Bin.binnedHere(c, l.code, w.id, at)
        Bin.added(BinEntry(binned, l.code, content.name.ifBlank { l.name }, reserved[w.id]))
        content = content.copy(wishes = content.wishes.filter { it.id != w.id })
        stamp.incrementAndGet()
        writeCache(c, l.code, content)
    } else readCache(c, l.code)?.let { ct -> writeCache(c, l.code, ct.copy(wishes = ct.wishes.filter { it.id != w.id })) }
    return Trashed(binned, index, l.code)
}

/** True when [url] is a picture stored online for collab wishlist [code] (it goes when that wish is deleted for good). */
fun Shared.ownsPhoto(code: String, url: String): Boolean = photoNameOf(code, url) != null

// ---- deleting: the wish goes to the bin at once (for everyone); Undo takes it out again ----

/**
 * Moves a wish of the open list to the bin: it leaves the list at once and is marked online (only the fields
 * "bin" and "binBy" are written; its picture and any reservation stay). If that fails it comes back with a message.
 */
fun Shared.trash(c: Context, id: Long): Trashed? {
    val l = current ?: return null
    val before = content
    val i = before.wishes.indexOfFirst { it.id == id }
    if (i < 0) return null
    val at = System.currentTimeMillis()
    val binned = before.wishes[i].copy(bin = at, binBy = l.me)
    Bin.binnedHere(c, l.code, id, at)
    Bin.added(BinEntry(binned, l.code, before.name.ifBlank { l.name }, reserved[id]))
    write(c, l, before, before.copy(wishes = before.wishes.filter { it.id != id })) {
        try {
            Cloud.shPut(l.key, binned, listOf("bin", "binBy"))
        } catch (e: Exception) {
            Bin.removed(l.code, id)
            throw e
        }
    }
    return Trashed(binned, i, l.code)
}

/** Undo: the wish comes out of the bin and back where it was. */
fun Shared.untrash(c: Context, t: Trashed) {
    val l = lists.firstOrNull { it.code == t.listCode } ?: return
    Bin.removed(l.code, t.wish.id)
    val back = t.wish.copy(bin = 0L, binBy = "")
    // Only the bin fields are written (left out = cleared). The lock makes sure this goes after the move to the bin.
    val job: suspend () -> Unit = { Cloud.shPut(l.key, back, listOf("bin", "binBy")) }
    if (active == l.code && content.wishes.none { it.id == back.id }) {
        val before = content
        val after = before.copy(wishes = before.wishes.toMutableList().also { it.add(t.index.coerceIn(0, it.size), back) })
        write(c, l, before, after, job = job)
    } else {
        scope.launch {
            try {
                lock.withLock { job() }
            } catch (e: Exception) {
                notice = tr("Couldn’t save that. Check your connection and try again.")
            }
        }
    }
}

fun Shared.delete(c: Context, id: Long) {
    val l = current ?: return
    val before = content
    val removed = before.wishes.firstOrNull { it.id == id } ?: return
    val after = before.copy(wishes = before.wishes.filter { it.id != id })
    write(c, l, before, after) {
        Cloud.shDelete(l.key, id)
        ImageStore.delete(c, removed.image)
        dropPhoto(l, removed.image)
    }
}
