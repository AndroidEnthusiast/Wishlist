package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
    val everywhere: Everywhere? = null
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
    if (a.onMove != null) add("move")
    if (a.own) { add("private"); add("have") }
    if (a.own && a.onHistory != null) add("history")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishSheet(w: Wish, a: WishActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Every row closes the sheet first, then does its thing.
    fun then(f: () -> Unit): () -> Unit = { onClose(); f() }
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
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                    if (w.image.isNotBlank()) WishImage(w.image, Modifier.size(56.dp), ContentScale.Crop)
                    else Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { PlaceholderIcon("gift", 28.dp) }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        w.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    w.price?.let { p ->
                        Text(money(p, w.currency), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
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
            for (row in wishSheetRows(w, a)) when (row) {
                "reserve" -> SheetRow({ Icon(AppIcons.Bag, null, tint = cs.primary) }, tr("Reserve"), then { a.reserve?.set?.invoke(true) })
                "unreserve" -> SheetRow({ Icon(AppIcons.Bag, null, tint = cs.onSurfaceVariant) }, tr("Unreserve"), then { a.reserve?.set?.invoke(false) })
                "edit" -> SheetRow({ Icon(AppIcons.Edit, null, tint = cs.onSurfaceVariant) }, tr("Edit"), then { a.onEdit?.invoke() })
                "share" -> SheetRow({ Icon(AppIcons.Share, null, tint = cs.onSurfaceVariant) }, tr("Share"), then { shareItem(ctx, w, idea = a.shareAsIdea) })
                "move" -> SheetRow({ Icon(AppIcons.Swap, null, tint = cs.onSurfaceVariant) }, tr("Move to…"), then { a.onMove?.invoke() })
                "private" -> SheetRow(
                    { Icon(PrivateIcon, null, tint = cs.onSurfaceVariant) }, tr("Private"),
                    then { a.everywhere?.setPrivate?.invoke(!w.priv) ?: togglePrivate(ctx, w) },
                    checked = w.priv
                )
                // One action for "I have it" (owner, 12 November 2026; it replaces "Owned" and "Received"): ticked, a
                // tap takes it back (with Undo); not ticked, it asks where the wish goes.
                "have" -> SheetRow(
                    { Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant) }, tr("I have it"),
                    if (w.got) then { a.everywhere?.setOwned?.invoke(false) ?: OwnedFlow.toggle(ctx, w) } else ({ asking = true }),
                    checked = w.got
                )
                "history" -> SheetRow({ HistoryIcon(cs.onSurfaceVariant) }, tr("Price history"), then { a.onHistory?.invoke() })
            }
        }
    }
}

/** One row of a sheet (the profile sheet and the wish sheet): an icon, a label, perhaps a tick box. 52 dp tall. */
@Composable
fun SheetRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit, checked: Boolean? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp).weight(1f))
        if (checked != null) Checkbox(checked = checked, onCheckedChange = null)
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
                SheetRow({ Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant) }, tr("Keep it on my list, marked “Owned”"), onKeep)
                SheetRow({ Icon(AppIcons.Gift, null, tint = cs.onSurfaceVariant) }, tr("Move it to Received gifts"), onReceived)
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
