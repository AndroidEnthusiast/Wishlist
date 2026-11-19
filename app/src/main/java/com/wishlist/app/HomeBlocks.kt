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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
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
 * Home's list blocks (split out of Hub.kt in the rebuild, owner 18 November 2026): a list as a block with its
 * photo collage, name and count, the grid two per row, section titles, and the order lists were opened in.
 */

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
    newLabel: String? = null, onNew: (() -> Unit)? = null,
    // The ⋮ next to each name (Home, 16 November 2026).
    dots: Boolean = false, modifier: Modifier = Modifier
) {
    val cells: List<HubItem?> = items + (if (onNew != null) listOf(null) else emptyList())
    Column(modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    if (item == null) NewBlock(newLabel.orEmpty(), onNew ?: {}, Modifier.weight(1f))
                    else ListBlock(item, if (onHome) actionsOf(item) else emptyList(), onAddTo, Modifier.weight(1f), small = false, dots = dots)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The dashed "+ New …" block at the end of a section on Home (owner, 2.134). */
@Composable
internal fun NewBlock(label: String, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    if (look.design == "quiet") {
        // Quiet (2.207, the approved plan): a tile as big as a list's, a + in a soft circle of your colour, the label
        // under it where a list's name sits. No dashed box.
        // The tile and its label are one button, but only the tile is rounded: clipping the whole column cut off the
        // label's first letter at the bottom-left corner (owner's screenshot, 15 November 2026). The label shares the
        // tile's ripple.
        val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Column(modifier.clickable(press, null, onClickLabel = label, onClick = onClick)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).lookCard(look, 9L, look.cardShape(9L, small = true))
                    .clip(look.cardShape(9L, small = true)).indication(press, androidx.compose.material3.ripple()),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Add, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            }
            Text(
                label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, top = 6.dp)
            )
        }
        return
    }
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
                // No long press (owner, 15 November 2026: a wishlist can't be moved, so there is nothing to select);
                // everything is in the ⋮.
                .clickable(onClick = item.open)
        ) {
            // The picture area: the theme's soft colour, the photos filling it. Quiet (2.207): the photos fill the whole
            // block, no frame inside the frame.
            val quiet = look.design == "quiet"
            val mat = if (quiet) shape else RoundedCornerShape(if (small) 12.dp else 16.dp)
            Box(
                Modifier.fillMaxSize().padding(if (quiet) 0.dp else if (small) 6.dp else 8.dp)
                    .lookMat(look, mat, look.pictureBg(id, item.picture != null), over = true).clip(mat),
                contentAlignment = Alignment.Center
            ) {
                val pics = item.pics
                when {
                    pics.size >= 3 -> Collage(pics)
                    // Two photos side by side (owner, 16 November 2026, like Giftful), not just the first.
                    pics.size == 2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        WishImage(pics[0], Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                        WishImage(pics[1], Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    }
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
                if (menu) ListMenuSheet(item, actions) { menu = false }
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

/** Lists in the order they were last opened, newest first; never-opened ones keep their own order after them. */
internal fun byRecent(items: List<HubItem>): List<HubItem> = items.sortedByDescending { Repo.recent[it.key] ?: 0L }
