package com.wishlist.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/* A shared wishlist (rebuilt 19 November 2026): making one and joining one; sharing and managing it is SharedManage.kt;
 * what they change goes through SharedListModel.kt. */

fun toast(c: Context, message: String, long: Boolean = false) {
    Toast.makeText(c, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/**
 * A new Co-op wishlist (was "Create a collab wishlist"; owner, 17 November 2026): a name, a description and the Friends
 * to invite (owner, 23 November 2026: "Gifts for someone?" is gone, Friends are picked here instead of afterwards).
 * The invitations go out once the list is made; any that can't be sent are told, with where to try again. [top]: the
 * Personal / Co-op choice of [NewWishlistSheet].
 */
@Composable
fun NewSharedDialog(onCreated: (String) -> Unit, onClose: () -> Unit, top: (@Composable () -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = rememberSharedList()
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    // "Added by" uses your profile name (owner, 2.102): not asked here.
    val me = Shared.lastName
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var friendIds by remember { mutableStateOf(listOf<String>()) }
    var pickingFriends by remember { mutableStateOf(false) }
    // Friends from the phone's copy, refreshed once in the background (offline: the copy).
    if (Friends.hasProfile(ctx)) Friends.load(ctx)
    LaunchedEffect(Unit) { if (Friends.hasProfile(ctx)) try { Friends.refresh(ctx) } catch (e: Exception) {} }
    val friends = if (Friends.hasProfile(ctx)) Friends.state?.friends ?: emptyList() else emptyList()

    FormSheet(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("New wishlist")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // What Co-op means is said under the Personal / Co-op choice (owner, 21 November 2026).
                top?.invoke()
                OutlinedTextField(
                    name, { name = it.take(60); if (it.isNotBlank()) missing = false }, label = { Text(tr("Wishlist name*")) },
                    placeholder = { Text(tr("Our wedding, Gifts for Mum…")) }, singleLine = true, isError = missing,
                    supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                // No "Shown at the top of the list." under it (owner, 23 November 2026: fewer words).
                OutlinedTextField(
                    notes, { notes = it.take(ABOUT_MAX) },
                    label = { Text(tr("Description")) }, placeholder = { Text(tr("What is this wishlist for?")) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                // No occasion here (owner, 21 November 2026: everything on one page; it is in Wishlist settings).
                // Friends to invite (owner, 23 November 2026), the same card as on New group and New gift exchange.
                FriendsCard(friends, friendIds, onRemove = { id -> friendIds = friendIds - id }) { pickingFriends = true }
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && Config.enabled,
                onClick = {
                    if (name.isBlank()) {
                        missing = true
                        return@TextButton
                    }
                    busy = true; err = ""
                    scope.launch {
                        val code = try {
                            model.create(name, me, notes, null)
                        } catch (e: Exception) {
                            busy = false
                            err = tr("Couldn’t create it. Check your connection and try again.", (e.message ?: tr("no details")).take(90))
                            return@launch
                        }
                        // The list is made: it opens whatever happens to the invitations.
                        val failed = model.inviteFriends(code, friendIds)
                        if (failed > 0) toast(ctx, coopInviteFailed(failed), long = true)
                        onCreated(code)
                    }
                }
            ) { Text(if (busy) tr("Creating…") else tr("Create")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } },
        unsaved = name.isNotBlank() || notes.isNotBlank() || friendIds.isNotEmpty()
    )
    if (pickingFriends) PickFriendsDialog(
        friends, friendIds, onDone = { friendIds = it; pickingFriends = false }, onClose = { pickingFriends = false },
        note = tr("They get an invitation when the wishlist is made.")
    )
}

/** What a new Co-op wishlist says when [failed] of its invitations couldn't be sent (the list was made). */
internal fun coopInviteFailed(failed: Int): String =
    if (failed == 1) tr("Couldn’t send 1 invitation. Tap Share, then Invite Friends, to try again.")
    else tr("Couldn’t send %d invitations. Tap Share, then Invite Friends, to try again.", failed)

/**
 * Accepting an invitation that reached the app as a link, a shared text, or (after an install) from the clipboard.
 * The invitation [key] is checked at once; then you see which list it is, type your name and join.
 */
@Composable
fun JoinDialog(key: String, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = rememberSharedList()
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
            val n = model.inviteName(key)
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
        title = { Text(if (name != null) tr("Join “%s”?", name) else tr("Invitation to a wishlist")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (name != null) {
                    Text(
                        tr("Add wishes and reserve gifts together with the other givers."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Your profile name is used (owner, 2.102); only asked when there is none yet (a brand-new install).
                    if (Shared.lastName.isBlank()) OutlinedTextField(
                        me, { me = it.take(30) }, label = { Text(tr("Your name")) },
                        supportingText = { Text(tr("Shown on the wishes you add")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
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
                                val l = model.join(key, me)
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
