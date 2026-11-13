package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The sizes you can pick. A size is chosen from this list, never typed. */
val SIZES = listOf("XS", "S", "M", "L", "XL")

/** A drop-down field for the size of a wish. Picking "No size" clears it. */
@Composable
fun SizeField(size: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = size, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(tr("Size")) },
            trailingIcon = { Icon(AppIcons.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth()
        )
        // A text field would pop up the keyboard. This invisible layer opens the menu instead.
        Box(
            Modifier.matchParentSize()
                .clickable(role = Role.DropdownList, onClickLabel = tr("Choose a size")) { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr("No size")) }, onClick = { open = false; onPick("") })
            SIZES.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s, fontWeight = if (s == size) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onPick(s) }
                )
            }
        }
    }
}

/** A small "Size M" tag for cards and dialogs. */
@Composable
fun SizeTag(size: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    // The same height as the reaction chips next to it (CHIP_HEIGHT, docs/STYLE.md).
    Surface(color = cs.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = modifier.heightIn(min = CHIP_HEIGHT)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = CHIP_HEIGHT)) {
            Text(
                tr("Size %s", size), style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}
