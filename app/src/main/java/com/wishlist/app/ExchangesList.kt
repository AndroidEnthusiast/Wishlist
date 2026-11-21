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
 * Your gift exchanges: the list with invitations (Join, No thanks) and "New gift exchange". (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangesScreen(snack: SnackbarHostState, onOpenMine: (String) -> Unit, onNew: () -> Unit) {
    val places = LocalPlaces.current
    // One exchange slides in over the list and back (Motion.kt).
    Moving(places.exchange, depth = { if (it != null) 1 else 0 }) { open ->
        if (open != null) key(open) { ExchangeScreen(open, snack, onBack = { places.exchange = null }, onOpenMine = onOpenMine) }
        else ExchangesList(snack, onOpenMine, onNew)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExchangesList(snack: SnackbarHostState, onOpenMine: (String) -> Unit, onNew: () -> Unit) {
    val places = LocalPlaces.current
    val go = LocalGo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var state by remember { mutableStateOf(if (Exchanges.list.isEmpty() && Exchanges.invites.isEmpty()) "loading" else "ok") }
    LaunchedEffect(Unit) {
        Exchanges.load(ctx)
        while (true) {
            state = try {
                Exchanges.refresh(ctx); "ok"
            } catch (e: Exception) {
                if (gxMissing(e)) "missing" else if (Exchanges.list.isEmpty() && Exchanges.invites.isEmpty()) "offline" else "ok"
            }
            delay(30000)
        }
    }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Gift exchange")) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            when (state) {
                "loading" -> Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
                "missing" -> Text(
                    tr("Gift exchange needs an update in Supabase first."), color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp)
                )
                "offline" -> EmptyState(AppIcons.Refresh, tr("Couldn’t reach the server. Check your connection."), tr("Try again"), {
                    state = "loading"
                    scope.launch { state = try { Exchanges.refresh(ctx); "ok" } catch (e: Exception) { if (gxMissing(e)) "missing" else "offline" } }
                })
                else -> {
                    Exchanges.invites.forEach { v -> GxInviteCard(v, snack) }
                    if (Exchanges.list.isEmpty() && Exchanges.invites.isEmpty()) {
                        GxCover("xmas", null, Modifier.padding(16.dp).fillMaxWidth().height(150.dp))
                        Text(
                            tr("Draw names with friends: everyone secretly buys a gift for one person."),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Text(
                            tr("Nobody ever draws themselves, and the person you draw sees only your wishlist, never who drew them."),
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    Exchanges.list.forEach { x ->
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                                .lookCard(look, x.id.hashCode().toLong(), look.cardShape(x.id.hashCode().toLong(), small = true))
                                .clickable { go(Deep.ExchangePage(x.id)) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GxCover(x.theme, Exchanges.photos[x.id], Modifier.size(64.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(x.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        !x.drawn -> tr("Names not drawn yet")
                                        x.me.waiting -> tr("Waiting to be added to the draw")
                                        !x.me.seen -> tr("Your name is ready to open 🎁")
                                        else -> tr("You’re buying for %s", x.me.giftee?.name ?: tr("someone"))
                                    },
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                listOfNotNull(Exchanges.countdown(x), Exchanges.budgetText(x)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (x.me.unreadGiftee + x.me.unreadSanta > 0) RedDot()
                        }
                    }
                    Button(onClick = onNew, modifier = Modifier.padding(16.dp)) {
                        Icon(AppIcons.Add, null)
                        Text(tr("New gift exchange"), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun GxInviteCard(v: GxInvite, snack: SnackbarHostState) {
    val go = LocalGo.current
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .lookCard(look, 11L, look.cardShape(11L, small = true)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            tr("%1\$s invited you to “%2\$s”", v.byName.ifBlank { tr("Someone") }, v.name) + " " + GxThemes.emoji(v.theme),
            style = MaterialTheme.typography.titleSmall, color = look.cardInk(11L)
        )
        val bits = listOfNotNull(
            v.budget?.let { tr("Budget: %s", money(it, v.currency.ifBlank { "EUR" })) },
            v.day?.let { gxDayText(it) },
            if (v.count == 1) tr("1 person") else tr("%d people", v.count)
        )
        Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(11L))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { go(Deep.Exchange(v.gx)) }) { Text(tr("Join")) }
            OutlinedButton(onClick = {
                scope.launch {
                    try { ex.decline(v.gx) } catch (e: Exception) { snack.showToast(tr("Couldn’t do that. Check your connection.")) }
                }
            }) { Text(tr("No thanks")) }
        }
    }
}

// ---------- making and settings ----------
