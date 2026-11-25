package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* A wish as a card (see WishCards.kt): Two per row's compact card. */

/**
 * Two-per-row card: picture, title and price, with edit / got it / remove in a small menu.
 * On a shared list ([reserve] set) the menu has Reserve instead of most wanted and got it.
 * The owner gets a round star on the picture to tap (no text, there is no room); friends only see it when it is set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactCard(
    w: Wish, onLongPress: (() -> Unit)? = null, onStar: (() -> Unit)? = null, reserve: Reserving? = null,
    onPrivate: (() -> Unit)? = null, emoji: String = "🎁", inert: Boolean = false,
    // Someone else's list (a Friend's, 7 November 2026): the menu only has Share and Reserve.
    readOnly: Boolean = false,
    // "Move to…" in the ⋮ menu (your own lists, owner 7 November 2026).
    onMove: (() -> Unit)? = null,
    // "Price history" in the ⋮ sheet (your own lists).
    onHistory: (() -> Unit)? = null,
    // "Chat about this wish" (a friend's list, owner 20 November 2026; Two per row was missing it in 2.266).
    chatAbout: (() -> Unit)? = null,
    onEdit: () -> Unit
) {
    val ctx = LocalContext.current
    // inert: a sample card (the Themer's preview) that does nothing when tapped.
    val look = LocalLook.current
    // The text on the card: dark ink on the bright cards of neobrutalism Midnight, the theme's own text otherwise.
    val ink = look.cardInk(w.id)
    val muted = look.cardMuted(w.id)
    val ui = LocalUriHandler.current
    // Tapping the picture shows it big (ImageViewer); tapping the words opens the shop page.
    var viewing by remember { mutableStateOf(false) }
    if (viewing) ImageViewer(w) { viewing = false }
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)

    // The frame comes from the design (Design.kt): shape, colour, lines and shadow.
    Column(
        Modifier.fillMaxWidth().alpha(if (w.got || reserveButton(w, reserve) == "taken") 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = true))
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
        Box(
            Modifier.padding(look.inset(small = true)).fillMaxWidth().aspectRatio(1f)
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
            if (w.image.isNotEmpty()) {
                PhotoStage(w.image, w.label, small = true)
            } else PlaceholderIcon("gift", 36.dp, look.photoInk)
            // One row on the photo (CardLabels.kt, owner 20 November 2026): the price change at the left, Love at the
            // right, never overlapping; the reservation is under the photo.
            PictureTopRow(
                8.dp,
                left = if ((drop || rise) && newP != null && oldP != null) ({
                    PriceBadge(
                        if (rise) "+${Math.round((newP / oldP - 1) * 100)}%" else "−${Math.round((1 - newP / oldP) * 100)}%",
                        rise, small = true
                    )
                }) else null,
                right = { compact -> StarCorner(w, onStar, small = true, compact = compact) },
                modifier = Modifier.align(Alignment.TopStart)
            )
            // Bottom left: the eye of a private wish (the price change has the top left to itself).
            if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(8.dp), size = 14.dp, onClick = onPrivate)
        }
        if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
        Row(Modifier.padding(start = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            // The name and price lead to the shop page.
            Column(
                Modifier.weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                )
            ) {
                // Always two lines tall, so the prices and buttons of two cards side by side line up (owner, 20 November 2026).
                Text(
                    w.label, style = MaterialTheme.typography.titleSmall, minLines = 2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink
                )
                    // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                    wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                        Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                    }
                // The old price crossed out next to it after a change (owner, 2.126).
                PriceLine(w, MaterialTheme.typography.titleMedium, Modifier.padding(top = 2.dp, bottom = 6.dp), stacked = true)
                // No "Added on" here (owner, 2.100): too busy on a list; it stays in Price watch.
            }
            // The ⋮ lines up with the first line of the name, the bin under it (owner, 7 November 2026: Delete left the
            // menu). The 48 dp touch areas stay; only the icons move up next to the name.
            // It is moved up 6 dp and gives back the empty half of the bin's touch area below it, so the reactions
            // follow the price closely (owner, 7 November 2026: too much space between them).
            Column(
                Modifier.hiddenWhileSelecting().layout { m, c ->
                    val p = m.measure(c)
                    val up = 6.dp.roundToPx()
                    layout(p.width, (p.height - up - 12.dp.roundToPx()).coerceAtLeast(0)) { p.place(0, -up) }
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box {
                IconButton(onClick = { if (!inert) menu = true }) {
                    Icon(AppIcons.MoreVert, tr("More"), tint = muted, modifier = Modifier.size(if (look.design == "quiet") 20.dp else 24.dp))
                }
                // ⋮ opens the wish's sheet (WishSheet.kt, owner 12 November 2026): the same everywhere. Delete is the bin
                // under the ⋮ (owner, 7 November 2026); Top wish is the star on the picture.
                if (menu) WishSheet(
                    w, WishActions(
                        reserve = reserve, onEdit = if (readOnly) null else onEdit, onMove = onMove,
                        own = reserve == null && !readOnly, onHistory = onHistory,
                        shareAsIdea = readOnly || reserve != null, chatAbout = chatAbout
                    )
                ) { menu = false }
            }
            if (!readOnly) IconButton(onClick = { if (!inert) DeleteFlow.request(ctx, w) }) {
                Icon(AppIcons.CardDelete, tr("Delete"), tint = muted, modifier = Modifier.size(if (look.design == "quiet") 20.dp else 24.dp))
            }
            }
        }
        // Reserve as a button on the card, as in the other views (owner, 12 November 2026: it was only in the ⋮ here).
        // A compact pill (owner, 18 November 2026). One button for every state (owner, 23 November 2026): Reserve,
        // "Reserved by you" with a lock, or a greyed "Reserved" with a lock; no line above it about the reservation.
        // The line under the photo keeps "Owned" and "1 of 3 reserved" (Reserving.label).
        val status = if (w.got) tr("Owned") else reserve?.label
        if (status != null) StatusLine(w, status, Modifier.padding(start = 12.dp, end = 10.dp))
        val rs = reserveButton(w, reserve)
        if (!inert && rs != null && reserve != null) CompositionLocalProvider(LocalCardButton provides true) {
            val onBadge = LocalWishDeco.current?.onBadge
            ReserveButton(
                w, rs, reserveButtonText(w, reserve.counts, rs), reserve.set, onTaken = onBadge?.let { b -> { b(w) } },
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 4.dp)
            )
        }
        // Reactions under the name, as wide as the card, sized for it (owner, 7 November 2026): 2 at most, then "+N",
        // and "+ React" on lists where you react.
        ReactionBar(w, Modifier.padding(start = 10.dp, end = 6.dp, bottom = 6.dp), max = 2, compact = true)
        }
    }
}
