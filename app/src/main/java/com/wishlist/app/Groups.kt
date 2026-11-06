package com.wishlist.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    var groups by mutableStateOf<List<Group>>(emptyList())
        private set
    var invites by mutableStateOf<List<GroupInvite>>(emptyList())
        private set
    /** Group photos on this phone, by group id (a file address). */
    var photos by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** Photos of group members who aren't friends, by profile id (friends' photos are in [Friends.photos]). */
    var faces by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        prefs(c).getString("gr_state", null)?.let { parse(it) }?.let { (g, i) -> groups = g; invites = i }
        photos = files(groupDir(c))
        faces = files(faceDir(c))
    }

    private fun files(dir: File): Map<String, String> = (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
        .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }

    fun myId(c: Context): String = prefs(c).getString("pf_id", "") ?: ""

    private fun base(c: Context): JSONObject {
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

    private fun parse(t: String): Pair<List<Group>, List<GroupInvite>>? = try {
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

    private suspend fun fetchLocked(c: Context, onScreen: Boolean) {
        val t = Cloud.rpc("wl_gr_state", base(c))
        val (g, i) = parse(t) ?: return
        groups = g
        invites = i
        prefs(c).edit().putString("gr_state", t).apply()
        news(c, JSONObject(t).optJSONArray("news") ?: JSONArray(), i, onScreen)
        loadPhotos(c)
    }

    /** Invites and news are told once: a notification (only in the background) and a line on the Notifications page. */
    private suspend fun news(c: Context, news: JSONArray, ins: List<GroupInvite>, onScreen: Boolean) {
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

    // ---- photos ----

    private fun groupDir(c: Context) = File(c.filesDir, "groups").apply { mkdirs() }
    private fun faceDir(c: Context) = File(c.filesDir, "groupfaces").apply { mkdirs() }

    private fun str(t: String) = t.trim().removeSurrounding("\"")

    private suspend fun loadPhotos(c: Context) {
        val gd = groupDir(c)
        val want = groups.filter { it.pv > 0 }.associate { it.id to "${it.id}_${it.pv}.jpg" }
        val out = HashMap<String, String>()
        for ((id, name) in want) {
            val f = File(gd, name)
            if (!f.exists()) try {
                val b64 = str(Cloud.rpc("wl_gr_photo", base(c).put("p_group", id)))
                if (b64.isEmpty()) continue
                withContext(Dispatchers.IO) { f.writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
            } catch (e: Exception) {
                continue
            }
            out[id] = Uri.fromFile(f).toString()
        }
        (gd.listFiles() ?: emptyArray()).filter { it.name !in want.values }.forEach { it.delete() }
        photos = out

        // Members who aren't your friends (friends' photos are loaded by Friends).
        val fd = faceDir(c)
        val me = myId(c)
        val people = groups.flatMap { it.members }.filter { it.id != me && it.pv > 0 && !it.friend }.distinctBy { it.id }
        val wantFaces = people.associate { it.id to "${it.id}_${it.pv}.jpg" }
        val outFaces = HashMap<String, String>()
        for ((id, name) in wantFaces) {
            val f = File(fd, name)
            if (!f.exists()) try {
                val b64 = str(Cloud.rpc("wl_gr_face", base(c).put("p_other", id)))
                if (b64.isEmpty()) continue
                withContext(Dispatchers.IO) { f.writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
            } catch (e: Exception) {
                continue
            }
            outFaces[id] = Uri.fromFile(f).toString()
        }
        (fd.listFiles() ?: emptyArray()).filter { it.name !in wantFaces.values }.forEach { it.delete() }
        faces = outFaces
    }

    /** A member's picture: your own, a friend's, or one loaded for the group. */
    fun faceOf(c: Context, m: GroupMember): String? =
        if (m.id == myId(c)) Friends.myPhoto.ifEmpty { null } else Friends.photos[m.id] ?: faces[m.id]

    // ---- your list in a group ----

    /**
     * Your list [listId] online for a group: its code and owner token. A list that was "Only me" goes online with its
     * friends' link off and not visible to friends, so it stays "Only me" for everyone outside your groups.
     */
    private suspend fun listOnline(c: Context, listId: String): Pair<String, String> {
        val before = Repo.mine(c, listId)
        if (before.code == null) {
            Sync.publishFor(c, listId)
            ListLink.set(c, listId, false)
        }
        val d = Repo.mine(c, listId)
        return (d.code ?: throw CloudException("no code")) to (d.token ?: throw CloudException("no token"))
    }

    // ---- making, joining, leaving ----

    /** A new group: you organise it and bring your list [listId]. Returns the group's id. */
    suspend fun create(c: Context, name: String, listId: String): String {
        Friends.ensure(c)
        val (code, token) = listOnline(c, listId)
        val gid = Sync.newKey()
        Cloud.rpc(
            "wl_gr_create", base(c).put("p_group", gid).put("p_link", Sync.newKey()).put("p_name", name.trim())
                .put("p_list", code).put("p_list_token", token)
        )
        refresh(c)
        return gid
    }

    /** What group link [link] leads to, or null when it doesn't work (any more). */
    suspend fun preview(link: String): GroupPreview? {
        val t = Cloud.rpc("wl_gr_info", JSONObject().put("p_link", link)).trim()
        if (t.isEmpty() || t == "null") return null
        val o = JSONObject(t)
        return GroupPreview(o.optString("id"), o.optString("name"), o.optString("organiser"), o.optInt("count"), o.optString("photo"))
    }

    /**
     * Join with group link [link], or with an invite to group [gid], bringing your list [listId]. Returns the group's
     * id, or null when the link or invite doesn't work any more.
     */
    suspend fun join(c: Context, link: String?, gid: String?, listId: String): String? {
        Friends.ensure(c)
        val (code, token) = listOnline(c, listId)
        val t = Cloud.rpc(
            "wl_gr_join", base(c).put("p_link", link ?: "").put("p_group", gid ?: "")
                .put("p_list", code).put("p_list_token", token)
        ).trim()
        if (t.isEmpty() || t == "null") return null
        val id = JSONObject(t).optString("id")
        refresh(c)
        return id
    }

    /** "Change my wishlist". */
    suspend fun setList(c: Context, gid: String, listId: String) {
        val (code, token) = listOnline(c, listId)
        Cloud.rpc("wl_gr_set_list", base(c).put("p_group", gid).put("p_list", code).put("p_list_token", token))
        refresh(c)
    }

    /** "No thanks" to an invite. Whoever invited you is told. */
    suspend fun decline(c: Context, gid: String) {
        Cloud.rpc("wl_gr_decline", base(c).put("p_group", gid))
        invites = invites.filter { it.group != gid }
        refresh(c)
    }

    /** Leave a group. The organiser names the next organiser ([next]) first. */
    suspend fun leave(c: Context, gid: String, next: String?) {
        Cloud.rpc("wl_gr_leave", base(c).put("p_group", gid).put("p_next", next ?: ""))
        groups = groups.filter { it.id != gid }
        refresh(c)
    }

    /** The organiser removes member [member] (they are told). */
    suspend fun remove(c: Context, gid: String, member: String) {
        Cloud.rpc("wl_gr_remove", base(c).put("p_group", gid).put("p_member", member))
        refresh(c)
    }

    /** The organiser's name and "Members can invite too". */
    suspend fun settings(c: Context, gid: String, name: String, inviteAll: Boolean) {
        Cloud.rpc("wl_gr_settings", base(c).put("p_group", gid).put("p_name", name.trim()).put("p_invite_all", inviteAll))
        refresh(c)
    }

    /** The organiser makes a new group link; the old one stops working. */
    suspend fun newLink(c: Context, gid: String) {
        Cloud.rpc("wl_gr_new_link", base(c).put("p_group", gid).put("p_link", Sync.newKey()))
        refresh(c)
    }

    /** The organiser deletes the group for everyone. Nobody's wishlist is deleted. */
    suspend fun delete(c: Context, gid: String) {
        Cloud.rpc("wl_gr_delete", base(c).put("p_group", gid))
        groups = groups.filter { it.id != gid }
        refresh(c)
    }

    /**
     * The group's photo after the editor ([src] cut as [edit], in the frame [fa]): a small JPEG sent online, seen by
     * every member. Null [src]: the photo is removed.
     */
    suspend fun setPhoto(c: Context, gid: String, src: String?, edit: HeroEdit, fa: Float) {
        val b64 = if (src == null) "" else {
            val cut = renderCover(c, src, edit, fa) ?: throw CloudException("photo")
            withContext(Dispatchers.IO) {
                val f = File(Uri.parse(cut).path ?: throw CloudException("photo"))
                val bmp = BitmapFactory.decodeFile(f.path) ?: throw CloudException("photo")
                f.delete()
                val w = minOf(900, bmp.width)
                val small = Bitmap.createScaledBitmap(bmp, w, (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1), true)
                val out = java.io.ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 80, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        }
        Cloud.rpc("wl_gr_set_photo", base(c).put("p_group", gid).put("p_photo", b64))
        refresh(c)
    }

    /**
     * Invites your friends [ids] to group [g]: online (they see it on Home and get a notification) and as a chat
     * message with the link. A message that can't be sent (a friend who never opened Messages) is skipped: the invite
     * itself still reaches them.
     */
    suspend fun invite(c: Context, g: Group, ids: List<String>) {
        for (id in ids) {
            Cloud.rpc("wl_gr_invite", base(c).put("p_group", g.id).put("p_friend", id))
            try {
                Chat.send(c, id, ChatContent(true, "group", g.id, g.name, url = link(g.link)))
            } catch (e: Exception) {
            }
        }
        refresh(c)
    }

    /**
     * Invitations sent while making a group (owner, 2.125): each friend on its own, so one that fails doesn't stop the
     * others. Returns how many couldn't be sent; the group page then tells the organiser ([unsent]).
     */
    suspend fun inviteNew(c: Context, gid: String, ids: List<String>): Int {
        val g = group(gid) ?: return ids.size
        var failed = 0
        for (id in ids) {
            try {
                Cloud.rpc("wl_gr_invite", base(c).put("p_group", g.id).put("p_friend", id))
            } catch (e: Exception) {
                failed++
                continue
            }
            try {
                Chat.send(c, id, ChatContent(true, "group", g.id, g.name, url = link(g.link)))
            } catch (e: Exception) {
            }
        }
        try { refresh(c) } catch (e: Exception) {}
        unsent = if (failed > 0) unsent + (gid to failed) else unsent - gid
        return failed
    }

    /** Invitations that couldn't be sent when a group was made, by group id (shown to the organiser until done). */
    var unsent by mutableStateOf(mapOf<String, Int>())

    /** A brought list of someone in group [gid]. Null when it isn't in the group (any more). */
    suspend fun fetchList(c: Context, gid: String, code: String): RemoteList? {
        val t = Cloud.rpc("wl_gr_list", base(c).put("p_group", gid).put("p_code", code)).trim()
        if (t.isEmpty() || t == "null") return null
        return Cloud.parseRemote(JSONObject(t))
    }

    // ---- links ----

    /** The group link, through the page group/ (its link preview), which opens the group page. */
    fun link(key: String): String = Config.SHARE_URL.trimEnd('/') + "/group/?g=" + key

    fun share(c: Context, g: Group) {
        // A name first (owner, 2.135): friends see who shares it.
        if (!NameGate.hasName()) { NameGate.ensure { share(c, g) }; return }
        val text = tr("Join my wishlist group “%1\$s”: %2\$s", g.name, link(g.link))
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(i, tr("Share group link")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val LINK = Regex("[?&]G=([A-HJ-NP-Z2-9]{20})(?![A-Z0-9])")
    private val ME = Regex("ME=([A-HJ-NP-Z2-9]{20})\\.([A-HJ-NP-Z2-9]{20})(?:\\.([A-Za-z0-9-]{1,64}))?")

    /** The group link key in text (a web link or wishlist://group?g=…), or null. */
    fun parseLink(s: String): String? = LINK.find(s.uppercase())?.groupValues?.get(1)

    /** A browser member's "My link" (…#me=ID.TOKEN.DEVICE): their profile id, token and device id, or null. */
    fun parseMe(s: String): Triple<String, String, String>? {
        val m = ME.find(s.replace("me=", "ME=")) ?: return null
        return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    // ---- a browser member moves to the app ----

    /**
     * Takes over a browser member's wishlist and groups ("My link" opened in the app; owner: now, not later).
     * Their list becomes one of your own lists (same code, so reservations stay), "Only me" outside the groups; their
     * places in groups (also as organiser) and the reservations made in that browser move to this phone. Returns the
     * new list's id (null when they had no list).
     */
    suspend fun takeover(c: Context, webId: String, webToken: String, webDevice: String): String? {
        Friends.ensure(c)
        val me = JSONObject(Cloud.rpc("wl_gr_web_me", JSONObject().put("p_id", webId).put("p_token", webToken)))
        if (!me.optBoolean("web")) throw CloudException("not a browser profile")
        val code = me.optString("list")
        val token = me.optString("list_token")
        val name = me.optString("name")
        // Read the list before the browser profile goes.
        val remote = if (code.isNotEmpty()) Cloud.fetchList(code) else null
        val p = prefs(c)
        // Their gift exchanges move first (2.129): the browser profile is deleted by wl_gr_takeover.
        Exchanges.takeover(c, webId, webToken)
        Cloud.rpc(
            "wl_gr_takeover", JSONObject().put("p_web", webId).put("p_web_token", webToken).put("p_web_device", webDevice)
                .put("p_id", p.getString("pf_id", "")).put("p_token", p.getString("pf_token", "")).put("p_device", Repo.deviceId)
        )
        // Your name on the app, when you hadn't given one yet.
        if (Shared.lastName.isBlank() && name.isNotBlank()) {
            Shared.setAppName(c, name)
            try { Friends.nameChanged(c) } catch (e: Exception) {}
        }
        var id: String? = null
        if (remote != null && token.isNotEmpty()) {
            val newId = Repo.createMine(c, remote.name, remote.about)
            Repo.setShareFor(c, newId, code, token)
            val wishes = remote.wishes.filter { it.id != ABOUT_ITEM_ID }
            Repo.updateMine(c, newId) { wishes }
            // Only the groups see it (its friends' link off); "Visible to" can open it to friends later.
            try { ListLink.set(c, newId, false) } catch (e: Exception) {}
            id = newId
        }
        refresh(c)
        return id
    }
}

/** Which group is open, and requests to open one or to join (from a link, a chat message or a notification). */
object GroupNav {
    /** The group on screen (its id), or null. */
    var open by mutableStateOf<String?>(null)
    /** A request to show the join screen: a group link key, or an invite's group id. */
    var joinLink by mutableStateOf<String?>(null)
    var joinGroup by mutableStateOf<String?>(null)
    /** A browser member's "My link" to take over. */
    var takeover by mutableStateOf<Triple<String, String, String>?>(null)
    /** A group just made: its page opens with "Invite friends". */
    var inviteNow by mutableStateOf<String?>(null)
    /** Set when something asked for a group screen, so the app switches to it. */
    var request by mutableStateOf(0)

    /** A group invitation was tapped (chat message or notification). */
    fun openInvite(gid: String?, link: String?) {
        if (gid != null && Groups.group(gid) != null) { open = gid; request++; return }
        joinGroup = gid?.takeIf { it.isNotEmpty() }
        joinLink = link
        request++
    }

    fun openGroup(gid: String) { open = gid; request++ }
}
