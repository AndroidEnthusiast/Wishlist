package com.wishlist.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/* Wishlist groups (see GroupsParts.kt): the organiser's settings, deleting a group, and leaving one. */

// ---------- the organiser's settings ----------

/** Name, photo, "Members can invite too", a new link, and deleting the group. Only for the organiser. */
@Composable
fun GroupSettingsDialog(g: Group, snack: SnackbarHostState, onDeleted: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val model = rememberGroup()
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
                    if (Groups.photos[g.id] != null) TextButton(danger = true, enabled = !busy, onClick = {
                        run(tr("Photo removed")) { model.setPhoto(g.id, null, HeroEdit(), fa) }
                    }) { Text(tr("Remove photo")) }
                }
                Text(tr("Group link"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                TextButton(enabled = !busy, onClick = { askLink = true }) { Text(tr("Make a new link")) }
                TextButton(danger = true, enabled = !busy, onClick = { askDelete = true }) { Text(tr("Delete group")) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                if (name.trim() == g.name && inviteAll == g.inviteAll) { onClose(); return@TextButton }
                busy = true
                scope.launch {
                    val msg = try {
                        model.saveSettings(g.id, name, inviteAll)
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
                    model.setPhoto(g.id, chosen, e, fa)
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
            TextButton(onClick = { askLink = false; run(tr("New link made")) { model.newLink(g.id) } }) { Text(tr("New link")) }
        },
        dismissButton = { TextButton(onClick = { askLink = false }) { Text(tr("Cancel")) } }
    )
    if (askDelete) DeleteGroupDialog(g, snack, onDeleted) { askDelete = false }
}

@Composable
private fun DeleteGroupDialog(g: Group, snack: SnackbarHostState, onDeleted: () -> Unit, onClose: () -> Unit) {
    val model = rememberGroup()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Delete “%s” for everyone?", g.name)) },
        text = { Text(tr("The group goes for all its members. Everyone keeps their own wishlist.")) },
        confirmButton = {
            TextButton(danger = true, enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        model.delete(g.id)
                        onClose()
                        onDeleted()
                        snack.toastSoon(tr("“%s” was deleted", g.name))
                    } catch (e: Exception) {
                        busy = false
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Delete")) }
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
    val model = rememberGroup()
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
            confirmButton = { TextButton(danger = true, onClick = { deleting = true }) { Text(tr("Delete group")) } },
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
            TextButton(danger = true, enabled = !busy && (!organiser || next != null), onClick = {
                busy = true
                scope.launch {
                    try {
                        model.leave(g.id, next)
                        onLeft()
                        snack.toastSoon(tr("You left “%s”", g.name))
                    } catch (e: Exception) {
                        busy = false
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Leave")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}
