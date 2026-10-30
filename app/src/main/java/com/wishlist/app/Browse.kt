package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Three bars that get shorter: the "sort" icon. */
@Composable
private fun SortIcon(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        fun bar(w: Float, y: Float) = drawRoundRect(
            color = tint, topLeft = Offset(3f * u, y * u), size = Size(w * u, 3f * u),
            cornerRadius = CornerRadius(1.5f * u, 1.5f * u)
        )
        bar(18f, 5f); bar(12f, 10.5f); bar(6f, 16f)
    }
}

/**
 * Sits next to the category drop-down (what you see and in which order), away from the view buttons (how it looks).
 * Opens the list of orders. When a sort is active it is highlighted and says which one.
 */
@Composable
fun SortButton(mode: String, withStar: Boolean = true, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val changed = mode != "new"
    Box {
        Row(
            Modifier.height(44.dp).defaultMinSize(minWidth = 44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (changed) cs.primaryContainer else Color.Transparent)
                .semantics { contentDescription = if (changed) tr("Sort: %s", tr(sortShortName(mode))) else tr("Sort") }
                .clickable { open = true }
                .padding(horizontal = if (changed) 12.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            SortIcon(if (changed) cs.onPrimaryContainer else cs.onSurfaceVariant)
            if (changed) {
                Spacer(Modifier.width(6.dp))
                Text(
                    tr(sortShortName(mode)), style = MaterialTheme.typography.labelLarge,
                    color = cs.onPrimaryContainer, maxLines = 1
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // A shared list has no "most wanted", so it can't be sorted by it either.
            SORT_OPTIONS.filter { withStar || it.first != "star" }.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(tr(label), fontWeight = if (key == mode) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = if (key == mode) ({ Icon(Icons.Default.Check, null) }) else null,
                    onClick = { open = false; onPick(key) }
                )
            }
        }
    }
}

/** The search box under the top bar. Its X clears the text, and closes the box when it is already empty. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
    }
    OutlinedTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        placeholder = { Text(tr("Search your wishes")) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            IconButton(onClick = { if (query.isNotEmpty()) onQuery("") else onClose() }) {
                Icon(Icons.Default.Close, if (query.isEmpty()) tr("Close search") else tr("Clear search"))
            }
        },
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus)
    )
}
