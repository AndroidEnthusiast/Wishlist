package com.wishlist.app

import android.content.Context
import org.json.JSONObject

// Making, joining and leaving groups, the organiser's settings and invites, your list in a group (a part of [Groups]
// since the data rebuild, owner 19 November 2026; callers keep writing Groups.…).

// ---- your list in a group ----

/**
 * Your list [listId] online for a group: its code and owner token. A list that was "Only me" goes online with its
 * friends' link off and not visible to friends, so it stays "Only me" for everyone outside your groups.
 */
internal suspend fun Groups.listOnline(c: Context, listId: String): Pair<String, String> {
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
suspend fun Groups.create(c: Context, name: String, listId: String): String {
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
suspend fun Groups.preview(link: String): GroupPreview? {
    val t = Cloud.rpc("wl_gr_info", JSONObject().put("p_link", link)).trim()
    if (t.isEmpty() || t == "null") return null
    val o = JSONObject(t)
    return GroupPreview(o.optString("id"), o.optString("name"), o.optString("organiser"), o.optInt("count"), o.optString("photo"))
}

/**
 * Join with group link [link], or with an invite to group [gid], bringing your list [listId]. Returns the group's
 * id, or null when the link or invite doesn't work any more.
 */
suspend fun Groups.join(c: Context, link: String?, gid: String?, listId: String): String? {
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
suspend fun Groups.setList(c: Context, gid: String, listId: String) {
    val (code, token) = listOnline(c, listId)
    Cloud.rpc("wl_gr_set_list", base(c).put("p_group", gid).put("p_list", code).put("p_list_token", token))
    refresh(c)
}

/** "No thanks" to an invite. Whoever invited you is told. */
suspend fun Groups.decline(c: Context, gid: String) {
    Cloud.rpc("wl_gr_decline", base(c).put("p_group", gid))
    invites = invites.filter { it.group != gid }
    refresh(c)
}

/** Leave a group. The organiser names the next organiser ([next]) first. */
suspend fun Groups.leave(c: Context, gid: String, next: String?) {
    Cloud.rpc("wl_gr_leave", base(c).put("p_group", gid).put("p_next", next ?: ""))
    groups = groups.filter { it.id != gid }
    refresh(c)
}

/** The organiser removes member [member] (they are told). */
suspend fun Groups.remove(c: Context, gid: String, member: String) {
    Cloud.rpc("wl_gr_remove", base(c).put("p_group", gid).put("p_member", member))
    refresh(c)
}

/** The organiser's name and "Members can invite too". */
suspend fun Groups.settings(c: Context, gid: String, name: String, inviteAll: Boolean) {
    Cloud.rpc("wl_gr_settings", base(c).put("p_group", gid).put("p_name", name.trim()).put("p_invite_all", inviteAll))
    refresh(c)
}

/** The organiser makes a new group link; the old one stops working. */
suspend fun Groups.newLink(c: Context, gid: String) {
    Cloud.rpc("wl_gr_new_link", base(c).put("p_group", gid).put("p_link", Sync.newKey()))
    refresh(c)
}

/** The organiser deletes the group for everyone. Nobody's wishlist is deleted. */
suspend fun Groups.delete(c: Context, gid: String) {
    Cloud.rpc("wl_gr_delete", base(c).put("p_group", gid))
    groups = groups.filter { it.id != gid }
    refresh(c)
}

/**
 * Invites your friends [ids] to group [g]: online (they see it on Home and get a notification) and as a chat
 * message with the link. A message that can't be sent (a friend who never opened Messages) is skipped: the invite
 * itself still reaches them.
 */
suspend fun Groups.invite(c: Context, g: Group, ids: List<String>) {
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
suspend fun Groups.inviteNew(c: Context, gid: String, ids: List<String>): Int {
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

/** A brought list of someone in group [gid]. Null when it isn't in the group (any more). */
suspend fun Groups.fetchList(c: Context, gid: String, code: String): RemoteList? {
    val t = Cloud.rpc("wl_gr_list", base(c).put("p_group", gid).put("p_code", code)).trim()
    if (t.isEmpty() || t == "null") return null
    return Cloud.parseRemote(JSONObject(t))
}
