package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Your own wishlists (a part of [Repo] since the data rebuild, owner 19 November 2026): the open one is held here in
 * memory (its wishes, name, note, categories and friends' link), the others are in storage (MyLists.kt, the database).
 */
internal object OwnLists {
    /** My wishlist (stored on this phone, mirrored online for friends when shared). */
    var myWishes by mutableStateOf(listOf<Wish>())
        internal set
    var myCats by mutableStateOf(listOf("Tech", "Home", "Fashion", "Books"))
        internal set
    var myName by mutableStateOf("My wishlist")
        internal set
    /** A note shown to friends at the top of the shared list (sizes, things you don't want, ...). */
    var myAbout by mutableStateOf("")
        internal set

    /** Set once the owner has shared the list online. */
    var shareCode by mutableStateOf<String?>(null)
        internal set
    var ownerToken: String? = null
        internal set
    /** File names of pictures already uploaded for the current share code. */
    var uploadedPhotos: Set<String> = emptySet()
        internal set

    // ---- several wishlists of your own (see MyLists.kt) ----
    /** The id of your own wishlist that is open (its data is in myWishes, myName, ...). "main" is the original one. */
    var openMine by mutableStateOf(MAIN_LIST)
        internal set
    /** All your own wishlists, oldest first. */
    var mineIds by mutableStateOf(listOf(MAIN_LIST))
        internal set
    /** The names of your own lists (id to name), for places that only need the name (the bin, menus). */
    var mineNames by mutableStateOf(mapOf<String, String>())
        internal set
    /** Goes up whenever a list that isn't open is changed, so the dashboard reads them again. */
    var mineVersion by mutableStateOf(0)
        internal set
}

// ---- what you can do with it (callers keep writing Repo.…, as before the rebuild) ----

fun Repo.add(c: Context, w: Wish): Unit = synchronized(this) {
    if (Shared.active != null) {
        // Shared lists keep no price history: the givers only want to see what could be given.
        Shared.put(c, w)
        return
    }
    addMine(c, w)
}

/** Adds to My wishlist, whatever list is open. */
fun Repo.addMine(c: Context, w: Wish): Unit = synchronized(this) {
    OwnLists.myWishes = listOf(w) + myWishes
    commit(c)
    w.price?.let { History.record(c, w.id, it) }
}

/** [base] is the wish as the edit form started (only for shared lists: it decides which fields count as changed). */
fun Repo.replace(c: Context, w: Wish, base: Wish? = null): Unit = synchronized(this) {
    if (Shared.active != null) {
        Shared.put(c, w, base)
        return
    }
    replaceMine(c, w)
}

/** Changes a wish on My wishlist, whatever list is open (used by the background price check). */
fun Repo.replaceMine(c: Context, w: Wish): Unit = synchronized(this) {
    val before = myWishes.firstOrNull { it.id == w.id }
    OwnLists.myWishes = myWishes.map { if (it.id == w.id) w else it }
    commit(c)
    // A wish on several of your lists is one wish (owner, 12 November 2026): its copies change along.
    if (before != null) syncCopies(c, w, openMine)
    // A changed price (found by a check, or typed in by you) becomes a point in the history.
    val np = w.price
    if (np != null && np != before?.price) History.record(c, w.id, np)
}


/** The open list of your own, as stored. */
fun Repo.currentMine(): MineData = MineData(openMine, myName, myAbout, myCats, myWishes, shareCode, ownerToken, uploadedPhotos)

internal fun Repo.applyMine(d: MineData) {
    OwnLists.myWishes = d.wishes; OwnLists.myName = d.name; OwnLists.myAbout = d.about; OwnLists.myCats = d.cats
    OwnLists.shareCode = d.code; OwnLists.ownerToken = d.token; OwnLists.uploadedPhotos = d.photos
}

/** One of your lists: the open one from memory, the others from storage. */
fun Repo.mine(c: Context, id: String): MineData = synchronized(this) { if (id == openMine) currentMine() else MyLists.read(prefs(c), id) }

/** All your own lists, oldest first. */
fun Repo.allMine(c: Context): List<MineData> = synchronized(this) { mineIds.map { mine(c, it) } }

/** The name of one of your lists as shown ("My wishlist" is translated). */
fun Repo.mineName(id: String): String {
    val n = mineNames[id] ?: "My wishlist"
    return if (n == "My wishlist") tr(n) else n
}

/** Opens another of your own lists (the one that was open is saved, and sent online first when it is shared). */
fun Repo.openMine(c: Context, id: String): Unit = synchronized(this) {
    if (id == openMine || id !in mineIds) return
    Sync.flush(c)
    persist(c)
    applyMine(MyLists.read(prefs(c), id))
    OwnLists.openMine = id
    persist(c)
    Sync.push()
}

/** Makes a new wishlist of your own and returns its id. It isn't opened. */
fun Repo.createMine(c: Context, name: String, about: String): String = synchronized(this) {
    val id = System.currentTimeMillis().toString()
    val e = prefs(c).edit()
    MyLists.write(e, MineData(id, name.trim().ifEmpty { "Wishlist" }, about.trim().take(ABOUT_MAX), myCats, emptyList(), null, null, emptySet()))
    e.apply()
    OwnLists.mineIds = mineIds + id
    OwnLists.mineNames = mineNames + (id to name.trim().ifEmpty { "Wishlist" })
    persist(c)
    OwnLists.mineVersion++
    return id
}

/**
 * Deletes one of your own lists (since 2.79 any of them, as long as one is left). Its wishes go to the bin for
 * [BIN_DAYS] days, remembered as coming from a deleted list ([goneNames]), so restoring asks where they should go.
 * When the original list ("main", with the old storage keys) is deleted, the next list takes its place there.
 * Taking the list offline (its friends' link) is up to the caller, before this.
 */
fun Repo.deleteMine(c: Context, id: String): Boolean = synchronized(this) {
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
    OwnBin.myBin = binned + myBin.filter { b -> binned.none { it.id == b.id } }
    OwnBin.binOwner = OwnBin.binOwner + binned.associate { it.id to goneId }
    OwnBin.goneNames = OwnBin.goneNames + (goneId to d.name)
    OwnCopies.copies = OwnCopies.copies - id
    AppPrefs.recent = recent - "m:$id"
    OwnLists.mineNames = mineNames - id
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
        OwnCopies.copies = (OwnCopies.copies - next).let { m -> OwnCopies.copies[next]?.let { m + (MAIN_LIST to it) } ?: m }
        OwnBin.binOwner = OwnBin.binOwner.mapValues { (_, v) -> if (v == next) MAIN_LIST else v }
        AppPrefs.recent = recent["m:$next"]?.let { t -> recent - "m:$next" + ("m:$MAIN_LIST" to t) } ?: recent
        OwnLists.mineNames = mineNames - next + (MAIN_LIST to nd.name)
        OwnLists.mineIds = mineIds - next
        if (openMine == next) OwnLists.openMine = MAIN_LIST
    } else {
        e.remove(MyLists.blobKey(id))
        OwnLists.mineIds = mineIds - id
        if (openMine == id) OwnLists.openMine = MAIN_LIST
    }
    e.apply()
    // The open list is read again from storage (it may have moved).
    applyMine(MyLists.read(p, openMine))
    persist(c)
    OwnLists.mineVersion++
    return true
}

/** Renames one of your lists, open or not. */
fun Repo.renameMine(c: Context, id: String, name: String): Unit = synchronized(this) {
    val n = name.trim().ifEmpty { return }
    if (id !in mineIds) return
    if (id == openMine) {
        OwnLists.myName = n
        commit(c)
    } else {
        val d = MyLists.read(prefs(c), id).copy(name = n)
        val e = prefs(c).edit()
        MyLists.write(e, d)
        e.apply()
        if (d.code != null) Sync.pushStored(c, d, myBin.filter { ownerOf(it.id) == id })
    }
    OwnLists.mineNames = mineNames + (id to n)
    OwnLists.mineVersion++
}

/**
 * Changes the wishes of one of your lists, open or not (the background price check, saving a wish elsewhere).
 * A list that isn't open is saved straight to storage and, when it has a friends' link, sent online.
 */
fun Repo.updateMine(c: Context, id: String, change: (List<Wish>) -> List<Wish>) {
    if (id == openMine) {
        OwnLists.myWishes = change(myWishes)
        commit(c)
        return
    }
    val d = MyLists.read(prefs(c), id)
    val nd = d.copy(wishes = change(d.wishes))
    val e = prefs(c).edit()
    MyLists.write(e, nd)
    e.apply()
    OwnLists.mineVersion++
    if (nd.code != null) Sync.pushStored(c, nd, myBin.filter { ownerOf(it.id) == id })
}

/**
 * Once (owner, 2.135): the names of the wishes on your own lists without article numbers and the shop's name
 * ([cleanTitle]); a wish without a shop link keeps its name. Lists online are updated as after any change.
 */
fun Repo.cleanTitlesOnce(c: Context): Unit = synchronized(this) {
    val p = prefs(c)
    if (p.getBoolean("titles_clean_2135", false)) return
    fun clean(w: Wish): Wish {
        val host = try { java.net.URI(w.url).host.orEmpty() } catch (e: Exception) { "" }
        return if (host.isEmpty()) w else cleanTitle(w.title, host).let { t -> if (t == w.title) w else w.copy(title = t) }
    }
    // Only lists where a name really changes are saved (and sent online).
    for (id in mineIds) if (mine(c, id).wishes.any { clean(it) != it }) updateMine(c, id) { ws -> ws.map(::clean) }
    p.edit().putBoolean("titles_clean_2135", true).apply()
}

/** The friends' link code and owner token of your list [id], open or not. */
fun Repo.setShareFor(c: Context, id: String, code: String?, token: String?): Unit = synchronized(this) {
    if (id == openMine) { setShare(c, code, token); OwnLists.mineVersion++; return }
    val d = MyLists.read(prefs(c), id)
    val e = prefs(c).edit()
    MyLists.write(e, d.copy(code = code, token = token))
    e.apply()
    OwnLists.mineVersion++
}

/** Pictures that went online for a list that may no longer be open. */
fun Repo.setUploadedFor(c: Context, id: String, names: Set<String>): Unit = synchronized(this) {
    if (id == openMine) {
        setUploaded(c, names)
        return
    }
    val d = MyLists.read(prefs(c), id)
    val e = prefs(c).edit()
    MyLists.write(e, d.copy(photos = names))
    e.apply()
}


/** Makes a wish of My wishlist private (it goes offline at the next push) or visible to friends again. */
fun Repo.setPrivate(c: Context, id: Long, on: Boolean): Unit = synchronized(this) {
    val w = myWishes.firstOrNull { it.id == id } ?: return
    if (w.priv != on) replaceMine(c, w.copy(priv = on))
}

fun Repo.setListName(c: Context, name: String): Unit = synchronized(this) {
    val n = name.trim().ifEmpty { if (Shared.active != null) "Shared wishlist" else if (openMine == MAIN_LIST) "My wishlist" else "Wishlist" }
    if (Shared.active != null) {
        Shared.setName(c, n)
        return
    }
    OwnLists.myName = n
    commit(c)
}

/** Saves the note for friends and, if the list is shared, sends it along. */
fun Repo.setAbout(c: Context, text: String): Unit = synchronized(this) {
    val t = text.trim().take(ABOUT_MAX)
    if (Shared.active != null) {
        Shared.setAbout(c, t)
        return
    }
    OwnLists.myAbout = t
    commit(c)
}

fun Repo.setShare(c: Context, code: String?, token: String?): Unit = synchronized(this) {
    OwnLists.shareCode = code; OwnLists.ownerToken = token; persist(c)
}

fun Repo.setUploaded(c: Context, names: Set<String>): Unit = synchronized(this) { OwnLists.uploadedPhotos = names; persist(c) }
