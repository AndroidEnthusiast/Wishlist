package com.wishlist.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The screens of wishlist groups (2.108, see Groups.kt): the section on Home, the group page with its members, the
 * list picker used to make, join and change, inviting friends, the organiser's settings, leaving, and moving a
 * browser member's list into the app.
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

/** "Wishlist groups" on Home: invites waiting for you, then your groups as blocks, two per row. */
@Composable
fun GroupsHomeSection(onHome: Boolean, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Groups.load(ctx)
    HubTitle(tr("Wishlist groups"), addLabel = tr("New group"), onAdd = if (onHome) onNew else null)
    if (onHome) Groups.invites.forEach { InviteCard(it) }
    if (Groups.groups.isEmpty() && (!onHome || Groups.invites.isEmpty())) {
        Text(
            tr("See each other’s wishlists with family or friends."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    val items = Groups.groups.map { g ->
        val pic = Groups.photos[g.id] ?: g.members.mapNotNull { it.list?.image?.takeIf { i -> i.isNotBlank() } }.firstOrNull()
        HubItem("g:" + g.id, g.name, g.members.size, pic, true, "") { onOpen(g.id) }
    }
    BlockGrid(byRecent(items), onHome = false, actionsOf = { emptyList() }, onAddTo = {})
}

/** An invite on Home: who invited you, the group, "No thanks" and "Join". */
@Composable
private fun InviteCard(v: GroupInvite) {
    val ctx = LocalContext.current
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
                    try { Groups.decline(ctx, v.group) } catch (e: Exception) {}
                    busy = false
                }
            }) { Text(tr("No thanks")) }
            Button(enabled = !busy, onClick = { GroupNav.openInvite(v.group, null) }) { Text(tr("Join")) }
        }
    }
}

// ---------- picking the list you bring ----------

/**
 * Which of your own wishlists you bring to a group, or a new one (empty, or with wishes copied from your other
 * lists). [onPick] gets the list's id and whether it was made here; it runs the action and returns an error message
 * or null. [extra]: shown above the choices (the join screen's group details).
 */
@Composable
fun ListPickDialog(
    title: String, confirm: String, current: String?, onClose: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    dismissLabel: String? = null, onDismissAction: (() -> Unit)? = null,
    onPick: suspend (String, Boolean) -> String?
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val lists = remember(Repo.mineVersion, Repo.mineIds) { Repo.allMine(ctx) }
    var chosen by remember { mutableStateOf(current ?: lists.firstOrNull()?.id ?: "new") }
    var name by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                extra?.invoke()
                Text(
                    tr("Which wishlist do you bring?"), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                lists.forEach { d ->
                    val n = if (d.name == "My wishlist") tr(d.name) else d.name
                    PickRow(n, if (d.wishes.size == 1) tr("1 wish") else tr("%d wishes", d.wishes.size), chosen == d.id) { chosen = d.id }
                }
                PickRow(tr("New wishlist"), tr("Empty, or with wishes from your other lists"), chosen == "new") { chosen = "new" }
                if (chosen == "new") {
                    OutlinedTextField(
                        name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                        label = { Text(tr("Name*")) }, singleLine = true, isError = missing,
                        supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), keyboardOptions = Capitalised
                    )
                    if (canCopy) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add wishes from your other lists"), style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                            Text(
                                when (picked.size) {
                                    0 -> tr("Optional")
                                    1 -> tr("1 wish chosen")
                                    else -> tr("%d wishes chosen", picked.size)
                                },
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    tr("Everyone in the group sees this wishlist, whatever “Visible to” says. Private wishes stay hidden."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (chosen == "new" && name.isBlank()) { missing = true; return@TextButton }
                busy = true
                error = null
                scope.launch {
                    val fresh = chosen == "new"
                    val id = if (fresh) {
                        val made = Repo.createMine(ctx, name, "")
                        Repo.copyInto(ctx, made, picked)
                        // Later tries use this list rather than making another one.
                        chosen = made
                        made
                    } else chosen
                    error = try {
                        onPick(id, fresh)
                    } catch (e: Exception) {
                        tr("Couldn’t reach the server. Check your connection.")
                    }
                    busy = false
                }
            }) { Text(confirm) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { if (onDismissAction != null) onDismissAction() else onClose() }) {
                Text(dismissLabel ?: tr("Cancel"))
            }
        }
    )
    if (picking) CopyWishesDialog(null, onDone = { picked = it; picking = false }, onClose = { picking = false })
}

@Composable
private fun PickRow(title: String, detail: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = on, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A list made while joining starts on "My friends", like every new list (2.105). After the group has it. */
private suspend fun newListShared(c: Context, id: String, fresh: Boolean) {
    if (fresh) try { ListVisibility.set(c, id, Who.FRIENDS) } catch (e: Exception) {}
}

// ---------- new group ----------

/** "New group": a name, then the list you bring. [onCreated] gets the new group's id. */
@Composable
fun NewGroupDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(1) }
    if (step == 1) {
        AlertDialog(
            onDismissRequest = onClose,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(tr("New group")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("Everyone in a group brings one wishlist, and you can all see each other’s wishlists and reserve gifts."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                        label = { Text(tr("Group name*")) }, singleLine = true, isError = missing,
                        supportingText = if (missing) ({ Text(tr("What is this group called?")) }) else null,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { if (name.isBlank()) missing = true else step = 2 }) { Text(tr("Next")) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
    } else {
        ListPickDialog(
            title = name.trim(), confirm = tr("Create group"), current = null, onClose = onClose,
            dismissLabel = tr("Back"), onDismissAction = { step = 1 }
        ) { id, fresh ->
            val gid = Groups.create(ctx, name, id)
            newListShared(ctx, id, fresh)
            onCreated(gid)
            null
        }
    }
}

// ---------- joining ----------

/**
 * The join screen: from a group link ([link]) or an invite ([gid]). Shows the group, then the list you bring. Already
 * in the group: it opens instead.
 */
@Composable
fun JoinGroupDialog(link: String?, gid: String?, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val invite = Groups.invites.firstOrNull { it.group == gid }
    var preview by remember(link, gid) { mutableStateOf<GroupPreview?>(null) }
    var state by remember(link, gid) { mutableStateOf("loading") }
    LaunchedEffect(link, gid) {
        try { Groups.refresh(ctx) } catch (e: Exception) {}
        // Already a member: open the group.
        val known = Groups.groups.firstOrNull { it.id == gid || (link != null && it.link == link) }
        if (known != null) { onJoined(known.id); return@LaunchedEffect }
        state = try {
            if (link != null) {
                preview = Groups.preview(link)
                if (preview == null) "gone" else "ok"
            } else if (Groups.invites.any { it.group == gid }) "ok" else "gone"
        } catch (e: Exception) {
            "offline"
        }
        // The link leads to a group you're already in (by another link): open it.
        preview?.let { p -> if (Groups.group(p.id) != null) onJoined(p.id) }
    }
    when (state) {
        "ok" -> {
            val name = preview?.name ?: invite?.name ?: ""
            ListPickDialog(
                title = tr("Join “%s”", name), confirm = tr("Join"), current = null, onClose = onClose,
                extra = {
                    val by = preview?.organiser ?: invite?.byName ?: ""
                    val count = preview?.count ?: invite?.count ?: 0
                    Text(
                        (if (by.isNotBlank()) tr("Organised by %s", by) + " · " else "") +
                            (if (count == 1) tr("1 member") else tr("%d members", count)),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                },
                dismissLabel = if (invite != null) tr("No thanks") else null,
                onDismissAction = if (invite != null) ({
                    scope.launch { try { Groups.decline(ctx, invite.group) } catch (e: Exception) {} }
                    onClose()
                }) else null
            ) { id, fresh ->
                val joined = Groups.join(ctx, link, if (link == null) gid else null, id)
                if (joined == null) tr("This invitation doesn’t work any more. Ask for a new one.")
                else {
                    newListShared(ctx, id, fresh)
                    onJoined(joined)
                    null
                }
            }
        }
        "loading" -> AlertDialog(
            onDismissRequest = onClose, title = { Text(tr("Wishlist group")) },
            text = { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
        else -> AlertDialog(
            onDismissRequest = onClose, title = { Text(tr("Wishlist group")) },
            text = {
                Text(
                    if (state == "gone") tr("This invitation doesn’t work any more. Ask for a new one.")
                    else tr("Couldn’t reach the server. Check your connection.")
                )
            },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("OK")) } }
        )
    }
}

// ---------- the group page ----------

/**
 * A group: its photo and name, then every member as a block (their picture, name and their list's newest wish). Tap
 * a member to open their list (yours opens as your own list). The organiser long-presses a member to remove them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupScreen(gid: String, snack: SnackbarHostState, onBack: () -> Unit, onOpenMine: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val g = Groups.group(gid)
    var member by remember(gid) { mutableStateOf<GroupMember?>(null) }
    var menu by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<GroupMember?>(null) }

    // Loaded when it opens, then every 30 seconds while it is on screen.
    LaunchedEffect(gid) {
        while (true) {
            try { Groups.refresh(ctx) } catch (e: Exception) {}
            delay(30000)
        }
    }
    // Made just now: the invite comes up so you can ask people in.
    LaunchedEffect(gid, GroupNav.inviteNow) {
        if (GroupNav.inviteNow == gid) { GroupNav.inviteNow = null; inviting = true }
    }

    val m = member
    if (m != null && g != null) {
        BackHandler { member = null }
        val code = m.list?.code ?: ""
        FriendScreen(
            Friend(code, m.list?.name ?: ""), snack, onMenu = {}, onRemoved = {},
            fetch = { Groups.fetchList(ctx, gid, code) }, onBack = { member = null }, ownerName = m.name,
            extraAction = if (!m.friend && !m.web && m.code.isNotEmpty()) ({ AddFriendButton(m, snack) }) else null
        )
        return
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(g?.name ?: tr("Wishlist group"), fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    if (g != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (Groups.canInvite(ctx, g)) {
                                DropdownMenuItem(text = { Text(tr("Invite friends")) }, onClick = { menu = false; inviting = true })
                                DropdownMenuItem(text = { Text(tr("Share group link")) }, onClick = { menu = false; Groups.share(ctx, g) })
                            }
                            DropdownMenuItem(text = { Text(tr("Change my wishlist")) }, onClick = { menu = false; changing = true })
                            if (Groups.isOrganiser(ctx, g)) {
                                DropdownMenuItem(text = { Text(tr("Group settings")) }, onClick = { menu = false; settings = true })
                            }
                            DropdownMenuItem(text = { Text(tr("Leave group"), color = cs.error) }, onClick = { menu = false; leaving = true })
                        }
                    }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (g == null) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(tr("This group isn’t available any more."), color = cs.onSurfaceVariant)
            }
            return@Scaffold
        }
        val me = Groups.myId(ctx)
        val organiser = g.members.firstOrNull { it.id == g.organiser }
        Column(Modifier.padding(pad).fillMaxSize().lookBackdrop(look).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
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
                    Button(onClick = { changing = true }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Choose a wishlist")) }
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
                                        mb.id == me -> Groups.myListId(ctx, g)?.let(onOpenMine) ?: run { changing = true }
                                        mb.list == null -> snack.toastSoon(tr("%s doesn’t bring a wishlist yet.", mb.name))
                                        else -> member = mb
                                    }
                                },
                                onRemove = if (Groups.isOrganiser(ctx, g) && mb.id != me) ({ removing = mb }) else null
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (Groups.canInvite(ctx, g)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { inviting = true }) { Text(tr("Invite friends")) }
                    OutlinedButton(onClick = { Groups.share(ctx, g) }) { Text(tr("Share group link")) }
                }
            }
        }
    }

    if (g == null) return
    if (inviting) InviteFriendsDialog(g, snack) { inviting = false }
    if (changing) ListPickDialog(
        title = tr("Change my wishlist"), confirm = tr("Save"), current = Groups.myListId(ctx, g), onClose = { changing = false }
    ) { id, fresh ->
        Groups.setList(ctx, g.id, id)
        newListShared(ctx, id, fresh)
        changing = false
        snack.toastSoon(tr("“%s” is in the group now", Repo.mineName(id)))
        null
    }
    if (settings) GroupSettingsDialog(g, snack, onDeleted = { settings = false; onBack() }) { settings = false }
    if (leaving) LeaveGroupDialog(g, snack, onLeft = { leaving = false; onBack() }) { leaving = false }
    removing?.let { mb ->
        var busy by remember(mb.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) removing = null },
            title = { Text(tr("Remove %1\$s from “%2\$s”?", mb.name, g.name)) },
            text = { Text(tr("They are told that they were removed. Their wishlist stays theirs.")) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val msg = try {
                            Groups.remove(ctx, g.id, mb.id)
                            tr("%s was removed from the group", mb.name)
                        } catch (e: Exception) {
                            tr("Couldn’t do that. Check your connection.")
                        }
                        busy = false
                        removing = null
                        snack.toastSoon(msg)
                    }
                }) { Text(tr("Remove"), color = cs.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { removing = null }) { Text(tr("Cancel")) } }
        )
    }
}

/** A member: their list's newest wish, their picture on it, their name (and "Organiser") and their list under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberBlock(g: Group, m: GroupMember, isMe: Boolean, modifier: Modifier, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val id = m.id.hashCode().toLong()
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onRemove != null) DropdownMenuItem(
                    text = { Text(tr("Remove from group"), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onRemove() }
                )
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).lookCard(look, id, look.cardShape(id, small = true))
                    .combinedClickable(onClick = onOpen, onLongClick = if (onRemove == null) null else ({ menu = true }))
            ) {
                val pic = m.list?.image?.takeIf { it.isNotBlank() }
                Box(
                    Modifier.fillMaxSize().padding(8.dp)
                        .lookMat(look, RoundedCornerShape(16.dp), look.pictureBg(id, pic != null), over = true),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        pic != null -> PhotoStage(pic, m.name, small = true)
                        m.list == null -> Text(
                            tr("No wishlist yet"), style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold, color = look.cardMuted(id)
                        )
                        m.list?.count == 0 -> Text(
                            tr("Empty"), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = look.cardMuted(id)
                        )
                        else -> PlaceholderIcon("gift", 40.dp)
                    }
                }
                // Their picture, bottom left on the block.
                Box(
                    Modifier.align(Alignment.BottomStart).padding(14.dp).size(44.dp).clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                ) { Avatar(Groups.faceOf(ctx, m), m.name, 44.dp) }
                // The number of wishes, top right (like the list blocks).
                val count = m.list?.count
                if (count != null) Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(28.dp).clip(CircleShape).background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(count.toString(), color = Color(0xFF1B1B1F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            if (isMe) tr("You") else m.name.ifBlank { tr("Someone") },
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        val sub = listOfNotNull(
            if (m.id == g.organiser) tr("Organiser") else null,
            m.list?.name
        ).joinToString(" · ")
        if (sub.isNotEmpty()) Text(
            sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, end = 2.dp)
        )
    }
}

/** "Add friend" on the list of a group member who isn't your friend (the normal request, with approval). */
@Composable
private fun AddFriendButton(m: GroupMember, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sent by remember(m.id) { mutableStateOf(false) }
    TextButton(enabled = !sent, onClick = {
        sent = true
        scope.launch {
            val msg = try {
                when (Friends.add(ctx, m.code).first) {
                    "friends" -> tr("You and %s are friends now", m.name)
                    "already" -> tr("Your friend request to %s is waiting for approval", m.name)
                    else -> tr("Friend request sent to %s", m.name)
                }
            } catch (e: Exception) {
                sent = false
                tr("Couldn’t reach the server. Check your connection.")
            }
            snack.toastSoon(msg)
        }
    }) { Text(if (sent) tr("Request sent") else tr("Add friend")) }
}

// ---------- inviting ----------

/** Pick friends to invite; they get a notification, a chat message and a card on Home. Or share the group link. */
@Composable
fun InviteFriendsDialog(g: Group, snack: SnackbarHostState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val members = g.members.map { it.id }.toSet()
    val friends = (Friends.state?.friends ?: emptyList()).filter { it.id !in members }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Invite friends")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (friends.isEmpty()) Text(
                    tr("No friends to invite yet. Share the group link instead."),
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                )
                friends.forEach { f ->
                    val waiting = f.id in g.invited
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !waiting) { picked = if (f.id in picked) picked - f.id else picked + f.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(Friends.photos[f.id], f.name, 36.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(f.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (waiting) Text(tr("Invited"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Checkbox(
                            checked = waiting || f.id in picked, enabled = !waiting,
                            onCheckedChange = { on -> picked = if (on) picked + f.id else picked - f.id }
                        )
                    }
                }
                TextButton(onClick = { Groups.share(ctx, g) }, modifier = Modifier.padding(top = 6.dp)) {
                    Text(tr("Share group link"))
                }
                Text(
                    tr("Anyone with the link can join, also without the app."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && picked.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val msg = try {
                        Groups.invite(ctx, g, picked.toList())
                        if (picked.size == 1) tr("Invitation sent") else tr("%d invitations sent", picked.size)
                    } catch (e: Exception) {
                        tr("Couldn’t reach the server. Check your connection.")
                    }
                    busy = false
                    onClose()
                    snack.toastSoon(msg)
                }
            }) { Text(tr("Invite")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Close")) } }
    )
}

// ---------- the organiser's settings ----------

/** Name, photo, "Members can invite too", a new link, and deleting the group. Only for the organiser. */
@Composable
fun GroupSettingsDialog(g: Group, snack: SnackbarHostState, onDeleted: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var name by remember(g.id) { mutableStateOf(g.name) }
    var inviteAll by remember(g.id) { mutableStateOf(g.inviteAll) }
    var busy by remember { mutableStateOf(false) }
    var askLink by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    val fa = coverAspect()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }

    fun run(done: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            val msg = try { block(); done } catch (e: Exception) { tr("Couldn’t do that. Check your connection.") }
            busy = false
            snack.toastSoon(msg)
        }
    }

    // While the photo editor is open, these settings step aside (what you typed is kept).
    if (pending == null) AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Group settings")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.take(60) }, label = { Text(tr("Group name*")) }, singleLine = true,
                    isError = name.isBlank(), modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Members can invite too"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("Otherwise only you invite people and share the link."),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    Switch(checked = inviteAll, onCheckedChange = { inviteAll = it })
                }
                Text(tr("Group photo"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Groups.photos[g.id]?.let {
                    AsyncImage(
                        model = it, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (Groups.photos[g.id] == null) tr("Choose a photo") else tr("New photo"))
                    }
                    if (Groups.photos[g.id] != null) TextButton(enabled = !busy, onClick = {
                        run(tr("Photo removed")) { Groups.setPhoto(ctx, g.id, null, HeroEdit(), fa) }
                    }) { Text(tr("Remove photo"), color = cs.error) }
                }
                Text(tr("Group link"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                TextButton(enabled = !busy, onClick = { askLink = true }) { Text(tr("Make a new link")) }
                TextButton(enabled = !busy, onClick = { askDelete = true }) { Text(tr("Delete group"), color = cs.error) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                if (name.trim() == g.name && inviteAll == g.inviteAll) { onClose(); return@TextButton }
                busy = true
                scope.launch {
                    val msg = try {
                        Groups.settings(ctx, g.id, name, inviteAll)
                        tr("Saved!")
                    } catch (e: Exception) {
                        tr("Couldn’t do that. Check your connection.")
                    }
                    busy = false
                    onClose()
                    snack.toastSoon(msg)
                }
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )

    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(),
            onSave = { chosen, e ->
                pending = null
                run(tr("Group photo saved")) {
                    Groups.setPhoto(ctx, g.id, chosen, e, fa)
                    ImageStore.delete(ctx, chosen)
                    if (chosen != img) ImageStore.delete(ctx, img)
                }
            },
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
    if (askLink) AlertDialog(
        onDismissRequest = { askLink = false },
        title = { Text(tr("Make a new link?")) },
        text = { Text(tr("The old group link stops working. Members stay, and invitations you already sent still work.")) },
        confirmButton = {
            TextButton(onClick = { askLink = false; run(tr("New link made")) { Groups.newLink(ctx, g.id) } }) { Text(tr("New link")) }
        },
        dismissButton = { TextButton(onClick = { askLink = false }) { Text(tr("Cancel")) } }
    )
    if (askDelete) DeleteGroupDialog(g, snack, onDeleted) { askDelete = false }
}

@Composable
private fun DeleteGroupDialog(g: Group, snack: SnackbarHostState, onDeleted: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Delete “%s” for everyone?", g.name)) },
        text = { Text(tr("The group goes for all its members. Everyone keeps their own wishlist.")) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        Groups.delete(ctx, g.id)
                        onClose()
                        onDeleted()
                        snack.toastSoon(tr("“%s” was deleted", g.name))
                    } catch (e: Exception) {
                        busy = false
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

// ---------- leaving ----------

/**
 * Leave a group. The organiser picks the next organiser first (owner: never automatic); alone in the group, they can
 * only delete it.
 */
@Composable
fun LeaveGroupDialog(g: Group, snack: SnackbarHostState, onLeft: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Groups.myId(ctx)
    val organiser = Groups.isOrganiser(ctx, g)
    val others = g.members.filter { it.id != me }
    var next by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (organiser && others.isEmpty()) {
        var deleting by remember { mutableStateOf(false) }
        if (deleting) DeleteGroupDialog(g, snack, onLeft, onClose)
        else AlertDialog(
            onDismissRequest = onClose,
            title = { Text(tr("Leave “%s”?", g.name)) },
            text = { Text(tr("You are the only member. You can delete the group instead.")) },
            confirmButton = { TextButton(onClick = { deleting = true }) { Text(tr("Delete group"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Leave “%s”?", g.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr("Your wishlist stays yours. You can join again with the group link."))
                if (organiser) {
                    Text(
                        tr("Choose the next organiser first:"), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    others.forEach { o ->
                        PickRow(o.name.ifBlank { tr("Someone") }, if (o.web) tr("Uses the browser") else tr("Uses the app"), next == o.id) { next = o.id }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (!organiser || next != null), onClick = {
                busy = true
                scope.launch {
                    try {
                        Groups.leave(ctx, g.id, next)
                        onLeft()
                        snack.toastSoon(tr("You left “%s”", g.name))
                    } catch (e: Exception) {
                        busy = false
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Leave"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

// ---------- a browser member moves to the app ----------

/** "My link" of a browser member was opened in the app: move their wishlist, groups and reservations here. */
@Composable
fun TakeoverDialog(me: Triple<String, String, String>, snack: SnackbarHostState, onDone: (String?) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Move your wishlist to the app?")) },
        text = {
            Column {
                Text(tr("The wishlist you made in the browser, your wishlist groups and your reservations move into the app. Your browser link stops working."))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val id = Groups.takeover(ctx, me.first, me.second, me.third)
                        onDone(id)
                        snack.toastSoon(tr("Your browser wishlist is in the app now"))
                    } catch (e: Exception) {
                        busy = false
                        error = tr("Couldn’t move it. The link may have been used already, or check your connection.")
                    }
                }
            }) { Text(tr("Move")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}
