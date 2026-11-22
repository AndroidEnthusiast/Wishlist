package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* The pages behind Home's numbers (see Overview.kt): All your wishes and Wishes you love. What they hold and change is
 * OverviewState (OverviewModel.kt). */

/** The page behind one of the numbers. Back (arrow or the phone's Back) returns to Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    page: String, snack: SnackbarHostState, lists: @Composable () -> Unit,
    onOpenMine: (String) -> Unit
) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    BackHandler { places.homePage = null }
    // What is selected and open, and what a tap changes (OverviewState in OverviewModel.kt).
    val st = remember { OverviewState(ctx) }
    // The other page starts without a selection (as before); an Undo still on screen keeps going.
    var shownPage by remember { mutableStateOf(page) }
    if (shownPage != page) { shownPage = page; st.picked = emptySet() }
    val selecting = st.selecting
    BackHandler(enabled = selecting) { st.picked = emptySet() }
    SelectingFlag(selecting)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val all = placedWishes()
    fun start(action: String, ids: Set<Long>) = st.start(action, ids, all)
    Scaffold(
        containerColor = cs.background,
        topBar = {
            if (selecting) TopAppBar(
                title = { ScreenTitle(if (st.picked.size == 1) tr("1 selected") else tr("%d selected", st.picked.size)) }, large = false,
                navigationIcon = { IconButton(onClick = { st.picked = emptySet() }) { Icon(AppIcons.Close, tr("Stop selecting")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    MoveBusy(Modifier.padding(end = 8.dp))
                    // Move to… and Delete are in the action bar at the bottom (2.209, Selecting.kt).
                    val ids = (if (page == "top") all.filter { it.starred } else all).map { it.w.id }.toSet()
                    if (!st.picked.containsAll(ids)) TextButton(onClick = { st.picked = ids }) { Text(tr("Select all")) }
                }
            ) else TopAppBar(
                title = {
                    ScreenTitle(when (page) { "lists" -> tr("Wishlists"); "top" -> tr("Wishes you love"); else -> tr("All your wishes") })
                },
                navigationIcon = { IconButton(onClick = { places.homePage = null }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = {
            if (selecting) SelectionBar(
                onMove = { start("move", st.picked); st.picked = emptySet() },
                onDelete = { start("delete", st.picked); st.picked = emptySet() }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (page == "lists") {
            Column(
                Modifier.padding(pad).fillMaxSize()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp)
            ) { lists() }
            return@Scaffold
        }
        val shown = if (page == "top") all.filter { it.starred } else all
        Column(Modifier.padding(pad).fillMaxSize()) {
        // All wishes or only the top wishes (owner, 7 November 2026: the Top wishes page is reached this way).
        // Quiet (2.207): a segmented switch; chaos keeps its two chips.
        if (look.design == "quiet") SegmentedTabs(
            listOf(tr("All wishes"), tr("Wishes you love")), if (page == "top") 1 else 0,
            { places.homePage = if (it == 1) "top" else "wishes" }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) else Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = page != "top", onClick = { places.homePage = "wishes" }, label = { Text(tr("All wishes")) }
            )
            FilterChip(
                selected = page == "top", onClick = { places.homePage = "top" }, label = { Text(tr("Wishes you love")) }
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (page == "top") EmptyState(
                    AppIcons.FavoriteLine, tr("Nothing here yet. Tap the heart on the wishes you’d love most."),
                    tr("Show all wishes"), { places.homePage = "wishes" }
                ) else EmptyState(
                    AppIcons.Gift, tr("Nothing here yet. Tap + to add the first thing you’d love."),
                    tr("Open my wishlist"), { onOpenMine(Repo.mineIds.first()) }
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A short line under the title (owner, 2.91); Top wishes keeps its gold label above it.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    if (page == "top") MostWantedBadge(showLabel = true)
                    Text(
                        if (page == "top") tr("The wishes you’d love most, from all your wishlists.")
                        else tr("Every wish from all your wishlists, in one place."),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
            }
            items(shown, key = { it.w.id }) { p ->
                Selectable(selecting, p.w.id in st.picked, { st.toggle(p.w.id) }) {
                    WishLine(
                        p,
                        // Opens the list, scrolls to the wish and lets it glow (WishFocus).
                        onOpen = { WishFocus.target = p.w.id; onOpenMine(p.listIds.first()) },
                        onUnstar = if (page == "top") ({ st.unstarring = p }) else null,
                        onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); st.picked = setOf(p.w.id) },
                        // The same sheet as on every list; its actions work on the list that has the original (its
                        // copies follow, owner: "every copy, everywhere").
                        actions = WishActions(
                            onEdit = { st.edit(p) },
                            onMove = { start("move", setOf(p.w.id)) },
                            own = true,
                            onHistory = if (p.w.url.isBlank()) null else ({ st.history = p.w }),
                            everywhere = Everywhere(
                                p.listIds,
                                setPrivate = { on -> st.setPrivate(p, on) },
                                setOwned = { on -> st.setOwned(p, on) },
                                received = { st.received(p) }
                            )
                        )
                    )
                }
            }
        }
        }
    }
    // Taking a top wish off happens at once with Undo (owner, 12 November 2026: the same rule as deleting a wish;
    // until then it was asked first, 2.90).
    st.unstarring?.let { p ->
        LaunchedEffect(p) {
            st.star(p, false)
            val r = snack.showToast(tr("Removed from wishes you love"), actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) st.star(p, true)
            // Only now: clearing it earlier would end this effect before the message shows.
            st.unstarring = null
        }
    }
    st.editing?.let { w -> androidx.compose.runtime.key(w.id) { WishDialog(w, "") { st.editing = null } } }
    st.history?.let { w -> PriceHistoryDialog(w) { st.history = null } }
    st.fromAsk?.let { (action, ids) -> FromWhichListDialog(action, ids, all, onPick = { l -> st.fromAsk = null; st.act(action, ids, l) }) { st.fromAsk = null } }
}

/**
 * "From which list?" (owner, 12 November 2026): the wishes are on more than one of your lists, and it says so, so
 * the question makes sense. Delete also offers "All lists" (the originals go, their copies follow).
 */
@Composable
private fun FromWhichListDialog(action: String, ids: Set<Long>, all: List<Placed>, onPick: (String?) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val chosen = all.filter { it.w.id in ids }
    val lists = chosen.flatMap { it.listIds }.distinct()
    // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt).
    PickSheet(
        if (action == "move") tr("Move from which list?") else tr("Delete from which list?"), onClose = onClose,
        top = { MovingWishes(ids, chosen.map { it.w }) },
        note = if (chosen.size == 1) tr("This wish is on %1\$d of your wishlists: %2\$s.", lists.size, lists.joinToString(", ") { Repo.mineName(it) })
        else tr("These wishes are on %d of your wishlists.", lists.size)
    ) {
        SheetGroup(lists.map { l -> { SheetRow({ Icon(AppIcons.ListIcon, null, tint = cs.onSurfaceVariant) }, Repo.mineName(l), { onPick(l) }) } })
        if (action == "delete") SheetGroup(listOf { SheetRow({ Icon(AppIcons.Delete, null, tint = cs.error) }, tr("All lists"), { onPick(null) }, danger = true) })
    }
}

/**
 * One wish: a small picture, its name with "Added to …" under it, the price, (top wishes) the star and the ⋮ with the
 * wish's sheet. A tap opens its list; a long press starts selecting (owner, 12 November 2026).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WishLine(p: Placed, onOpen: () -> Unit, onUnstar: (() -> Unit)?, onLongPress: () -> Unit, actions: WishActions) {
    val look = LocalLook.current
    val w = p.w
    val id = w.id
    var sheet by remember { mutableStateOf(false) }
    if (sheet) WishSheet(w, actions) { sheet = false }
    Row(
        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 0.dp),
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
                tr("Added to %s", p.listNames.joinToString(", ")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = look.cardMuted(id), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        w.price?.let {
            Text(money(it, w.currency), style = MaterialTheme.typography.titleSmall, fontWeight = look.priceWeight, color = look.cardInk(id))
        }
        if (onUnstar != null) {
            StarToggle(true, onUnstar, Modifier.padding(horizontal = 8.dp))
        }
        IconButton(onClick = { sheet = true }) { Icon(AppIcons.MoreVert, tr("More"), tint = look.cardMuted(id)) }
    }
}
