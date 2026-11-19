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
import androidx.compose.material3.DropdownMenuItem
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
 * A friend's page (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): their lists, their wishes in one place
 * (or the ones they love), and your private note about them.
 */

/** Loads every list friend [f] lets you see (for the blocks and the numbers on their page). */
@Composable
internal fun LoadFriendLists(f: Person) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    LaunchedEffect(f.id, f.lists) {
        // All lists at once, each shown as soon as it arrives (owner, 8 November 2026: one after another, and nothing
        // until the last one, a slow call after the app was in the background left the page empty for a long time).
        // What was loaded before stays on screen meanwhile.
        val out = java.util.concurrent.ConcurrentHashMap<String, RemoteList>()
        kotlinx.coroutines.coroutineScope {
            f.lists.forEach { l ->
                launch {
                    try {
                        Friends.fetchList(ctx, f.id, l.code)?.let { r ->
                            out[l.code] = r
                            FriendCache.loaded = FriendCache.loaded + (l.code to r)
                        }
                    } catch (e: Exception) {
                    }
                }
            }
        }
        FriendCache.loadedFor = f.id
        // Opening their page counts as seeing their new wishes (owner, 2.139).
        if (out.size == f.lists.size) FriendNews.markSeen(ctx, f.id, out.values)
    }
}

/** A friend's wishes that you can see: visible lists, not in their bin. */
internal fun friendWishes(f: Person, loaded: Map<String, RemoteList>): List<Pair<Wish, FriendList>> =
    f.lists.flatMap { l -> loaded[l.code]?.wishes.orEmpty().filter { it.bin == 0L && it.id != ABOUT_ITEM_ID }.map { it to l } }
        .distinctBy { it.first.id }
        .sortedByDescending { it.first.id }

/**
 * A friend's page, person first (owner, 16 November 2026, after comparing with Giftful): their photo and name once,
 * in the middle; what a giver needs as chips (birthday, sizes, what they like, and how many wishes they love when there
 * are any: never a 0); a Message button; then their lists as photo collages. The private note is a small row under
 * the lists. No number tiles any more.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FriendPage(f: Person, snack: SnackbarHostState) {
    val places = LocalPlaces.current
    val go = LocalGo.current
    val ctx = LocalContext.current
    val people = rememberPeople()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    LoadFriendLists(f)
    val wishes = friendWishes(f, FriendCache.loaded)
    var noteOpen by remember(f.id) { mutableStateOf(false) }
    var note by remember(f.id) { mutableStateOf(Friends.note(ctx, f.id)) }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            // Only the way back: the name is under the photo (it was shown twice).
            TopAppBar(
                title = {}, large = false,
                navigationIcon = { IconButton(onClick = { places.leavePerson(go) }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            PersonHero(Friends.photos[f.id], f.name, f.birthday, f.bio, birthdaySoon = true) {
                if (f.lists.isEmpty()) Text(
                    tr("%s hasn’t added a wishlist yet.", f.name), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
                )
                // Message (2.98), as a button under the name (owner, 16 November 2026), not an icon in the top bar.
                OutlinedButton(onClick = { go(Deep.Chat(f.id)) }, modifier = Modifier.padding(top = 16.dp)) {
                    ChatIcon(cs.onSurface, 18.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Text(if (f.lists.isEmpty()) tr("Send a message") else tr("Message"))
                }
            }
            if (f.lists.isNotEmpty()) {
                Text(
                    tr("Wishlists"), style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
                )
                val items = f.lists.map { l ->
                    val ws = FriendCache.loaded[l.code]?.wishes.orEmpty().filter { it.bin == 0L && it.id != ABOUT_ITEM_ID }
                    // Their newest photos as a collage, as on your own Home (16 November 2026: friends showed one photo).
                    val pics = newestPics(ws)
                    HubItem("f:" + l.code, l.name, if (FriendCache.loaded.containsKey(l.code)) ws.size else l.count, pics.firstOrNull(), false, "", pics) { places.friendList = l }
                }
                BlockGrid(items, onHome = false, actionsOf = { emptyList() }, onAddTo = {})
            }
            // The private note (owner, 2.128; only on this phone): a small row under the lists, a sheet to write it.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp)).clickable { noteOpen = true }.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.Edit, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (note.isBlank()) tr("Add a private note") else tr("Private note"), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    Text(
                        note.ifBlank { tr("Only you can see this.") }, style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    if (noteOpen) PrivateNoteSheet(f, note, { note = it.take(500); people.saveNote(f.id, note) }) { noteOpen = false }
}

/** Writing the private note about a friend, in a sheet; saved as you type, on this phone only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivateNoteSheet(f: Person, note: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Private note about %s", f.name), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text(tr("Only you can see this."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            OutlinedTextField(
                note, onChange, minLines = 3, maxLines = 6,
                placeholder = { Text(tr("For example: loves tea, no candles")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(tr("Done")) }
            }
        }
    }
}

/**
 * A friend's wishes or top wishes (2.95, like your own pages): picture, name, "On …", price. Tapping one opens its list,
 * scrolls to it and lets it glow; you reserve there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendWishesPage(f: Person, page: String, snack: SnackbarHostState) {
    val places = LocalPlaces.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    LoadFriendLists(f)
    val all = friendWishes(f, FriendCache.loaded)
    val shown = if (page == "top") all.filter { it.first.star } else all
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    ScreenTitle(if (page == "top") tr("%s loves these", f.name) else tr("%s’s wishes", f.name))
                },
                navigationIcon = { IconButton(onClick = { places.friendPage = null }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    if (page == "top") androidx.compose.runtime.CompositionLocalProvider(LocalLover provides f.name) { MostWantedBadge(showLabel = true) }
                    Text(
                        if (page == "top") tr("The wishes %s would love most: a safe bet.", f.name)
                        else tr("Every wish %s lets friends see, in one place.", f.name),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
            }
            // Still loading: a spinner, never "No wishes" (owner, 7 November 2026).
            if (shown.isEmpty() && FriendCache.loadedFor != f.id && f.lists.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (shown.isEmpty()) item {
                Text(
                    if (page == "top") tr("No loved wishes to see yet.") else tr("No wishes to see yet."),
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)
                )
            }
            items(shown, key = { it.first.id }) { (w, l) ->
                val id = w.id
                Row(
                    Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                        .clickable { WishFocus.target = w.id; places.friendList = l }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(look.pictureBg(id, w.image.isNotBlank())),
                        contentAlignment = Alignment.Center
                    ) {
                        if (w.image.isNotBlank()) WishImage(w.image, Modifier.fillMaxSize().padding(4.dp))
                        else PlaceholderIcon("gift", 20.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(w.label, style = MaterialTheme.typography.bodyLarge, color = look.cardInk(id), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            tr("On %s", l.name), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = look.cardMuted(id), maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    w.price?.let {
                        Text(money(it, w.currency), style = MaterialTheme.typography.titleSmall, fontWeight = look.priceWeight, color = look.cardInk(id))
                    }
                    // The heart, not the old star (2.213: "Love it" replaced "Top wish").
                    if (w.star) LoveIcon(true, StarGold, 20.dp, Modifier.padding(start = 8.dp).semantics { contentDescription = tr("%s loves it", f.name) })
                }
            }
        }
    }
}
