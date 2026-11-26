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
 * One gift exchange: its cover and facts, the people, the draw, the name you drew, the rules and the reveal. Its dialogs are in ExchangeDialogs.kt. (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(gid: String, snack: SnackbarHostState, onBack: () -> Unit, onOpenMine: (String) -> Unit) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val x = Exchanges.get(gid)
    // What is open on this screen (ExchangeModel.kt), so its dialogs can live in ExchangeDialogs.kt.
    val st = remember { ExchangeScreenState() }
    BackHandler { onBack() }

    LaunchedEffect(gid) {
        while (true) {
            try { Exchanges.refresh(ctx) } catch (e: Exception) {}
            delay(30000)
        }
    }
    LaunchedEffect(gid, places.inviteNow) {
        if (places.inviteNow == gid) { places.inviteNow = null; st.inviting = true }
    }

    val giftee = x?.me?.giftee
    if (st.showList && x != null && giftee != null) {
        BackHandler { st.showList = false }
        val code = giftee.list?.code
        if (code == null) {
            LaunchedEffect(Unit) { st.showList = false; snack.showToast(tr("%s has no wishlist right now.", giftee.name)) }
        } else {
            FriendScreen(
                Friend(code, giftee.list?.name.orEmpty()), snack, onRemoved = {},
                fetch = { Exchanges.gifteeList(ctx, gid) }, onBack = { st.showList = false }, ownerName = giftee.name,
                overBudget = { w -> Exchanges.overBudget(x, w) }, reactions = false
            )
            return
        }
    }

    fun act(block: suspend () -> String?) {
        st.busy = true
        scope.launch {
            val msg = try {
                block()
            } catch (e: Exception) {
                if (gxMissing(e)) tr("Gift exchange needs an update in Supabase first.") else tr("Couldn’t do that. Check your connection.")
            }
            st.busy = false
            msg?.let { snack.showToast(it) }
        }
    }

    fun draw(without: Boolean) = act {
        val r = ex.draw(gid, without)
        when {
            r.ok -> { st.confetti++; tr("Names are drawn! Everyone can open theirs now. 🎉") }
            r.few -> tr("You need at least 3 people to draw names.")
            r.pending.isNotEmpty() -> { st.pendingAsk = r.pending; null }
            r.stuck != null -> gxStuckText(r.stuck) + " " + tr("Change a rule first.")
            else -> tr("Couldn’t do that. Check your connection.")
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(x?.name ?: tr("Gift exchange")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    if (x != null) Box {
                        IconButton(onClick = { st.menu = true }) { Icon(AppIcons.MoreVert, tr("More")) }
                        DropdownMenu(expanded = st.menu, onDismissRequest = { st.menu = false }) {
                            val org = Exchanges.isOrganiser(ctx, x)
                            if (org) {
                                DropdownMenuItem(text = { Text(tr("Invite friends")) }, leadingIcon = { Icon(AppIcons.Friends, null) }, onClick = { st.menu = false; st.inviting = true })
                                DropdownMenuItem(text = { Text(tr("Share exchange link")) }, leadingIcon = { Icon(AppIcons.Share, null) }, onClick = { st.menu = false; Exchanges.share(ctx, x) })
                            }
                            DropdownMenuItem(text = { Text(tr("Change my wishlist")) }, leadingIcon = { Icon(AppIcons.Swap, null) }, onClick = { st.menu = false; st.changing = true })
                            if (org) DropdownMenuItem(text = { Text(tr("Exchange settings")) }, leadingIcon = { Icon(AppIcons.Settings, null) }, onClick = { st.menu = false; st.settings = true })
                            DropdownMenuItem(text = { Text(tr("Leave exchange")) }, leadingIcon = { Icon(AppIcons.Leave, null) }, danger = true, onClick = { st.menu = false; st.leaving = true })
                        }
                    }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (x == null) {
            // Cancelled, left or removed: back to the list of exchanges by itself (owner, 2.133: no dead end).
            LaunchedEffect(gid) { delay(1500); onBack() }
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                EmptyState(AppIcons.Exchange, tr("This gift exchange isn’t available any more."), tr("Back to gift exchanges"), onBack)
            }
            return@Scaffold
        }
        val me = Exchanges.myId(ctx)
        val org = Exchanges.isOrganiser(ctx, x)
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            GxCover(x.theme, Exchanges.photos[x.id], Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth().height(150.dp))
            Text(
                listOfNotNull(
                    tr("Organised by %s", if (x.organiser == me) tr("you") else x.organiserName.ifBlank { tr("someone") }),
                    if (x.members.size == 1) tr("1 person") else tr("%d people", x.members.size)
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
            )
            Exchanges.countdown(x)?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            listOfNotNull(Exchanges.budgetText(x), x.day?.let { gxDayText(it) }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (x.message.isNotBlank()) Text(
                "“" + x.message + "”", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp).fillMaxWidth().lookCard(look, 5L, look.cardShape(5L, small = true)).padding(14.dp),
                color = look.cardInk(5L)
            )
            if (x.me.list == null) GxNote(tr("You don’t join with a wishlist yet. Your Secret Santa needs one.")) {
                Button(onClick = { st.changing = true }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Choose a wishlist")) }
            }

            if (!x.drawn) {
                // ---- before the draw ----
                GxTitle(tr("People"))
                x.members.forEach { m ->
                    GxPersonRow(m, isMe = m.id == me, organiser = m.id == x.organiser,
                        trailing = if (org && m.id != me) ({ TextButton(onClick = { st.removing = m }) { Text(tr("Remove")) } }) else null)
                }
                if (x.invited.isNotEmpty()) {
                    Text(
                        tr("Invited: %s", x.invited.joinToString(", ") { it.second.ifBlank { tr("someone") } }),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (org) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { st.inviting = true }) { Text(tr("Invite friends")) }
                        OutlinedButton(onClick = { Exchanges.share(ctx, x) }) { Text(tr("Share link")) }
                    }
                    GxTitle(tr("Rules"))
                    Text(
                        tr("Only you see the rules. Nobody ever draws themselves."),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    gxRuleLines(x).forEach { (text, rule) ->
                        Row(Modifier.padding(start = 16.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(enabled = !st.busy, onClick = { act { ex.removeRule(gid, rule.first, rule.second, rule.third); null } }) {
                                Icon(AppIcons.Close, tr("Remove rule"), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    TextButton(enabled = x.members.size >= 2, onClick = { st.addingRule = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(AppIcons.Add, null)
                        Text(tr("Add a rule"), modifier = Modifier.padding(start = 6.dp))
                    }
                    x.blocked?.let { b ->
                        Text(
                            gxStuckText(b) + " " + tr("Change a rule first."), color = cs.error,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    Button(
                        enabled = !st.busy && x.members.size >= 3 && x.blocked == null, onClick = { st.drawAsk = true },
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    ) { Text(tr("Draw names") + " " + GxThemes.emoji(x.theme)) }
                    if (x.members.size < 3) Text(
                        tr("You need at least 3 people to draw names."), style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    GxNote(tr("Waiting for %s to draw names. You’ll get a notification.", x.organiserName.ifBlank { tr("the organiser") }))
                }
            } else {
                // ---- after the draw ----
                when {
                    x.me.waiting -> GxNote(tr("You joined after the draw. %s will add you soon.", x.organiserName.ifBlank { tr("The organiser") }))
                    !x.me.seen -> GxWrapped(st.busy) {
                        act {
                            ex.open(gid)
                            st.confetti++
                            null
                        }
                    }
                    giftee != null -> Column(
                        Modifier.padding(16.dp).fillMaxWidth().lookCard(look, 3L, look.cardShape(3L, small = false)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(tr("You’re buying for"), style = MaterialTheme.typography.titleSmall, color = look.cardMuted(3L))
                        Avatar(if (giftee.id == me) Friends.myPhoto else Friends.photos[giftee.id], giftee.name, 72.dp)
                        Text(giftee.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = look.cardInk(3L))
                        Exchanges.budgetText(x)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = look.cardInk(3L)) }
                        Button(onClick = { st.showList = true }) { Text(tr("See their wishlist")) }
                        OutlinedButton(onClick = { st.chatSide = "giftee" }) {
                            Text(tr("Ask %s anonymously", giftee.name.ifBlank { tr("them") }))
                            if (x.me.unreadGiftee > 0) RedDot(Modifier.padding(start = 6.dp))
                        }
                    }
                }
                if (x.me.hasSanta) Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { st.chatSide = "santa" }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🤫", fontSize = 26.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(tr("Your Secret Santa"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (x.me.unreadSanta > 0) tr("Wrote to you. Tap to read and answer.") else tr("Can ask you questions. You can write to them too."),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    if (x.me.unreadSanta > 0) RedDot()
                }
                if (org) {
                    // The draw check (owner): no names of pairs, so the organiser keeps their own surprise too.
                    GxTitle(tr("Draw check"))
                    val drawn = x.members.filter { !it.waiting }
                    Text(
                        "✓ " + tr("Everyone has someone") + "\n✓ " + tr("Nobody has themselves") + "\n✓ " + tr("All rules kept"),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    val opened = drawn.count { it.seen == true }
                    Text(
                        tr("%1\$d of %2\$d have opened their name", opened, drawn.size),
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                    )
                    drawn.forEach { m ->
                        GxPersonRow(m, isMe = m.id == me, organiser = m.id == x.organiser, trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (m.seen == true) "✓ " + tr("Opened") else tr("Not yet"), style = MaterialTheme.typography.bodySmall,
                                    color = if (m.seen == true) cs.primary else cs.onSurfaceVariant)
                                if (m.id != me) TextButton(onClick = { st.removing = m }) { Text(tr("Remove")) }
                            }
                        })
                    }
                    x.members.filter { it.waiting }.forEach { m ->
                        GxPersonRow(m, isMe = false, organiser = false, trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { st.adding = m }) { Text(tr("Add to the draw")) }
                                TextButton(onClick = { st.removing = m }) { Text(tr("Remove")) }
                            }
                        })
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { st.inviting = true }) { Text(tr("Invite friends")) }
                        OutlinedButton(onClick = { Exchanges.share(ctx, x) }) { Text(tr("Share link")) }
                    }
                    val canReveal = x.day == null || !x.day.isAfter(LocalDate.now())
                    if (!x.revealed && canReveal) Button(onClick = { st.revealAsk = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(tr("Reveal who had whom"))
                    }
                    if (!x.revealed && !canReveal) Text(
                        tr("After the exchange you can reveal who had whom."), style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    GxTitle(tr("People"))
                    x.members.forEach { m -> GxPersonRow(m, isMe = m.id == me, organiser = m.id == x.organiser, trailing = null) }
                }
                if (x.revealed) {
                    GxTitle(tr("Who had whom") + " " + GxThemes.emoji(x.theme))
                    x.members.filter { it.gaveTo != null }.forEach { m ->
                        Text(
                            m.name.ifBlank { tr("Someone") } + "  →  " + x.nameOf(m.gaveTo!!),
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
    Confetti(st.confetti)
    }

    if (x == null) return
    ExchangeScreenDialogs(x, gid, st, snack, ::act, ::draw, onBack)
}
