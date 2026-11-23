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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * Move or copy as one bottom sheet (owner, 22 November 2026: the centred pop-up looked out of place; "same sheet,
 * next step"): step 1 "Move or copy?" with the two rows, step 2 "Move to…" / "Copy to…" with your wishlists in the
 * same sheet, ← and Back going to step 1. The wish stays on top in both. Move is greyed with the reason when someone
 * reserved the co-op wish.
 */
@Composable
fun MoveSheet(ids: Set<Long>, picking: Boolean, copy: Boolean, onChoose: (copy: Boolean) -> Unit, onBack: () -> Unit, onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    var newList by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (!picking) {
        val here = shortName(MoveFlow.nameOf(MoveFlow.sourceKey()))
        val canMove = mayMove(ids)
        val coop = MoveAcross.isCollab(MoveFlow.sourceKey())
        PickSheet(tr("Move or copy?"), onClose = onClose, top = { MovingWishes(ids) }) {
            SheetGroup(listOf(
                {
                    SheetRow(
                        { Icon(AppIcons.Move, null, tint = cs.onSurfaceVariant) }, tr("Move"), { onChoose(false) },
                        detail = when {
                            !canMove -> if (ids.size == 1) tr("Someone reserved it, so it stays. You can copy it.") else tr("Someone reserved one, so they stay. You can copy them.")
                            coop -> tr("Takes it off “%s” for everyone", here)
                            else -> tr("Takes it off “%s”", here)
                        },
                        enabled = canMove
                    )
                },
                { SheetRow({ Icon(AppIcons.Copy, null, tint = cs.onSurfaceVariant) }, tr("Copy"), { onChoose(true) }, detail = tr("Keeps it on both lists")) }
            ))
        }
    } else {
        // Your other wishlists (all of them when a co-op wishlist is open), then the other co-op wishlists.
        val here = Shared.active
        val others = androidx.compose.runtime.remember(Repo.mineVersion, Repo.mineIds, here) { Repo.allMine(ctx).filter { here != null || it.id != Repo.openMine } }
        val collabs = Shared.lists.filter { it.code != here }
        PickSheet(
            if (copy) tr("Copy to…") else tr("Move to…"), onClose = onClose, onBack = onBack, top = { MovingWishes(ids) },
            note = if (others.isEmpty() && collabs.isEmpty()) {
                if (copy) tr("You have one wishlist. Make a new one to copy wishes to.") else tr("You have one wishlist. Make a new one to move wishes to.")
            } else null
        ) {
            if (others.isNotEmpty()) SheetGroup(others.map { d ->
                { SheetRow({ Icon(listKindIcon(false), null, tint = cs.onSurfaceVariant) }, Repo.mineName(d.id), { onPick(d.id) }, detail = wishesLabel(d.wishes.size)) }
            })
            if (collabs.isNotEmpty()) {
                PickHeading(tr("Co-op wishlists"))
                SheetGroup(collabs.map { l ->
                    { SheetRow({ Icon(listKindIcon(true), null, tint = cs.onSurfaceVariant) }, l.name, { onPick(MoveAcross.collabKey(l.code)) }) }
                })
            }
            // "+ New wishlist" last, in your colour (docs/STYLE.md "Picking from a list").
            SheetGroup(listOf { NewRow(tr("New wishlist")) { newList = true } })
        }
    }
    if (newList) NewListDialog(onCreated = { id -> newList = false; onPick(id) }, onClose = { newList = false })
}

/** "+ New …" as the last row of a picker: the + in its tile and the words in your colour. */
@Composable
fun NewRow(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val quiet = LocalLook.current.design == "quiet"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (quiet) 14.dp else 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like SheetRow: the icon in a tinted tile in Quiet, on its own in the other style.
        if (quiet) IconTile { Icon(AppIcons.Add, null, modifier = Modifier.size(20.dp)) } else Icon(AppIcons.Add, null, tint = cs.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Start), color = cs.primary, modifier = Modifier.padding(start = if (quiet) 14.dp else 16.dp).weight(1f))
    }
}

/**
 * What is being moved or copied, at the top of both steps (owner, 21 November 2026: "so at any time it's clear what
 * you're about to move or copy"): one wish as its photo, name and price; several as their photos and "3 wishes".
 */
@Composable
fun MovingWishes(ids: Set<Long>, from: List<Wish>? = null) {
    // [from]: the wishes themselves when they aren't on the open list (All your wishes).
    val ws = from ?: wishesOf(ids)
    if (ws.isEmpty()) return
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    @Composable
    fun photo(w: Wish, s: androidx.compose.ui.unit.Dp) = Box(
        Modifier.size(s).clip(RoundedCornerShape(10.dp)).background(look.photoSurface(w.id)), contentAlignment = Alignment.Center
    ) {
        if (w.image.isNotBlank()) WishImage(w.image, Modifier.size(s), ContentScale.Crop) else PlaceholderIcon("gift", s / 2)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ws.size == 1) {
            val w = ws.first()
            // Under the picker's question (owner, 22 November 2026): calmer than the heading above it.
            photo(w, 48.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(w.label, style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Start), color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                w.price?.let { Text(money(it, w.currency), style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start), color = cs.onSurfaceVariant) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { ws.take(4).forEach { photo(it, 40.dp) } }
            Text(
                tr("%d wishes", ws.size), style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Start), color = cs.onSurface,
                modifier = Modifier.padding(start = 14.dp).weight(1f)
            )
        }
    }
}
