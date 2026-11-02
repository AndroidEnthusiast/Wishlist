package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Messages (2.98, owner): a list of conversations like WhatsApp (picture, name, last message, time, unread bubble,
 * newest on top, + to start a chat with a friend), and a conversation with bubbles, ticks (one sent, two read), wishes
 * as cards, and long press on your own message to delete it for both. Everything in the selected style. Encrypted
 * end to end (Chat.kt, ChatCrypto.kt).
 */

/** A speech bubble: the Messages icon (drawn here; the core Material icons have none). */
@Composable
fun ChatIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val p = Path().apply {
            moveTo(5f * u, 4f * u)
            lineTo(19f * u, 4f * u)
            quadraticBezierTo(21f * u, 4f * u, 21f * u, 6f * u)
            lineTo(21f * u, 15f * u)
            quadraticBezierTo(21f * u, 17f * u, 19f * u, 17f * u)
            lineTo(10f * u, 17f * u)
            lineTo(5.5f * u, 20.5f * u)
            lineTo(6f * u, 17f * u)
            lineTo(5f * u, 17f * u)
            quadraticBezierTo(3f * u, 17f * u, 3f * u, 15f * u)
            lineTo(3f * u, 6f * u)
            quadraticBezierTo(3f * u, 4f * u, 5f * u, 4f * u)
            close()
        }
        drawPath(p, tint, style = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(7.5f * u, 9f * u), Offset(16.5f * u, 9f * u), 1.8f * u, StrokeCap.Round)
        drawLine(tint, Offset(7.5f * u, 12.5f * u), Offset(13f * u, 12.5f * u), 1.8f * u, StrokeCap.Round)
    }
}

/** A paper plane: send. */
@Composable
private fun SendIcon(tint: Color) {
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

private fun dayLabel(t: Long): String {
    val s = shortTime(t)
    return if (s.contains(':')) tr("Today") else s
}

@Composable
fun MessagesScreen(snack: SnackbarHostState, onProfile: () -> Unit) {
    val peer = ChatNav.peer
    if (peer != null) {
        BackHandler { ChatNav.peer = null }
        ChatScreen(peer, snack)
    } else ConversationList(snack, onProfile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationList(snack: SnackbarHostState, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var problem by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(Chat.conversations.isNotEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var layoutDlg by remember { mutableStateOf(false) }
    Friends.load(ctx)
    ChatLayout.load(ctx)
    LaunchedEffect(Unit) {
        while (true) {
            problem = try {
                Chat.refresh(ctx)
                false
            } catch (e: Exception) {
                true
            }
            loaded = true
            delay(8000)
        }
    }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Messages"), fontWeight = look.titleWeight) },
                navigationIcon = { MenuSlot() },
                actions = {
                    IconButton(onClick = { picking = true }) { Icon(AppIcons.Add, tr("New chat")) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More")) }
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(tr("Chat layout")) }, onClick = { menu = false; layoutDlg = true }
                            )
                        }
                    }
                    ProfileButton(onProfile)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        val list = Chat.conversations
        Box(Modifier.padding(pad).fillMaxSize().lookBackdrop(look)) {
            when {
                !Config.enabled || !Friends.hasProfile(ctx) && list.isEmpty() -> Empty(
                    tr("Chat with your friends here. Add friends on the Friends tab first.")
                )
                list.isEmpty() && !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Empty(
                    if (problem) tr("Couldn’t reach the server. Check your connection.")
                    else tr("No messages yet. Tap + to start a chat with a friend.")
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { cv -> ConversationRow(cv) { ChatNav.peer = cv.id } }
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
    if (picking) NewChatDialog(onPick = { picking = false; ChatNav.peer = it }, onClose = { picking = false })
    if (layoutDlg) ChatLayoutDialog { layoutDlg = false }
}

@Composable
private fun Empty(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ChatIcon(LocalLook.current.accent, 56.dp)
        Text(
            text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
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
                    color = if (unread) look.accent else look.cardMuted(id)
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

/** Start a chat: your friends. */
@Composable
private fun NewChatDialog(onPick: (String) -> Unit, onClose: () -> Unit) {
    val friends = Friends.state?.friends.orEmpty()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("New chat")) },
        text = {
            if (friends.isEmpty()) Text(tr("You can chat with friends. Add friends on the Friends tab first."))
            else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                friends.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(f.id) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(Friends.photos[f.id], f.name, 40.dp)
                        Text(f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** One conversation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatScreen(peer: String, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val friend = Friends.state?.friends?.firstOrNull { it.id == peer }
    val convo = Chat.conversations.firstOrNull { it.id == peer }
    val name = friend?.name ?: convo?.name ?: tr("Someone")
    val canSend = friend != null
    var msgs by remember(peer) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember(peer) { mutableStateOf(true) }
    var text by remember(peer) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ChatMessage?>(null) }
    var wishPick by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    ChatLayout.load(ctx)

    // Load, then ask for new messages (and read ticks) every 3 seconds while the chat is open.
    LaunchedEffect(peer) {
        var round = 0
        while (true) {
            try {
                // Every 10th time (about every 30 seconds) the whole chat is loaded again, so a message its sender
                // deleted disappears here too; in between only new messages and read ticks are fetched.
                val full = round % 10 == 0
                val after = if (full) 0L else msgs.lastOrNull()?.id ?: 0L
                val fresh = Chat.messages(ctx, peer, after)
                if (full) msgs = fresh
                else {
                    val reads = msgs.firstOrNull { it.mine && !it.read }?.let { Chat.reads(ctx, peer, it.id) }.orEmpty()
                    var all = msgs.map { if (it.mine && it.id in reads) it.copy(read = true) else it }
                    if (fresh.isNotEmpty()) all = (all + fresh).distinctBy { it.id }
                    msgs = all
                }
            } catch (e: Exception) {
            }
            loading = false
            round++
            delay(3000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    fun send(content: ChatContent) {
        sending = true
        scope.launch {
            try {
                val m = Chat.send(ctx, peer, content)
                msgs = (msgs + m).distinctBy { it.id }
                if (content.kind == "text") text = ""
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t send. Check your connection."))
            }
            sending = false
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Friends.photos[peer], name, 36.dp)
                        Text(
                            name, fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = { ChatNav.peer = null }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().lookBackdrop(look).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (loading && msgs.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (msgs.isEmpty()) Text(
                    tr("Say hi! Messages are end-to-end encrypted: only you and %s can read them.", name),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    items(msgs.size, key = { msgs[it].id }) { i ->
                        val m = msgs[i]
                        ChatRow(m, msgs.getOrNull(i - 1), msgs.getOrNull(i + 1), ChatLayout.current, name, peer,
                            onLongPress = if (m.mine) ({ deleting = m }) else null)
                    }
                }
            }
            if (canSend) InputBar(
                text, { text = it }, sending,
                onSend = { if (text.isNotBlank()) send(ChatContent(true, "text", text.trim())) },
                onWish = { wishPick = true }
            ) else Text(
                tr("You’re no longer friends, so you can’t send messages here."),
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            )
        }
    }

    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("Delete message?")) },
            text = { Text(tr("It is deleted for both of you.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            Chat.delete(ctx, m.id)
                            msgs = msgs.filter { it.id != m.id }
                        } catch (e: Exception) {
                            snack.showToast(tr("Couldn’t reach the server. Check your connection."))
                        }
                    }
                }) { Text(tr("Delete"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel")) } }
        )
    }
    if (wishPick) WishPicker(onPick = { w ->
        wishPick = false
        send(ChatContent(true, "wish", "", w.title, w.price, w.currency, if (ImageStore.isLocal(w.image)) "" else w.image, w.url))
    }, onClose = { wishPick = false })
}

@Composable
private fun DayChip(label: String) {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = look.chipContent(false),
            modifier = Modifier.lookChip(look).padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/*
 * Chat layouts (2.99, owner): one for all chats, in the theme's colours and fonts (the theme never changes).
 *  - chat:  a small tail on the corner, time and ticks inside the bubble, a date chip per day.
 *  - calm:  rounded bubbles without a tail, more space; time and ticks under the last bubble of a group.
 *  - text:  simple rectangles, no ticks; the time between groups.
 *  - round: very round bubbles that merge within a group; their picture next to their last one; "Sent" / "Seen".
 * A group: messages from the same person within five minutes of each other.
 */
object ChatLayout {
    val KEYS = listOf("chat", "calm", "text", "round")
    var current by mutableStateOf("chat")
        private set
    private var loaded = false

    fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        current = c.applicationContext.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE)
            .getString("chat_layout", "chat")?.takeIf { it in KEYS } ?: "chat"
    }

    fun set(c: android.content.Context, key: String) {
        current = key
        c.applicationContext.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().putString("chat_layout", key).apply()
    }

    fun name(key: String) = when (key) { "calm" -> tr("Calm"); "text" -> tr("Text"); "round" -> tr("Round"); else -> tr("Chat") }

    fun about(key: String) = when (key) {
        "calm" -> tr("Rounded bubbles with more space; the time under a group.")
        "text" -> tr("Simple and plain, like text messages, without ticks.")
        "round" -> tr("Very round bubbles that join up, with your friend’s picture.")
        else -> tr("Bubbles with a small tail; time and ticks inside.")
    }
}

private fun sameGroup(a: ChatMessage?, b: ChatMessage?) =
    a != null && b != null && a.mine == b.mine && kotlin.math.abs(a.at - b.at) < 5 * 60_000L && dayLabel(a.at) == dayLabel(b.at)

/** One message in the chosen layout, with what goes around it (date chips, times, the picture). */
@Composable
fun ChatRow(m: ChatMessage, prev: ChatMessage?, next: ChatMessage?, layout: String, name: String, peer: String, onLongPress: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val first = !sameGroup(prev, m)
    val last = !sameGroup(m, next)
    val newDay = prev == null || dayLabel(prev.at) != dayLabel(m.at)
    val hm = SimpleDateFormat("HH:mm", Locale.UK).format(Date(m.at))
    when (layout) {
        "text" -> {
            // The time (with the day when it is another day) between groups, centred and quiet.
            if (first) Text(
                if (dayLabel(m.at) == tr("Today")) hm else dayLabel(m.at) + " " + hm,
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = if (prev == null) 0.dp else 14.dp, bottom = 6.dp)
            )
            Bubble(m, RoundedCornerShape(6.dp), meta = false, onLongPress = onLongPress, modifier = Modifier.padding(bottom = 4.dp))
        }
        "calm" -> {
            if (newDay) DayChip(dayLabel(m.at))
            Bubble(m, RoundedCornerShape(18.dp), meta = false, onLongPress = onLongPress,
                modifier = Modifier.padding(top = if (first && !newDay) 10.dp else 3.dp))
            if (last) Meta(m, hm, Modifier.fillMaxWidth().padding(top = 3.dp, start = 6.dp, end = 6.dp))
        }
        "round" -> {
            if (newDay) DayChip(dayLabel(m.at))
            // Inner corners of a group are small, so the bubbles join up.
            val big = 22.dp; val small = 6.dp
            val shape = if (m.mine) RoundedCornerShape(big, if (first) big else small, if (last) big else small, big)
            else RoundedCornerShape(if (first) big else small, big, big, if (last) big else small)
            Row(
                Modifier.fillMaxWidth().padding(top = if (first && !newDay) 10.dp else 2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (!m.mine) {
                    Box(Modifier.size(28.dp)) { if (last) Avatar(Friends.photos[peer], name, 28.dp) }
                    Spacer(Modifier.size(6.dp))
                }
                Box(Modifier.weight(1f)) { Bubble(m, shape, meta = false, onLongPress = onLongPress) }
            }
            if (last && m.mine) Text(
                if (m.read) tr("Seen") else tr("Sent"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 6.dp)
            )
        }
        else -> {
            if (newDay) DayChip(dayLabel(m.at))
            // A small "tail": the corner nearest the sender is sharp on the first bubble of a group.
            val r = if (LocalLook.current.design == "neo" || LocalLook.current.design == "cyber") 10.dp else 16.dp
            val shape = if (m.mine) RoundedCornerShape(r, if (first) 2.dp else r, r, r) else RoundedCornerShape(if (first) 2.dp else r, r, r, r)
            Bubble(m, shape, meta = true, onLongPress = onLongPress, modifier = Modifier.padding(top = if (first && !newDay) 8.dp else 2.dp))
        }
    }
}

/** The time and ticks under a group (Calm). */
@Composable
private fun Meta(m: ChatMessage, hm: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Text(hm, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        if (m.mine) Text(
            if (m.read) " ✓✓" else " ✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = if (m.read) LocalLook.current.accent else cs.onSurfaceVariant
        )
    }
}

/**
 * A message bubble: yours on the right in the accent colour, theirs on the left in the card colour, with the [shape]
 * of the layout. [meta]: the time and ticks inside (Chat).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: ChatMessage, shape: androidx.compose.ui.graphics.Shape, meta: Boolean, onLongPress: (() -> Unit)?, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val fill = if (m.mine) look.accent else cs.surfaceVariant
    val ink = if (m.mine) look.onAccent else cs.onSurface
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        if (m.mine) Spacer(Modifier.widthIn(min = 48.dp))
        Column(
            Modifier.widthIn(max = 300.dp).lookSurface(look, shape, fill, raised = look.design != "neu" || m.mine, depth = 3.dp)
                .combinedClickable(
                    onClick = { if (m.content.kind == "wish" && m.content.url.isNotBlank()) try { uri.openUri(m.content.url) } catch (e: Exception) {} },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when {
                !m.content.readable -> Text(
                    tr("This message can’t be read on this phone"), color = ink.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                m.content.kind == "wish" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (m.content.img.isNotBlank()) AsyncImage(m.content.img, null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
                        else GiftIcon(Color(0xFF8A8A92), 26.dp)
                    }
                    Column(Modifier.padding(start = 10.dp).widthIn(max = 200.dp)) {
                        Text(m.content.title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        m.content.price?.let { Text(money(it, m.content.cur), color = ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                        if (m.content.url.isNotBlank()) Text(tr("View in shop"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                else -> Text(m.content.text, color = ink, style = MaterialTheme.typography.bodyLarge)
            }
            if (meta) Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("HH:mm", Locale.UK).format(Date(m.at)), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                if (m.mine) Text(
                    if (m.read) " ✓✓" else " ✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    color = if (m.read) Color(0xFF7FE3FF) else ink.copy(alpha = 0.7f)
                )
            }
        }
        if (!m.mine) Spacer(Modifier.widthIn(min = 48.dp))
    }
}

/** Messages ⋮ > Chat layout (and App settings): the four layouts, each with a small sample in the theme. */
@Composable
fun ChatLayoutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    ChatLayout.load(ctx)
    val now = System.currentTimeMillis()
    val sample = listOf(
        ChatMessage(1, false, ChatContent(true, "text", tr("Any ideas for Mum?")), now - 120_000, true),
        ChatMessage(2, true, ChatContent(true, "text", tr("The candle on her list!")), now - 60_000, true),
        ChatMessage(3, true, ChatContent(true, "text", tr("I’ll reserve it.")), now, false)
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Chat layout")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ChatLayout.KEYS) { k ->
                    val on = ChatLayout.current == k
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (on) cs.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { ChatLayout.set(ctx, k) }.padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = on, onClick = { ChatLayout.set(ctx, k) })
                            Column {
                                Text(ChatLayout.name(k), style = MaterialTheme.typography.titleMedium)
                                Text(ChatLayout.about(k), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        // The sample, drawn with the real layout (taps do nothing here).
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(cs.background).padding(8.dp)) {
                            sample.forEachIndexed { i, m ->
                                ChatRow(m, sample.getOrNull(i - 1), sample.getOrNull(i + 1), k, "Anna", "", onLongPress = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done")) } }
    )
}

/** The bar to type in: a gift (send a wish), the text field, and send. */
@Composable
private fun InputBar(text: String, onText: (String) -> Unit, sending: Boolean, onSend: () -> Unit, onWish: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(onClick = onWish) { GiftIcon(cs.onSurfaceVariant) }
        val shape = RoundedCornerShape(24.dp)
        TextField(
            text, onText, placeholder = { Text(tr("Message")) }, maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f).lookSurface(look, if (look.design == "cyber") look.chipShape else shape, cs.surfaceVariant, raised = false)
        )
        Box(
            Modifier.padding(start = 8.dp).size(48.dp).lookSurface(look, look.fabShape, if (text.isBlank() || sending) cs.outline else look.accent)
                .clickable(enabled = text.isNotBlank() && !sending, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = look.onAccent, strokeWidth = 2.dp)
            else SendIcon(look.onAccent)
        }
    }
}

/** Pick a wish from your own lists to send. */
@Composable
private fun WishPicker(onPick: (Wish) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lists = remember { Repo.allMine(ctx) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Send a wish")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                lists.forEach { d ->
                    val ws = d.wishes.filter { !it.priv }
                    if (ws.isNotEmpty()) {
                        item(key = "h" + d.id) {
                            Text(
                                if (d.name == "My wishlist") tr(d.name) else d.name, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(ws, key = { d.id + "/" + it.id }) { w ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(w) }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                    if (w.image.isNotBlank()) AsyncImage(w.image, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
                                    else GiftIcon(Color(0xFF8A8A92), 20.dp)
                                }
                                Text(w.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}
