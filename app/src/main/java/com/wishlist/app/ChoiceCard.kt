package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/*
 * The "choose some" card of the New … forms (owner, 23 November 2026: "never plain + lines anywhere in the app"; the
 * same kind of element looks the same everywhere). One frame, [ChoiceCard]: an icon tile, a title, how many are chosen,
 * a chevron, and the chosen ones in a sideways row, each with a ✕ to take it out. Used by "Add wishes from other
 * wishlists" ([CopyWishesCard]) and "Add friends" ([FriendsCard]) in New wishlist (Co-op), New group, New gift exchange.
 */

/** One chosen thing in a [ChoiceCard] row: [label] says what the ✕ takes out; [picture] draws a 44 dp tile. */
internal class Chosen(val label: String, val onRemove: () -> Unit, val picture: @Composable BoxScope.() -> Unit)

@Composable
internal fun ChoiceCard(icon: ImageVector, title: String, summary: String, chosen: List<Chosen>, onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val quiet = look.design == "quiet"
    val fill = if (quiet) look.groupFill() else androidx.compose.ui.graphics.lerp(sheetColor(look, cs.surface), cs.onSurface, 0.08f)
    val shape = if (quiet) Squircle(18.dp) else RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(fill)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(if (quiet) Squircle(10.dp) else RoundedCornerShape(10.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        // The chosen ones, each with a ✕ to take it out again (owner, 21 November 2026). All of them, in one row
        // that scrolls sideways, so the card never grows taller.
        if (chosen.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chosen.forEach { c ->
                // The ✕ sits inside the 52 dp tap area (it was clipped at the top in 2.273's first pictures).
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = tr("Take out %s", c.label)) { c.onRemove() },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Box(Modifier.padding(bottom = 1.dp).size(44.dp), contentAlignment = Alignment.Center, content = c.picture)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).size(18.dp).clip(CircleShape).background(cs.inverseSurface),
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Close, null, tint = cs.inverseOnSurface, modifier = Modifier.size(12.dp)) }
                }
            }
        }
    }
}

/** "Add wishes from other wishlists" in the New wishlist form (owner, 21 November 2026: not a loose "+ Add" line). */
@Composable
internal fun CopyWishesCard(picked: List<Wish>, onRemove: (Wish) -> Unit = {}, onClick: () -> Unit) {
    val look = LocalLook.current
    ChoiceCard(
        AppIcons.Gift, tr("Add wishes from other wishlists"),
        when (picked.size) { 0 -> tr("None chosen yet"); 1 -> tr("1 wish chosen"); else -> tr("%d wishes chosen", picked.size) },
        picked.map { w ->
            Chosen(w.label, { onRemove(w) }) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(look.photoSurface(w.id)), contentAlignment = Alignment.Center) {
                    if (w.image.isNotBlank()) WishImage(w.image, Modifier.size(44.dp), ContentScale.Crop)
                    else PlaceholderIcon("gift", 22.dp)
                }
            }
        },
        onClick
    )
}

/**
 * "Add friends" in New wishlist (Co-op), New group and New gift exchange (owner, 23 November 2026): the same card as
 * "Add wishes from other wishlists", with the chosen friends' photos. Shown without Friends too: tapping it then says
 * how to invite people afterwards ([PickFriendsDialog]).
 */
@Composable
internal fun FriendsCard(friends: List<Person>, picked: List<String>, onRemove: (String) -> Unit, onClick: () -> Unit) {
    val chosen = friends.filter { it.id in picked }
    ChoiceCard(
        AppIcons.Friends, tr("Add friends"),
        when (chosen.size) { 0 -> tr("None chosen yet"); 1 -> tr("1 friend chosen"); else -> tr("%d friends chosen", chosen.size) },
        chosen.map { f -> Chosen(f.name.ifBlank { tr("Someone") }, { onRemove(f.id) }) { Avatar(Friends.photos[f.id], f.name, 44.dp, ring = Iconic.ringFor(f.id)) } },
        onClick
    )
}
