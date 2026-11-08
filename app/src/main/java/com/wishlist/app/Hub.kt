package com.wishlist.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/*
 * The dashboard (since 2.64): the app's start screen. At the top a picture you can make your own (the pencil), then
  * "My wishlists" (since 2.110 each section starts with the list opened last) with a + for a new one, and "Group wishlists" (lists you make
 * together with other givers, for someone else) with a + for a new one. Tapping a list opens it; Back returns here.
 */

/**
 * "Wishlist" at the top of Home, in the style's own way (2.97): Quiet bold and tight; Cyberpunk in capitals with a
 * glitch (the word twice more, shifted, in the accent and neon); chaos tilted with a hard pink shadow.
 */
@Composable
fun Wordmark() {
    val look = LocalLook.current
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = look.titleWeight)
    when (look.design) {
        "cyber" -> Box {
            val st = style.copy(letterSpacing = 3.sp)
            Text("WISHLIST", style = st, color = look.accent, modifier = Modifier.offset(x = 2.dp))
            Text("WISHLIST", style = st, color = look.blobs[0], modifier = Modifier.offset(x = (-2).dp))
            Text("WISHLIST", style = st, color = MaterialTheme.colorScheme.onBackground)
        }
        "chaos" -> Text(
            "Wishlist", color = MaterialTheme.colorScheme.onBackground,
            style = style.copy(shadow = androidx.compose.ui.graphics.Shadow(look.blobs[0], Offset(6f, 6f), 0f)),
            modifier = Modifier.rotate(-3f)
        )
        else -> Text("Wishlist", style = style, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * One list on the dashboard: your own ("m:<id>") or a group wishlist ("s:<code>"). [picture]: its most recently added
 * wish with a photo (none: the empty state or a gift). [addTo]: the "Save to" key, so "+ Add wish" starts on this list.
 */
internal class HubItem(
    val key: String, val name: String, val count: Int, val picture: String?, val group: Boolean,
    val addTo: String,
    // The newest wish photos (up to three): a collage on the block when there are three (owner, 7 November 2026).
    val pics: List<String> = listOfNotNull(picture),
    // The line under the name: "3 wishes" (null), or e.g. "4 members" for a group.
    val sub: String? = null,
    val open: () -> Unit
)

/** "1 wish" / "3 wishes", the line under a list's name on its block. */
fun wishesLabel(n: Int): String = if (n == 1) tr("1 wish") else tr("%d wishes", n)

/** The newest photos of [ws] (a wish's id is the moment it was added), at most three, for a block's collage. */
fun newestPics(ws: List<Wish>): List<String> =
    ws.filter { it.image.isNotBlank() }.sortedByDescending { it.id }.map { it.image }.distinct().take(3)

/** Opens the share card (HomeShare.kt) for one of your own lists, from Home or from the list itself. */
object ShareNav {
    var list by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    snack: SnackbarHostState,
    onOpenMine: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNewMine: () -> Unit,
    onNewGroup: () -> Unit,
    onProfile: () -> Unit = {},
    // "+ Add wish" on an empty list's block: the wish form, with that list chosen.
    onAddTo: (String) -> Unit = {},
    // Wishlist groups (2.108): open one, or make a new one.
    onOpenWishGroup: (String) -> Unit = {},
    onNewWishGroup: () -> Unit = {},
    // The Friends and Reserved tiles (owner, 2.134).
    onFriends: () -> Unit = {},
    onReserved: () -> Unit = {},
    // "Create list" (owner, 7 November 2026): the choice of My wishlist / Group / Gift list.
    onCreateList: () -> Unit = {},
    // "+ Add wish" on the Wishes tile when you have none.
    onAddWish: () -> Unit = {},
    // The gift exchange card in its season.
    onNewExchange: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    // Your own lists are read again whenever one changes (mineVersion) or the open one changes.
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    // The picture of a list: its newest wish photos (a collage when there are three; never the cover photo, owner).
    val mineItems = mine.map { d ->
        val n = if (d.name == "My wishlist") tr(d.name) else d.name
        val pics = newestPics(d.wishes)
        HubItem("m:" + d.id, n, d.wishes.size, pics.firstOrNull(), false, MyLists.saveKey(d.id, false), pics, wishesLabel(d.wishes.size)) { onOpenMine(d.id) }
    }
    // Gift lists: from the copy kept on the phone (it may be a little behind until the list is opened).
    val groupItems = remember(Shared.lists, Shared.content) {
        Shared.lists.map { l ->
            val ws = Shared.cachedWishes(ctx, l.code)
            val pics = newestPics(ws)
            HubItem("s:" + l.code, l.name, ws.size, pics.firstOrNull(), true, l.code, pics, wishesLabel(ws.size)) { onOpenGroup(l.code) }
        }
    }
    val scope = rememberCoroutineScope()
    // Long press on a list (see the end of this screen).
    var renaming by remember { mutableStateOf<HubItem?>(null) }
    var deleting by remember { mutableStateOf<HubItem?>(null) }
    var leaving by remember { mutableStateOf<HubItem?>(null) }
    var visibleOf by remember { mutableStateOf<HubItem?>(null) }
    var sharingCollab by remember { mutableStateOf<SharedList?>(null) }
    // Long press on a list: Edit name, Visible to (own lists, 2.105), then Delete wishlist (or Leave wishlist on a group wishlist someone else made).
    fun actionsOf(item: HubItem): List<Triple<String, Boolean, () -> Unit>> {
        val group = item.key.startsWith("s:")
        val code = item.key.substringAfter(':')
        val creator = group && Shared.lists.firstOrNull { it.code == code }?.owner?.isNotEmpty() == true
        val onEdit: () -> Unit = { renaming = item }
        val onLeave: () -> Unit = { leaving = item }
        val onDelete: () -> Unit = {
            if (!group && Repo.mineIds.size < 2) {
                scope.launch { snack.showToast(tr("This is your only wishlist. Make another one first.")) }
            } else {
                deleting = item
            }
        }
        val onVisible: () -> Unit = { visibleOf = item }
        // Share first on your own lists (owner, 7 November 2026): it opens the share card (HomeShare.kt).
        val onShare: () -> Unit = { NameGate.ensure { ShareNav.list = code } }
        val edit = Triple(tr("Edit name"), false, onEdit)
        // A collab wishlist's Share is its invitation: the link and Invite Friends (owner, 7 November 2026). No
        // "Visible to": only people with the invitation are on it.
        val onShareCollab: () -> Unit = { NameGate.ensure { sharingCollab = Shared.lists.firstOrNull { it.code == code } } }
        val shareCollab = Triple(tr("Share"), false, onShareCollab)
        return if (group && !creator) listOf(shareCollab, edit, Triple(tr("Leave wishlist"), true, onLeave))
        else if (group) listOf(shareCollab, edit, Triple(tr("Delete wishlist"), true, onDelete))
        else listOfNotNull(
            Triple(tr("Share"), false, onShare),
            edit,
            if (Config.enabled) Triple(tr("Visible to"), false, onVisible) else null,
            Triple(tr("Delete wishlist"), true, onDelete)
        )
    }

    // Your own lists, then your groups and gift lists (owner, 7 November 2026). On Home each section is one sideways
    // row: the 3 lists opened last, then a block that makes a new one; the heading's › opens all lists. Groups and gift
    // lists only show once you have one: Home starts with your own wishlists. On the "wishlists" page: every list,
    // two per row, only for opening.
    @Composable
    fun ListsSection(onHome: Boolean) {
        val more: (() -> Unit)? = if (onHome) ({ HubNav.page = "lists" }) else null
        HubTitle(tr("Your wishlists"), onMore = more)
        // In every section the list opened last comes first (owner, 2.110, instead of a "Recently opened" row).
        // "Create list" offers all three kinds: that is how people find groups and gift lists.
        HubBlocks(byRecent(mineItems), onHome, ::actionsOf, onAddTo, tr("Create list"), if (onHome) onCreateList else null, dots = true)

        // Your groups, right after your own lists (owner, 2.108).
        GroupsHomeSection(onHome, onOpenWishGroup, onNewWishGroup)

        if (groupItems.isNotEmpty()) {
            HubTitle(tr("Collab wishlists"), onMore = more)
            HubBlocks(byRecent(groupItems), onHome, ::actionsOf, onAddTo, tr("New collab wishlist"), if (onHome) onNewGroup else null, dots = true)
        }
    }

    val page = HubNav.page
    if (page != null) OverviewScreen(page, snack, { ListsSection(onHome = false) }, onOpenMine) else Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                // The app's wordmark instead of "Home" (2.97, in the style's own way).
                title = { Wordmark() },
                navigationIcon = { MenuSlot(back = false) },
                actions = { ProfileButton(onProfile) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().lookBackdrop(look).verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Your picture and name, and the numbers (2.89: the cover photo card is gone).
            ProfileSummary(onProfile, onFriends, onAddWish)

            // (The shops moved to their own tab, Discover, owner 7 November 2026.)
            ListsSection(onHome = true)

            // Gift exchange in its season (owner, 7 November 2026).
            SeasonCard(onNewExchange)
            // Gift exchanges (owner, 7 November 2026: out of the menu, onto Home): yours as blocks and a small "New gift
            // exchange" block, all year.
            if (Config.enabled) ExchangesSection(onNewExchange)

            // No "Price changes" here any more (owner, 2.95): price news is on Price watch and in Notifications.
        }
    }

    sharingCollab?.let { l -> androidx.compose.runtime.key(l.code) { SharedShareDialog(l) { sharingCollab = null } } }
    // "Visible to" (2.105, owner): the same three choices as in the list's settings, with radio buttons.
    visibleOf?.let { item ->
        AlertDialog(
            onDismissRequest = { visibleOf = null },
            title = { Text(tr("Visible to")) },
            text = {
                Column {
                    Text(
                        item.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    WhoChoices(item.key.substringAfter(':'), snack)
                }
            },
            confirmButton = { TextButton(onClick = { visibleOf = null }) { Text(tr("Done")) } }
        )
    }
    renaming?.let { item ->
        val group = item.key.startsWith("s:")
        val code = item.key.substringAfter(':')
        var n by remember(item.key) { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("Edit name")) },
            text = {
                OutlinedTextField(
                    n, { n = it.take(60) }, singleLine = true, label = { Text(tr("Name*")) },
                    keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = n.isNotBlank(), onClick = {
                    renaming = null
                    if (group) {
                        // Group wishlists are renamed for everyone on them.
                        Shared.open(ctx, code)
                        Shared.setName(ctx, n.trim())
                    } else Repo.renameMine(ctx, code, n)
                }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Cancel")) } }
        )
    }
    deleting?.let { item ->
        val group = item.key.startsWith("s:")
        val code = item.key.substringAfter(':')
        var busy by remember(item.key) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text(if (group) tr("Delete “%s” for everyone?", item.name) else tr("Delete “%s”?", item.name)) },
            text = {
                Text(
                    if (group) tr("It will be deleted for everyone, with all wishes and reservations. This can’t be undone.")
                    else tr("Deleting this wishlist will move all its wishes to the bin, where they can be restored within 30 days.") +
                        groupNote(ctx, code)
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val msg = try {
                            if (group) {
                                Shared.open(ctx, code)
                                Shared.deleteForEveryone(ctx)
                            } else {
                                // Its friends' link stops working first, then the list goes and its wishes to the bin.
                                val d = Repo.mine(ctx, code)
                                if (d.code != null) {
                                    try {
                                        Sync.unpublishStored(d)
                                    } catch (e: Exception) {
                                    }
                                }
                                Repo.deleteMine(ctx, code)
                            }
                            tr("“%s” was deleted", item.name)
                        } catch (e: Exception) {
                            tr("Couldn’t do that. Check your connection.")
                        }
                        busy = false
                        deleting = null
                        snack.showToast(msg)
                    }
                }) { Text(tr("Delete"), color = cs.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text(tr("Cancel")) } }
        )
    }
    leaving?.let { item ->
        val code = item.key.substringAfter(':')
        AlertDialog(
            onDismissRequest = { leaving = null },
            title = { Text(tr("Leave “%s”?", item.name)) },
            text = { Text(tr("It disappears from your lists. It stays for everyone else, and you can join again with the invite link.")) },
            confirmButton = { TextButton(onClick = { leaving = null; Shared.leave(ctx, code) }) { Text(tr("Leave")) } },
            dismissButton = { TextButton(onClick = { leaving = null }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
private fun ActionRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.bodyLarge,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp)
    )
}

/** One wish in "Price changes": a square picture, the old and the new price, and the change in percent. */
@Composable
private fun PriceChangeRow(w: Wish) {
    val look = LocalLook.current
    val id = w.id
    val old = w.oldPrice ?: return
    val now = w.price ?: return
    val down = now < old
    Row(
        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
            .clickable { HistoryNav.open = w.id }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sq = RoundedCornerShape(14.dp)
        Box(Modifier.size(64.dp).lookMat(look, sq, look.pictureBg(id, false)), contentAlignment = Alignment.Center) {
            if (w.image.isNotBlank()) {
                WishImage(w.image, Modifier.fillMaxSize(), ContentScale.Crop)
            } else PlaceholderIcon("gift", 26.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                w.label, style = MaterialTheme.typography.titleSmall, color = look.cardInk(id),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(tr("Old %s", money(old, w.currency)), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
                Text(
                    tr("New %s", money(now, w.currency)), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = look.cardInk(id), modifier = Modifier.padding(start = 12.dp)
                )
            }
            val pct = Math.round(kotlin.math.abs(now / old - 1) * 100).coerceAtLeast(1)
            Text(
                (if (down) "−" else "+") + pct + "%", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = if (down) look.drop else look.rise
            )
        }
    }
}

/**
 * A section title, with a small round + button when something can be added, and a › after the words when [onMore]
 * opens all of it (owner, 7 November 2026: "Your wishlists ›").
 */
@Composable
internal fun HubTitle(
    text: String, addLabel: String? = null, small: Boolean = false, line: String? = null, onAdd: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null
) {
    val look = LocalLook.current
    Column {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (small) 12.dp else 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).then(if (onMore != null) Modifier.clickable(onClickLabel = tr("See all"), onClick = onMore) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                fontWeight = look.titleWeight, color = MaterialTheme.colorScheme.onBackground
            )
            if (onMore != null) Icon(
                AppIcons.Chevron, null, tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 2.dp).size(28.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            Box(
                Modifier.size(30.dp).lookChip(look, on = true).clickable(onClickLabel = addLabel, onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Add, addLabel, tint = look.chipContent(true), modifier = Modifier.size(18.dp))
            }
        }
    }
    // A short line under each Home section that says what it is (owner, 2.128).
    if (line != null) Text(
        line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
    )
    }
}

/**
 * A section's blocks: on Home one sideways row with the 3 lists opened last and the "new" block (owner, 7 November
 * 2026); elsewhere every list, two per row.
 */
@Composable
internal fun HubBlocks(
    list: List<HubItem>, onHome: Boolean, actionsOf: (HubItem) -> List<Triple<String, Boolean, () -> Unit>>,
    onAddTo: (String) -> Unit, newLabel: String? = null, onNew: (() -> Unit)? = null, dots: Boolean = false
) {
    if (!onHome) {
        BlockGrid(list, false, actionsOf, onAddTo)
        return
    }
    // Two blocks and the edge of a third fit on the screen: the cut-off edge shows you can swipe.
    val w = ((LocalConfiguration.current.screenWidthDp - 16) / 2.3f).dp
    val shown = list.take(3)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shown.size, key = { shown[it].key }) { i ->
            ListBlock(shown[i], actionsOf(shown[i]), onAddTo, Modifier.width(w), small = false, dots = dots)
        }
        if (onNew != null) item(key = "new") { NewBlock(newLabel.orEmpty(), onNew, Modifier.width(w)) }
    }
}

/** Blocks two per row (the last one alone on its row when the number is odd). */
@Composable
internal fun BlockGrid(
    items: List<HubItem>, onHome: Boolean, actionsOf: (HubItem) -> List<Triple<String, Boolean, () -> Unit>>,
    onAddTo: (String) -> Unit,
    // "+ New …" as the last block (owner, 2.134): a new list appears right where you tapped.
    newLabel: String? = null, onNew: (() -> Unit)? = null
) {
    val cells: List<HubItem?> = items + (if (onNew != null) listOf(null) else emptyList())
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    if (item == null) NewBlock(newLabel.orEmpty(), onNew ?: {}, Modifier.weight(1f))
                    else ListBlock(item, if (onHome) actionsOf(item) else emptyList(), onAddTo, Modifier.weight(1f), small = false)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The dashed "+ New …" block at the end of a section on Home (owner, 2.134). */
@Composable
private fun NewBlock(label: String, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    // Smaller than a list (owner, 7 November 2026): a dashed square with its label, centred on the lists' pictures.
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = label, onClick = onClick).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(72.dp).clip(shape)
                    .drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            color = cs.outline, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Add, null, tint = cs.primary, modifier = Modifier.size(32.dp))
            }
            Text(
                label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.primary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * A list as a block (owner, 2.91; reworked 7 November 2026 after GoWish and Giftful): its newest wish photos filling
 * the square (a collage of three when it has three, else one photo), its name and "3 wishes" under it. An empty list
 * of yours shows only "+ Add wish" (the wish form with this list chosen); someone else's empty list says "Empty".
 * Tap opens the list; a long press, or the ⋮ next to the name ([dots]: your own lists on Home), opens the menu.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ListBlock(
    item: HubItem, actions: List<Triple<String, Boolean, () -> Unit>>, onAddTo: (String) -> Unit,
    modifier: Modifier, small: Boolean, dots: Boolean = false
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val id = item.key.hashCode().toLong()
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        val shape = look.cardShape(id, small = true)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).lookCard(look, id, shape)
                .combinedClickable(onClick = item.open, onLongClick = if (actions.isEmpty()) null else ({ menu = true }))
        ) {
            // The picture area: the theme's soft colour, the photos filling it.
            val mat = RoundedCornerShape(if (small) 12.dp else 16.dp)
            Box(
                Modifier.fillMaxSize().padding(if (small) 6.dp else 8.dp)
                    .lookMat(look, mat, look.pictureBg(id, item.picture != null), over = true).clip(mat),
                contentAlignment = Alignment.Center
            ) {
                val pics = item.pics
                when {
                    pics.size >= 3 -> Collage(pics)
                    item.picture != null -> WishImage(item.picture, Modifier.fillMaxSize(), ContentScale.Crop)
                    item.count == 0 && item.addTo.isNotEmpty() && !small -> Text(
                        "+ " + tr("Add wish"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = look.chipContent(true),
                        modifier = Modifier.lookChip(look, on = true).clickable { onAddTo(item.addTo) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    item.count == 0 && item.sub == null -> Text(
                        tr("Empty"), style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = look.cardMuted(id)
                    )
                    // Wishes, but none with a photo (or still loading).
                    else -> PlaceholderIcon(if (item.group) "group" else "gift", if (small) 26.dp else 40.dp)
                }
                // Who is on a collab wishlist (owner, 7 November 2026): small circles in the bottom right corner.
                if (item.key.startsWith("s:") && !small) {
                    val ctx = LocalContext.current
                    val code = item.key.substringAfter(':')
                    val ws = remember(code, Shared.content, item.count) { Shared.cachedWishes(ctx, code) }
                    PeopleCircles(code, ws, Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 2.dp, end = 2.dp)) {
                Text(
                    item.name, style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                // The count under the name (owner, 7 November 2026), not in a circle on the photo.
                if (!small) Text(
                    item.sub ?: wishesLabel(item.count), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                if (dots && actions.isNotEmpty()) IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(AppIcons.MoreVert, tr("More for %s", item.name), tint = cs.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { (label, danger, act) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (danger) cs.error else cs.onSurface) },
                            onClick = { menu = false; act() }
                        )
                    }
                }
            }
        }
    }
}

/** Three photos on a block: a big one on the left and two small ones on the right (owner, 7 November 2026). */
@Composable
internal fun Collage(pics: List<String>) {
    val gap = 2.dp
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        WishImage(pics[0], Modifier.weight(1.6f).fillMaxHeight(), ContentScale.Crop)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            WishImage(pics[1], Modifier.weight(1f).fillMaxWidth(), ContentScale.Crop)
            WishImage(pics[2], Modifier.weight(1f).fillMaxWidth(), ContentScale.Crop)
        }
    }
}

/**
 * "Secret Santa this year?" on Home from 1 November to 20 December (owner, 7 November 2026): Gift exchange is only in
 * the menu otherwise. Gone for the year after ✕, and while you are in an exchange or have an invitation.
 */
@Composable
private fun SeasonCard(onStart: () -> Unit) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val today = remember { java.time.LocalDate.now() }
    val inSeason = today.monthValue == 11 || (today.monthValue == 12 && today.dayOfMonth <= 20)
    if (!inSeason || !Config.enabled) return
    val prefs = remember { ctx.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE) }
    val key = "gx_card_" + today.year
    var hidden by remember { mutableStateOf(prefs.getBoolean(key, false)) }
    if (hidden || Exchanges.list.isNotEmpty() || Exchanges.invites.isNotEmpty()) return
    // Secret Santa colours (owner, 7 November 2026: "now it's just a grey block"): a deep red with light snow dots,
    // white words and a white pill to start. A drawn illustration, so its colours are fixed in every theme.
    val red = Color(0xFFB3261E)
    val deep = Color(0xFF6E1414)
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth().clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(red, deep)))
            .drawBehind {
                // Snow: fixed dots, nothing moves.
                val dots = listOf(0.08f to 0.18f, 0.22f to 0.72f, 0.35f to 0.12f, 0.5f to 0.85f, 0.62f to 0.3f,
                    0.74f to 0.68f, 0.86f to 0.15f, 0.93f to 0.55f, 0.15f to 0.45f, 0.44f to 0.5f)
                dots.forEachIndexed { k, (x, y) ->
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = (2 + k % 3).dp.toPx(), center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height))
                }
            }
            .clickable(onClickLabel = tr("Start a gift exchange"), onClick = onStart)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎅", fontSize = 40.sp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(tr("Secret Santa this year?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    tr("Draw names with family or friends."), style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .clickable(onClick = onStart).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎁 " + tr("Start a gift exchange"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = red)
                }
            }
        }
        IconButton(
            onClick = { hidden = true; prefs.edit().putBoolean(key, true).apply() },
            modifier = Modifier.align(Alignment.TopEnd)
        ) { Icon(AppIcons.Close, tr("Hide until next year"), tint = Color.White) }
    }
}

/** A new wishlist of your own: a name (required) and, optionally, the note for friends shown at the top of the list. */
@Composable
fun NewListDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var occ by remember { mutableStateOf<Occasion?>(null) }
    var occMissing by remember { mutableStateOf(false) }
    // Optional: wishes from your other lists, copied in when the list is made.
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("New wishlist")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                    label = { Text(tr("Wishlist name*")) }, singleLine = true, isError = missing,
                    supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                OutlinedTextField(
                    notes, { notes = it.take(ABOUT_MAX) },
                    // "Description" (owner, 2.125; it was "Note for friends", and "Notes" before 2.113).
                    label = { Text(tr("Description")) },
                    supportingText = { Text(tr("Shown at the top of your wishlist.")) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                // The occasion (owner, 2.124): pick one first; only then its date is asked.
                Text(tr("Occasion"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OccasionPicker(gift = false, value = occ) { occ = it; occMissing = false }
                if (occMissing) Text(
                    if (occ?.kind == "own" && occ?.name.isNullOrBlank()) tr("Give it a name and a date.") else tr("Choose the date."),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                if (canCopy) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add wishes from your other lists"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            // No "Optional" (owner, 2.125: it suggested the rest isn't optional).
                            if (picked.isNotEmpty()) Text(
                                if (picked.size == 1) tr("1 wish chosen") else tr("%d wishes chosen", picked.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) missing = true
                else if (!Occasions.complete(occ)) occMissing = true
                else {
                    val id = Repo.createMine(ctx, name, notes)
                    occ?.let { Occasions.setMine(ctx, id, it) }
                    Repo.copyInto(ctx, id, picked)
                    // New lists start on the choice in App settings (owner, 7 November 2026; default: anyone with the link).
                    ListVisibility.startShared(ctx, id)
                    onCreated(id)
                }
            }) { Text(tr("Create")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
    if (picking) {
        CopyWishesDialog(null, onDone = { picked = it; picking = false }, onClose = { picking = false })
    }
}

/** Lists in the order they were last opened, newest first; never-opened ones keep their own order after them. */
internal fun byRecent(items: List<HubItem>): List<HubItem> = items.sortedByDescending { Repo.recent[it.key] ?: 0L }

/** "Gift exchanges ›" on Home: your exchanges (tap: open it) and "New gift exchange"; the › opens the exchanges page. */
@Composable
private fun ExchangesSection(onNew: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val list = Exchanges.list
    Spacer(Modifier.height(8.dp))
    HubTitle(tr("Gift exchanges"), onMore = { ExchangeNav.open = null; ExchangeNav.show = true })
    val w = ((LocalConfiguration.current.screenWidthDp - 16) / 2.3f).dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list.size, key = { list[it].id }) { i ->
            val x = list[i]
            Column(Modifier.width(w).clip(RoundedCornerShape(16.dp)).clickable { ExchangeNav.openExchange(x.id) }) {
                Box {
                    GxCover(x.theme, Exchanges.photos[x.id], Modifier.fillMaxWidth().aspectRatio(1f).clip(look.cardShape(x.id.hashCode().toLong(), small = true)))
                    if (x.me.unreadGiftee + x.me.unreadSanta > 0) RedDot(Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                Text(
                    x.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                )
                Text(
                    Exchanges.countdown(x) ?: if (!x.drawn) tr("Names not drawn yet") else tr("Names drawn"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
        item(key = "new") { NewBlock(tr("New gift exchange"), onNew, Modifier.width(w)) }
    }
}
