package com.wishlist.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/* Messages (see MessagesList.kt): a chat's bubbles, day chips and reactions. */

@Composable
private fun DayChip(label: String) {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = look.chipContent(false),
            modifier = Modifier.lookChip(look).padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/*
 * One chat style (owner, 2.101: the four layouts of 2.99 looked almost the same and were removed, to be fleshed out
 * later): a small tail on the corner, time and ticks inside the bubble, a date chip per day, in the theme's colours.
 * A group: messages from the same person within five minutes of each other.
 */
private fun sameGroup(a: ChatMessage?, b: ChatMessage?) =
    a != null && b != null && a.mine == b.mine && kotlin.math.abs(a.at - b.at) < 5 * 60_000L && dayLabel(a.at) == dayLabel(b.at)

/** One message, with the date chip above it when a new day starts. */
@Composable
internal fun ChatRow(
    m: ChatMessage, prev: ChatMessage?, peerName: String, onLongPress: (() -> Unit)?, onReply: (() -> Unit)?,
    onOpenPhoto: (File) -> Unit
) {
    val first = !sameGroup(prev, m)
    val newDay = prev == null || dayLabel(prev.at) != dayLabel(m.at)
    if (newDay) DayChip(dayLabel(m.at))
    // A small "tail": the corner nearest the sender is sharp on the first bubble of a group.
    // Quiet (2.207): rounder bubbles (20) with a softer tail corner (6); chaos as before.
    val quietChat = LocalLook.current.design == "quiet"
    val r = if (quietChat) 20.dp else 16.dp
    val tail = if (quietChat) 6.dp else 2.dp
    val shape = if (m.mine) RoundedCornerShape(r, if (first) tail else r, r, r) else RoundedCornerShape(if (first) tail else r, r, r, r)
    // Swipe a message to the right to reply to it (owner, 2.128); it springs back.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val limit = with(density) { 72.dp.toPx() }
    val drag = remember(m.id) { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().then(
            if (onReply != null) Modifier.pointerInput(m.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag.value > limit * 0.8f) onReply()
                        scope.launch { drag.animateTo(0f) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } }
                ) { change, d ->
                    change.consume()
                    scope.launch { drag.snapTo((drag.value + d).coerceIn(0f, limit)) }
                }
            } else Modifier
        ).offset { androidx.compose.ui.unit.IntOffset(drag.value.toInt(), 0) }
    ) {
        Bubble(m, shape, peerName, onLongPress = onLongPress, onOpenPhoto = onOpenPhoto, modifier = Modifier.padding(top = if (first && !newDay) 8.dp else 2.dp))
        // The reactions under the bubble, on its side.
        val emojis = listOf(m.reactTheirs, m.react).filter { it.isNotEmpty() }
        if (emojis.isNotEmpty()) Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
        ) {
            Text(
                emojis.joinToString(" "), fontSize = 16.sp,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * A message bubble: yours on the right in the accent colour, theirs on the left in the card colour, with the time and
 * ticks inside.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: ChatMessage, shape: androidx.compose.ui.graphics.Shape, peerName: String, onLongPress: (() -> Unit)?, onOpenPhoto: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val go = LocalGo.current
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    // A photo: this phone's copy, fetched and unlocked the first time (null while loading or when it is gone).
    var photo by remember(m.id) { mutableStateOf<File?>(null) }
    var photoState by remember(m.id) { mutableStateOf("loading") } // loading | ok | gone | offline
    var photoTry by remember(m.id) { mutableStateOf(0) }
    if (m.content.readable && m.content.kind == "photo") LaunchedEffect(m.id, photoTry) {
        photoState = "loading"
        photoState = try {
            ChatPhotos.load(ctx, m.content)?.let { photo = it; "ok" } ?: "gone"
        } catch (e: Exception) {
            "offline"
        }
    }
    val fill = if (m.mine) look.accent else cs.surfaceVariant
    val ink = if (m.mine) look.onAccent else cs.onSurface
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        if (m.mine) Spacer(Modifier.widthIn(min = 48.dp))
        Column(
            Modifier.widthIn(max = 300.dp).lookSurface(look, shape, fill, depth = 3.dp)
                .combinedClickable(
                    onClick = {
                        // A wish card opens the wish in its list (owner, 20 November 2026); an older card without its
                        // list still opens the shop (only an http(s) link: it comes from another phone).
                        if (m.content.kind == "wish") {
                            if (m.content.code.isNotEmpty() && m.content.wishId > 0L) go(Deep.WishIn(m.content.code, m.content.wishId))
                            else ChatWish.safeLink(m.content.url).takeIf { it.isNotEmpty() }?.let { u -> try { uri.openUri(u) } catch (e: Exception) {} }
                        }
                        // A group invitation (2.108): the join screen, or the group when you are in it already.
                        if (m.content.kind == "group") go(Deep.Group(m.content.text, Groups.parseLink(m.content.url)))
                        // A gift exchange invitation (2.129).
                        if (m.content.kind == "exchange") go(Deep.Exchange(m.content.text, Exchanges.parseLink(m.content.url)))
                        // A gift list invitation (2.142): the invitation (or the list when you are on it already).
                        if (m.content.kind == "giftlist" && !m.mine) go(Deep.GiftInvite(m.content.text))
                        if (m.content.kind == "photo") {
                            if (photoState == "ok") photo?.let(onOpenPhoto)
                            else if (photoState == "offline") photoTry++
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // The message this one answers: who wrote it and its first words, with a bar on the left.
            if (m.content.replyTo > 0L) Row(
                Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.12f)).height(IntrinsicSize.Min)
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(ink.copy(alpha = 0.6f)))
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        if (m.content.replyBy == Chat.me(ctx)) tr("You") else peerName,
                        color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                    )
                    Text(m.content.replyText, color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                !m.content.readable -> Text(
                    tr("This message can’t be read on this phone"), color = ink.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                m.content.kind == "wish" -> ChatWishRow(
                    m.content.title, m.content.price, m.content.cur, m.content.img, ink,
                    hint = if (m.content.code.isNotEmpty() && m.content.wishId > 0L) tr("See it on the list")
                    else if (m.content.url.isNotBlank()) tr("View in shop") else null
                )
                m.content.kind == "giftlist" -> GiftListChatCard(m.content.title, m.mine, ink)
                m.content.kind == "group" || m.content.kind == "exchange" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val gx = m.content.kind == "exchange"
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Icon(if (gx) AppIcons.Swap else AppIcons.Group, null, tint = Color(0xFF8A8A92), modifier = Modifier.size(28.dp)) }
                    Column(Modifier.padding(start = 10.dp).widthIn(max = 200.dp)) {
                        Text(if (gx) tr("Join my gift exchange") else tr("Join my wishlist group"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(m.content.title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (m.mine) tr("Invitation sent") else tr("Tap to join"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                m.content.kind == "photo" -> {
                    // The photo in its own shape (wide or tall), at most 240 dp wide.
                    val ratio = if (m.content.w > 0 && m.content.h > 0) (m.content.w.toFloat() / m.content.h).coerceIn(0.6f, 1.8f) else 1f
                    Box(
                        Modifier.width(240.dp).aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (photoState) {
                            "ok" -> AsyncImage(
                                model = photo, contentDescription = tr("Photo"), contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            "loading" -> CircularProgressIndicator(Modifier.size(24.dp), color = ink, strokeWidth = 2.dp)
                            else -> Text(
                                if (photoState == "gone") tr("This photo is no longer available") else tr("Couldn’t load the photo. Tap to try again."),
                                color = ink.copy(alpha = 0.8f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                else -> {
                    // "Chat about this wish" (owner, 20 November 2026): the wish above the text; a tap opens it in its list.
                    m.content.about?.let { a ->
                        Box(
                            Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.12f))
                                .clickable(onClickLabel = tr("See it on the list")) { go(Deep.WishIn(a.code, a.id)) }.padding(6.dp)
                        ) { ChatWishRow(a.title, a.price, a.cur, a.img, ink, hint = tr("See it on the list")) }
                    }
                    Text(m.content.text, color = ink, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("HH:mm", Locale.UK).format(Date(m.at)), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                // The two read ticks sit close together, overlapping a little (owner, 2.102).
                if (m.mine) Text(
                    if (m.read) "✓✓" else "✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    letterSpacing = (-4).sp, color = if (m.read) Color(0xFF7FE3FF) else ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, end = if (m.read) 4.dp else 0.dp)
                )
            }
        }
        if (!m.mine) Spacer(Modifier.widthIn(min = 48.dp))
    }
}

/** A wish in a chat: its photo on white, name, price, and a small line saying what a tap does. */
@Composable
internal fun ChatWishRow(title: String, price: Double?, cur: String, img: String, ink: Color, hint: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (img.isNotBlank()) WishImage(img, Modifier.fillMaxSize().padding(3.dp)) else GiftIcon(Color(0xFF8A8A92), 26.dp)
        }
        Column(Modifier.padding(start = 10.dp).widthIn(max = 200.dp)) {
            Text(title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            price?.let { Text(money(it, cur), color = ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
            if (hint != null) Text(hint, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
