package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Wishlist groups (2.108, see Groups.kt; split up in the rebuild, owner 19 November 2026, MAP "How a screen is built"):
 * the parts several group screens share, and groups on Home (invitation cards, the groups as blocks). The other
 * screens: GroupListPick.kt (the list you bring), GroupNew.kt, GroupJoin.kt, GroupScreen.kt, GroupMembers.kt and
 * GroupSettings.kt; what they change goes through GroupModel.kt.
 */

/**
 * Shows a message without waiting for it, on a scope that outlives the dialog that asked (a dialog's own scope is
 * cancelled as soon as it closes, which would take the message away at once).
 */
private val uiScope = kotlinx.coroutines.MainScope()
fun SnackbarHostState.toastSoon(message: String) { uiScope.launch { showToast(message) } }

/** Said when you delete a list that is brought to wishlist groups: it leaves them too. */
fun groupNote(c: Context, listId: String): String {
    val gs = Groups.groupsOf(c, listId)
    if (gs.isEmpty()) return ""
    return " " + tr("It also leaves your wishlist groups (%s).", gs.joinToString(", ") { it.name })
}

// ---------- Home ----------

/**
 * "Your groups" on Home: invites waiting for you, then your groups (on Home one sideways row with "New group" at the
 * end, owner 7 November 2026). Only shown once you have a group or an invite: Home starts with your own wishlists.
 */
/** Invitations to a wishlist group, as cards on Home (16 November 2026: the groups themselves are in Home's grid). */
@Composable
fun GroupInvites() {
    val ctx = LocalContext.current
    Groups.load(ctx)
    Groups.invites.forEach { InviteCard(it) }
}

/** Your wishlist groups as blocks for Home's one grid. */
@Composable
internal fun wishGroupItems(onOpen: (String) -> Unit): List<HubItem> {
    val ctx = LocalContext.current
    Groups.load(ctx)
    return Groups.groups.map { g ->
        val memberPics = g.members.mapNotNull { it.list?.image?.takeIf { i -> i.isNotBlank() } }.distinct().take(3)
        val pic = Groups.photos[g.id] ?: memberPics.firstOrNull()
        val pics = if (Groups.photos[g.id] != null) listOf(pic!!) else memberPics
        val n = g.members.size
        HubItem("g:" + g.id, g.name, n, pic, true, "", pics, if (n == 1) tr("1 member") else tr("%d members", n)) { onOpen(g.id) }
    }
}

@Composable
fun GroupsHomeSection(onHome: Boolean, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    Groups.load(ctx)
    if (Groups.groups.isEmpty() && (!onHome || Groups.invites.isEmpty())) return
    HubTitle(tr("Wishlist groups"), onMore = if (onHome && Groups.groups.isNotEmpty()) ({ places.homePage = "lists" }) else null)
    if (onHome) Groups.invites.forEach { InviteCard(it) }
    if (Groups.groups.isEmpty()) return
    val items = Groups.groups.map { g ->
        // The group's photo, else the newest photos of the members' lists.
        val memberPics = g.members.mapNotNull { it.list?.image?.takeIf { i -> i.isNotBlank() } }.distinct().take(3)
        val pic = Groups.photos[g.id] ?: memberPics.firstOrNull()
        val pics = if (Groups.photos[g.id] != null) listOf(pic!!) else memberPics
        val n = g.members.size
        HubItem("g:" + g.id, g.name, n, pic, true, "", pics, if (n == 1) tr("1 member") else tr("%d members", n)) { onOpen(g.id) }
    }
    HubBlocks(byRecent(items), onHome, actionsOf = { emptyList() }, onAddTo = {}, newLabel = tr("New group"), onNew = if (onHome) onNew else null)
}

/** An invite on Home: who invited you, the group, "No thanks" and "Join". */
@Composable
private fun InviteCard(v: GroupInvite) {
    val go = LocalGo.current
    val model = rememberGroup()
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    val id = v.group.hashCode().toLong()
    var busy by remember(v.group) { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .lookCard(look, id, look.cardShape(id, small = true)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(look.accent),
                contentAlignment = Alignment.Center
            ) { Icon(AppIcons.Group, null, tint = look.onAccent, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    tr("%s invited you", v.byName.ifBlank { tr("Someone") }),
                    style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
                )
                Text(
                    v.name, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { model.decline(v.group) } catch (e: Exception) {}
                    busy = false
                }
            }) { Text(tr("No thanks")) }
            Button(enabled = !busy, onClick = { go(Deep.Group(v.group)) }) { Text(tr("Join")) }
        }
    }
}

