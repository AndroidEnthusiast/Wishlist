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
import androidx.compose.material3.OutlinedTextField
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
fun ReactionBar(w: Wish, modifier: Modifier = Modifier) {
    val deco = LocalWishDeco.current ?: return
    if (Reactions.missing) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val rx = Reactions.of(deco.code, w.id)
    if (rx.isEmpty() && !deco.canReact) return
    var picking by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun react(e: String) {
        scope.launch { msg = Reactions.set(ctx, deco.code, w.id, e) }
    }
    val byEmoji = rx.groupBy { it.emoji }.entries.sortedByDescending { it.value.size }
    FlowRow(modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        byEmoji.forEach { (e, l) ->
            val mine = l.any { it.mine }
            Row(
                Modifier.clip(CircleShape)
                    .background(if (mine) look.accent.copy(alpha = 0.18f) else ink.copy(alpha = 0.07f))
                    .border(1.dp, if (mine) look.accent else ink.copy(alpha = 0.12f), CircleShape)
                    .combinedClickable(
                        onClick = { if (deco.canReact) react(if (mine) "" else e) else who = e },
                        onLongClick = { who = e }
                    )
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(e, fontSize = 15.sp)
                Text(" ${l.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
            }
        }
        if (deco.canReact) Row(
            Modifier.clip(CircleShape).border(1.dp, ink.copy(alpha = 0.25f), CircleShape)
                .clickable { picking = true }.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+ " + tr("react"), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
        }
    }
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
                        ) { Text(groups[g].emoji.first().char, fontSize = 20.sp) }
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
