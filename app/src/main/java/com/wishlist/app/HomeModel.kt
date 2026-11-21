package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * Home's state holder (the rebuild, owner 18 November 2026; ROADMAP review point 1): what Home shows and what a list's
 * ⋮ can do live here, not in the screen. The screen ([HubScreen]) only draws what this gives it; the data itself still
 * comes from the app's stores (Repo for your lists, Shared for the ones you share).
 *  - Your lists and the shared ones as blocks ([mineItems], [sharedItems]).
 *  - A list's ⋮ ([actionsOf], decided by [homeActions], which the checks test on its own).
 *  - Which dialog is open (Edit name, Delete, Leave, Visibility, a shared list's invitation) and doing it
 *    ([rename], [delete], [leave]).
 */
@Stable
internal class HomeModel(
    private val ctx: Context,
    private val onOpenMine: (String) -> Unit,
    private val onOpenGroup: (String) -> Unit
) {
    var renaming by mutableStateOf<HubItem?>(null)
    var deleting by mutableStateOf<HubItem?>(null)
    var leaving by mutableStateOf<HubItem?>(null)
    var visibleOf by mutableStateOf<HubItem?>(null)
    var sharingCollab by mutableStateOf<SharedList?>(null)

    /** Your own lists as blocks: the picture is their newest wish photos (a collage of three; never a cover photo, owner). */
    fun mineItems(mine: List<MineData>): List<HubItem> = mine.map { d ->
        val n = if (d.name == "My wishlist") tr(d.name) else d.name
        val pics = newestPics(d.wishes)
        HubItem("m:" + d.id, n, d.wishes.size, pics.firstOrNull(), false, MyLists.saveKey(d.id, false), pics, wishesLabel(d.wishes.size)) { onOpenMine(d.id) }
    }

    /** The wishlists you make with others, from the copy kept on the phone (a little behind until a list is opened). */
    fun sharedItems(lists: List<SharedList>): List<HubItem> = lists.map { l ->
        val ws = Shared.cachedWishes(ctx, l.code)
        val pics = newestPics(ws)
        HubItem("s:" + l.code, l.name, ws.size, pics.firstOrNull(), true, l.code, pics, wishesLabel(ws.size)) { onOpenGroup(l.code) }
    }

    /**
     * A list's ⋮ (label, red, what it does). [onlyList]: deleting your only wishlist is refused with this message.
     * [shareCard]: your own list's share card.
     */
    fun actionsOf(item: HubItem, onlyList: () -> Unit, shareCard: (String) -> Unit): List<Triple<String, Boolean, () -> Unit>> {
        val shared = item.key.startsWith("s:")
        val code = item.key.substringAfter(':')
        val creator = shared && Shared.lists.firstOrNull { it.code == code }?.owner?.isNotEmpty() == true
        return homeActions(shared, creator, Config.enabled).map { a ->
            when (a) {
                // Share first (owner, 7 November 2026). A shared list's Share is its invitation; your own list's its card.
                "share" -> Triple(tr("Share"), false, {
                    NameGate.ensure { if (shared) sharingCollab = Shared.lists.firstOrNull { it.code == code } else shareCard(code) }
                })
                "edit" -> Triple(tr("Edit name"), false, { renaming = item })
                // "Visibility" (owner, 15 November 2026: "Visible to" was too long and unclear).
                "visibility" -> Triple(tr("Visibility"), false, { visibleOf = item })
                "leave" -> Triple(tr("Leave wishlist"), true, { leaving = item })
                else -> Triple(tr("Delete wishlist"), true, {
                    if (!shared && Repo.mineIds.size < 2) onlyList() else deleting = item
                })
            }
        }
    }

    /** Edit name: a shared list is renamed for everyone on it. */
    fun rename(item: HubItem, name: String) {
        val code = item.key.substringAfter(':')
        if (item.key.startsWith("s:")) {
            Shared.open(ctx, code)
            Shared.setName(ctx, name.trim())
        } else Repo.renameMine(ctx, code, name)
    }

    /**
     * Delete: a shared list for everyone; your own list's friends' link stops working first, then the list goes and its
     * wishes to the bin. The message to show.
     */
    suspend fun delete(item: HubItem): String {
        val code = item.key.substringAfter(':')
        return try {
            if (item.key.startsWith("s:")) {
                Shared.open(ctx, code)
                Shared.deleteForEveryone(ctx)
            } else {
                val d = Repo.mine(ctx, code)
                if (d.code != null) {
                    try { Sync.unpublishStored(d) } catch (e: Exception) { }
                }
                Repo.deleteMine(ctx, code)
            }
            tr("“%s” was deleted", item.name)
        } catch (e: Exception) {
            tr("Couldn’t do that. Check your connection.")
        }
    }

    fun leave(item: HubItem) = Shared.leave(ctx, item.key.substringAfter(':'))
}

/**
 * What a list's ⋮ offers, in order (owner, 7 and 15 November 2026): a shared list you joined: Share, Edit name, Leave;
 * one you made: Share, Edit name, Delete; your own: Share, Edit name, Visibility (only with sharing online), Delete.
 */
internal fun homeActions(shared: Boolean, creator: Boolean, online: Boolean): List<String> = when {
    shared && !creator -> listOf("share", "edit", "leave")
    shared -> listOf("share", "edit", "delete")
    else -> listOfNotNull("share", "edit", if (online) "visibility" else null, "delete")
}

/** Nothing on any of your lists and nothing shared: Home offers to import a list from elsewhere (owner, 18 November 2026). */
internal fun homeIsEmpty(mine: List<MineData>, shared: List<SharedList>): Boolean = mine.all { it.wishes.isEmpty() } && shared.isEmpty()

/**
 * A new wishlist of your own (the New wishlist form, NewWishlist.kt): made with its name and description, its occasion,
 * the wishes copied in from your other lists, and the visibility chosen in App settings (owner, 7 November 2026;
 * default: anyone with the link). Its id.
 */
internal fun createOwnWishlist(c: Context, name: String, notes: String, occ: Occasion?, picked: List<Wish>, who: Who? = null): String {
    val id = Repo.createMine(c, name, notes)
    occ?.let { Occasions.setMine(c, id, it) }
    Repo.copyInto(c, id, picked)
    // Who can see it, as chosen in the form (owner, 21 November 2026).
    ListVisibility.startShared(c, id, who)
    return id
}

