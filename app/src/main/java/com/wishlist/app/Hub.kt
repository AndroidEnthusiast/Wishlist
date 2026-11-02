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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * "Recently opened" (swipe sideways), "My wishlists" with a + for a new one, and "Group wishlists" (lists you make
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
    val addTo: String, val open: () -> Unit
)

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
    onAddTo: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    // Your own lists are read again whenever one changes (mineVersion) or the open one changes.
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    // The picture of a list: its most recently added wish that has one (a wish's id is the moment it was added).
    fun newest(ws: List<Wish>) = ws.filter { it.image.isNotBlank() }.maxByOrNull { it.id }?.image
    val mineItems = mine.map { d ->
        val n = if (d.name == "My wishlist") tr(d.name) else d.name
        HubItem("m:" + d.id, n, d.wishes.size, newest(d.wishes), false, MyLists.saveKey(d.id, false)) { onOpenMine(d.id) }
    }
    // Group wishlists: from the copy kept on the phone (it may be a little behind until the list is opened).
    val groupItems = remember(Shared.lists, Shared.content) {
        Shared.lists.map { l ->
            val ws = Shared.cachedWishes(ctx, l.code)
            HubItem("s:" + l.code, l.name, ws.size, newest(ws), true, l.code) { onOpenGroup(l.code) }
        }
    }
    val all = (mineItems + groupItems).associateBy { it.key }
    val recentItems = Repo.recent.entries.sortedByDescending { it.value }.mapNotNull { all[it.key] }.take(10)
    val scope = rememberCoroutineScope()
    // Long press on a list (see the end of this screen).
    var renaming by remember { mutableStateOf<HubItem?>(null) }
    var deleting by remember { mutableStateOf<HubItem?>(null) }
    var leaving by remember { mutableStateOf<HubItem?>(null) }
    // Long press on a list: Edit name, then Delete wishlist (or Leave wishlist on a group wishlist someone else made).
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
        val edit = Triple(tr("Edit name"), false, onEdit)
        return if (group && !creator) listOf(edit, Triple(tr("Leave wishlist"), true, onLeave))
        else listOf(edit, Triple(tr("Delete wishlist"), true, onDelete))
    }

    // Your own lists, then the group wishlists, as blocks two per row (owner, 2.91). On Home with the + buttons and
    // the long-press menu; on the "wishlists" page only for opening lists (owner: new lists are made from Home and
    // the bottom bar).
    @Composable
    fun ListsSection(onHome: Boolean) {
        HubTitle(tr("My wishlists"), addLabel = tr("New wishlist"), onAdd = if (onHome) onNewMine else null)
        BlockGrid(mineItems, onHome, ::actionsOf, onAddTo)

        HubTitle(tr("Gift lists"), addLabel = tr("New gift list"), onAdd = if (onHome) onNewGroup else null)
        if (groupItems.isEmpty()) {
            Text(
                tr("Make a wishlist with other gift givers and surprise someone with a gift they’ll love."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        BlockGrid(groupItems, onHome, ::actionsOf, onAddTo)
    }

    val page = HubNav.page
    if (page != null) OverviewScreen(page, snack, { ListsSection(onHome = false) }, onOpenMine) else Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                // The app's wordmark instead of "Home" (2.97, in the style's own way).
                title = { Wordmark() },
                navigationIcon = { MenuSlot() },
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
            ProfileSummary(onProfile)

            if (recentItems.isNotEmpty()) {
                HubTitle(tr("Recently opened"), small = true)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentItems.size) { i -> RecentCard(recentItems[i], onAddTo) }
                }
            }

            ListsSection(onHome = true)

            // No "Price changes" here any more (owner, 2.95): price news is on Price watch and in Notifications.
        }
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
                    if (group) tr("Everyone on this gift list will lose it, with all its wishes and reservations. This can’t be undone.")
                    else tr("The wishlist will be deleted and its wishes moved to the bin. You can restore them there for 30 days.")
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
                w.title, style = MaterialTheme.typography.titleSmall, color = look.cardInk(id),
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
 * What all wishes with a price on a list add up to, for example "€245.50". Wishes in different currencies are added
 * up per currency ("€120.00 + $30.00"). Null when no wish has a price.
 */
fun totalValue(wishes: List<Wish>): String? {
    val sums = wishes.filter { it.price != null }.groupBy { it.currency }
        .map { (cur, ws) -> cur to ws.sumOf { it.price ?: 0.0 } }
    if (sums.isEmpty()) return null
    return sums.sortedByDescending { it.second }.joinToString(" + ") { (cur, sum) -> money(sum, cur) }
}

/** A section title, with a small round + button when something can be added. */
@Composable
private fun HubTitle(text: String, addLabel: String? = null, small: Boolean = false, onAdd: (() -> Unit)? = null) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (small) 12.dp else 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = look.titleWeight,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            Box(
                Modifier.size(30.dp).lookChip(look, on = true).clickable(onClickLabel = addLabel, onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Add, addLabel, tint = look.chipContent(true), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Blocks two per row (the last one alone on its row when the number is odd). */
@Composable
internal fun BlockGrid(
    items: List<HubItem>, onHome: Boolean, actionsOf: (HubItem) -> List<Triple<String, Boolean, () -> Unit>>,
    onAddTo: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    ListBlock(item, if (onHome) actionsOf(item) else emptyList(), onAddTo, Modifier.weight(1f), small = false)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A wishlist as a block (owner, 2.91, after GoWish's home): a square with the newest wish's photo (on the theme's
 * soft colour, like every product photo) and the number of wishes in a white circle top right; its name under it.
 * An empty list says "Empty" with "+ Add wish", which opens the wish form with this list chosen. Tap opens the list;
 * a long press opens the small menu ([actions], Home only).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ListBlock(
    item: HubItem, actions: List<Triple<String, Boolean, () -> Unit>>, onAddTo: (String) -> Unit,
    modifier: Modifier, small: Boolean
) {
    val look = LocalLook.current
    val id = item.key.hashCode().toLong()
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.forEach { (label, danger, act) ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                        onClick = { menu = false; act() }
                    )
                }
            }
            val shape = look.cardShape(id, small = true)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).lookCard(look, id, shape)
                    .combinedClickable(onClick = item.open, onLongClick = if (actions.isEmpty()) null else ({ menu = true }))
            ) {
                // The picture area: the theme's soft colour, the photo on it.
                Box(
                    Modifier.fillMaxSize().padding(if (small) 6.dp else 8.dp)
                        .lookMat(look, RoundedCornerShape(if (small) 12.dp else 16.dp), look.pictureBg(id, item.picture != null), over = true),
                    contentAlignment = Alignment.Center
                ) {
                    val pic = item.picture
                    if (pic != null) {
                        PhotoStage(pic, item.name, small = true)
                    } else if (item.count == 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                tr("Empty"), style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = look.cardMuted(id)
                            )
                            if (!small) Text(
                                "+ " + tr("Add wish"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                color = look.chipContent(true),
                                modifier = Modifier.padding(top = 8.dp).lookChip(look, on = true)
                                    .clickable { onAddTo(item.addTo) }.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        // Wishes, but none with a photo.
                        PlaceholderIcon(if (item.group) "group" else "gift", if (small) 26.dp else 40.dp)
                    }
                }
                // The number of wishes: a white circle top right (also 0, owner). Not on "Recently opened" (owner, 2.92).
                if (!small) Box(
                    Modifier.align(Alignment.TopEnd).padding(if (small) 8.dp else 12.dp)
                        .size(if (small) 22.dp else 28.dp).clip(CircleShape).background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.count.toString(), color = Color(0xFF1B1B1F), fontWeight = FontWeight.Bold,
                        fontSize = if (small) 11.sp else 13.sp, maxLines = 1
                    )
                }
            }
        }
        Text(
            item.name, style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** A list in "Recently opened": a smaller block (owner, 2.91). */
@Composable
private fun RecentCard(item: HubItem, onAddTo: (String) -> Unit) {
    ListBlock(item, emptyList(), onAddTo, Modifier.width(112.dp), small = true)
}

/** A new wishlist of your own: a name (required) and notes about what it's for. */
@Composable
fun NewListDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    // Optional: wishes from your other lists, copied in when the list is made.
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("New wishlist")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                    label = { Text(tr("Name*")) }, singleLine = true, isError = missing,
                    supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                OutlinedTextField(
                    notes, { notes = it.take(ABOUT_MAX) },
                    label = { Text(tr("Notes")) }, placeholder = { Text(tr("What is this wishlist for?")) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                if (canCopy) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add wishes from your other lists"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                when (picked.size) {
                                    0 -> tr("Optional")
                                    1 -> tr("1 wish chosen")
                                    else -> tr("%d wishes chosen", picked.size)
                                },
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
                else {
                    val id = Repo.createMine(ctx, name, notes)
                    Repo.copyInto(ctx, id, picked)
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
