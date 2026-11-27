package com.wishlist.app

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Reactions on wishes (see Reactions.kt): loading them, the row under a wish and the emoji picker. What a tap changes
 * is ReactionBarState (ReactionModel.kt). */

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
    // Which dialog is open and reacting (ReactionBarState in ReactionModel.kt), one per wish.
    val st = remember(deco.code, w.id) { ReactionBarState(ctx, deco.code, scope) }
    val reacted = rx.any { it.mine }
    val byEmoji = rx.groupBy { it.emoji }.entries.sortedByDescending { it.value.size }
    val shown = byEmoji.take(max)
    val hidden = byEmoji.size - shown.size
    val pad = if (compact) 8.dp else 10.dp
    // One row of chips, all the same height (docs/STYLE.md: chips 28 dp, a 48 dp touch area for the ones you tap).
    FlowRow(modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        // Every item is 48 dp tall (the tap area) with the visible chip centred in it, so the row lines up.
        if (lead != null) Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { lead() }
        shown.forEach { (e, l) ->
            val mine = l.any { it.mine }
            Row(
                Modifier.minimumInteractiveComponentSize().heightIn(min = CHIP_HEIGHT)
                    // An Iconic member's reaction glows (owner, 27 November 2026; Iconic.kt).
                    .holoGlow(l.any { it.glow }).clip(CircleShape)
                    .background(if (mine) look.accent.copy(alpha = 0.18f) else ink.copy(alpha = 0.07f))
                    .border(1.dp, if (mine) look.textAccent else ink.copy(alpha = 0.12f), CircleShape)
                    .combinedClickable(
                        onClick = { st.tapChip(w.id, e, mine, deco.canReact) },
                        onLongClick = { st.who = e }
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
                .clickable(onClickLabel = tr("All reactions")) { st.all = true },
            contentAlignment = Alignment.Center
        ) {
            Text("+$hidden", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        }
        if (deco.canReact && !reacted) Row(
            Modifier.minimumInteractiveComponentSize().heightIn(min = CHIP_HEIGHT).clip(CircleShape).border(1.dp, ink.copy(alpha = 0.25f), CircleShape)
                .clickable(onClickLabel = tr("React")) { st.picking = true }.padding(horizontal = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A drawn plus, centred with the word (owner, 27 November 2026: the "+" character sat lower than "React").
            Icon(AppIcons.Add, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(if (compact) 14.dp else 16.dp))
            Spacer(Modifier.width(4.dp))
            Text(tr("React"), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f), maxLines = 1)
        }
    }
    // Every reaction on this wish, with who (owner: the "+N" circle shows them all).
    if (st.all) AlertDialog(
        onDismissRequest = { st.all = false },
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
        confirmButton = { TextButton(onClick = { st.all = false }) { Text(tr("Close")) } }
    )
    st.who?.let { e ->
        val names = rx.filter { it.emoji == e }.map { if (it.mine) tr("You") else it.name.ifBlank { tr("Someone") } }
        AlertDialog(
            onDismissRequest = { st.who = null },
            title = { Text(e + " " + names.size, fontSize = 22.sp) },
            text = { Text(names.joinToString(", ")) },
            confirmButton = { TextButton(onClick = { st.who = null }) { Text(tr("Close")) } }
        )
    }
    st.msg?.let { m ->
        LaunchedEffect(m) { android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show(); st.msg = null }
    }
    if (st.picking) EmojiPickerDialog(onPick = { e -> st.picking = false; st.react(w.id, e) }, onClose = { st.picking = false })
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
