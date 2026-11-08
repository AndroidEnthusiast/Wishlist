package com.wishlist.app

import org.json.JSONArray

/*
 * Saving only what changed (ROADMAP S2.2, 2.169). The database holds [ListStore]'s last snapshot; a change compares the
 * new one with it and writes just the rows that differ, in one transaction: one wish edited is one row written, a wish
 * deleted one row removed. The categories, friends' lists and notifications are small and are rewritten as a whole
 * when they change. Afterwards the counts are checked inside the transaction; a mismatch undoes it, and ListStore
 * falls back to a full copy (DbMove.copy).
 */
object DbDiff {
    /** How many rows a save wrote or removed (for the checks: "one wish edited writes one row"). */
    data class Written(val upserts: Int, val deletes: Int)

    private fun listRow(l: MineData, i: Int) = ListRow(l.id, l.name, l.about, l.code, l.token, JSONArray(l.photos.toList()).toString(), i)

    /** Writes the difference between [old] (what the database holds) and [new]; both as ListStore.same gives them. */
    fun apply(db: WishlistDb, old: Snapshot, new: Snapshot): Written {
        val dao = db.dao()
        var ups = 0
        var dels = 0
        db.runInTransaction {
            // Lists (a removed list takes its wishes with it).
            val oldLists = old.lists.mapIndexed { i, l -> l.id to listRow(l, i) }.toMap()
            val newLists = new.lists.mapIndexed { i, l -> l.id to listRow(l, i) }.toMap()
            (oldLists.keys - newLists.keys).forEach { dao.deleteList(it); dels++ }
            newLists.values.filter { oldLists[it.id] != it }.let { if (it.isNotEmpty()) { dao.putLists(it); ups += it.size } }
            // Wishes, per list, in their places.
            val oldW = old.lists.filter { it.id in newLists }.flatMap { l -> l.wishes.mapIndexed { i, w -> (l.id to w.id) to WishRow(l.id, w.id, i, w.cols()) } }.toMap()
            val newW = new.lists.flatMap { l -> l.wishes.mapIndexed { i, w -> (l.id to w.id) to WishRow(l.id, w.id, i, w.cols()) } }.toMap()
            (oldW.keys - newW.keys).forEach { (l, id) -> dao.deleteWish(l, id); dels++ }
            newW.values.filter { oldW[it.listId to it.id] != it }.let { if (it.isNotEmpty()) { dao.putWishes(it); ups += it.size } }
            // The bin.
            val oldB = old.bin.mapIndexed { i, (l, w) -> (l to w.id) to BinRow(l, w.id, i, w.cols()) }.toMap()
            val newB = new.bin.mapIndexed { i, (l, w) -> (l to w.id) to BinRow(l, w.id, i, w.cols()) }.toMap()
            (oldB.keys - newB.keys).forEach { (l, id) -> dao.deleteBinRow(l, id); dels++ }
            newB.values.filter { oldB[it.listId to it.id] != it }.let { if (it.isNotEmpty()) { dao.putBin(it); ups += it.size } }
            // Price history: one row per point.
            val oldP = old.history.flatMap { (id, ps) -> ps.map { (id to it.t) to it.p } }.toMap()
            val newP = new.history.flatMap { (id, ps) -> ps.map { (id to it.t) to it.p } }.toMap()
            (oldP.keys - newP.keys).forEach { (id, t) -> dao.deletePricePoint(id, t); dels++ }
            newP.filter { (k, v) -> oldP[k] != v }.map { (k, v) -> PricePointRow(k.first, k.second, v) }
                .let { if (it.isNotEmpty()) { dao.putPricePoints(it); ups += it.size } }
            // The small ones, whole.
            if (old.categories != new.categories) {
                dao.clearCategories(); dao.putCategories(new.categories.mapIndexed { i, n -> CategoryRow(n, i) }); ups += new.categories.size
            }
            if (old.friends != new.friends) {
                dao.clearFriendLists(); dao.putFriendLists(new.friends.mapIndexed { i, f -> FriendListRow(f.code, f.name, i) }); ups += new.friends.size
            }
            if (old.notes != new.notes) {
                dao.clearNotes(); dao.putNotes(new.notes.mapIndexed { i, n -> n.toRow(i) }); ups += new.notes.size
            }
            check(dao.countLists() == newLists.size) { "lists" }
            check(dao.countWishes() == newW.size) { "wishes" }
            check(dao.countBin() == newB.size) { "bin" }
            check(dao.countPricePoints() == newP.size) { "price points" }
        }
        return Written(ups, dels)
    }
}
