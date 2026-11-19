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
 * Starting a gift exchange and joining one: their fields (name, budget, date, theme, message) and the wishlist you join with. (split out of ExchangeUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"). What changes an exchange goes through ExchangeModel.kt.
 */

/** The fields of a new exchange and of its settings: name, theme, budget, date and welcome message. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GxFields(
    name: String, onName: (String) -> Unit, missing: Boolean,
    theme: String, onTheme: (String) -> Unit,
    budget: String, onBudget: (String) -> Unit, currency: String,
    day: LocalDate?, onDay: (LocalDate?) -> Unit,
    message: String, onMessage: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            name, { onName(it.take(60)) }, label = { Text(tr("Name*")) }, placeholder = { Text(tr("Family Christmas")) },
            singleLine = true, isError = missing,
            supportingText = if (missing) ({ Text(tr("What is this gift exchange called?")) }) else null,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised
        )
        Text(tr("Theme"), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GxThemes.all.forEach { t ->
                FilterChip(selected = t == theme, onClick = { onTheme(t) }, label = { Text(GxThemes.emoji(t) + " " + GxThemes.label(t)) })
            }
        }
        OutlinedTextField(
            budget, { v -> onBudget(v.filter { it.isDigit() || it == '.' || it == ',' }.take(8)) },
            label = { Text(tr("Budget")) }, prefix = { Text(currencySymbol(currency) + " ") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
        )
        Text(tr("Exchange date"), style = MaterialTheme.typography.titleSmall)
        DayMonthPicker(day?.let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) } ?: "", removable = true) { mmdd ->
            onDay(if (mmdd.isEmpty()) null else try { LocalDate.parse(Occasions.ownDate(mmdd)) } catch (e: Exception) { null })
        }
        day?.let { Text(gxDayText(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        OutlinedTextField(
            message, { onMessage(it.take(300)) }, label = { Text(tr("Welcome message")) },
            placeholder = { Text(tr("Ho ho ho! Bring it wrapped.")) }, minLines = 2, maxLines = 4,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised
        )
    }
}

internal fun gxBudget(s: String): Double? = s.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 99999 }

internal fun gxBudgetText(b: Double?): String =
    b?.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, it) } ?: ""

/** Asks before a list that is "Only me" is shown to your Secret Santa (owner: short copy). */
class GxShowAsk {
    var pending by mutableStateOf<CompletableDeferred<Boolean>?>(null)

    suspend fun ok(c: Context, listId: String): Boolean {
        if (ListVisibility.of(c, listId) != Who.ME) return true
        val d = CompletableDeferred<Boolean>()
        pending = d
        return d.await()
    }

    @Composable
    fun Host() {
        pending?.let { d ->
            AlertDialog(
                onDismissRequest = { d.complete(false); pending = null },
                title = { Text(tr("Show to your Secret Santa?")) },
                text = { Text(tr("This list is set to Only me. Only your Secret Santa will see it.")) },
                confirmButton = { TextButton(onClick = { d.complete(true); pending = null }) { Text(tr("Show it")) } },
                dismissButton = { TextButton(onClick = { d.complete(false); pending = null }) { Text(tr("Back")) } }
            )
        }
    }
}

internal val gxListNote get() = tr("Only the person who draws you can open it. Private wishes are always hidden.")

/** "New gift exchange": the details and friends to invite, then the wishlist you join with. */
@Composable
fun NewExchangeDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("xmas") }
    var budget by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var message by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) }
    var friendIds by remember { mutableStateOf(listOf<String>()) }
    var pickingFriends by remember { mutableStateOf(false) }
    val currency = remember { CurrencyChoice.chosen.ifEmpty { countryCurrency() } }
    val ask = remember { GxShowAsk() }
    Friends.load(ctx)
    LaunchedEffect(Unit) { try { Friends.refresh(ctx) } catch (e: Exception) {} }
    val friends = Friends.state?.friends ?: emptyList()
    if (step == 1) {
        FormSheet(
            onDismissRequest = onClose,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(tr("New gift exchange")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GxFields(
                        name, { name = it; if (it.isNotBlank()) missing = false }, missing, theme, { theme = it },
                        budget, { budget = it }, currency, day, { day = it }, message, { message = it }
                    )
                    if (friends.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { pickingFriends = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Add, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(tr("Add friends"), style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                            val chosen = friends.filter { it.id in friendIds }.map { it.name.ifBlank { tr("Someone") } }
                            if (chosen.isNotEmpty()) Text(
                                chosen.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (name.isBlank()) missing = true else step = 2 }) { Text(tr("Next")) } },
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
    } else {
        ListPickDialog(
            title = name.trim(), confirm = tr("Create"), current = null, onClose = onClose,
            dismissLabel = tr("Back"), onDismissAction = { step = 1 }, note = gxListNote
        ) { id, _ ->
            if (!ask.ok(ctx, id)) return@ListPickDialog tr("Pick another wishlist, or make a new one.")
            val gx = ex.create(name, id, gxBudget(budget), currency, day, theme, message)
            if (friendIds.isNotEmpty()) {
                val x = Exchanges.get(gx)
                if (x != null) ex.invite(x, friendIds)
            }
            onCreated(gx)
            null
        }
    }
    ask.Host()
    if (pickingFriends) PickFriendsDialog(
        friends, friendIds, onDone = { friendIds = it; pickingFriends = false }, onClose = { pickingFriends = false },
        note = tr("They get an invitation when the exchange is made.")
    )
}

/** The join screen: from an exchange link ([link]) or an invite ([gx]). Already in it: it opens instead. */
@Composable
fun JoinExchangeDialog(link: String?, gx: String?, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = rememberExchange()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val invite = Exchanges.invites.firstOrNull { it.gx == gx }
    var preview by remember(link, gx) { mutableStateOf<GxPreview?>(null) }
    var state by remember(link, gx) { mutableStateOf("loading") }
    val ask = remember { GxShowAsk() }
    LaunchedEffect(link, gx) {
        try { Exchanges.refresh(ctx) } catch (e: Exception) {}
        val known = Exchanges.list.firstOrNull { it.id == gx || (link != null && it.link == link) }
        if (known != null) { onJoined(known.id); return@LaunchedEffect }
        state = try {
            if (link != null) {
                preview = Exchanges.preview(link)
                if (preview == null) "gone" else "ok"
            } else if (Exchanges.invites.any { it.gx == gx }) "ok" else "gone"
        } catch (e: Exception) {
            if (gxMissing(e)) "missing" else "offline"
        }
        preview?.let { p -> if (Exchanges.get(p.id) != null) onJoined(p.id) }
    }
    when (state) {
        "ok" -> {
            val name = preview?.name ?: Exchanges.invites.firstOrNull { it.gx == gx }?.name ?: ""
            val theme = preview?.theme ?: invite?.theme ?: "xmas"
            ListPickDialog(
                title = tr("Join “%s”", name) + " " + GxThemes.emoji(theme), confirm = tr("Join"), current = null, onClose = onClose,
                extra = {
                    val by = preview?.organiser ?: invite?.byName ?: ""
                    val count = preview?.count ?: invite?.count ?: 0
                    val budget = preview?.budget ?: invite?.budget
                    val cur = preview?.currency ?: invite?.currency ?: ""
                    val day = preview?.day ?: invite?.day
                    Text(
                        listOfNotNull(
                            by.takeIf { it.isNotBlank() }?.let { tr("Organised by %s", it) },
                            if (count == 1) tr("1 person") else tr("%d people", count),
                            budget?.let { tr("Budget: %s", money(it, cur.ifBlank { "EUR" })) },
                            day?.let { gxDayText(it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp)
                    )
                    if (preview?.drawn == true) Text(
                        tr("Names are drawn already. You’ll be added to the draw."),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
                    )
                },
                dismissLabel = if (invite != null) tr("No thanks") else null,
                onDismissAction = if (invite != null) ({
                    scope.launch { try { ex.decline(invite.gx) } catch (e: Exception) {} }
                    onClose()
                }) else null,
                note = gxListNote
            ) { id, fresh ->
                if (!ask.ok(ctx, id)) return@ListPickDialog tr("Pick another wishlist, or make a new one.")
                val joined = ex.join(link, if (link == null) gx else null, id)
                if (joined == null) tr("This invitation doesn’t work any more. Ask for a new one.")
                else {
                    if (fresh) try { ex.listToFriends(id) } catch (e: Exception) {}
                    onJoined(joined)
                    null
                }
            }
            ask.Host()
        }
        "loading" -> AlertDialog(
            onDismissRequest = onClose, title = { Text(tr("Gift exchange")) },
            text = { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
        else -> AlertDialog(
            onDismissRequest = onClose, title = { Text(tr("Gift exchange")) },
            text = {
                Text(
                    when (state) {
                        "gone" -> tr("This invitation doesn’t work any more. Ask for a new one.")
                        "missing" -> tr("Gift exchange needs an update in Supabase first.")
                        else -> tr("Couldn’t reach the server. Check your connection.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("OK")) } }
        )
    }
}

// ---------- an exchange's page ----------
