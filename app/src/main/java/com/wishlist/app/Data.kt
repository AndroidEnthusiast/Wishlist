package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Currency
import java.util.UUID

// A new field here must ALSO be added to wishToJson / wishFromJson below and to changedFields / applyFields in
// Shared.kt (and to the web pages), or it won't be saved when someone edits a shared wish. (Like "by", the bin fields
// are not edited in the form: only moving to the bin and restoring write them.)
data class Wish(
    val id: Long,
    val url: String,
    val title: String,
    val desc: String,
    val image: String,
    val price: Double?,
    val oldPrice: Double?,
    val currency: String,
    val category: String,
    val got: Boolean = false,
    /** "Most wanted": shown with a star, so givers know where to look first. */
    val star: Boolean = false,
    /** A note for givers: colour, details, anything they should know. */
    val note: String = "",
    /** One of SIZES ("XS" to "XL"), or empty for no size. */
    val size: String = "",
    /** On a shared wishlist: the name of the person who added it. Empty on My wishlist. */
    val by: String = "",
    /** In the bin since this moment (milliseconds), or 0 when it is on the list. See Bin.kt. */
    val bin: Long = 0L,
    /** On a shared wishlist: the name of the person who moved it to the bin. */
    val binBy: String = ""
)

/** A wishlist that someone else shared with you. */
data class Friend(val code: String, val name: String)

fun wishToJson(w: Wish): JSONObject = JSONObject().apply {
    put("id", w.id); put("url", w.url); put("title", w.title)
    put("desc", w.desc); put("image", w.image)
    put("price", w.price ?: JSONObject.NULL)
    put("old", w.oldPrice ?: JSONObject.NULL)
    put("cur", w.currency); put("cat", w.category); put("got", w.got)
    if (w.star) put("star", true)
    if (w.note.isNotBlank()) put("note", w.note)
    if (w.size.isNotBlank()) put("size", w.size)
    if (w.by.isNotBlank()) put("by", w.by)
    if (w.bin > 0L) put("bin", w.bin)
    if (w.binBy.isNotBlank()) put("binBy", w.binBy)
}

fun wishFromJson(o: JSONObject): Wish = Wish(
    o.getLong("id"), o.getString("url"), o.getString("title"),
    o.optString("desc"), o.optString("image"),
    if (o.isNull("price")) null else o.getDouble("price"),
    if (o.isNull("old")) null else o.getDouble("old"),
    o.optString("cur", "USD"), o.optString("cat", ""), o.optBoolean("got"),
    o.optBoolean("star"), o.optString("note", ""), o.optString("size", ""), o.optString("by", ""),
    o.optLong("bin", 0L), o.optString("binBy", "")
)

object Repo {
    /** My wishlist (stored on this phone, mirrored online for friends when shared). */
    var myWishes by mutableStateOf(listOf<Wish>())
        private set
    /** The bin of My wishlist: deleted wishes, kept for [BIN_DAYS] days (newest first). Not on the list and never price-checked. */
    var myBin by mutableStateOf(listOf<Wish>())
        private set
    var myCats by mutableStateOf(listOf("Tech", "Home", "Fashion", "Books"))
        private set
    var theme by mutableStateOf("system")
        private set
    /** The colours of the app: "wallpaper" (Android 12+) or one of PALETTES in Theme.kt. Indigo is the original. */
    var palette by mutableStateOf("indigo")
        private set
    /** The font of the app: one of FONTS in Fonts.kt. Google Sans is the original. */
    var font by mutableStateOf("google_sans")
        private set
    /** On: wishes are deleted without asking first (Undo is still offered). Off (the default): "Delete this wish?" is asked. */
    var instantDelete by mutableStateOf(false)
        private set
    /** Notifications when a price drops (on unless switched off) or goes up (off unless switched on). */
    var notifyDrops by mutableStateOf(true)
        private set
    var notifyRises by mutableStateOf(false)
        private set
    var myName by mutableStateOf("My wishlist")
        private set
    /** How the wishes are shown: "list" (large cards), "grid" (two per row) or "gallery" (images only). */
    var view by mutableStateOf("list")
        private set
    /** How the wishes are ordered: "new", "star", "low", "high" or "az" (see Sorting.kt). */
    var sort by mutableStateOf("new")
        private set
    /** A note shown to friends at the top of the shared list (sizes, things you don't want, ...). */
    var myAbout by mutableStateOf("")
        private set

    // What the screens show: My wishlist, or the shared wishlist that is open (see Shared.kt).
    val wishes: List<Wish> get() = if (Shared.active == null) myWishes else Shared.content.wishes
    val cats: List<String> get() = if (Shared.active == null) myCats else Shared.content.cats
    val listName: String
        get() = if (Shared.active == null) (if (myName == "My wishlist") tr(myName) else myName)
        else Shared.content.name.ifBlank { tr("Shared wishlist") }
    val about: String get() = if (Shared.active == null) myAbout else Shared.content.about
    /** Written on wishes you add to a shared wishlist ("Added by ..."). Empty on My wishlist. */
    val authorName: String get() = if (Shared.active == null) "" else Shared.me

    /** How often the background price check should run, in hours. */
    var intervalHours by mutableStateOf(6)
        private set
    /** When the last background price check finished (0 = never). */
    var lastCheck by mutableStateOf(0L)
        private set

    /** Set once the owner has shared the list online. */
    var shareCode by mutableStateOf<String?>(null)
        private set
    var ownerToken: String? = null
        private set
    /** File names of pictures already uploaded for the current share code. */
    var uploadedPhotos: Set<String> = emptySet()
        private set
    var friends by mutableStateOf(listOf<Friend>())
        private set
    var deviceId: String = ""
        private set
    private var loaded = false

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        p.getString("wishes", null)?.let { s ->
            try {
                val a = JSONArray(s)
                myWishes = (0 until a.length()).map { i -> wishFromJson(a.getJSONObject(i)) }
            } catch (e: Exception) {
            }
        }
        p.getString("bin", null)?.let { s ->
            try {
                val a = JSONArray(s)
                myBin = (0 until a.length()).map { i -> wishFromJson(a.getJSONObject(i)) }
            } catch (e: Exception) {
            }
        }
        History.load(c)
        History.seed(c, myWishes)
        p.getString("cats", null)?.let { s ->
            myCats = s.split("\n").filter { it.isNotBlank() }
        }
        theme = p.getString("theme", "system") ?: "system"
        palette = p.getString("palette", "indigo") ?: "indigo"
        font = p.getString("font", "google_sans") ?: "google_sans"
        instantDelete = p.getBoolean("instant_delete", false)
        notifyDrops = p.getBoolean("notify_drops", true)
        notifyRises = p.getBoolean("notify_rises", false)
        view = p.getString("view", "list") ?: "list"
        sort = p.getString("sort", "new")?.takeIf { s -> SORT_OPTIONS.any { it.first == s } } ?: "new"
        myAbout = p.getString("about", "") ?: ""
        intervalHours = p.getInt("interval", 6).let { if (it in listOf(1, 3, 6, 12, 24)) it else 6 }
        lastCheck = p.getLong("lastCheck", 0L)
        myName = p.getString("name", null)?.takeIf { it.isNotBlank() } ?: "My wishlist"
        shareCode = p.getString("code", null)
        ownerToken = p.getString("token", null)
        uploadedPhotos = p.getStringSet("photosUp", null)?.toSet() ?: emptySet()
        Lang.load(c)
        Sync.attach(c)
        Shared.load(c)
        p.getString("friends", null)?.let { s ->
            try {
                val a = JSONArray(s)
                friends = (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Friend(o.getString("code"), o.optString("name", "Wishlist"))
                }
            } catch (e: Exception) {
            }
        }
        deviceId = p.getString("device", null)
            ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    private fun persist(c: Context) {
        val arr = JSONArray()
        myWishes.forEach { arr.put(wishToJson(it)) }
        val binArr = JSONArray()
        myBin.forEach { binArr.put(wishToJson(it)) }
        val fr = JSONArray()
        friends.forEach { fr.put(JSONObject().put("code", it.code).put("name", it.name)) }
        prefs(c).edit()
            .putString("wishes", arr.toString())
            .putString("bin", binArr.toString())
            .putString("cats", myCats.joinToString("\n"))
            .putString("theme", theme)
            .putString("palette", palette)
            .putString("font", font)
            .putString("view", view)
            .putString("sort", sort)
            .putString("about", myAbout)
            .putInt("interval", intervalHours)
            .putBoolean("instant_delete", instantDelete)
            .putBoolean("notify_drops", notifyDrops)
            .putBoolean("notify_rises", notifyRises)
            .putLong("lastCheck", lastCheck)
            .putString("name", myName)
            .putString("code", shareCode)
            .putString("token", ownerToken)
            .putStringSet("photosUp", uploadedPhotos.toSet())
            .putString("friends", fr.toString())
            .apply()
    }

    /** Saves locally and, if the list is shared, pushes the change online. */
    private fun commit(c: Context) {
        persist(c)
        Sync.push()
    }

    @Synchronized
    fun add(c: Context, w: Wish) {
        if (Shared.active != null) {
            // Shared lists keep no price history: the givers only want to see what could be given.
            Shared.put(c, w)
            return
        }
        addMine(c, w)
    }

    /** Adds to My wishlist, whatever list is open. */
    @Synchronized
    fun addMine(c: Context, w: Wish) {
        myWishes = listOf(w) + myWishes
        commit(c)
        w.price?.let { History.record(c, w.id, it) }
    }

    /** [base] is the wish as the edit form started (only for shared lists: it decides which fields count as changed). */
    @Synchronized
    fun replace(c: Context, w: Wish, base: Wish? = null) {
        if (Shared.active != null) {
            Shared.put(c, w, base)
            return
        }
        replaceMine(c, w)
    }

    /** Changes a wish on My wishlist, whatever list is open (used by the background price check). */
    @Synchronized
    fun replaceMine(c: Context, w: Wish) {
        val before = myWishes.firstOrNull { it.id == w.id }
        myWishes = myWishes.map { if (it.id == w.id) w else it }
        commit(c)
        // A changed price (found by a check, or typed in by you) becomes a point in the history.
        val np = w.price
        if (np != null && np != before?.price) History.record(c, w.id, np)
    }

    // ---- deleting with Undo (begin) ----

    /** The wish of My wishlist that was just deleted. Friends keep seeing it online until the undo time is over. */
    @Volatile
    private var heldMine: Trashed? = null

    /**
     * My wishlist as it goes online for friends: a wish that was just deleted is still in it until the undo time is
     * over. The bin goes along too (marked "bin", so friends don't see it), so a reservation on it stays when it is restored.
     */
    fun syncWishes(): List<Wish> {
        val h = heldMine ?: return myWishes + myBin
        return myWishes.toMutableList().also { it.add(h.index.coerceIn(0, it.size), h.wish) } + myBin
    }

    /**
     * Takes a wish off the open list at once, but keeps everything of it (online copy and reservations, picture,
     * price history) until [finishTrash]. Null if the wish isn't there.
     */
    @Synchronized
    fun trash(c: Context, id: Long): Trashed? {
        if (Shared.active != null) return Shared.trash(id)
        val i = myWishes.indexOfFirst { it.id == id }
        if (i < 0) return null
        val t = Trashed(myWishes[i], i, null)
        heldMine = t
        myWishes = myWishes.filter { it.id != id }
        persist(c) // no online push: friends still see it, with any reservation on it
        return t
    }

    /** Undo: the wish comes back where it was. */
    @Synchronized
    fun untrash(c: Context, t: Trashed) {
        if (t.listCode != null) {
            Shared.untrash(t)
            return
        }
        if (heldMine === t) heldMine = null
        if (myWishes.none { it.id == t.wish.id }) {
            myWishes = myWishes.toMutableList().also { it.add(t.index.coerceIn(0, it.size), t.wish) }
        }
        commit(c)
    }

    /** The undo time is over: the wish goes to the bin. Its picture and price history stay until it leaves the bin. */
    @Synchronized
    fun finishTrash(c: Context, t: Trashed) {
        if (t.listCode != null) {
            Shared.finishTrash(c, t)
            return
        }
        if (heldMine === t) heldMine = null
        if (myBin.none { it.id == t.wish.id }) myBin = listOf(t.wish.copy(bin = System.currentTimeMillis())) + myBin
        commit(c)
    }

    /** Takes a wish out of the bin and puts it back at the top of My wishlist. */
    @Synchronized
    fun restoreMine(c: Context, id: Long): Boolean {
        val w = myBin.firstOrNull { it.id == id } ?: return false
        myBin = myBin.filter { it.id != id }
        if (myWishes.none { it.id == id }) myWishes = listOf(w.copy(bin = 0L, binBy = "")) + myWishes
        commit(c)
        return true
    }

    /** Deletes wishes from the bin for good, with their pictures and price history. */
    @Synchronized
    fun dropFromBin(c: Context, ids: Set<Long>) {
        val gone = myBin.filter { it.id in ids }
        if (gone.isEmpty()) return
        myBin = myBin.filter { it.id !in ids }
        commit(c)
        gone.forEach {
            ImageStore.delete(c, it.image)
            History.remove(c, it.id)
        }
    }

    // ---- deleting with Undo (end) ----

    /** A category name without line breaks or doubled spaces. */
    fun cleanCatName(name: String): String = name.replace(Regex("\\s+"), " ").trim()

    /** Adds a category and returns its name. If one with that name exists (in any capitals) that one is returned. Null for an empty name or "All". */
    @Synchronized
    fun addCat(c: Context, name: String): String? {
        val n = cleanCatName(name)
        if (n.isEmpty() || n.equals("All", ignoreCase = true)) return null
        val existing = cats.firstOrNull { it.equals(n, ignoreCase = true) }
        if (existing != null) return existing
        if (Shared.active != null) return Shared.addCat(c, n)
        myCats = myCats + n
        commit(c)
        return n
    }

    /** Renames a category. Wishes in it move along. False if the new name is empty, "All" or taken by another category. */
    @Synchronized
    fun renameCat(c: Context, old: String, name: String): Boolean {
        val n = cleanCatName(name)
        if (old !in cats || n.isEmpty() || n.equals("All", ignoreCase = true)) return false
        if (cats.any { it != old && it.equals(n, ignoreCase = true) }) return false
        if (n == old) return true
        if (Shared.active != null) return Shared.renameCat(c, old, n)
        myCats = myCats.map { if (it == old) n else it }
        myWishes = myWishes.map { if (it.category == old) it.copy(category = n) else it }
        myBin = myBin.map { if (it.category == old) it.copy(category = n) else it }
        commit(c)
        return true
    }

    /** Removes a category. Wishes that were in it stay, just without a category. */
    @Synchronized
    fun removeCat(c: Context, name: String) {
        if (Shared.active != null) {
            Shared.removeCat(c, name)
            return
        }
        myCats = myCats.filter { it != name }
        myWishes = myWishes.map { if (it.category == name) it.copy(category = "") else it }
        myBin = myBin.map { if (it.category == name) it.copy(category = "") else it }
        commit(c)
    }

    @Synchronized
    fun setListName(c: Context, name: String) {
        val n = name.trim().ifEmpty { if (Shared.active != null) "Shared wishlist" else "My wishlist" }
        if (Shared.active != null) {
            Shared.setName(c, n)
            return
        }
        myName = n
        commit(c)
    }

    @Synchronized
    fun setTheme(c: Context, t: String) { theme = t; persist(c) }
    fun setPalette(c: Context, p: String) { palette = p; persist(c) }
    fun setFont(c: Context, f: String) { font = f; persist(c) }

    @Synchronized
    fun setInstantDelete(c: Context, on: Boolean) { instantDelete = on; persist(c) }
    fun setNotifyDrops(c: Context, on: Boolean) { notifyDrops = on; persist(c) }
    fun setNotifyRises(c: Context, on: Boolean) { notifyRises = on; persist(c) }

    @Synchronized
    fun setView(c: Context, v: String) { view = v; persist(c) }

    @Synchronized
    fun setSort(c: Context, s: String) { sort = s; persist(c) }

    /** Saves the note for friends and, if the list is shared, sends it along. */
    @Synchronized
    fun setAbout(c: Context, text: String) {
        val t = text.trim().take(ABOUT_MAX)
        if (Shared.active != null) {
            Shared.setAbout(c, t)
            return
        }
        myAbout = t
        commit(c)
    }

    @Synchronized
    fun setInterval(c: Context, hours: Int) { intervalHours = hours; persist(c) }

    @Synchronized
    fun setLastCheck(c: Context, t: Long) { lastCheck = t; persist(c) }

    @Synchronized
    fun setShare(c: Context, code: String?, token: String?) {
        shareCode = code; ownerToken = token; persist(c)
    }

    @Synchronized
    fun setUploaded(c: Context, names: Set<String>) { uploadedPhotos = names; persist(c) }

    @Synchronized
    fun addFriend(c: Context, f: Friend) { friends = friends.filter { it.code != f.code } + f; persist(c) }

    @Synchronized
    fun removeFriend(c: Context, code: String) { friends = friends.filter { it.code != code }; persist(c) }

    @Synchronized
    fun renameFriend(c: Context, code: String, name: String) {
        friends = friends.map { if (it.code == code) it.copy(name = name) else it }; persist(c)
    }
}

fun money(p: Double, cur: String): String = try {
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(cur) }.format(p)
} catch (e: Exception) {
    "%.2f".format(p)
}
