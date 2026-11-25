package com.wishlist.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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

/*
 * The Friends tab (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): your friend code card, requests,
 * your friends and lists from links, and moving between a friend, their list and their wishes.
 */

@Composable
internal fun FriendsTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleMedium, fontWeight = LocalLook.current.titleWeight,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * The Friends tab: your code and "Add a friend", requests waiting for your approval, requests you sent, your friends
 * (tap: their wishlists; long press or ⋮: unfriend) and lists you opened from a link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(snack: SnackbarHostState, onProfile: () -> Unit, onOpenLink: (String) -> Unit) {
    val places = LocalPlaces.current
    val go = LocalGo.current
    val ctx = LocalContext.current
    Friends.load(ctx)
    val person = places.person?.let { id -> Friends.state?.friends?.firstOrNull { it.id == id } }
    val list = places.friendList
    if (places.person != null && person == null && Friends.state != null) {
        // No longer friends (unfriended, here or by them).
        LaunchedEffect(Unit) { places.resetFriends() }
    }
    // Back goes one step up: from a list or their wishes to their page, from their page to Friends (or the chat it
    // came from). Outside the movement, so only the screen on screen answers it.
    when {
        person != null && list != null -> BackHandler { places.friendList = null }
        person != null && places.friendPage != null -> BackHandler { places.friendPage = null }
        person != null -> BackHandler { places.leavePerson(go) }
    }
    // Each step slides in from the right and back (Motion.kt). The screen that leaves is drawn from where it was.
    val at = FriendsAt(person, if (person != null) list else null, if (person != null) places.friendPage else null)
    Moving(at, depth = { it.depth }, key = { listOf(it.person?.id, it.list?.code, it.page) }) { a ->
        val p = a.person
        when {
            p != null && a.list != null -> {
                val l = a.list
                // Coming back from the list returns to where you were: their page or their wishes page.
                FriendScreen(
                    Friend(l.code, l.name), snack, onRemoved = {},
                    fetch = { Friends.fetchList(ctx, p.id, l.code) }, ownerName = p.name, chatWith = p.id,
                    onBack = { places.friendList = null }
                )
            }
            p != null && a.page != null -> FriendWishesPage(p, a.page, snack)
            p != null -> FriendPage(p, snack)
            else -> FriendsHome(snack, onProfile, onOpenLink)
        }
    }
}

/** Where you are in the Friends tab, as one value for the movement between its screens. */
internal data class FriendsAt(val person: Person?, val list: FriendList?, val page: String?) {
    val depth: Int get() = when {
        person == null -> 0
        list != null || page != null -> 2
        else -> 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsHome(snack: SnackbarHostState, onProfile: () -> Unit, onOpenLink: (String) -> Unit) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var problem by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var unfollow by remember { mutableStateOf<Person?>(null) }
    // Block and report (Safety.kt, ROADMAP S4.8), next to Unfriend.
    var blocking by remember { mutableStateOf<Person?>(null) }
    var reporting by remember { mutableStateOf<Person?>(null) }

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

    // "3 new wishes" (owner, 2.139): their lists are looked at when this tab opens (not on every refresh).
    LaunchedEffect(tick, Friends.state?.friends?.map { it.id }) {
        Friends.state?.friends?.let { try { FriendNews.refresh(ctx, it) } catch (e: Exception) {} }
    }
    // A friend link was tapped (owner, 2.139): "Send Jorai a friend request?".
    LaunchedEffect(places.addCode) { if (places.addCode != null) adding = true }

    val people = rememberPeople()
    var meeting by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
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
                title = { ScreenTitle(tr("Friends"), big = true) }, large = false,
                navigationIcon = { MenuSlot(back = false) },
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
                Notice(tr("Add your name and photo so friends recognise you."), tr("Go to profile"), onProfile)
            }
            item { CodeCard(s?.code.orEmpty(), problem && s == null, onAdd = { adding = true }, onRetry = { tick++ }, small = s?.friends?.isNotEmpty() == true) }
            // With a way on (the review of 20 November 2026: the message stood there without one).
            if (problem && s != null) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Couldn’t reach the server. Check your connection."), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { tick++ }) { Text(tr("Try again")) }
                }
            }
            if (s != null && s.incoming.isNotEmpty()) {
                item { FriendsTitle(tr("Waiting for your approval")) }
                items(s.incoming, key = { "in" + it.id }) { r ->
                    // Approve: your two pictures meet first, then they become a friend (owner, 25 November 2026).
                    PersonRow(r, tr("wants to be your friend"), meeting = if (meeting == r.id) ({
                        meeting = null
                        act(tr("You and %s are friends now", r.name)) { people.answer(r.id, true) }
                    }) else null) {
                        TextButton(enabled = meeting == null, onClick = { act(tr("Request declined")) { people.answer(r.id, false) } }) { Text(tr("Decline")) }
                        Button(enabled = meeting == null, onClick = { meeting = r.id }) { Text(tr("Approve")) }
                    }
                }
            }
            if (s != null && s.outgoing.isNotEmpty()) {
                item { FriendsTitle(tr("Sent requests")) }
                items(s.outgoing, key = { "out" + it.id }) { r ->
                    PersonRow(r, tr("Waiting for approval")) {
                        TextButton(onClick = { act(tr("Request withdrawn")) { people.withdraw(r.id) } }) { Text(tr("Withdraw")) }
                    }
                }
            }
            if (s != null) {
                item { FriendsTitle(tr("Your friends")) }
                if (s.friends.isEmpty()) item {
                    Text(
                        tr("No friends here yet. Share your friend code, or add someone with theirs."),
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                // Friends with new wishes first (owner, 2.139), then by name.
                val ordered = s.friends.sortedWith(compareByDescending<Person> { FriendNews.counts[it.id] ?: 0 }.thenBy { it.name.lowercase() })
                items(ordered, key = { "f" + it.id }) { f ->
                    FriendRow(
                        f,
                        onOpen = { places.person = f.id },
                        onUnfriend = { unfollow = f },
                        onBlock = { blocking = f },
                        onReport = { reporting = f }
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
                        PlaceholderIcon("gift", 22.dp)
                        Text(
                            l.name, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (adding) AddFriendDialog(onClose = { msg ->
        adding = false
        places.addCode = null
        if (msg != null) scope.launch { snack.showToast(msg) }
    }, prefill = places.addCode.orEmpty(), prefillName = places.addName)
    blocking?.let { f -> BlockDialog(f.id, f.name, snack, onDone = { blocking = null }, onClose = { blocking = null }) }
    reporting?.let { f -> ReportDialog(f.id, f.name, snack) { reporting = null } }
    unfollow?.let { f ->
        AlertDialog(
            onDismissRequest = { unfollow = null },
            title = { Text(tr("Unfriend %s?", f.name)) },
            text = {
                Text(tr("You’ll stop seeing each other’s wishlists. They won’t be told, and you can add each other again later."))
            },
            confirmButton = {
                TextButton(danger = true, onClick = {
                    unfollow = null
                    act(tr("You and %s are no longer friends", f.name)) { people.unfriend(f.id) }
                }) { Text(tr("Unfriend")) }
            },
            dismissButton = { TextButton(onClick = { unfollow = null }) { Text(tr("Cancel")) } }
        )
    }
}
