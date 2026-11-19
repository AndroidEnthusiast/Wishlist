package com.wishlist.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* Wishlist groups (see GroupsParts.kt): joining from a link or an invitation, and a browser member moving to the app. */

// ---------- joining ----------

/**
 * The join screen: from a group link ([link]) or an invite ([gid]). Shows the group, then the list you bring. Already
 * in the group: it opens instead.
 */
@Composable
fun JoinGroupDialog(link: String?, gid: String?, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val model = rememberGroup()
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
                    scope.launch { try { model.decline(invite.group) } catch (e: Exception) {} }
                    onClose()
                }) else null
            ) { id, fresh ->
                val joined = model.join(link, if (link == null) gid else null, id)
                if (joined == null) tr("This invitation doesn’t work any more. Ask for a new one.")
                else {
                    model.newListShared(id, fresh)
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

// ---------- a browser member moves to the app ----------

/** "My link" of a browser member was opened in the app: move their wishlist, groups and reservations here. */
@Composable
fun TakeoverDialog(me: Triple<String, String, String>, snack: SnackbarHostState, onDone: (String?) -> Unit, onClose: () -> Unit) {
    val model = rememberGroup()
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
                        val id = model.takeover(me.first, me.second, me.third)
                        onDone(id)
                        snack.toastSoon(tr("Your browser wishlist is in the app now"))
                    } catch (e: Exception) {
                        busy = false
                        error = tr("Couldn’t move it. Check your connection, or the link was already used.")
                    }
                }
            }) { Text(tr("Move")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}
