package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The occasion picker and dialog (screens). The rules are in Occasion.kt.

/**
 * Picking an occasion (owner: pick one or type your own; only after picking, the date is asked, never both at once).
 * [gift]: the choices of a gift list. [value] null = none. [onChange] gets the new choice (maybe not complete yet:
 * see [Occasions.complete]).
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OccasionPicker(gift: Boolean, value: Occasion?, onChange: (Occasion?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            (if (gift) Occasions.GIFT_KINDS else Occasions.OWN_KINDS).forEach { k ->
                FilterChip(
                    selected = value?.kind == k,
                    onClick = {
                        if (value?.kind == k) onChange(null)
                        else {
                            // Your own birthday fills in from your profile (owner).
                            val d = if (k == "birthday" && !gift) Friends.myBirthday else ""
                            onChange(Occasion(k, date = d))
                        }
                    },
                    label = { Text(Occasions.choiceLabel(k)) },
                    // A little pencil on "Custom": you type your own occasion there (owner, 2.125).
                    trailingIcon = if (k == "own") ({
                        androidx.compose.material3.Icon(AppIcons.Edit, null, modifier = Modifier.size(16.dp))
                    }) else null
                )
            }
        }
        if (value != null) {
            if (value.kind == "own") {
                OutlinedTextField(
                    value.name, { onChange(value.copy(name = it.take(Occasions.NAME_MAX))) },
                    label = { Text(tr("Occasion")) }, placeholder = { Text(tr("Wedding")) }, singleLine = true,
                    keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                )
            }
            if (Occasions.asksDate(value.kind)) {
                DayMonthPicker(Occasions.pickerValue(value), removable = false) { mmdd ->
                    onChange(value.copy(date = if (value.kind == "own") Occasions.ownDate(mmdd) else mmdd))
                }
            } else {
                Text(Occasions.dateText(value), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** List settings > Occasion: the same choices, saved with Save; "Remove" takes it off. */
@Composable
fun OccasionDialog(gift: Boolean, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val start = remember { Occasions.ofOpen(ctx) }
    var o by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Occasion")) },
        text = { OccasionPicker(gift, o) { o = it } },
        confirmButton = {
            TextButton(enabled = Occasions.complete(o), onClick = {
                if (o != start) Occasions.setOpen(ctx, o)
                onClose()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}
