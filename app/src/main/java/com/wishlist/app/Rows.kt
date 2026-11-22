package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The parts rows are made of, once (owner, 22 November 2026: "six kinds of row that do nearly the same thing" drifted
 * apart, e.g. the invisible lines between rows and grey text). Before, each row drew its own icon tile, its own
 * "label … value ›" or its own choice circle. They look exactly as before (compared picture by picture).
 *  - [IconTile]: an icon in its soft tinted square (menus, sheets, App settings, "+ New …").
 *  - [ValueRow]: a label, its current value in grey and › (Profile, App settings and its pages).
 *  - [RadioRow]: a choice circle, a label and perhaps a line under it (Import, the list you bring to a group).
 */

/**
 * An icon in its 32 dp tinted square, red-tinted for something that removes ([danger]). [recolour]: the icon is
 * painted in the tile's ink whatever its own colour (sheets, App settings); otherwise it gets the ink as its content
 * colour (menus, whose icons take that colour).
 */
@Composable
fun IconTile(danger: Boolean = false, recolour: Boolean = true, icon: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tile = if (danger) cs.error.copy(alpha = 0.14f) else cs.primaryContainer
    val ink = if (danger) cs.error else cs.onPrimaryContainer
    Box(
        Modifier.size(32.dp).clip(Squircle(9.dp)).background(tile)
            .then(
                if (recolour) Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent { drawContent(); drawRect(ink, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop) }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) { Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() } }
    }
}

/**
 * A row that opens a page or a choice: [label], its current [value] in grey (red when [warn]) and ›. With an [icon]
 * (App settings): the icon in its tile in Quiet, on its own in the other style, and 56 dp tall; without: 52 dp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ValueRow(
    label: String, value: String?, modifier: Modifier = Modifier, warn: Boolean = false,
    icon: (@Composable () -> Unit)? = null, onLongClick: (() -> Unit)? = null, minHeight: Dp = if (icon != null) 56.dp else 52.dp,
    // Space inside the row's edges (Profile's rows: 4 dp).
    inset: Dp = 0.dp, onClick: () -> Unit
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = inset, vertical = if (icon != null) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (look.design == "quiet") IconTile(icon = icon) else Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        }
        Text(
            label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
            modifier = if (icon != null) Modifier.padding(start = 14.dp).weight(1f) else Modifier
        )
        if (value != null) Text(
            value, style = MaterialTheme.typography.bodyMedium, color = if (warn) cs.error else cs.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier.weight(1f).padding(start = 16.dp)
        )
        Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

/** A choice circle with its [label] (and a [detail] line under it): tap anywhere on the row to choose it. */
@Composable
fun RadioRow(label: String, selected: Boolean, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
