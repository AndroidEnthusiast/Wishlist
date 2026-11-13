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
import androidx.compose.material3.IconButton
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
 * The screens of Gift exchange (2.129, see Exchange.kt): the list of your exchanges (side menu), a new exchange, the
 * join screen, an exchange's page (before the draw: people, rules and Draw; after it: your name with the reveal moment,
 * the anonymous chats, the organiser's draw check and Reveal), its settings, and the anonymous chat.
 */

/** Gift exchange needs supabase-exchange.sql: an error that says the server doesn't know its functions yet. */
fun gxMissing(e: Exception): Boolean = e.message.orEmpty().let { it.contains("404") || it.contains("PGRST202") }

/** "Anna would have nobody left to draw." / "Anna, Ben and Cas would only have 2 people left to draw between them." */
fun gxStuckText(s: GxStuck): String {
    val names = s.names.ifEmpty { listOf(tr("Someone")) }
    if (names.size == 1) return tr("%s would have nobody left to draw.", names[0])
    val joined = names.dropLast(1).joinToString(", ") + " " + tr("and") + " " + names.last()
    return if (s.left == 1) tr("%s would only have 1 person left to draw between them.", joined)
    else tr("%1\$s would only have %2\$d people left to draw between them.", joined, s.left)
}

private fun gxDayText(d: LocalDate): String =
    d.dayOfMonth.toString() + " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK) + " " + d.year

// ---------- the drawn cover and the confetti ----------

private fun gxColours(theme: String): List<Color> = when (theme) {
    "sint" -> listOf(Color(0xFFB71C1C), Color(0xFFE0A100))
    "bday" -> listOf(Color(0xFFE91E63), Color(0xFF7E57C2))
    "office" -> listOf(Color(0xFF1565C0), Color(0xFF00897B))
    "plain" -> listOf(Color(0xFF455A64), Color(0xFF7E57C2))
    else -> listOf(Color(0xFF1B5E20), Color(0xFFC62828))
}

/** The exchange's cover: the organiser's photo, or the theme drawn here (colours and a few of its emoji). */
@Composable
fun GxCover(theme: String, photo: String?, modifier: Modifier, title: String? = null) {
    Box(modifier.clip(RoundedCornerShape(24.dp))) {
        if (!photo.isNullOrEmpty()) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(gxColours(theme))))
            val e = GxThemes.emoji(theme)
            // A few of the theme's emoji, always in the same places, slightly turned.
            listOf(Triple(0.08f, 0.15f, -12f), Triple(0.78f, 0.1f, 14f), Triple(0.62f, 0.62f, -8f), Triple(0.2f, 0.66f, 10f), Triple(0.9f, 0.7f, -16f))
                .forEach { (fx, fy, r) ->
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        Text(
                            e, fontSize = 26.sp, modifier = Modifier.padding(start = maxWidth * fx, top = maxHeight * fy).rotate(r)
                        )
                    }
                }
        }
        if (title != null) Text(
            title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))).padding(16.dp)
        )
    }
}

/** Confetti falling over the screen for about two seconds each time [run] goes up. */
@Composable
fun Confetti(run: Int, modifier: Modifier = Modifier) {
    if (run == 0) return
    val p = remember(run) { Animatable(0f) }
    LaunchedEffect(run) { p.animateTo(1f, tween(2400, easing = LinearEasing)) }
    if (p.value >= 1f) return
    val colours = listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))
    Canvas(modifier.fillMaxSize()) {
        val rnd = kotlin.random.Random(run)
        repeat(90) { i ->
            val x = rnd.nextFloat() * size.width
            val speed = 0.7f + rnd.nextFloat() * 0.8f
            val start = -rnd.nextFloat() * size.height * 0.4f
            val y = start + p.value * speed * size.height * 1.4f
            val sway = kotlin.math.sin((p.value * 6f + i) * 1.3f) * 18f
            rotate(p.value * 540f * speed + i * 23f, Offset(x + sway, y)) {
                drawRect(colours[i % colours.size], Offset(x + sway - 6f, y - 9f), Size(12f, 18f), alpha = 1f - p.value * 0.5f)
            }
        }
    }
}

// ---------- the list of exchanges (side menu) ----------

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
private fun ExchangesList(snack: SnackbarHostState, onOpenMine: (String) -> Unit, onNew: () -> Unit) {
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
                title = { Text(tr("Gift exchange"), fontWeight = look.titleWeight) },
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
                "offline" -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Couldn’t reach the server. Check your connection."), color = cs.onSurfaceVariant)
                    Button(onClick = {
                        state = "loading"
                        scope.launch { state = try { Exchanges.refresh(ctx); "ok" } catch (e: Exception) { if (gxMissing(e)) "missing" else "offline" } }
                    }) { Text(tr("Try again")) }
                }
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
private fun GxInviteCard(v: GxInvite, snack: SnackbarHostState) {
    val go = LocalGo.current
    val ctx = LocalContext.current
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
                    try { Exchanges.decline(ctx, v.gx) } catch (e: Exception) { snack.showToast(tr("Couldn’t do that. Check your connection.")) }
                }
            }) { Text(tr("No thanks")) }
        }
    }
}

// ---------- making and settings ----------

/** The fields of a new exchange and of its settings: name, theme, budget, date and welcome message. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GxFields(
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

private fun gxBudget(s: String): Double? = s.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 99999 }

private fun gxBudgetText(b: Double?): String =
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

private val gxListNote get() = tr("Only the person who draws you can open it. Private wishes are always hidden.")

/** "New gift exchange": the details and friends to invite, then the wishlist you join with. */
@Composable
fun NewExchangeDialog(onCreated: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
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
        AlertDialog(
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
            val gx = Exchanges.create(ctx, name, id, gxBudget(budget), currency, day, theme, message)
            if (friendIds.isNotEmpty()) {
                val x = Exchanges.get(gx)
                if (x != null) Exchanges.invite(ctx, x, friendIds)
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
                    scope.launch { try { Exchanges.decline(ctx, invite.gx) } catch (e: Exception) {} }
                    onClose()
                }) else null,
                note = gxListNote
            ) { id, fresh ->
                if (!ask.ok(ctx, id)) return@ListPickDialog tr("Pick another wishlist, or make a new one.")
                val joined = Exchanges.join(ctx, link, if (link == null) gx else null, id)
                if (joined == null) tr("This invitation doesn’t work any more. Ask for a new one.")
                else {
                    if (fresh) try { ListVisibility.set(ctx, id, Who.FRIENDS) } catch (e: Exception) {}
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(gid: String, snack: SnackbarHostState, onBack: () -> Unit, onOpenMine: (String) -> Unit) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val x = Exchanges.get(gid)
    var menu by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<GxMember?>(null) }
    var adding by remember { mutableStateOf<GxMember?>(null) }
    var addingRule by remember { mutableStateOf(false) }
    var drawAsk by remember { mutableStateOf(false) }
    var pendingAsk by remember { mutableStateOf<List<String>?>(null) }
    var revealAsk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(0) }
    var showList by remember { mutableStateOf(false) }
    var chatSide by remember { mutableStateOf<String?>(null) }
    BackHandler { onBack() }

    LaunchedEffect(gid) {
        while (true) {
            try { Exchanges.refresh(ctx) } catch (e: Exception) {}
            delay(30000)
        }
    }
    LaunchedEffect(gid, places.inviteNow) {
        if (places.inviteNow == gid) { places.inviteNow = null; inviting = true }
    }

    val giftee = x?.me?.giftee
    if (showList && x != null && giftee != null) {
        BackHandler { showList = false }
        val code = giftee.list?.code
        if (code == null) {
            LaunchedEffect(Unit) { showList = false; snack.showToast(tr("%s has no wishlist right now.", giftee.name)) }
        } else {
            FriendScreen(
                Friend(code, giftee.list?.name.orEmpty()), snack, onRemoved = {},
                fetch = { Exchanges.gifteeList(ctx, gid) }, onBack = { showList = false }, ownerName = giftee.name,
                overBudget = { w -> Exchanges.overBudget(x, w) }, reactions = false
            )
            return
        }
    }

    fun act(block: suspend () -> String?) {
        busy = true
        scope.launch {
            val msg = try {
                block()
            } catch (e: Exception) {
                if (gxMissing(e)) tr("Gift exchange needs an update in Supabase first.") else tr("Couldn’t do that. Check your connection.")
            }
            busy = false
            msg?.let { snack.showToast(it) }
        }
    }

    fun draw(without: Boolean) = act {
        val r = Exchanges.draw(ctx, gid, without)
        when {
            r.ok -> { confetti++; tr("Names are drawn! Everyone can open theirs now. 🎉") }
            r.few -> tr("You need at least 3 people to draw names.")
            r.pending.isNotEmpty() -> { pendingAsk = r.pending; null }
            r.stuck != null -> gxStuckText(r.stuck) + " " + tr("Change a rule first.")
            else -> tr("Couldn’t do that. Check your connection.")
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(x?.name ?: tr("Gift exchange"), fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    if (x != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val org = Exchanges.isOrganiser(ctx, x)
                            if (org) {
                                DropdownMenuItem(text = { Text(tr("Invite friends")) }, onClick = { menu = false; inviting = true })
                                DropdownMenuItem(text = { Text(tr("Share exchange link")) }, onClick = { menu = false; Exchanges.share(ctx, x) })
                            }
                            DropdownMenuItem(text = { Text(tr("Change my wishlist")) }, onClick = { menu = false; changing = true })
                            if (org) DropdownMenuItem(text = { Text(tr("Exchange settings")) }, onClick = { menu = false; settings = true })
                            DropdownMenuItem(text = { Text(tr("Leave exchange"), color = cs.error) }, onClick = { menu = false; leaving = true })
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
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(tr("This gift exchange isn’t available any more."), color = cs.onSurfaceVariant)
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Back to gift exchanges")) }
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
                Button(onClick = { changing = true }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Choose a wishlist")) }
            }

            if (!x.drawn) {
                // ---- before the draw ----
                GxTitle(tr("People"))
                x.members.forEach { m ->
                    GxPersonRow(m, isMe = m.id == me, organiser = m.id == x.organiser,
                        trailing = if (org && m.id != me) ({ TextButton(onClick = { removing = m }) { Text(tr("Remove")) } }) else null)
                }
                if (x.invited.isNotEmpty()) {
                    Text(
                        tr("Invited: %s", x.invited.joinToString(", ") { it.second.ifBlank { tr("someone") } }),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (org) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { inviting = true }) { Text(tr("Invite friends")) }
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
                            IconButton(enabled = !busy, onClick = { act { Exchanges.removeRule(ctx, gid, rule.first, rule.second, rule.third); null } }) {
                                Icon(AppIcons.Close, tr("Remove rule"), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    TextButton(enabled = x.members.size >= 2, onClick = { addingRule = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
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
                        enabled = !busy && x.members.size >= 3 && x.blocked == null, onClick = { drawAsk = true },
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
                    !x.me.seen -> GxWrapped(busy) {
                        act {
                            Exchanges.open(ctx, gid)
                            confetti++
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
                        Button(onClick = { showList = true }) { Text(tr("See their wishlist")) }
                        OutlinedButton(onClick = { chatSide = "giftee" }) {
                            Text(tr("Ask %s anonymously", giftee.name.ifBlank { tr("them") }))
                            if (x.me.unreadGiftee > 0) RedDot(Modifier.padding(start = 6.dp))
                        }
                    }
                }
                if (x.me.hasSanta) Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { chatSide = "santa" }.padding(12.dp),
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
                                if (m.id != me) TextButton(onClick = { removing = m }) { Text(tr("Remove")) }
                            }
                        })
                    }
                    x.members.filter { it.waiting }.forEach { m ->
                        GxPersonRow(m, isMe = false, organiser = false, trailing = {
                            Row {
                                TextButton(onClick = { adding = m }) { Text(tr("Add to the draw")) }
                                TextButton(onClick = { removing = m }) { Text(tr("Remove")) }
                            }
                        })
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { inviting = true }) { Text(tr("Invite friends")) }
                        OutlinedButton(onClick = { Exchanges.share(ctx, x) }) { Text(tr("Share link")) }
                    }
                    val canReveal = x.day == null || !x.day.isAfter(LocalDate.now())
                    if (!x.revealed && canReveal) Button(onClick = { revealAsk = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
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
    Confetti(confetti)
    }

    if (x == null) return
    if (inviting) GxInviteDialog(x, snack) { inviting = false }
    if (changing) {
        val ask = remember { GxShowAsk() }
        ListPickDialog(
            title = tr("Change my wishlist"), confirm = tr("Save"), current = Exchanges.myListId(ctx, x),
            onClose = { changing = false }, note = gxListNote
        ) { id, fresh ->
            if (!ask.ok(ctx, id)) return@ListPickDialog tr("Pick another wishlist, or make a new one.")
            Exchanges.setList(ctx, x.id, id)
            if (fresh) try { ListVisibility.set(ctx, id, Who.FRIENDS) } catch (e: Exception) {}
            changing = false
            snack.toastSoon(tr("“%s” is in the exchange now", Repo.mineName(id)))
            null
        }
        ask.Host()
    }
    if (settings) ExchangeSettingsDialog(x, snack, onCancelled = { settings = false; onBack() }) { settings = false }
    if (leaving) GxLeaveDialog(x, snack, onLeft = { leaving = false; onBack() }) { leaving = false }
    if (addingRule) GxRuleDialog(x) { addingRule = false }
    if (drawAsk) AlertDialog(
        onDismissRequest = { drawAsk = false },
        title = { Text(tr("Draw names now?")) },
        text = { Text(tr("Everyone gets one name. Rules can’t change after this.")) },
        confirmButton = { TextButton(onClick = { drawAsk = false; draw(false) }) { Text(tr("Draw names")) } },
        dismissButton = { TextButton(onClick = { drawAsk = false }) { Text(tr("Cancel")) } }
    )
    pendingAsk?.let { names ->
        AlertDialog(
            onDismissRequest = { pendingAsk = null },
            title = { Text(if (names.size == 1) tr("%s hasn’t joined yet", names[0]) else tr("%d people haven’t joined yet", names.size)) },
            text = { Text(tr("Draw without them? They can still join later and get a name.")) },
            confirmButton = { TextButton(onClick = { pendingAsk = null; draw(true) }) { Text(tr("Draw without them")) } },
            dismissButton = { TextButton(onClick = { pendingAsk = null }) { Text(tr("Wait")) } }
        )
    }
    if (revealAsk) AlertDialog(
        onDismissRequest = { revealAsk = false },
        title = { Text(tr("Show everyone who had whom?")) },
        text = { Text(tr("Everyone in the exchange sees all the pairs.")) },
        confirmButton = {
            TextButton(onClick = {
                revealAsk = false
                act { if (Exchanges.reveal(ctx, gid)) { confetti++; null } else tr("You can reveal after the exchange date.") }
            }) { Text(tr("Reveal")) }
        },
        dismissButton = { TextButton(onClick = { revealAsk = false }) { Text(tr("Cancel")) } }
    )
    adding?.let { m ->
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(tr("Add %s to the draw?", m.name.ifBlank { tr("them") })) },
            text = { Text(tr("Everyone has seen their name, so one person gets %s instead. They’ll be told.", m.name.ifBlank { tr("them") })) },
            confirmButton = { TextButton(onClick = { adding = null; act { Exchanges.addLate(ctx, gid, m.id); null } }) { Text(tr("Add")) } },
            dismissButton = { TextButton(onClick = { adding = null }) { Text(tr("Cancel")) } }
        )
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tr("Remove %s?", m.name.ifBlank { tr("them") })) },
            text = {
                Text(
                    if (x.drawn) tr("They are told. Only the person who drew them gets a new name.")
                    else tr("They are told. Their wishlist stays theirs.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    act { Exchanges.remove(ctx, gid, m.id); tr("%s was removed", m.name.ifBlank { tr("Someone") }) }
                }) { Text(tr("Remove"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Cancel")) } }
        )
    }
    chatSide?.let { side -> GxChatScreen(x, side) { chatSide = null; scope.launch { try { Exchanges.refresh(ctx) } catch (e: Exception) {} } } }
}

@Composable
private fun GxTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun GxNote(t: String, extra: (@Composable () -> Unit)? = null) {
    val look = LocalLook.current
    Column(Modifier.padding(16.dp).fillMaxWidth().lookCard(look, 7L, look.cardShape(7L, small = true)).padding(14.dp)) {
        Text(t, color = look.cardInk(7L))
        extra?.invoke()
    }
}

@Composable
private fun GxPersonRow(m: GxMember, isMe: Boolean, organiser: Boolean, trailing: (@Composable () -> Unit)?) {
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
private fun GxWrapped(busy: Boolean, onOpen: () -> Unit) {
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
private fun gxRuleLines(x: Exchange): List<Pair<String, Triple<String, String, Boolean>>> {
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
private fun GxRuleDialog(x: Exchange, onClose: () -> Unit) {
    val ctx = LocalContext.current
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
                        Exchanges.addRule(ctx, x.id, g.id, r.id, both)?.let { tr("This rule would make the draw impossible.") + " " + gxStuckText(it) }
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
private fun GxPersonPick(label: String, value: String, people: List<GxMember>, onPick: (GxMember) -> Unit) {
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
private fun GxInviteDialog(x: Exchange, snack: SnackbarHostState, onClose: () -> Unit) {
    val ctx = LocalContext.current
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
                    val failed = Exchanges.invite(ctx, x, friends.map { it.id }.filter { it in picked })
                    busy = false
                    onClose()
                    snack.toastSoon(if (failed == 0) tr("Invitations sent") else tr("Couldn’t do that. Check your connection."))
                }
            }) { Text(tr("Invite")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** The organiser's settings: the details, the cover photo, hand over, and cancel the exchange. */
@Composable
fun ExchangeSettingsDialog(x: Exchange, snack: SnackbarHostState, onCancelled: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
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
                        photo(tr("Back to the theme’s cover")) { Exchanges.setPhoto(ctx, x.id, null, HeroEdit(), fa) }
                    }) { Text(tr("Use the theme")) }
                }
                if (x.members.any { it.id != Exchanges.myId(ctx) && !it.web }) TextButton(enabled = !busy, onClick = { handing = true }) { Text(tr("Hand over to someone else")) }
                TextButton(enabled = !busy, onClick = { cancelling = true }) { Text(tr("Cancel the exchange"), color = cs.error) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (name.isBlank()) { missing = true; return@TextButton }
                busy = true
                scope.launch {
                    try {
                        Exchanges.saveSettings(ctx, x.id, name, gxBudget(budget), currency, day, theme, message)
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
                    Exchanges.setPhoto(ctx, x.id, chosen, e, fa)
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
        Exchanges.handover(ctx, x.id, id)
        handing = false
        onClose()
        snack.toastSoon(tr("%s organises it now", x.nameOf(id)))
    }
    if (cancelling) AlertDialog(
        onDismissRequest = { cancelling = false },
        title = { Text(tr("Cancel “%s” for everyone?", x.name)) },
        text = { Text(tr("Everyone is told. Nobody’s wishlist is deleted.")) },
        confirmButton = {
            TextButton(onClick = {
                cancelling = false
                scope.launch {
                    try {
                        Exchanges.cancel(ctx, x.id)
                        onCancelled()
                        snack.toastSoon(tr("“%s” was cancelled", x.name))
                    } catch (e: Exception) {
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Cancel it"), color = cs.error) }
        },
        dismissButton = { TextButton(onClick = { cancelling = false }) { Text(tr("Keep it")) } }
    )
}

/** Pick another member (hand over, or the next organiser when you leave). */
@Composable
private fun GxPickPersonDialog(x: Exchange, title: String, text: String, confirm: String, onClose: () -> Unit, onPick: suspend (String) -> Unit) {
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
private fun GxLeaveDialog(x: Exchange, snack: SnackbarHostState, onLeft: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val org = Exchanges.isOrganiser(ctx, x)
    if (org && x.members.any { it.id != Exchanges.myId(ctx) && !it.web }) {
        GxPickPersonDialog(
            x, title = tr("Leave “%s”?", x.name), text = tr("Pick who organises it after you."), confirm = tr("Leave"), onClose = onClose
        ) { id ->
            Exchanges.leave(ctx, x.id, id)
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
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (org) Exchanges.cancel(ctx, x.id) else Exchanges.leave(ctx, x.id, null)
                        onLeft()
                        snack.toastSoon(tr("You left “%s”", x.name))
                    } catch (e: Exception) {
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                    busy = false
                }
            }) { Text(if (org) tr("Cancel it") else tr("Leave"), color = cs.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Stay")) } }
    )
}

// ---------- the anonymous chat ----------

/**
 * One anonymous chat: [side] "giftee" (you and the person you drew; they don't see your name) or "santa" (your Secret
 * Santa and you; you don't see theirs). Full screen; new messages every few seconds while it is open.
 */
@Composable
fun GxChatScreen(x: Exchange, side: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
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
                msgs.lastOrNull { !it.mine }?.let { Exchanges.seen(ctx, x.id, side, it.id) }
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
                                if (Exchanges.send(ctx, x.id, side, body)) {
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
