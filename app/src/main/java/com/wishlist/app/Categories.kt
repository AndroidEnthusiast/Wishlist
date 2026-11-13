package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
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
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
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
    // Your own lists share their categories (2.95): count on all of them. A wishlist for someone: only that list.
    val own = Shared.active == null
    val (n, lists) = if (own) Repo.catUse(ctx, name) else Repo.wishes.count { it.category == name } to 1
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Delete “%s”?", name), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            // The wishes always stay (owner, 2.110: "write it better").
            Text(
                if (n == 0) tr("No wishes are in this category.")
                else (if (n == 1) tr("1 wish is in this category.") else tr("%d wishes are in this category.", n)) + " " +
                    (if (!own) tr("They stay on this list, just without a category. This changes the list for everyone on it.")
                    else if (lists > 1) tr("They stay on your wishlists, just without a category. The category goes from all your wishlists.")
                    else tr("They stay on your wishlist, just without a category."))
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
                    MenuRow(tr("Edit"), { mode = "edit" }) { Icon(AppIcons.Edit, null, tint = cs.onSurfaceVariant) }
                    MenuRow(tr("Delete"), { mode = "delete" }) { Icon(AppIcons.Delete, null, tint = cs.onSurfaceVariant) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
    }
}

/** How many wishes have category [name]: on all your own lists (they share categories), or on this gift list. */
fun catCount(c: Context, name: String): Int =
    if (Shared.active == null) Repo.catUse(c, name).first else Repo.wishes.count { it.category == name }

/**
 * "Edit categories" (2.110, owner): opened from the category drop-down of the wish form, under "New category". It
 * edits the categories of the list the wish is being saved to: the set all your own wishlists share, or the gift
 * list's own set (shared with everyone on it). Add, rename (tap the name) and delete. A category no wish has is
 * deleted at once; otherwise a short warning says how many wishes lose it (they stay). This replaced the categories
 * part of List settings.
 */
@Composable
fun EditCategoriesScreen(onRenamed: (String, String) -> Unit = { _, _ -> }, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var newCat by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val own = Shared.active == null
    val cats = Repo.cats
    fun add() {
        if (Repo.addCat(ctx, newCat) != null) newCat = ""
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().lookPage(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(AppIcons.ArrowBack, tr("Back")) }
                    Text(
                        tr("Edit categories"), style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(
                        if (own) tr("These categories are shared by all your wishlists.")
                        else tr("These categories are only for “%s”, and everyone on it sees them.", Repo.listName),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newCat, { newCat = it.take(40) }, label = { Text(tr("New category")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { add() }, enabled = newCat.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text(tr("Add")) }
                    }
                    if (cats.isEmpty()) Text(tr("No categories yet."), color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    cats.forEach { c ->
                        val n = catCount(ctx, c)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = tr("Rename")) { renaming = c }.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(c, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when (n) { 0 -> tr("No wishes"); 1 -> tr("1 wish"); else -> tr("%d wishes", n) },
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renaming = c }) { Icon(AppIcons.Edit, tr("Rename %s", c), tint = cs.onSurfaceVariant) }
                            IconButton(onClick = {
                                // Nothing loses anything: gone at once. Otherwise say what happens first.
                                if (catCount(ctx, c) == 0) Repo.removeCat(ctx, c) else deleting = c
                            }) { Icon(AppIcons.Delete, tr("Delete %s", c), tint = cs.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
    renaming?.let { r -> RenameCategoryDialog(r, onRenamed = { n -> onRenamed(r, n) }, onClose = { renaming = null }) }
    deleting?.let { d -> DeleteCategoryDialog(d, onDeleted = {}, onClose = { deleting = null }) }
}
