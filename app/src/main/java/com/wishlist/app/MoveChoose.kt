package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Move or copy, the action first (owner, 21 November 2026, after his friend's old version and a recording where Move
 * was invisible): "Move or copy?" with two choice rows, each with one line on what it does, then the wishlist picker
 * titled "Move to…" or "Copy to…". The wish (or the chosen wishes) stays in sight in both steps.
 */

/** Whether [ids] may leave the open list: not a co-op wish someone reserved (it can only be copied). */
internal fun mayMove(ids: Set<Long>): Boolean {
    val from = MoveFlow.sourceKey()
    return !MoveAcross.isCollab(from) || ids.all { MoveAcross.mayLeave(Shared.reserved[it]) }
}

/** The wishes being moved or copied, from the open list. */
private fun wishesOf(ids: Set<Long>): List<Wish> = Repo.wishes.filter { it.id in ids }

/** Step 1: "Move or copy?" as two choice rows; Move greyed with the reason when someone reserved the wish. */
@Composable
fun MoveOrCopyDialog(ids: Set<Long>, onChoose: (copy: Boolean) -> Unit, onClose: () -> Unit) {
    val here = shortName(MoveFlow.nameOf(MoveFlow.sourceKey()))
    val canMove = mayMove(ids)
    val coop = MoveAcross.isCollab(MoveFlow.sourceKey())
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Move or copy?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MovingWishes(ids)
                SheetGroup(listOf(
                    {
                        ChoiceRow(
                            AppIcons.Move, tr("Move"),
                            when {
                                !canMove -> if (ids.size == 1) tr("Someone reserved it, so it stays. You can copy it.") else tr("Someone reserved one, so they stay. You can copy them.")
                                coop -> tr("Takes it off “%s” for everyone", here)
                                else -> tr("Takes it off “%s”", here)
                            },
                            enabled = canMove
                        ) { onChoose(false) }
                    },
                    { ChoiceRow(AppIcons.Copy, tr("Copy"), tr("Keeps it on both lists"), enabled = true) { onChoose(true) } }
                ))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** One choice: an icon in its tile, the word and one line under it; greyed and not tappable when [enabled] is false. */
@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, line: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Start), color = cs.onSurface)
            Text(line, style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start), color = cs.onSurfaceVariant)
        }
    }
}

/**
 * What is being moved or copied, at the top of both steps (owner, 21 November 2026: "so at any time it's clear what
 * you're about to move or copy"): one wish as its photo, name and price; several as their photos and "3 wishes".
 */
@Composable
fun MovingWishes(ids: Set<Long>) {
    val ws = wishesOf(ids)
    if (ws.isEmpty()) return
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    @Composable
    fun photo(w: Wish, s: androidx.compose.ui.unit.Dp) = Box(
        Modifier.size(s).clip(RoundedCornerShape(10.dp)).background(look.photoSurface(w.id)), contentAlignment = Alignment.Center
    ) {
        if (w.image.isNotBlank()) WishImage(w.image, Modifier.size(s), ContentScale.Crop) else PlaceholderIcon("gift", s / 2)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ws.size == 1) {
            val w = ws.first()
            photo(w, 48.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(w.label, style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Start), color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                w.price?.let { Text(money(it, w.currency), style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start), color = cs.onSurfaceVariant) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { ws.take(4).forEach { photo(it, 40.dp) } }
            Text(
                tr("%d wishes", ws.size), style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Start), color = cs.onSurface,
                modifier = Modifier.padding(start = 12.dp).weight(1f)
            )
        }
    }
}
