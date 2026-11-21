package com.wishlist.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/*
 * The gift exchange cards on Home (split out of Hub.kt in the rebuild, owner 18 November 2026): "Secret Santa this
 * year?" in its season, and your gift exchanges as blocks.
 */

/**
 * "Secret Santa this year?" on Home from 1 November to 20 December (owner, 7 November 2026): Gift exchange is only in
 * the menu otherwise. Gone for the year after ✕, and while you are in an exchange or have an invitation.
 */
@Composable
internal fun SeasonCard(onStart: () -> Unit) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    val today = remember { java.time.LocalDate.now() }
    val inSeason = today.monthValue == 11 || (today.monthValue == 12 && today.dayOfMonth <= 20)
    if (!inSeason || !Config.enabled) return
    val prefs = remember { ctx.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE) }
    val key = "gx_card_" + today.year
    var hidden by remember { mutableStateOf(prefs.getBoolean(key, false)) }
    if (hidden || Exchanges.list.isNotEmpty() || Exchanges.invites.isNotEmpty()) return
    val hide = { hidden = true; prefs.edit().putBoolean(key, true).apply() }
    if (look.design == "chaos") { ChaosSeasonCard(onStart, hide); return }
    // Secret Santa colours (owner, 7 November 2026: "now it's just a grey block"): a deep red with light snow dots,
    // white words and a white pill to start. A drawn illustration, so its colours are fixed in every theme.
    val red = Color(0xFFB3261E)
    val deep = Color(0xFF6E1414)
    // Quiet (2.207, owner: festive, "but make it fit the style"): one solid festive red, a drawn gift instead of the
    // emoji, no snow, the title in Fraunces. Chaos has its own card since 2.209 (ChaosSeasonCard).
    val quiet = look.design == "quiet"
    val solid = if (look.dark) Color(0xFF8E1418) else Color(0xFFA4161A)
    val shape = if (quiet) Squircle(20.dp) else RoundedCornerShape(24.dp)
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth().clip(shape)
            .background(if (quiet) androidx.compose.ui.graphics.SolidColor(solid) else androidx.compose.ui.graphics.Brush.linearGradient(listOf(red, deep)))
            .drawBehind {
                if (quiet) return@drawBehind
                // Snow: fixed dots, nothing moves.
                val dots = listOf(0.08f to 0.18f, 0.22f to 0.72f, 0.35f to 0.12f, 0.5f to 0.85f, 0.62f to 0.3f,
                    0.74f to 0.68f, 0.86f to 0.15f, 0.93f to 0.55f, 0.15f to 0.45f, 0.44f to 0.5f)
                dots.forEachIndexed { k, (x, y) ->
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = (2 + k % 3).dp.toPx(), center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height))
                }
            }
            .clickable(onClickLabel = tr("Start a gift exchange"), onClick = onStart)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (quiet) Icon(AppIcons.Gift, null, tint = Color.White, modifier = Modifier.size(40.dp))
            else Text("🎅", fontSize = 40.sp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    tr("Secret Santa this year?"),
                    style = if (quiet) MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp) else MaterialTheme.typography.titleMedium,
                    fontWeight = if (quiet) null else FontWeight.Bold, color = Color.White
                )
                Text(
                    tr("Draw names with family or friends."), style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .clickable(onClick = onStart).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (if (quiet) "" else "🎁 ") + tr("Start a gift exchange"), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = if (quiet) solid else red
                    )
                }
            }
        }
        IconButton(
            onClick = hide,
            modifier = Modifier.align(Alignment.TopEnd)
        ) { Icon(AppIcons.Close, tr("Hide until next year"), tint = Color.White) }
    }
}

/**
 * The Secret Santa card in Gen Z chaos (2.209, owner 15 November 2026: the old card "doesn't fit the theme"): chaos's
 * own parts on a festive red: the thick ink line and hard shadow of every chaos card, a slight tilt, the heading in
 * Bungee, a drawn gift on a tilted yellow sticker (no 🎅 emoji) and a white button with the same line and shadow.
 */
@Composable
private fun ChaosSeasonCard(onStart: () -> Unit, onHide: () -> Unit) {
    val look = LocalLook.current
    val red = Color(0xFFC62828)
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth().rotate(-1f)
            .lookSurface(look, shape, red).clickable(onClickLabel = tr("Start a gift exchange"), onClick = onStart)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 18.dp, bottom = 18.dp, end = 44.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 2.dp).size(56.dp).rotate(-8f)
                    .lookSurface(look, RoundedCornerShape(14.dp), Color(0xFFFFE14D), depth = 3.dp),
                contentAlignment = Alignment.Center
            ) { Icon(AppIcons.Gift, null, tint = Color(0xFF0D0D0D), modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(tr("Secret Santa this year?"), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    tr("Draw names with family or friends."), style = MaterialTheme.typography.bodyMedium,
                    color = Color.White, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                Box(
                    Modifier.heightIn(min = 48.dp).lookSurface(look, RoundedCornerShape(24.dp), Color.White, depth = 3.dp)
                        .clickable(onClick = onStart).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("Start a gift exchange"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = red)
                }
            }
        }
        IconButton(onClick = onHide, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(AppIcons.Close, tr("Hide until next year"), tint = Color.White)
        }
    }
}

/** "Gift exchanges ›" on Home: your exchanges (tap: open it) and "New gift exchange"; the › opens the exchanges page. */
@Composable
internal fun ExchangesSection(onNew: () -> Unit, withNew: Boolean = true) {
    val go = LocalGo.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val list = Exchanges.list
    Spacer(Modifier.height(8.dp))
    HubTitle(tr("Gift exchanges"), onMore = { go(Deep.ExchangePage(null)) })
    val w = ((LocalConfiguration.current.screenWidthDp - 16) / 2.3f).dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list.size, key = { list[it].id }) { i -> ExchangeTile(list[i], Modifier.width(w)) }
        if (withNew) item(key = "new") { NewBlock(tr("New gift exchange"), onNew, Modifier.width(w)) }
    }
}

/** One gift exchange as a block: its cover, name and countdown (Home's row and the Exchanges tab). */
@Composable
internal fun ExchangeTile(x: Exchange, modifier: Modifier) {
    val go = LocalGo.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable { go(Deep.ExchangePage(x.id)) }) {
        Box {
            GxCover(x.theme, Exchanges.photos[x.id], Modifier.fillMaxWidth().aspectRatio(1f).clip(look.cardShape(x.id.hashCode().toLong(), small = true)))
            if (x.me.unreadGiftee + x.me.unreadSanta > 0) RedDot(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Text(
            x.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            Exchanges.countdown(x) ?: if (!x.drawn) tr("Names not drawn yet") else tr("Names drawn"),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/** Home's Exchanges tab (owner, 21 November 2026): your gift exchanges, two per row like the other tabs. */
@Composable
internal fun ExchangeGrid() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Exchanges.list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { x -> ExchangeTile(x, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
