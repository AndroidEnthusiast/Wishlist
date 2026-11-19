package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* A shared wishlist (see SharedUi.kt): Share, a new invite link, Leave, Delete for everyone, the notice when it is gone,
 * and picking a new owner. */

/**
 * The share screen of the open shared wishlist: the invite link, the only link of a shared list (since 2.35: whoever has
 * it can add wishes and reserve gifts). Managing the list is in its settings.
 */
@Composable
fun SharedShareDialog(list: SharedList? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    // [list]: a collab wishlist shared from its ⋮ on Home (owner, 7 November 2026), else the open one.
    val l = list ?: Shared.current ?: return
    val title = if (list != null) list.name else Repo.listName
    val invite = inviteLink(l.key)
    // Invite Friends in the app (owner, 7 November 2026): an invitation to accept, besides the link.
    var inviting by remember { mutableStateOf(false) }
    if (inviting) { GiftInviteFriendsDialog(l) { inviting = false }; return }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Share “%s”", title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("They can edit and reserve wishes. Only send it to people you trust."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
                // The surprise (owner, 17 November 2026): who it's for, when this phone made the list.
                val forWho = remember(l.code) { Shared.forName(ctx, l.code) }
                if (forWho.isNotBlank()) Text(
                    tr("Don’t send it to %s: it’s a surprise.", forWho),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold
                )
                Text(invite, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { ShareCard.share(ctx, ShareCard.Kind.INVITE, title, invite) } }) { Text(tr("Send invite")) }
                    OutlinedButton(onClick = {
                        clip.setText(AnnotatedString(invite))
                        toast(ctx, tr("Invite link copied"))
                    }) { Text(tr("Copy")) }
                }
                if (Friends.hasProfile(ctx) && !Friends.state?.friends.isNullOrEmpty()) {
                    Text(
                        tr("Or invite your Friends in the app:"), style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(onClick = { inviting = true }) { Text(tr("Invite Friends")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done")) } }
    )
}

/**
 * The questions behind "Make a new invite link" ([confirm] = "reset"), "Leave this wishlist" ("leave") and
 * "Delete for everyone" ("delete") in a shared list's settings. [onGone] runs when the list is gone from this phone.
 */
@Composable
fun SharedManageDialog(confirm: String, onClose: () -> Unit, onGone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = rememberSharedList()
    val l = Shared.current ?: return
    var busy by remember { mutableStateOf(false) }
    when (confirm) {
        "reset" -> AlertDialog(
            onDismissRequest = { if (!busy) onClose() },
            title = { Text(tr("Make a new invite link?")) },
            text = {
                Text(
                    tr("The old link will stop working. Everyone needs the new link to get back in.")
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            model.resetKey()
                            toast(ctx, tr("New invite link is ready"))
                        } catch (e: Exception) {
                            toast(ctx, tr("Couldn’t do that. Check your connection."))
                        }
                        busy = false; onClose()
                    }
                }) { Text(tr("Make new link")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
        )
        // The person who made the list picks a new owner first (owner, 2.128), or deletes it when nobody is left.
        "leave" -> if (l.owner.isNotEmpty()) CreatorLeaveDialog(l, onClose, onGone) else AlertDialog(
            onDismissRequest = onClose,
            title = { Text(tr("Leave this wishlist?")) },
            text = {
                Text(
                    tr("It disappears from your menu. It stays for everyone else, and you can join again with the invite link.") +
                        (if (l.owner.isNotEmpty()) " " + tr("You made this list: after leaving you can no longer make a new invite link or delete it.") else "")
                )
            },
            confirmButton = {
                // A dangerous main action is red (owner, 17 November 2026; fixed in the rebuild, 19 November 2026).
                TextButton(danger = true, onClick = {
                    model.leave(l.code)
                    onGone()
                }) { Text(tr("Leave")) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
        "delete" -> AlertDialog(
            onDismissRequest = { if (!busy) onClose() },
            title = { Text(tr("Delete for everyone?")) },
            text = {
                Text(tr("The list, all its wishes, pictures and reservations are deleted for everyone. This can’t be undone."))
            },
            confirmButton = {
                // Red, as every dangerous main action (fixed in the rebuild, 19 November 2026).
                TextButton(danger = true, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            model.deleteForEveryone()
                            onGone()
                        } catch (e: Exception) {
                            busy = false
                            toast(ctx, tr("Couldn’t delete it. Check your connection."))
                        }
                    }
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
        )
        else -> {}
    }
}

/** Shown on a shared wishlist whose edit key no longer works. */
@Composable
fun SharedGoneNotice() {
    val model = rememberSharedList()
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.errorContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                tr("This list was deleted or its link was renewed. Ask for the new invite link."),
                color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { Shared.current?.let { model.leave(it.code) } }) { Text(tr("Remove from my menu")) }
        }
    }
}

/**
 * Leaving a gift list you made (owner, 2.128): "Pick a new owner" from the members who use the app ("They can renew
 * the link and delete the list."), or, with nobody to pick, "Delete “…” for everyone? This can’t be undone."
 */
@Composable
private fun CreatorLeaveDialog(l: SharedList, onClose: () -> Unit, onGone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = rememberSharedList()
    val cs = MaterialTheme.colorScheme
    var members by remember { mutableStateOf<List<Member>?>(null) }
    var state by remember { mutableStateOf("loading") } // loading | ok | offline | old
    var picked by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(l.code) {
        state = try {
            members = model.members(l); "ok"
        } catch (e: Exception) {
            // Without the new script there is no member list: leave as before.
            if ((e.message ?: "").contains("wl_sh_members")) "old" else "offline"
        }
    }
    val ms = members.orEmpty()
    when {
        state == "old" -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(tr("Leave this wishlist?")) },
            text = { Text(tr("It disappears from your menu. It stays for everyone else, and you can join again with the invite link.")) },
            confirmButton = { TextButton(danger = true, onClick = { model.leave(l.code); onGone() }) { Text(tr("Leave")) } },
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
        state == "loading" -> AlertDialog(
            onDismissRequest = onClose, title = { Text(tr("Leave this wishlist?")) },
            text = { CircularProgressIndicator() },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
        state == "offline" -> AlertDialog(
            onDismissRequest = onClose, title = { Text(tr("Leave this wishlist?")) },
            text = { Text(tr("Couldn’t reach the server. Check your connection.")) },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("OK")) } }
        )
        ms.isEmpty() -> AlertDialog(
            onDismissRequest = { if (!busy) onClose() },
            title = { Text(tr("Delete “%s” for everyone?", l.name)) },
            text = { Text(tr("This can’t be undone.")) },
            confirmButton = {
                TextButton(danger = true, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            model.deleteForEveryone(); onGone()
                        } catch (e: Exception) {
                            busy = false; toast(ctx, tr("Couldn’t delete it. Check your connection."))
                        }
                    }
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
        )
        else -> AlertDialog(
            onDismissRequest = { if (!busy) onClose() },
            title = { Text(tr("Pick a new owner")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(tr("They can renew the link and delete the list."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    ms.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picked = m.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = picked == m.id, onClick = { picked = m.id })
                            Text(m.name.ifBlank { tr("Someone") })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(danger = true, enabled = !busy && picked != null, onClick = {
                    val to = picked ?: return@TextButton
                    busy = true
                    scope.launch {
                        try {
                            model.handOverAndLeave(l, to)
                            onGone()
                        } catch (e: Exception) {
                            busy = false; toast(ctx, tr("Couldn’t reach the server. Check your connection."))
                        }
                    }
                }) { Text(tr("Leave")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
        )
    }
}
