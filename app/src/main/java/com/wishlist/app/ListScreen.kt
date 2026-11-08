package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

// The open wishlist (Route.LIST): its screen, the view buttons and menus. Moved out of MainActivity.kt in 2.163.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snack: SnackbarHostState, onAdd: () -> Unit, onInvite: () -> Unit,
    onListSettings: () -> Unit, onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    // "+ New category" from the category menu (ROADMAP S1.4: only this screen opens it, so it lives here).
    var catDlg by remember { mutableStateOf(false) }
    if (catDlg) CategoryDialog { catDlg = false }
    var cat by remember { mutableStateOf("All") }
    var catMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }
    var editing by remember { mutableStateOf<Wish?>(null) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    var historyId by remember { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current
    val openMenu: (Wish) -> Unit = { w ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuId = w.id
    }
    // Selecting several wishes (owner, 7 November 2026): a long press on your own list ticks that wish; then taps tick
    // more, and the top bar offers Move to… and Delete. Back or ✕ stops. Collab wishlists keep their long-press menu.
    var picked by remember(Repo.openMine, Shared.active) { mutableStateOf(setOf<Long>()) }
    val startSelect: (Wish) -> Unit = { w ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        picked = setOf(w.id)
    }
    val selecting = picked.isNotEmpty() && Shared.active == null
    BackHandler(enabled = selecting) { picked = emptySet() }
    fun toggle(w: Wish) { picked = if (w.id in picked) picked - w.id else picked + w.id }
    // A collab wishlist updates live while it is open (ROADMAP S3.2, Live.kt): someone else's change shows within a
    // second or two. Without the live connection: every 20 seconds, as before. Nothing while the app is in the background.
    LiveRefresh(Shared.active?.let { Live.listTopic(it) }, Shared.active != null, 20_000) {
        // A save of this phone still on its way: wait for it, then fetch (else this answer would be thrown away).
        while (Shared.busy()) delay(300)
        Shared.refresh(ctx)
    }
    LaunchedEffect(Shared.notice) {
        val m = Shared.notice
        if (m.isNotEmpty()) {
            Shared.clearNotice()
            snack.showToast(m)
        }
    }
    val inShared = Shared.active != null
    // Home puts the list opened last first in its section (2.110).
    LaunchedEffect(Shared.active, Repo.openMine) {
        Repo.touchRecent(ctx, Shared.active?.let { "s:$it" } ?: ("m:" + Repo.openMine))
    }
    val pull = rememberPullToRefreshState()
    if (pull.isRefreshing) {
        LaunchedEffect(true) {
            // Asking every shop can take long (some are read in a hidden browser, one wish at a time), so the circle
            // shows for at most 2.5 seconds; the check goes on in the background, prices change on screen as they
            // come in, and a message says when it's done.
            val job = PriceRefresh.start(ctx, if (inShared) Shared.active else null) { msg -> snack.showToast(msg) }
            val finished = withTimeoutOrNull(2500) { job.join() } != null
            pull.endRefresh()
            if (!finished) snack.showToast(tr("Checking prices…"), duration = SnackbarDuration.Short)
        }
    }
    // A shared wishlist is put together by the givers, for someone else: there is no "most wanted" (nobody can say
    // that for the person) and no "got it" (reserving is what counts). Wishes are shown without them.
    val sortMode = if (inShared && Repo.sort == "star") "new" else Repo.sort
    val res = if (inShared) Shared.reserved else emptyMap()
    val source = if (inShared) Repo.wishes.map { it.copy(star = false, got = false) } else Repo.wishes

    val sel = if (cat == "All" || cat in Repo.cats) cat else "All"
    val list = sortWishes(
        source.filter { (sel == "All" || it.category == sel) && matchesQuery(it, query) },
        sortMode
    )
    val view = Repo.view
    val look = LocalLook.current
    // A wish tapped on "All your wishes" or "Top wishes": scroll to it (showing every category if it was hidden by
    // the filter or a search) and let it glow (owner, 2.91).
    val gridState = rememberLazyGridState()
    // The item that holds the summary card and the filters: 1 with a cover photo above it, else 0 (see stickyTop).
    val stickyIndex = if (remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) != null }) 1 else 0
    val pinned by remember(stickyIndex) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > stickyIndex ||
                (gridState.firstVisibleItemIndex == stickyIndex && gridState.firstVisibleItemScrollOffset > 0)
        }
    }
    var stickyHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(WishFocus.target, sel, query) {
        val t = WishFocus.target ?: return@LaunchedEffect
        val i = list.indexOfFirst { it.id == t }
        if (i < 0) {
            if (sel != "All" || query.isNotEmpty()) {
                cat = "All"; query = ""; searching = false
            } else WishFocus.target = null
            return@LaunchedEffect
        }
        // The target is cleared only at the end: clearing it is a change of this effect's key, which would stop it
        // before the scroll and the glow (the bug in 2.91).
        delay(150)
        // Before the wishes: the cover photo (when there is one), then the summary card and filters (pinned at the
        // top once scrolled, 2.111: the wish must not hide under them). Only scrolls when the wish isn't fully in sight
        // (2.94: it also scrolled when it was, so the filters jumped away).
        val pre = stickyIndex + 1
        val info = gridState.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == i + pre }
        val inSight = seen != null && seen.offset.y >= info.viewportStartOffset + stickyHeight &&
            seen.offset.y + seen.size.height <= info.viewportEndOffset
        if (!inSight) gridState.animateScrollToItem(i + pre, -stickyHeight)
        WishFocus.glow(t)
        WishFocus.target = null
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            if (selecting) TopAppBar(
                title = { Text(if (picked.size == 1) tr("1 selected") else tr("%d selected", picked.size), fontWeight = look.titleWeight) },
                navigationIcon = { IconButton(onClick = { picked = emptySet() }) { Icon(AppIcons.Close, tr("Stop selecting")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    MoveBusy(Modifier.padding(end = 8.dp))
                    TextButton(onClick = { MoveFlow.ask(picked); picked = emptySet() }) { Text(tr("Move to…")) }
                    IconButton(onClick = {
                        val ws = Repo.wishes.filter { it.id in picked }
                        picked = emptySet()
                        DeleteFlow.requestMany(ctx, ws)
                    }) { Icon(AppIcons.Delete, tr("Delete selected")) }
                }
            ) else TopAppBar(
                title = {
                    // No "Owned by you" any more (owner, 2.135): friends see "Owned by Jorai", and a list is never
                    // called "My wishlist" when shared (NameGate).
                    ListTitle(Repo.listName, Repo.wishes.size)
                },
                // Back to Home (2.110, owner): the menu button gives way to this arrow on an open list.
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back to Home")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
                        Icon(AppIcons.Search, if (searching) tr("Close search") else tr("Search"))
                    }
                    // The settings of this list. Refreshing is pulling down on the list.
                    IconButton(onClick = onListSettings) { Icon(AppIcons.Settings, tr("Wishlist settings")) }
                    IconButton(onClick = { if (inShared) NameGate.ensure(onInvite) else NameGate.ensureListName { ShareNav.list = Repo.openMine } }) {
                        Icon(AppIcons.Share, if (inShared) tr("Invite or share") else tr("Share wishlist"))
                    }
                }
            )
        },
        // No floating "Add wish" button any more (owner, 2.112): the + in the bottom bar does the same.
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        // Behind the list: the blobs, glows or dots of the design (nothing in Material 3).
        Column(Modifier.padding(pad).lookBackdrop(look)) {
            if (inShared && Shared.gone) SharedGoneNotice()
            if (searching) SearchField(query, { query = it }) { searching = false; query = "" }
            // The category and sort choices and the view buttons. Since 2.89 they scroll with the list, under the
            // cover photo (when chosen) and the summary card.
            // [live]: this copy may open the category menu (the list's own copy is hidden under the pinned one while
            // that one shows, and the menu must open only once).
            val filters: @Composable (Boolean) -> Unit = { live ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: what you see and in which order (category, sort). Right: how it looks (view buttons).
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f, fill = false)) {
                            // Plain text, no filled chip (owner, 2.130: a calmer top).
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).clickable { catMenu = true }
                                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    catLabel(sel), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp)
                                )
                                Icon(AppIcons.ArrowDropDown, tr("Choose category"))
                            }
                            DropdownMenu(expanded = catMenu && live, onDismissRequest = { catMenu = false }) {
                                (listOf("All") + Repo.cats).forEach { c ->
                                    val n = if (c == "All") Repo.wishes.size else Repo.wishes.count { it.category == c }
                                    // Editing categories is in the wish form's "Edit categories" (2.110), not a long press here.
                                    CatMenuRow(
                                        text = catLabel(c) + " ($n)", bold = c == sel,
                                        onClick = { cat = c; catMenu = false }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(tr("New category…")) },
                                    leadingIcon = { Icon(AppIcons.Add, null) },
                                    onClick = { catMenu = false; catDlg = true }
                                )
                            }
                        }
                        // Room between the category and the sort button (owner, 2.127: they touched).
                        Spacer(Modifier.width(8.dp))
                        SortButton(sortMode, withStar = !inShared, quiet = true) { Repo.setSort(ctx, it) }
                        Spacer(Modifier.width(8.dp))
                    }
                    // One icon for the view (owner, 2.130): it shows the view in use and opens Rows, Two per row, Large cards.
                    ViewMenuButton(view) { Repo.setView(ctx, it) }
                }
            }
            // The top that stays in sight while scrolling (owner, 2.111): the summary card (your own lists) and the
            // category, sort and view choices. The cover photo above it scrolls away.
            val hasCover = remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) != null }
            // "Viewed 23 times" (owner, 2.136): only your own lists that are online; read when the list opens and on
            // every pull to refresh. Offline or before supabase-features.sql: nothing is shown.
            var views by remember(Repo.openMine, Shared.active) { mutableIntStateOf(0) }
            LaunchedEffect(Repo.openMine, Shared.active, pull.isRefreshing) {
                if (inShared) return@LaunchedEffect
                val d = Repo.mine(ctx, Repo.openMine)
                val code = d.code ?: return@LaunchedEffect
                val token = d.token ?: return@LaunchedEffect
                views = try {
                    Cloud.rpc("wl_view_count", org.json.JSONObject().put("p_code", code).put("p_token", token)).trim().toIntOrNull() ?: 0
                } catch (e: Exception) { views }
            }
            // Reactions (2.142): your own lists show them (no reacting), gift lists let every giver react. On a gift list a
            // tapped "Reserved" label of someone else asks "Is it still being bought?" (2.142).
            var askBought by remember { mutableStateOf<Wish?>(null) }
            var reserveNow by remember { mutableStateOf<Wish?>(null) }
            val listCode = if (inShared) Shared.active else Repo.mine(ctx, Repo.openMine).code
            ReactionsLoader(listCode)
            val listDeco = listCode?.let { code ->
                WishDeco(code, canReact = inShared, onBadge = if (!inShared) null else ({ w ->
                    val c = ResCounts.of(code, w.id)
                    if (res[w.id] == false || (w.qty > 1 && c != null && c.full && c.my == 0)) askBought = w
                }))
            }
            askBought?.let { w ->
                AskDialog(Shared.active.orEmpty(), w, Occasions.ofOpen(ctx), onClose = { askBought = null }) { m -> scope.launch { snack.showToast(m) } }
            }
            // A tapped "X is free" or "Your reservation of X has ended" on a gift list (2.142): it opens on that wish.
            LaunchedEffect(AskNav.focus, Shared.active, res) {
                val (code, id, reserve) = AskNav.focus ?: return@LaunchedEffect
                if (!inShared || code != Shared.active) return@LaunchedEffect
                val w = Repo.wishes.firstOrNull { it.id == id && it.bin == 0L } ?: return@LaunchedEffect
                AskNav.focus = null
                WishFocus.target = id
                if (reserve && res[id] == null) reserveNow = w
            }
            reserveNow?.let { w -> ReserveNowDialog(w, onReserve = { Shared.reserve(w, true) }, onClose = { reserveNow = null }) }
            val stickyTop: @Composable (Boolean) -> Unit = { live ->
                // More room between the summary and the choices (owner, 2.130: calmer, without making things smaller).
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The occasion shows in the summary card (owner, 2.124); on a gift list, or with the summary
                    // switched off, the card shows only the occasion.
                    val occ = Occasions.line(Occasions.ofOpen(ctx))
                    val counts = !inShared && Show["show_summary"]
                    if (counts || occ != null) ListSummaryCard(Repo.wishes, occasion = occ, counts = counts, views = views)
                    filters(live)
                }
            }
            // Pull down on the list to refresh it: the shops are asked for their prices now (and a shared list is
            // fetched again), so price changes show. This took the place of the refresh button in the top bar.
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pull.nestedScrollConnection)) {
            if (list.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ListHeader(Repo.wishes, snack)
                    if (hasCover) Spacer(Modifier.height(10.dp))
                    stickyTop(true)
                    Text(
                        if (query.isNotBlank()) {
                            if (sel != "All") tr("No wishes match “%1\$s” in %2\$s.", query.trim(), catLabel(sel))
                            else tr("No wishes match “%s”.", query.trim())
                        } else tr("Nothing here yet. Tap +, or share a product from any shop to Wishlist."),
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val gap = if (view == "list") 16.dp else 12.dp
                androidx.compose.runtime.CompositionLocalProvider(LocalWishDeco provides listDeco) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), // no floating button to keep clear of any more (2.112)
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    if (hasCover) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { ListHeader(Repo.wishes, snack) }
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { stickyTop(!pinned) }
                    gridItems(list, key = { it.id }) { w ->
                        val r = res[w.id]
                        // A wish opened from "All your wishes" glows softly twice (WishFocus).
                        GlowBox(w.id, small = view == "grid") {
                        Selectable(selecting, w.id in picked, { toggle(w) }) {
                        when (view) {
                            "grid" -> CompactCard(
                                // Two per row: the ⋮ holds everything; a long press on your own list starts selecting.
                                w, onLongPress = if (inShared) null else ({ startSelect(w) }),
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                reserve = if (inShared) Reserving(r, reserveLabel(Shared.active.orEmpty(), w, r)) { on -> Shared.reserve(w, on) } else null,
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
                                onMove = if (inShared) null else ({ MoveFlow.ask(setOf(w.id)) })
                            ) { editing = w }
                            // The buttons of Rows and Large cards: Share, the eye (or Reserve on a gift list), Delete.
                            else -> {
                            // Rows has smaller icons than the large cards (owner, 2.127: bigger than the private badge,
                            // smaller than before); the tap area stays the same.
                            val ico = if (view == "rows") 20.dp else 24.dp
                            val actions: @Composable RowScope.() -> Unit = {
                                // The bin stays, the rest is in one ⋮ (owner, 2.135: calmer rows).
                                if (!inShared) WishMoreButton(w, look.cardMuted(w.id), ico) { editing = w }
                                else IconButton(onClick = { shareItem(ctx, w) }) {
                                    Icon(AppIcons.Share, tr("Share"), tint = look.cardMuted(w.id), modifier = Modifier.size(ico))
                                }
                                if (inShared) {
                                    // Reserving takes the place of "got it" on a shared list.
                                    if (r == true) {
                                        OutlinedButton(onClick = { Shared.reserve(w, false) }) { Text(tr("Unreserve")) }
                                    } else if (r == null) {
                                        Button(onClick = { Shared.reserve(w, true) }) { Text(tr("Reserve")) }
                                    }
                                }
                                IconButton(onClick = { DeleteFlow.request(ctx, w) }) {
                                    Icon(AppIcons.Delete, tr("Delete"), tint = look.cardMuted(w.id), modifier = Modifier.size(ico))
                                }
                            }
                            if (view == "rows") RowCard(
                                w,
                                badge = if (inShared) reserveLabel(Shared.active.orEmpty(), w, r) else if (w.got) tr("Owned") else null,
                                dim = if (inShared) r == false else w.got,
                                onLongPress = { if (inShared) openMenu(w) else startSelect(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
                                actions = actions
                            ) else WishCard(
                                w,
                                badge = if (inShared) reserveLabel(Shared.active.orEmpty(), w, r) else if (w.got) tr("Owned") else null,
                                dim = if (inShared) r == false else w.got,
                                onLongPress = { if (inShared) openMenu(w) else startSelect(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
                                actions = actions
                            )
                            }
                        }
                        }
                        }
                    }
                }
                }
            }
            // The pinned copy of the top, once the list's own copy starts to scroll away under it.
            if (pinned && list.isNotEmpty()) Box(
                Modifier.fillMaxWidth().background(cs.background).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .onSizeChanged { stickyHeight = it.height }
            ) { stickyTop(true) }
            // Only while pulling or refreshing: at rest the circle with the arrow must not show at all.
            if (pull.progress > 0f || pull.isRefreshing) {
                PullToRefreshContainer(state = pull, modifier = Modifier.align(Alignment.TopCenter))
            }
            }
        }
    }

    editing?.let { w -> key(w.id) { WishDialog(w, "") { editing = null } } }
    menuId?.let { id -> source.firstOrNull { it.id == id } }?.let { w ->
        ItemMenuDialog(
            w,
            onEdit = { menuId = null; editing = w },
            // Only collab wishlists still have this long-press menu (Edit); on your own lists a long press selects
            // (owner, 7 November 2026). Share and Private are on the card, Price history is on Price watch.
            onHistory = null,
            onShare = null,
            onOwned = if (inShared) null else ({ menuId = null; OwnedFlow.toggle(ctx, w) }),
            onClose = { menuId = null }
        )
    }
    historyId?.let { id -> Repo.wishes.firstOrNull { it.id == id } }?.let { w ->
        PriceHistoryDialog(w) { historyId = null }
    }
}

/** One of the three view buttons in the row above the wishes. */
@Composable
fun ViewButton(kind: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    // The classic themes show only the chosen one filled (as before); the designs frame both, as buttons.
    val framed = true
    Box(
        Modifier.size(40.dp)
            .then(if (framed) Modifier.lookChip(look, on = selected) else Modifier.clip(look.chipShape))
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ViewIcon(kind, look.chipContent(selected))
    }
}

/** ⋮ on a wish of your own list (owner, 2.135): Edit, Share, Private (with its tick), Mark as owned. The bin stays next to it. */
@Composable
fun WishMoreButton(w: Wish, tint: Color, ico: androidx.compose.ui.unit.Dp, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More"), tint = tint, modifier = Modifier.size(ico)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Edit")) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(tr("Share")) }, onClick = { menu = false; shareItem(ctx, w) })
            PrivateMenuItem(w.priv) { menu = false; togglePrivate(ctx, w) }
            DropdownMenuItem(
                text = { Text(if (w.got) tr("Mark as not owned") else tr("Mark as owned")) },
                onClick = { menu = false; OwnedFlow.toggle(ctx, w) }
            )
            // Owner, 7 November 2026: to another of your wishlists.
            DropdownMenuItem(text = { Text(tr("Move to…")) }, onClick = { menu = false; MoveFlow.ask(setOf(w.id)) })
        }
    }
}

/** The view in use as one icon; tapping it opens the three views with their names (owner, 2.130). */
@Composable
fun ViewMenuButton(view: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val names = listOf("rows" to tr("Rows"), "grid" to tr("Two per row"), "list" to tr("Large cards"))
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .semantics { contentDescription = tr("View: %s", names.firstOrNull { it.first == view }?.second ?: "") }
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) { ViewIcon(view, cs.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { (k, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (k == view) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { ViewIcon(k, if (k == view) cs.primary else cs.onSurfaceVariant) },
                    trailingIcon = if (k == view) ({ Icon(AppIcons.Check, null) }) else null,
                    onClick = { open = false; onPick(k) }
                )
            }
        }
    }
}

/** Tiny drawn icons that look like each view: Rows, Two per row (a 2x2 grid) and Large cards. */
@Composable
fun ViewIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color = tint, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u),
            cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
        )
        when (kind) {
            // Large cards: one big picture with a line of text under it.
            "list" -> { box(3f, 3f, 18f, 12f); box(3f, 17f, 18f, 2.5f); box(3f, 20.5f, 11f, 2.5f) }
            // Rows: a small square with two lines next to it, twice.
            "rows" -> {
                box(3f, 3f, 8f, 8f); box(13f, 4f, 8f, 2.5f); box(13f, 7.5f, 5f, 2.5f)
                box(3f, 13f, 8f, 8f); box(13f, 14f, 8f, 2.5f); box(13f, 17.5f, 5f, 2.5f)
            }
            else -> { box(3f, 3f, 8f, 8f); box(13f, 3f, 8f, 8f); box(3f, 13f, 8f, 8f); box(13f, 13f, 8f, 8f) }
        }
    }
}
