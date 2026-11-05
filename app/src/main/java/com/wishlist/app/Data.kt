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
// and "chk" are not edited in the form: only moving to the bin, restoring and the price check write them.)
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
    val binBy: String = "",
    /** On a shared wishlist: when a phone last asked the shop for its price (ms), so the other phones can skip it. */
    val checked: Long = 0L,
    /**
     * "Private wish" (My wishlist only): it stays on this phone and never goes online, so friends never see it. It is
     * stored as "priv" on the phone only: [Cloud.pushList] leaves such wishes out, and shared lists never have them.
     */
    val priv: Boolean = false
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
    if (w.checked > 0L) put("chk", w.checked)
    if (w.priv) put("priv", true)
}

fun wishFromJson(o: JSONObject): Wish = Wish(
    o.getLong("id"), o.getString("url"), o.getString("title"),
    o.optString("desc"), o.optString("image"),
    if (o.isNull("price")) null else o.getDouble("price"),
    if (o.isNull("old")) null else o.getDouble("old"),
    o.optString("cur", "USD"), o.optString("cat", ""), o.optBoolean("got"),
    o.optBoolean("star"), o.optString("note", ""), o.optString("size", ""), o.optString("by", ""),
    o.optLong("bin", 0L), o.optString("binBy", ""), o.optLong("chk", 0L), o.optBoolean("priv")
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
    var theme by mutableStateOf("light")
        private set
    /** The accent colour (2.97): one of ACCENTS in Design.kt. (Before: the colour set of the standard themes.) */
    var palette by mutableStateOf("indigo")
        private set
    /**
     * The style (2.97): one of STYLES in Design.kt ("quiet", "neo", "neu", "cyber", "chaos"), with light or dark in
     * [theme] and the accent in [palette]. ([colours] is the old designs' colour set, only read to move people over.)
     */
    var design by mutableStateOf("quiet")
        private set
    var colours by mutableStateOf("")
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
    /** How the wishes are shown: "list" (large cards) or "grid" (two per row). */
    var view by mutableStateOf("rows")
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
        else Shared.content.name.ifBlank { tr("Gift list") }
    val about: String get() = if (Shared.active == null) myAbout else Shared.content.about
    /** Written on wishes you add to a shared wishlist ("Added by ..."). Empty on My wishlist. */
    val authorName: String get() = if (Shared.active == null) "" else Shared.me

    /** How often the background price check should run, in hours. */
    var intervalHours by mutableStateOf(6)
        private set
    /** When the last background price check finished (0 = never). */
    var lastCheck by mutableStateOf(0L)
        private set
    /** Where the last wish shared in from the browser was saved: "mine", "private" or the code of a shared list. */
    var lastSaveTo = "mine"
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

    // ---- several wishlists of your own (see MyLists.kt) ----
    /** The id of your own wishlist that is open (its data is in myWishes, myName, ...). "main" is the original one. */
    var openMine by mutableStateOf(MAIN_LIST)
        private set
    /** All your own wishlists, oldest first. */
    var mineIds by mutableStateOf(listOf(MAIN_LIST))
        private set
    /** The names of your own lists (id to name), for places that only need the name (the bin, menus). */
    var mineNames by mutableStateOf(mapOf<String, String>())
        private set
    /** Goes up whenever a list that isn't open is changed, so the dashboard reads them again. */
    var mineVersion by mutableStateOf(0)
        private set
    /**
     * Wishes copied from one of your lists into another (list id to the ids copied into it). A copy keeps the wish's
     * id, so it is the same wish: when the original is removed, its copies go too (and anyone who reserved a copy
     * hears that it's gone, as with any removed wish). Removing a copy only removes that copy.
     */
    private var copies: Map<String, Set<Long>> = emptyMap()
    /** The copies the last move to the bin took along, so Undo can put them back. */
    private var lastCascade: Pair<Long, List<String>>? = null
    /** Names of deleted lists whose wishes may still be in the bin ("gone_<time>" to name). */
    private var goneNames: Map<String, String> = emptyMap()
    /** Which of your lists a wish in the bin came from (wish id to list id); missing means "main". */
    private var binOwner: Map<Long, String> = emptyMap()
    /** Recently opened lists: "m:<id>" for your own, "s:<code>" for a group wishlist, with the moment opened. */
    var recent by mutableStateOf(mapOf<String, Long>())
        private set
    /** The picture at the top of the dashboard (a file: address), or empty for the theme's own colours. */
    var hero by mutableStateOf("")
        private set
    /** How that picture sits in its frame (zoom, position) and its optional text: see HeroEditor.kt. */
    var heroEdit by mutableStateOf(HeroEdit())
        private set
    /** The finished cover picture (the part in the frame, turned and lightened), made when it was saved; empty for older ones. */
    var heroCut by mutableStateOf("")
        private set

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context) {
        CurrencyChoice.load(c) // App settings > Prices (2.122)
        if (loaded) return
        loaded = true
        val p = prefs(c)
        myWishes = SafeStore.readList(p, "wishes") { wishFromJson(it) }
        myBin = SafeStore.readList(p, "bin") { wishFromJson(it) }
        History.load(c)
        History.seed(c, myWishes)
        p.getString("cats", null)?.let { s ->
            myCats = s.split("\n").filter { it.isNotBlank() }
        }
        // There is no "System default" any more (2.53): the first start (and a phone that had it) quietly takes
        // Light or Dark to match the phone's setting at that moment.
        theme = p.getString("theme", null)?.takeIf { it == "light" || it == "dark" || it == "amoled" }
            ?: if ((c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        palette = p.getString("palette", "indigo") ?: "indigo"
        // Stored as "style" since 2.52. Since 2.97 (owner) it is one of STYLES (Design.kt), with an accent in
        // "palette" and light or dark in "theme". The first time, the old choice moves over: Neobrutalism stays
        // (Midnight becomes dark), the other old designs and the standard themes become Quiet; AMOLED black becomes
        // dark; the old colour sets become the closest accent.
        val oldStyle = p.getString("style", "classic") ?: "classic"
        colours = p.getString("style_colours", "") ?: ""
        if (!p.getBoolean("look_v2", false)) {
            design = if (oldStyle == "neo") "neo" else "quiet"
            if (oldStyle != "classic") {
                // The old designs had their own dark sets (light or dark came from the set, not from "theme").
                theme = if (colours in setOf("midnight", "plum", "night", "dusk")) "dark" else "light"
            }
            if (theme == "amoled") theme = "dark"
            palette = when (if (oldStyle == "classic") palette else colours) {
                "indigo", "lavender", "lilac", "plum", "dusk" -> "indigo"
                "sage", "forest", "night", "mint", "seaglass" -> "green"
                "rose", "berry", "bubblegum", "coral" -> "pink"
                "amber", "sunshine", "terracotta", "tangerine", "peach", "lemon" -> "orange"
                else -> "blue"
            }
            p.edit().putBoolean("look_v2", true).apply()
        } else {
            design = styleFor(oldStyle).key
            if (ACCENTS.none { it.key == palette } && customAccent(palette) == null) palette = "blue"
        }
        font = p.getString("font", "google_sans") ?: "google_sans"
        instantDelete = p.getBoolean("instant_delete", false)
        notifyDrops = p.getBoolean("notify_drops", true)
        notifyRises = p.getBoolean("notify_rises", false)
        // "gallery" (images only) was removed in 2.34: it falls back to the large cards.
        // Two per row is the default since 2.100 (owner); the large cards are the "details" view. Everyone who used an
        // older version has "view" saved already, so they keep what they had.
        // Rows is the default for new installs since 2.126 (owner); a saved choice is kept (owner: nobody's screen
        // changes by surprise).
        view = p.getString("view", null)?.let { v -> if (v == "list" || v == "rows") v else "grid" } ?: "rows"
        sort = p.getString("sort", "new")?.takeIf { s -> SORT_OPTIONS.any { it.first == s } } ?: "new"
        myAbout = p.getString("about", "") ?: ""
        intervalHours = p.getInt("interval", 6).let { if (it in listOf(1, 3, 6, 12, 24)) it else 6 }
        lastCheck = p.getLong("lastCheck", 0L)
        lastSaveTo = p.getString("last_save_to", "mine") ?: "mine"
        myName = p.getString("name", null)?.takeIf { it.isNotBlank() } ?: "My wishlist"
        shareCode = p.getString("code", null)
        ownerToken = p.getString("token", null)
        uploadedPhotos = p.getStringSet("photosUp", null)?.toSet() ?: emptySet()
        // Your own wishlists. Before 2.64 there was only "main", stored as above.
        mineIds = try {
            val a = JSONArray(p.getString("mine_ids", null) ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }.let { ids -> if (MAIN_LIST in ids) ids else listOf(MAIN_LIST) + ids }
        binOwner = try {
            val o = JSONObject(p.getString("bin_owner", null) ?: "{}")
            o.keys().asSequence().associate { it.toLong() to o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        recent = try {
            val o = JSONObject(p.getString("recent", null) ?: "{}")
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        hero = p.getString("hero", "") ?: ""
        goneNames = try {
            val o = JSONObject(p.getString("mine_gone", null) ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        copies = try {
            val o = JSONObject(p.getString("mine_copies", null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val a = o.getJSONArray(k)
                (0 until a.length()).map { a.getLong(it) }.toSet()
            }
        } catch (e: Exception) {
            emptyMap()
        }
        heroEdit = HeroEdit.fromJson(p.getString("hero_edit", null))
        heroCut = p.getString("hero_cut", "") ?: ""
        val open = p.getString("mine_open", MAIN_LIST) ?: MAIN_LIST
        if (open != MAIN_LIST && open in mineIds) {
            applyMine(MyLists.read(p, open))
            openMine = open
        }
        mineNames = mineIds.associateWith { MyLists.read(p, it).name }
        // One set of categories for all your own lists (owner, 2.95): the first time, every list gets them all.
        if (!p.getBoolean("cats_merged", false)) {
            val union = (listOf(myCats) + mineIds.filter { it != openMine }.map { MyLists.read(p, it).cats }).flatten()
                .fold(listOf<String>()) { acc, n -> if (acc.any { it.equals(n, ignoreCase = true) }) acc else acc + n }
            myCats = union
            persist(c)
            otherLists(c) { it.copy(cats = union) }
            p.edit().putBoolean("cats_merged", true).apply()
        }
        Lang.load(c)
        Notifications.load(c)
        Targets.load(c)
        Sync.attach(c)
        Shared.load(c)
        friends = SafeStore.readList(p, "friends") { o -> Friend(o.getString("code"), o.optString("name", "Wishlist")) }
        deviceId = p.getString("device", null)
            ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    private fun persist(c: Context) {
        val binArr = JSONArray()
        myBin.forEach { binArr.put(wishToJson(it)) }
        val fr = JSONArray()
        friends.forEach { fr.put(JSONObject().put("code", it.code).put("name", it.name)) }
        mineNames = mineNames + (openMine to myName)
        val e = prefs(c).edit()
        // The open list of your own goes where it belongs ("main": the old keys; another list: its own entry).
        MyLists.write(e, currentMine())
        val owners = JSONObject()
        binOwner.forEach { (k, v) -> owners.put(k.toString(), v) }
        val rec = JSONObject()
        recent.forEach { (k, v) -> rec.put(k, v) }
        val cp = JSONObject()
        copies.forEach { (k, v) -> cp.put(k, JSONArray(v.toList())) }
        e.putString("bin", binArr.toString())
            .putString("mine_ids", JSONArray(mineIds).toString())
            .putString("mine_open", openMine)
            .putString("bin_owner", owners.toString())
            .putString("recent", rec.toString())
            .putString("mine_copies", cp.toString())
            .putString("mine_gone", JSONObject(goneNames as Map<*, *>).toString())
            .putString("hero", hero)
            .putString("hero_edit", heroEdit.toJson())
            .putString("hero_cut", heroCut)
            .putString("theme", theme)
            .putString("palette", palette)
            .putString("style", design)
            .putString("style_colours", colours)
            .putString("font", font)
            .putString("view", view)
            .putString("sort", sort)
            .putInt("interval", intervalHours)
            .putBoolean("instant_delete", instantDelete)
            .putBoolean("notify_drops", notifyDrops)
            .putBoolean("notify_rises", notifyRises)
            .putLong("lastCheck", lastCheck)
            .putString("last_save_to", lastSaveTo)
            .putString("friends", fr.toString())
            .apply()
    }

    /** Saves locally and, if the list is shared, pushes the change online. */
    /** Changes every list of your own that isn't open (the categories they share). */
    private fun otherLists(c: Context, change: (MineData) -> MineData) {
        val p = prefs(c)
        val e = p.edit()
        for (id in mineIds) if (id != openMine) MyLists.write(e, change(MyLists.read(p, id)))
        e.apply()
        mineVersion++
    }

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

    /**
     * My wishlist as it goes online for friends. The bin goes along too (marked "bin", so friends don't see it), so a
     * reservation on it stays when it is restored. Private wishes never go online.
     */
    fun syncWishes(): List<Wish> = (myWishes + myBin.filter { ownerOf(it.id) == openMine }).filter { !it.priv }

    /** The list a wish in the bin came from (the original list when unknown or deleted). */
    fun ownerOf(wishId: Long): String = binOwner[wishId]?.takeIf { it in mineIds } ?: MAIN_LIST

    // ---- several wishlists of your own (begin) ----

    /** The open list of your own, as stored. */
    fun currentMine(): MineData = MineData(openMine, myName, myAbout, myCats, myWishes, shareCode, ownerToken, uploadedPhotos)

    private fun applyMine(d: MineData) {
        myWishes = d.wishes; myName = d.name; myAbout = d.about; myCats = d.cats
        shareCode = d.code; ownerToken = d.token; uploadedPhotos = d.photos
    }

    /** One of your lists: the open one from memory, the others from storage. */
    @Synchronized
    fun mine(c: Context, id: String): MineData = if (id == openMine) currentMine() else MyLists.read(prefs(c), id)

    /** All your own lists, oldest first. */
    @Synchronized
    fun allMine(c: Context): List<MineData> = mineIds.map { mine(c, it) }

    /** The name of one of your lists as shown ("My wishlist" is translated). */
    fun mineName(id: String): String {
        val n = mineNames[id] ?: "My wishlist"
        return if (n == "My wishlist") tr(n) else n
    }

    /** Opens another of your own lists (the one that was open is saved, and sent online first when it is shared). */
    @Synchronized
    fun openMine(c: Context, id: String) {
        if (id == openMine || id !in mineIds) return
        Sync.flush(c)
        persist(c)
        applyMine(MyLists.read(prefs(c), id))
        openMine = id
        persist(c)
        Sync.push()
    }

    /** Makes a new wishlist of your own and returns its id. It isn't opened. */
    @Synchronized
    fun createMine(c: Context, name: String, about: String): String {
        val id = System.currentTimeMillis().toString()
        val e = prefs(c).edit()
        MyLists.write(e, MineData(id, name.trim().ifEmpty { "Wishlist" }, about.trim().take(ABOUT_MAX), myCats, emptyList(), null, null, emptySet()))
        e.apply()
        mineIds = mineIds + id
        mineNames = mineNames + (id to name.trim().ifEmpty { "Wishlist" })
        persist(c)
        mineVersion++
        return id
    }

    /**
     * Deletes one of your own lists (since 2.79 any of them, as long as one is left). Its wishes go to the bin for
     * [BIN_DAYS] days, remembered as coming from a deleted list ([goneNames]), so restoring asks where they should go.
     * When the original list ("main", with the old storage keys) is deleted, the next list takes its place there.
     * Taking the list offline (its friends' link) is up to the caller, before this.
     */
    @Synchronized
    fun deleteMine(c: Context, id: String): Boolean {
        if (id !in mineIds || mineIds.size < 2) return false
        persist(c)
        Friends.forget(c, id)
        ListCovers.remove(c, "m:$id")
        Occasions.forget(c, id)
        ListLink.forget(c, id)
        val p = prefs(c)
        val d = MyLists.read(p, id)
        val now = System.currentTimeMillis()
        val goneId = "gone_$now"
        val binned = d.wishes.map { it.copy(bin = now) }
        myBin = binned + myBin.filter { b -> binned.none { it.id == b.id } }
        binOwner = binOwner + binned.associate { it.id to goneId }
        goneNames = goneNames + (goneId to d.name)
        copies = copies - id
        recent = recent - "m:$id"
        mineNames = mineNames - id
        val e = p.edit()
        if (id == MAIN_LIST) {
            // The next list moves into the original's place, with its friends' link, copies and history entry.
            val next = mineIds.first { it != MAIN_LIST }
            val nd = MyLists.read(p, next)
            Friends.moveId(c, next, MAIN_LIST)
            ListCovers.move(c, "m:$next", "m:$MAIN_LIST")
            Occasions.move(c, next, MAIN_LIST)
            MyLists.write(e, nd.copy(id = MAIN_LIST))
            e.remove(MyLists.blobKey(next))
            copies = (copies - next).let { m -> copies[next]?.let { m + (MAIN_LIST to it) } ?: m }
            binOwner = binOwner.mapValues { (_, v) -> if (v == next) MAIN_LIST else v }
            recent = recent["m:$next"]?.let { t -> recent - "m:$next" + ("m:$MAIN_LIST" to t) } ?: recent
            mineNames = mineNames - next + (MAIN_LIST to nd.name)
            mineIds = mineIds - next
            if (openMine == next) openMine = MAIN_LIST
        } else {
            e.remove(MyLists.blobKey(id))
            mineIds = mineIds - id
            if (openMine == id) openMine = MAIN_LIST
        }
        e.apply()
        // The open list is read again from storage (it may have moved).
        applyMine(MyLists.read(p, openMine))
        persist(c)
        mineVersion++
        return true
    }

    /** The name of the deleted list a wish in the bin came from, or null when its list still exists. */
    fun deletedListOf(wishId: Long): String? {
        val o = binOwner[wishId] ?: return null
        return if (o in mineIds) null else goneNames[o] ?: ""
    }

    /** The name to show for a wish in the bin: its list, or the deleted list it came from. */
    fun binListName(wishId: Long): String = deletedListOf(wishId)?.ifBlank { null } ?: mineName(ownerOf(wishId))

    /** Restores a wish from the bin to one of your lists (for a wish whose own list was deleted). */
    @Synchronized
    fun restoreMineTo(c: Context, wishId: Long, listId: String): Boolean {
        val w = myBin.firstOrNull { it.id == wishId } ?: return false
        if (listId !in mineIds) return false
        myBin = myBin.filter { it.id != wishId }
        binOwner = binOwner - wishId
        updateMine(c, listId) { l -> if (l.any { it.id == wishId }) l else listOf(w.copy(bin = 0L, binBy = "")) + l }
        persist(c)
        mineVersion++
        return true
    }

    /** Renames one of your lists, open or not. */
    @Synchronized
    fun renameMine(c: Context, id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        if (id !in mineIds) return
        if (id == openMine) {
            myName = n
            commit(c)
        } else {
            val d = MyLists.read(prefs(c), id).copy(name = n)
            val e = prefs(c).edit()
            MyLists.write(e, d)
            e.apply()
            if (d.code != null) Sync.pushStored(c, d, myBin.filter { ownerOf(it.id) == id })
        }
        mineNames = mineNames + (id to n)
        mineVersion++
    }

    /**
     * Changes the wishes of one of your lists, open or not (the background price check, saving a wish elsewhere).
     * A list that isn't open is saved straight to storage and, when it has a friends' link, sent online.
     */
    @Synchronized
    fun updateMine(c: Context, id: String, change: (List<Wish>) -> List<Wish>) {
        if (id == openMine) {
            myWishes = change(myWishes)
            commit(c)
            return
        }
        val d = MyLists.read(prefs(c), id)
        val nd = d.copy(wishes = change(d.wishes))
        val e = prefs(c).edit()
        MyLists.write(e, nd)
        e.apply()
        mineVersion++
        if (nd.code != null) Sync.pushStored(c, nd, myBin.filter { ownerOf(it.id) == id })
    }

    private fun dropCopy(m: Map<String, Set<Long>>, list: String, id: Long): Map<String, Set<Long>> {
        val left = m[list].orEmpty() - id
        return if (left.isEmpty()) m - list else m + (list to left)
    }

    /**
     * Copies wishes from your other lists into one of yours (at the top, skipping any it already has). They keep their
     * id: see [copies].
     */
    @Synchronized
    fun copyInto(c: Context, listId: String, wishes: List<Wish>) {
        if (listId !in mineIds || wishes.isEmpty()) return
        var added = emptySet<Long>()
        updateMine(c, listId) { ws ->
            val fresh = wishes.filter { w -> ws.none { it.id == w.id } }.distinctBy { it.id }
                .map { it.copy(bin = 0L, binBy = "") }
            added = fresh.map { it.id }.toSet()
            fresh + ws
        }
        copies = copies + (listId to (copies[listId].orEmpty() + added))
        persist(c)
        mineVersion++
    }

    /** The friends' link code and owner token of your list [id], open or not. */
    @Synchronized
    fun setShareFor(c: Context, id: String, code: String?, token: String?) {
        if (id == openMine) { setShare(c, code, token); mineVersion++; return }
        val d = MyLists.read(prefs(c), id)
        val e = prefs(c).edit()
        MyLists.write(e, d.copy(code = code, token = token))
        e.apply()
        mineVersion++
    }

    /** Wishes of your list [id] that are in the bin. */
    fun binnedOf(id: String): List<Wish> = myBin.filter { ownerOf(it.id) == id }

    /** Pictures that went online for a list that may no longer be open. */
    @Synchronized
    fun setUploadedFor(c: Context, id: String, names: Set<String>) {
        if (id == openMine) {
            setUploaded(c, names)
            return
        }
        val d = MyLists.read(prefs(c), id)
        val e = prefs(c).edit()
        MyLists.write(e, d.copy(photos = names))
        e.apply()
    }

    /**
     * Remembers that a list ("m:"/"s:") or a wishlist group ("g:") was opened: Home shows the one opened last first in
     * its section (2.110; "Recently opened" was removed).
     */
    @Synchronized
    fun touchRecent(c: Context, key: String) {
        recent = (recent + (key to System.currentTimeMillis())).entries
            .sortedByDescending { it.value }.take(200).associate { it.key to it.value }
        persist(c)
    }

    @Synchronized
    fun forgetRecent(c: Context, key: String) {
        if (key !in recent) return
        recent = recent - key
        persist(c)
    }

    @Synchronized
    fun setHero(c: Context, image: String, edit: HeroEdit = HeroEdit(), cut: String = "") {
        val old = hero
        val oldCut = heroCut
        hero = image
        heroEdit = edit
        heroCut = cut
        if (oldCut.isNotEmpty() && oldCut != cut) ImageStore.delete(c, oldCut)
        persist(c)
        if (old.isNotEmpty() && old != image) ImageStore.delete(c, old)
    }

    // ---- several wishlists of your own (end) ----

    /** Makes a wish of My wishlist private (it goes offline at the next push) or visible to friends again. */
    @Synchronized
    fun setPrivate(c: Context, id: Long, on: Boolean) {
        val w = myWishes.firstOrNull { it.id == id } ?: return
        if (w.priv != on) replaceMine(c, w.copy(priv = on))
    }

    /** Moves a wish of the open list to the bin at once. Null if the wish isn't there. */
    @Synchronized
    fun trash(c: Context, id: Long): Trashed? {
        if (Shared.active != null) return Shared.trash(c, id)
        val i = myWishes.indexOfFirst { it.id == id }
        if (i < 0) return null
        val w = myWishes[i].copy(bin = System.currentTimeMillis())
        myWishes = myWishes.filter { it.id != id }
        myBin = listOf(w) + myBin.filter { it.id != id }
        binOwner = binOwner + (id to openMine)
        val wasCopy = copies[openMine]?.contains(id) == true
        copies = dropCopy(copies, openMine, id)
        // Removing an original also removes its copies on your other lists (removing a copy leaves the rest).
        lastCascade = null
        if (!wasCopy) {
            val others = copies.filter { (l, ids) -> l != openMine && id in ids && l in mineIds }.keys.toList()
            for (l in others) {
                updateMine(c, l) { ws -> ws.filter { it.id != id } }
                copies = dropCopy(copies, l, id)
            }
            if (others.isNotEmpty()) lastCascade = id to others
        }
        commit(c)
        return Trashed(w, i, null)
    }

    /** Undo: the wish comes out of the bin and back where it was. */
    @Synchronized
    fun untrash(c: Context, t: Trashed) {
        if (t.listCode != null) {
            Shared.untrash(c, t)
            return
        }
        val owner = ownerOf(t.wish.id)
        myBin = myBin.filter { it.id != t.wish.id }
        binOwner = binOwner - t.wish.id
        // Undo also brings back the copies the removal took along.
        lastCascade?.takeIf { it.first == t.wish.id }?.let { (wid, lists) ->
            val back = t.wish.copy(bin = 0L, binBy = "")
            for (l in lists) {
                if (l !in mineIds) continue
                updateMine(c, l) { ws -> if (ws.any { it.id == wid }) ws else listOf(back) + ws }
                copies = copies + (l to (copies[l].orEmpty() + wid))
            }
            lastCascade = null
        }
        if (owner != openMine) {
            // You opened another of your lists before tapping Undo: the wish goes back to its own list.
            updateMine(c, owner) { l -> if (l.any { it.id == t.wish.id }) l else listOf(t.wish.copy(bin = 0L, binBy = "")) + l }
            persist(c)
            return
        }
        if (myWishes.none { it.id == t.wish.id }) {
            myWishes = myWishes.toMutableList().also { it.add(t.index.coerceIn(0, it.size), t.wish.copy(bin = 0L, binBy = "")) }
        }
        commit(c)
    }

    /** Takes a wish out of the bin and puts it back at the top of My wishlist. */
    @Synchronized
    fun restoreMine(c: Context, id: Long): Boolean {
        val w = myBin.firstOrNull { it.id == id } ?: return false
        val owner = ownerOf(id)
        myBin = myBin.filter { it.id != id }
        binOwner = binOwner - id
        if (owner != openMine) {
            updateMine(c, owner) { l -> if (l.any { it.id == id }) l else listOf(w.copy(bin = 0L, binBy = "")) + l }
            persist(c)
            return true
        }
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
        binOwner = binOwner - ids
        commit(c)
        // A copy on another list may still use the same picture and price history.
        val inUse = allMine(c).flatMap { it.wishes }
        gone.forEach { g ->
            if (inUse.none { it.image == g.image } && myBin.none { it.image == g.image }) ImageStore.delete(c, g.image)
            if (inUse.none { it.id == g.id }) History.remove(c, g.id)
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
        // Every list of your own gets it (2.95).
        otherLists(c) { d -> if (d.cats.any { it.equals(n, ignoreCase = true) }) d else d.copy(cats = d.cats + n) }
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
        // On all your own lists (2.95).
        otherLists(c) { d ->
            d.copy(cats = d.cats.map { if (it == old) n else it }, wishes = d.wishes.map { if (it.category == old) it.copy(category = n) else it })
        }
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
        // Gone from all your own lists; their wishes stay, without a category (2.95).
        otherLists(c) { d ->
            d.copy(cats = d.cats.filter { it != name }, wishes = d.wishes.map { if (it.category == name) it.copy(category = "") else it })
        }
    }

    /** How many wishes on how many of your own lists are in category [name] (for the delete question). */
    fun catUse(c: Context, name: String): Pair<Int, Int> {
        val per = allMine(c).map { d -> d.wishes.count { it.category == name } }.filter { it > 0 }
        return per.sum() to per.size
    }

    @Synchronized
    fun setListName(c: Context, name: String) {
        val n = name.trim().ifEmpty { if (Shared.active != null) "Shared wishlist" else if (openMine == MAIN_LIST) "My wishlist" else "Wishlist" }
        if (Shared.active != null) {
            Shared.setName(c, n)
            return
        }
        myName = n
        commit(c)
    }

    @Synchronized
    fun setTheme(c: Context, t: String) { theme = t; persist(c) }
    /** The accent colour: one of ACCENTS (Design.kt). Kept as "palette" (it held the old colour sets). */
    fun setPalette(c: Context, p: String) { palette = p; persist(c) }
    /** The look (2.97): a style of STYLES, "light" or "dark", and an accent of ACCENTS. */
    fun setLook(c: Context, style: String, mode: String, accent: String) {
        design = styleFor(style).key; theme = mode; palette = accentFor(accent).key
        persist(c)
    }
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
    fun setLastSaveTo(c: Context, to: String) { lastSaveTo = to; persist(c) }

    @Synchronized
    fun setShare(c: Context, code: String?, token: String?) {
        shareCode = code; ownerToken = token; persist(c)
    }

    @Synchronized
    fun setUploaded(c: Context, names: Set<String>) { uploadedPhotos = names; persist(c) }

    @Synchronized
    fun addFriend(c: Context, f: Friend) { friends = friends.filter { it.code != f.code } + f; persist(c) }

    @Synchronized
    fun removeFriend(c: Context, code: String) { friends = friends.filter { it.code != code }; persist(c); FriendWatch.forget(c, code) }

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
