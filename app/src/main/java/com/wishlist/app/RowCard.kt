package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* A wish as a card (see WishCards.kt): Rows. */

/**
 * "Rows" (owner, 2.126; the default view for new installs): the picture on the left (a third of the width, square),
 * on the right the shop and category, the name (two lines at most), the price with the old price crossed out after a
 * change, and below them the buttons ([actions]: Share, the eye, Delete; on a gift list Share, Reserve, Delete). The
 * Top wish star, Reserved / Owned and the private eye sit on the picture, as on the other views.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowCard(
    w: Wish, badge: String? = null, dim: Boolean = false, onStar: (() -> Unit)? = null, onPrivate: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit
) {
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val muted = look.cardMuted(w.id)
    val ui = LocalUriHandler.current
    var viewing by remember { mutableStateOf(false) }
    if (viewing) ImageViewer(w) { viewing = false }
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    val label = listOf(host(w.url), w.category.takeIf { it.isNotBlank() && it != "All" })
        .filter { !it.isNullOrBlank() }.joinToString(" · ")
    val card = Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f).then(
        if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
        else Modifier
    ).lookCard(look, w.id, look.cardShape(w.id, small = true))
    // The picture, in [box]: the photo surface with the star, the status and the private eye.
    val picture: @Composable (Modifier) -> Unit = { box ->
            Box(
                box
                    .lookMat(look, look.pictureShape(w.id, small = true), look.photoSurface(w.id), over = true)
                    .combinedClickable(
                        enabled = w.image.isNotEmpty() || w.url.isNotBlank() || onLongPress != null,
                        onClickLabel = if (w.image.isNotEmpty()) tr("View picture") else null,
                        onClick = {
                            if (w.image.isNotEmpty()) viewing = true
                            else if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } }
                        },
                        onLongClick = onLongPress
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (w.image.isNotEmpty()) PhotoStage(w.image, w.label, small = true) else PlaceholderIcon("gift", 32.dp, look.photoInk)
                if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(6.dp), size = 12.dp, onClick = onPrivate)
                // The Love label on the picture (owner, 16 November 2026: not in the words); the reservation is under the
                // words now, above the buttons (owner, 20 November 2026: it didn't fit on the picture).
                PictureTopRow(6.dp, left = null, right = { compact -> StarCorner(w, onStar, small = true, buttonSize = 28.dp, compact = compact) }, modifier = Modifier.align(Alignment.TopStart))
            }
    }
    // The words and buttons next to the picture.
    val words: @Composable RowScope.(Modifier) -> Unit = { extra ->
            // Room under the buttons, so Reserve never touches the card's edge (owner, 7 November 2026).
            // 16 dp from the picture (owner, 7 November 2026: the words almost touched it).
            Column(Modifier.weight(1f).then(extra).padding(start = if (look.design == "quiet") 14.dp else 16.dp, top = if (look.design == "quiet") 12.dp else 10.dp, end = 8.dp, bottom = if (look.design == "quiet") 4.dp else 10.dp),
                // Quiet: the buttons at the bottom of the card, level with the bottom of the photo.
                verticalArrangement = if (look.design == "quiet") Arrangement.SpaceBetween else Arrangement.Top) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(
                        enabled = w.url.isNotBlank() || onLongPress != null,
                        onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                        onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                        onLongClick = onLongPress
                    )
                ) {
                    // The shop and category ("Love it" sits on the picture, owner 16 November 2026).
                    if (label.isNotEmpty()) Text(
                        label,
                        // Quiet: details at 13 (its smallest size for something you read, 2.207).
                        style = if (look.design == "quiet") MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                        color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(w.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink)
                        // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                        wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                            Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                        }
                    PriceLine(w, MaterialTheme.typography.titleMedium, Modifier.padding(top = 2.dp))
                }
                // Reactions (2.142): only on lists that show them (LocalWishDeco). Where you only see them (your own
                // list) they share the line with ⋮ and the bin, 2 at most then "+N" (owner, 7 November 2026); where you
                // react too, they keep their own line above Reserve.
                val deco = LocalWishDeco.current
                if (deco != null && !deco.canReact) {
                    if (badge != null) StatusLine(w, badge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ReactionBar(w, max = 2, compact = true) }
                        actions()
                    }
                } else {
                    ReactionBar(w)
                    // The reservation, above the buttons (owner, 20 November 2026).
                    if (badge != null) StatusLine(w, badge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        actions()
                    }
                }
            }
    }
    if (look.design == "quiet") {
        // Quiet (2.207): the picture fills the left of the card from top to bottom (a square at least), so a long name
        // never leaves a strip of empty card under the photo.
        androidx.compose.foundation.layout.BoxWithConstraints(card) {
            val pw = maxWidth * 0.34f
            Row(verticalAlignment = Alignment.Top) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(pw))
                    words(Modifier.heightIn(min = pw))
                }
            }
            Box(Modifier.matchParentSize()) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    picture(Modifier.width(pw).fillMaxHeight())
                }
            }
        }
    } else Row(card, verticalAlignment = Alignment.Top) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            picture(Modifier.padding(look.inset(small = true)).fillMaxWidth(0.36f).aspectRatio(1f))
            words(Modifier)
        }
    }
}
