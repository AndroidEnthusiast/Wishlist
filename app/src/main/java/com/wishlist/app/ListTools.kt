package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The tools above the wishes of every list you open (owner, 12 November 2026: "the same tools in the same places"):
 * left what you see and in which order (the category, the sort), right how it looks (the view). Your own lists,
 * collab wishlists and friends' lists all use this row; search is the 🔍 in the top bar.
 *
 * [menuOpen]/[onMenu]: whether the category menu is open (the screen keeps it, so only one copy of a pinned row opens
 * it, see ListScreen's `live`). [onNewCategory]: "New category…" at the bottom of the menu (your own lists only).
 */
@Composable
fun ListTools(
    sel: String, cats: List<String>, countOf: (String) -> Int, onCat: (String) -> Unit,
    menuOpen: Boolean, onMenu: (Boolean) -> Unit,
    sortMode: String, withStar: Boolean, onSort: (String) -> Unit,
    view: String, onView: (String) -> Unit,
    onNewCategory: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: what you see and in which order (category, sort). Right: how it looks (view buttons).
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f, fill = false)) {
                // Plain text, no filled chip (owner, 2.130: a calmer top).
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).clickable { onMenu(true) }
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
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                    (listOf("All") + cats).forEach { c ->
                        // Editing categories is in the wish form's "Edit categories" (2.110), not a long press here.
                        CatMenuRow(
                            text = catLabel(c) + " (${countOf(c)})", bold = c == sel,
                            onClick = { onCat(c); onMenu(false) }
                        )
                    }
                    if (onNewCategory != null) DropdownMenuItem(
                        text = { Text(tr("New category…")) },
                        leadingIcon = { Icon(AppIcons.Add, null) },
                        onClick = { onMenu(false); onNewCategory() }
                    )
                }
            }
            // Room between the category and the sort button (owner, 2.127: they touched).
            Spacer(Modifier.width(8.dp))
            SortButton(sortMode, withStar = withStar, quiet = true, onPick = onSort)
            Spacer(Modifier.width(8.dp))
        }
        // One icon for the view (owner, 2.130): it shows the view in use and opens Rows, Two per row, Large cards.
        ViewMenuButton(view, onView)
    }
}
