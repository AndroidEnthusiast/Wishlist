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
import androidx.compose.foundation.horizontalScroll
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
 * "Personal / Co-op" at the top of a new wishlist (owner, 21 November 2026; was "Whose wishlist? My own / With
 * others", then "Personal / Shared", which read as "a list I shared"): one person and two people, and one line under
 * them about the chosen one. Co-op = a wishlist made together with others (in the code: a shared or collab list).
 */
@Composable
fun WhoIsItFor(someoneElse: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!someoneElse, { if (someoneElse) onChange(false) }, { Text(tr("Personal")) },
            leadingIcon = { Icon(AppIcons.Person, null, Modifier.size(18.dp)) })
        FilterChip(someoneElse, { if (!someoneElse) onChange(true) }, { Text(tr("Co-op")) },
            leadingIcon = { Icon(AppIcons.Group, null, Modifier.size(18.dp)) })
    }
    // What it is, on one line (owner, 21 November 2026: nothing about reserving; it shrinks a little rather than wrap).
    FitText(
        if (someoneElse) tr("Collaborate: everyone you invite adds wishes.") else tr("Only you add wishes to it."),
        MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth(), androidx.compose.ui.text.style.TextAlign.Start
    )
}

/**
 * "Add wishes from other wishlists" in the New wishlist form (owner, 21 November 2026: not a loose "+ Add" line): a
 * card like the settings rows, with how many are chosen and their photos.
 */
@Composable
internal fun CopyWishesCard(picked: List<Wish>, onRemove: (Wish) -> Unit = {}, onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val quiet = look.design == "quiet"
    val fill = if (quiet) look.groupFill() else androidx.compose.ui.graphics.lerp(sheetColor(look, cs.surface), cs.onSurface, 0.08f)
    val shape = if (quiet) Squircle(18.dp) else RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(fill)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(if (quiet) Squircle(10.dp) else RoundedCornerShape(10.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Gift, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(tr("Add wishes from other wishlists"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text(
                    when (picked.size) { 0 -> tr("None chosen yet"); 1 -> tr("1 wish chosen"); else -> tr("%d wishes chosen", picked.size) },
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        // The chosen wishes, each with a ✕ to take it out again (owner, 21 November 2026). All of them, in one row
        // that scrolls sideways, so the card never grows taller.
        if (picked.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            picked.forEach { w ->
                // The ✕ sits inside the 52 dp tap area (it was clipped at the top in 2.273's first pictures).
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = tr("Take out %s", w.label)) { onRemove(w) },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Box(Modifier.padding(bottom = 1.dp).size(44.dp).clip(RoundedCornerShape(10.dp)).background(look.photoSurface(w.id)), contentAlignment = Alignment.Center) {
                        if (w.image.isNotBlank()) WishImage(w.image, Modifier.size(44.dp), ContentScale.Crop)
                        else PlaceholderIcon("gift", 22.dp)
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).size(18.dp).clip(CircleShape).background(cs.inverseSurface),
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Close, null, tint = cs.inverseOnSurface, modifier = Modifier.size(12.dp)) }
                }
            }
        }
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
 * A new wishlist of your own: a name (required), optionally the description shown at the top of the list, wishes from
 * your other lists, and Privacy. In the full-width form sheet (2.223). [top]: what goes above the fields (the My own / With others chips of
 * [NewWishlistSheet]).
 */
@Composable
fun NewListDialog(onCreated: (String) -> Unit, onClose: () -> Unit, top: (@Composable () -> Unit)? = null) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    // Optional: wishes from your other lists, copied in when the list is made.
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }
    // Who can see it (owner, 21 November 2026): starts on App settings' choice; changing it is for this list only.
    var who by remember { ListVisibility.loadDefault(ctx); mutableStateOf(ListVisibility.newDefault) }
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
                // No occasion here (owner, 21 November 2026: everything on one page; it is in Wishlist settings).
                // Wishes from other lists, above Privacy (owner, 21 November 2026).
                if (canCopy) CopyWishesCard(picked, onRemove = { w -> picked = picked - w }) { picking = true }
                // Only with online sharing set up: without it every list stays on the phone.
                if (Config.enabled) WhoPicker(who) { who = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) missing = true
                else onCreated(createOwnWishlist(ctx, name, notes, null, picked, if (Config.enabled) who else null))
            }) { Text(tr("Create")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
        unsaved = name.isNotBlank() || notes.isNotBlank() || picked.isNotEmpty()
    )
    if (picking) {
        CopyWishesDialog(null, onDone = { picked = it; picking = false }, onClose = { picking = false }, chosen = picked)
    }
}
