package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * The top of Home (2.89, owner): your picture with your name under it, and three numbers next to it: wishlists,
 * wishes and top wishes. Each number opens a page: all your wishlists (your own, then group wishlists), all wishes of
 * your own lists, or only the top wishes (with a star to take a wish off again).
 */

/**
 * A wish opened from "All your wishes" or "Top wishes" (2.91), or a notification about it (2.161): the list scrolls to [target] and lets it glow softly
 * twice ([GlowBox]).
 */
object WishFocus {
    var target by mutableStateOf<Long?>(null)
    /** The wish that glows and a counter, so the same wish can glow again. */
    var glowing by mutableStateOf<Pair<Long, Int>?>(null)
        private set

    /**
     * A notification about one wish on your own list or a collab wishlist (a price change, a gift moved, …), tapped on
     * the Notifications page or as a push: its list opens on that wish (owner, 8 November 2026, 2.161). Friends'
     * lists load from the server first, so they open at the top; "Still buying …?" news has its own (AskNav).
     */
    fun forNote(listKey: String, wishId: Long, kind: String) {
        if (wishId <= 0L || kind.startsWith("ask")) return
        if (listKey.startsWith("m:") || listKey.startsWith("s:")) target = wishId
    }

    fun glow(id: Long) { glowing = id to ((glowing?.second ?: 0) + 1) }

    /** The glow of [id] is over: the card goes back to how it was. */
    fun done(id: Long) { if (glowing?.first == id) glowing = null }
}

/**
 * Around a wish card on a list: a soft glow in the theme's colour that pulses twice and fades, when [WishFocus] says
 * so. One smooth wave (up, down, up, down) with no stops between (2.94: in 2.93 the steps each slowed down at their
 * end, so the last fade seemed to pause). The card is always inside the same Box: only the glow comes and goes, so
 * the card is never rebuilt and its photo doesn't flash (it did in 2.93). When it is over, or stopped halfway,
 * [WishFocus] forgets it.
 */
@Composable
fun GlowBox(id: Long, small: Boolean, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val col = MaterialTheme.colorScheme.primary
    val p = remember { Animatable(0f) }
    val n = WishFocus.glowing?.takeIf { it.first == id }?.second ?: 0
    LaunchedEffect(n) {
        if (n == 0) return@LaunchedEffect
        try {
            p.snapTo(0f)
            p.animateTo(1f, tween(1800, easing = LinearEasing))
        } finally {
            // No suspending here (it may be a cancellation): forgetting it is enough, lit is 0 then.
            WishFocus.done(id)
        }
    }
    // Two pulses: 0 → 1 → 0 → 1 → 0, as a smooth wave.
    val lit = if (n == 0) 0f else ((1f - kotlin.math.cos(4f * Math.PI.toFloat() * p.value)) / 2f)
    val shape = look.cardShape(id, small)
    Box(
        if (lit > 0.01f) Modifier
            .shadow((18 * lit).dp, shape, clip = false, ambientColor = col, spotColor = col)
            .border(3.dp, col.copy(alpha = 0.8f * lit), shape)
        else Modifier
    ) { content() }
}


/**
 * A wish with the lists it is on (your own). A wish copied into other lists keeps its id, so it is one wish: counted
 * and shown once, with every list in "Added to …" (owner, 2.90). Top wish when any of its copies is.
 */
private data class Placed(val w: Wish, val listIds: List<String>, val listNames: List<String>) {
    val starred: Boolean get() = w.star
}

private fun listLabel(name: String) = if (name == "My wishlist") tr(name) else name

@Composable
private fun placedWishes(): List<Placed> {
    val ctx = LocalContext.current
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    return mine.flatMap { d -> d.wishes.map { Triple(it, d.id, listLabel(d.name)) } }
        .groupBy { it.first.id }
        .map { (_, copies) ->
            val w = copies.first().first.copy(star = copies.any { it.first.star })
            Placed(w, copies.map { it.second }, copies.map { it.third })
        }
        .sortedByDescending { it.w.id }
}

/**
 * Your picture on the left; next to it your name in a heading size and under it the three tiles (owner, 7 November
 * 2026): Friends (the Friends tab), Wishes (all your wishes) and Total value (what your own wishlists still wish for,
 * collab wishlists left out; only a number, no tap). At 0 a tile invites: "+ Add friends", "+ Add wish".
 */
@Composable
fun ProfileSummary(onProfile: () -> Unit, onFriends: () -> Unit = {}, onAddWish: () -> Unit = {}) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    Friends.load(ctx)
    val all = placedWishes()
    val friends = Friends.state?.friends?.size ?: 0
    val total = homeTotal(all.map { it.w })
    val look = LocalLook.current
    // One soft card in the style's own look around you and your numbers (owner, 7 November 2026), set apart from the
    // lists below; inside it the numbers are plain, with thin lines between them (no cards in a card).
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth()
            .lookPeek(look).lookCard(look, 5L, look.cardShape(5L, small = true)).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = tr("Profile"), onClick = onProfile)) {
            Avatar(Friends.myPhoto, Shared.lastName, 72.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.titleLarge,
                fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, color = look.cardInk(5L),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onProfile).padding(horizontal = 2.dp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                val line = look.cardMuted(5L).copy(alpha = 0.3f)
                Stat(friends.toString(), friends == 0, if (friends == 1) tr("Friend") else tr("Friends"), Modifier.weight(1f), if (Config.enabled) tr("Add friends") else null, onFriends, onFriends, plain = true)
                Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(line))
                Stat(all.size.toString(), all.isEmpty(), if (all.size == 1) tr("Wish") else tr("Wishes"), Modifier.weight(1f), tr("Add wish"), onAddWish, onClick = { places.homePage = "wishes" }, plain = true)
                Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(line))
                Stat(total, false, tr("Total value"), Modifier.weight(1.3f), onClick = null, plain = true)
            }
        }
    }
}

/**
 * The small tile behind a number (owner, 2.96: "separate tiles, but subtle"). Quiet: the card colour, a little see-
 * through, no shadow, a faint edge in dark. The other styles (owner, 2.103: the tiles must follow the theme): a small
 * version of their own card, with its lines, hard shadow, cut corners or raised neumorphic shape.
 */
@Composable
fun Modifier.statTile(): Modifier {
    val look = LocalLook.current
    return when (look.design) {
        "chaos" -> lookSurface(look, RoundedCornerShape(14.dp), look.cardFill(5L), depth = 3.dp)
        "quiet" -> {
            val shape = RoundedCornerShape(12.dp)
            clip(shape).background(look.cardFill(5L).copy(alpha = 0.75f))
                .then(if (look.dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), shape) else Modifier)
        }
        // The other styles: a small version of their own card (its shape, line and shadow).
        else -> lookSurface(look, MaterialTheme.shapes.medium, look.cardFill(5L), depth = 3.dp)
    }
}

/**
 * One tile: the number (or amount) and its label; at 0 ([zero]) with an [invite], a "+" and the invite in the accent
 * colour instead. [onClick] null: only shown, not tappable (Total value).
 */
@Composable
private fun Stat(
    value: String, zero: Boolean, label: String, modifier: Modifier = Modifier, invite: String? = null, onInvite: () -> Unit = {},
    onClick: (() -> Unit)?,
    // Inside the profile card: no tile of its own (owner, 7 November 2026).
    plain: Boolean = false
) {
    val look = LocalLook.current
    val inviting = zero && invite != null
    val tap = if (inviting) onInvite else onClick
    Column(
        modifier.then(if (plain) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier.statTile())
            .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The tile's own ink (dark on the bright cards of some styles) and the style's display font for the number.
        val ink = if (inviting) MaterialTheme.colorScheme.primary else look.cardInk(5L)
        Text(
            if (inviting) "+" else value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            fontFamily = look.display, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            if (inviting) invite!! else label, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (inviting) FontWeight.SemiBold else null,
            color = if (inviting) ink else look.cardMuted(5L), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

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
    var unstarring by remember { mutableStateOf<Placed?>(null) }
    // Like any list (owner, 12 November 2026): a long press selects, the ⋮ opens the wish's sheet.
    var picked by remember(page) { mutableStateOf(setOf<Long>()) }
    val selecting = picked.isNotEmpty()
    BackHandler(enabled = selecting) { picked = emptySet() }
    var editing by remember { mutableStateOf<Wish?>(null) }
    var history by remember { mutableStateOf<Wish?>(null) }
    // "From which list?" for Move to… or Delete, when the wishes are on more than one of your lists.
    var fromAsk by remember { mutableStateOf<Pair<String, Set<Long>>?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val all = placedWishes()
    /** Makes [list] the open one, so the usual actions (and their Undo) work on the wish there. */
    fun focus(list: String) { Shared.close(); Repo.openMine(ctx, list) }
    /** Moves or deletes [ids] from [list], or (null, Delete only) from every list they are on. */
    fun act(action: String, ids: Set<Long>, list: String?) {
        if (action == "move" && list != null) {
            focus(list)
            MoveFlow.ask(ids.filter { id -> Repo.myWishes.any { it.id == id } }.toSet())
        } else if (action == "delete") {
            val byList = if (list != null) mapOf(list to ids)
            else ids.groupBy { Repo.originalOf(ctx, it) ?: Repo.openMine }.mapValues { it.value.toSet() }
            DeleteFlow.requestFrom(ctx, byList)
        }
    }
    /** Move to… or Delete: asks "From which list?" only when the wishes are on more than one list. */
    fun start(action: String, ids: Set<Long>) {
        val lists = all.filter { it.w.id in ids }.flatMap { it.listIds }.distinct()
        if (lists.size <= 1) act(action, ids, lists.firstOrNull()) else fromAsk = action to ids
    }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            if (selecting) TopAppBar(
                title = { ScreenTitle(if (picked.size == 1) tr("1 selected") else tr("%d selected", picked.size)) },
                navigationIcon = { IconButton(onClick = { picked = emptySet() }) { Icon(AppIcons.Close, tr("Stop selecting")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    MoveBusy(Modifier.padding(end = 8.dp))
                    Button(onClick = { start("move", picked); picked = emptySet() }) { Text(tr("Move to…")) }
                    IconButton(onClick = { start("delete", picked); picked = emptySet() }) { Icon(AppIcons.Delete, tr("Delete selected")) }
                }
            ) else TopAppBar(
                title = {
                    ScreenTitle(when (page) { "lists" -> tr("Wishlists"); "top" -> tr("Top wishes"); else -> tr("All your wishes") })
                },
                navigationIcon = { IconButton(onClick = { places.homePage = null }) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = page != "top", onClick = { places.homePage = "wishes" }, label = { Text(tr("All wishes")) }
            )
            FilterChip(
                selected = page == "top", onClick = { places.homePage = "top" }, label = { Text(tr("Top wishes") + " ★") }
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    if (page == "top") tr("No top wishes yet. Tap the star on a wish to make it a top wish.")
                    else tr("No wishes yet. Tap + at the bottom to add your first wish."),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
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
                Selectable(selecting, p.w.id in picked, { picked = if (p.w.id in picked) picked - p.w.id else picked + p.w.id }) {
                    WishLine(
                        p,
                        // Opens the list, scrolls to the wish and lets it glow (WishFocus).
                        onOpen = { WishFocus.target = p.w.id; onOpenMine(p.listIds.first()) },
                        onUnstar = if (page == "top") ({ unstarring = p }) else null,
                        onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); picked = setOf(p.w.id) },
                        // The same sheet as on every list; its actions work on the list that has the original (its
                        // copies follow, owner: "every copy, everywhere").
                        actions = WishActions(
                            onEdit = { focus(Repo.originalOf(ctx, p.w.id) ?: p.listIds.first()); editing = Repo.myWishes.firstOrNull { it.id == p.w.id } },
                            onMove = { start("move", setOf(p.w.id)) },
                            own = true,
                            onHistory = if (p.w.url.isBlank()) null else ({ history = p.w }),
                            everywhere = Everywhere(
                                p.listIds,
                                setPrivate = { on -> focus(Repo.originalOf(ctx, p.w.id) ?: p.listIds.first()); Repo.myWishes.firstOrNull { it.id == p.w.id }?.let { PrivateFlow.set(ctx, it, on) } },
                                setOwned = { on -> focus(Repo.originalOf(ctx, p.w.id) ?: p.listIds.first()); Repo.myWishes.firstOrNull { it.id == p.w.id }?.takeIf { it.got != on }?.let { OwnedFlow.toggle(ctx, it) } },
                                received = { ReceivedFlow.everywhere(ctx, p.w.id) }
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
    unstarring?.let { p ->
        LaunchedEffect(p) {
            fun star(on: Boolean) = p.listIds.forEach { id ->
                // Every copy of the wish, on every list it is on.
                Repo.updateMine(ctx, id) { ws -> ws.map { if (it.id == p.w.id) it.copy(star = on) else it } }
            }
            star(false)
            val r = snack.showToast(tr("Removed from top wishes"), actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) star(true)
            // Only now: clearing it earlier would end this effect before the message shows.
            unstarring = null
        }
    }
    editing?.let { w -> androidx.compose.runtime.key(w.id) { WishDialog(w, "") { editing = null } } }
    history?.let { w -> PriceHistoryDialog(w) { history = null } }
    fromAsk?.let { (action, ids) -> FromWhichListDialog(action, ids, all, onPick = { l -> fromAsk = null; act(action, ids, l) }) { fromAsk = null } }
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
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (action == "move") tr("Move from which list?") else tr("Delete from which list?")) },
        text = {
            Column {
                Text(
                    if (chosen.size == 1) tr("This wish is on %1\$d of your wishlists: %2\$s.", lists.size, lists.joinToString(", ") { Repo.mineName(it) })
                    else tr("These wishes are on %d of your wishlists.", lists.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                lists.forEach { l ->
                    SheetRow({ Icon(AppIcons.Gift, null, tint = cs.onSurfaceVariant) }, Repo.mineName(l), { onPick(l) })
                }
                if (action == "delete") SheetRow({ Icon(AppIcons.Delete, null, tint = cs.error) }, tr("All lists"), { onPick(null) })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
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
