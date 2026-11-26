package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/* Gift list invitations (see GiftInvites.kt): Invite Friends, the invitation and its chat card. What they do is in
 * GiftInviteModel.kt. */

// ---------- the share screen's "Invite Friends" ----------

/** Pick Friends to invite to gift list [l]; those on it or invited already are shown as such. */
@Composable
fun GiftInviteFriendsDialog(l: SharedList, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    // The person a wishlist for someone else is for is never offered (owner, 17 November 2026: the surprise).
    val forWho = remember(l.code) { Shared.forName(ctx, l.code) }
    val all = Friends.state?.friends ?: emptyList()
    val friends = all.filterNot { Shared.isFor(forWho, it.name) }
    val hidden = all.filter { Shared.isFor(forWho, it.name) }
    // Who is on the list already, who you picked, sending (InviteFriendsState in GiftInviteModel.kt).
    val st = remember(l.code) { InviteFriendsState(ctx, l, scope) }
    LaunchedEffect(st.attempt) { st.load() }
    // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt): tick friends, then Invite.
    val t = st.taken
    // Only friends who aren't on it or invited yet (owner, 26 November 2026: the greyed-out list was a dead end);
    // who is on it shows on the share screen (InvitePeople.kt). "Add friends", the same words everywhere.
    val left = if (t == null) friends else friends.filter { it.id !in t }
    PickSheet(
        tr("Add friends"), onClose = onClose, busy = st.busy,
        note = tr("They get an invitation to join “%s”.", l.name.ifBlank { Repo.listName }),
        action = if (t != null && left.isNotEmpty() && st.err.isEmpty()) tr("Invite") else null,
        actionEnabled = st.picked.isNotEmpty(), onAction = { st.send(onClose) }
    ) {
        // The surprise (owner, 17 November 2026): the person it's for is left out, or named as a reminder.
        if (forWho.isNotBlank()) PickNote(
            if (hidden.isNotEmpty()) tr("%s isn’t shown: this wishlist is a surprise for them.", hidden.joinToString(", ") { it.name })
            else tr("Don’t invite %s: this wishlist is a surprise for them.", forWho)
        )
        if (st.err.isNotEmpty()) {
            PickNote(st.err, error = true)
            Box(Modifier.padding(horizontal = 16.dp)) { TextButton(onClick = { st.attempt++ }) { Text(tr("Try again")) } }
        } else if (t == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        } else if (friends.isEmpty()) {
            PickNote(tr("No friends yet. Add friends on the Friends tab, or share the invite link."))
        } else if (left.isEmpty()) {
            PickNote(tr("All your friends are on it or invited."))
        } else SheetGroup(left.map { f ->
            {
                SheetRow(
                    { Avatar(Friends.photos[f.id], f.name, 32.dp) }, f.name.ifBlank { tr("Someone") }, { st.toggle(f.id) },
                    chosen = f.id in st.picked, tile = false, enabled = !st.busy
                )
            }
        })
    }
}

// ---------- the invitation ----------

/**
 * The invitation to a gift list (owner: "well designed, not too flat, inviting and fun to join"): a colourful top with
 * the list's pictures fanned out (or a big gift), who invited you, the list's name and occasion, the givers, then a big
 * Join (confetti, then the list opens) and No thanks. [onJoined] gets the list's code.
 */
@Composable
fun GiftInviteDialog(code: String, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    // What the invitation says, Join and No thanks (GiftInviteState in GiftInviteModel.kt).
    val st = remember(code) { GiftInviteState(ctx, code, scope) }

    // Already on the list (joined from another invitation or the link): open it.
    LaunchedEffect(code) {
        if (Shared.lists.any { it.code == code }) { onJoined(code); return@LaunchedEffect }
    }
    LaunchedEffect(code, st.attempt) { st.load() }

    Dialog(onDismissRequest = { if (!st.busy) onClose() }, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(20.dp).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().lookSurface(look, MaterialTheme.shapes.extraLarge, cs.surface, depth = 10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // The colourful top: the accent fading into a warm pink, with confetti dots drawn here.
                Box(
                    Modifier.fillMaxWidth().height(190.dp)
                        .background(Brush.linearGradient(listOf(look.accent, Color(0xFFFF7AA2), Color(0xFFFFB86B)))),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val rnd = kotlin.random.Random(code.hashCode())
                        val dots = listOf(Color.White, Color(0xFFFFE082), Color(0xFFB3E5FC), Color(0xFFC8E6C9))
                        repeat(26) { i ->
                            val r = 3f + rnd.nextFloat() * 6f
                            drawCircle(dots[i % dots.size].copy(alpha = 0.55f + rnd.nextFloat() * 0.4f), r,
                                Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
                        }
                    }
                    val pics = st.info?.images.orEmpty()
                    if (pics.isNotEmpty()) {
                        val angles = when (pics.size) { 1 -> listOf(0f); 2 -> listOf(-7f, 7f); else -> listOf(-10f, 0f, 10f) }
                        val shifts = when (pics.size) { 1 -> listOf(0); 2 -> listOf(-34, 34); else -> listOf(-62, 0, 62) }
                        pics.take(3).forEachIndexed { i, p ->
                            Box(
                                Modifier.offset(x = shifts[i].dp, y = if (i == 1 && pics.size >= 3) (-6).dp else 4.dp).rotate(angles[i])
                                    .size(104.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(6.dp)
                            ) { WishImage(p, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), ContentScale.Crop) }
                        }
                    } else {
                        Box(
                            Modifier.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) { GiftIcon(look.textAccent, 60.dp) }
                    }
                }
                // The page's text colour (fixed in the rebuild, 19 November 2026: the headings were black on black in dark).
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides cs.onSurface) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val i = st.info
                    when {
                        st.state == "loading" -> CircularProgressIndicator(Modifier.padding(16.dp))
                        st.state == "gone" -> {
                            Text(tr("This invitation is no longer open."), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text(tr("Ask for a new invitation or the invite link."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            LinkButton(onClick = onClose) { Text(tr("Close")) }
                        }
                        st.state == "missing" -> {
                            Text(tr("Invitations need an update in Supabase first."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            LinkButton(onClick = onClose) { Text(tr("Close")) }
                        }
                        st.state == "offline" || i == null -> {
                            Text(tr("Couldn’t reach the server. Check your connection."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            Row {
                                LinkButton(onClick = onClose) { Text(tr("Close")) }
                                Button(onClick = { st.attempt++ }) { Text(tr("Try again")) }
                            }
                        }
                        else -> {
                            Text(
                                tr("%s invited you to plan gifts for", i.by.ifBlank { tr("Someone") }),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                            )
                            Text(
                                i.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Occasions.line(i.occ)?.let { o ->
                                Text(o, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = look.textAccent)
                            }
                            val bits = listOfNotNull(
                                if (i.count == 1) tr("1 gift idea") else tr("%d gift ideas", i.count),
                                when (i.givers.size) {
                                    0 -> null
                                    1 -> tr("%s is in", i.givers[0])
                                    2 -> tr("%1\$s and %2\$s are in", i.givers[0], i.givers[1])
                                    else -> tr("%1\$s and %2\$d others are in", i.givers[0], i.givers.size - 1)
                                }
                            )
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            Text(
                                tr("Add ideas, reserve what you’ll buy, and keep it a surprise."),
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                            )
                            if (st.err.isNotEmpty()) Text(st.err, color = cs.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Button(
                                enabled = !st.busy && st.party == 0,
                                onClick = { st.join(onJoined) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)
                            ) { Text(if (st.busy) tr("Joining…") else tr("Join") + " 🎉", style = MaterialTheme.typography.titleMedium) }
                            TextButton(enabled = !st.busy, onClick = { st.decline(onClose) }) { Text(tr("No thanks")) }
                        }
                    }
                }
                }
            }
            Confetti(st.party, Modifier.matchParentSize())
        }
    }
}

/** The chat card of a gift list invitation: a small colourful gift tile, the list's name and "Tap to join". */
@Composable
fun GiftListChatCard(title: String, mine: Boolean, ink: Color) {
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(look.accent, Color(0xFFFF7AA2), Color(0xFFFFB86B))))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { GiftIcon(Color.White, 30.dp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(tr("Plan gifts with me 🎁"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Text(title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (mine) tr("Invitation sent") else tr("Tap to join"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
