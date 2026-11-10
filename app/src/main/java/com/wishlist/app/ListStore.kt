package com.wishlist.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Your lists, wishes, bin, price history, friends' lists and notifications live in the database (ROADMAP Stage 2).
 *
 * Since 2.171 (S2.5, owner's OK) the database is the only source:
 *  - At start ([start]) everything is read from the database. The first start of 2.171 (or of any version for a phone
 *    coming from an old one) still moves the settings store in and checks it, then marks the database "frozen-store":
 *    from then on the settings store (SharedPreferences) is no longer written for these, and its last values stay on
 *    the phone untouched.
 *  - Every change goes from the app's memory ([source], Repo.memory) to the database, only what changed (DbDiff), on
 *    one background thread, in one transaction.
 *  - When the database can't be read, the app falls back to the settings store AND writes to it again ([live] false),
 *    so nothing typed in that session is lost; the crash reports say so.
 * (2.167–2.170 wrote both and compared them at every start.)
 */
object ListStore {
    /** True once the lists in memory came from the database (or were checked against it) at this start. */
    @Volatile var live = false
        private set
    /**
     * True when the database couldn't be read at this start, or was lost, while it was the only source before (2.178):
     * the lists on screen then come from the settings store's OLD copy (frozen in 2.171), so they must never be sent
     * online, where they would wipe newer wishes and their reservations. See Sync.pushData. It stays true until an
     * Import. ROADMAP R1 replaces this silent fallback with a clear screen and a way to get the online copy back.
     */
    @Volatile var stale = false
        private set
    /** Whether the database matched the settings store at this start (null: not read). For the checks. */
    @Volatile var matched: Boolean? = null
        private set
    private val lists = HashMap<String, MineData>()
    /** What the database held at this start (checked), for the parts that are read once: bin, history, friends, notes. */
    private var data: Snapshot? = null
    /** What the database holds now, so a change writes only the difference (DbDiff); null: unknown, copy it all. */
    private var inDb: Snapshot? = null
    /** Builds what the app holds now (Repo.memory); set by Repo.load. */
    @Volatile var source: (() -> Snapshot)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val building = AtomicBoolean(false)

    /** Rows written and removed since this start, added up over every save (for the checks); -1 deletes: a full copy. */
    @Volatile var written = DbDiff.Written(0, 0)
        private set
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wishlist-db").apply { isDaemon = true } }
    private val pending = AtomicBoolean(false)
    @Volatile private var latest: Snapshot? = null
    private var started = false
    private var app: Context? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    /** Settings-store flag: the database has been the only source of the lists on this phone. */
    private const val DB_ONLY = "db_only"
    /** Database note: its lists were refilled from the settings store's old copy after the database was lost. */
    private const val OLD_COPY = "old_copy"

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
            val db = WishlistDb.get(c)
            val (fromDb, frozen, old) = io.submit<Triple<Snapshot, Boolean, Boolean>> {
                val dao = db.dao()
                if (dao.meta("lists_live") != "1") {
                    // An empty database on a phone where it was the only source: the file was lost or damaged
                    // (Android then starts a new one). The settings store's old copy is all there is; it is used,
                    // and marked, so it is never sent online over the newer lists there (2.178).
                    val lost = p.getBoolean(DB_ONLY, false)
                    DbMove.copy(db, fromStore, version(c))
                    dao.putMeta(MetaRow("lists_live", "1"))
                    if (lost) dao.putMeta(MetaRow(OLD_COPY, "1"))
                }
                Triple(read(dao), dao.meta("store_frozen") == "1", dao.meta(OLD_COPY) == "1")
            }.get()
            if (frozen) {
                // The database is the only source (S2.5): the settings store holds an old copy, never compared.
                data = fromDb
                inDb = fromDb
            } else {
                // The last start that compares: the settings store is still up to date. When they differ, the settings
                // store wins and is copied over (now, before freezing).
                val expected = same(fromStore)
                matched = fromDb == expected
                if (fromDb != expected) {
                    Crash.report(IllegalStateException("The database differed from the settings store (" + diff(fromDb, expected) + "); copied again"))
                    io.submit { DbMove.copy(db, expected, version(c)) }.get()
                }
                data = expected
                inDb = expected
                io.submit { db.dao().putMeta(MetaRow("store_frozen", "1")) }.get()
            }
            data!!.lists.forEach { lists[it.id] = it }
            live = true
            stale = old
            if (old) Crash.report(IllegalStateException("The database was lost; the lists are the settings store's OLD copy (nothing is sent online)"))
            // Remembered outside the database: from now on the settings store's copy is an old one.
            if (!p.getBoolean(DB_ONLY, false)) p.edit().putBoolean(DB_ONLY, true).apply()
        } catch (e: Throwable) {
            stale = p.getBoolean(DB_ONLY, false)
            Crash.report(IllegalStateException("Reading from the database failed; using the settings store" + if (stale) " (an OLD copy: nothing is sent online)" else "", e))
            lists.clear(); data = null; inDb = null
            live = false
        }
    }

    /** Everything that is in the database, in the same shape as [DbMove.snapshot] reads the settings store. */
    internal fun read(dao: WishlistDao): Snapshot {
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

    /**
     * Something that lives in the database changed: save it soon, once for several changes in a row. What is saved is
     * read from the app's memory on the main thread once the current change is complete (a list made, then opened…).
     */
    fun changed() {
        if (!live || source == null) return
        if (!building.compareAndSet(false, true)) return
        main.post { building.set(false); save() }
    }

    /** Takes what the app holds now and writes the difference on the database's thread. */
    private fun save() {
        val c = app ?: return
        val now = try { same(source?.invoke() ?: return) } catch (e: Throwable) { Crash.report(e); return }
        latest = now
        if (!pending.compareAndSet(false, true)) return
        io.execute {
            pending.set(false)
            val now = latest ?: return@execute
            val db = WishlistDb.get(c)
            try {
                // Only what changed (2.169); everything when it isn't known what the database holds.
                val old = inDb
                val w = if (old != null) DbDiff.apply(db, old, now)
                else DbMove.copy(db, now, version(c)).let { DbDiff.Written(it.lists + it.wishes + it.bin + it.points, -1) }
                written = if (w.deletes < 0 || written.deletes < 0) DbDiff.Written(written.upserts + w.upserts, -1)
                else DbDiff.Written(written.upserts + w.upserts, written.deletes + w.deletes)
                inDb = now
            } catch (e: Throwable) {
                try {
                    // A save that went wrong is undone by its transaction; a full copy puts the database right.
                    DbMove.copy(db, now, version(c))
                    inDb = now
                    Crash.report(IllegalStateException("Saving the changes to the database failed; copied it all", e))
                } catch (e2: Throwable) {
                    // Kept in memory: the next change tries a full copy again. Only if the app closes before that is
                    // this change missing from the database (the crash report says so).
                    inDb = null
                    Crash.report(IllegalStateException("Saving to the database failed", e2))
                }
            }
        }
    }

    /**
     * What the database holds now, for Export (S2.4): every save asked for so far is written first. Null when the
     * database can't be used (Export then takes the settings store as it is).
     */
    fun current(): Snapshot? {
        val c = app ?: return null
        if (!live) return null
        return try { io.submit<Snapshot> { read(WishlistDb.get(c).dao()) }.get() } catch (e: Throwable) { Crash.report(e); null }
    }

    /**
     * Import (S2.4): stop saving, empty the database and forget it, so nothing is written halfway while the file's
     * data goes into the settings store, and the next start moves it in fresh (no "differed" report).
     */
    fun forImport(c: Context) {
        reset()
        // The file's data goes into the settings store, which is then the newest copy again.
        prefs(c).edit().remove(DB_ONLY).apply()
        try {
            io.submit {
                WishlistDb.get(c).let { db -> db.runInTransaction { db.dao().clearData(); db.dao().clearMeta() } }
            }.get()
        } catch (e: Throwable) {
            Crash.report(e)
        }
    }

    /** Waits until every save asked for so far is written (Export; the checks). */
    fun flush() {
        if (building.getAndSet(false)) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) save()
            else java.util.concurrent.CountDownLatch(1).also { l -> main.post { save(); l.countDown() } }.await()
        }
        io.submit {}.get()
    }

    /** Your lists' ids in their order, as the database had them at this start, or null. */
    @Synchronized fun ids(): List<String>? = if (live) data?.lists?.map { it.id } else null

    /** Which list each wish in the bin came from (only those not from "main"), as the database had it, or null. */
    @Synchronized fun binOwners(): Map<Long, String>? =
        if (live) data?.bin?.filter { it.first != MAIN_LIST }?.associate { it.second.id to it.first } else null

    /** Forget this start, as a new start of the app would (the checks, and Import before it restarts the app). */
    @Synchronized
    internal fun reset() {
        source = null; building.set(false); latest = null
        started = false; live = false; stale = false; matched = null; lists.clear(); data = null; inDb = null; written = DbDiff.Written(0, 0); app = null
    }
}
