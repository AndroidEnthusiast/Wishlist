package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray

/** How long a deleted wish stays in the bin before it is deleted for good. */
const val BIN_DAYS = 30
private const val DAY_MS = 24L * 60 * 60 * 1000

/** One wish in the bin. [listCode] is the shared wishlist it came from, or null for My wishlist. */
data class BinEntry(val wish: Wish, val listCode: String?, val listName: String, val reserved: Boolean? = null) {
    val key: String get() = (listCode ?: "") + ":" + wish.id
}

/**
 * The bin (menu > Bin). A deleted wish goes here at once (Undo in the message takes it out again) and stays [BIN_DAYS] days.
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
    /** The bins of the shared wishlists, as last loaded, plus what was moved to them on this phone since. */
    private var shared by mutableStateOf(listOf<BinEntry>())
    /** Goes up with every change made here, so a load that started before it can't undo it on screen. */
    @Volatile
    private var changes = 0

    /** Everything in the bin, newest first: My wishlist's bin straight from the phone, and the shared bins. */
    val entries: List<BinEntry>
        // Received gifts (2.128) are in "Received gifts", not in the bin.
        get() = (Repo.myBin.filter { it.recv == 0L }.map { BinEntry(it, null, Repo.binListName(it.id)) } + shared).sortedByDescending { it.wish.bin }
    var loading by mutableStateOf(false)
        private set
    /** True when the bin of a shared wishlist couldn't be loaded (no internet). */
    var offline by mutableStateOf(false)
        private set

    /** The wish to scroll to and light up when the bin opens (from a notification): list code ("" = My wishlist) and id. */
    var focus by mutableStateOf<Pair<String, Long>?>(null)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)


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
        // Received gifts are kept (2.128).
        val old = Repo.myBin.filter { it.recv == 0L && expired(it, now) }.map { it.id }.toSet()
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

    /** A wish of a shared list was moved to the bin on this phone: it shows in the bin at once. */
    fun added(e: BinEntry) {
        changes++
        shared = listOf(e) + shared.filter { it.key != e.key }
    }

    /** A wish of a shared list came out of the bin on this phone (Undo, or saving the move failed). */
    fun removed(code: String, id: Long) {
        changes++
        shared = shared.filter { !(it.listCode == code && it.wish.id == id) }
    }

    /** Fetches the bins of every shared wishlist on this phone (My wishlist's bin is on the phone already). */
    suspend fun load(c: Context) {
        loading = true
        val start = changes
        try {
            purgeMine(c)
            val out = ArrayList<BinEntry>()
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
                    shared.filter { it.listCode == l.code }.forEach { out.add(it) }
                }
            }
            offline = failed
            // Something was moved to or out of the bin here while loading: this answer is already old.
            if (changes == start) shared = out
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
            removed(l.code, e.wish.id)
        }
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
            removed(e.listCode, e.wish.id)
        }
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
     * someone else moved to the bin. Runs in the background ([BinWorker]). [only]: just these lists (null: all).
     * Returns the lists that were looked at (the others couldn't be loaded and are tried again next time).
     */
    suspend fun watch(c: Context, only: Set<String>? = null): Set<String> {
        purgeMine(c)
        val handled = HashSet<String>()
        for (l in Shared.lists.toList()) {
            // The background check asks only about lists that changed (Pulse.kt, ROADMAP S3.3).
            if (only != null && l.code !in only) continue
            val r = try {
                Cloud.shGet(l.key)
            } catch (e: Exception) {
                null
            } ?: continue
            val inBin = purgeShared(l, r.list.wishes)
            // 7 days before the gift list's occasion (2.128): a reminder of what you reserved on it.
            if (r.list.occ != null && Reminders.due(r.list.occ) != null) {
                val res = try { Cloud.fetchReservations(l.code, Repo.deviceId) } catch (e: Exception) { emptyMap() }
                val titles = r.list.wishes.filter { it.bin == 0L && res[it.id] == true }.map { it.title }
                Reminders.giver(c, l.code, gift = true, occ = r.list.occ, owner = "", listName = r.list.name.ifBlank { l.name }, titles = titles)
            }
            val mine = readSet(c, "shbh_${l.code}")
            val told = readSet(c, "shbn_${l.code}")
            val marks = inBin.map { mark(it) }.toSet()
            // A wish moved to the bin a moment ago may still be taken out with Undo: it is looked at next time.
            val settled = System.currentTimeMillis() - 30_000
            val fresh = inBin.filter { mark(it) !in mine && mark(it) !in told && it.bin < settled }
            if (fresh.isNotEmpty()) {
                val res = try {
                    Cloud.fetchReservations(l.code, Repo.deviceId)
                } catch (e: Exception) {
                    continue
                }
                for (w in fresh) {
                    if (res[w.id] == true) Alerts.notifyGiftLost(c, l.code, gift = true, id = w.id, title = w.label, image = w.image, place = "“" + r.list.name.ifBlank { l.name } + "”", owned = false)
                }
            }
            remember(c, l.code, (told + fresh.map { mark(it) }).intersect(marks))
            handled += l.code
        }
        return handled
    }
}

/**
 * The half-hourly background check (see [Alerts.scheduleBin]). Since 2.175 it first asks the server what changed
 * ([Pulse], one call) and then only asks about those parts; the first check of each day asks about everything.
 */
class BinWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Repo.load(c)
        // Your own lists: saves that failed while offline (ROADMAP S3.1); no call when nothing is waiting.
        try {
            Sync.catchUp(c)
        } catch (e: Exception) {
        }
        val plan = Pulse.plan(c)
        try {
            // Collab lists: someone binned a gift you reserved; old wishes leave the bin after 30 days.
            Bin.watch(c, plan.lists("s:", Shared.lists.map { it.code })).forEach { plan.ok("s:$it") }
        } catch (e: Exception) {
        }
        try {
            // Friends' lists you reserved on: a reserved gift taken off, moved or bought.
            FriendWatch.checkAll(c, plan.lists("l:", FriendWatch.codes(c))).forEach { plan.ok("l:$it") }
        } catch (e: Exception) {
        }
        // A gift list handed to you by its creator (2.128).
        try {
            Members.claimAll(c, if (plan.all) null else plan.claims)
        } catch (e: Exception) {
        }
        // "Did your wishes come true?" the day after an occasion of your own lists (2.128). On the phone only.
        try {
            Reminders.checkMine(c)
        } catch (e: Exception) {
        }
        // Friend requests, and requests of yours that were approved (Friends.kt).
        if (plan.due("fr")) try {
            Friends.poll(c)
            plan.ok("fr")
        } catch (e: Exception) {
        }
        // New messages (Chat.kt).
        if (plan.due("chat")) try {
            Chat.poll(c)
            plan.ok("chat")
        } catch (e: Exception) {
        }
        // Wishlist group invites and news (Groups.kt, 2.108).
        if (plan.due("gr")) try {
            Groups.poll(c)
            plan.ok("gr")
        } catch (e: Exception) {
        }
        // News from the server (GiftInvites.kt, 2.142): invitations to gift lists, "Still buying …?" and its answers,
        // in case a push didn't arrive.
        if (plan.due("news")) try {
            News.poll(c)
            plan.ok("news")
        } catch (e: Exception) {
        }
        // The reactions on your wishes, told once a day in the evening (Reactions.kt).
        try {
            Reactions.daily(c)
        } catch (e: Exception) {
        }
        // Gift exchange news, anonymous messages and the exchange day (Exchange.kt, 2.129).
        if (plan.due("gx")) try {
            Exchanges.poll(c)
            plan.ok("gx")
        } catch (e: Exception) {
        }
        Pulse.finish(c, plan)
        return Result.success()
    }
}
