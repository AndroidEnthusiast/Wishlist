package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/*
 * The state holder of the wishlist group screens (the rebuild, owner 19 November 2026; MAP "How a screen is built"):
 * the screens (GroupScreen.kt and the other Group*.kt files) only draw; everything that changes a group, or the list
 * you bring to it, goes through here. The data and the server calls stay in Groups (Groups.kt).
 */
@Stable
internal class GroupModel(private val ctx: Context) {
    // ---- making, joining, leaving ----
    suspend fun create(name: String, listId: String): String = Groups.create(ctx, name, listId)
    suspend fun join(link: String?, gid: String?, listId: String): String? = Groups.join(ctx, link, gid, listId)
    suspend fun decline(gid: String) = Groups.decline(ctx, gid)
    suspend fun setList(gid: String, listId: String) = Groups.setList(ctx, gid, listId)
    suspend fun leave(gid: String, next: String?) = Groups.leave(ctx, gid, next)
    /** "My link" of a browser member opened in the app: their list, groups and reservations move here. */
    suspend fun takeover(webId: String, webToken: String, webDevice: String): String? = Groups.takeover(ctx, webId, webToken, webDevice)

    // ---- the list you bring ----
    /** A new list made in the list picker, with the wishes copied in from your other lists. Its id. */
    fun newList(name: String, picked: List<Wish>): String {
        val made = Repo.createMine(ctx, name, "")
        Repo.copyInto(ctx, made, picked)
        return made
    }
    /** A list made while joining starts on "My friends", like every new list (2.105). After the group has it. */
    suspend fun newListShared(id: String, fresh: Boolean) {
        if (fresh) try { ListVisibility.set(ctx, id, Who.FRIENDS) } catch (e: Exception) {}
    }

    // ---- inviting and the members ----
    suspend fun invite(g: Group, ids: List<String>) = Groups.invite(ctx, g, ids)
    /** The new-group form's invitations; how many couldn't be sent. */
    suspend fun inviteNew(gid: String, ids: List<String>): Int = Groups.inviteNew(ctx, gid, ids)
    /** The group page's "couldn't be sent" line goes once you open Invite friends. */
    fun clearUnsent(gid: String) { Groups.unsent = Groups.unsent - gid }
    suspend fun remove(gid: String, member: String) = Groups.remove(ctx, gid, member)
    /** "Add friend" on a member who isn't your friend: ("friends" | "already" | sent, …). */
    suspend fun addFriend(code: String): Pair<String, String> = Friends.add(ctx, code)

    // ---- the organiser's settings ----
    suspend fun saveSettings(gid: String, name: String, inviteAll: Boolean) = Groups.settings(ctx, gid, name, inviteAll)
    suspend fun setPhoto(gid: String, src: String?, edit: HeroEdit, fa: Float) = Groups.setPhoto(ctx, gid, src, edit, fa)
    suspend fun newLink(gid: String) = Groups.newLink(ctx, gid)
    suspend fun delete(gid: String) = Groups.delete(ctx, gid)
}

/** The group screens' state holder, one per screen or dialog. */
@Composable
internal fun rememberGroup(): GroupModel {
    val ctx = LocalContext.current
    return remember { GroupModel(ctx) }
}

/** What is open on a group's page: a member's list, the ⋮ menu, which dialog. */
@Stable
internal class GroupScreenState {
    var member by mutableStateOf<GroupMember?>(null)
    var menu by mutableStateOf(false)
    var sharing by mutableStateOf(false)
    var inviting by mutableStateOf(false)
    var changing by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var leaving by mutableStateOf(false)
    var removing by mutableStateOf<GroupMember?>(null)
}
