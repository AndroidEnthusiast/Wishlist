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
    fun forget(c: Context, code: String) = prefs(c).edit().remove(key(code)).remove("fresn_$code").apply()

    /** Every list this phone reserved on: code -> (wish id -> title), for "Gifts I reserved" (2.129). */
    fun all(c: Context): Map<String, Map<Long, String>> =
        prefs(c).all.keys.filter { it.startsWith("fres_") }.map { it.removePrefix("fres_") }
            .associateWith { read(c, it) }.filterValues { it.isNotEmpty() }

    /** How a list is called on "Gifts I reserved": "Sam’s wishlist" or its name, remembered when it was last seen. */
    fun label(c: Context, code: String): String = prefs(c).getString("fresn_$code", null).orEmpty()

    /**
     * Compares what this phone reserved on friend [f]'s list with the list now ([list] when it was just fetched, else
     * it is fetched here). A reserved wish that friends can't see any more gives one notification. [mine] are the ids
     * reserved on this phone according to the server, when known: they are remembered too (for example after an update).
     */
    /**
     * Reserved wishes that left list [code] for another of the owner's lists (moved, supabase-collab.sql): wish id to
     * the new list's code. Only asked when a remembered reservation is missing from [list]; nothing when offline.
     */
    suspend fun movedFrom(c: Context, code: String, list: RemoteList): Map<Long, String> {
        val visible = list.wishes.filter { it.bin == 0L && !it.priv }.map { it.id }.toSet()
        if (read(c, code).keys.all { it in visible }) return emptyMap()
        return try {
            val a = org.json.JSONArray(Cloud.rpc("wl_moved_mine", JSONObject().put("p_code", code).put("p_device", Repo.deviceId)))
            (0 until a.length()).map { a.getJSONObject(it) }.associate { it.optLong("item") to it.optString("to") }
                .filterValues { it.isNotBlank() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @Synchronized
    fun check(c: Context, f: Friend, list: RemoteList, mine: Set<Long> = emptySet(), moved: Map<Long, String> = emptyMap()) {
        val visible = list.wishes.filter { it.bin == 0L && !it.priv }.associateBy { it.id }
        var m = read(c, f.code)
        val label = list.owner.takeIf { it.isNotBlank() }?.let { tr("%s’s wishlist", it) } ?: list.name.ifBlank { f.name }
        if (label.isNotBlank() && label != label(c, f.code)) prefs(c).edit().putString("fresn_${f.code}", label).apply()
        // (An owned wish is not taken in again: its old reservation stays online and would be told about every time.)
        for (id in mine) visible[id]?.takeIf { !it.got }?.let { if (id !in m) m = m + (id to it.label) }
        for ((id, title) in m) {
            val now = visible[id]
            // Still on the list and not owned: nothing to say.
            if (now != null && !now.got) continue
            // Moved to another of the friend's lists (owner, 7 November 2026): the reservation went along, so this
            // phone follows it there quietly instead of saying it's gone.
            val to = moved[id]
            if (now == null && to != null && to != f.code) {
                write(c, to, read(c, to) + (id to title))
                if (label(c, to).isBlank()) label(c, f.code).takeIf { it.isNotBlank() }?.let { prefs(c).edit().putString("fresn_$to", it).apply() }
                m = m - id
                continue
            }
            // Received by the friend after the occasion (2.128): a thank-you, not "pick another gift".
            val recv = list.wishes.firstOrNull { it.id == id && it.recv > 0L }
            if (recv != null) {
                Alerts.notifyThanks(c, f.code, id, title, list.owner.ifBlank { list.name.ifBlank { f.name } })
                m = m - id
                continue
            }
            // Taken off, or (2.113, owner) marked as owned by the friend: say so once, so nobody buys a duplicate.
            // One notification for both (owner, 2.126), naming whose list it was.
            val place = list.owner.takeIf { it.isNotBlank() }?.let { tr("%s’s wishlist", it) } ?: ("“" + list.name.ifBlank { f.name } + "”")
            Alerts.notifyGiftLost(c, f.code, gift = false, id = id, title = title, image = now?.image.orEmpty(), place = place, owned = now != null)
            m = m - id
        }
        write(c, f.code, m)
        // 7 days before the list's occasion (2.128): a reminder of what you reserved there.
        val titles = m.keys.mapNotNull { visible[it]?.takeIf { w -> !w.got }?.label }
        Reminders.giver(c, f.code, gift = false, occ = list.occ, owner = list.owner, listName = list.name.ifBlank { f.name }, titles = titles)
    }

    /**
     * The background check of every friend's list ([only]: just these; null: all). A list that can't be loaded (or
     * isn't shared any more) is skipped. Returns the lists that were looked at.
     */
    suspend fun checkAll(c: Context, only: Set<String>? = null): Set<String> {
        val handled = HashSet<String>()
        for (code in codes(c)) {
            // The background check asks only about lists that changed (Pulse.kt, ROADMAP S3.3).
            if (only != null && code !in only) continue
            val f = Repo.friends.firstOrNull { it.code == code } ?: Friend(code, "")
            if (read(c, f.code).isEmpty()) { handled += code; continue }
            val list = try {
                Cloud.fetchList(f.code)
            } catch (e: Exception) {
                null
            } ?: continue
            check(c, f, list, moved = movedFrom(c, f.code, list))
            handled += code
        }
        return handled
    }

    /** Every list this phone reserved on (also friends' lists opened from the Friends tab). */
    fun codes(c: Context): List<String> = prefs(c).all.keys.filter { it.startsWith("fres_") }.map { it.removePrefix("fres_") }
}
