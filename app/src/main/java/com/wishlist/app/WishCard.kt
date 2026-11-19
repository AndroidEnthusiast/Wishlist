package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* A wish as a card (see WishCards.kt): Large cards. */

/** One wish. [actions] are the buttons at the bottom right, which differ for the owner and for friends. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WishCard(
    w: Wish, badge: String? = null, dim: Boolean = false, onLongPress: (() -> Unit)? = null,
    onStar: (() -> Unit)? = null, onPrivate: (() -> Unit)? = null, emoji: String = "🎁",
    actions: @Composable RowScope.() -> Unit
) {
    val look = LocalLook.current
    // The text on the card: dark ink on the bright cards of neobrutalism Midnight, the theme's own text otherwise.
    val ink = look.cardInk(w.id)
    val muted = look.cardMuted(w.id)
    val ui = LocalUriHandler.current
    // Tapping the picture shows it big (ImageViewer); tapping the words opens the shop page.
    var viewing by remember { mutableStateOf(false) }
    if (viewing) ImageViewer(w) { viewing = false }
    val cs = MaterialTheme.colorScheme
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    val label = listOf(host(w.url), w.category.takeIf { it.isNotBlank() && it != "All" })
        .filter { !it.isNullOrBlank() }.joinToString(" · ")

    // The frame comes from the design (Design.kt): shape, colour, lines and shadow.
    Column(
        Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = false))
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
        Box(
            Modifier.padding(look.inset(small = false)).fillMaxWidth().aspectRatio(4f / 3f)
                .lookMat(look, look.pictureShape(w.id, small = false), look.photoSurface(w.id), over = true)
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
            if (w.image.isNotEmpty()) {
                PhotoStage(w.image, w.label, small = false)
            } else PlaceholderIcon("gift", 48.dp, look.photoInk)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if ((drop || rise) && newP != null && oldP != null) {
                    PriceBadge(
                        if (rise) "+${Math.round((newP / oldP - 1) * 100)}%" else "−${Math.round((1 - newP / oldP) * 100)}%",
                        rise, small = false
                    )
                }
            }
            // Bottom left: the eye of a private wish (the price change has the top left to itself).
            if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(12.dp), onClick = onPrivate)
            // Top right: the star (the owner can tap it), and below it the status such as "Got it".
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StarCorner(w, onStar)
                val mine = rememberAppearPulse(reservedByMe(badge))
                if (badge != null) {
                    TappableBadge(w, badge, small = false, modifier = Modifier.starPulse(mine, TransformOrigin(1f, 0.5f)))
                }
            }
        }
        if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
        Column(Modifier.padding(16.dp)) {
            // The name and details lead to the shop page.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                )
            ) {
                if (label.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
                }
                Text(
                    w.label, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink
                )
                    // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                    wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                        Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                    }
                if (w.desc.isNotBlank()) {
                    Text(
                        w.desc, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = muted
                    )
                }
                if (w.note.isNotBlank()) {
                    Text(
                        tr("Note: %s", w.note), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, color = ink
                    )
                }
                if (w.by.isNotBlank()) {
                    Text(tr("Added by %s", w.by), style = MaterialTheme.typography.labelMedium, color = muted)
                }
                // No "Added on" here (owner, 2.100): too busy on a list; it stays in Price watch.
            }
            if (w.size.isNotBlank()) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { SizeTag(w.size) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (newP != null) money(newP, w.currency) else tr("No price"),
                        // The style's display font, as on Rows and Two per row (owner, 7 November 2026).
                        style = MaterialTheme.typography.titleLarge, fontWeight = look.priceWeight, fontFamily = look.display,
                        color = if (drop) look.drop else if (rise) look.rise else ink
                    )
                    if ((drop || rise) && oldP != null) {
                        Text(
                            money(oldP, w.currency), style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.LineThrough, color = muted,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                actions()
            }
            // The reactions on their own line under the price (owner, 7 November 2026): 3 at most, then "+N".
            ReactionBar(w, max = 3)
        }
        }
    }
}
