package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

/** A wishlist a new wish can be saved to. [code] is null for My wishlist, otherwise the code of a shared wishlist. */
data class SaveTarget(val code: String?, val name: String)

/**
 * Every wishlist you can save to, My wishlist first. When you can make more wishlists of your own later,
 * this is the one place that has to list them too.
 */
fun saveTargets(myName: String, shared: List<SharedList>): List<SaveTarget> =
    listOf(SaveTarget(null, myName.ifBlank { "My wishlist" }.let { if (it == "My wishlist") tr(it) else it })) + shared.map { SaveTarget(it.code, it.name) }

/** A drop-down to choose which wishlist a wish is saved to. */
@Composable
fun SaveToField(targets: List<SaveTarget>, selected: String?, onPick: (String?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = targets.firstOrNull { it.code == selected } ?: targets.first()
    Box(modifier) {
        OutlinedTextField(
            value = current.name, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(tr("Save to")) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
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
                    text = { Text(t.name, fontWeight = if (t.code == current.code) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onPick(t.code) }
                )
            }
        }
    }
}
