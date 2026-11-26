package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* Wishlist groups (see GroupsParts.kt): a member's block on the group page, "Add friend", and inviting friends. */

/** A member: their list's newest wish, their picture on it, their name (and "Organiser") and their list under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemberBlock(g: Group, m: GroupMember, isMe: Boolean, modifier: Modifier, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val id = m.id.hashCode().toLong()
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onRemove != null) DropdownMenuItem(
                    text = { Text(tr("Remove from group")) }, leadingIcon = { androidx.compose.material3.Icon(AppIcons.Close, null) }, danger = true,
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
internal fun AddFriendButton(m: GroupMember, snack: SnackbarHostState) {
    val model = rememberGroup()
    val scope = rememberCoroutineScope()
    var sent by remember(m.id) { mutableStateOf(false) }
    TextButton(enabled = !sent, onClick = {
        sent = true
        scope.launch {
            val msg = try {
                when (model.addFriend(m.code).first) {
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

/** "+ Add friends" from the group's share sheet: tick friends who aren't in or invited yet; they get a notification, a chat message and a card on Home. */
@Composable
fun InviteFriendsDialog(g: Group, snack: SnackbarHostState, onClose: () -> Unit) {
    val model = rememberGroup()
    val scope = rememberCoroutineScope()
    val taken = g.members.map { it.id }.toSet() + g.invited
    val friends = (Friends.state?.friends ?: emptyList()).filter { it.id !in taken }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    fun invite() {
        busy = true
        scope.launch {
            val msg = try {
                model.invite(g, picked.toList())
                if (picked.size == 1) tr("Invitation sent") else tr("%d invitations sent", picked.size)
            } catch (e: Exception) {
                tr("Couldn’t reach the server. Check your connection.")
            }
            busy = false
            onClose()
            snack.toastSoon(msg)
        }
    }
    // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt): tick friends, then Invite.
    PickSheet(
        tr("Add friends"), onClose = onClose, busy = busy,
        action = if (friends.isNotEmpty()) tr("Invite") else null, actionEnabled = picked.isNotEmpty(), onAction = { invite() }
    ) {
        if (friends.isEmpty()) PickNote(tr("All your friends are in it or invited."))
        else SheetGroup(friends.map { f ->
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
