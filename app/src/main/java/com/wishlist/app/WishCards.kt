package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A wish on a list: the three card styles (Rows, Two per row, Large), its price line and labels. Moved out of
// MainActivity.kt in 2.163.

private val Green = Color(0xFF0B7A3E)
/** A price that went up: the opposite of the green of a drop. */
private val Rise = Color(0xFFBA1A1A)

/** True when a price check found a higher price than before (old price lower than the price now). */
fun priceRose(w: Wish): Boolean {
    val p = w.price
    val o = w.oldPrice
    return p != null && o != null && p > o + 0.005
}

private fun host(url: String): String = try {
    java.net.URI(url).host.removePrefix("www.")
} catch (e: Exception) {
    url
}

/** Reserving a wish on a shared list. [state]: true = reserved on this phone, false = by someone else, null = still free. */
class Reserving(val state: Boolean?, val label: String? = null, val set: (Boolean) -> Unit)

/**
 * The Reserve button under a Two per row card (owner, 12 November 2026): "reserve" when it is free, "unreserve" when
 * you reserved it, null when it is owned, reserved by someone else, or not a list you reserve on.
 */
fun reserveButton(w: Wish, reserve: Reserving?): String? = when {
    reserve == null || w.got -> null
    reserve.state == null -> "reserve"
    reserve.state == true -> "unreserve"
    else -> null
}

/** "2×" and "Exactly this" / "Similar is fine" (owner, 2.128), as one short line, or "". */
fun wishTags(w: Wish): String = listOfNotNull(
    if (w.qty > 1) "${w.qty}×" else null,
    when (w.exact) { "exact" -> tr("Exactly this"); "similar" -> tr("Similar is fine"); else -> null }
).joinToString(" · ")

/** The label for a reservation on a shared list, or null when the wish is still free. */
fun reservedLabel(r: Boolean?): String? = when (r) {
    true -> tr("Reserved by you")
    false -> tr("Reserved")
    else -> null
}

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
        Modifier.fillMaxWidth().alpha(if (w.got || reserve?.state == false) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = true))
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
        Box(
            Modifier.padding(look.inset(small = true)).fillMaxWidth().aspectRatio(1f)
                .lookMat(look, look.pictureShape(w.id, small = true), look.pictureBg(w.id, w.image.isNotEmpty()), over = true)
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
            } else PlaceholderIcon("gift", 36.dp)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (Show["show_badge"] && (drop || rise) && newP != null && oldP != null) {
                    PriceBadge(
                        if (rise) "+${Math.round((newP / oldP - 1) * 100)}%" else "−${Math.round((1 - newP / oldP) * 100)}%",
                        rise, small = true
                    )
                }
            }
            // Bottom left: the eye of a private wish (the price change has the top left to itself).
            if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(8.dp), size = 14.dp, onClick = onPrivate)
            if (!w.got) {
                if (onStar != null) StarToggle(w.star, onStar, Modifier.align(Alignment.TopEnd).padding(6.dp))
                else if (w.star) MostWantedBadge(false, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            val status = if (w.got) tr("Owned") else reserve?.label ?: reservedLabel(reserve?.state)
            if (status != null) {
                TappableBadge(w, status, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
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
                Text(
                    w.label, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink
                )
                    // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                    wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                        Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                    }
                // The old price crossed out next to it after a change (owner, 2.126).
                PriceLine(w, MaterialTheme.typography.titleMedium, Modifier.padding(top = 2.dp, bottom = 6.dp))
                // No "Added on" here (owner, 2.100): too busy on a list; it stays in Price watch.
            }
            // The ⋮ lines up with the first line of the name, the bin under it (owner, 7 November 2026: Delete left the
            // menu). The 48 dp touch areas stay; only the icons move up next to the name.
            // It is moved up 6 dp and gives back the empty half of the bin's touch area below it, so the reactions
            // follow the price closely (owner, 7 November 2026: too much space between them).
            Column(
                Modifier.layout { m, c ->
                    val p = m.measure(c)
                    val up = 6.dp.roundToPx()
                    layout(p.width, (p.height - up - 12.dp.roundToPx()).coerceAtLeast(0)) { p.place(0, -up) }
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box {
                IconButton(onClick = { if (!inert) menu = true }) {
                    Icon(AppIcons.MoreVert, tr("More"), tint = muted)
                }
                // ⋮ opens the wish's sheet (WishSheet.kt, owner 12 November 2026): the same everywhere. Delete is the bin
                // under the ⋮ (owner, 7 November 2026); Top wish is the star on the picture.
                if (menu) WishSheet(
                    w, WishActions(
                        reserve = reserve, onEdit = if (readOnly) null else onEdit, onMove = onMove,
                        own = reserve == null && !readOnly, onHistory = onHistory,
                        shareAsIdea = readOnly || reserve != null
                    )
                ) { menu = false }
            }
            if (!readOnly) IconButton(onClick = { if (!inert) DeleteFlow.request(ctx, w) }) {
                Icon(AppIcons.Delete, tr("Delete"), tint = muted)
            }
            }
        }
        // Reserve as a button on the card, as in the other views (owner, 12 November 2026: it was only in the ⋮ here).
        // Reserved by someone else: nothing (the badge says so); reserved by you: Unreserve.
        if (!inert) when (reserveButton(w, reserve)) {
            "reserve" -> Button(onClick = { reserve?.set?.invoke(true) }, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                Text(tr("Reserve"), maxLines = 1)
            }
            "unreserve" -> OutlinedButton(onClick = { reserve?.set?.invoke(false) }, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                Text(tr("Unreserve"), maxLines = 1)
            }
        }
        // Reactions under the name, as wide as the card, sized for it (owner, 7 November 2026): 2 at most, then "+N",
        // and "+ React" on lists where you react.
        ReactionBar(w, Modifier.padding(start = 10.dp, end = 6.dp, bottom = 6.dp), max = 2, compact = true)
        }
    }
}

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
                .lookMat(look, look.pictureShape(w.id, small = false), look.pictureBg(w.id, w.image.isNotEmpty()), over = true)
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
            } else PlaceholderIcon("gift", 48.dp)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (Show["show_badge"] && (drop || rise) && newP != null && oldP != null) {
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
                if (badge != null) {
                    TappableBadge(w, badge, small = false)
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
                if (Show["show_desc"] && w.desc.isNotBlank()) {
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
    Row(
        Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = true)),
        verticalAlignment = Alignment.Top
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            Box(
                Modifier.padding(look.inset(small = true)).fillMaxWidth(0.36f).aspectRatio(1f)
                    .lookMat(look, look.pictureShape(w.id, small = true), look.pictureBg(w.id, w.image.isNotEmpty()), over = true)
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
                if (w.image.isNotEmpty()) PhotoStage(w.image, w.label, small = true) else PlaceholderIcon("gift", 32.dp)
                if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(6.dp), size = 12.dp, onClick = onPrivate)
                Column(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!w.got) {
                        if (onStar != null) StarToggle(w.star, onStar, size = 28.dp)
                        else if (w.star) MostWantedBadge(false)
                    }
                    if (badge != null) TappableBadge(w, badge, small = true)
                }
            }
            // Room under the buttons, so Reserve never touches the card's edge (owner, 7 November 2026).
            // 16 dp from the picture (owner, 7 November 2026: the words almost touched it).
            Column(Modifier.weight(1f).padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(
                        enabled = w.url.isNotBlank() || onLongPress != null,
                        onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                        onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                        onLongClick = onLongPress
                    )
                ) {
                    if (label.isNotEmpty()) Text(
                        label,
                        style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ReactionBar(w, max = 2, compact = true) }
                        actions()
                    }
                } else {
                    ReactionBar(w)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        actions()
                    }
                }
            }
        }
    }
}

/**
 * The price with, after a change, the old price crossed out next to it (owner, 2.126: also on Two per row and Rows,
 * like the large cards). Wraps to a second line when both don't fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceLine(w: Wish, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        Text(
            if (newP != null) money(newP, w.currency) else tr("No price"),
            style = style, fontWeight = look.priceWeight, fontFamily = look.display, maxLines = 1,
            color = if (drop) look.drop else if (rise) look.rise else ink,
            modifier = Modifier.alignByBaseline()
        )
        if ((drop || rise) && oldP != null) Text(
            money(oldP, w.currency), style = MaterialTheme.typography.bodySmall, maxLines = 1,
            textDecoration = TextDecoration.LineThrough, color = look.cardMuted(w.id),
            modifier = Modifier.alignByBaseline()
        )
    }
}
