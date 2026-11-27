package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* A list's cover photo (see Covers.kt): the cover at the top of a list with its ⋮, the summary card, the cover part of
 * the list's settings and the editor's Preview. */

/** The cover's shape (width / height): the width of the list (16 dp from both sides), 150 dp high. */
@Composable
fun coverAspect(): Float = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120) / 150f

/** The cover photo at the top of a list, when one was chosen. */
@Composable
fun ListCoverPicture(cover: ListCover, modifier: Modifier = Modifier) {
    HeroPicture(
        cover.image, cover.edit,
        modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)), cut = cover.cut
    )
}

/**
 * The narrow card at the top of a list: how many wishes and their total value (the total moved here from Home, 2.89).
 * Since 2.111 (owner) the number takes less room than the total, both centred in their part, and the number pulses
 * when it goes up or down. [occasion] ("Birthday · in 3 weeks", owner, 2.124) shows as a line below them; with
 * [counts] false (gift lists, or the summary switched off) the card shows only that line.
 */
@Composable
fun ListSummaryCard(wishes: List<Wish>, modifier: Modifier = Modifier, occasion: String? = null, counts: Boolean = true, levels: Boolean = false) {
    val look = LocalLook.current
    val id = 11L
    val total = totalValue(wishes)
    // The level of your own list's total ([levels]: your own list, and App settings > Display > Value levels on).
    val level = if (levels && counts) ValueLevels.ofList(wishes) else null
    val count = wishes.size
    val pulse = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count == last) return@LaunchedEffect
        last = count
        pulse.snapTo(1f)
        pulse.animateTo(1.35f, tween(140, easing = FastOutSlowInEasing))
        pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    if (!counts && occasion == null) return
    Column(
        modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (counts) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    count.toString(), style = MaterialTheme.typography.titleLarge.number(look), fontWeight = FontWeight.Bold, color = look.cardInk(id),
                    modifier = Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                )
                Text(if (count == 1) tr("Wish") else tr("Wishes"), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
            }
            Box(Modifier.width(1.dp).height(32.dp).background(look.cardMuted(id).copy(alpha = 0.3f)))
            Column(Modifier.weight(0.62f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (level != null) ValueLevelTag(level, small = false, modifier = Modifier.padding(bottom = 2.dp))
                // Never cut off (2.209): a little smaller instead.
                FitText(total ?: "–", MaterialTheme.typography.titleLarge.number(look).copy(fontWeight = FontWeight.Bold), look.cardInk(id), Modifier.fillMaxWidth().levelLine(level, 96.dp))
                Text(tr("Total value"), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
            }
        }
        if (occasion != null) {
            if (counts) Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(look.cardMuted(id).copy(alpha = 0.3f)))
            Text(
                occasion, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = look.cardInk(id),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The top of a list, above its filters: the cover photo (only when chosen, with its ⋮ menu) and, on your own lists,
 * the summary card.
 */
@Composable
fun ListHeader(wishes: List<Wish>, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val cover = remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cover != null) Box {
            ListCoverPicture(cover)
            CoverMenu(cover, snack, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        // The summary card moved to the part that stays at the top while scrolling (MainActivity, 2.111).
    }
}

/**
 * The ⋮ in the bottom right corner of a list's cover photo (owner, 2.110: there was no way to remove it from the list
 * itself). Change: Choose a photo / Take a photo / Adjust this photo (the editor). Delete: gone at once, with Undo.
 */
@Composable
private fun CoverMenu(cover: ListCover, snack: SnackbarHostState, modifier: Modifier = Modifier) {
    // What the menu does, and the editor (CoverModel.kt); the photo picker and camera are shared with CoverSection.
    val m = rememberCover(snack)
    val pickers = rememberCoverPickers(m)

    Box(modifier) {
        // A dark round backing so the dots show on any photo.
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { m.menu = true },
            contentAlignment = Alignment.Center
        ) { Icon(AppIcons.MoreVert, tr("Cover photo options"), tint = Color.White, modifier = Modifier.size(22.dp)) }
        DropdownMenu(expanded = m.menu, onDismissRequest = { m.menu = false }) {
            DropdownMenuItem(text = { Text(tr("Change")) }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = { m.menu = false; m.change = true })
            DropdownMenuItem(text = { Text(tr("Delete")) }, leadingIcon = { Icon(AppIcons.Delete, null) }, danger = true, onClick = { m.menu = false; m.delete() })
        }
        DropdownMenu(expanded = m.change, onDismissRequest = { m.change = false }) {
            DropdownMenuItem(text = { Text(tr("Choose a photo")) }, leadingIcon = { Icon(AppIcons.Gallery, null) }, onClick = {
                m.change = false; pickers.choose()
            })
            DropdownMenuItem(text = { Text(tr("Take a photo")) }, leadingIcon = { Icon(AppIcons.Camera, null) }, onClick = {
                m.change = false
                pickers.take()
            })
            DropdownMenuItem(text = { Text(tr("Adjust this photo")) }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = { m.change = false; m.adjusting = true })
        }
    }

    val preview: @Composable (String, HeroEdit, () -> Unit) -> Unit = { img, e, close -> CoverPreview(img, e, close) }
    m.pending?.let { img ->
        HeroEditor(
            img, HeroEdit(), preview = preview,
            onSave = { chosen, e -> m.savePending(img, chosen, e) },
            onCancel = { m.dropPending(img) }
        )
    }
    if (m.adjusting) {
        HeroEditor(
            cover.image, cover.edit, preview = preview,
            onSave = { chosen, e -> m.adjusting = false; m.save(chosen, e) },
            onCancel = { m.adjusting = false }
        )
    }
}

/**
 * List settings > Cover photo: none by default. Choose or take a photo, adjust it in the editor (with Preview), and
 * later edit or remove it.
 */
@Composable
fun CoverSection(snack: androidx.compose.material3.SnackbarHostState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val key = ListCovers.openKey()
    val cover = remember(ListCovers.version, key) { ListCovers.get(ctx, key) }
    // What its buttons do, and the editor (CoverModel.kt).
    val m = rememberCover(snack)
    val pickers = rememberCoverPickers(m)

    Text(
        tr("Personalise your wishlist with a cover photo at the top."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
    if (cover != null) ListCoverPicture(cover, Modifier.padding(vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { pickers.choose() }) {
            Text(if (cover == null) tr("Choose a photo") else tr("New photo"))
        }
        OutlinedButton(onClick = { pickers.take() }) { Text(tr("Take a photo")) }
    }
    if (cover != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { m.adjusting = true }) { Text(tr("Edit cover photo")) }
            TextButton(danger = true, onClick = { m.remove() }) { Text(tr("Remove cover photo")) }
        }
    }

    val preview: @Composable (String, HeroEdit, () -> Unit) -> Unit = { img, e, close -> CoverPreview(img, e, close) }
    m.pending?.let { img ->
        HeroEditor(
            img, HeroEdit(), preview = preview,
            onSave = { chosen, e -> m.savePending(img, chosen, e) },
            onCancel = { m.dropPending(img) }
        )
    }
    if (m.adjusting && cover != null) {
        HeroEditor(
            cover.image, cover.edit, preview = preview,
            onSave = { chosen, e -> m.adjusting = false; m.save(chosen, e) },
            onCancel = { m.adjusting = false }
        )
    }
}

/**
 * Preview (in the editor): the top of this list as it will look with the photo as it is now in the frame, with the
 * summary card and the first wishes under it. Nothing is saved; "Back to editing" returns to the editor.
 */
@Composable
fun CoverPreview(img: String, edit: HeroEdit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val wishes = Repo.wishes
    Surface(Modifier.fillMaxSize().lookPage(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Text(
                Repo.listName, style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The photo exactly as in the frame now (worked out from the edit, as on the list).
                HeroPicture(img, edit, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)))
                val occ = remember(Occasions.version) { Occasions.line(Occasions.ofOpen(ctx)) }
                ListSummaryCard(wishes, occasion = occ, counts = Shared.active == null, levels = Shared.active == null && Show[ValueLevels.SHOW])
                wishes.take(4).forEach { w ->
                    val id = w.id
                    Row(
                        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(look.pictureBg(id, w.image.isNotBlank())),
                            contentAlignment = Alignment.Center
                        ) {
                            if (w.image.isNotBlank()) WishImage(w.image, Modifier.fillMaxSize().padding(4.dp))
                            else PlaceholderIcon("gift", 20.dp)
                        }
                        Text(
                            w.label, style = MaterialTheme.typography.bodyLarge, color = look.cardInk(id), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Button(onClick = onClose) { Text(tr("Back to editing")) }
            }
        }
    }
}
