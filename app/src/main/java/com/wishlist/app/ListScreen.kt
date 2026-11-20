package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
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
    val go = LocalGo.current
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    // What the screen shows besides the wishes, and what a tap changes (ListModel.kt, the rebuild of 19 November 2026).
    val model = remember { ListModel(ctx) }
    // "+ New category" from the category menu (ROADMAP S1.4: only this screen opens it, so it lives here).
    if (model.catDlg) CategoryDialog { model.catDlg = false }
    BackHandler(enabled = model.searching) { model.stopSearch() }
    val haptic = LocalHapticFeedback.current
    // Selecting several wishes (owner, 7 November 2026; on every list you can change since 12 November 2026): a long
    // press ticks that wish, in every view; then taps tick more, and the top bar offers Move to… and Delete. Back or ✕
    // stops. (Collab wishlists had a long-press menu until then; its Edit and Move to… are in the ⋮ sheet now.)
    // Another list opened: nothing selected.
    LaunchedEffect(Repo.openMine, Shared.active) { model.picked = emptySet() }
    val startSelect: (Wish) -> Unit = { w ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        model.picked = setOf(w.id)
    }
    val selecting = model.selecting
    // Drag to reorder (owner, 16 November 2026; ListDrag.kt): hold a wish and move your finger to drag it, or hold and
    // let go to select it as before.
    val drag = remember { DragState() }
    LaunchedEffect(Repo.openMine, Shared.active) { drag.dragIds = null }
    val holdSelect: (Wish) -> Unit = { w ->
        if (selecting) startSelect(w)
        else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); drag.holding = w }
    }
    BackHandler(enabled = selecting) { model.picked = emptySet() }
    SelectingFlag(selecting)
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
    // Pull down to refresh (Material 3's 2025 version, P19 2.191: the screen keeps the "refreshing" itself).
    val pull = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    if (refreshing) {
        LaunchedEffect(true) {
            // Asking every shop can take long (some are read in a hidden browser, one wish at a time), so the circle
            // shows for at most 2.5 seconds; the check goes on in the background, prices change on screen as they
            // come in, and a message says when it's done.
            val job = PriceRefresh.start(ctx, if (inShared) Shared.active else null) { msg -> snack.showToast(msg) }
            val finished = withTimeoutOrNull(2500) { job.join() } != null
            refreshing = false
            if (!finished) snack.showToast(tr("Checking prices…"), duration = SnackbarDuration.Short)
        }
    }
    // A shared wishlist is put together by the givers, for someone else: there is no "most wanted" (nobody can say
    // that for the person) and no "got it" (reserving is what counts). Wishes are shown without them.
    // Someone who loves a wish on one of their own lists knows the heart: its tip is done for good (LoveTip.kt).
    val anyLoved = !inShared && Repo.wishes.any { it.star }
    LaunchedEffect(anyLoved) { if (anyLoved && !LoveTip.done(ctx)) LoveTip.finish(ctx) }
    // Each list has its own sort, "My order" included (ListOrder.kt, owner 16 November 2026).
    val listKey = ListOrder.openKey()
    val chosenSort = ListOrder.sortFor(ctx, listKey)
    val sortMode = if (inShared && chosenSort == "star") "new" else chosenSort
    val res = if (inShared) Shared.reserved else emptyMap()
    val source = if (inShared) Repo.wishes.map { it.copy(star = false, got = false) } else Repo.wishes

    val sel = shownCategory(model.cat, Repo.cats)
    val query = model.query
    val sorted = shownWishes(source, sel, query, sortMode, ListOrder.order(ctx, listKey))
    // While dragging, the order on screen; wishes that arrive meanwhile go after it.
    val list = drag.dragIds?.let { ids ->
        val byId = sorted.associateBy { it.id }
        ids.mapNotNull { byId[it] } + sorted.filter { it.id !in ids }
    } ?: sorted
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
                model.cat = "All"; model.stopSearch()
            } else {
                WishFocus.target = null
                // Not on this list (any more): say so rather than leave you looking (owner, 20 November 2026). A
                // shared list may still be loading, so only on your own lists, which are on the phone.
                if (Shared.active == null) scope.launch { snack.showToast(tr("This wish is no longer on the list")) }
            }
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
                title = { ScreenTitle(if (model.picked.size == 1) tr("1 selected") else tr("%d selected", model.picked.size)) }, large = false,
                navigationIcon = { IconButton(onClick = { model.picked = emptySet() }) { Icon(AppIcons.Close, tr("Stop selecting")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    MoveBusy(Modifier.padding(end = 8.dp))
                    // Move to… and Delete are in the action bar at the bottom (2.209, Selecting.kt).
                    val all = list.map { it.id }.toSet()
                    if (!model.picked.containsAll(all)) TextButton(onClick = { model.picked = all }) { Text(tr("Select all")) }
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
                    IconButton(onClick = { if (model.searching) model.stopSearch() else model.searching = true }) {
                        Icon(AppIcons.Search, if (model.searching) tr("Close search") else tr("Search"))
                    }
                    // The settings of this list. Refreshing is pulling down on the list.
                    IconButton(onClick = onListSettings) { Icon(AppIcons.Settings, tr("Wishlist settings")) }
                    IconButton(onClick = { if (inShared) NameGate.ensure(onInvite) else NameGate.ensureListName { go(Deep.ShareCard(Repo.openMine)) } }) {
                        Icon(AppIcons.Share, if (inShared) tr("Invite or share") else tr("Share wishlist"))
                    }
                }
            )
        },
        // No floating "Add wish" button any more (owner, 2.112): the + in the bottom bar does the same.
        bottomBar = {
            if (selecting) SelectionBar(
                onMove = { MoveFlow.ask(model.picked); model.picked = emptySet() },
                onDelete = {
                    val ws = Repo.wishes.filter { it.id in model.picked }
                    model.picked = emptySet()
                    DeleteFlow.requestMany(ctx, ws)
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        // Behind the list: the blobs, glows or dots of the design (nothing in Material 3).
        Column(Modifier.padding(pad)) {
            if (inShared && Shared.gone) SharedGoneNotice()
            if (model.searching) SearchField(query, { model.query = it }) { model.stopSearch() }
            // The category and sort choices and the view buttons. Since 2.89 they scroll with the list, under the
            // cover photo (when chosen) and the summary card.
            // [live]: this copy may open the category menu (the list's own copy is hidden under the pinned one while
            // that one shows, and the menu must open only once).
            val filters: @Composable (Boolean) -> Unit = { live ->
                // The same row on every list (ListTools.kt, owner 12 November 2026).
                ListTools(
                    sel, Repo.cats, { c -> if (c == "All") Repo.wishes.size else Repo.wishes.count { it.category == c } }, { model.cat = it },
                    model.catMenu && live, { model.catMenu = it },
                    sortMode, !inShared, { model.setSort(listKey, it) },
                    view, { model.setView(it) },
                    onNewCategory = { model.catDlg = true },
                    withMine = true
                )
            }
            // The top that stays in sight while scrolling (owner, 2.111): the summary card (your own lists) and the
            // category, sort and view choices. The cover photo above it scrolls away.
            val hasCover = remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) != null }
            // No "Viewed 23 times" any more (owner, 15 November 2026: it shouldn't be there). The server's wl_view and
            // wl_view_count stay, unused, so old app versions keep working.
            // Reactions (2.142): your own lists show them (no reacting), gift lists let every giver react. On a gift list a
            // tapped "Reserved" label of someone else asks "Is it still being bought?" (2.142).
            val listCode = if (inShared) Shared.active else Repo.mine(ctx, Repo.openMine).code
            ReactionsLoader(listCode)
            val listDeco = listCode?.let { code ->
                WishDeco(code, canReact = inShared, onBadge = if (!inShared) null else ({ w ->
                    val c = ResCounts.of(code, w.id)
                    if (res[w.id] == false || (w.qty > 1 && c != null && c.full && c.my == 0)) model.askBought = w
                }))
            }
            model.askBought?.let { w ->
                AskDialog(Shared.active.orEmpty(), w, Occasions.ofOpen(ctx), onClose = { model.askBought = null }) { m -> scope.launch { snack.showToast(m) } }
            }
            // A tapped "X is free" or "Your reservation of X has ended" on a gift list (2.142): it opens on that wish.
            LaunchedEffect(places.askFocus, Shared.active, res) {
                val (code, id, reserve) = places.askFocus ?: return@LaunchedEffect
                if (!inShared || code != Shared.active) return@LaunchedEffect
                val w = Repo.wishes.firstOrNull { it.id == id && it.bin == 0L } ?: return@LaunchedEffect
                places.askFocus = null
                WishFocus.target = id
                if (reserve && res[id] == null) model.reserveNow = w
            }
            model.reserveNow?.let { w -> ReserveNowDialog(w, onReserve = { model.reserve(w, true) }, onClose = { model.reserveNow = null }) }
            val stickyTop: @Composable (Boolean) -> Unit = { live ->
                // More room between the summary and the choices (owner, 2.130: calmer, without making things smaller).
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The occasion shows in the summary card (owner, 2.124); on a gift list, or with the summary
                    // switched off, the card shows only the occasion.
                    val occ = Occasions.line(Occasions.ofOpen(ctx))
                    val counts = !inShared && Show["show_summary"]
                    if (counts || occ != null) ListSummaryCard(Repo.wishes, occasion = occ, counts = counts)
                    filters(live)
                }
            }
            // Pull down on the list to refresh it: the shops are asked for their prices now (and a shared list is
            // fetched again), so price changes show. This took the place of the refresh button in the top bar.
            Box(Modifier.fillMaxSize().clipToBounds().pullToRefresh(isRefreshing = refreshing, state = pull, onRefresh = { refreshing = true })) {
            if (list.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ListHeader(Repo.wishes, snack)
                    if (hasCover) Spacer(Modifier.height(10.dp))
                    stickyTop(true)
                    Text(
                        if (query.isNotBlank()) {
                            if (sel != "All") tr("No wishes match “%1\$s” in %2\$s.", query.trim(), catLabel(sel))
                            else tr("No wishes match “%s”.", query.trim())
                        } else tr("Nothing here yet. Tap +, or share something you love from any shop to Wishlist."),
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val gap = if (view == "list") 16.dp else 12.dp
                androidx.compose.runtime.CompositionLocalProvider(LocalWishDeco provides listDeco) {
                // The end of a hold: moved = the new order is kept (the list becomes "My order"), else the wish is
                // selected as before. The whole list order is saved, so wishes hidden by a category keep their place.
                fun endHold(moved: Boolean) {
                    val ids = drag.dragIds
                    if (moved && drag.dragArmed && ids != null) {
                        ListOrder.save(ctx, listKey, keptOrder(ids, sorted.map { it.id }, sortWishes(source, sortMode).map { it.id }))
                    } else drag.holding?.let { startSelect(it) }
                    drag.reset()
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                    modifier = Modifier.onGloballyPositioned { drag.gridBox = it }
                        .reorderGesture(drag, arrayOf(listKey, view, selecting), !selecting, { list }, gridState, scope) { endHold(it) },
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), // no floating button to keep clear of any more (2.112)
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    if (hasCover) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { ListHeader(Repo.wishes, snack) }
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { stickyTop(!pinned) }
                    // The heart explained once (LoveTip.kt, owner 16 November 2026), on your own lists.
                    if (!inShared && LoveTip.shows(ctx, Repo.wishes)) item(key = "lovetip", span = { GridItemSpan(maxLineSpan) }) {
                        LoveTipCard(ctx, Repo.wishes)
                    }
                    gridItems(list, key = { it.id }) { w -> ListWishItem(w, res[w.id], view, inShared, model, drag, holdSelect) }
                }
                }
            }
            // The pinned copy of the top, once the list's own copy starts to scroll away under it.
            if (pinned && list.isNotEmpty()) Box(
                Modifier.fillMaxWidth().background(cs.background).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .onSizeChanged { stickyHeight = it.height }
            ) { stickyTop(true) }
            // Only while pulling or refreshing: at rest the circle with the arrow must not show at all.
            if (pull.distanceFraction > 0f || refreshing) {
                PullToRefreshDefaults.Indicator(state = pull, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter))
            }
            }
        }
    }

    model.editing?.let { w -> key(w.id) { WishDialog(w, "") { model.editing = null } } }
    model.historyId?.let { id -> Repo.wishes.firstOrNull { it.id == id } }?.let { w ->
        PriceHistoryDialog(w) { model.historyId = null }
    }
}

