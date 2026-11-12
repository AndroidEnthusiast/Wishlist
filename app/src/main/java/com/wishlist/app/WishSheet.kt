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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
    val shareAsIdea: Boolean = false
)

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
    if (a.own) { add("private"); add("owned") }
    if (a.own && a.onHistory != null) add("history")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishSheet(w: Wish, a: WishActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Every row closes the sheet first, then does its thing.
    fun then(f: () -> Unit): () -> Unit = { onClose(); f() }
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
                    { Icon(PrivateIcon, null, tint = cs.onSurfaceVariant) }, tr("Private"), then { togglePrivate(ctx, w) },
                    checked = w.priv
                )
                "owned" -> SheetRow(
                    { Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant) }, tr("Owned"), then { OwnedFlow.toggle(ctx, w) },
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
