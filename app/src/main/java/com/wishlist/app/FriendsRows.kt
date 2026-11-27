package com.wishlist.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The rows and cards of the Friends tab (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): a notice, your
 * friend code (and its Copy / Make a new code dialog), a request, a friend with their menu.
 */

@Composable
internal fun Notice(text: String, button: String, onClick: () -> Unit) {
    val look = LocalLook.current
    val id = 7L
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).lookCard(look, id, look.cardShape(id, small = true)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text, color = look.cardInk(id), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(button) }
    }
}

/** Your friend code, with Share and Add a friend. */
@Composable
internal fun CodeCard(code: String, offline: Boolean, onAdd: () -> Unit, onRetry: () -> Unit, small: Boolean = false) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val id = 3L
    // Tapping the code: Copy or Make a new code (owner, 18 November 2026: the code lives only here, not on Profile).
    var codeMenu by remember { mutableStateOf(false) }
    if (codeMenu) CodeDialog(code) { codeMenu = false }
    // Once you have friends: one slim row (owner, 2.139).
    if (small && !offline && code.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).lookCard(look, id, look.cardShape(id, small = true)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = tr("Copy or make a new code")) { codeMenu = true }) {
                Text(tr("Your code"), style = MaterialTheme.typography.labelSmall, color = look.cardMuted(id))
                Text(Friends.pretty(code), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = look.cardInk(id), letterSpacing = 1.sp)
            }
            // 8 dp between the two buttons (owner, 26 November 2026: they touched).
            TextButton(onClick = { Friends.shareCode(ctx, code) }) { Text(tr("Share")) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAdd) { Text(tr("Add a friend")) }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).lookCard(look, id, look.cardShape(id, small = false)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quiet (2.207): the label is the heading of the card, the message under it reads as text.
        if (look.design == "quiet") Text(tr("Your friend code"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = look.cardInk(id))
        else Text(tr("Your friend code"), style = MaterialTheme.typography.labelLarge, color = look.cardMuted(id))
        when {
            code.isNotEmpty() -> Text(
                Friends.pretty(code), style = MaterialTheme.typography.headlineMedium.copy(shadow = null),
                fontWeight = FontWeight.Bold, color = look.cardInk(id), letterSpacing = 2.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = tr("Copy or make a new code")) { codeMenu = true }
            )
            offline -> Text(
                tr("Couldn’t reach the server. Check your connection."), color = look.cardMuted(id),
                style = if (look.design == "quiet") MaterialTheme.typography.bodyMedium else LocalTextStyle.current
            )
            else -> CircularProgressIndicator(Modifier.size(28.dp))
        }
        Text(
            tr("Share it with friends. You approve them first."),
            style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (offline) Button(onClick = onRetry) { Text(tr("Try again")) }
            else Button(onClick = { Friends.shareCode(ctx, code) }, enabled = code.isNotEmpty()) { Text(tr("Share code")) }
            OutlinedButton(onClick = onAdd) { Text(tr("Add a friend")) }
        }
    }
}

/** Your friend code: Copy, or Make a new code (the old one stops working; friends stay). */
@Composable
internal fun CodeDialog(code: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val people = rememberPeople()
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var sure by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (sure) tr("Make a new code?") else Friends.pretty(code)) },
        text = {
            Text(
                msg ?: if (sure) tr("Your old code stops working. Friends you already have stay.") else tr("Your friend code."),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            if (sure) TextButton(onClick = {
                scope.launch {
                    msg = try { people.newCode(); null } catch (e: Exception) { tr("Couldn’t reach the server. Check your connection.") }
                    if (msg == null) onClose()
                }
            }) { Text(tr("Make a new code")) }
            else TextButton(onClick = { clip.setText(AnnotatedString(Friends.pretty(code))); onClose() }) { Text(tr("Copy")) }
        },
        // The dialog rule (docs/STYLE.md, owner 21 November 2026): a second choice is the outlined button, the way out
        // plain words at the bottom.
        secondButton = if (sure) null else ({ TextButton(onClick = { sure = true }) { Text(tr("Make a new code")) } }),
        dismissButton = { TextButton(onClick = onClose) { Text(if (sure) tr("Cancel") else tr("Close")) } }
    )
}

/** Someone in a request: picture, name, a line, and the buttons. */
@Composable
internal fun PersonRow(p: Person, line: String, meeting: (() -> Unit)? = null, buttons: @Composable () -> Unit) {
    val look = LocalLook.current
    val id = p.id.hashCode().toLong()
    Column(Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Approve (owner, 25 November 2026): your picture slides in until it overlaps theirs, with a small bump,
            // then [meeting] goes on (the request becomes a friend).
            if (meeting != null) MeetingPictures(p, meeting) else Avatar(Friends.photos[p.id], p.name, 48.dp, ring = Iconic.ringFor(p.id))
            // The name steps aside while the pictures meet, so yours never slides over it.
            val words by androidx.compose.animation.core.animateFloatAsState(if (meeting != null) 0f else 1f, androidx.compose.animation.core.tween(120), label = "words")
            Column(Modifier.weight(1f).padding(start = 12.dp).graphicsLayer { alpha = words }) {
                Text(p.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { buttons() }
    }
}

/** A friend: tap opens their wishlists; long press or ⋮ opens a small menu (Unfriend; "Hide my lists" went in 2.108). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FriendRow(f: Person, onOpen: () -> Unit, onUnfriend: () -> Unit, onBlock: () -> Unit, onReport: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val id = f.id.hashCode().toLong()
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(Friends.photos[f.id], f.name, 52.dp, ring = Iconic.ringFor(f.id))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(f.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleMedium, color = look.cardInk(id), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Their birthday when it's within 60 days, and wishes they added since you last looked (owner, 2.139).
                val days = Friends.daysUntil(f.birthday)
                val fresh = FriendNews.counts[f.id] ?: 0
                if (days != null && days <= 60) Text(
                    "🎂 " + when (days) { 0 -> tr("Birthday today!"); 1 -> tr("Birthday tomorrow"); else -> tr("Birthday in %d days", days) },
                    style = MaterialTheme.typography.bodySmall, color = look.cardInk(id)
                )
                // No red dot here (owner, 23 November 2026): the red badges are on the lists that have the new wishes.
                if (fresh > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (fresh == 1) tr("1 new wish") else tr("%d new wishes", fresh),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = look.cardInk(id)
                    )
                }
                if ((days == null || days > 60) && fresh == 0) Text(
                    when (f.lists.size) {
                        0 -> tr("No wishlists to see yet")
                        1 -> tr("1 wishlist")
                        else -> tr("%d wishlists", f.lists.size)
                    },
                    style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
                )
            }
            IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More"), tint = look.cardMuted(id)) }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Unfriend")) }, leadingIcon = { Icon(AppIcons.Close, null) }, danger = true, onClick = { menu = false; onUnfriend() })
                DropdownMenuItem(text = { Text(tr("Block")) }, leadingIcon = { Icon(AppIcons.Block, null) }, danger = true, onClick = { menu = false; onBlock() })
                DropdownMenuItem(text = { Text(tr("Report")) }, leadingIcon = { Icon(AppIcons.Flag, null) }, onClick = { menu = false; onReport() })
            }
        }
    }
}

/** Their picture, and yours sliding in from the right until the two overlap; then [done]. */
@Composable
private fun MeetingPictures(p: Person, done: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val x = remember { androidx.compose.animation.core.Animatable(1f) }
    val bump = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!animationsOff(ctx)) {
            kotlinx.coroutines.delay(100)
            x.animateTo(0f, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            bump.snapTo(1.12f)
            bump.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
            kotlinx.coroutines.delay(250)
        }
        done()
    }
    Box(Modifier.width(48.dp + 30.dp)) {
        Box(Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }) { Avatar(Friends.photos[p.id], p.name, 48.dp, ring = Iconic.ringFor(p.id)) }
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(((30.dp + 90.dp * x.value).toPx()).toInt(), 0) }
                .graphicsLayer { alpha = ((1f - x.value) * 1.6f).coerceIn(0f, 1f); scaleX = bump.value; scaleY = bump.value }
                // A ring in the card's colour sets your picture apart where the two overlap.
                .border(3.dp, LocalLook.current.cardFill(p.id.hashCode().toLong()), androidx.compose.foundation.shape.CircleShape)
        ) { Avatar(Friends.myPhoto, Shared.lastName, 48.dp, ring = Iconic.myRing) }
    }
}
