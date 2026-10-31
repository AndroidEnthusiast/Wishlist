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
    val wishes: List<Wish> = emptyList()
)

/**
 * Wishlists you edit together with others. The online copy is the truth: every change is sent as a small
 * change (only the fields you changed in one wish, one category, the name...), never the whole list, so two
 * people editing at the same time don't undo each other. The screen refreshes from the online copy every few
 * seconds. Editing needs internet; the last copy is kept on the phone so you can still look at the list offline.
 */
object Shared {
    var lists by mutableStateOf(listOf<SharedList>())
        private set

    /** Code of the shared wishlist that is open on screen, or null when My wishlist is. */
    var active by mutableStateOf<String?>(null)
        private set
    var content by mutableStateOf(SharedContent())
        private set

    /** True when the open list can't be reached with our key anymore (deleted, or the invite link was reset). */
    var gone by mutableStateOf(false)
        private set

    /** A short message for the snackbar (for example "Couldn't save that"). Cleared once shown. */
    var notice by mutableStateOf("")
        private set

    /**
     * Reservations on the open list: wish id -> true (reserved on this phone) or false (by someone else).
     * Everyone on a shared list sees them: the list is only shared between the givers, so nobody buys a gift twice.
     */
    var reserved by mutableStateOf(mapOf<Long, Boolean>())
        private set
    @Volatile
    private var resBusy = false

    /** The name typed last time, to prefill the "your name" fields. */
    var lastName by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val pending = AtomicInteger(0)

    /** Goes up with every change made on this phone, so a refresh that started before a change can't overwrite it. */
    private val stamp = AtomicInteger(0)

    /** Wishes that were just deleted here but can still be undone. They are still online, so they stay hidden from refreshes. */
    private val hidden = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    val current: SharedList? get() = lists.firstOrNull { it.code == active }
    val me: String get() = current?.me.orEmpty()
    val isCreator: Boolean get() = current?.owner?.isNotEmpty() == true

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        lastName = p.getString("sh_me", "") ?: ""
        lists = try {
            val a = JSONArray(p.getString("shared", null) ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SharedList(
                    o.getString("code"), o.getString("key"), o.optString("name", "Shared wishlist"),
                    o.optString("owner", ""), o.optString("me", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        active = null
        gone = false
    }

    private fun saveLists(c: Context) {
        val a = JSONArray()
        lists.forEach {
            a.put(
                JSONObject().put("code", it.code).put("key", it.key).put("name", it.name)
                    .put("owner", it.owner).put("me", it.me)
            )
        }
        prefs(c).edit().putString("shared", a.toString()).apply()
    }

    private fun rememberName(c: Context, name: String) {
        if (name.isBlank()) return
        lastName = name
        prefs(c).edit().putString("sh_me", name).apply()
    }

    /**
     * "Your name" in the app's Settings: used on every shared list, except a list where you chose another name
     * (in that list's settings). Lists that used the old name follow the new one.
     */
    fun setAppName(c: Context, name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        val old = lastName
        rememberName(c, n)
        lists = lists.map { if (it.me.isBlank() || it.me == old) it.copy(me = n) else it }
        saveLists(c)
    }

    /** Your name on one shared list only (its settings). Empty: use the name from the app's Settings. */
    fun setMe(c: Context, code: String, name: String) {
        val n = name.trim().take(30).ifEmpty { lastName }
        lists = lists.map { if (it.code == code) it.copy(me = n) else it }
        saveLists(c)
    }

    // ---- the copy kept on the phone, for looking at a list without internet ----

    private fun cacheKey(code: String) = "shc_$code"

    private fun readCache(c: Context, code: String): SharedContent? {
        val s = prefs(c).getString(cacheKey(code), null) ?: return null
        return try {
            val o = JSONObject(s)
            val cs = o.optJSONArray("cats")
            val items = o.optJSONArray("items")
            SharedContent(
                o.optString("name", ""),
                if (cs == null) emptyList() else (0 until cs.length()).map { cs.getString(it) },
                o.optString("about", ""),
                if (items == null) emptyList() else (0 until items.length()).map { wishFromJson(items.getJSONObject(it)) }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun writeCache(c: Context, code: String, ct: SharedContent) {
        val items = JSONArray()
        ct.wishes.forEach { items.put(wishToJson(it)) }
        val o = JSONObject().put("name", ct.name).put("cats", JSONArray(ct.cats))
            .put("about", ct.about).put("items", items)
        prefs(c).edit().putString(cacheKey(code), o.toString()).apply()
    }

    // ---- opening and refreshing ----

    fun open(c: Context, code: String) {
        val l = lists.firstOrNull { it.code == code } ?: return
        content = readCache(c, code) ?: SharedContent(name = l.name)
        reserved = emptyMap()
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
        // Wishes in the bin are not on the list (the Bin screen shows them, see Bin.kt).
        val nc = SharedContent(r.list.name, r.list.cats, r.list.about, r.list.wishes.filter { it.id !in hidden && it.bin == 0L })
        if (nc != content) content = nc
        writeCache(c, l.code, nc)
        if (r.list.name != l.name) {
            lists = lists.map { if (it.code == l.code) it.copy(name = r.list.name) else it }
            saveLists(c)
        }
        loadReservations(l.code)
        return true
    }

    // ---- reservations: the givers reserve what they will buy, and everyone on the list sees it ----

    private suspend fun loadReservations(code: String) {
        if (resBusy) return
        val m = try {
            Cloud.fetchReservations(code, Repo.deviceId)
        } catch (e: Exception) {
            return
        }
        if (active == code && !resBusy) reserved = m
    }

    /** Reserves a wish of the open list ([on] = true) or takes this phone's reservation away again. */
    fun reserve(w: Wish, on: Boolean) {
        val l = current ?: return
        if (resBusy) return
        val before = reserved
        reserved = if (on) before + (w.id to true) else before - w.id
        resBusy = true
        scope.launch {
            try {
                if (on) {
                    if (!Cloud.reserve(l.code, w.id, Repo.deviceId)) notice = tr("Someone else just reserved that one.")
                } else {
                    Cloud.unreserve(l.code, w.id, Repo.deviceId)
                }
            } catch (e: Exception) {
                if (active == l.code) reserved = before
                notice = tr("Couldn’t save that. Check your connection and try again.")
            } finally {
                resBusy = false
            }
            loadReservations(l.code)
        }
    }

    // ---- changing the open list: shown at once, saved online one wish at a time, undone if saving fails ----

    private fun write(c: Context, l: SharedList, before: SharedContent, after: SharedContent, job: suspend () -> Unit) {
        content = after
        stamp.incrementAndGet()
        pending.incrementAndGet()
        scope.launch {
            var ok = false
            try {
                lock.withLock { job() }
                ok = true
            } catch (e: Exception) {
                notice = tr("Couldn’t save that. Check your connection and try again.")
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

    private fun photoNameOf(code: String, url: String): String? {
        val prefix = Cloud.photoUrl(code, "")
        if (!url.startsWith(prefix)) return null
        return url.removePrefix(prefix).takeIf { it.isNotEmpty() && !it.contains('/') }
    }

    suspend fun dropPhoto(l: SharedList, url: String) {
        val n = photoNameOf(l.code, url) ?: return
        try {
            Cloud.deletePhoto(l.code, l.key, n, "x-edit-key")
        } catch (e: Exception) {
        }
    }

    /** The names (as stored online) of the fields that differ between two versions of a wish. ("by" is only set when a wish is made.) */
    private fun changedFields(a: Wish, b: Wish): List<String> {
        val f = ArrayList<String>()
        if (a.url != b.url) f.add("url")
        if (a.title != b.title) f.add("title")
        if (a.desc != b.desc) f.add("desc")
        if (a.image != b.image) f.add("image")
        if (a.price != b.price) f.add("price")
        if (a.oldPrice != b.oldPrice) f.add("old")
        if (a.currency != b.currency) f.add("cur")
        if (a.category != b.category) f.add("cat")
        if (a.got != b.got) f.add("got")
        if (a.star != b.star) f.add("star")
        if (a.note != b.note) f.add("note")
        if (a.size != b.size) f.add("size")
        return f
    }

    /** [into] with only the given fields taken from [from]. */
    private fun applyFields(into: Wish, from: Wish, fields: List<String>): Wish {
        var w = into
        for (f in fields) {
            w = when (f) {
                "url" -> w.copy(url = from.url)
                "title" -> w.copy(title = from.title)
                "desc" -> w.copy(desc = from.desc)
                "image" -> w.copy(image = from.image)
                "price" -> w.copy(price = from.price)
                "old" -> w.copy(oldPrice = from.oldPrice)
                "cur" -> w.copy(currency = from.currency)
                "cat" -> w.copy(category = from.category)
                "got" -> w.copy(got = from.got)
                "star" -> w.copy(star = from.star)
                "note" -> w.copy(note = from.note)
                "size" -> w.copy(size = from.size)
                else -> w
            }
        }
        return w
    }

    /**
     * Adds a wish, or saves a changed one. [base] is the version the edit form was opened on (leave it out for quick
     * changes like the star). Only the fields that differ from it are written online, on top of what is online now,
     * so a change someone else made to another field of the same wish stays. A picture picked on this phone is uploaded first.
     */
    fun put(c: Context, w: Wish, base: Wish? = null) {
        val l = current ?: return
        val before = content
        val old = before.wishes.firstOrNull { it.id == w.id }
        val mine = if (old == null && w.by.isBlank()) w.copy(by = l.me) else w
        // null = a new wish here: all of it is sent.
        val fields = if (old != null) changedFields(base ?: old, mine) else null
        if (fields != null && fields.isEmpty()) return
        val was = (base ?: old)?.image
        val shown = if (old != null && fields != null) applyFields(old, mine, fields) else mine
        val after = before.copy(
            wishes = if (old != null) before.wishes.map { if (it.id == w.id) shown else it } else listOf(mine) + before.wishes
        )
        write(c, l, before, after) {
            var out = mine
            val sendsImage = fields == null || "image" in fields
            if (sendsImage && ImageStore.isLocal(out.image)) {
                val name = out.image.substringAfterLast('/')
                val bytes = ImageStore.shareCopy(out.image)
                out = if (bytes != null) {
                    try {
                        Cloud.uploadPhoto(l.code, l.key, name, bytes, "x-edit-key")
                        out.copy(image = Cloud.photoUrl(l.code, name))
                    } catch (e: Exception) {
                        notice = tr("Saved without the picture: it couldn’t be uploaded.")
                        out.copy(image = "")
                    }
                } else out.copy(image = "")
            }
            Cloud.shPut(l.key, out, fields)
            if (out.image != mine.image) {
                // The picture now lives online. Show that one here too and free the copy on this phone.
                ImageStore.delete(c, mine.image)
                if (active == l.code) {
                    content = content.copy(
                        wishes = content.wishes.map { if (it.id == out.id && it.image == mine.image) out else it }
                    )
                }
            }
            // A picture that this save replaced or removed is deleted online too (never one someone else just set).
            if (fields != null && "image" in fields && was != null && was != out.image) dropPhoto(l, was)
        }
    }

    // ---- deleting with Undo: hidden at once, removed online only when the undo time is over ----

    /** Hides a wish of the open list. It stays online (with any reservation on it) until [finishTrash]. */
    fun trash(id: Long): Trashed? {
        val l = current ?: return null
        val i = content.wishes.indexOfFirst { it.id == id }
        if (i < 0) return null
        val w = content.wishes[i]
        hidden.add(id)
        stamp.incrementAndGet()
        content = content.copy(wishes = content.wishes.filter { it.id != id })
        return Trashed(w, i, l.code)
    }

    /** Undo: the wish is shown again where it was. It never left the online copy. */
    fun untrash(t: Trashed) {
        hidden.remove(t.wish.id)
        if (active == t.listCode && content.wishes.none { it.id == t.wish.id }) {
            stamp.incrementAndGet()
            content = content.copy(
                wishes = content.wishes.toMutableList().also { it.add(t.index.coerceIn(0, it.size), t.wish) }
            )
        }
    }

    /**
     * The undo time is over: the wish goes to the list's bin online, for everyone (only the fields "bin" and "binBy"
     * are written; its picture and any reservation on it stay). If that fails it comes back with a message.
     */
    fun finishTrash(c: Context, t: Trashed) {
        val l = lists.firstOrNull { it.code == t.listCode }
        if (l == null) {
            hidden.remove(t.wish.id)
            return
        }
        val at = System.currentTimeMillis()
        Bin.binnedHere(c, l.code, t.wish.id, at)
        val remove: suspend () -> Unit = {
            try {
                Cloud.shPut(l.key, t.wish.copy(bin = at, binBy = l.me), listOf("bin", "binBy"))
            } finally {
                hidden.remove(t.wish.id)
            }
        }
        if (active == l.code) {
            val shown = content
            val before = shown.copy(wishes = shown.wishes.toMutableList().also { it.add(t.index.coerceIn(0, it.size), t.wish) })
            write(c, l, before, shown, remove)
        } else {
            // Another list is open now: just move it to the bin online. A failure shows up as the wish still being there next time.
            scope.launch {
                try {
                    lock.withLock { remove() }
                } catch (e: Exception) {
                }
            }
        }
    }

    fun delete(c: Context, id: Long) {
        val l = current ?: return
        val before = content
        val removed = before.wishes.firstOrNull { it.id == id } ?: return
        val after = before.copy(wishes = before.wishes.filter { it.id != id })
        write(c, l, before, after) {
            Cloud.shDelete(l.key, id)
            ImageStore.delete(c, removed.image)
            dropPhoto(l, removed.image)
        }
    }

    fun setName(c: Context, name: String) {
        val l = current ?: return
        val before = content
        val after = before.copy(name = name)
        lists = lists.map { if (it.code == l.code) it.copy(name = name) else it }
        saveLists(c)
        write(c, l, before, after) { Cloud.shName(l.key, name) }
    }

    fun setAbout(c: Context, about: String) {
        val l = current ?: return
        val before = content
        val after = before.copy(about = about)
        write(c, l, before, after) { Cloud.shAbout(l.key, about) }
    }

    /** [name] is already cleaned and known to be new (Repo.addCat checks that). */
    fun addCat(c: Context, name: String): String? {
        val l = current ?: return null
        val before = content
        val after = before.copy(cats = before.cats + name)
        write(c, l, before, after) { Cloud.shCatAdd(l.key, name) }
        return name
    }

    fun renameCat(c: Context, old: String, name: String): Boolean {
        val l = current ?: return false
        val before = content
        val after = before.copy(
            cats = before.cats.map { if (it == old) name else it },
            wishes = before.wishes.map { if (it.category == old) it.copy(category = name) else it }
        )
        // The online copy renames the category and moves every wish in it, including ones this phone hasn't seen yet.
        write(c, l, before, after) { Cloud.shCatRename(l.key, old, name) }
        return true
    }

    fun removeCat(c: Context, name: String) {
        val l = current ?: return
        val before = content
        val after = before.copy(
            cats = before.cats.filter { it != name },
            wishes = before.wishes.map { if (it.category == name) it.copy(category = "") else it }
        )
        write(c, l, before, after) { Cloud.shCatRemove(l.key, name) }
    }

    /**
     * The invitation key in text from the clipboard (the invite page copies its link when "Download app" is tapped),
     * or null when there is none, or this phone is on that list already.
     */
    fun inviteFromClipboard(c: Context, text: String?): String? {
        if (text.isNullOrBlank() || text.length > 500) return null
        // Only a real invite link counts, never a loose string of letters.
        if (!text.uppercase().contains("J=")) return null
        val key = Sync.parseInvite(text) ?: return null
        if (lists.any { it.key == key }) return null
        return key
    }

    // ---- making, joining and leaving lists ----

    /** Makes a new shared wishlist online and adds it to this phone. */
    suspend fun create(c: Context, name: String, me: String): SharedList {
        val n = name.trim().ifEmpty { tr("Our wishlist") }
        val code = Sync.newCode()
        val key = Sync.newKey()
        val token = Sync.newKey()
        Cloud.shCreate(code, key, token, n)
        val l = SharedList(code, key, n, token, me.trim())
        rememberName(c, l.me)
        lists = lists + l
        saveLists(c)
        return l
    }

    /** Accepts an invite. Returns the list, or null when the invite doesn't work (anymore). */
    suspend fun join(c: Context, key: String, me: String): SharedList? {
        val r = Cloud.shGet(key) ?: return null
        val name = me.trim()
        rememberName(c, name)
        val old = lists.firstOrNull { it.code == r.code }
        val l = if (old != null) {
            // Already on this list (for example after the invite link was reset): just take the new key.
            old.copy(key = key, name = r.list.name, me = name.ifEmpty { old.me })
        } else {
            SharedList(r.code, key, r.list.name, "", name)
        }
        lists = if (old != null) lists.map { if (it.code == r.code) l else it } else lists + l
        saveLists(c)
        writeCache(c, r.code, SharedContent(r.list.name, r.list.cats, r.list.about, r.list.wishes))
        return l
    }

    /** Takes the list off this phone only. It stays online for everyone else. */
    fun leave(c: Context, code: String) {
        if (active == code) close()
        lists = lists.filter { it.code != code }
        saveLists(c)
        prefs(c).edit().remove(cacheKey(code)).remove("shp_$code").apply()
        Bin.forget(c, code)
    }

    /** Creator only: deletes the list online for everyone (wishes, reservations, pictures). */
    suspend fun deleteForEveryone(c: Context) {
        val l = current ?: return
        if (l.owner.isEmpty()) throw CloudException(tr("Only the person who made this list can delete it."))
        content.wishes.forEach { dropPhoto(l, it.image) }
        Cloud.deleteList(l.code, l.owner)
        leave(c, l.code)
    }

    /** Creator only: a new edit key. The old invite link stops working. Returns the new key. */
    suspend fun resetKey(c: Context): String {
        val l = current ?: throw CloudException(tr("No shared wishlist is open."))
        if (l.owner.isEmpty()) throw CloudException(tr("Only the person who made this list can do that."))
        val nk = Sync.newKey()
        Cloud.shReset(l.code, l.owner, nk)
        lists = lists.map { if (it.code == l.code) it.copy(key = nk) else it }
        saveLists(c)
        return nk
    }
}
