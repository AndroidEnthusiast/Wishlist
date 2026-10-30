package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One category as a chip. Tap to pick it. Long press (when [onLongClick] is set) to edit or delete it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.height(32.dp).clip(shape)
            .background(if (selected) cs.secondaryContainer else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else cs.outline, shape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = tr("Edit or delete"),
                onLongClick = onLongClick?.let { f ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        f()
                    }
                },
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A tick on the chosen category, or the icon you gave (the "New" chip has a plus).
        if (selected) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant
        )
    }
}

/** One row of the category drop-down on the home screen. Long press (when [onLongClick] is set) edits or deletes it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatMenuRow(text: String, bold: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth().widthIn(min = 112.dp).defaultMinSize(minHeight = 48.dp)
            .combinedClickable(onLongClickLabel = tr("Edit or delete"), onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "New category" box. [onAdded] gets the name of the category that now exists (a new one, or the one with that name already). */
@Composable
fun CategoryDialog(onAdded: (String) -> Unit = {}, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    val reserved = name.trim().equals("All", ignoreCase = true) || name.trim().equals(tr("All"), ignoreCase = true)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("New category")) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it }, label = { Text(tr("Name")) }, singleLine = true, isError = reserved,
                    modifier = Modifier.fillMaxWidth()
                )
                if (reserved) {
                    Text(tr("“All” is already used."), color = cs.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !reserved,
                onClick = {
                    val added = Repo.addCat(ctx, name)
                    if (added != null) onAdded(added)
                    onClose()
                }
            ) { Text(tr("Add")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Changes the name of a category. Every wish in it moves along. [onRenamed] gets the new name. */
@Composable
fun RenameCategoryDialog(name: String, onRenamed: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(name) }
    val t = text.trim()
    val taken = t.equals("All", ignoreCase = true) || Repo.cats.any { it != name && it.equals(t, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Edit category")) },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, label = { Text(tr("Name")) }, singleLine = true, isError = taken,
                    modifier = Modifier.fillMaxWidth()
                )
                if (taken) {
                    Text(tr("That name is already used."), color = cs.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = t.isNotEmpty() && !taken,
                onClick = {
                    if (Repo.renameCat(ctx, name, t)) onRenamed(Repo.cleanCatName(t))
                    onClose()
                }
            ) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Asks before deleting a category. Its wishes stay on the list, just without a category. */
@Composable
fun DeleteCategoryDialog(name: String, onDeleted: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val n = Repo.wishes.count { it.category == name }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Delete “%s”?", name), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Text(
                if (n == 0) tr("No wishes are in this category.")
                else tr("%d wish(es) stay on your list, just without a category.", n)
            )
        },
        confirmButton = {
            TextButton(onClick = { Repo.removeCat(ctx, name); onDeleted(); onClose() }) { Text(tr("Delete")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** What a long press on a category opens: Edit and Delete. */
@Composable
fun CategoryActionsDialog(name: String, onRenamed: (String) -> Unit, onDeleted: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf("menu") }
    when (mode) {
        "edit" -> RenameCategoryDialog(name, onRenamed, onClose)
        "delete" -> DeleteCategoryDialog(name, onDeleted, onClose)
        else -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuRow(tr("Edit"), { mode = "edit" }) { Icon(Icons.Default.Edit, null, tint = cs.onSurfaceVariant) }
                    MenuRow(tr("Delete"), { mode = "delete" }) { Icon(Icons.Default.Delete, null, tint = cs.onSurfaceVariant) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
    }
}
