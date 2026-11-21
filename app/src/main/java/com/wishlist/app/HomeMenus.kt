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
 * What a list on Home can do (split out of Hub.kt in the rebuild, owner 18 November 2026): its ⋮ sheet, the
 * Visibility sheet, and the Edit name / Delete / Leave dialogs ([HomeListDialogs]). The actions come from [HomeModel].
 */

/**
 * A wishlist's ⋮ (2.212, owner 15 November 2026: the drop-down menu "looks horrible"): a bottom sheet like a wish's,
 * with the list's picture and name on top, its actions grouped (Share, Edit name, Visibility with the current choice
 * under it) and the one that removes (Delete wishlist, Leave wishlist) in red on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListMenuSheet(item: HubItem, actions: List<Triple<String, Boolean, () -> Unit>>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val code = item.key.substringAfter(':')
    // Always the full-width sheet (owner, 17 November 2026: the small pop-up "looks cheap"; it replaced 2.218's).
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(if (look.design == "quiet") Squircle(14.dp) else RoundedCornerShape(12.dp))
                        .background(look.pictureBg(item.key.hashCode().toLong(), item.picture != null)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.picture != null) WishImage(item.picture, Modifier.fillMaxSize(), ContentScale.Crop)
                    else PlaceholderIcon(if (item.group) "group" else "gift", 28.dp)
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.sub ?: wishesLabel(item.count), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) {
                    IconButton(onClick = onClose) { Icon(AppIcons.Close, tr("Close")) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val visibility = remember(code, ListVisibility.version, Repo.mineVersion) {
                when (ListVisibility.of(ctx, code)) {
                    Who.ME -> tr("Only me"); Who.FRIENDS -> tr("My friends"); Who.LINK -> tr("Friends + anyone with the link")
                }
            }
            fun row(a: Triple<String, Boolean, () -> Unit>): @Composable () -> Unit = {
                val (label, danger, act) = a
                val icon = when (label) {
                    tr("Share") -> AppIcons.Share
                    tr("Edit name") -> AppIcons.Edit
                    tr("Privacy") -> AppIcons.Lock
                    tr("Leave wishlist") -> AppIcons.Close
                    else -> AppIcons.Delete
                }
                SheetRow(
                    { Icon(icon, null, tint = cs.onSurfaceVariant) }, label, { onClose(); act() }, danger = danger,
                    detail = if (label == tr("Privacy")) visibility else null
                )
            }
            val (removing, doing) = actions.partition { it.second }
            if (doing.isNotEmpty()) SheetGroup(doing.map { row(it) })
            if (removing.isNotEmpty()) SheetGroup(removing.map { row(it) })
        }
    }
}

/** The Visibility sheet of a wishlist (2.212): the list's name and the three choices; a tap chooses and closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VisibilitySheet(item: HubItem, snack: SnackbarHostState, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                tr("Privacy"), style = MaterialTheme.typography.titleLarge, fontWeight = LocalLook.current.titleWeight,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                tr("Who sees “%s”", item.name), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp)
            )
            WhoChoices(item.key.substringAfter(':'), snack, sheet = true, onChosen = onClose)
        }
    }
}

/** The dialogs a list's ⋮ opens (their state and actions are in [HomeModel]): a shared list's invitation, Visibility,
 * Edit name, Delete and Leave. */
@Composable
internal fun HomeListDialogs(model: HomeModel, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    model.sharingCollab?.let { l -> androidx.compose.runtime.key(l.code) { SharedShareDialog(l) { model.sharingCollab = null } } }
    // "Visibility" (2.105 as "Visible to"; a sheet since 2.212, owner 15 November 2026): the three choices as rows,
    // a tap chooses and closes.
    model.visibleOf?.let { item -> VisibilitySheet(item, snack) { model.visibleOf = null } }
    model.renaming?.let { item ->
        var n by remember(item.key) { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { model.renaming = null },
            title = { Text(tr("Edit name")) },
            text = {
                OutlinedTextField(
                    n, { n = it.take(60) }, singleLine = true, label = { Text(tr("Name*")) },
                    keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = n.isNotBlank(), onClick = { model.renaming = null; model.rename(item, n) }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { model.renaming = null }) { Text(tr("Cancel")) } }
        )
    }
    model.deleting?.let { item ->
        val group = item.key.startsWith("s:")
        val code = item.key.substringAfter(':')
        var busy by remember(item.key) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) model.deleting = null },
            title = { Text(if (group) tr("Delete “%s” for everyone?", item.name) else tr("Delete “%s”?", item.name)) },
            text = {
                Text(
                    if (group) tr("It will be deleted for everyone, with all wishes and reservations. This can’t be undone.")
                    else tr("Deleting this wishlist will move all its wishes to the bin, where they can be restored within 30 days.") +
                        groupNote(ctx, code)
                )
            },
            confirmButton = {
                TextButton(danger = true, enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val msg = model.delete(item)
                        busy = false
                        model.deleting = null
                        snack.showToast(msg)
                    }
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { model.deleting = null }) { Text(tr("Cancel")) } }
        )
    }
    model.leaving?.let { item ->
        AlertDialog(
            onDismissRequest = { model.leaving = null },
            title = { Text(tr("Leave “%s”?", item.name)) },
            text = { Text(tr("It disappears from your lists. It stays for everyone else, and you can join again with the invite link.")) },
            confirmButton = { TextButton(onClick = { model.leaving = null; model.leave(item) }) { Text(tr("Leave")) } },
            dismissButton = { TextButton(onClick = { model.leaving = null }) { Text(tr("Cancel")) } }
        )
    }
}
