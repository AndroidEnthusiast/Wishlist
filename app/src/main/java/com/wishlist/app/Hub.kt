package com.wishlist.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
 * The dashboard (since 2.64): the app's start screen. At the top a picture you can make your own (the pencil), then
 * "Recently opened" (swipe sideways), "My wishlists" with a + for a new one, and "Group wishlists" (lists you make
 * together with other givers, for someone else) with a + for a new one. Tapping a list opens it; Back returns here.
 */

/** One list on the dashboard: your own ([mineId]) or a group wishlist ([code]). */
private class HubItem(val key: String, val name: String, val detail: String, val pictures: List<String>, val group: Boolean, val open: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    snack: SnackbarHostState,
    onOpenMine: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNewMine: () -> Unit,
    onNewGroup: () -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    // Your own lists are read again whenever one changes (mineVersion) or the open one changes.
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    val mineItems = mine.map { d ->
        val n = if (d.name == "My wishlist") tr(d.name) else d.name
        val count = d.wishes.size
        val total = totalValue(d.wishes)
        val detail = (if (count == 1) tr("1 wish") else tr("%d wishes", count)) +
            if (total != null) " · " + tr("Total value %s", total) else ""
        HubItem("m:" + d.id, n, detail, d.wishes.map { it.image }.filter { it.isNotBlank() }.take(3), false) { onOpenMine(d.id) }
    }
    val groupItems = Shared.lists.map { l ->
        HubItem("s:" + l.code, l.name, tr("Group wishlist"), emptyList(), true) { onOpenGroup(l.code) }
    }
    val all = (mineItems + groupItems).associateBy { it.key }
    val recentItems = Repo.recent.entries.sortedByDescending { it.value }.mapNotNull { all[it.key] }.take(10)

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Home"), fontWeight = look.titleWeight) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().lookBackdrop(look).verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Hero(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (recentItems.isNotEmpty()) {
                HubTitle(tr("Recently opened"), small = true)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentItems.size) { i -> RecentCard(recentItems[i]) }
                }
            }

            HubTitle(tr("My wishlists"), addLabel = tr("New wishlist"), onAdd = onNewMine)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                mineItems.forEach { ListRow(it) }
            }

            HubTitle(tr("Group wishlists"), addLabel = tr("New group wishlist"), onAdd = onNewGroup)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (groupItems.isEmpty()) {
                    Text(
                        tr("Collect gift ideas for someone together with other givers, and see who buys what."),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
                groupItems.forEach { ListRow(it) }
            }
        }
    }
}

/**
 * What all wishes with a price on a list add up to, for example "€245.50". Wishes in different currencies are added
 * up per currency ("€120.00 + $30.00"). Null when no wish has a price.
 */
fun totalValue(wishes: List<Wish>): String? {
    val sums = wishes.filter { it.price != null }.groupBy { it.currency }
        .map { (cur, ws) -> cur to ws.sumOf { it.price ?: 0.0 } }
    if (sums.isEmpty()) return null
    return sums.sortedByDescending { it.second }.joinToString(" + ") { (cur, sum) -> money(sum, cur) }
}

/** A section title, with a small round + button when something can be added. */
@Composable
private fun HubTitle(text: String, addLabel: String? = null, small: Boolean = false, onAdd: (() -> Unit)? = null) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (small) 12.dp else 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = look.titleWeight,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            Box(
                Modifier.size(30.dp).lookChip(look, on = true).clickable(onClickLabel = addLabel, onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Add, addLabel, tint = look.chipContent(true), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A list in "My wishlists" or "Group wishlists": a small picture, its name and how many wishes it has. */
@Composable
private fun ListRow(item: HubItem) {
    val look = LocalLook.current
    val id = item.key.hashCode().toLong()
    Row(
        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).clickable(onClick = item.open)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListThumb(item, id, Modifier.size(64.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                item.name, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id), maxLines = 1)
        }
    }
}

/** A card in "Recently opened". */
@Composable
private fun RecentCard(item: HubItem) {
    val look = LocalLook.current
    val id = item.key.hashCode().toLong()
    Column(
        Modifier.width(112.dp).lookCard(look, id, look.cardShape(id, small = true)).clickable(onClick = item.open)
            .padding(6.dp)
    ) {
        ListThumb(item, id, Modifier.fillMaxWidth().height(64.dp))
        Text(
            item.name, style = MaterialTheme.typography.labelLarge, color = look.cardInk(id),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
        )
        Text(
            // Only the number of wishes here: the total value doesn't fit this small card.
            item.detail.substringBefore(" · "), style = MaterialTheme.typography.labelSmall, color = look.cardMuted(id), maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        )
    }
}

/** The picture of a list: its first wish's photo, or a gift (your own) or people (a group wishlist). */
@Composable
private fun ListThumb(item: HubItem, id: Long, modifier: Modifier) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.lookMat(look, shape, look.pictureBg(id, false)), contentAlignment = Alignment.Center) {
        val pic = item.pictures.firstOrNull()
        if (pic != null) {
            AsyncImage(
                model = pic, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        } else {
            Text(if (item.group) "👥" else "🎁", fontSize = 28.sp)
        }
    }
}

/**
 * The cover photo at the top of the dashboard: your own photo, or the theme's colours with a gift. The pencil lets you
 * choose a photo, take one, or go back to the colours.
 */
@Composable
private fun Hero(modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var menu by remember { mutableStateOf(false) }
    // The cover's shape (width / height): 16 dp from both sides, 150 dp high, as in the editor.
    val coverShape = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120) / 150f
    // A new photo is adjusted first (HeroEditor) and only becomes the picture after Save.
    var pending by remember { mutableStateOf<String?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }
    // A photo from the camera goes to a temporary file first, then into the app like a chosen photo.
    val cameraFile = remember { File(File(ctx.cacheDir, "share").apply { mkdirs() }, "hero_camera.jpg") }
    val cameraUri: Uri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { ImageStore.import(ctx, cameraUri)?.let { pending = it } }
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(150.dp)
            .then(if (look.classic) Modifier.clip(shape) else Modifier.lookCard(look, 1L, shape))
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
    ) {
        if (Repo.hero.isNotEmpty()) {
            HeroPicture(Repo.hero, Repo.heroEdit, Modifier.fillMaxSize(), cut = Repo.heroCut)
        } else {
            Text("🎁", fontSize = 56.sp, modifier = Modifier.align(Alignment.Center))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).size(32.dp).clip(CircleShape)
                .background(cs.surface.copy(alpha = 0.85f))
                .clickable(onClickLabel = tr("Change the cover photo")) { menu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Edit, tr("Change the cover photo"), tint = cs.onSurface, modifier = Modifier.size(16.dp))
        }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(tr("Cover photo")) },
            text = {
                Column {
                    HeroChoice(tr("Choose a photo")) {
                        menu = false
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    HeroChoice(tr("Take a photo")) {
                        menu = false
                        try {
                            camera.launch(cameraUri)
                        } catch (e: Exception) {
                        }
                    }
                    if (Repo.hero.isNotEmpty()) {
                        HeroChoice(tr("Edit cover photo")) { menu = false; adjusting = true }
                        HeroChoice(tr("Remove the photo")) { menu = false; Repo.setHero(ctx, "") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text(tr("Cancel")) } }
        )
    }
    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(),
            onSave = { chosen, e ->
                pending = null
                // The cover is cut out exactly as it was in the frame, then saved.
                scope.launch {
                    Repo.setHero(ctx, chosen, e, renderCover(ctx, chosen, e, coverShape) ?: "")
                    // Another photo was picked in the editor: the first one isn't needed.
                    if (chosen != img) ImageStore.delete(ctx, img)
                }
            },
            // Cancelled: the copied photo isn't needed.
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
    if (adjusting && Repo.hero.isNotEmpty()) {
        HeroEditor(
            Repo.hero, Repo.heroEdit,
            onSave = { chosen, e ->
                adjusting = false
                scope.launch { Repo.setHero(ctx, chosen, e, renderCover(ctx, chosen, e, coverShape) ?: "") }
            },
            onCancel = { adjusting = false }
        )
    }
}

@Composable
private fun HeroChoice(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp)
    )
}

/** A new wishlist of your own: a name (required) and notes about what it's for. */
@Composable
fun NewListDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    // Optional: wishes from your other lists, copied in when the list is made.
    var picked by remember { mutableStateOf(listOf<Wish>()) }
    var picking by remember { mutableStateOf(false) }
    val canCopy = remember { canCopyFromOthers(ctx, null) }
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("New wishlist")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.take(60); if (it.isNotBlank()) missing = false },
                    label = { Text(tr("Name*")) }, singleLine = true, isError = missing,
                    supportingText = if (missing) ({ Text(tr("What is this wishlist called?")) }) else null,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                OutlinedTextField(
                    notes, { notes = it.take(ABOUT_MAX) },
                    label = { Text(tr("Notes")) }, placeholder = { Text(tr("What is this wishlist for?")) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                if (canCopy) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add wishes from your other lists"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                when (picked.size) {
                                    0 -> tr("Optional")
                                    1 -> tr("1 wish chosen")
                                    else -> tr("%d wishes chosen", picked.size)
                                },
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
                else {
                    val id = Repo.createMine(ctx, name, notes)
                    Repo.copyInto(ctx, id, picked)
                    onCreated(id)
                }
            }) { Text(tr("Create")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
    if (picking) {
        CopyWishesDialog(null, onDone = { picked = it; picking = false }, onClose = { picking = false })
    }
}
