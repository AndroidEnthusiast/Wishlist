package com.wishlist.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Your own lists and wishes come from the database (ROADMAP Stage 2, version C1, 2.167).
 *
 *  - At start ([start]) the lists are read from the database into memory, after a check that they match the settings
 *    store (SharedPreferences), which is still written as the fallback during the safety period (S2.5 removes it after
 *    the owner's OK). The first time, the data is moved in first (DbMove.copy). If they ever differ, or the database
 *    can't be read, the app says so in the crash reports, uses the settings store and copies it over again: nothing
 *    can be lost.
 *  - Every change is written to both: MyLists.write keeps the lists in memory, and a listener on the settings store
 *    copies the changed data into the database on one background thread, in one transaction (all or nothing).
 *  - Since 2.168 (C2) the bin, the price history, friends' lists and the Notifications page come from the database
 *    at start too, with the same check ([bin], [history], [friends], [notes]).
 */
object ListStore {
    /** True once the lists in memory came from the database (or were checked against it) at this start. */
    @Volatile var live = false
        private set
    /** Whether the database matched the settings store at this start (null: not read). For the checks. */
    @Volatile var matched: Boolean? = null
        private set
    private val lists = HashMap<String, MineData>()
    /** What the database held at this start (checked), for the parts that are read once: bin, history, friends, notes. */
    private var data: Snapshot? = null
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wishlist-db").apply { isDaemon = true } }
    private val pending = AtomicBoolean(false)
    private var started = false
    private var app: Context? = null

    /** The keys of the settings store whose data is in the database: a change to one copies it over. */
    private fun moves(key: String?) = key != null && (
        key in setOf("wishes", "name", "about", "cats", "code", "token", "photosUp", "mine_ids", "bin", "bin_owner", "history", "friends", "notes") ||
            key.startsWith("mine_")
        )

    // Kept here: the settings store only keeps a weak link to its listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (moves(key)) changed() }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Reads the lists from the database (moving the data in first, the first time). Called by Repo.load, once. */
    @Synchronized
    fun start(c: Context) {
        if (started) return
        started = true
        app = c.applicationContext
        val p = prefs(c)
        val fromStore = DbMove.snapshot(p)
        try {
            // The database is read on its own thread (Room refuses the screen's thread); the start waits for it, as it
            // waited for the settings store before. It is small: one row per wish.
            val fromDb = io.submit<Snapshot> {
                val dao = WishlistDb.get(c).dao()
                if (dao.meta("lists_live") != "1") {
                    DbMove.copy(WishlistDb.get(c), fromStore, version(c))
                    dao.putMeta(MetaRow("lists_live", "1"))
                }
                read(dao)
            }.get()
            val expected = same(fromStore)
            matched = fromDb == expected
            if (fromDb == expected) {
                data = fromDb
            } else {
                Crash.report(IllegalStateException("The database differed from the settings store (" + diff(fromDb, expected) + "); copied again"))
                data = expected
                changed()
            }
            data!!.lists.forEach { lists[it.id] = it }
            live = true
        } catch (e: Throwable) {
            Crash.report(IllegalStateException("Reading from the database failed", e))
            lists.clear(); data = null
            live = false
        }
        p.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Everything that is in the database, in the same shape as [DbMove.snapshot] reads the settings store. */
    private fun read(dao: WishlistDao): Snapshot {
        val cats = dao.categories().map { it.name }
        val lists = dao.lists().map { r ->
            val photos = try {
                JSONArray(r.photos).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            } catch (e: Exception) {
                emptySet()
            }
            MineData(r.id, r.name, r.about, cats, dao.wishes(r.id).map { it.w.toWish(it.id) }, r.code, r.token, photos)
        }
        return Snapshot(
            lists,
            dao.bin().map { it.listId to it.w.toWish(it.id) },
            cats,
            dao.allPricePoints().groupBy({ it.wishId }, { PricePoint(it.at, it.price) }),
            dao.friendLists().map { Friend(it.code, it.name) },
            dao.notes().map { it.toNote() }
        )
    }

    /**
     * The settings store's data as the database keeps it: one set of categories for all lists (as the app does since
     * 2.95), a wish once per list and per bin, price points in time order without repeats or empty histories, a
     * friends' list once.
     */
    internal fun same(s: Snapshot) = Snapshot(
        s.lists.map { l -> l.copy(cats = s.categories, wishes = l.wishes.distinctBy { it.id }) },
        s.bin.distinctBy { it.first to it.second.id },
        s.categories,
        s.history.mapValues { (_, ps) -> ps.distinctBy { it.t }.sortedBy { it.t } }.filterValues { it.isNotEmpty() },
        s.friends.distinctBy { it.code },
        s.notes
    )

    /** Which parts differ (names only, never their contents: the crash report must not hold your wishes). */
    private fun diff(a: Snapshot, b: Snapshot) = listOfNotNull(
        "lists".takeIf { a.lists != b.lists }, "bin".takeIf { a.bin != b.bin }, "categories".takeIf { a.categories != b.categories },
        "history".takeIf { a.history != b.history }, "friends".takeIf { a.friends != b.friends }, "notes".takeIf { a.notes != b.notes }
    ).joinToString()

    private fun version(c: Context) = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }

    /** One of your lists from memory (the database's copy), or null when it isn't there (then the store is read). */
    @Synchronized
    fun get(id: String): MineData? = if (live) lists[id] else null

    /** The bin as the database had it at this start (newest first), or null: then the settings store is read. */
    @Synchronized fun bin(): List<Wish>? = if (live) data?.bin?.map { it.second } else null
    /** The price history as the database had it at this start, or null. */
    @Synchronized fun history(): Map<Long, List<PricePoint>>? = if (live) data?.history else null
    /** Friends' lists saved from a link, as the database had them at this start, or null. */
    @Synchronized fun friends(): List<Friend>? = if (live) data?.friends else null
    /** The Notifications page as the database had it at this start, or null. */
    @Synchronized fun notes(): List<PriceNote>? = if (live) data?.notes else null

    /** A list was written (MyLists.write): memory follows at once; the database follows through [changed]. */
    @Synchronized
    fun put(d: MineData) {
        if (live) lists[d.id] = d
    }

    /** Something that lives in the database changed: copy it over soon, once for several changes in a row. */
    fun changed() {
        val c = app ?: return
        if (!pending.compareAndSet(false, true)) return
        io.execute {
            pending.set(false)
            try {
                DbMove.copy(WishlistDb.get(c), DbMove.snapshot(prefs(c)), version(c))
            } catch (e: Throwable) {
                // The settings store still has it all; the next start finds the difference and copies again.
                Crash.report(IllegalStateException("Saving to the database failed", e))
            }
        }
    }

    /** Waits until every copy asked for so far is written (the checks; Export later). */
    fun flush() {
        io.submit {}.get()
    }

    /** For the checks: forget this start, as a new start of the app would. */
    @Synchronized
    internal fun reset() {
        app?.let { prefs(it).unregisterOnSharedPreferenceChangeListener(listener) }
        started = false; live = false; matched = null; lists.clear(); data = null; app = null
    }
}
