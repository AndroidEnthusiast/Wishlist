package com.wishlist.app

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Someone else's list: you can see it and reserve items. The owner never sees reservations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    f: Friend, snack: SnackbarHostState, onMenu: () -> Unit, onRemoved: () -> Unit,
    // A list of a friend from the Friends tab: loaded through the friendship (not the link), with a back arrow and
    // no "Remove this list" (unfriending is done on the friend).
    fetch: (suspend () -> RemoteList?)? = null,
    onBack: (() -> Unit)? = null,
    // Whose list it is ("Owned by …", 2.102): the friend's name, or what the list itself says (opened from a link).
    ownerName: String? = null,
    // Something more in the top bar: "Add friend" on the list of a wishlist group member (2.108).
    extraAction: (@Composable () -> Unit)? = null,
    // A gift exchange's budget (2.129): wishes that cost more get "Over budget" on your giftee's list.
    overBudget: ((Wish) -> Boolean)? = null,
    // Reactions on wishes (2.142): everywhere except in a gift exchange (owner).
    reactions: Boolean = true
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var remote by remember(f.code) { mutableStateOf<RemoteList?>(null) }
    var res by remember(f.code) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var state by remember(f.code) { mutableStateOf("loading") }
    // Wishes whose reservation is on its way to the server (owner, 7 November 2026): only their button waits; the label
    // changes at once and goes back if the server says no.
    var pending by remember(f.code) { mutableStateOf<Set<Long>>(emptySet()) }
    var cat by remember(f.code) { mutableStateOf("All") }
    var tick by remember { mutableStateOf(0) }
    var confirmRemove by remember { mutableStateOf(false) }
    // A 2× wish asks "How many will you buy?" (2.128).
    var askFor by remember { mutableStateOf<Wish?>(null) }
    // "Is it still being bought?" about someone else's reservation (2.142), and "Reserve it now?" after "X is free".
    var askBought by remember { mutableStateOf<Wish?>(null) }
    var reserveNow by remember { mutableStateOf<Wish?>(null) }
    ReactionsLoader(f.code)
    val deco = WishDeco(f.code, canReact = reactions, onBadge = { w ->
        val c = ResCounts.of(f.code, w.id)
        if (!w.got && (res[w.id] == false || (w.qty > 1 && c != null && c.full && c.my == 0))) askBought = w
    })

    // "Viewed 23 times" for the owner (2.136): one view each time this list is opened, never on the refresh below.
    LaunchedEffect(f.code) {
        try { Cloud.rpc("wl_view", org.json.JSONObject().put("p_code", f.code).put("p_device", Repo.deviceId)) } catch (e: Exception) {}
    }
    // Load now, then refresh every 20 seconds while this screen is open.
    LaunchedEffect(f.code, tick) {
        while (true) {
            try {
                val r = if (fetch != null) fetch() else Cloud.fetchList(f.code)
                if (r == null) {
                    state = "gone"
                } else {
                    val m = Cloud.fetchReservations(f.code, Repo.deviceId)
                    remote = r
                    // A tap still on its way keeps what it shows.
                    res = if (pending.isEmpty()) m else m.toMutableMap().apply { pending.forEach { id -> res[id]?.let { put(id, it) } ?: remove(id) } }
                    FriendWatch.check(ctx, f, r, m.filterValues { it }.keys, FriendWatch.movedFrom(ctx, f.code, r))
                    state = "ok"
                    if (fetch == null && r.name != f.name) Repo.renameFriend(ctx, f.code, r.name)
                }
            } catch (e: Exception) {
                if (remote == null) state = "offline"
            }
            delay(20000)
        }
    }

    fun reserveCount(w: Wish, n: Int) {
        val before = res[w.id]
        res = if (n > 0) res + (w.id to true) else res - w.id
        pending = pending + w.id
        scope.launch {
            var msg: String? = null
            try {
                if (!Cloud.reserveCount(f.code, w.id, Repo.deviceId, n)) msg = tr("Someone else just reserved that one.")
                else if (n > 0) FriendWatch.reserved(ctx, f.code, w) else FriendWatch.unreserved(ctx, f.code, w.id)
            } catch (e: Exception) {
                msg = tr("No connection. Try again.")
                res = if (before == null) res - w.id else res + (w.id to before)
            }
            pending = pending - w.id
            try { res = Cloud.fetchReservations(f.code, Repo.deviceId) } catch (e: Exception) {}
            msg?.let { snack.showToast(it) }
        }
    }

    // Reserve shows "Reserved by you" and Unreserve at once (owner, 7 November 2026: it took too long, and every
    // Reserve button went grey); the server is asked in the background and a "no" puts it back with a message.
    fun toggle(w: Wish, reserve: Boolean) {
        if (w.qty > 1) { if (reserve) askFor = w else reserveCount(w, 0); return }
        if (w.id in pending) return
        val before = res[w.id]
        res = if (reserve) res + (w.id to true) else res - w.id
        pending = pending + w.id
        scope.launch {
            var msg: String? = null
            var failed = false
            try {
                if (reserve) {
                    if (!Cloud.reserve(f.code, w.id, Repo.deviceId)) { msg = tr("Someone else just reserved that one."); failed = true }
                    else FriendWatch.reserved(ctx, f.code, w)
                } else {
                    Cloud.unreserve(f.code, w.id, Repo.deviceId)
                    FriendWatch.unreserved(ctx, f.code, w.id)
                }
            } catch (e: Exception) {
                msg = tr("No connection. Try again.")
                failed = true
            }
            if (failed) res = if (before == null) res - w.id else res + (w.id to before)
            pending = pending - w.id
            try { res = Cloud.fetchReservations(f.code, Repo.deviceId) } catch (e: Exception) {}
            msg?.let { snack.showToast(it) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(f.name, fontWeight = LocalLook.current.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val owner = ownerName?.takeIf { it.isNotBlank() } ?: remote?.owner
                        // "Owned by …" only when the list's name doesn't say whose it is (owner, 7 November 2026).
                        if (!owner.isNullOrBlank() && !nameSaysOwner(f.name, owner)) Text(
                            tr("Wishlist of %s", owner), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        // The list's occasion (owner, 2.124), e.g. "Birthday · in 3 weeks".
                        Occasions.line(remote?.occ)?.let { o ->
                            Text(
                                o, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } else MenuSlot()
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    extraAction?.invoke()
                    // The same views as your own lists, one choice for all lists (owner, 7 November 2026).
                    ViewMenuButton(Repo.view) { Repo.setView(ctx, it) }
                    // No refresh button (owner, 7 November 2026): the list refreshes itself every 20 seconds.
                    if (onBack == null) IconButton(onClick = { confirmRemove = true }) { Icon(AppIcons.Delete, tr("Remove this list")) }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        val r0 = remote
        Column(Modifier.padding(pad)) {
            when {
                state == "gone" -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        tr("This list isn’t shared any more."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                r0 != null -> {
                    // The list's cover photo, as its owner made it (2.105).
                    // Only the categories that wishes on this list carry (owner, 7 November 2026); none at all: no row,
                    // the wishes move up. A category that empties while you look at it falls back to All.
                    val onList = r0.wishes.filter { it.bin == 0L }
                    val usedCats = r0.cats.filter { c -> onList.any { it.category == c } }
                    if (cat != "All" && cat !in usedCats) cat = "All"
                    val hasCover = r0.cover.isNotEmpty()
                    // The cover scrolls away with the wishes; the categories stay pinned at the top (owner, 7 November 2026).
                    val cover: @Composable (Modifier) -> Unit = { m ->
                        HeroPicture(
                            r0.cover, HeroEdit.fromJson(r0.coverEdit),
                            m.fillMaxWidth().height(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                            cut = r0.cover
                        )
                    }
                    val catRow: @Composable (PaddingValues) -> Unit = { pv ->
                        LazyRow(contentPadding = pv, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("All") + usedCats) { c ->
                                FilterChip(selected = c == cat, onClick = { cat = c }, label = { Text(catLabel(c)) })
                            }
                        }
                    }
                    // Wishes in the owner's bin are not on the list.
                    // Owned wishes at the bottom (owner, 2.113): what's still wanted comes first.
                    val shown = onList.filter { cat == "All" || it.category == cat }.sortedBy { it.got }
                    if (shown.isEmpty()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (hasCover) cover(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                            if (usedCats.isNotEmpty()) catRow(PaddingValues(horizontal = 16.dp))
                            Text(
                                tr("Nothing here yet."), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        // Before the wishes in the grid: the cover (when there is one) and the categories (when used).
                        val pre = (if (hasCover) 1 else 0) + (if (usedCats.isNotEmpty()) 1 else 0)
                        val catIndex = if (hasCover) 1 else 0
                        // A wish tapped on a friend's "Wishes" or "Top wishes" page: scroll to it if it isn't in
                        // sight and let it glow (2.95, like your own lists).
                        val gridState = rememberLazyGridState()
                        val pinned by remember(catIndex, usedCats.isNotEmpty()) {
                            derivedStateOf {
                                usedCats.isNotEmpty() && (gridState.firstVisibleItemIndex > catIndex ||
                                    (gridState.firstVisibleItemIndex == catIndex && gridState.firstVisibleItemScrollOffset > 0))
                            }
                        }
                        LaunchedEffect(WishFocus.target, cat, shown.size) {
                            val t = WishFocus.target ?: return@LaunchedEffect
                            val i = shown.indexOfFirst { it.id == t }
                            if (i < 0) {
                                if (cat != "All") cat = "All" else WishFocus.target = null
                                return@LaunchedEffect
                            }
                            delay(150)
                            val info = gridState.layoutInfo
                            val seen = info.visibleItemsInfo.firstOrNull { it.index == i + pre }
                            val inSight = seen != null && seen.offset.y >= info.viewportStartOffset &&
                                seen.offset.y + seen.size.height <= info.viewportEndOffset
                            if (!inSight) gridState.animateScrollToItem(i + pre)
                            WishFocus.glow(t)
                            WishFocus.target = null
                        }
                        Box(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalWishDeco provides deco) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = if (Repo.view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                            contentPadding = PaddingValues(16.dp, if (pre == 0) 12.dp else 0.dp, 16.dp, 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (Repo.view == "list") 16.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (Repo.view == "list") 16.dp else 12.dp)
                        ) {
                            if (hasCover) item(key = "cover", span = { GridItemSpan(maxLineSpan) }) { cover(Modifier) }
                            if (usedCats.isNotEmpty()) item(key = "cats", span = { GridItemSpan(maxLineSpan) }) {
                                catRow(PaddingValues(0.dp))
                            }
                            gridItems(shown, key = { it.id }) { w ->
                                val r = res[w.id]
                                val label = if (w.got) tr("Owned") else reserveLabel(f.code, w, r)
                                    ?: if (overBudget?.invoke(w) == true) tr("Over budget") else null
                                val actions: @Composable RowScope.() -> Unit = {
                                    // Share this gift idea: its shop link (owner, 7 November 2026).
                                    IconButton(onClick = { shareItem(ctx, w, idea = true) }) {
                                        Icon(AppIcons.Share, tr("Share"), tint = LocalLook.current.cardMuted(w.id))
                                    }
                                    if (!w.got) {
                                        // "Unreserve" says exactly what it does (owner: "Undo" could be forgotten).
                                        if (r == null) {
                                            Button(onClick = { toggle(w, true) }) { Text(tr("Reserve")) }
                                        } else if (r == true) {
                                            OutlinedButton(onClick = { toggle(w, false) }) { Text(tr("Unreserve")) }
                                        }
                                    }
                                }
                                GlowBox(w.id, small = Repo.view == "grid") {
                                when (Repo.view) {
                                    "grid" -> CompactCard(
                                        w, readOnly = true,
                                        reserve = if (w.got) null else Reserving(r, label) { on -> toggle(w, on) }
                                    ) {}
                                    "rows" -> RowCard(w, badge = label, dim = w.got || r == false, actions = actions)
                                    else -> WishCard(w, badge = label, dim = w.got || r == false, actions = actions)
                                }
                                }
                            }
                        }
                        }
                        // The pinned copy of the categories once the list's own row scrolls away under it.
                        if (pinned) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 4.dp)) {
                            catRow(PaddingValues(horizontal = 16.dp))
                        }
                        }
                    }
                }
                state == "offline" -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("Couldn’t load the list. Check your connection."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { tick++ }) { Text(tr("Try again")) }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    // A tapped "X is free" or "Your reservation of X has ended" (2.142): this list opens on that wish.
    LaunchedEffect(AskNav.focus, remote, res) {
        val (code, id, reserve) = AskNav.focus ?: return@LaunchedEffect
        if (code != f.code) return@LaunchedEffect
        val w = remote?.wishes?.firstOrNull { it.id == id && it.bin == 0L } ?: return@LaunchedEffect
        AskNav.focus = null
        WishFocus.target = id
        if (reserve && res[id] == null && !w.got) reserveNow = w
    }
    askBought?.let { w ->
        AskDialog(f.code, w, remote?.occ, onClose = { askBought = null }) { m -> scope.launch { snack.showToast(m) } }
    }
    reserveNow?.let { w -> ReserveNowDialog(w, onReserve = { toggle(w, true) }, onClose = { reserveNow = null }) }
    askFor?.let { w ->
        ReserveCountDialog(
            ResCounts.free(f.code, w), ResCounts.of(f.code, w.id)?.my ?: 1,
            onPick = { n -> askFor = null; reserveCount(w, n) }, onClose = { askFor = null }
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(tr("Remove this list?")) },
            text = { Text(tr("It disappears from your menu. Your reservations stay reserved. You can add it again with its code.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    Repo.removeFriend(ctx, f.code)
                    onRemoved()
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("Cancel")) } }
        )
    }
}

/** True when a list's name already says whose it is ("Magdalena’s wishlist" by Magdalena): no "Owned by" then. */
fun nameSaysOwner(listName: String, owner: String): Boolean {
    val n = listName.lowercase()
    val o = owner.trim().lowercase()
    if (o.isEmpty()) return true
    return n.contains(o) || o.split(" ").first().let { it.length >= 2 && n.contains(it) }
}
