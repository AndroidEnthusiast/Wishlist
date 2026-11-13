package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/*
 * Reactions on wishes (owner, 7 November 2026 evening; supabase-together.sql):
 *  - A small "+ react" pill on someone else's wish: friends' wishlists, group members' wishlists and gift lists (all
 *    givers). Never on your own list, and not in a gift exchange. The owner sees the reactions on their own list.
 *  - Any emoji: a picker with categories, "Recently used" and a search bar (the free Unicode emoji list with English
 *    names, assets/emoji.txt; credited in Licences). Shown as "😊 2"; yours is highlighted.
 *  - One reaction per person per wish: a new one replaces the old; tap yours to take it away. Long press shows who.
 *  - The owner gets one bundled notification a day at most, in the evening (around 19:00), with a switch in App
 *    settings > Notifications.
 */

data class React(val item: Long, val emoji: String, val name: String, val mine: Boolean, val at: Long)

/**
 * What the cards on a list need from it ([LocalWishDeco]): the list's code for reactions, whether you may react, and
 * what tapping a "Reserved" label does ([onBadge], stuck reservations; null: nothing).
 */
class WishDeco(val code: String, val canReact: Boolean, val onBadge: ((Wish) -> Unit)? = null)

val LocalWishDeco = compositionLocalOf<WishDeco?> { null }

/** The height of the small chips on a card: the size label, reactions, "+ react" (docs/STYLE.md). */
val CHIP_HEIGHT = 28.dp

object Reactions {
    private val map = mutableStateMapOf<String, List<React>>()
    /** "missing" when Supabase doesn't have reactions yet (then nothing shows). */
    var missing by mutableStateOf(false)
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun of(code: String, item: Long): List<React> = map[code].orEmpty().filter { it.item == item }

    private fun time(s: String): Long = try { Instant.parse(s).toEpochMilli() } catch (e: Exception) {
        try { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (e2: Exception) { 0L }
    }

    private fun body(c: Context, code: String): JSONObject {
        val auth = Friends.auth(c)
        return JSONObject().put("p_code", code).put("p_id", auth?.optString("p_id").orEmpty())
            .put("p_token", auth?.optString("p_token").orEmpty()).put("p_device", Repo.deviceId)
    }

    private suspend fun fetch(c: Context, code: String): List<React> {
        val a = JSONArray(Cloud.rpc("wl_rx_get", body(c, code)))
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            React(it.optLong("i"), it.optString("e"), it.optString("n"), it.optBoolean("m"), time(it.optString("at")))
        }
    }

    /** Loads the reactions on list [code]. Quiet when offline. */
    suspend fun load(c: Context, code: String) {
        try {
            map[code] = fetch(c, code)
            missing = false
        } catch (e: Exception) {
            if ((e.message ?: "").contains("wl_rx_get")) missing = true
        }
    }

    /** Your reaction on wish [item] ('' takes it away). Shows at once; undone with a message when it can't be saved. */
    suspend fun set(c: Context, code: String, item: Long, emoji: String): String? {
        val before = map[code].orEmpty()
        val mineGone = before.filterNot { it.item == item && it.mine }
        map[code] = if (emoji.isEmpty()) mineGone else mineGone + React(item, emoji, Shared.lastName, true, System.currentTimeMillis())
        if (emoji.isNotEmpty()) addRecent(c, emoji)
        return try {
            val ok = Cloud.rpc("wl_rx_set", body(c, code).put("p_item", item).put("p_name", "").put("p_emoji", emoji)).trim() == "true"
            if (!ok) { map[code] = before; tr("Couldn’t react. Check your connection.") } else null
        } catch (e: Exception) {
            map[code] = before
            if ((e.message ?: "").contains("wl_rx_set")) tr("Reactions need an update in Supabase first.")
            else tr("Couldn’t react. Check your connection.")
        }
    }

    // ---- recently used ----

    fun recent(c: Context): List<String> = prefs(c).getString("rx_recent", "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun addRecent(c: Context, e: String) {
        val l = (listOf(e) + recent(c).filter { it != e }).take(24)
        prefs(c).edit().putString("rx_recent", l.joinToString("\n")).apply()
    }

    // ---- the evening summary for the owner ----

    /**
     * Once a day at most (owner): the reactions on your own lists that you weren't told about yet, in one notification.
     * In the evening (from 19:00); a reaction waiting since yesterday is told at the next check anyway.
     */
    suspend fun daily(c: Context) {
        if (!Show.on(c, "nt_react")) return
        val p = prefs(c)
        val today = LocalDate.now().toString()
        if (p.getString("rx_told_day", "") == today) return
        // Before the evening only a reaction waiting since yesterday is told, so asking once is enough (2.175: it
        // asked every half hour, for every list, only to wait for the evening).
        if (LocalTime.now().hour < 19) {
            if (p.getString("rx_morning", "") == today) return
            p.edit().putString("rx_morning", today).apply()
        }
        val since = p.getLong("rx_told_at", 0L)
        val fresh = ArrayList<Pair<MineData, React>>()
        for (d in Repo.allMine(c)) {
            val code = d.code ?: continue
            val l = try { fetch(c, code) } catch (e: Exception) { continue }
            l.filter { it.at > since && !it.mine }.forEach { fresh += d to it }
        }
        if (since == 0L) {
            // The first time: start counting from now, so old reactions don't all come at once.
            p.edit().putLong("rx_told_at", System.currentTimeMillis()).apply()
            return
        }
        if (fresh.isEmpty()) return
        val evening = LocalTime.now().hour >= 19
        val old = fresh.minOf { it.second.at } < System.currentTimeMillis() - 24 * 3_600_000L
        if (!evening && !old) return
        val names = fresh.map { it.second.name.ifBlank { tr("Someone") } }.distinct()
        val title = when (names.size) {
            1 -> tr("%s reacted to your wishes", names[0])
            2 -> tr("%1\$s and %2\$s reacted to your wishes", names[0], names[1])
            else -> tr("%1\$s and %2\$d others reacted to your wishes", names[0], names.size - 1)
        }
        val wishes = fresh.groupBy { it.first.id to it.second.item }.entries.take(4).joinToString(" · ") { (k, v) ->
            val w = Repo.allMine(c).firstOrNull { it.id == k.first }?.wishes?.firstOrNull { it.id == k.second }
            v.map { it.second.emoji }.distinct().take(3).joinToString("") + " " + (w?.label ?: tr("a wish"))
        }
        val list = fresh.groupBy { it.first.id }.maxByOrNull { it.value.size }!!.key
        Notifications.add(c, "reacts", "m:$list", title, extra = wishes)
        Alerts.notifyReactions(c, "m:$list", title, wishes)
        p.edit().putString("rx_told_day", today).putLong("rx_told_at", fresh.maxOf { it.second.at }).apply()
    }

    // ---- the emoji list ----

    data class Emoji(val char: String, val name: String)
    data class EmojiGroup(val name: String, val emoji: List<Emoji>)

    private var groups: List<EmojiGroup>? = null

    /** The emoji with their English names, by group (assets/emoji.txt: "=Group", then "emoji name" lines). */
    fun groups(c: Context): List<EmojiGroup> {
        groups?.let { return it }
        val out = ArrayList<EmojiGroup>()
        var name = ""
        var cur = ArrayList<Emoji>()
        try {
            c.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { l ->
                    if (l.startsWith("=")) {
                        if (cur.isNotEmpty()) out += EmojiGroup(name, cur)
                        name = l.substring(1); cur = ArrayList()
                    } else if (l.isNotBlank()) {
                        val i = l.indexOf(' ')
                        if (i > 0) cur += Emoji(l.substring(0, i), l.substring(i + 1))
                    }
                }
            }
            if (cur.isNotEmpty()) out += EmojiGroup(name, cur)
        } catch (e: Exception) {
        }
        groups = out
        return out
    }
}

/** Shows and loads the reactions of list [code] while it is on screen (every 30 seconds). */
@Composable
fun ReactionsLoader(code: String?) {
    val ctx = LocalContext.current
    LaunchedEffect(code) {
        if (code.isNullOrEmpty()) return@LaunchedEffect
        while (true) {
            Reactions.load(ctx, code)
            delay(30000)
        }
    }
}

/**
 * Under a wish: the reactions ("😍 2", yours highlighted) and, when you may react, the "+ react" pill. Tapping yours
 * takes it away, tapping another one reacts with the same emoji; a long press shows who.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReactionBar(
    w: Wish, modifier: Modifier = Modifier, lead: (@Composable () -> Unit)? = null,
    // At most this many reactions as chips; the rest in a "+N" circle that lists them all (owner, 7 November 2026).
    max: Int = 3,
    // Smaller chips for narrow places (Rows next to its buttons, Two per row).
    compact: Boolean = false
) {
    val deco0 = LocalWishDeco.current
    // [lead] (the size label) shares the row (owner, 7 November 2026); without reactions it stands alone.
    if (deco0 == null || Reactions.missing) {
        if (lead != null) Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { lead() }
        return
    }
    val deco: WishDeco = deco0
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val rx = Reactions.of(deco.code, w.id)
    if (rx.isEmpty() && !deco.canReact) {
        if (lead != null) Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { lead() }
        return
    }
    var picking by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun react(e: String) {
        scope.launch { msg = Reactions.set(ctx, deco.code, w.id, e) }
    }
    val byEmoji = rx.groupBy { it.emoji }.entries.sortedByDescending { it.value.size }
    val shown = byEmoji.take(max)
    val hidden = byEmoji.size - shown.size
    var all by remember { mutableStateOf(false) }
    val pad = if (compact) 8.dp else 10.dp
    // One row of chips, all the same height (docs/STYLE.md: chips 28 dp, a 48 dp touch area for the ones you tap).
    FlowRow(modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        // Every item is 48 dp tall (the tap area) with the visible chip centred in it, so the row lines up.
        if (lead != null) Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { lead() }
        shown.forEach { (e, l) ->
            val mine = l.any { it.mine }
            Row(
                Modifier.minimumInteractiveComponentSize().heightIn(min = CHIP_HEIGHT).clip(CircleShape)
                    .background(if (mine) look.accent.copy(alpha = 0.18f) else ink.copy(alpha = 0.07f))
                    .border(1.dp, if (mine) look.textAccent else ink.copy(alpha = 0.12f), CircleShape)
                    .combinedClickable(
                        onClick = { if (deco.canReact) react(if (mine) "" else e) else who = e },
                        onLongClick = { who = e }
                    )
                    .padding(horizontal = pad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(e, fontSize = if (compact) 14.sp else 15.sp)
                Text(" ${l.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
            }
        }
        // "+2": the reactions that don't fit; tapping it lists every reaction, the shown ones too.
        if (hidden > 0) Box(
            Modifier.minimumInteractiveComponentSize().size(CHIP_HEIGHT).clip(CircleShape)
                .background(ink.copy(alpha = 0.07f)).border(1.dp, ink.copy(alpha = 0.12f), CircleShape)
                .clickable(onClickLabel = tr("All reactions")) { all = true },
            contentAlignment = Alignment.Center
        ) {
            Text("+$hidden", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        }
        if (deco.canReact) Row(
            Modifier.minimumInteractiveComponentSize().heightIn(min = CHIP_HEIGHT).clip(CircleShape).border(1.dp, ink.copy(alpha = 0.25f), CircleShape)
                .clickable(onClickLabel = tr("React")) { picking = true }.padding(horizontal = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+ " + tr("React"), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f), maxLines = 1)
        }
    }
    // Every reaction on this wish, with who (owner: the "+N" circle shows them all).
    if (all) AlertDialog(
        onDismissRequest = { all = false },
        title = { Text(tr("Reactions")) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                byEmoji.forEach { (e, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e, fontSize = 22.sp)
                        Text(
                            " ${l.size} · " + l.joinToString(", ") { if (it.mine) tr("You") else it.name.ifBlank { tr("Someone") } },
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { all = false }) { Text(tr("Close")) } }
    )
    who?.let { e ->
        val names = rx.filter { it.emoji == e }.map { if (it.mine) tr("You") else it.name.ifBlank { tr("Someone") } }
        AlertDialog(
            onDismissRequest = { who = null },
            title = { Text(e + " " + names.size, fontSize = 22.sp) },
            text = { Text(names.joinToString(", ")) },
            confirmButton = { TextButton(onClick = { who = null }) { Text(tr("Close")) } }
        )
    }
    msg?.let { m ->
        LaunchedEffect(m) { android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show(); msg = null }
    }
    if (picking) EmojiPickerDialog(onPick = { e -> picking = false; react(e) }, onClose = { picking = false })
}

/** The emoji picker: a search bar, "Recently used", then every group with a row of tabs to jump to it. */
@Composable
fun EmojiPickerDialog(onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val groups = remember { Reactions.groups(ctx) }
    val recent = remember { Reactions.recent(ctx) }
    var query by remember { mutableStateOf("") }
    val grid = rememberLazyGridState()
    // Where each group starts in the grid (a heading row, then its emoji), for the tabs.
    val starts = remember(groups, recent) {
        val m = HashMap<Int, Int>()
        var i = if (recent.isEmpty()) 0 else 1 + recent.size
        groups.forEachIndexed { g, gr -> m[g] = i; i += 1 + gr.emoji.size }
        m
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("React")) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it.take(30) }, singleLine = true, placeholder = { Text(tr("Search emoji")) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (query.isBlank()) LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(groups.size) { g ->
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { scope.launch { grid.scrollToItem(starts[g] ?: 0) } },
                            contentAlignment = Alignment.Center
                        ) {
                            // Grey drawn icons for the groups (owner, 7 November 2026: emoji there looked tappable).
                            GroupIcon(g, cs.onSurfaceVariant, Modifier.size(22.dp).semantics { contentDescription = tr(groups[g].name) })
                        }
                    }
                }
                val q = query.trim().lowercase()
                val found = if (q.isEmpty()) emptyList() else groups.flatMap { it.emoji }.filter { e -> q.split(" ").all { it in e.name } }.take(240)
                LazyVerticalGrid(
                    GridCells.Adaptive(42.dp), Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 6.dp), state = grid
                ) {
                    if (q.isNotEmpty()) {
                        if (found.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(tr("No emoji found."), color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                        }
                        items(found, key = { "s" + it.char }) { e -> EmojiCell(e.char, e.name, onPick) }
                    } else {
                        if (recent.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "h-recent") {
                                Text(tr("Recently used"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                            }
                            items(recent, key = { "r$it" }) { e -> EmojiCell(e, e, onPick) }
                        }
                        groups.forEachIndexed { g, gr ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "h$g") {
                                Text(tr(gr.name), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                            }
                            items(gr.emoji, key = { "g$g" + it.char }) { e -> EmojiCell(e.char, e.name, onPick) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } }
    )
}

@Composable
private fun EmojiCell(e: String, name: String, onPick: (String) -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = name) { onPick(e) },
        contentAlignment = Alignment.Center
    ) { Text(e, fontSize = 24.sp) }
}

/**
 * The emoji groups' tabs as simple grey line icons, drawn here (our own drawings): a smiley, a person, a leaf, a cup,
 * a car, a ball, a light bulb, a heart and a flag, in the order of assets/emoji.txt.
 */
@Composable
fun GroupIcon(i: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 24f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * u, y * u)
        fun path(b: androidx.compose.ui.graphics.Path.() -> Unit) = androidx.compose.ui.graphics.Path().apply(b)
        fun androidx.compose.ui.graphics.Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
        fun androidx.compose.ui.graphics.Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
        fun androidx.compose.ui.graphics.Path.q(a: Float, b: Float, x: Float, y: Float) = quadraticBezierTo(a * u, b * u, x * u, y * u)
        when (i) {
            0 -> { // smiley
                drawCircle(color, 9f * u, o(12f, 12f), style = st)
                drawCircle(color, 1.3f * u, o(9f, 10f)); drawCircle(color, 1.3f * u, o(15f, 10f))
                drawPath(path { m(8f, 14.5f); q(12f, 18.5f, 16f, 14.5f) }, color, style = st)
            }
            1 -> { // person
                drawCircle(color, 3.6f * u, o(12f, 8f), style = st)
                drawPath(path { m(5f, 20f); q(5f, 13.5f, 12f, 13.5f); q(19f, 13.5f, 19f, 20f) }, color, style = st)
            }
            2 -> { // leaf
                drawPath(path { m(5f, 19f); q(4f, 6f, 19f, 5f); q(19f, 19f, 5f, 19f) }, color, style = st)
                drawPath(path { m(5f, 19f); l(13f, 11f) }, color, style = st)
            }
            3 -> { // cup
                drawPath(path { m(5f, 9f); l(16f, 9f); l(15f, 18f); q(14.8f, 19.5f, 13f, 19.5f); l(8f, 19.5f); q(6.2f, 19.5f, 6f, 18f); l(5f, 9f) }, color, style = st)
                drawPath(path { m(16f, 11f); q(20f, 11f, 19.5f, 13.5f); q(19f, 15.5f, 15.5f, 15.5f) }, color, style = st)
                drawPath(path { m(9f, 6.5f); l(9f, 4.5f); m(12f, 6.5f); l(12f, 4.5f) }, color, style = st)
            }
            4 -> { // car
                drawPath(path { m(4f, 16f); l(4f, 12.5f); l(6.5f, 8f); l(17.5f, 8f); l(20f, 12.5f); l(20f, 16f); l(4f, 16f) }, color, style = st)
                drawCircle(color, 1.8f * u, o(8f, 17f)); drawCircle(color, 1.8f * u, o(16f, 17f))
                drawPath(path { m(4f, 12.5f); l(20f, 12.5f) }, color, style = st)
            }
            5 -> { // ball
                drawCircle(color, 9f * u, o(12f, 12f), style = st)
                drawPath(path { m(3f, 12f); l(21f, 12f); m(12f, 3f); q(7f, 12f, 12f, 21f); m(12f, 3f); q(17f, 12f, 12f, 21f) }, color, style = st)
            }
            6 -> { // light bulb
                drawPath(path { m(9f, 16f); q(9f, 14f, 7.5f, 12.5f); q(5f, 10f, 6f, 7f); q(7.5f, 3.5f, 12f, 3.5f); q(16.5f, 3.5f, 18f, 7f); q(19f, 10f, 16.5f, 12.5f); q(15f, 14f, 15f, 16f); l(9f, 16f) }, color, style = st)
                drawPath(path { m(9.5f, 19f); l(14.5f, 19f); m(10.5f, 21.5f); l(13.5f, 21.5f) }, color, style = st)
            }
            7 -> { // heart
                drawPath(path { m(12f, 20f); q(3f, 14f, 3.5f, 9f); q(4f, 4.5f, 8f, 4.5f); q(10.5f, 4.5f, 12f, 7f); q(13.5f, 4.5f, 16f, 4.5f); q(20f, 4.5f, 20.5f, 9f); q(21f, 14f, 12f, 20f) }, color, style = st)
            }
            else -> { // flag
                drawPath(path { m(6f, 21f); l(6f, 4f) }, color, style = st)
                drawPath(path { m(6f, 4.5f); q(9f, 3f, 12f, 4.5f); q(15f, 6f, 18f, 4.5f); l(18f, 12.5f); q(15f, 14f, 12f, 12.5f); q(9f, 11f, 6f, 12.5f) }, color, style = st)
            }
        }
    }
}
