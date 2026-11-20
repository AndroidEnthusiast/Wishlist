package com.wishlist.app

import android.content.Context

/*
 * No dead ends from a notification (owner, 20 November 2026; docs/decisions/notifications.md): before a notification
 * about a wish or a list opens it, this says whether it is still there. A price drop of a deleted wish opened an empty
 * list before. Pure checks on what is on the phone; the screens show the message.
 */

/** Where a notification leads now. */
internal sealed interface NoteWhere {
    /** Open it as usual. */
    object Open : NoteWhere
    /** The wish moved to another of your lists: open that one ([listId]) on it. */
    data class Moved(val listId: String) : NoteWhere
    /** The wish is in your bin: say so, with Restore. */
    data class InBin(val wishId: Long) : NoteWhere
    /** Gone: say [message], open nothing. */
    data class Gone(val message: String) : NoteWhere
}

internal object NoteCheck {
    /** The kinds that are about one of your own wishes (or a shared wishlist's wish) and open it. */
    private val WISH_KINDS = setOf("price", "target")

    fun where(c: Context, n: PriceNote): NoteWhere = where(c, n.kind, n.listKey, n.wishId)

    fun where(c: Context, kind: String, listKey: String, wishId: Long): NoteWhere {
        val aboutWish = wishId > 0L && kind in WISH_KINDS
        return when {
            listKey.startsWith("m:") || (kind == "target" && wishId > 0L) -> {
                val mine = Repo.allMine(c)
                val list = mine.firstOrNull { "m:" + it.id == listKey }
                if (!aboutWish) return if (list != null) NoteWhere.Open else NoteWhere.Gone(tr("That wishlist isn’t on this phone any more."))
                when {
                    list != null && list.wishes.any { it.id == wishId && it.bin == 0L } -> NoteWhere.Open
                    Repo.myBin.any { it.id == wishId } -> NoteWhere.InBin(wishId)
                    else -> mine.firstOrNull { l -> l.wishes.any { it.id == wishId && it.bin == 0L } }?.let { NoteWhere.Moved(it.id) }
                        ?: NoteWhere.Gone(tr("This wish is no longer on your list"))
                }
            }
            listKey.startsWith("s:") -> {
                val code = listKey.drop(2)
                when {
                    Shared.lists.none { it.code == code } -> NoteWhere.Gone(tr("That wishlist isn’t on this phone any more."))
                    // The open shared list is known on this phone; another one is checked when it opens (ListScreen).
                    aboutWish && Shared.active == code && Shared.content.wishes.none { it.id == wishId && it.bin == 0L } ->
                        NoteWhere.Gone(tr("This wish is no longer on the list"))
                    else -> NoteWhere.Open
                }
            }
            else -> NoteWhere.Open
        }
    }

    /**
     * Notifications about your own wishes that were deleted for good (bin emptied, 30 days passed) go too (owner,
     * 20 November 2026). While a wish is in the bin, they stay.
     */
    fun forgetGone(c: Context) {
        val mine = Repo.allMine(c)
        val alive = mine.flatMap { l -> l.wishes.map { it.id } }.toSet() + Repo.myBin.map { it.id }
        Notifications.forget(c) { n -> n.kind in WISH_KINDS && n.wishId > 0L && (n.listKey.startsWith("m:") || n.kind == "target") && n.wishId !in alive }
    }
}
