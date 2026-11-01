package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where you are inside the Friends tab: a friend's page, and one of their lists. */
object FriendsNav {
    var person by mutableStateOf<String?>(null)
    var list by mutableStateOf<FriendList?>(null)

    /** Set when a friend request alert is tapped: MainActivity opens the Friends tab. */
    var openTab by mutableStateOf(false)

    /** On a friend's page or list: the screen has a back arrow instead of the menu button. */
    val deep: Boolean get() = person != null

    fun reset() { person = null; list = null }

    /** Opens the list with [code] if it belongs to a friend (for example from a "gift no longer on the list" alert). */
    fun openByCode(code: String): Boolean {
        val f = Friends.state?.friends?.firstOrNull { p -> p.lists.any { it.code == code } } ?: return false
        person = f.id
        list = f.lists.first { it.code == code }
        return true
    }
}

/** A round picture of a person: their photo, or their first letter on the theme's colour. */
@Composable
fun Avatar(photo: String?, name: String, size: Dp) {
    val look = LocalLook.current
    if (!photo.isNullOrEmpty()) {
        AsyncImage(
            model = photo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(look.accent), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "🙂" }, color = look.onAccent,
                fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Your profile picture at the top right of Home, Prices and Friends: opens your profile. */
@Composable
fun ProfileButton(onProfile: () -> Unit) {
    val ctx = LocalContext.current
    Friends.load(ctx)
    IconButton(onClick = onProfile) { Avatar(Friends.myPhoto, Shared.lastName, 32.dp) }
}

@Composable
private fun FriendsTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleMedium, fontWeight = LocalLook.current.titleWeight,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun countText(n: Int): String = when (n) {
    0 -> tr("No wishes yet")
    1 -> tr("1 wish")
    else -> tr("%d wishes", n)
}

/**
 * The Friends tab: your code and "Add a friend", requests waiting for your approval, requests you sent, your friends
 * (tap: their wishlists; long press or ⋮: hide your lists from them, or unfollow) and lists you opened from a link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(snack: SnackbarHostState, onProfile: () -> Unit, onOpenLink: (String) -> Unit) {
    val ctx = LocalContext.current
    Friends.load(ctx)
    val person = FriendsNav.person?.let { id -> Friends.state?.friends?.firstOrNull { it.id == id } }
    val list = FriendsNav.list
    if (FriendsNav.person != null && person == null && Friends.state != null) {
        // No longer friends (unfollowed, here or by them).
        LaunchedEffect(Unit) { FriendsNav.reset() }
    }
    if (person != null && list != null) {
        BackHandler { FriendsNav.list = null }
        FriendScreen(
            Friend(list.code, list.name), snack, onMenu = {}, onRemoved = {},
            fetch = { Friends.fetchList(ctx, person.id, list.code) },
            onBack = { FriendsNav.list = null }
        )
        return
    }
    if (person != null) {
        BackHandler { FriendsNav.reset() }
        FriendPage(person, snack)
        return
    }
    FriendsHome(snack, onProfile, onOpenLink)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsHome(snack: SnackbarHostState, onProfile: () -> Unit, onOpenLink: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var problem by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var unfollow by remember { mutableStateOf<Person?>(null) }

    // Load now, then every 30 seconds while the tab is open.
    LaunchedEffect(tick) {
        while (true) {
            problem = try {
                Friends.refresh(ctx)
                false
            } catch (e: Exception) {
                true
            }
            delay(30000)
        }
    }

    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                snack.showToast(done)
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t reach the server. Check your connection."))
            }
        }
    }

    val s = Friends.state
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Friends"), fontWeight = LocalLook.current.titleWeight) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = { ProfileButton(onProfile) }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (!Config.enabled) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(tr("Online sharing isn’t set up in this build yet."), color = cs.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (Shared.lastName.isBlank()) item {
                Notice(tr("Add your name and a photo to your profile, so friends know it’s you."), tr("Go to profile"), onProfile)
            }
            item { CodeCard(s?.code.orEmpty(), problem && s == null, onAdd = { adding = true }, onRetry = { tick++ }) }
            if (problem && s != null) item {
                Text(tr("Couldn’t reach the server. Check your connection."), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (s != null && s.incoming.isNotEmpty()) {
                item { FriendsTitle(tr("Waiting for your approval")) }
                items(s.incoming, key = { "in" + it.id }) { r ->
                    PersonRow(r, tr("wants to be your friend")) {
                        TextButton(onClick = { act(tr("Request declined")) { Friends.answer(ctx, r.id, false) } }) { Text(tr("Decline")) }
                        Button(onClick = {
                            act(tr("You and %s are friends now", r.name)) { Friends.answer(ctx, r.id, true) }
                        }) { Text(tr("Approve")) }
                    }
                }
            }
            if (s != null && s.outgoing.isNotEmpty()) {
                item { FriendsTitle(tr("Sent requests")) }
                items(s.outgoing, key = { "out" + it.id }) { r ->
                    PersonRow(r, tr("Waiting for approval")) {
                        TextButton(onClick = { act(tr("Request withdrawn")) { Friends.remove(ctx, r.id) } }) { Text(tr("Withdraw")) }
                    }
                }
            }
            if (s != null) {
                item { FriendsTitle(tr("Your friends")) }
                if (s.friends.isEmpty()) item {
                    Text(
                        tr("No friends yet. Share your friend code, or add a friend with theirs."),
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                items(s.friends, key = { "f" + it.id }) { f ->
                    FriendRow(
                        f,
                        onOpen = { FriendsNav.person = f.id },
                        onHide = {
                            act(if (f.hidden) tr("%s can see your lists again", f.name) else tr("Your lists are hidden from %s", f.name)) {
                                Friends.hide(ctx, f.id, !f.hidden)
                            }
                        },
                        onUnfollow = { unfollow = f }
                    )
                }
            } else if (!problem) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            // Lists you opened from a friends' link (before Friends existed, or from people without a profile).
            if (Repo.friends.isNotEmpty()) {
                item { FriendsTitle(tr("Lists from links")) }
                items(Repo.friends, key = { "l" + it.code }) { l ->
                    val look = LocalLook.current
                    val id = l.code.hashCode().toLong()
                    Row(
                        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                            .clickable { onOpenLink(l.code) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎁", fontSize = 22.sp)
                        Text(
                            l.name, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (adding) AddFriendDialog { msg ->
        adding = false
        if (msg != null) scope.launch { snack.showToast(msg) }
    }
    unfollow?.let { f ->
        AlertDialog(
            onDismissRequest = { unfollow = null },
            title = { Text(tr("Unfollow %s?", f.name)) },
            text = {
                Text(tr("You’ll no longer be friends: you won’t see each other’s wishlists, and you’ll leave each other’s Friends. They aren’t told. You can add each other again with a friend code."))
            },
            confirmButton = {
                TextButton(onClick = {
                    unfollow = null
                    act(tr("You unfollowed %s", f.name)) { Friends.remove(ctx, f.id) }
                }) { Text(tr("Unfollow"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { unfollow = null }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
private fun Notice(text: String, button: String, onClick: () -> Unit) {
    val look = LocalLook.current
    val id = 7L
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).lookCard(look, id, look.cardShape(id, small = true)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text, color = look.cardInk(id), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(button) }
    }
}

/** Your friend code, with Share and Add a friend. */
@Composable
private fun CodeCard(code: String, offline: Boolean, onAdd: () -> Unit, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val id = 3L
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).lookCard(look, id, look.cardShape(id, small = false)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(tr("Your friend code"), style = MaterialTheme.typography.labelLarge, color = look.cardMuted(id))
        when {
            code.isNotEmpty() -> Text(
                Friends.pretty(code), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = look.cardInk(id), letterSpacing = 2.sp
            )
            offline -> Text(tr("Couldn’t reach the server. Check your connection."), color = look.cardMuted(id))
            else -> CircularProgressIndicator(Modifier.size(28.dp))
        }
        Text(
            tr("Give it to people you want as friends. When they enter it, you approve them first."),
            style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (offline) Button(onClick = onRetry) { Text(tr("Try again")) }
            else Button(onClick = { Friends.shareCode(ctx, code) }, enabled = code.isNotEmpty()) { Text(tr("Share code")) }
            OutlinedButton(onClick = onAdd) { Text(tr("Add a friend")) }
        }
    }
}

/** Someone in a request: picture, name, a line, and the buttons. */
@Composable
private fun PersonRow(p: Person, line: String, buttons: @Composable () -> Unit) {
    val look = LocalLook.current
    val id = p.id.hashCode().toLong()
    Column(Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(Friends.photos[p.id], p.name, 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(p.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { buttons() }
    }
}

/** A friend: tap opens their wishlists; long press or ⋮ opens a small menu (Hide my lists / Unfollow). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendRow(f: Person, onOpen: () -> Unit, onHide: () -> Unit, onUnfollow: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val id = f.id.hashCode().toLong()
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(Friends.photos[f.id], f.name, 52.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(f.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (f.lists.size) {
                        0 -> tr("No wishlists to see yet")
                        1 -> tr("1 wishlist")
                        else -> tr("%d wishlists", f.lists.size)
                    },
                    style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
                )
                if (f.hidden) Text(tr("Your lists are hidden from them"), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
            }
            IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More"), tint = look.cardMuted(id)) }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (f.hidden) tr("Show my lists to %s", f.name) else tr("Hide my lists from %s", f.name)) },
                    onClick = { menu = false; onHide() }
                )
                DropdownMenuItem(text = { Text(tr("Unfollow"), color = cs.error) }, onClick = { menu = false; onUnfollow() })
            }
        }
    }
}

/** A friend's page: their picture and name, and the wishlists they let friends see. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendPage(f: Person, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(f.name, fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { FriendsNav.reset() }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(Friends.photos[f.id], f.name, 96.dp)
                    Text(f.name, style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (f.lists.isEmpty()) item {
                Text(
                    tr("%s hasn’t made a wishlist visible to friends yet.", f.name),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
            items(f.lists, key = { it.code }) { l ->
                val id = l.code.hashCode().toLong()
                Row(
                    Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                        .clickable { FriendsNav.list = l }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎁", fontSize = 26.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(l.name, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(countText(l.count), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
                    }
                }
            }
            if (f.hidden) item {
                Text(
                    tr("Your lists are hidden from %s. Change this with ⋮ on Friends.", f.name),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Enter a friend code. Asks for your name first when you haven't got one yet (they need to know who's asking).
 * [onClose] gets the message to show (the screen behind shows it: this dialog is gone by then).
 */
@Composable
fun AddFriendDialog(onClose: (String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val needName = remember { Shared.lastName.isBlank() }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!needName) try { focus.requestFocus() } catch (e: Exception) {} }

    fun send() {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        if (needName && name.isBlank()) { error = tr("What’s your name?"); return }
        if (clean.length != 6) { error = tr("A friend code has 6 letters and numbers."); return }
        busy = true
        scope.launch {
            try {
                if (needName) {
                    Shared.setAppName(ctx, name)
                    Friends.nameChanged(ctx)
                }
                val (status, who) = Friends.add(ctx, clean)
                val n = who.ifBlank { tr("them") }
                busy = false
                when (status) {
                    "sent" -> onClose(tr("Request sent to %s. They need to approve it.", n))
                    "friends" -> onClose(tr("You and %s are friends now", n))
                    "already" -> onClose(tr("You’re already friends with %s, or your request is still waiting.", n))
                    "self" -> error = tr("That’s your own code.")
                    else -> error = tr("No one has that code. Check it and try again.")
                }
            } catch (e: Exception) {
                busy = false
                error = tr("Couldn’t reach the server. Check your connection.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onClose(null) },
        title = { Text(tr("Add a friend")) },
        text = {
            val err = error
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Enter your friend’s code. They approve your request, then you see each other’s wishlists that are visible to friends."), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (needName) OutlinedTextField(
                    name, { name = it.take(30); error = null }, singleLine = true, label = { Text(tr("Your name")) },
                    keyboardOptions = Capitalised
                )
                OutlinedTextField(
                    code, { code = it.take(9); error = null }, singleLine = true, label = { Text(tr("Friend code")) },
                    isError = error != null,
                    supportingText = if (err != null) {
                        { Text(err) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy) send() }),
                    modifier = Modifier.focusRequester(focus)
                )
            }
        },
        confirmButton = { TextButton(onClick = { send() }, enabled = !busy) { Text(tr("Send request")) } },
        dismissButton = { TextButton(onClick = { onClose(null) }) { Text(tr("Cancel")) } }
    )
}

/**
 * Your profile (menu > Profile, or your picture on Friends): photo, name and friend code. The name is the one the app
 * uses everywhere (also "Added by" on group wishlists); it used to be in App settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val clip = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Friends.load(ctx)
    var me by remember { mutableStateOf(Shared.lastName) }
    var nameSaved by remember { mutableIntStateOf(0) }
    var photoMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var newCodeDlg by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!Config.enabled) return@LaunchedEffect
        problem = try {
            Friends.refresh(ctx)
            false
        } catch (e: Exception) {
            true
        }
    }

    // A new photo (chosen or taken) goes to the editor first, with a round frame, like the cover photo on Home.
    var pending by remember { mutableStateOf<String?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "profile_camera.jpg") }
    val cameraUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { ImageStore.import(ctx, cameraUri)?.let { pending = it } }
    }
    fun save(src: String, e: HeroEdit) {
        busy = true
        scope.launch {
            val ok = Friends.setPhoto(ctx, src, e)
            busy = false
            snack.showToast(if (ok) tr("Photo saved") else tr("Couldn’t save the photo online. Check your connection."))
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Profile"), fontWeight = look.titleWeight) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The photo, with a pencil: choose a photo, or remove it.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box {
                    Box(Modifier.clip(CircleShape).clickable { photoMenu = true }) { Avatar(Friends.myPhoto, me, 120.dp) }
                    if (busy) CircularProgressIndicator(Modifier.size(120.dp))
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).size(36.dp)
                            .lookChip(look, on = true).clickable { photoMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Edit, tr("Change photo"), tint = look.chipContent(true), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Choose a photo")) }, onClick = {
                            photoMenu = false
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text(tr("Take a photo")) }, onClick = {
                            photoMenu = false
                            try {
                                camera.launch(cameraUri)
                            } catch (e: Exception) {
                            }
                        })
                        if (Friends.photoSrc.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Edit profile photo")) }, onClick = {
                            photoMenu = false
                            adjusting = true
                        })
                        if (Friends.myPhoto.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Remove photo")) }, onClick = {
                            photoMenu = false
                            scope.launch {
                                try {
                                    Friends.removePhoto(ctx)
                                    snack.showToast(tr("Photo removed"))
                                } catch (e: Exception) {
                                    snack.showToast(tr("Couldn’t reach the server. Check your connection."))
                                }
                            }
                        })
                    }
                }
            }

            ProfileTitle(tr("Your name"))
            // No Save button: the keyboard's tick saves the name (as it did in App settings).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    me, { me = it.take(30) }, singleLine = true, label = { Text(tr("Name")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = {
                        if (me.isNotBlank()) {
                            Shared.setAppName(ctx, me)
                            nameSaved++
                            scope.launch { try { Friends.nameChanged(ctx) } catch (e: Exception) {} }
                        }
                        me = Shared.lastName
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.weight(1f)
                )
                SavedFeedback(nameSaved)
            }
            Text(
                tr("Friends see your name and photo. Your name is also shown as “Added by” on wishlists for someone (you can use a different name on one list in its ⚙)."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )

            ProfileTitle(tr("Your friend code"))
            val code = Friends.state?.code.orEmpty()
            when {
                !Config.enabled -> Text(tr("Online sharing isn’t set up in this build yet."), color = cs.onSurfaceVariant)
                code.isNotEmpty() -> Text(Friends.pretty(code), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                problem -> Text(tr("Couldn’t reach the server. Check your connection."), color = cs.onSurfaceVariant)
                else -> CircularProgressIndicator(Modifier.size(28.dp))
            }
            Text(
                tr("Give it to people you want as friends. When they enter it, you approve them first."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Friends.shareCode(ctx, code) }, enabled = code.isNotEmpty()) { Text(tr("Share code")) }
                OutlinedButton(onClick = {
                    clip.setText(AnnotatedString(Friends.pretty(code)))
                    scope.launch { snack.showToast(tr("Code copied")) }
                }, enabled = code.isNotEmpty()) { Text(tr("Copy")) }
            }
            TextButton(onClick = { adding = true }, enabled = Config.enabled) { Text(tr("Add a friend")) }
            TextButton(onClick = { newCodeDlg = true }, enabled = code.isNotEmpty()) { Text(tr("Make a new code")) }

            ProfileTitle(tr("Wishlists your friends can see"))
            val shown = Repo.mineIds.filter { Friends.isVisible(it) }.map { Repo.mineName(it) }
            Text(
                if (shown.isEmpty()) tr("None yet. Turn on “Visible to friends” in a wishlist’s settings (⚙).")
                else shown.joinToString(", "),
                color = if (shown.isEmpty()) cs.onSurfaceVariant else cs.onSurface
            )
            if (shown.isNotEmpty()) Text(
                tr("Change this in a wishlist’s settings (⚙). Private wishes always stay hidden."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            Text(
                tr("Your profile belongs to this phone. On a new phone, or after reinstalling the app, you start again."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    if (adding) AddFriendDialog { msg ->
        adding = false
        if (msg != null) scope.launch { snack.showToast(msg) }
    }
    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(), round = true,
            onSave = { chosen, e ->
                pending = null
                // Another photo was picked in the editor: the first one isn't needed.
                if (chosen != img) ImageStore.delete(ctx, img)
                save(chosen, e)
            },
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
    if (adjusting && Friends.photoSrc.isNotEmpty()) {
        HeroEditor(
            Friends.photoSrc, Friends.photoEdit, round = true,
            onSave = { chosen, e -> adjusting = false; save(chosen, e) },
            onCancel = { adjusting = false }
        )
    }
    if (newCodeDlg) {
        AlertDialog(
            onDismissRequest = { newCodeDlg = false },
            title = { Text(tr("Make a new code?")) },
            text = { Text(tr("Your old code stops working. Friends you already have stay.")) },
            confirmButton = {
                TextButton(onClick = {
                    newCodeDlg = false
                    scope.launch {
                        try {
                            Friends.newCode(ctx)
                            snack.showToast(tr("You have a new friend code"))
                        } catch (e: Exception) {
                            snack.showToast(tr("Couldn’t reach the server. Check your connection."))
                        }
                    }
                }) { Text(tr("Make a new code")) }
            },
            dismissButton = { TextButton(onClick = { newCodeDlg = false }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
private fun ProfileTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp)
    )
}
