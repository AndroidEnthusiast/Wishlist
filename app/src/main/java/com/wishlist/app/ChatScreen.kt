package com.wishlist.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Messages (see MessagesList.kt): one conversation. What it shows and changes is in ChatModel.kt. */

/** One conversation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreen(peer: String, snack: SnackbarHostState) {
    val places = LocalPlaces.current
    // On screen: no notification for this chat while the app is in front (ChatOnScreen, 2.194).
    DisposableEffect(peer) {
        ChatOnScreen.peer = peer
        onDispose { if (ChatOnScreen.peer == peer) ChatOnScreen.peer = null }
    }
    val go = LocalGo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val friend = Friends.state?.friends?.firstOrNull { it.id == peer }
    val convo = Chat.conversations.firstOrNull { it.id == peer }
    val name = friend?.name ?: convo?.name ?: tr("Someone")
    val canSend = friend != null
    // The chat's messages and what is open (ChatModel); the chat is keyed by the friend.
    val chat = remember(peer) { ChatModel(ctx, peer, scope, snack) }
    // Opened with "Chat about this wish": the wish is attached to what you type (once).
    LaunchedEffect(peer, places.chatAbout) { places.chatAbout?.let { chat.about = it; places.chatAbout = null } }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            ImageStore.import(ctx, uri)?.let { chat.preview = it } ?: snack.showToast(tr("Couldn’t open that photo."))
        }
    }
    val cameraFile = remember { File(File(ctx.cacheDir, "share").apply { mkdirs() }, "chat_camera.jpg") }
    // Made when the camera is opened, not when the screen is (2.183): nothing is prepared that may not be used.
    val cameraUriLazy = remember { lazy { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) } }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            ImageStore.import(ctx, cameraUriLazy.value)?.let { chat.preview = it } ?: snack.showToast(tr("Couldn’t open that photo."))
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (chat.msgs.size - 1).coerceAtLeast(0))

    // Load, then ask for new messages (and read ticks) whenever something changes in your chats (live, ROADMAP S3.2,
    // Live.kt); without the live connection every 3 seconds, as before.
    LaunchedEffect(peer) {
        var round = 0
        Live.follow(Chat.liveTopic(ctx), 3000) { live ->
            chat.refresh(live, round)
            round++
        }
    }
    LaunchedEffect(chat.msgs) { chat.keep() }
    LaunchedEffect(chat.msgs.size) {
        if (chat.msgs.isEmpty()) return@LaunchedEffect
        if (!chat.placed) { listState.scrollToItem(chat.msgs.size - 1); chat.placed = true }
        else listState.animateScrollToItem(chat.msgs.size - 1)
    }

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
    val newestTheirs = chat.msgs.lastOrNull { !it.mine }?.id ?: 0L
    LaunchedEffect(resumed, atEnd, newestTheirs) {
        if (!resumed || !atEnd || newestTheirs <= chat.seenUpTo) return@LaunchedEffect
        delay(400) // really looked, not just passing through
        chat.markSeen(newestTheirs)
    }


    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                large = false,
                title = {
                    // Tap the picture or name: their page (owner, 2.102), only while you are friends.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(
                            if (friend != null) Modifier.clickable(onClickLabel = tr("Open profile")) {
                                go(Deep.Person(peer, fromChat = true))
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
                navigationIcon = { IconButton(onClick = { places.chat = null }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                // Report and block (Safety.kt, ROADMAP S4.8; owner: in a chat's menu).
                actions = {
                    Box {
                        IconButton(onClick = { chat.chatMenu = true }) { Icon(AppIcons.MoreVert, tr("More")) }
                        DropdownMenu(expanded = chat.chatMenu, onDismissRequest = { chat.chatMenu = false }) {
                            DropdownMenuItem(text = { Text(tr("Report")) }, onClick = { chat.chatMenu = false; chat.reporting = true })
                            DropdownMenuItem(text = { Text(tr("Block"), color = cs.error) }, onClick = { chat.chatMenu = false; chat.blocking = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (chat.reporting) ReportDialog(peer, name, snack) { chat.reporting = false }
        if (chat.blocking) BlockDialog(peer, name, snack, onDone = { chat.blocking = false; places.chat = null }, onClose = { chat.blocking = false })
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (chat.loading && chat.msgs.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (chat.msgs.isEmpty()) Text(
                    tr("The beginning of your conversation with %s", name),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize().alpha(if (chat.placed) 1f else 0f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    items(chat.msgs.size, key = { chat.msgs[it].id }) { i ->
                        val m = chat.msgs[i]
                        val r = chat.reacts[m.id]
                        ChatRow(m.copy(react = r?.first.orEmpty(), reactTheirs = r?.second.orEmpty()), chat.msgs.getOrNull(i - 1), name,
                            onLongPress = { chat.menuFor = m },
                            onReply = if (canSend) ({ chat.replying = m }) else null,
                            onOpenPhoto = { chat.viewing = it })
                    }
                }
            }
            // "Replying to Sam" with the quoted words and ✕, above the text box (owner, 2.128).
            chat.replying?.let { r ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp).lookSurface(look, MaterialTheme.shapes.small, cs.surfaceVariant, raised = false)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(
                            if (r.mine) tr("Replying to yourself") else tr("Replying to %s", name),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = look.textAccent
                        )
                        Text(Chat.preview(r.content), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { chat.replying = null }) { Icon(AppIcons.Close, tr("Cancel reply")) }
                }
            }
            // "Chat about this wish" (owner, 20 November 2026): the wish above the text box, ✕ takes it off.
            if (canSend) chat.about?.let { a ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp).lookSurface(look, MaterialTheme.shapes.small, cs.surfaceVariant, raised = false)
                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { ChatWishRow(a.title, a.price, a.cur, a.img, cs.onSurface, hint = tr("Sent with your message")) }
                    IconButton(onClick = { chat.about = null }) { Icon(AppIcons.Close, tr("Remove the wish")) }
                }
            }
            if (canSend) InputBar(
                chat.text, { chat.text = it }, chat.sending, attached = chat.about != null,
                onSend = { chat.sendText() },
                onWish = { chat.wishPick = true },
                onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onCamera = { try { takePhoto.launch(cameraUriLazy.value) } catch (e: Exception) { } }
            ) else Text(
                tr("You’re no longer friends, so you can’t send messages here."),
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            )
        }
    }

    // Long press (owner, 2.128): five reactions, Reply, and Delete for your own messages.
    chat.menuFor?.let { m ->
        val mineNow = chat.reacts[m.id]?.first.orEmpty()
        AlertDialog(
            onDismissRequest = { chat.menuFor = null },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("❤️", "👍", "😂", "😮", "🎉").forEach { e ->
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (e == mineNow) look.accent.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable(onClickLabel = tr("React with %s", e)) { chat.menuFor = null; chat.react(m, e) },
                                contentAlignment = Alignment.Center
                            ) { Text(e, fontSize = 26.sp) }
                        }
                    }
                    if (canSend) TextButton(onClick = { chat.menuFor = null; chat.replying = m }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Reply")) }
                    if (m.mine) TextButton(danger = true, onClick = { chat.menuFor = null; chat.deleting = m }) { Text(tr("Delete")) }
                }
            },
            confirmButton = { TextButton(onClick = { chat.menuFor = null }) { Text(tr("Close")) } }
        )
    }
    chat.deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { chat.deleting = null },
            title = { Text(tr("Delete message?")) },
            text = { Text(tr("It is deleted for both of you.")) },
            confirmButton = {
                TextButton(danger = true, onClick = {
                    chat.deleting = null
                    chat.delete(m)
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { chat.deleting = null }) { Text(tr("Cancel")) } }
        )
    }
    if (chat.wishPick) WishPicker(onPick = { w, code ->
        chat.wishPick = false
        chat.sendWish(w, code)
    }, onClose = { chat.wishPick = false })
    chat.preview?.let { img ->
        PhotoPreview(img, onCancel = { chat.preview = null; ImageStore.delete(ctx, img) }, onSend = { chat.preview = null; chat.sendPhoto(img) })
    }
    chat.viewing?.let { f -> PhotoViewer(f, onClose = { chat.viewing = null }) }
}
