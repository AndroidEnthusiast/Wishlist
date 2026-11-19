package com.wishlist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The app's memory of your own data. Since the data rebuild (owner, 19 November 2026) it is split into parts, each in
 * its own file with its own checks, while every screen keeps using Repo as before (Repo.trash, Repo.theme, …):
 *  - Wish.kt: a wish and how it is written as text.
 *  - RepoLists.kt ([OwnLists]): your own lists; the open one in memory, the others in storage.
 *  - RepoCopies.kt ([OwnCopies]): one wish on several of your lists.
 *  - RepoBin.kt ([OwnBin]): the bin, Undo and Received gifts.
 *  - RepoCats.kt: categories, the same on all your lists.
 *  - RepoPrefs.kt ([AppPrefs]): App settings, the look, the cover picture, recently opened.
 *  - RepoFriendLists.kt ([FriendLists]): friends' lists you follow.
 * Saving is unchanged: [persist] writes everything at once (the settings store and, through ListStore, the database) in
 * the same format as before, and every change holds the same lock (Repo), so two changes never mix. DataSameTest proves
 * that saving and loading give back exactly what the app did before the rebuild.
 */
object Repo {
    // What the parts hold, read as before (Repo.myWishes, Repo.theme, …). Changing them goes through the doors.
    // OwnLists
    val myWishes: List<Wish> get() = OwnLists.myWishes
    val myCats: List<String> get() = OwnLists.myCats
    val myName: String get() = OwnLists.myName
    val myAbout: String get() = OwnLists.myAbout
    val shareCode: String? get() = OwnLists.shareCode
    val ownerToken: String? get() = OwnLists.ownerToken
    val uploadedPhotos: Set<String> get() = OwnLists.uploadedPhotos
    val openMine: String get() = OwnLists.openMine
    val mineIds: List<String> get() = OwnLists.mineIds
    val mineNames: Map<String, String> get() = OwnLists.mineNames
    val mineVersion: Int get() = OwnLists.mineVersion
    // OwnBin
    val myBin: List<Wish> get() = OwnBin.myBin
    // AppPrefs
    val theme: String get() = AppPrefs.theme
    val palette: String get() = AppPrefs.palette
    val design: String get() = AppPrefs.design
    val colours: String get() = AppPrefs.colours
    val font: String get() = AppPrefs.font
    val instantDelete: Boolean get() = AppPrefs.instantDelete
    val notifyDrops: Boolean get() = AppPrefs.notifyDrops
    val notifyRises: Boolean get() = AppPrefs.notifyRises
    val view: String get() = AppPrefs.view
    val sort: String get() = AppPrefs.sort
    val intervalHours: Int get() = AppPrefs.intervalHours
    val lastCheck: Long get() = AppPrefs.lastCheck
    val lastSaveTo: String get() = AppPrefs.lastSaveTo
    val recent: Map<String, Long> get() = AppPrefs.recent
    val hero: String get() = AppPrefs.hero
    val heroEdit: HeroEdit get() = AppPrefs.heroEdit
    val heroCut: String get() = AppPrefs.heroCut
    // FriendLists
    val friends: List<Friend> get() = FriendLists.friends
    val deviceId: String get() = FriendLists.deviceId

    // What the screens show: My wishlist, or the shared wishlist that is open (see Shared.kt).
    val wishes: List<Wish> get() = if (Shared.active == null) myWishes else Shared.content.wishes
    val cats: List<String> get() = if (Shared.active == null) myCats else Shared.content.cats
    val listName: String
        get() = if (Shared.active == null) (if (myName == "My wishlist") tr(myName) else myName)
        else Shared.content.name.ifBlank { tr("Wishlist") }
    val about: String get() = if (Shared.active == null) myAbout else Shared.content.about
    /** Written on wishes you add to a shared wishlist ("Added by ..."). Empty on My wishlist. */
    val authorName: String get() = if (Shared.active == null) "" else Shared.me
    private var loaded = false

    internal fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context) {
        CurrencyChoice.load(c) // App settings > Prices (2.122)
        if (loaded) return
        loaded = true
        // Your lists and wishes come from the database (ListStore, 2.167), checked against the settings store.
        ListStore.start(c)
        val p = prefs(c)
        OwnLists.myWishes = SafeStore.readList(p, "wishes") { wishFromJson(it) }
        // The bin from the database (ListStore, 2.168), or the settings store when the database can't be used.
        OwnBin.myBin = ListStore.bin() ?: SafeStore.readList(p, "bin") { wishFromJson(it) }
        History.load(c)
        History.seed(c, myWishes)
        p.getString("cats", null)?.let { s ->
            OwnLists.myCats = s.split("\n").filter { it.isNotBlank() }
        }
        AppPrefs.load(c, p)
        OwnLists.myAbout = p.getString("about", "") ?: ""
        OwnLists.myName = p.getString("name", null)?.takeIf { it.isNotBlank() } ?: "My wishlist"
        OwnLists.shareCode = p.getString("code", null)
        OwnLists.ownerToken = p.getString("token", null)
        OwnLists.uploadedPhotos = p.getStringSet("photosUp", null)?.toSet() ?: emptySet()
        // Your own wishlists. Before 2.64 there was only "main", stored as above.
        OwnLists.mineIds = try {
            val a = JSONArray(p.getString("mine_ids", null) ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }.let { ids -> if (MAIN_LIST in ids) ids else listOf(MAIN_LIST) + ids }
        // Since 2.171 the database is the only source (ListStore); the settings store is read only without it.
        ListStore.ids()?.let { OwnLists.mineIds = it }
        OwnBin.binOwner = ListStore.binOwners() ?: try {
            val o = JSONObject(p.getString("bin_owner", null) ?: "{}")
            o.keys().asSequence().associate { it.toLong() to o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        OwnBin.goneNames = try {
            val o = JSONObject(p.getString("mine_gone", null) ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        OwnCopies.copies = try {
            val o = JSONObject(p.getString("mine_copies", null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val a = o.getJSONArray(k)
                (0 until a.length()).map { a.getLong(it) }.toSet()
            }
        } catch (e: Exception) {
            emptyMap()
        }
        val open = p.getString("mine_open", MAIN_LIST) ?: MAIN_LIST
        // The open list as ListStore has it (the database's copy); the main list's own keys were read above as before.
        val first = if (open != MAIN_LIST && open in mineIds) open else MAIN_LIST
        applyMine(MyLists.read(p, first))
        OwnLists.openMine = first
        OwnLists.mineNames = mineIds.associateWith { MyLists.read(p, it).name }
        // One set of categories for all your own lists (owner, 2.95): the first time, every list gets them all.
        if (!p.getBoolean("cats_merged", false)) {
            val union = (listOf(myCats) + mineIds.filter { it != openMine }.map { MyLists.read(p, it).cats }).flatten()
                .fold(listOf<String>()) { acc, n -> if (acc.any { it.equals(n, ignoreCase = true) }) acc else acc + n }
            OwnLists.myCats = union
            persist(c)
            otherLists(c) { it.copy(cats = union) }
            p.edit().putBoolean("cats_merged", true).apply()
        }
        Lang.load(c)
        Notifications.load(c)
        Targets.load(c)
        Sync.attach(c)
        Shared.load(c)
        FriendLists.friends = ListStore.friends() ?: SafeStore.readList(p, "friends") { o -> Friend(o.getString("code"), o.optString("name", "Wishlist")) }
        FriendLists.deviceId = p.getString("device", null)
            ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
        // Every change is saved from here into the database (ListStore, 2.171).
        ListStore.source = { memory(c) }
    }

    /** Everything that lives in the database, as the app holds it now (ListStore saves the difference). */
    @Synchronized
    fun memory(c: Context): Snapshot = Snapshot(
        mineIds.map { mine(c, it) },
        myBin.map { (OwnBin.binOwner[it.id] ?: MAIN_LIST) to it },
        myCats,
        History.all,
        friends,
        Notifications.all
    )

    internal fun persist(c: Context) {
        val binArr = JSONArray()
        myBin.forEach { binArr.put(wishToJson(it)) }
        val fr = JSONArray()
        friends.forEach { fr.put(JSONObject().put("code", it.code).put("name", it.name)) }
        OwnLists.mineNames = mineNames + (openMine to myName)
        val e = prefs(c).edit()
        // The open list of your own goes where it belongs ("main": the old keys; another list: its own entry).
        MyLists.write(e, currentMine())
        val owners = JSONObject()
        OwnBin.binOwner.forEach { (k, v) -> owners.put(k.toString(), v) }
        val cp = JSONObject()
        OwnCopies.copies.forEach { (k, v) -> cp.put(k, JSONArray(v.toList())) }
        // Since 2.171 your lists, the bin and friends' lists are saved to the database (ListStore); the settings store
        // only when the database can't be used.
        if (!ListStore.live) e.putString("bin", binArr.toString())
            .putString("mine_ids", JSONArray(mineIds).toString())
            .putString("bin_owner", owners.toString())
            .putString("friends", fr.toString())
        ListStore.changed()
        e.putString("mine_open", openMine)
            .putString("mine_copies", cp.toString())
            .putString("mine_gone", JSONObject(OwnBin.goneNames as Map<*, *>).toString())
        AppPrefs.write(e)
        e.apply()
    }

    /** Changes every list of your own that isn't open (the categories they share). */
    internal fun otherLists(c: Context, change: (MineData) -> MineData) {
        val p = prefs(c)
        val e = p.edit()
        for (id in mineIds) if (id != openMine) MyLists.write(e, change(MyLists.read(p, id)))
        e.apply()
        OwnLists.mineVersion++
    }

    /** Saves locally and, if the list is shared, pushes the change online. */
    internal fun commit(c: Context) {
        persist(c)
        Sync.push()
    }
}
