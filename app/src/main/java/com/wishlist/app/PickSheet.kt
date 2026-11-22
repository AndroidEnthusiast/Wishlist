package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Pickers are bottom sheets (owner, 22 November 2026, after "Move or copy?" looked out of place as a centred pop-up:
 * "make sure they are all built and that it looks flawless"; docs/STYLE.md "Dialogs"). Every "choose what to do" and
 * every "pick a list, a wish or a person" is a [PickSheet]: the same panel as a wish's ⋮ (MenuSheet, WishSheet), the
 * title at the top, what it's about under it, the rows in a SheetGroup, no Cancel (swipe down or tap outside, like
 * every menu). Picking several (friends to invite, gifts you got) ends with one filled button. Centred pop-ups stay
 * for "are you sure?", short messages and forms with typing.
 */

/**
 * A picker as a bottom sheet. [onBack]: a second step (Move to… after Move or copy): a ← in the title and Back go to
 * the step before instead of closing. [top]: what it is about (the wish). [note]: one line under it. [action] with
 * [onAction]: the filled button at the bottom when you pick several. While [busy] it can't be closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickSheet(
    title: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    top: (@Composable () -> Unit)? = null,
    note: String? = null,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    busy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { busy.not() || it != androidx.compose.material3.SheetValue.Hidden }),
        containerColor = cs.surface,
        properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnBackPress = onBack == null && !busy)
    ) {
        if (onBack != null) BackHandler(enabled = !busy) { onBack() }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = if (onBack != null) 8.dp else 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) IconButton(onClick = onBack, enabled = !busy) { Icon(AppIcons.ArrowBack, tr("Back")) }
                // The question is the heading (titleLarge, the style's title weight); what it's about sits under it.
                Text(
                    title, style = MaterialTheme.typography.titleLarge, fontWeight = LocalLook.current.titleWeight, color = cs.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)
                )
            }
            top?.let { Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) { it() } }
            if (note != null) Text(
                note, style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start), color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
            )
            // The rows scroll inside when there are many; the button stays in sight below them.
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = content
            )
            if (action != null && onAction != null) Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Button(onClick = onAction, enabled = actionEnabled && !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(action)
                }
            }
        }
    }
}

/** A line of text inside a picker's rows (empty states, a hint), lined up with the rows' words. */
@Composable
fun PickNote(text: String, error: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start),
        color = if (error) cs.error else cs.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

/** A small heading above a group of rows (a wishlist's name above its wishes). */
@Composable
fun PickHeading(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 10.dp)
    )
}

/** A wish's small picture at the start of a picker row (in place of an icon tile), 32 dp like the tiles. */
@Composable
fun PickWishPhoto(w: Wish) {
    val look = LocalLook.current
    Box(
        Modifier.size(32.dp).clip(Squircle(8.dp)).background(look.photoSurface(w.id)),
        contentAlignment = Alignment.Center
    ) {
        if (w.image.isNotBlank()) WishImage(w.image, Modifier.size(32.dp), androidx.compose.ui.layout.ContentScale.Crop)
        else PlaceholderIcon("gift", 16.dp, look.photoInk)
    }
}

