package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/* Wishlist groups (see GroupsParts.kt): making a new group and choosing the friends to invite. */

// ---------- new group ----------

/**
 * "New group": a name and (owner, 2.125) the friends to invite, then the list you bring. The friends get their
 * invitation the moment the group is made. [onCreated] gets the new group's id.
 */
@Composable
fun NewGroupDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val model = rememberGroup()
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(1) }
    var friendIds by remember { mutableStateOf(listOf<String>()) }
    var pickingFriends by remember { mutableStateOf(false) }
    Friends.load(ctx)
    // Friends from the phone's copy, refreshed once in the background (offline: the copy, or the row stays hidden).
    LaunchedEffect(Unit) { try { Friends.refresh(ctx) } catch (e: Exception) {} }
    val friends = Friends.state?.friends ?: emptyList()
    if (step == 1) {
        FormSheet(
            onDismissRequest = onClose,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(tr("New group")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("Everyone shares one wishlist. You see each other’s lists and reserve gifts."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                        label = { Text(tr("Group name*")) }, singleLine = true, isError = missing,
                        supportingText = if (missing) ({ Text(tr("What is this group called?")) }) else null,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised
                    )
                    // Friends to invite (owner, 2.125): the same card as on every New … form (owner, 23 November 2026).
                    FriendsCard(friends, friendIds, onRemove = { id -> friendIds = friendIds - id }) { pickingFriends = true }
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
            val gid = model.create(name, id)
            model.newListShared(id, fresh)
            // The invitations go out now; any that fail are told on the group page (organiser only).
            if (friendIds.isNotEmpty()) model.inviteNew(gid, friendIds)
            onCreated(gid)
            null
        }
    }
    if (pickingFriends) PickFriendsDialog(friends, friendIds, onDone = { friendIds = it; pickingFriends = false }, onClose = { pickingFriends = false })
}

/** Choose friends (the new-group form): ticks, then Done. */
@Composable
fun PickFriendsDialog(
    friends: List<Person>, start: List<String>, onDone: (List<String>) -> Unit, onClose: () -> Unit,
    note: String = tr("They get an invitation when the group is made.")
) {
    var picked by remember { mutableStateOf(start.toSet()) }
    // No Friends (or no profile) yet: the card still opens, and says what to do instead (owner, 23 November 2026).
    if (friends.isEmpty()) {
        PickSheet(tr("Add friends"), onClose = onClose) { PickNote(noFriendsYet()) }
        return
    }
    // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt): tick friends, then Done.
    PickSheet(
        tr("Add friends"), onClose = onClose, note = note,
        action = tr("Done"), onAction = { onDone(friends.map { it.id }.filter { it in picked }) }
    ) {
        SheetGroup(friends.map { f ->
            {
                SheetRow(
                    { Avatar(Friends.photos[f.id], f.name, 32.dp) }, f.name.ifBlank { tr("Someone") },
                    { picked = if (f.id in picked) picked - f.id else picked + f.id },
                    chosen = f.id in picked, tile = false
                )
            }
        })
    }
}

/** What "Add friends" says on a New … form when you have no Friends yet. */
internal fun noFriendsYet(): String = tr("No Friends yet. After creating, you can send the invite link.")
