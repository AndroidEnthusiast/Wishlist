package com.wishlist.app


import androidx.compose.foundation.layout.size

import androidx.compose.runtime.CompositionLocalProvider

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Someone else's list: you can see it and reserve items. The owner never sees reservations. What it holds and does is
 * FriendListState (FriendListModel.kt, the rebuild of 19 November 2026); this only draws.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    f: Friend, snack: SnackbarHostState, onRemoved: () -> Unit,
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
    reactions: Boolean = true,
    // The list's owner when they are your friend: "Chat about this wish" in a wish's ⋮ (owner, 20 November 2026).
    // Null on a gift exchange giftee's list (it would show you are their Secret Santa, owner) and for strangers.
    chatWith: String? = null
) {
    val go = LocalGo.current
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // The list, your reservations on it and what is open (FriendListState in FriendListModel.kt), one per list.
    val st = remember(f.code) { FriendListState(ctx, f, snack, scope, chatWith) }
    androidx.activity.compose.BackHandler(enabled = st.searching) { st.stopSearch() }
    ReactionsLoader(f.code)
    val deco = WishDeco(f.code, canReact = reactions, onBadge = { w ->
        val c = ResCounts.of(f.code, w.id)
        if (!w.got && (st.res[w.id] == false || (w.qty > 1 && c != null && c.full && c.my == 0))) st.askBought = w
    })

    // Load now, then again whenever the list changes (live, ROADMAP S3.2, Live.kt); without the live connection every
    // 20 seconds, as before. Nothing while the app is in the background.
    LaunchedEffect(f.code, st.tick) {
        Live.follow(Live.listTopic(f.code), 20000) { st.refresh(fetch) }
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                large = false,
                title = {
                    Column {
                        ScreenTitle(f.name)
                        val owner = ownerName?.takeIf { it.isNotBlank() } ?: st.remote?.owner
                        // "Owned by …" only when the list's name doesn't say whose it is (owner, 7 November 2026).
                        if (!owner.isNullOrBlank() && !nameSaysOwner(f.name, owner)) Text(
                            tr("Wishlist of %s", owner), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        // The list's occasion (owner, 2.124), e.g. "Birthday · in 3 weeks".
                        Occasions.line(st.remote?.occ)?.let { o ->
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
                    // Search, as on your own lists; the view choice is in the row above the wishes (ListTools).
                    IconButton(onClick = { if (st.searching) st.stopSearch() else st.searching = true }) {
                        Icon(AppIcons.Search, if (st.searching) tr("Close search") else tr("Search"))
                    }
                    // No refresh button (owner, 7 November 2026): the list refreshes itself every 20 seconds.
                    if (onBack == null) IconButton(onClick = { st.confirmRemove = true }) { Icon(AppIcons.Delete, tr("Remove this list")) }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        val r0 = st.remote
        Column(Modifier.padding(pad)) {
            if (st.searching && r0 != null) SearchField(st.query, { st.query = it }) { st.stopSearch() }
            when {
                st.state == "gone" -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(AppIcons.ListIcon, tr("This list isn’t shared any more."), tr("Back"), { (onBack ?: onRemoved)() })
                }
                r0 != null -> {
                    // The list's cover photo, as its owner made it (2.105).
                    // Only the categories that wishes on this list carry (owner, 7 November 2026); the row stays for the
                    // sort and the view (12 November 2026). A category that empties while you look at it falls back to All.
                    val onList = r0.wishes.filter { it.bin == 0L }
                    val usedCats = r0.cats.filter { c -> onList.any { it.category == c } }
                    if (st.cat != "All" && st.cat !in usedCats) st.cat = "All"
                    val hasCover = r0.cover.isNotEmpty()
                    // The cover scrolls away with the wishes; the categories stay pinned at the top (owner, 7 November 2026).
                    val cover: @Composable (Modifier) -> Unit = { m ->
                        HeroPicture(
                            r0.cover, HeroEdit.fromJson(r0.coverEdit),
                            m.fillMaxWidth().height(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                            cut = r0.cover
                        )
                    }
                    // The same row as on your own lists (ListTools.kt, owner 12 November 2026): category, sort, view.
                    // The view and the sort are one choice for all lists (owner, 7 November 2026).
                    val catRow: @Composable (PaddingValues, Boolean) -> Unit = { pv, live ->
                        Box(Modifier.padding(pv)) {
                            ListTools(
                                st.cat, usedCats, { c -> if (c == "All") onList.size else onList.count { it.category == c } }, { st.cat = it },
                                st.catMenu && live, { st.catMenu = it },
                                Repo.sort, true, { st.setSort(it) },
                                Repo.view, { st.setView(it) }
                            )
                        }
                    }
                    // Wishes in the owner's bin are not on the list.
                    // Owned wishes at the bottom (owner, 2.113): what's still wanted comes first.
                    // Gifts someone else reserved go below the free ones (owner, 23 November 2026).
                    val shown = reservedLast(sortWishes(onList.filter { (st.cat == "All" || it.category == st.cat) && matchesQuery(it, st.query) }, Repo.sort)) { w ->
                        reserveState(w, ResCounts.of(f.code, w.id), st.res[w.id])
                    }
                    if (shown.isEmpty()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (hasCover) cover(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                            catRow(PaddingValues(horizontal = 16.dp), true)
                            if (st.query.isNotBlank()) EmptyState(AppIcons.Search, tr("No wishes match “%s”.", st.query.trim()), tr("Clear search"), { st.stopSearch() })
                            else if (chatWith != null) EmptyState(AppIcons.Gift, tr("Nothing here yet."), tr("Send a message"), { go(Deep.Chat(chatWith)) })
                            else EmptyState(AppIcons.Gift, tr("Nothing here yet."), tr("Back"), { (onBack ?: onRemoved)() })
                        }
                    } else {
                        // Before the wishes in the grid: the cover (when there is one) and the categories (when used).
                        val pre = (if (hasCover) 1 else 0) + 1
                        val catIndex = if (hasCover) 1 else 0
                        // A wish tapped on a friend's "Wishes" or "Top wishes" page: scroll to it if it isn't in
                        // sight and let it glow (2.95, like your own lists).
                        val gridState = rememberLazyGridState()
                        // The pinned categories' height, once shown (to keep a wish that glows clear of them).
                        var pinnedHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val pinned by remember(catIndex) {
                            derivedStateOf {
                                (gridState.firstVisibleItemIndex > catIndex ||
                                    (gridState.firstVisibleItemIndex == catIndex && gridState.firstVisibleItemScrollOffset > 0))
                            }
                        }
                        LaunchedEffect(WishFocus.target, st.cat, shown.size) {
                            val t = WishFocus.target ?: return@LaunchedEffect
                            val i = shown.indexOfFirst { it.id == t }
                            if (i < 0) {
                                if (st.cat != "All" || st.query.isNotEmpty()) { st.cat = "All"; st.query = ""; st.searching = false } else WishFocus.target = null
                                return@LaunchedEffect
                            }
                            delay(150)
                            // Fully in sight below the pinned categories, in every view (owner, 21 November 2026, a
                            // recording: it glowed half under them; FocusScroll.kt). Before the first scroll the pinned
                            // copy isn't measured yet: the list's own row plus the pinned copy's padding then.
                            val pad = with(density) { 8.dp.roundToPx() }
                            gridState.showWhole(i + pre, {
                                if (pinned && pinnedHeight > 0) pinnedHeight
                                else gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == catIndex }?.size?.height?.plus(pad) ?: 0
                            }, with(density) { 12.dp.roundToPx() })
                            WishFocus.glow(t)
                            WishFocus.target = null
                        }
                        Box(Modifier.fillMaxSize()) {
                        // Givers read "Jorai loves it" on the gold label (owner, 16 November 2026).
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalWishDeco provides deco,
                            LocalLover provides (ownerName?.takeIf { it.isNotBlank() } ?: st.remote?.owner).orEmpty()
                        ) {
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier.fadeThroughOn(Repo.view),
                            columns = if (Repo.view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                            contentPadding = PaddingValues(16.dp, if (pre == 0) 12.dp else 0.dp, 16.dp, 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (Repo.view == "list") 16.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (Repo.view == "list") 16.dp else 12.dp)
                        ) {
                            if (hasCover) item(key = "cover", span = { GridItemSpan(maxLineSpan) }) { cover(Modifier) }
                            item(key = "cats", span = { GridItemSpan(maxLineSpan) }) {
                                catRow(PaddingValues(0.dp), !pinned)
                            }
                            gridItems(shown, key = { it.id }) { w ->
                                val r = st.res[w.id]
                                val c = ResCounts.of(f.code, w.id)
                                val rs = reserveState(w, c, r)
                                // The reservation is on the button (owner, 23 November 2026); the line keeps "Owned",
                                // "1 of 3 reserved" and "Over budget".
                                val label = if (w.got) tr("Owned") else reserveLine(w, c)
                                    ?: if (overBudget?.invoke(w) == true) tr("Over budget") else null
                                val ico = cardIcon(Repo.view)
                                val actions: @Composable RowScope.() -> Unit = {
                                    // Reserve left, share, then the ⋮ last (owner, 18 November 2026; CardButtons).
                                    CardButtons(
                                        spread = Repo.view == "rows",
                                        // "Unreserve" says exactly what it does (owner: "Undo" could be forgotten).
                                        reserve = rs?.let { state -> {
                                            ReserveButton(w, state, reserveButtonText(w, c, state), { on -> st.toggle(w, on) }, onTaken = deco.onBadge?.let { b -> { b(w) } })
                                        } },
                                        icons = {
                                            // Share as its own button too (owner, 12 November 2026).
                                            IconButton(onClick = { shareItem(ctx, w, idea = true) }) {
                                                Icon(AppIcons.CardShare, tr("Share"), tint = LocalLook.current.cardMuted(w.id), modifier = Modifier.size(ico))
                                            }
                                        }
                                    ) {
                                        // The wish's ⋮ sheet, as on every list (WishSheet.kt, 12 November 2026): Reserve and
                                        // Share this gift idea (its shop link, owner 7 November 2026).
                                        WishMoreButton(
                                            w, LocalLook.current.cardMuted(w.id), ico,
                                            WishActions(
                                                reserve = if (w.got) null else Reserving(r, label, c) { on -> st.toggle(w, on) }, shareAsIdea = true,
                                                chatAbout = chatWith?.let { p -> { go(Deep.Chat(p, ChatWish.of(w, f.code))) } }
                                            )
                                        )
                                    }
                                }
                                // Glides to its new place when the order changes, like your own lists (owner, 25 November
                                // 2026); "Add to my wishlist" peels a copy off it (CardMotion.kt).
                                Box(Modifier.animateItem(placementSpec = glideUnless(Repo.view))) {
                                GlowBox(w.id, small = Repo.view == "grid") {
                                Box(Modifier.motionCard(w.id)) {
                                when (Repo.view) {
                                    "grid" -> CompactCard(
                                        w, readOnly = true,
                                        reserve = if (w.got) null else Reserving(r, label, c) { on -> st.toggle(w, on) },
                                        chatAbout = chatWith?.let { p -> { go(Deep.Chat(p, ChatWish.of(w, f.code))) } }
                                    ) {}
                                    "rows" -> RowCard(w, badge = label, dim = w.got || rs == "taken", actions = actions)
                                    else -> WishCard(w, badge = label, dim = w.got || rs == "taken", actions = actions)
                                }
                                // New since you last opened this list (owner, 23 November 2026).
                                if (w.id in st.fresh) NewRibbon(LocalLook.current.cardShape(w.id, Repo.view == "grid"))
                                }
                                }
                                }
                            }
                        }
                        }
                        // The pinned copy of the categories once the list's own row scrolls away under it.
                        if (pinned) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                            .onSizeChanged { pinnedHeight = it.height }.padding(vertical = 4.dp)) {
                            catRow(PaddingValues(horizontal = 16.dp), true)
                        }
                        }
                    }
                }
                st.state == "offline" -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(AppIcons.Refresh, tr("Couldn’t load the list. Check your connection."), tr("Try again"), { st.tick++ })
                }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    // A tapped "X is free" or "Your reservation of X has ended" (2.142): this list opens on that wish.
    LaunchedEffect(places.askFocus, st.remote, st.res) {
        val (code, id, reserve) = places.askFocus ?: return@LaunchedEffect
        if (code != f.code) return@LaunchedEffect
        val remote = st.remote ?: return@LaunchedEffect
        val w = remote.wishes.firstOrNull { it.id == id && it.bin == 0L }
        if (w == null) {
            // Gone from the list (owner, 20 November 2026: no dead ends): the list stays open and says so.
            places.askFocus = null
            snack.showToast(tr("This wish is no longer on the list"))
            return@LaunchedEffect
        }
        places.askFocus = null
        WishFocus.target = id
        if (reserve && st.res[id] == null && !w.got) st.reserveNow = w
    }
    st.askBought?.let { w ->
        AskDialog(f.code, w, st.remote?.occ, onClose = { st.askBought = null }) { m -> scope.launch { snack.showToast(m) } }
    }
    st.reserveNow?.let { w -> ReserveNowDialog(w, onReserve = { st.toggle(w, true) }, onClose = { st.reserveNow = null }) }
    st.askFor?.let { w ->
        ReserveCountDialog(
            ResCounts.free(f.code, w), ResCounts.of(f.code, w.id)?.my ?: 1,
            onPick = { n -> st.askFor = null; st.reserveCount(w, n) }, onClose = { st.askFor = null }
        )
    }
    if (st.confirmRemove) {
        AlertDialog(
            onDismissRequest = { st.confirmRemove = false },
            title = { Text(tr("Remove this list?")) },
            text = { Text(tr("It disappears from your menu. Your reservations stay reserved. You can add it again with its code.")) },
            confirmButton = {
                TextButton(danger = true, onClick = {
                    st.removeList()
                    onRemoved()
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(onClick = { st.confirmRemove = false }) { Text(tr("Cancel")) } }
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
