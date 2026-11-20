package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * What you can do with one wish (owner, 12 November 2026): the ⋮ on every wish card opens this sheet from the bottom,
 * the same in every view and on every list, instead of a small drop-down that differed per view. At the top the wish
 * itself (photo, name, price), so it's clear which one; then big rows with icons, like the profile sheet.
 *
 * Order (owner): on a collab wishlist or a friend's list Reserve comes first (what you do most there); then Edit,
 * Share, Move to…; on your own lists Private, Mark as owned and Price history. Only what applies shows. The bin stays
 * on the card; selecting several wishes is a long press (ListScreen).
 */
class WishActions(
    /** Reserve or Unreserve (collab wishlists and friends' lists), or null. */
    val reserve: Reserving? = null,
    val onEdit: (() -> Unit)? = null,
    val onMove: (() -> Unit)? = null,
    /** Your own lists: Private and Mark as owned. */
    val own: Boolean = false,
    /** Your own lists: the wish's price history. */
    val onHistory: (() -> Unit)? = null,
    /** Share as a gift idea (someone else's list or a collab wishlist) instead of as your wish. */
    val shareAsIdea: Boolean = false,
    /**
     * "All your wishes" (owner, 12 November 2026): the wish may be on several of your lists, so Private, I have it and
     * the move to Received gifts go to every copy; null: the wish of the list that is open.
     */
    val everywhere: Everywhere? = null,
    /** Someone else's wish whose owner is your friend: "Chat about this wish" (owner, 20 November 2026). */
    val chatAbout: (() -> Unit)? = null
)

/** What the sheet does to every copy of a wish (All your wishes). */
class Everywhere(val lists: List<String>, val setPrivate: (Boolean) -> Unit, val setOwned: (Boolean) -> Unit, val received: () -> Unit)

/** The rows of the sheet for [w], in order, as (key, label): the checks read this to keep the order fixed. */
fun wishSheetRows(w: Wish, a: WishActions): List<String> = buildList {
    when (a.reserve?.state) {
        null -> if (a.reserve != null) add("reserve")
        true -> add("unreserve")
        false -> {} // someone else reserved it: nothing to do
    }
    if (a.onEdit != null) add("edit")
    add("share")
    // Someone else's wish (a friend's, a group member's, a gift exchange's or a collab wishlist's): copy it into one of
    // your own lists (owner, 18 November 2026).
    if (a.shareAsIdea) add("addmine")
    if (a.chatAbout != null) add("chat")
    if (a.onMove != null) add("move")
    if (a.own) { add("private"); add("have") }
    if (a.own && a.onHistory != null) add("history")
}

/**
 * The rows of a wish's sheet in groups (2.210, owner 15 November 2026: grouped cards instead of Google-Settings rows):
 * what you do with it (reserve, edit, share, move), its switches (private, I have it), and information (price history).
 * The order inside each group stays [wishSheetRows]'s.
 */
fun sheetGroups(rows: List<String>): List<List<String>> {
    val doing = setOf("reserve", "unreserve", "edit", "share", "addmine", "chat", "move")
    val switches = setOf("private", "have")
    return listOf(rows.filter { it in doing }, rows.filter { it in switches }, rows.filter { it !in doing && it !in switches })
        .filter { it.isNotEmpty() }
}

/**
 * Whether tapping [row] closes the sheet: the switches don't (Private; "I have it" when it is on, a tap turns it
 * off), so you see them flip (owner, 15 November 2026). Everything else opens something else and closes it.
 */
fun sheetRowCloses(row: String, w: Wish): Boolean = when (row) {
    "private" -> false
    "have" -> !w.got
    else -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishSheet(w: Wish, a: WishActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Every row that opens something else closes the sheet first, then does its thing. The switches (Private, and
    // turning "I have it" off) don't: the sheet stays open and the switch flips (owner, 15 November 2026: closing on
    // Private was "very annoying"); tapping it again is the undo.
    fun then(f: () -> Unit): () -> Unit = { onClose(); f() }
    fun keep(row: String, f: () -> Unit): () -> Unit = if (sheetRowCloses(row, w)) then(f) else f
    // "I have it" on a wish you don't have yet asks where it goes; the sheet steps aside for the question.
    var asking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (asking) {
        HaveItDialog(
            1,
            onKeep = { onClose(); a.everywhere?.setOwned?.invoke(true) ?: OwnedFlow.toggle(ctx, w) },
            onReceived = { onClose(); a.everywhere?.received?.invoke() ?: ReceivedFlow.receive(ctx, w) },
            onClose = onClose
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            // The wish itself.
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val look = LocalLook.current
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(if (look.design == "quiet") 14.dp else 12.dp)).then(if (look.design == "quiet") Modifier.background(look.photoSurface(w.id)) else Modifier)) {
                    if (w.image.isNotBlank()) {
                        // Quiet (2.207): the whole photo on the light photo surface, as on the cards.
                        if (look.design == "quiet") PhotoStage(w.image, w.label, small = true)
                        else WishImage(w.image, Modifier.size(56.dp), ContentScale.Crop)
                    } else Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { PlaceholderIcon("gift", 28.dp, if (look.design == "quiet") look.photoInk else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        w.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    w.price?.let { p ->
                        // Quiet (2.207): the price as on the card, with its change.
                        if (LocalLook.current.design == "quiet") PriceLine(w, MaterialTheme.typography.titleMedium)
                        else Text(money(p, w.currency), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    // On several of your lists: say so (owner: otherwise "From which list?" makes no sense).
                    a.everywhere?.lists?.takeIf { it.size > 1 }?.let { ls ->
                        Text(
                            tr("On %1\$d lists: %2\$s", ls.size, ls.joinToString(", ") { Repo.mineName(it) }),
                            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = cs.outlineVariant)
            // Quiet (2.210): the rows in grouped cards (doing, on/off, information), each icon in a tinted tile.
            @Composable
            fun rowContent(row: String) = when (row) {
                "reserve" -> SheetRow({ Icon(AppIcons.Bag, null, tint = cs.primary) }, tr("Reserve"), then { a.reserve?.set?.invoke(true) })
                "unreserve" -> SheetRow({ Icon(AppIcons.Bag, null, tint = cs.onSurfaceVariant) }, tr("Unreserve"), then { a.reserve?.set?.invoke(false) })
                "edit" -> SheetRow({ Icon(AppIcons.Edit, null, tint = cs.onSurfaceVariant) }, tr("Edit"), then { a.onEdit?.invoke() })
                "share" -> SheetRow({ Icon(AppIcons.Share, null, tint = cs.onSurfaceVariant) }, tr("Share"), then { shareItem(ctx, w, idea = a.shareAsIdea) })
                "move" -> SheetRow({ Icon(AppIcons.Swap, null, tint = cs.onSurfaceVariant) }, tr("Move or copy…"), then { a.onMove?.invoke() })
                "addmine" -> SheetRow({ Icon(AppIcons.Add, null, tint = cs.onSurfaceVariant) }, tr("Add to my wishlist"), then { AddToMine.ask(w) })
                "chat" -> SheetRow({ Icon(AppIcons.Chat, null, tint = cs.onSurfaceVariant) }, tr("Chat about this wish"), then { a.chatAbout?.invoke() })
                "private" -> SheetRow(
                    { Icon(PrivateIcon, null, tint = cs.onSurfaceVariant) }, tr("Private"),
                    keep("private") { a.everywhere?.setPrivate?.invoke(!w.priv) ?: togglePrivate(ctx, w) },
                    checked = w.priv
                )
                // One action for "I have it" (owner, 12 November 2026; it replaces "Owned" and "Received"): ticked, a
                // tap takes it back (with Undo); not ticked, it asks where the wish goes.
                "have" -> SheetRow(
                    { Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant) }, tr("I have it"),
                    if (w.got) keep("have") { a.everywhere?.setOwned?.invoke(false) ?: OwnedFlow.toggle(ctx, w) } else ({ asking = true }),
                    checked = w.got
                )
                "history" -> SheetRow({ HistoryIcon(cs.onSurfaceVariant) }, tr("Price history"), then { a.onHistory?.invoke() })
                else -> {}
            }
            for (g in sheetGroups(wishSheetRows(w, a))) SheetGroup(g.map { r -> { rowContent(r) } })
        }
    }
}

/** One row of a sheet (the profile sheet and the wish sheet): an icon, a label, perhaps a tick box. 52 dp tall. */
@Composable
fun SheetRow(
    icon: @Composable () -> Unit, label: String, onClick: () -> Unit, checked: Boolean? = null, danger: Boolean = false,
    // A line under the label (2.212: the current Visibility, or what a choice means), and a tick on the chosen choice.
    detail: String? = null, chosen: Boolean? = null
) {
    val look = LocalLook.current
    val quiet = look.design == "quiet"
    val inGroup = LocalInSheetGroup.current && quiet
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (inGroup) 0.dp else 12.dp).heightIn(min = 52.dp)
            .clip(if (inGroup) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = if (inGroup) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (quiet) {
            // Quiet (2.210): the icon in a small squircle tile with a soft tint, recoloured to the tile's ink; red for
            // something that removes.
            val cs = look.scheme
            val tile = if (danger) cs.error.copy(alpha = 0.14f) else cs.primaryContainer
            val ink = if (danger) cs.error else cs.onPrimaryContainer
            Box(
                Modifier.size(32.dp).clip(Squircle(9.dp)).background(tile)
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent { drawContent(); drawRect(ink, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop) },
                contentAlignment = Alignment.Center
            ) { Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() } }
        } else icon()
        Column(Modifier.padding(start = if (quiet) 14.dp else 16.dp, top = 6.dp, bottom = 6.dp).weight(1f)) {
            Text(
                label, style = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start),
                color = if (danger) look.scheme.error else look.scheme.onSurface
            )
            if (detail != null) Text(
                detail, style = MaterialTheme.typography.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start),
                color = look.scheme.onSurfaceVariant
            )
        }
        if (chosen == true) Icon(AppIcons.Check, tr("Chosen"), tint = look.scheme.primary, modifier = Modifier.size(22.dp))
        // On or off: a switch in Quiet (owner, 14 November 2026: like App settings), a tick box elsewhere.
        if (checked != null) {
            if (quiet) Switch(checked = checked, onCheckedChange = null)
            else Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

/** True for rows inside a [SheetGroup] (Quiet draws them flush in the group's card). */
val LocalInSheetGroup = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Rows grouped in one rounded card with hairlines between them (Quiet, 2.210, owner 15 November 2026); other styles
 * keep their rows as they were.
 */
@Composable
fun SheetGroup(rows: List<@Composable () -> Unit>) {
    val look = LocalLook.current
    if (look.design != "quiet") { rows.forEach { it() }; return }
    val bg = look.groupFill()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(Squircle(18.dp)).background(bg)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalInSheetGroup provides true) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(1.dp).background(look.line))
                row()
            }
        }
    }
}

/**
 * "I have it" (owner, 12 November 2026): keep the wish on the list, marked "Owned", or move it to Received gifts
 * (it leaves the list; people who gave it get a thank-you). [count] wishes at once ("What did you get?").
 */
@Composable
fun HaveItDialog(count: Int, onKeep: () -> Unit, onReceived: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (count == 1) tr("You have it!") else tr("You have %d of them!", count)) },
        text = {
            Column {
                Text(tr("Where should it go?"), style = MaterialTheme.typography.bodyMedium)
                SheetGroup(listOf(
                    { SheetRow({ Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant) }, tr("Keep it on my list, marked “Owned”"), onKeep) },
                    { SheetRow({ Icon(AppIcons.Gift, null, tint = cs.onSurfaceVariant) }, tr("Move it to Received gifts"), onReceived) }
                ))
                Text(
                    tr("Received gifts leave your wishlist. Whoever gave it gets a thank-you."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** One row of a [PopMenu]: its icon, its word, red when it removes, and an optional second line (the current choice). */
class PopRow(
    val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val danger: Boolean = false,
    val detail: String? = null, val onClick: () -> Unit
)

/**
 * A small menu at its button (owner, 16 November 2026, after Giftful's ⋯ menu: "clean and clear, with the icons"): an
 * icon and a word per row, a thin line, then what removes in red. For up to four actions; longer menus (a wish's ⋮) stay
 * bottom sheets. Shown while it is in the composition (call it only when open).
 */
@Composable
fun PopMenu(onClose: () -> Unit, rows: List<PopRow>) {
    val cs = MaterialTheme.colorScheme
    val (removing, doing) = rows.partition { it.danger }
    @Composable
    fun item(r: PopRow) {
        val ink = if (r.danger) cs.error else cs.onSurface
        androidx.compose.material3.DropdownMenuItem(
            text = {
                Column {
                    Text(r.label, style = MaterialTheme.typography.bodyLarge, color = ink)
                    if (r.detail != null) Text(r.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            },
            leadingIcon = { Icon(r.icon, null, tint = ink, modifier = Modifier.size(22.dp)) },
            onClick = { onClose(); r.onClick() },
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 4.dp)
        )
    }
    DropdownMenu(expanded = true, onDismissRequest = onClose, modifier = Modifier.widthIn(min = 220.dp)) {
        doing.forEach { item(it) }
        if (doing.isNotEmpty() && removing.isNotEmpty()) androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = cs.outlineVariant)
        removing.forEach { item(it) }
    }
}
