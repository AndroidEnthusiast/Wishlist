package com.wishlist.app

import android.content.Context
import kotlinx.coroutines.launch

// The shared wishlists you are on: making, joining, leaving, the invite link, names (a part of [Shared] since the data rebuild, owner 19 November 2026; callers keep writing Shared.…).

/**
 * Who a wishlist for someone else is for ("Mum"), asked when it is made (owner, 17 November 2026, to keep the
 * surprise). Kept on the phone that made it only; "" when not known (lists made before, or joined).
 */
fun Shared.forName(c: Context, code: String): String = prefs(c).getString("sh_for_$code", "") ?: ""

fun Shared.setForName(c: Context, code: String, name: String) {
    prefs(c).edit().putString("sh_for_$code", name.trim().take(FOR_MAX)).apply()
}

/** True when a friend called [name] looks like the person the list is for: the same name or first name. */
fun Shared.isFor(forName: String, name: String): Boolean {
    val f = forName.trim().lowercase(); val n = name.trim().lowercase()
    if (f.isEmpty() || n.isEmpty()) return false
    return f == n || f == n.substringBefore(' ') || n == f.substringBefore(' ')
}

/**
 * "Your name" in the app's Settings: used on every shared list, except a list where you chose another name
 * (in that list's settings). Lists that used the old name follow the new one.
 */
fun Shared.setAppName(c: Context, name: String) {
    val n = name.trim().take(30)
    if (n.isEmpty()) return
    val old = lastName
    rememberName(c, n)
    lists = lists.map { it.copy(me = n) }
    saveLists(c)
}

/** Your name on one shared list only (its settings). Empty: use the name from the app's Settings. */
fun Shared.setMe(c: Context, code: String, name: String) {
    val n = name.trim().take(30).ifEmpty { lastName }
    lists = lists.map { if (it.code == code) it.copy(me = n) else it }
    saveLists(c)
}

/**
 * The invitation key in text from the clipboard (the invite page copies its link when "Download app" is tapped),
 * or null when there is none, or this phone is on that list already.
 */
fun Shared.inviteFromClipboard(c: Context, text: String?): String? {
    if (text.isNullOrBlank() || text.length > 500) return null
    // Only a real invite link counts, never a loose string of letters.
    if (!text.uppercase().contains("J=")) return null
    val key = Sync.parseInvite(text) ?: return null
    if (lists.any { it.key == key }) return null
    return key
}

// ---- making, joining and leaving lists ----

/** Makes a new shared wishlist online and adds it to this phone. */
suspend fun Shared.create(c: Context, name: String, me: String): SharedList {
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
suspend fun Shared.join(c: Context, key: String, me: String): SharedList? {
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
    writeCache(c, r.code, SharedContent(r.list.name, r.list.cats, r.list.about, r.list.wishes, r.list.occ))
    return l
}

/** Takes the list off this phone only. It stays online for everyone else. */
fun Shared.leave(c: Context, code: String) {
    // Off the list of members (2.128), so nobody picks you as its new owner later.
    lists.firstOrNull { it.code == code }?.let { l -> scope.launch { Members.left(c, l.key) } }
    if (active == code) close()
    lists = lists.filter { it.code != code }
    saveLists(c)
    // Its saved copy, people and last known reservations go with it (owner, 26 November 2026).
    prefs(c).edit().remove(cacheKey(code)).remove("shp_$code").remove("res_$code").apply()
    Bin.forget(c, code)
}

/** This phone became the owner of list [code] (2.128): its new owner token. */
fun Shared.becameOwner(c: Context, code: String, token: String) {
    lists = lists.map { if (it.code == code) it.copy(owner = token) else it }
    saveLists(c)
}

/** Creator only: deletes the list online for everyone (wishes, reservations, pictures). */
suspend fun Shared.deleteForEveryone(c: Context) {
    val l = current ?: return
    if (l.owner.isEmpty()) throw CloudException(tr("Only the person who made this list can delete it."))
    content.wishes.forEach { dropPhoto(l, it.image) }
    Cloud.deleteList(l.code, l.owner)
    leave(c, l.code)
}

/** Creator only: a new edit key. The old invite link stops working. Returns the new key. */
suspend fun Shared.resetKey(c: Context): String {
    val l = current ?: throw CloudException(tr("No wishlist is open."))
    if (l.owner.isEmpty()) throw CloudException(tr("Only the person who made this list can do that."))
    val nk = Sync.newKey()
    Cloud.shReset(l.code, l.owner, nk)
    lists = lists.map { if (it.code == l.code) it.copy(key = nk) else it }
    saveLists(c)
    return nk
}
