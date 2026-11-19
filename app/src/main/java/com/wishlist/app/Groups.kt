package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/*
 * Wishlist groups (2.108, with supabase-groups.sql; decided with the owner, see docs/DECISIONS.md "Wishlist groups").
 *  - A group is a few people who each bring ONE of their own wishlists (gift lists can't be brought). Everyone in it
 *    sees every brought list and reserves gifts on the others' lists; the owner of a list never sees reservations.
 *  - The group always sees the list you brought, whatever its "Visible to" says. A list that was "Only me" goes online
 *    for it, with its friends' link off and not visible to friends (so "Visible to" still says "Only me").
 *  - The organiser invites friends (or every member, with "Members can invite too") and has the group link: anyone
 *    with it can join. An invite comes as a notification, a chat message and a card on Home.
 *  - News told once: someone said "No thanks" (to whoever invited them), you were removed from a group.
 *  - People without the app join in the browser (web/group.html). Their private "My link" opened in the app moves
 *    their list, their places in groups and their reservations into the app ([takeover]).
 *
 * Since the data rebuild (owner, 19 November 2026) this file is the front door (your groups, invites, the background
 * check and its news); the rest is in parts while callers keep writing Groups.…: GroupsMembership.kt (making, joining,
 * leaving, settings, invites, your list in a group), GroupsPhotos.kt ([GroupPhotos]), GroupsLinks.kt (links, takeover).
 * GroupsSameTest proves it all does exactly what it did before.
 */

data class GroupList(val code: String, val name: String, val count: Int, val image: String)

data class GroupMember(
    val id: String, val name: String, val pv: Long, val web: Boolean, val code: String, val friend: Boolean,
    val list: GroupList?
)

data class Group(
    val id: String, val link: String, val name: String, val pv: Long, val organiser: String, val inviteAll: Boolean,
    val members: List<GroupMember>, val invited: Set<String>
)

data class GroupInvite(
    val group: String, val name: String, val link: String, val by: String, val byName: String, val pv: Long,
    val count: Int
)

/** What a group link shows before you join (wl_gr_info). */
data class GroupPreview(val id: String, val name: String, val organiser: String, val count: Int, val photo: String)

object Groups {
    internal fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    var groups by mutableStateOf<List<Group>>(emptyList())
        internal set
    var invites by mutableStateOf<List<GroupInvite>>(emptyList())
        internal set

    // Photos (GroupsPhotos.kt), read as before.
    val photos: Map<String, String> get() = GroupPhotos.photos
    val faces: Map<String, String> get() = GroupPhotos.faces

    private var loaded = false

    /** Invitations that couldn't be sent when a group was made, by group id (shown to the organiser until done). */
    var unsent by mutableStateOf(mapOf<String, Int>())

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        prefs(c).getString("gr_state", null)?.let { parse(it) }?.let { (g, i) -> groups = g; invites = i }
        GroupPhotos.photos = files(groupDir(c))
        GroupPhotos.faces = files(faceDir(c))
    }

    fun myId(c: Context): String = prefs(c).getString("pf_id", "") ?: ""

    internal fun base(c: Context): JSONObject {
        val p = prefs(c)
        return JSONObject().put("p_id", p.getString("pf_id", "")).put("p_token", p.getString("pf_token", ""))
    }

    fun group(id: String?): Group? = groups.firstOrNull { it.id == id }

    fun isOrganiser(c: Context, g: Group) = g.organiser == myId(c)

    /** You may invite people (and share the link) in this group. */
    fun canInvite(c: Context, g: Group) = g.inviteAll || isOrganiser(c, g)

    /** Your own place in group [g]. */
    fun me(c: Context, g: Group): GroupMember? = g.members.firstOrNull { it.id == myId(c) }

    /** The id of your own list that you brought to group [g] (null: none, or it was deleted). */
    fun myListId(c: Context, g: Group): String? {
        val code = me(c, g)?.list?.code ?: return null
        return Repo.allMine(c).firstOrNull { it.code == code }?.id
    }

    /** Groups your list [listId] is brought to. */
    fun groupsOf(c: Context, listId: String): List<Group> {
        val code = Repo.mine(c, listId).code ?: return emptyList()
        val me = myId(c)
        return groups.filter { g -> g.members.any { it.id == me && it.list?.code == code } }
    }

    // ---- loading ----

    internal fun parse(t: String): Pair<List<Group>, List<GroupInvite>>? = try {
        val o = JSONObject(t)
        val ga = o.optJSONArray("groups") ?: JSONArray()
        val gs = (0 until ga.length()).map { i ->
            val g = ga.getJSONObject(i)
            val ma = g.optJSONArray("members") ?: JSONArray()
            val inv = g.optJSONArray("invited") ?: JSONArray()
            Group(
                g.getString("id"), g.optString("link"), g.optString("name"), g.optLong("pv"), g.optString("organiser"),
                g.optBoolean("invite_all"),
                (0 until ma.length()).map { j ->
                    val m = ma.getJSONObject(j)
                    val l = m.optJSONObject("list")
                    GroupMember(
                        m.getString("id"), m.optString("name"), m.optLong("pv"), m.optBoolean("web"), m.optString("code"),
                        m.optBoolean("friend"),
                        l?.let { GroupList(it.optString("code"), it.optString("name"), it.optInt("count"), it.optString("image")) }
                    )
                },
                (0 until inv.length()).map { inv.getString(it) }.toSet()
            )
        }
        val ia = o.optJSONArray("invites") ?: JSONArray()
        val ins = (0 until ia.length()).map { i ->
            val v = ia.getJSONObject(i)
            GroupInvite(
                v.getString("group"), v.optString("name"), v.optString("link"), v.optString("by"), v.optString("by_name"),
                v.optLong("pv"), v.optInt("count")
            )
        }
        gs to ins
    } catch (e: Exception) {
        null
    }

    /** Loads your groups and invites. Throws when offline (the last state stays). */
    suspend fun refresh(c: Context) = lock.withLock {
        load(c)
        Friends.ensure(c)
        fetchLocked(c, onScreen = true)
    }

    /** The background check: notifications for invites and news. */
    suspend fun poll(c: Context) {
        load(c)
        if (!prefs(c).getBoolean("pf_made", false)) return
        lock.withLock { fetchLocked(c, onScreen = false) }
    }

    internal suspend fun fetchLocked(c: Context, onScreen: Boolean) {
        val t = Cloud.rpc("wl_gr_state", base(c))
        val (g, i) = parse(t) ?: return
        groups = g
        invites = i
        prefs(c).edit().putString("gr_state", t).apply()
        news(c, JSONObject(t).optJSONArray("news") ?: JSONArray(), i, onScreen)
        loadPhotos(c)
        // Photos of groups you organise move from the database to files, once (PeoplePhotos, 2.185).
        if (onScreen) for (gr in g) if (gr.organiser == myId(c) && gr.pv > 0) {
            PeoplePhotos.moveOld(c, "g:" + gr.id, { Cloud.rpc("wl_gr_photo", base(c).put("p_group", gr.id)) }) { bytes ->
                PeoplePhotos.save("g/" + gr.id, base(c), "wl_gr_photo_url", base(c).put("p_group", gr.id), bytes)
            }
        }
    }

    /** Invites and news are told once: a notification (only in the background) and a line on the Notifications page. */
    internal suspend fun news(c: Context, news: JSONArray, ins: List<GroupInvite>, onScreen: Boolean) {
        val p = prefs(c)
        val told = p.getStringSet("gr_inv_told", null)?.toSet() ?: emptySet()
        for (v in ins) if (v.group !in told) {
            val who = v.byName.ifBlank { tr("Someone") }
            if (!onScreen) Alerts.notifyGroup(
                c, v.group, tr("%1\$s invited you to “%2\$s”", who, v.name), tr("Pick the wishlist you want to bring, then join."),
                "group_invite", "g:" + v.group
            )
            Notifications.add(c, "group_invite", "g:" + v.group, v.name, extra = who, read = onScreen)
        }
        p.edit().putStringSet("gr_inv_told", ins.map { it.group }.toSet()).apply()
        var upto = 0L
        for (i in 0 until news.length()) {
            val n = news.getJSONObject(i)
            upto = maxOf(upto, n.optLong("id"))
            val gid = n.optString("group")
            val name = n.optString("name")
            when (n.optString("kind")) {
                "declined" -> {
                    val who = n.optString("who").ifBlank { tr("Someone") }
                    // Only on the Notifications page, no buzz (owner, 2.122: group changes aren't urgent).
                    Notifications.add(c, "group_declined", "g:$gid", who, extra = name, read = onScreen)
                }
                "removed" -> {
                    // Always a notification: you might not be looking at the group when it goes (owner).
                    Alerts.notifyGroup(c, "", tr("You are removed from “%s”", name), tr("The organiser removed you from this wishlist group."), "group_removed", "x:$gid")
                    Notifications.add(c, "group_removed", "x:$gid", name, read = onScreen)
                }
            }
        }
        if (upto > 0) Cloud.rpc("wl_gr_news_seen", base(c).put("p_upto", upto))
    }
}
