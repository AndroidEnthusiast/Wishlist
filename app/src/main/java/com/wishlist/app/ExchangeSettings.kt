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
 * The organiser's settings (name, budget, date, theme, cover, hand over, cancel), picking a person, and leaving an exchange. (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

/** The organiser's settings: the details, the cover photo, hand over, and cancel the exchange. */
@Composable
fun ExchangeSettingsDialog(x: Exchange, snack: SnackbarHostState, onCancelled: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(x.name) }
    var missing by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(x.theme) }
    var budget by remember { mutableStateOf(gxBudgetText(x.budget)) }
    var day by remember { mutableStateOf(x.day) }
    var message by remember { mutableStateOf(x.message) }
    var busy by remember { mutableStateOf(false) }
    var handing by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    val currency = x.currency.ifBlank { CurrencyChoice.chosen.ifEmpty { countryCurrency() } }
    // The photo goes through the same editor as a wishlist's cover (owner, 2.130): crop, move, zoom.
    var pending by remember { mutableStateOf<String?>(null) }
    val fa = coverAspect()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }
    fun photo(done: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            val msg = try { block(); done } catch (e: Exception) { tr("Couldn’t do that. Check your connection.") }
            busy = false
            snack.toastSoon(msg)
        }
    }
    // While the photo editor is open, these settings step aside (what you typed is kept).
    if (pending == null) AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(tr("Exchange settings")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GxFields(
                    name, { name = it; if (it.isNotBlank()) missing = false }, missing, theme, { theme = it },
                    budget, { budget = it }, currency, day, { day = it }, message, { message = it }
                )
                Text(tr("Cover"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                GxCover(theme, Exchanges.photos[x.id], Modifier.padding(vertical = 6.dp).fillMaxWidth().height(110.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (Exchanges.photos[x.id] == null) tr("Choose a photo") else tr("New photo"))
                    }
                    if (Exchanges.photos[x.id] != null) TextButton(enabled = !busy, onClick = {
                        photo(tr("Back to the theme’s cover")) { ex.setPhoto(x.id, null, HeroEdit(), fa) }
                    }) { Text(tr("Use the theme")) }
                }
                if (x.members.any { it.id != Exchanges.myId(ctx) && !it.web }) TextButton(enabled = !busy, onClick = { handing = true }) { Text(tr("Hand over to someone else")) }
                TextButton(danger = true, enabled = !busy, onClick = { cancelling = true }) { Text(tr("Cancel the exchange")) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (name.isBlank()) { missing = true; return@TextButton }
                busy = true
                scope.launch {
                    try {
                        ex.saveSettings(x.id, name, gxBudget(budget), currency, day, theme, message)
                        onClose()
                    } catch (e: Exception) {
                        snack.toastSoon(tr("Couldn’t save. Check your connection."))
                    }
                    busy = false
                }
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(),
            onSave = { chosen, e ->
                pending = null
                photo(tr("Cover photo saved")) {
                    ex.setPhoto(x.id, chosen, e, fa)
                    ImageStore.delete(ctx, chosen)
                    if (chosen != img) ImageStore.delete(ctx, img)
                }
            },
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
    if (handing) GxPickPersonDialog(
        x, title = tr("Hand over to…"), text = tr("They can draw names, set rules and invite people. You stay in the exchange."),
        confirm = tr("Hand over"), onClose = { handing = false }
    ) { id ->
        ex.handover(x.id, id)
        handing = false
        onClose()
        snack.toastSoon(tr("%s organises it now", x.nameOf(id)))
    }
    if (cancelling) AlertDialog(
        onDismissRequest = { cancelling = false },
        title = { Text(tr("Cancel “%s” for everyone?", x.name)) },
        text = { Text(tr("Everyone is told. Nobody’s wishlist is deleted.")) },
        confirmButton = {
            TextButton(danger = true, onClick = {
                cancelling = false
                scope.launch {
                    try {
                        ex.cancel(x.id)
                        onCancelled()
                        snack.toastSoon(tr("“%s” was cancelled", x.name))
                    } catch (e: Exception) {
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Cancel it")) }
        },
        dismissButton = { TextButton(onClick = { cancelling = false }) { Text(tr("Keep it")) } }
    )
}

/** Pick another member (hand over, or the next organiser when you leave). */
@Composable
internal fun GxPickPersonDialog(x: Exchange, title: String, text: String, confirm: String, onClose: () -> Unit, onPick: suspend (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val me = Exchanges.myId(ctx)
    // Only people with the app can organise: drawing names and rules are in the app.
    val others = x.members.filter { it.id != me && !it.web }
    var chosen by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (x.members.any { it.id != me && it.web }) Text(
                    tr("People without the app can’t organise."), style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
                others.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { chosen = m.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosen == m.id, onClick = { chosen = m.id })
                        Text(m.name.ifBlank { tr("Someone") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                error?.let { Text(it, color = cs.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && chosen != null, onClick = {
                val id = chosen ?: return@TextButton
                busy = true
                scope.launch {
                    try { onPick(id) } catch (e: Exception) { error = tr("Couldn’t do that. Check your connection.") }
                    busy = false
                }
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Leave: the organiser names the next organiser first; alone, they cancel instead. */
@Composable
internal fun GxLeaveDialog(x: Exchange, snack: SnackbarHostState, onLeft: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val org = Exchanges.isOrganiser(ctx, x)
    if (org && x.members.any { it.id != Exchanges.myId(ctx) && !it.web }) {
        GxPickPersonDialog(
            x, title = tr("Leave “%s”?", x.name), text = tr("Pick who organises it after you."), confirm = tr("Leave"), onClose = onClose
        ) { id ->
            ex.leave(x.id, id)
            onLeft()
            snack.toastSoon(tr("You left “%s”", x.name))
        }
        return
    }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Leave “%s”?", x.name)) },
        text = {
            Text(
                if (org && x.members.size > 1) tr("Nobody else here has the app, so nobody can organise it. Cancel the exchange instead?")
                else if (org) tr("You’re the only one in it. Cancel the exchange instead?")
                else if (x.drawn) tr("Whoever drew you gets someone else. Your wishlist stays yours.")
                else tr("Your wishlist stays yours.")
            )
        },
        confirmButton = {
            TextButton(danger = true, enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (org) ex.cancel(x.id) else ex.leave(x.id, null)
                        onLeft()
                        snack.toastSoon(tr("You left “%s”", x.name))
                    } catch (e: Exception) {
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                    busy = false
                }
            }) { Text(if (org) tr("Cancel it") else tr("Leave")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Stay")) } }
    )
}

// ---------- the anonymous chat ----------
