package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Selecting wishes (2.209, owner 15 November 2026: the "Move to…" pill in the top bar felt forced in). While wishes
 * are ticked, the app's bottom bar makes way for an action bar in the same place and look: Move to · Delete, each an
 * icon with its word. The top bar keeps "2 selected", ✕ and "Select all". The cards' own ⋮, share and bin fade out
 * ([LocalSelecting]), so the tick circle has room and there is one set of buttons. Like Google Photos and Gmail.
 */
val LocalSelecting = compositionLocalOf { false }

/** Tells the app that wishes are being selected, so its bottom bar steps aside for the [SelectionBar]. */
@Composable
fun SelectingFlag(selecting: Boolean) {
    val places = LocalPlaces.current
    DisposableEffect(selecting) {
        places.selecting = selecting
        onDispose { places.selecting = false }
    }
}

/** The action bar while selecting: Move to (null: not offered, e.g. on a collab wishlist with no list to move to) and Delete. */
@Composable
fun SelectionBar(onMove: (() -> Unit)?, onDelete: () -> Unit) {
    FloatingBar {
        if (onMove != null) SelectionAction(AppIcons.Swap, tr("Move or copy…"), onMove)
        SelectionAction(AppIcons.Delete, tr("Delete"), onDelete, Modifier.motionDelete())
    }
}

@Composable
private fun RowScope.SelectionAction(icon: ImageVector, label: String, onClick: () -> Unit, iconMod: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp).then(iconMod))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * A card's own buttons while selecting: invisible and gone for TalkBack too (they keep their place, so nothing
 * jumps). The tick overlay covers them for taps anyway.
 */
@Composable
fun Modifier.hiddenWhileSelecting(): Modifier =
    if (LocalSelecting.current) this.alpha(0f).clearAndSetSemantics { } else this
