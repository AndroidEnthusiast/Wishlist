package com.wishlist.app

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
    // Two tabs (owner, 2.101): "Chats" (the conversations) and "Friends" (tap one: the chat opens). No + in the top
    // bar: the bottom bar's + adds a wish, two pluses were confusing.
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Friends.load(ctx)
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
                actions = { ProfileButton(onProfile) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        val list = Chat.conversations
        val friends = Friends.state?.friends.orEmpty()
        Column(Modifier.padding(pad).fillMaxSize().lookBackdrop(look)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(tr("Chats"), tr("Friends")).forEachIndexed { i, label ->
                    val on = tab == i
                    Box(
                        Modifier.weight(1f).lookChip(look, on).clickable { tab = i }
                            .semantics { selected = on }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = look.chipContent(on))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 1) {
                    if (friends.isEmpty()) Empty(tr("Add friends to start chatting."))
                    else LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(friends, key = { it.id }) { f ->
                            val id = f.id.hashCode().toLong()
                            Row(
                                Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                                    .clickable { ChatNav.peer = f.id }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(Friends.photos[f.id], f.name, 44.dp)
                                Text(
                                    f.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp)
                                )
                                ChatIcon(look.accent, 22.dp)
                            }
                        }
                    }
                } else when {
                    !Config.enabled || !Friends.hasProfile(ctx) && list.isEmpty() -> Empty(
                        tr("Add friends to start chatting.")
                    )
                    list.isEmpty() && !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    list.isEmpty() && problem -> Empty(tr("Couldn’t reach the server. Check your connection."))
                    // No conversations yet: a "Start a chat" chip that shows your friends (it goes once there is a chat).
                    list.isEmpty() -> Empty(tr("No messages yet.")) {
                        Text(
                            tr("Start a chat"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = look.chipContent(true),
                            modifier = Modifier.padding(top = 16.dp).lookChip(look, on = true).clickable { tab = 1 }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
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
    }
}

@Composable
private fun Empty(text: String, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ChatIcon(LocalLook.current.accent, 56.dp)
        Text(
            text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        extra()
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
    // Reactions and replies (owner, 2.128): the long-press menu, the message being answered, and the emoji per message.
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var replying by remember(peer) { mutableStateOf<ChatMessage?>(null) }
    var reacts by remember(peer) { mutableStateOf<Map<Long, Pair<String, String>>>(emptyMap()) }
    // Photos (2.109): picked or taken, then shown full screen with Send (owner: a preview first); [viewing] is a
    // photo opened from the chat.
    var preview by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<File?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            ImageStore.import(ctx, uri)?.let { preview = it } ?: snack.showToast(tr("Couldn’t open that photo."))
        }
    }
    val cameraFile = remember { File(File(ctx.cacheDir, "share").apply { mkdirs() }, "chat_camera.jpg") }
    val cameraUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            ImageStore.import(ctx, cameraUri)?.let { preview = it } ?: snack.showToast(tr("Couldn’t open that photo."))
        }
    }
    val listState = rememberLazyListState()

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
            // Reactions can change on any message, so they are fetched for the whole chat (every other round).
            if (round % 2 == 0) try { reacts = Chat.reactions(ctx, peer) } catch (e: Exception) {}
            loading = false
            round++
            delay(3000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    // "Read" (owner, 2.105): only once you have really seen their newest message: the app is in front, this chat is
    // open and the end of the conversation is on screen. Opening the chat from the notification does the same.
    val lifeOwner = ctx as? LifecycleOwner
    var resumed by remember {
        mutableStateOf(lifeOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true)
    }
    DisposableEffect(lifeOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            else if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifeOwner?.lifecycle?.addObserver(obs)
        onDispose { lifeOwner?.lifecycle?.removeObserver(obs) }
    }
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            info.totalItemsCount == 0 || (last != null && last.index >= info.totalItemsCount - 1)
        }
    }
    var seenUpTo by remember(peer) { mutableStateOf(0L) }
    val newestTheirs = msgs.lastOrNull { !it.mine }?.id ?: 0L
    LaunchedEffect(resumed, atEnd, newestTheirs) {
        if (!resumed || !atEnd || newestTheirs <= seenUpTo) return@LaunchedEffect
        delay(400) // really looked, not just passing through
        try {
            Chat.seen(ctx, peer, newestTheirs)
            seenUpTo = newestTheirs
        } catch (e: Exception) {
        }
    }

    fun sendPhoto(image: String) {
        sending = true
        scope.launch {
            try {
                val content = ChatPhotos.upload(ctx, peer, image)
                val m = Chat.send(ctx, peer, content)
                msgs = (msgs + m).distinctBy { it.id }
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t send the photo. Check your connection."))
            }
            ImageStore.delete(ctx, image)
            sending = false
        }
    }

    fun react(m: ChatMessage, emoji: String) {
        val before = reacts
        val now = reacts[m.id] ?: ("" to "")
        // Tapping the emoji you already chose takes it off.
        val mine = if (now.first == emoji) "" else emoji
        reacts = reacts + (m.id to (mine to now.second))
        scope.launch {
            try {
                Chat.react(ctx, peer, m.id, mine)
            } catch (e: Exception) {
                reacts = before
                snack.showToast(
                    if ((e.message ?: "").contains("wl_chat_react")) tr("Reactions need an update in Supabase first.")
                    else tr("Couldn’t react. Check your connection.")
                )
            }
        }
    }

    fun send(content: ChatContent) {
        sending = true
        scope.launch {
            try {
                val m = Chat.send(ctx, peer, content)
                msgs = (msgs + m).distinctBy { it.id }
                if (content.kind == "text") { text = ""; replying = null }
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
                    // Tap the picture or name: their page (owner, 2.102), only while you are friends.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(
                            if (friend != null) Modifier.clickable(onClickLabel = tr("Open profile")) {
                                FriendsNav.reset(); FriendsNav.person = peer; FriendsNav.fromChat = peer; FriendsNav.openTab = true
                            } else Modifier
                        ).padding(end = 8.dp)
                    ) {
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
                    tr("The beginning of your conversation with %s", name),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    items(msgs.size, key = { msgs[it].id }) { i ->
                        val m = msgs[i]
                        val r = reacts[m.id]
                        ChatRow(m.copy(react = r?.first.orEmpty(), reactTheirs = r?.second.orEmpty()), msgs.getOrNull(i - 1), name,
                            onLongPress = { menuFor = m },
                            onReply = if (canSend) ({ replying = m }) else null,
                            onOpenPhoto = { viewing = it })
                    }
                }
            }
            // "Replying to Sam" with the quoted words and ✕, above the text box (owner, 2.128).
            replying?.let { r ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp).background(cs.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(
                            if (r.mine) tr("Replying to yourself") else tr("Replying to %s", name),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = look.accent
                        )
                        Text(Chat.preview(r.content), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { replying = null }) { Icon(AppIcons.Close, tr("Cancel reply")) }
                }
            }
            if (canSend) InputBar(
                text, { text = it }, sending,
                onSend = {
                    if (text.isNotBlank()) {
                        val r = replying
                        send(
                            if (r == null) ChatContent(true, "text", text.trim())
                            else ChatContent(
                                true, "text", text.trim(), replyTo = r.id, replyText = Chat.preview(r.content),
                                replyBy = if (r.mine) Chat.me(ctx) else peer
                            )
                        )
                    }
                },
                onWish = { wishPick = true },
                onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onCamera = { try { takePhoto.launch(cameraUri) } catch (e: Exception) { } }
            ) else Text(
                tr("You’re no longer friends, so you can’t send messages here."),
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            )
        }
    }

    // Long press (owner, 2.128): five reactions, Reply, and Delete for your own messages.
    menuFor?.let { m ->
        val mineNow = reacts[m.id]?.first.orEmpty()
        AlertDialog(
            onDismissRequest = { menuFor = null },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("❤️", "👍", "😂", "😮", "🎉").forEach { e ->
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (e == mineNow) look.accent.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable(onClickLabel = tr("React with %s", e)) { menuFor = null; react(m, e) },
                                contentAlignment = Alignment.Center
                            ) { Text(e, fontSize = 26.sp) }
                        }
                    }
                    if (canSend) TextButton(onClick = { menuFor = null; replying = m }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Reply")) }
                    if (m.mine) TextButton(onClick = { menuFor = null; deleting = m }) { Text(tr("Delete"), color = cs.error) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text(tr("Close")) } }
        )
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
                            ChatPhotos.forget(ctx, m.content)
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
    preview?.let { img ->
        PhotoPreview(img, onCancel = { preview = null; ImageStore.delete(ctx, img) }, onSend = { preview = null; sendPhoto(img) })
    }
    viewing?.let { f -> PhotoViewer(f, onClose = { viewing = null }) }
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
 * One chat style (owner, 2.101: the four layouts of 2.99 looked almost the same and were removed, to be fleshed out
 * later): a small tail on the corner, time and ticks inside the bubble, a date chip per day, in the theme's colours.
 * A group: messages from the same person within five minutes of each other.
 */
private fun sameGroup(a: ChatMessage?, b: ChatMessage?) =
    a != null && b != null && a.mine == b.mine && kotlin.math.abs(a.at - b.at) < 5 * 60_000L && dayLabel(a.at) == dayLabel(b.at)

/** One message, with the date chip above it when a new day starts. */
@Composable
private fun ChatRow(
    m: ChatMessage, prev: ChatMessage?, peerName: String, onLongPress: (() -> Unit)?, onReply: (() -> Unit)?,
    onOpenPhoto: (File) -> Unit
) {
    val first = !sameGroup(prev, m)
    val newDay = prev == null || dayLabel(prev.at) != dayLabel(m.at)
    if (newDay) DayChip(dayLabel(m.at))
    // A small "tail": the corner nearest the sender is sharp on the first bubble of a group.
    val r = if (LocalLook.current.design == "neo" || LocalLook.current.design == "cyber") 10.dp else 16.dp
    val shape = if (m.mine) RoundedCornerShape(r, if (first) 2.dp else r, r, r) else RoundedCornerShape(if (first) 2.dp else r, r, r, r)
    // Swipe a message to the right to reply to it (owner, 2.128); it springs back.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val limit = with(density) { 72.dp.toPx() }
    val drag = remember(m.id) { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().then(
            if (onReply != null) Modifier.pointerInput(m.id) {
                androidx.compose.foundation.gestures.detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag.value > limit * 0.8f) onReply()
                        scope.launch { drag.animateTo(0f) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } }
                ) { change, d ->
                    change.consume()
                    scope.launch { drag.snapTo((drag.value + d).coerceIn(0f, limit)) }
                }
            } else Modifier
        ).offset { androidx.compose.ui.unit.IntOffset(drag.value.toInt(), 0) }
    ) {
        Bubble(m, shape, peerName, onLongPress = onLongPress, onOpenPhoto = onOpenPhoto, modifier = Modifier.padding(top = if (first && !newDay) 8.dp else 2.dp))
        // The reactions under the bubble, on its side.
        val emojis = listOf(m.reactTheirs, m.react).filter { it.isNotEmpty() }
        if (emojis.isNotEmpty()) Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
        ) {
            Text(
                emojis.joinToString(" "), fontSize = 16.sp,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * A message bubble: yours on the right in the accent colour, theirs on the left in the card colour, with the time and
 * ticks inside.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: ChatMessage, shape: androidx.compose.ui.graphics.Shape, peerName: String, onLongPress: (() -> Unit)?, onOpenPhoto: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    // A photo: this phone's copy, fetched and unlocked the first time (null while loading or when it is gone).
    var photo by remember(m.id) { mutableStateOf<File?>(null) }
    var photoState by remember(m.id) { mutableStateOf("loading") } // loading | ok | gone | offline
    var photoTry by remember(m.id) { mutableStateOf(0) }
    if (m.content.readable && m.content.kind == "photo") LaunchedEffect(m.id, photoTry) {
        photoState = "loading"
        photoState = try {
            ChatPhotos.load(ctx, m.content)?.let { photo = it; "ok" } ?: "gone"
        } catch (e: Exception) {
            "offline"
        }
    }
    val fill = if (m.mine) look.accent else cs.surfaceVariant
    val ink = if (m.mine) look.onAccent else cs.onSurface
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        if (m.mine) Spacer(Modifier.widthIn(min = 48.dp))
        Column(
            Modifier.widthIn(max = 300.dp).lookSurface(look, shape, fill, raised = look.design != "neu" || m.mine, depth = 3.dp)
                .combinedClickable(
                    onClick = {
                        if (m.content.kind == "wish" && m.content.url.isNotBlank()) try { uri.openUri(m.content.url) } catch (e: Exception) {}
                        // A group invitation (2.108): the join screen, or the group when you are in it already.
                        if (m.content.kind == "group") GroupNav.openInvite(m.content.text, Groups.parseLink(m.content.url))
                        if (m.content.kind == "photo") {
                            if (photoState == "ok") photo?.let(onOpenPhoto)
                            else if (photoState == "offline") photoTry++
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // The message this one answers: who wrote it and its first words, with a bar on the left.
            if (m.content.replyTo > 0L) Row(
                Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.12f)).height(IntrinsicSize.Min)
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(ink.copy(alpha = 0.6f)))
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        if (m.content.replyBy == Chat.me(ctx)) tr("You") else peerName,
                        color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                    )
                    Text(m.content.replyText, color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
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
                        if (m.content.img.isNotBlank()) WishImage(m.content.img, Modifier.fillMaxSize().padding(3.dp))
                        else GiftIcon(Color(0xFF8A8A92), 26.dp)
                    }
                    Column(Modifier.padding(start = 10.dp).widthIn(max = 200.dp)) {
                        Text(m.content.title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        m.content.price?.let { Text(money(it, m.content.cur), color = ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                        if (m.content.url.isNotBlank()) Text(tr("View in shop"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                m.content.kind == "group" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Group, null, tint = Color(0xFF8A8A92), modifier = Modifier.size(28.dp)) }
                    Column(Modifier.padding(start = 10.dp).widthIn(max = 200.dp)) {
                        Text(tr("Join my wishlist group"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(m.content.title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (m.mine) tr("Invitation sent") else tr("Tap to join"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                m.content.kind == "photo" -> {
                    // The photo in its own shape (wide or tall), at most 240 dp wide.
                    val ratio = if (m.content.w > 0 && m.content.h > 0) (m.content.w.toFloat() / m.content.h).coerceIn(0.6f, 1.8f) else 1f
                    Box(
                        Modifier.width(240.dp).aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (photoState) {
                            "ok" -> AsyncImage(
                                model = photo, contentDescription = tr("Photo"), contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            "loading" -> CircularProgressIndicator(Modifier.size(24.dp), color = ink, strokeWidth = 2.dp)
                            else -> Text(
                                if (photoState == "gone") tr("This photo is no longer available") else tr("Couldn’t load the photo. Tap to try again."),
                                color = ink.copy(alpha = 0.8f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                else -> Text(m.content.text, color = ink, style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("HH:mm", Locale.UK).format(Date(m.at)), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                // The two read ticks sit close together, overlapping a little (owner, 2.102).
                if (m.mine) Text(
                    if (m.read) "✓✓" else "✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    letterSpacing = (-4).sp, color = if (m.read) Color(0xFF7FE3FF) else ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, end = if (m.read) 4.dp else 0.dp)
                )
            }
        }
        if (!m.mine) Spacer(Modifier.widthIn(min = 48.dp))
    }
}

/** The bar to type in: a gift (send a wish), a camera (send a photo), the text field, and send. */
@Composable
private fun InputBar(
    text: String, onText: (String) -> Unit, sending: Boolean, onSend: () -> Unit, onWish: () -> Unit,
    onGallery: () -> Unit, onCamera: () -> Unit
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // The gift and photo buttons each sit in a box exactly as tall as a one-line text box, so their middles line up
        // with it and with each other in every style (owner, 2.128: they looked misaligned); bottom-aligned when the
        // text box grows.
        Box(Modifier.size(44.dp, 56.dp).clip(CircleShape).clickable(onClickLabel = tr("Send a wish"), onClick = onWish), contentAlignment = Alignment.Center) {
            GiftIcon(cs.onSurfaceVariant)
        }
        var photoMenu by remember { mutableStateOf(false) }
        Box(Modifier.size(44.dp, 56.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !sending, onClickLabel = tr("Send a photo")) { photoMenu = true },
                contentAlignment = Alignment.Center
            ) { PhotoIcon(cs.onSurfaceVariant) }
            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                DropdownMenuItem(text = { Text(tr("Take a photo")) }, onClick = { photoMenu = false; onCamera() })
                DropdownMenuItem(text = { Text(tr("Choose from gallery")) }, onClick = { photoMenu = false; onGallery() })
            }
        }
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
            Modifier.padding(start = 8.dp, bottom = 4.dp).size(48.dp).lookSurface(look, look.fabShape, if (text.isBlank() || sending) cs.outline else look.accent)
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
                                    if (w.image.isNotBlank()) WishImage(w.image, Modifier.fillMaxSize().padding(2.dp))
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

/** The photo you are about to send, full screen on black, with Cancel and Send (owner, 2.109: a preview first). */
@Composable
private fun PhotoPreview(image: String, onCancel: () -> Unit, onSend: () -> Unit) {
    val look = LocalLook.current
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(model = image, contentDescription = tr("Photo"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text(tr("Cancel"), color = Color.White) }
                Row(
                    Modifier.lookSurface(look, look.fabShape, look.accent).clickable(onClick = onSend).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("Send"), color = look.onAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    SendIcon(look.onAccent)
                }
            }
        }
    }
}

/** A photo from the chat, full screen on black: pinch to zoom, drag to move, double tap to reset; Back closes it. */
@Composable
private fun PhotoViewer(file: File, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = file, contentDescription = tr("Photo"), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            )
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
                Icon(AppIcons.Close, tr("Close"), tint = Color.White)
            }
        }
    }
}
