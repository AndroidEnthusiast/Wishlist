package com.wishlist.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/*
 * "New wishlist" (split out of Hub.kt in the rebuild, owner 18 November 2026): one full-width sheet with My own /
 * With others, and the form for a new wishlist of your own.
 */

/**
 * "My own / With others" at the top of a new wishlist (owner, 17 November 2026: collab wishlists folded into "New
 * wishlist"). With others = a wishlist made together with others, where everyone on it sees reservations.
 */
@Composable
fun WhoIsItFor(someoneElse: Boolean, onChange: (Boolean) -> Unit) {
    Text(tr("Whose wishlist?"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!someoneElse, { if (someoneElse) onChange(false) }, { Text(tr("My own")) })
        FilterChip(someoneElse, { if (!someoneElse) onChange(true) }, { Text(tr("With others")) })
    }
}

/**
 * "New wishlist" (owner, 17 November 2026): one full-width sheet with My own / With others at the top; the chips swap
 * the fields below without the sheet moving or changing height. [withOthers]: the choice it opens on.
 */
@Composable
fun NewWishlistSheet(withOthers: Boolean, onCreatedMine: (String) -> Unit, onCreatedShared: (String) -> Unit, onClose: () -> Unit) {
    var others by remember { mutableStateOf(withOthers) }
    val chips: @Composable () -> Unit = { if (Config.enabled) WhoIsItFor(others) { others = it } }
    FormSheetHost(onClose) {
        if (others) NewSharedDialog(onCreatedShared, onClose, top = chips)
        else NewListDialog(onCreatedMine, onClose, top = chips)
    }
}

/**
 * A new wishlist of your own: a name (required) and, optionally, the note for friends shown at the top of the list.
 * In the full-width form sheet (2.223). [top]: what goes above the fields (the My own / With others chips of
 * [NewWishlistSheet]).
 */
@Composable
fun NewListDialog(onCreated: (String) -> Unit, onClose: () -> Unit, top: (@Composable () -> Unit)? = null) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var occ by remember { mutableStateOf<Occasion?>(null) }
    var occMissing by remember { mutableStateOf(false) }
    // Optional: wishes from your other lists, copied in when the list is made.
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }
    FormSheet(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("New wishlist")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top?.invoke()
                OutlinedTextField(
                    name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                    label = { Text(tr("Wishlist name*")) }, singleLine = true, isError = missing,
                    supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                OutlinedTextField(
                    notes, { notes = it.take(ABOUT_MAX) },
                    // "Description" (owner, 2.125; it was "Note for friends", and "Notes" before 2.113).
                    label = { Text(tr("Description")) },
                    supportingText = { Text(tr("Shown at the top of your wishlist.")) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                // The occasion (owner, 2.124): pick one first; only then its date is asked.
                Text(tr("Occasion"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OccasionPicker(gift = false, value = occ) { occ = it; occMissing = false }
                if (occMissing) Text(
                    if (occ?.kind == "own" && occ?.name.isNullOrBlank()) tr("Give it a name and a date.") else tr("Choose the date."),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                if (canCopy) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add wishes from your other lists"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            // No "Optional" (owner, 2.125: it suggested the rest isn't optional).
                            if (picked.isNotEmpty()) Text(
                                if (picked.size == 1) tr("1 wish chosen") else tr("%d wishes chosen", picked.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) missing = true
                else if (!Occasions.complete(occ)) occMissing = true
                else {
                    onCreated(createOwnWishlist(ctx, name, notes, occ, picked))
                }
            }) { Text(tr("Create")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
    if (picking) {
        CopyWishesDialog(null, onDone = { picked = it; picking = false }, onClose = { picking = false })
    }
}
