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
import androidx.compose.material3.DropdownMenuItem
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
 * The two anonymous chats of a gift exchange: with your giftee and with your Secret Santa. (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

/**
 * One anonymous chat: [side] "giftee" (you and the person you drew; they don't see your name) or "santa" (your Secret
 * Santa and you; you don't see theirs). Full screen; new messages every few seconds while it is open.
 */
@Composable
fun GxChatScreen(x: Exchange, side: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val msgs = remember { mutableStateListOf<GxMsg>() }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    DisposableEffect(x.id, side) {
        GxChatNav.open = x.id + ":" + side
        onDispose { GxChatNav.open = null }
    }
    LaunchedEffect(x.id, side) {
        while (true) {
            try {
                val after = msgs.lastOrNull()?.id ?: 0L
                val got = Exchanges.chat(ctx, x.id, side, after)
                if (got.isNotEmpty()) {
                    msgs.addAll(got.filter { g -> msgs.none { it.id == g.id } })
                    listState.animateScrollToItem(maxOf(0, msgs.size - 1))
                }
                msgs.lastOrNull { !it.mine }?.let { ex.seen(x.id, side, it.id) }
                error = null
            } catch (e: Exception) {
                error = tr("Couldn’t reach the server. Check your connection.")
            }
            delay(4000)
        }
    }
    val title = if (side == "giftee") tr("You and %s", x.me.giftee?.name ?: tr("your giftee")) else tr("Your Secret Santa")
    val hint = if (side == "giftee") tr("They don’t see your name. Ask about sizes, colours, anything.")
    else tr("You don’t know who this is. They’re buying your gift.")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().lookPage(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(AppIcons.ArrowBack, tr("Back")) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(x.name, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (side == "giftee") "🎅" else "🤫", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(msgs, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                            Text(
                                m.body, color = if (m.mine) look.onAccent else cs.onSurface,
                                modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (m.mine) look.accent else cs.surfaceVariant).padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
                error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        text, { text = it.take(1000) }, placeholder = { Text(tr("Message")) }, maxLines = 4,
                        modifier = Modifier.weight(1f), keyboardOptions = Capitalised
                    )
                    TextButton(enabled = text.isNotBlank() && !sending, onClick = {
                        val body = text.trim()
                        sending = true
                        scope.launch {
                            try {
                                if (ex.send(x.id, side, body)) {
                                    text = ""
                                    val got = Exchanges.chat(ctx, x.id, side, msgs.lastOrNull()?.id ?: 0L)
                                    msgs.addAll(got.filter { g -> msgs.none { it.id == g.id } })
                                    listState.animateScrollToItem(maxOf(0, msgs.size - 1))
                                } else error = tr("This chat isn’t open any more.")
                            } catch (e: Exception) {
                                error = tr("Couldn’t send. Check your connection.")
                            }
                            sending = false
                        }
                    }) { Text(tr("Send")) }
                }
            }
        }
    }
}
