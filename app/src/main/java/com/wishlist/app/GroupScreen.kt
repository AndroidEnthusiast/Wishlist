package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Wishlist groups (see GroupsParts.kt): a group's page. Its members are drawn in GroupMembers.kt. */

// ---------- the group page ----------

/**
 * A group: its photo and name, then every member as a block (their picture, name and their list's newest wish). Tap
 * a member to open their list (yours opens as your own list). The organiser long-presses a member to remove them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupScreen(gid: String, snack: SnackbarHostState, onBack: () -> Unit, onOpenMine: (String) -> Unit) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val model = rememberGroup()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val g = Groups.group(gid)
    // What is open (the screen is keyed by the group, so this starts afresh for another group).
    val st = remember(gid) { GroupScreenState() }

    // Loaded when it opens, then every 30 seconds while it is on screen.
    LaunchedEffect(gid) {
        while (true) {
            try { Groups.refresh(ctx) } catch (e: Exception) {}
            delay(30000)
        }
    }
    // Made just now: the invite comes up so you can ask people in.
    LaunchedEffect(gid, places.inviteNow) {
        if (places.inviteNow == gid) { places.inviteNow = null; st.sharing = true }
    }

    // A wish card in a chat, or a notification, about a member's wish: that member's list opens (it then finds the wish).
    LaunchedEffect(g, places.askFocus) {
        val code = places.askFocus?.code ?: return@LaunchedEffect
        g?.members?.firstOrNull { it.list?.code == code }?.let { if (st.member?.list?.code != code) st.member = it }
    }

    val m = st.member
    if (m != null && g != null) {
        BackHandler { st.member = null }
        val code = m.list?.code ?: ""
        FriendScreen(
            Friend(code, m.list?.name ?: ""), snack, onRemoved = {},
            fetch = { Groups.fetchList(ctx, gid, code) }, onBack = { st.member = null }, ownerName = m.name,
            // "Chat about this wish" only with a member who is your friend (owner, 20 November 2026).
            chatWith = if (m.friend && !m.web) m.id else null,
            extraAction = if (!m.friend && !m.web && m.code.isNotEmpty()) ({ AddFriendButton(m, snack) }) else null
        )
        return
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(g?.name ?: tr("Wishlist group")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    if (g != null) Box {
                        IconButton(onClick = { st.menu = true }) { Icon(AppIcons.MoreVert, tr("More")) }
                        DropdownMenu(expanded = st.menu, onDismissRequest = { st.menu = false }) {
                            if (Groups.canInvite(ctx, g)) {
                                DropdownMenuItem(text = { Text(tr("Invite people")) }, leadingIcon = { Icon(AppIcons.Friends, null) }, onClick = { st.menu = false; st.sharing = true })
                            }
                            DropdownMenuItem(text = { Text(tr("Change my wishlist")) }, leadingIcon = { Icon(AppIcons.Swap, null) }, onClick = { st.menu = false; st.changing = true })
                            if (Groups.isOrganiser(ctx, g)) {
                                DropdownMenuItem(text = { Text(tr("Group settings")) }, leadingIcon = { Icon(AppIcons.Settings, null) }, onClick = { st.menu = false; st.settings = true })
                            }
                            DropdownMenuItem(text = { Text(tr("Leave group")) }, leadingIcon = { Icon(AppIcons.Leave, null) }, danger = true, onClick = { st.menu = false; st.leaving = true })
                        }
                    }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (g == null) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                EmptyState(AppIcons.Group, tr("This group isn’t available any more."), tr("Back"), onBack)
            }
            return@Scaffold
        }
        val me = Groups.myId(ctx)
        val organiser = g.members.firstOrNull { it.id == g.organiser }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Groups.photos[g.id]?.let { photo ->
                AsyncImage(
                    model = photo, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth().height(150.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }
            Text(
                (if (organiser != null) tr("Organised by %s", if (organiser.id == me) tr("you") else organiser.name) + " · " else "") +
                    (if (g.members.size == 1) tr("1 member") else tr("%d members", g.members.size)),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            // Your list was deleted: pick another one.
            if (Groups.me(ctx, g)?.list == null) {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth().lookCard(look, 7L, look.cardShape(7L, small = true)).padding(14.dp)
                ) {
                    Text(tr("You don’t bring a wishlist to this group yet."), color = look.cardInk(7L))
                    Button(onClick = { st.changing = true }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Choose a wishlist")) }
                }
            }
            // You first, then the others in the order they joined.
            val shown = g.members.sortedBy { if (it.id == me) 0 else 1 }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                shown.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { mb ->
                            MemberBlock(
                                g, mb, mb.id == me, Modifier.weight(1f),
                                onOpen = {
                                    when {
                                        mb.id == me -> Groups.myListId(ctx, g)?.let(onOpenMine) ?: run { st.changing = true }
                                        mb.list == null -> snack.toastSoon(tr("%s doesn’t bring a wishlist yet.", mb.name))
                                        else -> st.member = mb
                                    }
                                },
                                onRemove = if (Groups.isOrganiser(ctx, g) && mb.id != me) ({ st.removing = mb }) else null
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            // Invitations from the new-group form that couldn't be sent (owner, 2.125: only the organiser sees this).
            val unsent = Groups.unsent[g.id] ?: 0
            if (unsent > 0 && Groups.isOrganiser(ctx, g)) Text(
                if (unsent == 1) tr("1 invitation couldn’t be sent. Tap Invite people to try again.")
                else tr("%d invitations couldn’t be sent. Tap Invite people to try again.", unsent),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (Groups.canInvite(ctx, g)) {
                Button(onClick = { st.sharing = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(tr("Invite people")) }
            }
        }
    }

    if (g == null) return
    // One "Invite people" sheet (owner, 26 November 2026): who is in and invited, "+ Add friends", the group link.
    if (st.sharing) GroupShareSheet(g, onAdd = { st.sharing = false; st.inviting = true }) { st.sharing = false; model.clearUnsent(g.id) }
    if (st.inviting) InviteFriendsDialog(g, snack) { st.inviting = false; st.sharing = true; model.clearUnsent(g.id) }
    if (st.changing) ListPickDialog(
        title = tr("Change my wishlist"), confirm = tr("Save"), current = Groups.myListId(ctx, g), onClose = { st.changing = false }
    ) { id, fresh ->
        model.setList(g.id, id)
        model.newListShared(id, fresh)
        st.changing = false
        snack.toastSoon(tr("“%s” is in the group now", Repo.mineName(id)))
        null
    }
    if (st.settings) GroupSettingsDialog(g, snack, onDeleted = { st.settings = false; onBack() }) { st.settings = false }
    if (st.leaving) LeaveGroupDialog(g, snack, onLeft = { st.leaving = false; onBack() }) { st.leaving = false }
    st.removing?.let { mb ->
        var busy by remember(mb.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) st.removing = null },
            title = { Text(tr("Remove %1\$s from “%2\$s”?", mb.name, g.name)) },
            text = { Text(tr("They are told that they were removed. Their wishlist stays theirs.")) },
            confirmButton = {
                TextButton(danger = true, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val msg = try {
                            model.remove(g.id, mb.id)
                            tr("%s was removed from the group", mb.name)
                        } catch (e: Exception) {
                            tr("Couldn’t do that. Check your connection.")
                        }
                        busy = false
                        st.removing = null
                        snack.toastSoon(msg)
                    }
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { st.removing = null }) { Text(tr("Cancel")) } }
        )
    }
}
