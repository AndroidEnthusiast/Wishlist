package com.wishlist.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private fun toast(c: Context, message: String) {
    Toast.makeText(c, message, Toast.LENGTH_SHORT).show()
}

/** "New shared wishlist": a name for the list and your own name. */
@Composable
fun NewSharedDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(Shared.lastName) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("New shared wishlist")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Put gift ideas together with the other givers, for example for a birthday. Everyone you invite can add wishes and reserve what they’ll buy, so nobody gives the same gift twice. Don’t invite the person the gifts are for: they would see what’s reserved."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    name, { name = it.take(60) }, label = { Text(tr("Name of the wishlist")) },
                    placeholder = { Text(tr("Baby registry")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    me, { me = it.take(30) }, label = { Text(tr("Your name")) },
                    supportingText = { Text(tr("Shown on the wishes you add")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && me.isNotBlank() && Config.enabled,
                onClick = {
                    busy = true; err = ""
                    scope.launch {
                        try {
                            val l = Shared.create(ctx, name, me)
                            onCreated(l.code)
                        } catch (e: Exception) {
                            busy = false
                            err = tr("Couldn’t create it. Check your connection, and that the shared-lists script was run in Supabase. (%s)", (e.message ?: tr("no details")).take(90))
                        }
                    }
                }
            ) { Text(if (busy) tr("Creating…") else tr("Create")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/**
 * Accepting an invitation that reached the app as a link, a shared text, or (after an install) from the clipboard.
 * The invitation [key] is checked at once; then you see which list it is, type your name and join.
 */
@Composable
fun JoinDialog(key: String, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf(Shared.lastName) }
    // The name of the list, once the invitation was checked.
    var listName by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var joining by remember { mutableStateOf(false) }
    // True when checking failed only because the server couldn't be reached.
    var canRetry by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var err by remember { mutableStateOf("") }

    LaunchedEffect(key, attempt) {
        checking = true; err = ""; canRetry = false
        try {
            val n = Cloud.shInfo(key)
            if (n == null) err = tr("This invitation doesn’t work (any more). Ask for a new invite link.")
            else listName = n
        } catch (e: Exception) {
            err = tr("Couldn’t reach the server. Check your connection.")
            canRetry = true
        }
        checking = false
    }

    val name = listName
    AlertDialog(
        onDismissRequest = { if (!joining) onClose() },
        // Only the buttons close this, so a tap next to it doesn't lose the invitation.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (name != null) tr("Join “%s”?", name) else tr("Shared wishlist invitation")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (name != null) {
                    Text(
                        tr("You’ll be able to add and change wishes and reserve gifts on this list, together with the other givers."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        me, { me = it.take(30) }, label = { Text(tr("Your name")) },
                        supportingText = { Text(tr("Shown on the wishes you add")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                } else if (checking) {
                    Text(
                        tr("Checking the invitation…"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (name != null) {
                TextButton(
                    enabled = !joining && me.isNotBlank(),
                    onClick = {
                        joining = true; err = ""
                        scope.launch {
                            try {
                                val l = Shared.join(ctx, key, me)
                                if (l == null) {
                                    joining = false
                                    err = tr("This invitation doesn’t work (any more). Ask for a new invite link.")
                                } else {
                                    onJoined(l.code)
                                }
                            } catch (e: Exception) {
                                joining = false
                                err = tr("Couldn’t join. Check your connection.")
                            }
                        }
                    }
                ) { Text(if (joining) tr("Joining…") else tr("Join")) }
            } else if (canRetry) {
                TextButton(enabled = !checking, onClick = { attempt++ }) { Text(tr("Try again")) }
            }
        },
        dismissButton = {
            TextButton(enabled = !joining, onClick = onClose) {
                Text(if (name == null && !checking && !canRetry) tr("Close") else tr("Cancel"))
            }
        }
    )
}

/**
 * The share screen of the open shared wishlist: the invite link, the only link of a shared list (since 2.35: whoever has
 * it can add wishes and reserve gifts). Managing the list is in its settings.
 */
@Composable
fun SharedShareDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val l = Shared.current ?: return
    val title = Repo.listName
    val invite = inviteLink(l.key)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Share “%s”", title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tr("They can add and change wishes and reserve gifts. Only send it to people you trust."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Text(invite, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { ShareCard.share(ctx, ShareCard.Kind.INVITE, title, invite) } }) { Text(tr("Send invite")) }
                    OutlinedButton(onClick = {
                        clip.setText(AnnotatedString(invite))
                        toast(ctx, tr("Invite link copied"))
                    }) { Text(tr("Copy")) }
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
    val l = Shared.current ?: return
    var busy by remember { mutableStateOf(false) }
    when (confirm) {
        "reset" -> AlertDialog(
            onDismissRequest = { if (!busy) onClose() },
            title = { Text(tr("Make a new invite link?")) },
            text = {
                Text(
                    tr("The old invite link stops working. People who already joined must open the new link to get back in.")
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            Shared.resetKey(ctx)
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
        "leave" -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(tr("Leave this wishlist?")) },
            text = {
                Text(
                    tr("It disappears from your menu. It stays for everyone else, and you can join again with the invite link.") +
                        (if (l.owner.isNotEmpty()) " " + tr("You made this list: after leaving you can no longer make a new invite link or delete it.") else "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Shared.leave(ctx, l.code)
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
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            Shared.deleteForEveryone(ctx)
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
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.errorContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                tr("This wishlist can’t be opened any more. It was deleted, or its invite link was reset. Ask for a new invite link and open it: the app asks if you want to join again."),
                color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { Shared.current?.let { Shared.leave(ctx, it.code) } }) { Text(tr("Remove from my menu")) }
        }
    }
}
