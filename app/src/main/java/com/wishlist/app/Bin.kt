package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray

/** How long a deleted wish stays in the bin before it is deleted for good. */
const val BIN_DAYS = 10
private const val DAY_MS = 24L * 60 * 60 * 1000

/** One wish in the bin. [listCode] is the shared wishlist it came from, or null for My wishlist. */
data class BinEntry(val wish: Wish, val listCode: String?, val listName: String, val reserved: Boolean? = null) {
    val key: String get() = (listCode ?: "") + ":" + wish.id
}

/**
 * The bin (menu > Bin). A deleted wish goes here when its 10 seconds of Undo are over, and stays [BIN_DAYS] days.
 *  - My wishlist: the bin is kept on this phone ([Repo.myBin]). It also goes online marked "bin", so a friend's
 *    reservation stays if the wish is restored; friends never see it.
 *  - A shared wishlist: the wish stays online with the fields "bin" (when) and "binBy" (who), so everyone on the
 *    list sees it in their bin and can restore it. Its picture and any reservation stay until it is deleted for good.
 * Wishes in the bin are on no list, so they get no price checks or price alerts.
 * Whoever opens the bin (or the background check) deletes wishes older than [BIN_DAYS] days for good.
 *
 * If someone else moves a gift you reserved to the bin, [watch] tells you with a notification; tapping it opens the
 * bin at that gift ([focus]).
 */
object Bin {
    /** Everything in the bin, newest first. Filled by [load]. */
    var entries by mutableStateOf(listOf<BinEntry>())
        private set
    var loading by mutableStateOf(false)
        private set
    /** True when the bin of a shared wishlist couldn't be loaded (no internet). */
    var offline by mutableStateOf(false)
        private set

    /** The wish to scroll to and light up when the bin opens (from a notification): list code ("" = My wishlist) and id. */
    var focus by mutableStateOf<Pair<String, Long>?>(null)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    private fun myListName() = if (Repo.myName == "My wishlist") tr(Repo.myName) else Repo.myName

    fun expired(w: Wish, now: Long = System.currentTimeMillis()): Boolean = w.bin > 0L && now - w.bin >= BIN_DAYS * DAY_MS

    /** Whole days until the wish is deleted for good (0 = today). */
    fun daysLeft(w: Wish, now: Long = System.currentTimeMillis()): Int {
        val left = w.bin + BIN_DAYS * DAY_MS - now
        return if (left <= 0) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    // ---- remembering, per shared list, what this phone moved to the bin and which alerts it already gave ("id:when") ----

    private fun readSet(c: Context, k: String): Set<String> = try {
        val a = JSONArray(prefs(c).getString(k, null) ?: "[]")
        (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun writeSet(c: Context, k: String, v: Set<String>) {
        prefs(c).edit().putString(k, JSONArray(v.toList()).toString()).apply()
    }

    private fun mark(w: Wish) = w.id.toString() + ":" + w.bin

    /** This phone moved a wish of shared list [code] to the bin at [at]: no "your reserved gift" alert for that one. */
    @Synchronized
    fun binnedHere(c: Context, code: String, id: Long, at: Long) {
        writeSet(c, "shbh_$code", readSet(c, "shbh_$code") + (id.toString() + ":" + at))
    }

    /**
     * Saves the alerts given for list [code] (only what is still in the bin needs remembering) and forgets what this
     * phone moved to the bin more than [BIN_DAYS] days ago.
     */
    @Synchronized
    private fun remember(c: Context, code: String, told: Set<String>) {
        writeSet(c, "shbn_$code", told)
        val since = System.currentTimeMillis() - (BIN_DAYS + 1) * DAY_MS
        writeSet(c, "shbh_$code", readSet(c, "shbh_$code").filter { (it.substringAfter(':').toLongOrNull() ?: 0L) > since }.toSet())
    }

    /** The list was taken off this phone. */
    fun forget(c: Context, code: String) {
        prefs(c).edit().remove("shbh_$code").remove("shbn_$code").apply()
    }

    // ---- the bin screen ----

    /** Deletes My wishlist's wishes that were in the bin too long. */
    private fun purgeMine(c: Context) {
        val now = System.currentTimeMillis()
        val old = Repo.myBin.filter { expired(it, now) }.map { it.id }.toSet()
        if (old.isNotEmpty()) Repo.dropFromBin(c, old)
    }

    /** Deletes a shared list's wishes that were in the bin too long. Returns the ones still in the bin. */
    private suspend fun purgeShared(l: SharedList, wishes: List<Wish>): List<Wish> {
        val now = System.currentTimeMillis()
        val (old, keep) = wishes.filter { it.bin > 0L }.partition { expired(it, now) }
        for (w in old) {
            try {
                Cloud.shDelete(l.key, w.id)
                Shared.dropPhoto(l, w.image)
            } catch (e: Exception) {
            }
        }
        return keep
    }

    /** Fills [entries]: the bin of My wishlist and of every shared wishlist on this phone. */
    suspend fun load(c: Context) {
        loading = true
        try {
            purgeMine(c)
            val out = ArrayList<BinEntry>()
            Repo.myBin.forEach { out.add(BinEntry(it, null, myListName())) }
            var failed = false
            for (l in Shared.lists.toList()) {
                try {
                    val r = Cloud.shGet(l.key) ?: continue
                    val keep = purgeShared(l, r.list.wishes)
                    val res = if (keep.isEmpty()) emptyMap() else Cloud.fetchReservations(l.code, Repo.deviceId)
                    keep.forEach { out.add(BinEntry(it, l.code, r.list.name.ifBlank { l.name }, res[it.id])) }
                } catch (e: Exception) {
                    failed = true
                    // Keep showing what was there before for this list.
                    entries.filter { it.listCode == l.code }.forEach { out.add(it) }
                }
            }
            offline = failed
            entries = out.sortedByDescending { it.wish.bin }
        } finally {
            loading = false
        }
    }

    /** Puts the wish back on its list. Throws when it couldn't be saved. */
    suspend fun restore(c: Context, e: BinEntry) {
        if (e.listCode == null) {
            Repo.restoreMine(c, e.wish.id)
        } else {
            val l = Shared.lists.firstOrNull { it.code == e.listCode } ?: throw CloudException(tr("You’re no longer on that list."))
            // Only the bin fields are written (left out = cleared), so nothing else anyone changed is undone.
            Cloud.shPut(l.key, e.wish.copy(bin = 0L, binBy = ""), listOf("bin", "binBy"))
            if (Shared.active == l.code) Shared.refresh(c)
        }
        entries = entries.filter { it.key != e.key }
    }

    /** Deletes the wish for good (with its picture and any reservation). Throws when that failed. */
    suspend fun deleteForGood(c: Context, e: BinEntry) {
        if (e.listCode == null) {
            Repo.dropFromBin(c, setOf(e.wish.id))
        } else {
            val l = Shared.lists.firstOrNull { it.code == e.listCode }
            if (l != null) {
                Cloud.shDelete(l.key, e.wish.id)
                Shared.dropPhoto(l, e.wish.image)
            }
        }
        entries = entries.filter { it.key != e.key }
    }

    /** Deletes everything in the bin for good. Returns false when something couldn't be deleted (it stays in the bin). */
    suspend fun empty(c: Context): Boolean {
        var ok = true
        for (e in entries.toList()) {
            try {
                deleteForGood(c, e)
            } catch (ex: Exception) {
                ok = false
            }
        }
        return ok
    }

    // ---- the background check: "Your reserved gift has been moved to the bin" ----

    /**
     * Deletes old wishes from the bins, and alerts once for every gift this phone reserved on a shared wishlist that
     * someone else moved to the bin. Runs in the background ([BinWorker]) and when the app starts.
     */
    suspend fun watch(c: Context) {
        purgeMine(c)
        for (l in Shared.lists.toList()) {
            val r = try {
                Cloud.shGet(l.key)
            } catch (e: Exception) {
                null
            } ?: continue
            val inBin = purgeShared(l, r.list.wishes)
            val mine = readSet(c, "shbh_${l.code}")
            val told = readSet(c, "shbn_${l.code}")
            val marks = inBin.map { mark(it) }.toSet()
            val fresh = inBin.filter { mark(it) !in mine && mark(it) !in told }
            if (fresh.isNotEmpty()) {
                val res = try {
                    Cloud.fetchReservations(l.code, Repo.deviceId)
                } catch (e: Exception) {
                    continue
                }
                for (w in fresh) {
                    if (res[w.id] == true) Alerts.notifyBinned(c, l.code, w, r.list.name.ifBlank { l.name })
                }
            }
            remember(c, l.code, (told + fresh.map { mark(it) }).intersect(marks))
        }
    }
}

/** Runs [Bin.watch] every half hour (see [Alerts.scheduleBin]). */
class BinWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Repo.load(c)
        try {
            Bin.watch(c)
        } catch (e: Exception) {
        }
        return Result.success()
    }
}
