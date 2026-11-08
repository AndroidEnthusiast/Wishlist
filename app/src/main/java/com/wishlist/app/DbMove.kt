package com.wishlist.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/*
 * Moving your data into the database (ROADMAP S2.3). [copy] replaces the database's contents with what the settings
 * store (SharedPreferences) holds, in ONE transaction, and checks every count inside it: a mismatch undoes it all.
 * 2.166 ran it as a rehearsal at every start; since 2.167 ListStore runs it the first time and after every change
 * (the settings store is still written as the fallback), and reads your lists from the database.
 *
 * What moves: your own lists (name, note, link, categories), their wishes in order, the bin (with the list each wish
 * came from), the categories, the price history, friends' lists and the Notifications page. Settings, the profile,
 * collab wishlists, groups and exchanges stay in the settings store.
 */

/** Everything that moves, as read from the settings store. */
data class Snapshot(
    val lists: List<MineData>,
    val bin: List<Pair<String, Wish>>,
    val categories: List<String>,
    val history: Map<Long, List<PricePoint>>,
    val friends: List<Friend>,
    val notes: List<PriceNote>
)

/** How many of each went in (and how many duplicates were left out); written to `meta` when the copy worked. */
data class MoveCount(val lists: Int, val wishes: Int, val bin: Int, val points: Int, val friends: Int, val notes: Int, val duplicates: Int) {
    override fun toString() = "lists=$lists wishes=$wishes bin=$bin points=$points friends=$friends notes=$notes dup=$duplicates"
}

object DbMove {
    /** Reads everything that moves, the same way Repo, History, Notifications and the bin read it (read only). */
    fun snapshot(p: SharedPreferences): Snapshot {
        val ids = try {
            val a = JSONArray(p.getString("mine_ids", null) ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }.let { if (MAIN_LIST in it) it else listOf(MAIN_LIST) + it }.distinct()
        val lists = ids.map { MyLists.read(p, it, keep = false) }
        val owner = try {
            val o = JSONObject(p.getString("bin_owner", null) ?: "{}")
            o.keys().asSequence().associate { it.toLong() to o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        val bin = SafeStore.readList(p, "bin", keep = false) { wishFromJson(it) }.map { (owner[it.id] ?: MAIN_LIST) to it }
        // One set for all your lists (owner, 2.95): in the order of the first list, then any the others add.
        val cats = lists.flatMap { it.cats }.fold(listOf<String>()) { acc, n -> if (acc.any { it.equals(n, ignoreCase = true) }) acc else acc + n }
        val history = try {
            val o = JSONObject(p.getString("history", null) ?: "{}")
            o.keys().asSequence().associate { k ->
                val a = o.getJSONArray(k)
                k.toLong() to (0 until a.length()).map { i -> a.getJSONArray(i).let { e -> PricePoint(e.getLong(0), e.getDouble(1)) } }
            }
        } catch (e: Exception) {
            emptyMap()
        }
        val friends = SafeStore.readList(p, "friends", keep = false) { o -> Friend(o.getString("code"), o.optString("name", "Wishlist")) }
        val notes = try {
            val a = JSONArray(p.getString("notes", null) ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                PriceNote(
                    o.getLong("id"), o.optString("list"), o.optString("title"), o.optString("image"),
                    o.optDouble("pct", 0.0), o.optLong("time"), o.optBoolean("read"),
                    o.optString("kind", "price"), o.optDouble("amount", 0.0), o.optString("cur", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        return Snapshot(lists, bin, cats, history, friends, notes)
    }

    /**
     * Replaces the database's contents with [s] in one transaction and checks the counts inside it: when anything
     * doesn't match, it throws and the transaction is undone (the database keeps what it had). A wish that appears
     * twice on the same list (or twice in the bin) goes in once; that is counted, not an error.
     */
    fun copy(db: WishlistDb, s: Snapshot, version: String): MoveCount {
        val dao = db.dao()
        var count: MoveCount? = null
        db.runInTransaction {
            dao.clearData()
            dao.putLists(s.lists.mapIndexed { i, l ->
                ListRow(l.id, l.name, l.about, l.code, l.token, JSONArray(l.photos.toList()).toString(), i)
            })
            var dup = 0
            val wishRows = s.lists.flatMap { l ->
                val seen = HashSet<Long>()
                l.wishes.mapIndexedNotNull { i, w -> if (seen.add(w.id)) WishRow(l.id, w.id, i, w.cols()) else { dup++; null } }
            }
            dao.putWishes(wishRows)
            val seenBin = HashSet<Pair<String, Long>>()
            val binRows = s.bin.mapIndexedNotNull { i, (list, w) ->
                if (seenBin.add(list to w.id)) BinRow(list, w.id, i, w.cols()) else { dup++; null }
            }
            dao.putBin(binRows)
            dao.putCategories(s.categories.mapIndexed { i, n -> CategoryRow(n, i) })
            val points = s.history.flatMap { (id, ps) -> ps.distinctBy { it.t }.map { PricePointRow(id, it.t, it.p) } }
            dao.putPricePoints(points)
            dao.putFriendLists(s.friends.distinctBy { it.code }.mapIndexed { i, f -> FriendListRow(f.code, f.name, i) })
            dao.putNotes(s.notes.mapIndexed { i, n -> n.toRow(i) })
            val c = MoveCount(
                dao.countLists(), dao.countWishes(), dao.countBin(), dao.countPricePoints(),
                dao.friendLists().size, dao.notes().size, dup
            )
            check(c.lists == s.lists.size) { "lists: ${c.lists} of ${s.lists.size}" }
            check(c.wishes == wishRows.size) { "wishes: ${c.wishes} of ${wishRows.size}" }
            check(c.bin == binRows.size) { "bin: ${c.bin} of ${binRows.size}" }
            check(c.points == points.size) { "price points: ${c.points} of ${points.size}" }
            check(c.notes == s.notes.size) { "notifications: ${c.notes} of ${s.notes.size}" }
            dao.putMeta(MetaRow("copied", "$version $c"))
            dao.putMeta(MetaRow("copied_at", System.currentTimeMillis().toString()))
            count = c
        }
        return count!!
    }
}
