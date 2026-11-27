package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The levels page (owner, 27 November 2026, from a preview), opened by tapping your Total value with its level on Home
 * or a list: your level, your total with how far to the next level, and the four levels with their start and a
 * one-liner. Iconic's prizes stay a secret until you reach it ("Something special unlocks here"); after that "See your
 * prizes" shows them again. Only draws: the numbers come from LevelsModel.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelsScreen(onBack: () -> Unit, onPrizes: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.mineIds) { Repo.allMine(ctx) }
    val st = remember(mine) { LevelsState.of(mine.flatMap { it.wishes }.filter { it.bin == 0L && it.recv == 0L }.distinctBy { it.id }) }
    Iconic.load(ctx)
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Your level, big.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                st.level?.let { LevelDisc(it, 72.dp) }
                Text(
                    levelHeadline(st.level), style = MaterialTheme.typography.headlineSmall, fontWeight = look.titleWeight,
                    fontFamily = look.display, textAlign = TextAlign.Center, color = cs.onBackground
                )
                Text(
                    tr("Every wish you add moves you up a level. Only you can see this."), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            // Your total and how far to the next level.
            Column(
                Modifier.fillMaxWidth().lookCard(look, 21L, look.cardShape(21L, small = true)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Your wishes are worth"), style = MaterialTheme.typography.bodyMedium, color = look.cardMuted(21L), modifier = Modifier.weight(1f))
                    Text(st.total, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = look.cardInk(21L))
                }
                val colours = (st.next ?: ValueLevel.ICONIC).colours()
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(look.cardMuted(21L).copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(st.progress).height(8.dp).clip(RoundedCornerShape(4.dp)).drawBehind { drawRect(Brush.horizontalGradient(colours)) })
                }
                val n = st.next
                Text(
                    if (n == null || st.toGo == null) tr("You’ve reached the top.")
                    else tr("%1\$s to go until %2\$s", wholeMoney(st.toGo, st.cur), tr(n.label)),
                    style = MaterialTheme.typography.bodyMedium, color = look.cardInk(21L)
                )
            }
            // The four levels.
            ValueLevel.entries.forEach { l -> LevelRow(l, st, onPrizes) }
        }
    }
}

/** The level's icon on a soft disc in its colours. */
@Composable
internal fun LevelDisc(l: ValueLevel, size: androidx.compose.ui.unit.Dp) {
    val c = l.colours()
    Box(
        Modifier.size(size).clip(CircleShape).drawBehind { drawRect(Brush.linearGradient(c.map { it.copy(alpha = 0.22f) })) },
        contentAlignment = Alignment.Center
    ) { LevelIcon(l, size * 0.5f) }
}

@Composable
private fun LevelRow(l: ValueLevel, st: LevelsState, onPrizes: () -> Unit) {
    val look = LocalLook.current
    val here = l == st.level
    val reached = st.level != null && l.ordinal < st.level.ordinal
    val locked = !here && !reached
    Column(
        Modifier.fillMaxWidth().lookCard(look, 22L + l.ordinal, look.cardShape(22L, small = true))
            .then(if (here) Modifier.border(2.dp, l.colours().first(), look.cardShape(22L, small = true)) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.alpha(if (locked && l != ValueLevel.ICONIC) 0.45f else 1f)) { LevelDisc(l, 52.dp) }
            Column(Modifier.weight(1f).alpha(if (locked) 0.7f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(l.label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = look.cardInk(22L))
                    if (here) LevelChip(tr("You’re here"), strong = true) else if (reached) LevelChip(tr("Reached"), strong = false)
                }
                Text(tr("From %s", wholeMoney(st.startOf(l), st.cur)), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(22L))
                Text(levelLine(l), style = MaterialTheme.typography.bodyMedium, color = look.cardInk(22L))
            }
        }
        // Iconic's prizes: a secret until reached (owner), then they can be seen again.
        if (l == ValueLevel.ICONIC) {
            if (Iconic.reached) TextButton(onClick = onPrizes) { Text(tr("See your prizes")) }
            else Row(
                Modifier.fillMaxWidth().border(1.5.dp, look.cardMuted(22L).copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(AppIcons.Lock, null, tint = look.cardMuted(22L), modifier = Modifier.size(18.dp))
                Text(tr("Something special unlocks here"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = look.cardInk(22L))
            }
        }
    }
}

@Composable
private fun LevelChip(text: String, strong: Boolean) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
        color = if (strong) cs.surface else cs.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (strong) cs.onSurface else cs.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
