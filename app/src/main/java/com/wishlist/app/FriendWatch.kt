package com.wishlist.app

import android.content.Context
import org.json.JSONObject

/**
 * For givers: when a gift you reserved on a friend's wishlist is taken off it (the friend deleted it, or made it a
 * private wish), or (2.113) the friend marked it as owned, you get a notification nudging you to pick another gift. The friend never learns that it was reserved.
 *
 * This phone remembers what it reserved on each friend's list (id -> title, "fres_<code>"), because once the wish is
 * gone online its reservation is gone too. [check] compares that with the list as friends see it now; it runs with the
 * bin check in the background ([BinWorker]) and every time a friend's list is refreshed on screen.
 */
object FriendWatch {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun key(code: String) = "fres_$code"

    private fun read(c: Context, code: String): Map<Long, String> = try {
        val o = JSONObject(prefs(c).getString(key(code), null) ?: "{}")
        o.keys().asSequence().associate { it.toLong() to o.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun write(c: Context, code: String, m: Map<Long, String>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs(c).edit().putString(key(code), o.toString()).apply()
    }

    /** This phone reserved [w] on the friend's list [code]. */
    @Synchronized
    fun reserved(c: Context, code: String, w: Wish) = write(c, code, read(c, code) + (w.id to w.title))

    /** This phone took its reservation away again. */
    @Synchronized
    fun unreserved(c: Context, code: String, id: Long) = write(c, code, read(c, code) - id)

    /** The friend's list was removed from this phone. */
    fun forget(c: Context, code: String) = prefs(c).edit().remove(key(code)).apply()

    /**
     * Compares what this phone reserved on friend [f]'s list with the list now ([list] when it was just fetched, else
     * it is fetched here). A reserved wish that friends can't see any more gives one notification. [mine] are the ids
     * reserved on this phone according to the server, when known: they are remembered too (for example after an update).
     */
    @Synchronized
    fun check(c: Context, f: Friend, list: RemoteList, mine: Set<Long> = emptySet()) {
        val visible = list.wishes.filter { it.bin == 0L && !it.priv }.associateBy { it.id }
        var m = read(c, f.code)
        // (An owned wish is not taken in again: its old reservation stays online and would be told about every time.)
        for (id in mine) visible[id]?.takeIf { !it.got }?.let { if (id !in m) m = m + (id to it.title) }
        for ((id, title) in m) {
            val now = visible[id]
            // Still on the list and not owned: nothing to say.
            if (now != null && !now.got) continue
            // Taken off, or (2.113, owner) marked as owned by the friend: say so once, so nobody buys a duplicate.
            // One notification for both (owner, 2.126), naming whose list it was.
            val place = list.owner.takeIf { it.isNotBlank() }?.let { tr("%s’s wishlist", it) } ?: ("“" + list.name.ifBlank { f.name } + "”")
            Alerts.notifyGiftLost(c, f.code, gift = false, id = id, title = title, image = now?.image.orEmpty(), place = place, owned = now != null)
            m = m - id
        }
        write(c, f.code, m)
    }

    /** The background check of every friend's list. A list that can't be loaded (or isn't shared any more) is skipped. */
    suspend fun checkAll(c: Context) {
        for (f in Repo.friends.toList()) {
            if (read(c, f.code).isEmpty()) continue
            val list = try {
                Cloud.fetchList(f.code)
            } catch (e: Exception) {
                null
            } ?: continue
            check(c, f, list)
        }
    }
}
