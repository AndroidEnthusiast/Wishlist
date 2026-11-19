package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/* The Add / Edit wish form (see WishForm.kt): the photo block, the small toggles, Take or Choose photo, More details. */

/**
 * The picture of a wish (owner, 7 November 2026: not too small, not overpowering): a 128 dp square tile on the left.
 * Empty: a dashed tile with a plus and "Add photo". Filled: the photo fills the tile (wish photos are square since
 * 2.144; a picture from a shop page sits whole on white), tap to change, ✕ to remove.
 */
@Composable
fun ImageBlock(image: String, onPick: () -> Unit, onRemove: () -> Unit, big: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline
    // [big]: the wish form's photo (2.212, owner: the photo first), the width of the form and 200 dp tall.
    // 168 dp since 16 November 2026 (owner: the form felt full), 200 before.
    val tile = (if (big) Modifier.fillMaxWidth().height(168.dp) else Modifier.size(128.dp))
        .clip(if (LocalLook.current.design == "quiet") Squircle(if (big) 20.dp else 16.dp) else RoundedCornerShape(16.dp))
    val look = LocalLook.current
    if (image.isEmpty() && look.design == "quiet") {
        // Quiet (2.207): the light photo surface with a camera in a soft circle of your colour; no dashed box.
        Column(
            tile.background(look.photoSurface(0)).semantics { contentDescription = tr("Add photo") }.clickable(onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                PhotoIcon(cs.onPrimaryContainer, 24.dp)
            }
            Text(tr("Add photo"), style = MaterialTheme.typography.labelLarge, color = look.photoInk, modifier = Modifier.padding(top = 6.dp))
        }
    } else if (image.isEmpty()) {
        Column(
            tile.background(cs.surfaceVariant)
                .drawBehind {
                    drawRoundRect(
                        color = line, cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    )
                }
                .semantics { contentDescription = tr("Add photo") }
                .clickable(onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(AppIcons.Add, null, Modifier.size(32.dp), tint = cs.onSurfaceVariant)
            Text(tr("Add photo"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
    } else {
        Box(
            tile.background(Color.White)
                .semantics { contentDescription = tr("Change photo") }
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            // A picture that can't be loaded says so, and a tap adds your own (owner, 16 November 2026: it stayed white).
            var failed by remember(image) { mutableStateOf(false) }
            AsyncImage(
                model = secureImage(image), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onError = { failed = true; BrokenPhotos.mark(image) }, onSuccess = { failed = false; BrokenPhotos.clear(image) }
            )
            if (failed) Column(
                Modifier.fillMaxSize().background(look.photoSurface(0)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                PlaceholderIcon("gift", 36.dp, look.photoInk)
                Text(tr("The shop’s picture couldn’t load"), style = MaterialTheme.typography.titleSmall, color = look.photoInk,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Text(tr("Tap to add your own photo"), style = MaterialTheme.typography.bodySmall, color = look.photoInk.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            // ✕ in the corner: 32 dp to see, a 48 dp area to tap (docs/STYLE.md).
            Box(
                Modifier.align(Alignment.TopEnd).size(48.dp)
                    .semantics { contentDescription = tr("Remove photo") }
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

/**
 * A tile you tap to turn something on (2.212, owner 15 November 2026: Top wish and Private "more visible, near the
 * top"): an icon, the name and one short line; on, it fills with [onColour] and its words take [onInk]. Read out as a
 * switch.
 */
@Composable
fun ToggleTile(
    icon: @Composable (Boolean) -> Unit, title: String, line: String, on: Boolean, onColour: Color, onInk: Color,
    modifier: Modifier = Modifier, onToggle: () -> Unit
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val shape = look.soft(16.dp)
    val bg = if (on) onColour else cs.surfaceVariant
    Column(
        modifier.pressScale().then(if (look.design == "chaos") Modifier.lookSurface(look, shape, bg, raised = on) else Modifier.clip(shape).background(bg)
            .then(if (on) Modifier else Modifier.border(1.dp, cs.outlineVariant, shape)))
            .toggleable(on, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { onToggle() })
            .heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        icon(on)
        Text(
            title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = if (on) onInk else cs.onSurface, modifier = Modifier.padding(top = 6.dp)
        )
        Text(line, style = MaterialTheme.typography.bodySmall, color = if (on) onInk.copy(alpha = 0.85f) else cs.onSurfaceVariant)
    }
}

/** Take photo or Choose photo, as a small bottom sheet (2.212). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onTake: () -> Unit, onChoose: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetGroup(listOf(
                { SheetRow({ PhotoIcon(cs.onSurfaceVariant, 22.dp) }, tr("Take photo"), onTake) },
                { SheetRow({ Icon(AppIcons.Gallery, null, tint = cs.onSurfaceVariant) }, tr("Choose photo"), onChoose) }
            ))
        }
    }
}


/** What is set under "More details", in one short line ("Fashion · Size M · 2× · Description"), or "". */
fun detailsSummary(cat: String, qty: Int, size: String, desc: String): String = listOfNotNull(
    cat.takeIf { it.isNotBlank() }?.let { catLabel(it) },
    size.takeIf { it.isNotBlank() }?.let { tr("Size %s", it) },
    if (qty > 1) "${qty}×" else null,
    if (desc.isNotBlank()) tr("Description") else null
).joinToString(" · ")

/** "More details" with what is set, tap to show or hide [content] (owner, 16 November 2026). */
@Composable
fun MoreDetails(summary: String, startOpen: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(startOpen) }
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (open) tr("Hide details") else tr("Show details")) { open = !open }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("More details"), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            if (summary.isNotEmpty() && !open) Text(summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            else if (!open) Text(tr("Category, size, how many, description"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ArrowDropDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.rotate(if (open) 180f else 0f))
    }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
