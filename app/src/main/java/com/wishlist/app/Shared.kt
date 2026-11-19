package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** A wishlist you edit together with other people. [owner] is only filled on the phone that created it. */
data class SharedList(
    /** The list's public code. Friends use it in their link. */
    val code: String,
    /** The secret edit key from the invite link. Whoever has it can edit. */
    val key: String,
    val name: String,
    /** The creator's owner token (empty on phones that joined). Needed to reset the invite link or delete the list. */
    val owner: String = "",
    /** The name you use on this list ("Added by ..."). */
    val me: String = ""
)

/** What is shown of the shared wishlist that is open. */
data class SharedContent(
    val name: String = "",
    val cats: List<String> = emptyList(),
    val about: String = "",
    val wishes: List<Wish> = emptyList(),
    /** The list's occasion (Occasion.kt), or null. */
    val occ: Occasion? = null
)

/**
 * Wishlists you edit together with others. The online copy is the truth: every change is sent as a small
 * change (only the fields you changed in one wish, one category, the name...), never the whole list, so two
 * people editing at the same time don't undo each other. The screen refreshes from the online copy every few
 * seconds. Editing needs internet; the last copy is kept on the phone so you can still look at the list offline.
 *
 * Since the data rebuild (owner, 19 November 2026) this file is the front door: the lists on this phone, the open list,
 * its offline copy, refreshing and [write] (a change shown at once, saved in the background, undone if it fails). The
 * rest is in parts, while callers keep writing Shared.…: SharedMembership.kt (making, joining, leaving, names),
 * SharedWishes.kt (saving wishes field by field, bin and Undo, pictures), SharedDetails.kt (name, note, occasion,
 * categories), SharedReservations.kt. SharedSameTest proves it all does exactly what it did before.
 */
object Shared {
    var lists by mutableStateOf(listOf<SharedList>())
        internal set

    /** Code of the shared wishlist that is open on screen, or null when My wishlist is. */
    var active by mutableStateOf<String?>(null)
        internal set
    var content by mutableStateOf(SharedContent())
        internal set

    /** True when the open list can't be reached with our key anymore (deleted, or the invite link was reset). */
    var gone by mutableStateOf(false)
        internal set

    /** A short message for the snackbar (for example "Couldn't save that"). Cleared once shown. */
    var notice by mutableStateOf("")
        internal set

    // Reservations (SharedReservations.kt), read as before.
    val reserved: Map<Long, Boolean> get() = SharedReservations.reserved
    /** A "2×" wish waiting for "How many will you buy?" (2.128); the dialog is on the list screen. */
    var askCount: Wish?
        get() = SharedReservations.askCount
        set(v) { SharedReservations.askCount = v }

    /** The name typed last time, to prefill the "your name" fields. */
    var lastName by mutableStateOf("")
        internal set

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val lock = Mutex()
    internal val pending = AtomicInteger(0)

    /** Goes up with every change made on this phone, so a refresh that started before a change can't overwrite it. */
    internal val stamp = AtomicInteger(0)

    val current: SharedList? get() = lists.firstOrNull { it.code == active }
    /** Your name on gift lists: always your profile name (owner, 2.102: never asked, no other name per list). */
    val me: String get() = lastName
    val isCreator: Boolean get() = current?.owner?.isNotEmpty() == true

    const val FOR_MAX = 40

    internal fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        lastName = p.getString("sh_me", "") ?: ""
        // Read carefully: these hold the keys (and creator rights) of the lists, which exist nowhere else.
        lists = SafeStore.readList(p, "shared") { o ->
            SharedList(
                o.getString("code"), o.getString("key"), o.optString("name", "Shared wishlist"),
                o.optString("owner", ""), o.optString("me", "")
            )
        }
        active = null
        gone = false
    }

    internal fun saveLists(c: Context) {
        val a = JSONArray()
        lists.forEach {
            a.put(
                JSONObject().put("code", it.code).put("key", it.key).put("name", it.name)
                    .put("owner", it.owner).put("me", it.me)
            )
        }
        prefs(c).edit().putString("shared", a.toString()).apply()
    }

    internal fun rememberName(c: Context, name: String) {
        if (name.isBlank()) return
        lastName = name
        prefs(c).edit().putString("sh_me", name).apply()
    }

    // ---- the copy kept on the phone, for looking at a list without internet ----

    internal fun cacheKey(code: String) = "shc_$code"

    internal fun readCache(c: Context, code: String): SharedContent? {
        val s = prefs(c).getString(cacheKey(code), null) ?: return null
        return try {
            val o = JSONObject(s)
            val cs = o.optJSONArray("cats")
            val items = o.optJSONArray("items")
            SharedContent(
                o.optString("name", ""),
                if (cs == null) emptyList() else (0 until cs.length()).map { cs.getString(it) },
                o.optString("about", ""),
                if (items == null) emptyList() else (0 until items.length()).map { wishFromJson(items.getJSONObject(it)) },
                Occasion.fromJson(o.optJSONObject("occ"))
            )
        } catch (e: Exception) {
            null
        }
    }

    /** The wishes of group wishlist [code] as last seen on this phone (for its block on Home), without the bin. */
    fun cachedWishes(c: Context, code: String): List<Wish> =
        (if (active == code) content.wishes else readCache(c, code)?.wishes.orEmpty()).filter { it.bin == 0L }

    internal fun writeCache(c: Context, code: String, ct: SharedContent) {
        val items = JSONArray()
        ct.wishes.forEach { items.put(wishToJson(it)) }
        val o = JSONObject().put("name", ct.name).put("cats", JSONArray(ct.cats))
            .put("about", ct.about).put("items", items)
        ct.occ?.let { o.put("occ", it.toJson()) }
        prefs(c).edit().putString(cacheKey(code), o.toString()).apply()
    }

    // ---- opening and refreshing ----

    fun open(c: Context, code: String) {
        val l = lists.firstOrNull { it.code == code } ?: return
        content = readCache(c, code) ?: SharedContent(name = l.name)
        SharedReservations.reserved = emptyMap()
        gone = false
        active = code
    }

    fun close() {
        active = null
        gone = false
    }

    fun clearNotice() {
        notice = ""
    }

    /** A save of this phone is still on its way (a refresh now would be thrown away). */
    fun busy(): Boolean = pending.get() > 0

    /** Fetches the online copy of the open list. Returns false when the server couldn't be reached (the screen keeps what it has). */
    suspend fun refresh(c: Context): Boolean {
        val l = current ?: return true
        if (pending.get() > 0) return true
        val s = stamp.get()
        val r = try {
            Cloud.shGet(l.key)
        } catch (e: Exception) {
            return false
        }
        // The user may have switched lists, or changed something, while we were waiting: then this answer is already old.
        if (active != l.code || pending.get() > 0 || stamp.get() != s) return true
        if (r == null) {
            gone = true
            return true
        }
        gone = false
        // You're on this list's members (2.128), and maybe its new owner: once per list each time the app starts.
        Members.seen(c, l)
        // Wishes in the bin are not on the list (the Bin screen shows them, see Bin.kt).
        val nc = SharedContent(r.list.name, r.list.cats, r.list.about, r.list.wishes.filter { it.bin == 0L }, r.list.occ)
        if (nc != content) content = nc
        writeCache(c, l.code, nc)
        if (r.list.name != l.name) {
            lists = lists.map { if (it.code == l.code) it.copy(name = r.list.name) else it }
            saveLists(c)
        }
        loadReservations(l.code)
        return true
    }

    // ---- changing the open list: shown at once, saved online one wish at a time, undone if saving fails ----

    internal fun write(
        c: Context, l: SharedList, before: SharedContent, after: SharedContent,
        failed: (Exception) -> String? = { null }, job: suspend () -> Unit
    ) {
        content = after
        stamp.incrementAndGet()
        pending.incrementAndGet()
        scope.launch {
            var ok = false
            try {
                lock.withLock { job() }
                ok = true
            } catch (e: Exception) {
                notice = failed(e) ?: tr("Couldn’t save that. Check your connection and try again.")
            } finally {
                pending.decrementAndGet()
            }
            if (ok) {
                if (active == l.code) writeCache(c, l.code, content)
                // Other people's changes to other parts of the same wish were kept online: show them.
                if (active == l.code && pending.get() == 0) refresh(c)
            } else if (active == l.code) {
                // Nothing else changed in the meantime: go back to how it was. Otherwise ask the server what is true.
                if (content === after) content = before else { refresh(c) }
            }
        }
    }
}
