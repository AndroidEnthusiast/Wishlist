package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/* Wishlist groups (see GroupsParts.kt): picking the list you bring (also used by gift exchanges). */

// ---------- picking the list you bring ----------

/**
 * Which of your own wishlists you bring to a group, or a new one (empty, or with wishes copied from your other
 * lists). [onPick] gets the list's id and whether it was made here; it runs the action and returns an error message
 * or null. [extra]: shown above the choices (the join screen's group details).
 */
@Composable
fun ListPickDialog(
    title: String, confirm: String, current: String?, onClose: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    dismissLabel: String? = null, onDismissAction: (() -> Unit)? = null,
    // The line under the choices (a gift exchange says who sees the list, 2.129).
    note: String? = null,
    onPick: suspend (String, Boolean) -> String?
) {
    val ctx = LocalContext.current
    val model = rememberGroup()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val lists = remember(Repo.mineVersion, Repo.mineIds) { Repo.allMine(ctx) }
    var chosen by remember { mutableStateOf(current ?: lists.firstOrNull()?.id ?: "new") }
    var name by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                extra?.invoke()
                Text(
                    tr("Which wishlist do you bring?"), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                lists.forEach { d ->
                    val n = if (d.name == "My wishlist") tr(d.name) else d.name
                    PickRow(n, if (d.wishes.size == 1) tr("1 wish") else tr("%d wishes", d.wishes.size), chosen == d.id) { chosen = d.id }
                }
                PickRow(tr("New wishlist"), tr("Empty, or with wishes from your other lists"), chosen == "new") { chosen = "new" }
                if (chosen == "new") {
                    OutlinedTextField(
                        name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                        label = { Text(tr("Wishlist name*")) }, singleLine = true, isError = missing,
                        supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), keyboardOptions = Capitalised
                    )
                    if (canCopy) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add wishes from your other lists"), style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                            // No "Optional" (owner, 2.125: it suggested the rest isn't optional).
                            if (picked.isNotEmpty()) Text(
                                if (picked.size == 1) tr("1 wish chosen") else tr("%d wishes chosen", picked.size),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    note ?: tr("Everyone in the group always sees this wishlist. Private wishes are always hidden."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (chosen == "new" && name.isBlank()) { missing = true; return@TextButton }
                busy = true
                error = null
                scope.launch {
                    val fresh = chosen == "new"
                    val id = if (fresh) {
                        val made = model.newList(name, picked)
                        // Later tries use this list rather than making another one.
                        chosen = made
                        made
                    } else chosen
                    error = try {
                        onPick(id, fresh)
                    } catch (e: Exception) {
                        tr("Couldn’t reach the server. Check your connection.")
                    }
                    busy = false
                }
            }) { Text(confirm) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { if (onDismissAction != null) onDismissAction() else onClose() }) {
                Text(dismissLabel ?: tr("Cancel"))
            }
        }
    )
    if (picking) CopyWishesDialog(null, onDone = { picked = it; picking = false }, onClose = { picking = false })
}
