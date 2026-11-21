package com.wishlist.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * The parts of an exchange's page: titles, notes, a person's row, the wrapped name, the rules dialog, picking a person and inviting friends. (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

@Composable
internal fun GxTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
internal fun GxNote(t: String, extra: (@Composable () -> Unit)? = null) {
    val look = LocalLook.current
    Column(Modifier.padding(16.dp).fillMaxWidth().lookCard(look, 7L, look.cardShape(7L, small = true)).padding(14.dp)) {
        Text(t, color = look.cardInk(7L))
        extra?.invoke()
    }
}

@Composable
internal fun GxPersonRow(m: GxMember, isMe: Boolean, organiser: Boolean, trailing: (@Composable () -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(if (isMe) Friends.myPhoto else Friends.photos[m.id], m.name, 36.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(if (isMe) tr("You") else m.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bits = listOfNotNull(if (organiser) tr("Organiser") else null, if (m.waiting) tr("Waiting for the draw") else null)
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** The wrapped gift: it wiggles until you tap it, then your name comes out with confetti. */
@Composable
internal fun GxWrapped(busy: Boolean, onOpen: () -> Unit) {
    val look = LocalLook.current
    val t = rememberInfiniteTransition(label = "wiggle")
    val r by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "wiggleAngle")
    Column(
        Modifier.padding(16.dp).fillMaxWidth().lookCard(look, 3L, look.cardShape(3L, small = false))
            .clickable(enabled = !busy, onClick = onOpen).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🎁", fontSize = 72.sp, modifier = Modifier.rotate(if (busy) 0f else r))
        Text(tr("Your name is ready"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = look.cardInk(3L))
        Text(tr("Tap the gift to open it. Make sure nobody is looking!"), textAlign = TextAlign.Center, color = look.cardMuted(3L))
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
}

/** The rules as lines: "Anna and Ben won’t draw each other" (both ways, once) or "Anna won’t draw Ben". */
internal fun gxRuleLines(x: Exchange): List<Pair<String, Triple<String, String, Boolean>>> {
    val out = ArrayList<Pair<String, Triple<String, String, Boolean>>>()
    val set = x.excl.toSet()
    for ((g, r) in x.excl) {
        val both = (r to g) in set
        if (both && g > r) continue
        out += (if (both) tr("%1\$s and %2\$s won’t draw each other", x.nameOf(g), x.nameOf(r))
        else tr("%1\$s won’t draw %2\$s", x.nameOf(g), x.nameOf(r))) to Triple(g, r, both)
    }
    return out
}

/** "Add a rule": who doesn't draw whom, and "Both ways". Refused (with who'd be stuck) when it makes a draw impossible. */
@Composable
internal fun GxRuleDialog(x: Exchange, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var giver by remember { mutableStateOf<GxMember?>(null) }
    var receiver by remember { mutableStateOf<GxMember?>(null) }
    var both by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val me = Exchanges.myId(ctx)
    fun label(m: GxMember?) = m?.let { if (it.id == me) tr("You") else it.name.ifBlank { tr("Someone") } } ?: tr("Choose")
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Add a rule")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GxPersonPick(tr("Who"), label(giver), x.members) { giver = it; if (receiver?.id == it.id) receiver = null; error = null }
                GxPersonPick(tr("won’t draw"), label(receiver), x.members.filter { it.id != giver?.id }) { receiver = it; error = null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Both ways"), style = MaterialTheme.typography.bodyLarge)
                        Text(tr("They won’t draw each other, like a couple."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(both, { both = it })
                }
                error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && giver != null && receiver != null, onClick = {
                val g = giver ?: return@TextButton
                val r = receiver ?: return@TextButton
                busy = true
                scope.launch {
                    error = try {
                        ex.addRule(x.id, g.id, r.id, both)?.let { tr("This rule would make the draw impossible.") + " " + gxStuckText(it) }
                    } catch (e: Exception) {
                        tr("Couldn’t do that. Check your connection.")
                    }
                    busy = false
                    if (error == null) onClose()
                }
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

@Composable
internal fun GxPersonPick(label: String, value: String, people: List<GxMember>, onPick: (GxMember) -> Unit) {
    val look = LocalLook.current
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 90.dp))
        Box {
            Text(
                value, style = MaterialTheme.typography.titleMedium, color = look.chipContent(false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.lookChip(look).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                people.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(if (m.id == Exchanges.myId(ctx)) tr("You") else m.name.ifBlank { tr("Someone") }) },
                        onClick = { open = false; onPick(m) }
                    )
                }
            }
        }
    }
}

/** Invite friends who aren't in the exchange (or invited) yet. */
@Composable
internal fun GxInviteDialog(x: Exchange, snack: SnackbarHostState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    Friends.load(ctx)
    LaunchedEffect(Unit) { try { Friends.refresh(ctx) } catch (e: Exception) {} }
    val taken = x.members.map { it.id }.toSet() + x.invited.map { it.first }
    val friends = (Friends.state?.friends ?: emptyList()).filter { it.id !in taken }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Invite friends")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (friends.isEmpty()) Text(tr("All your friends are in already. Share the link to invite others."))
                friends.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { picked = if (f.id in picked) picked - f.id else picked + f.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(Friends.photos[f.id], f.name, 36.dp)
                        Text(f.name.ifBlank { tr("Someone") }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Checkbox(checked = f.id in picked, onCheckedChange = { on -> picked = if (on) picked + f.id else picked - f.id })
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            if (friends.isEmpty()) TextButton(onClick = { onClose(); Exchanges.share(ctx, x) }) { Text(tr("Share link")) }
            else TextButton(enabled = !busy && picked.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val failed = ex.invite(x, friends.map { it.id }.filter { it in picked })
                    busy = false
                    onClose()
                    snack.toastSoon(if (failed == 0) tr("Invitations sent") else tr("Couldn’t do that. Check your connection."))
                }
            }) { Text(tr("Invite")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}
