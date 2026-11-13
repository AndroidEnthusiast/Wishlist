package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

/**
 * A wishlist a new wish can be saved to. [code] is null for your own lists, otherwise the code of a group wishlist.
 * [priv]: as a private wish (only on this phone). [key]: what Repo.lastSaveTo remembers ("mine", "private",
 * "mine:<id>", "private:<id>" or a group wishlist's code, see MyLists.saveKey).
 */
data class SaveTarget(val code: String?, val name: String, val key: String)

/**
 * Every wishlist you can save to: each of your own, then the group wishlists. (Until 2.76 each of your lists was
 * also offered as "· Private wish"; that was confusing, as there is no private list: the form's "Private wish"
 * switch does it.)
 */
fun saveTargets(mine: List<Pair<String, String>>, shared: List<SharedList>): List<SaveTarget> =
    mine.map { (id, raw) ->
        val name = raw.ifBlank { "My wishlist" }.let { if (it == "My wishlist") tr(it) else it }
        SaveTarget(null, name, MyLists.saveKey(id, false))
    } + shared.map { SaveTarget(it.code, it.name, it.code) }

/** A drop-down to choose which wishlist a wish is saved to. */
@Composable
fun SaveToField(
    targets: List<SaveTarget>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier,
    // "+ New wishlist" at the bottom (owner, 7 November 2026): the usual create form, then back here with it picked.
    onNew: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    val current = targets.firstOrNull { it.key == selected } ?: targets.first()
    Box(modifier) {
        OutlinedTextField(
            value = current.name, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(tr("Save to")) },
            trailingIcon = { Icon(AppIcons.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth()
        )
        // A text field would pop up the keyboard. This invisible layer opens the menu instead.
        Box(
            Modifier.matchParentSize()
                .clickable(role = Role.DropdownList, onClickLabel = tr("Choose a wishlist")) { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            targets.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.name, fontWeight = if (t.key == current.key) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onPick(t.key) }
                )
            }
            if (onNew != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(tr("New wishlist"), fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(AppIcons.Add, null) },
                    onClick = { open = false; onNew() }
                )
            }
        }
    }
}
