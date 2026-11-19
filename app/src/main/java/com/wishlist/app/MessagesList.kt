package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale



/*
 * Messages (2.98, owner): a list of conversations like WhatsApp (picture, name, last message, time, unread bubble,
 * newest on top, + to start a chat with a friend), and a conversation with bubbles, ticks (one sent, two read), wishes
 * as cards, and long press on your own message to delete it for both. Everything in the selected style. Encrypted
 * end to end (Chat.kt, ChatCrypto.kt). Split up in the rebuild (owner, 19 November 2026; MAP "How a screen is built"):
 * this file has the conversations; one chat is ChatScreen.kt with its state holder ChatModel.kt, its bubbles in
 * ChatBubbles.kt and the typing bar, wish picker and photos in ChatParts.kt.
 */

/** A speech bubble: the Messages icon, from the app's own icons (2.210); [filled] for the chosen tab. */
@Composable
fun ChatIcon(tint: Color, size: Dp = 24.dp, filled: Boolean = false) {
    Icon(if (filled) AppIcons.ChatFilled else AppIcons.Chat, null, tint = tint, modifier = Modifier.size(size))
}

/** A paper plane: send. */
@Composable
internal fun SendIcon(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val p = Path().apply {
            moveTo(3f * u, 11f * u); lineTo(21f * u, 3f * u); lineTo(14f * u, 21f * u); lineTo(11f * u, 13f * u); close()
        }
        drawPath(p, tint)
    }
}

/** "14:05" today, "Yesterday", or "02-11-26". */
private fun shortTime(t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("HH:mm", Locale.UK).format(Date(t))
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (yesterday) tr("Yesterday") else SimpleDateFormat("dd-MM-yy", Locale.UK).format(Date(t))
}

internal fun dayLabel(t: Long): String {
    val s = shortTime(t)
    return if (s.contains(':')) tr("Today") else s
}

@Composable
fun MessagesScreen(snack: SnackbarHostState, onProfile: () -> Unit) {
    val places = LocalPlaces.current
    val peer = places.chat
    // Back from the chat (outside the movement, so only the chat on screen answers it).
    if (peer != null) BackHandler { places.chat = null }
    // The chat slides in over the list and back (Motion.kt); another chat straight from a notification fades through.
    Moving(peer, depth = { if (it != null) 1 else 0 }) { p ->
        if (p != null) ChatScreen(p, snack) else ConversationList(snack, onProfile)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationList(snack: SnackbarHostState, onProfile: () -> Unit) {
    val places = LocalPlaces.current
    val go = LocalGo.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var problem by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(Chat.conversations.isNotEmpty()) }
    // No tabs any more (owner, 15 November 2026: Friends is in the bottom bar anyway): only the conversations, and a
    // round chat button at the bottom right (no +: the bottom bar's + adds a wish) that opens your friends to pick from.
    var picking by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Friends.load(ctx)
    // Live (ROADMAP S3.2, Live.kt): fetched again when a message comes or goes; without the live connection every
    // 8 seconds, as before.
    LaunchedEffect(Unit) {
        Live.follow(Chat.liveTopic(ctx), 8000) {
            problem = try {
                Chat.refresh(ctx)
                false
            } catch (e: Exception) {
                true
            }
            loaded = true
        }
    }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Messages"), big = true) }, large = false,
                navigationIcon = { MenuSlot(back = false) },
                actions = { ProfileButton(onProfile) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) },
        // Only once there is a chat: before that, the empty page has the same action as its one button.
        floatingActionButton = { if (Config.enabled && Chat.conversations.isNotEmpty()) StartChatButton { picking = true } }
    ) { pad ->
        val list = Chat.conversations
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !Config.enabled || !Friends.hasProfile(ctx) && list.isEmpty() -> Empty(
                        tr("Your chats with friends show up here. Add a friend to start.")
                    ) {
                        EmptyAction(tr("Add friends")) { go(Deep.FriendsAsLeft) }
                    }
                    list.isEmpty() && !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    list.isEmpty() && problem -> Empty(tr("Couldn’t reach the server. Check your connection."))
                    // No conversations yet: a "Start a chat" chip that shows your friends (it goes once there is a chat).
                    list.isEmpty() -> Empty(tr("No chats yet. Say hello to a friend.")) {
                        EmptyAction(tr("Start a chat")) { picking = true }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        // Room at the bottom for the round chat button, so it never covers the last chat.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list, key = { it.id }) { cv -> ConversationRow(cv) { places.chat = cv.id } }
                        item {
                            Text(
                                tr("Messages are end-to-end encrypted: only you and your friend can read them."),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    if (picking) FriendPicker(onPick = { id -> picking = false; places.chat = id }, onClose = { picking = false })
}

/**
 * Start a chat (owner, 15 November 2026): a round button at the bottom right with the chat icon, no + and no words
 * (the bottom bar's + adds a wish). In your colour's soft wash, so it doesn't compete with that +.
 */
@Composable
private fun StartChatButton(onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = look.scheme
    val shape = CircleShape
    Box(
        Modifier.size(56.dp).then(
            if (look.design == "quiet") Modifier.shadow(6.dp, shape, clip = false).clip(shape).background(cs.primaryContainer)
            else Modifier.lookSurface(look, look.fabShape, cs.primaryContainer, depth = 5.dp)
        ).clickable(onClickLabel = tr("Start a chat"), onClick = onClick),
        contentAlignment = Alignment.Center
    ) { ChatIcon(cs.onPrimaryContainer, 26.dp) }
}

/**
 * Your friends to start a chat with (it replaces the Friends tab of Messages). Kept on the phone, so it also opens
 * offline (the chat then says it sends once you're back online). No friends yet: says so, with "Add a friend".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendPicker(onPick: (String) -> Unit, onClose: () -> Unit) {
    val go = LocalGo.current
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val friends = Friends.state?.friends.orEmpty().sortedBy { it.name.lowercase() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                tr("Start a chat"), style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            if (friends.isEmpty()) {
                Text(
                    tr("No friends yet. Add a friend, then chat with them here."), color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Button(onClick = { onClose(); go(Deep.FriendsAsLeft) }, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(tr("Add a friend"))
                }
            } else LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(friends, key = { it.id }) { f ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onPick(f.id) }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(Friends.photos[f.id], f.name, 44.dp)
                        Text(
                            f.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String, extra: @Composable () -> Unit = {}) {
    val look = LocalLook.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        // Quiet (2.207): the icon in a soft circle of your colour, like every empty page.
        if (look.design == "quiet") Box(
            Modifier.size(88.dp).clip(CircleShape).background(look.scheme.primaryContainer), contentAlignment = Alignment.Center
        ) { ChatIcon(look.scheme.onPrimaryContainer, 40.dp) }
        else ChatIcon(look.textAccent, 56.dp)
        Text(
            text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        extra()
    }
}

/** The one thing to do on an empty page: a filled button in Quiet (2.207), the style's chip elsewhere. */
@Composable
private fun EmptyAction(label: String, onClick: () -> Unit) {
    val look = LocalLook.current
    if (look.design == "quiet") Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(label) }
    else Text(
        label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        color = look.chipContent(true),
        modifier = Modifier.padding(top = 16.dp).lookChip(look, on = true).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

/** One conversation: picture, name, last message, time and the unread bubble. */
@Composable
private fun ConversationRow(cv: Conversation, onOpen: () -> Unit) {
    val look = LocalLook.current
    val id = cv.id.hashCode().toLong()
    val unread = cv.unread > 0
    Row(
        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(Friends.photos[cv.id], cv.name, 50.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                cv.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id),
                fontWeight = if (unread) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            cv.last?.let { m ->
                Text(
                    (if (m.mine) tr("You: ") else "") + Chat.preview(m.content), style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) look.cardInk(id) else look.cardMuted(id), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            cv.last?.let {
                Text(
                    shortTime(it.at), style = MaterialTheme.typography.labelSmall,
                    color = if (unread) look.textAccent else look.cardMuted(id)
                )
            }
            if (unread) Box(
                Modifier.padding(top = 4.dp).heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(look.accent)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(cv.unread.toString(), color = look.onAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
