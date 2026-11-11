package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moving a wish to or from a collab wishlist (owner, 11 November 2026): own list → collab, collab → own, collab →
 * collab, in the app only. Moving between two of your own lists stays in Move.kt (reservations move along there).
 *
 * The owner's choices:
 * - **Leaving a collab wishlist asks each time: Move or Copy.** Move puts the wish in that list's bin, so anyone on
 *   it can put it back for 30 days (older apps and the browser editor already understand the bin). Copy leaves it.
 * - **A wish someone else reserved on a collab wishlist can't be moved away** (it can be copied). Your own
 *   reservation simply ends. Leaving your OWN list never checks this: you must not learn that a wish is reserved; the
 *   giver gets the usual "a gift you reserved is no longer on …".
 * - **Private wishes move without a question** and are visible to everyone on the collab wishlist afterwards.
 * - What a collab wishlist doesn't have is dropped on the way in: top wish, owned, private, the old price, received.
 *   What your own list doesn't have is dropped on the way out: "added by" and the shared price-check time.
 *
 * Nothing is lost when the server can't be reached: the wish arrives at the new list first and only then leaves the
 * old one; a failure in between is rolled back. A picture stored for the old collab list is copied (it would go when
 * that list's bin is emptied).
 *
 * A list is named by a key: one of your own lists by its id, a collab wishlist by "s:" + its code.
 */
object MoveAcross {
    private const val COLLAB = "s:"
    fun collabKey(code: String) = COLLAB + code
    fun isCollab(key: String) = key.startsWith(COLLAB)
    fun codeOf(key: String) = key.removePrefix(COLLAB)

    /** The wish as it arrives on a collab wishlist that has the categories [cats], added by [me]. */
    fun forCollab(w: Wish, me: String, cats: List<String>, id: Long = w.id): Wish = w.copy(
        id = id, star = false, got = false, priv = false, oldPrice = null, recv = 0L, bin = 0L, binBy = "", checked = 0L,
        by = me, category = if (w.category in cats) w.category else ""
    )

    /** The wish as it arrives on one of your own lists (their categories are [cats]). */
    fun forMine(w: Wish, cats: List<String>, id: Long = w.id): Wish = w.copy(
        id = id, by = "", checked = 0L, bin = 0L, binBy = "", oldPrice = null, star = false, got = false, priv = false,
        recv = 0L, category = if (w.category in cats) w.category else ""
    )

    /** May a wish be moved away from a collab wishlist? [reserved]: null free, true by this phone, false by someone else. */
    fun mayLeave(reserved: Boolean?): Boolean = reserved != false

    /** What moved (or was copied), the problem if any, and how to take it back. */
    class Result(val done: Set<Long>, val problem: String?, val undo: (suspend () -> String?)?)

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val h = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            h.connectTimeout = 10000; h.readTimeout = 15000
            try { if (h.responseCode == 200) h.inputStream.use { it.readBytes() }.takeIf { it.size in 1..3_000_000 } else null } finally { h.disconnect() }
        } catch (e: Exception) {
            null
        }
    }

    /** Moves (or copies) [ids] from the list [from] to the list [to]. */
    suspend fun run(c: Context, from: String, to: String, ids: Set<Long>, copy: Boolean): Result {
        val offline = tr("Couldn’t move. Check your connection.")
        val src = if (isCollab(from)) Shared.lists.firstOrNull { it.code == codeOf(from) } ?: return Result(emptySet(), offline, null) else null
        val dst = if (isCollab(to)) Shared.lists.firstOrNull { it.code == codeOf(to) } ?: return Result(emptySet(), offline, null) else null
        val wishes = when {
            src == null -> Repo.mine(c, from).wishes
            Shared.active == src.code -> Shared.content.wishes
            else -> Shared.cachedWishes(c, src.code)
        }.filter { it.id in ids && it.bin == 0L }
        val done = HashSet<Long>()
        var problem: String? = null
        val undos = ArrayList<suspend () -> Unit>()
        var n = 0
        for (w in wishes) {
            val reserved = if (src != null && Shared.active == src.code) Shared.reserved[w.id] else null
            if (src != null && !copy && !mayLeave(reserved)) {
                problem = tr("Someone reserved this wish, so it stays here.")
                continue
            }
            // A picture kept online for the collab list it leaves: a copy of its own, or it disappears with the bin.
            var image = w.image
            var fetched = false
            if (src != null && Shared.ownsPhoto(src.code, image)) {
                download(image)?.let { ImageStore.store(c, it) }?.let { image = it; fetched = true }
            }
            val id = if (copy) System.currentTimeMillis() + (n++) else w.id
            // 1. It arrives.
            val arrived: Wish
            try {
                arrived = if (dst != null) {
                    Shared.addTo(c, dst, forCollab(w.copy(image = image), dst.me.ifBlank { Shared.lastName }, Shared.catsOf(c, dst.code), id))
                        .also { if (fetched) ImageStore.delete(c, image) }
                } else {
                    val mine = forMine(w.copy(image = image), Repo.myCats, id)
                    Repo.updateMine(c, to) { ws -> listOf(mine) + ws.filter { it.id != id } }
                    mine
                }
            } catch (e: Exception) {
                if (fetched) ImageStore.delete(c, image)
                problem = offline
                continue
            }
            suspend fun takeBack() {
                if (dst != null) try { Shared.sendAway(c, dst, arrived) } catch (e: Exception) {}
                else Repo.updateMine(c, to) { ws -> ws.filter { it.id != id } }
            }
            // 2. It leaves (not for a copy).
            if (!copy) {
                if (src != null) {
                    val t = try {
                        Shared.sendAway(c, src, w)
                    } catch (e: Exception) {
                        takeBack()
                        problem = offline
                        continue
                    }
                    // Your own reservation of it ends with the move.
                    if (reserved == true && Shared.active == src.code) Shared.reserve(w, false)
                    undos.add { takeBack(); Shared.untrash(c, t) }
                } else {
                    Repo.updateMine(c, from) { ws -> ws.filter { it.id != w.id } }
                    undos.add { takeBack(); Repo.updateMine(c, from) { ws -> if (ws.any { it.id == w.id }) ws else listOf(w) + ws } }
                }
            } else {
                undos.add { takeBack() }
            }
            done += w.id
        }
        if (dst != null && Shared.active == dst.code) Shared.refresh(c)
        val undo: (suspend () -> String?)? = if (undos.isEmpty()) null else ({
            try { undos.forEach { it() }; null } catch (e: Exception) { offline }
        })
        return Result(done, problem, undo)
    }
}
