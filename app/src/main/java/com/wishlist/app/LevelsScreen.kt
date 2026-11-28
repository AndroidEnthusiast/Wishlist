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
            // The small bar: no big title here, so no empty space above the level (owner, 28 November 2026).
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                large = false
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            // Everything fits on one screen on a usual phone (owner, 28 November 2026: it scrolled).
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Your level, big.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                st.level?.let { LevelDisc(it, 64.dp) } ?: StarterDisc(64.dp)
                // The style's own title font (Quiet: Fraunces, like your profile name).
                Text(
                    levelHeadline(st.level), style = MaterialTheme.typography.headlineMedium, fontWeight = look.titleWeight,
                    textAlign = TextAlign.Center, color = cs.onBackground
                )
                Text(
                    tr("Every wish you add moves you up a level."), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            // Your total and how far to the next level.
            Column(
                Modifier.fillMaxWidth().lookCard(look, 21L, look.cardShape(21L, small = true)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Your wishes are worth"), style = MaterialTheme.typography.bodyMedium, color = look.cardMuted(21L), modifier = Modifier.weight(1f))
                    Text(st.total, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = look.cardInk(21L))
                }
                // In your own level's colours (the first level's before you reach it), as in the preview.
                val colours = (st.level ?: ValueLevel.RISING).colours()
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(look.cardMuted(21L).copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(st.progress).height(8.dp).clip(RoundedCornerShape(4.dp)).drawBehind { drawRect(Brush.horizontalGradient(colours)) })
                }
                val n = st.next
                if (n == null || st.toGo == null) Text(tr("You’ve reached the top."), style = MaterialTheme.typography.bodyMedium, color = look.cardInk(21L))
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // The amount in bold and the next level's icon after it (owner: as lively as the preview).
                    val amount = wholeMoney(st.toGo, st.cur)
                    val line = tr("%1\$s to go until %2\$s", amount, tr(n.label))
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            val at = line.indexOf(amount)
                            append(line)
                            if (at >= 0) addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold), at, at + amount.length)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = look.cardInk(21L), modifier = Modifier.weight(1f, fill = false)
                    )
                    LevelIcon(n, 18.dp)
                }
            }
            // The four levels.
            ValueLevel.entries.forEach { l -> LevelRow(l, st, onPrizes) }
        }
    }
}

/**
 * The level's icon on a solid pastel disc (owner, 27 November 2026: the see-through discs looked muddy on dark).
 * [locked]: not reached yet, all grey (Iconic too). [passed]: a level below yours, with a tick.
 */
@Composable
internal fun LevelDisc(l: ValueLevel, size: androidx.compose.ui.unit.Dp, locked: Boolean = false, passed: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val pastel = l.colours().map { androidx.compose.ui.graphics.lerp(it, androidx.compose.ui.graphics.Color.White, if (l == ValueLevel.ICONIC) 0.55f else 0.82f) }
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).clip(CircleShape)
                .drawBehind { if (locked) drawRect(cs.onSurface.copy(alpha = 0.09f)) else drawRect(Brush.linearGradient(pastel + pastel.first())) },
            contentAlignment = Alignment.Center
        ) {
            if (l == ValueLevel.ICONIC && !locked) IconicCrown(size * 0.62f)
            else LevelIcon(l, size * 0.5f, grey = if (locked) cs.onSurfaceVariant.copy(alpha = 0.55f) else null)
        }
        if (passed) Box(
            Modifier.align(Alignment.BottomEnd).size(size * 0.38f).clip(CircleShape).background(l.colours().first()),
            contentAlignment = Alignment.Center
        ) { Icon(AppIcons.Check, tr("Reached"), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(size * 0.26f)) }
    }
}

@Composable
private fun LevelRow(l: ValueLevel, st: LevelsState, onPrizes: () -> Unit) {
    val look = LocalLook.current
    val here = l == st.level
    val reached = st.level != null && l.ordinal < st.level.ordinal
    val locked = !here && !reached
    Column(
        Modifier.fillMaxWidth().lookCard(look, 22L + l.ordinal, look.cardShape(22L, small = true))
            // Iconic's card has a soft holographic tint, also before it's reached (as in the preview).
            .then(if (l == ValueLevel.ICONIC) Modifier.clip(look.cardShape(22L, small = true))
                .drawBehind { drawRect(Brush.linearGradient(HoloColours.map { it.copy(alpha = 0.12f) })) } else Modifier)
            .then(if (here) Modifier.border(2.dp, l.colours().first(), look.cardShape(22L, small = true)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LevelDisc(l, 48.dp, locked = locked, passed = reached)
            Column(Modifier.weight(1f).alpha(if (locked) 0.7f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(l.label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = look.cardInk(22L))
                    if (here) LevelChip(tr("Your level"), strong = true)
                }
                Text(tr("From %s", wholeMoney(st.startOf(l), st.cur)), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(22L))
                Text(levelLine(l), style = MaterialTheme.typography.bodyMedium, color = look.cardInk(22L))
            }
        }
        // Iconic's prizes: a secret until reached (owner), then they can be seen again.
        if (l == ValueLevel.ICONIC) {
            if (Iconic.reached) TextButton(onClick = onPrizes) { Text(tr("See your prizes")) }
            else Row(
                Modifier.fillMaxWidth().border(1.5.dp, look.cardMuted(22L).copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The golden padlock of a reserved gift, a bit bigger (owner, 27 November 2026).
                Padlock(0f, Modifier.size(26.dp))
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

/**
 * Before the first level: a twinkle in soft lilac on a pastel circle (owner, 27 November 2026, picked from 30 ideas:
 * simple, and its own colour, so it reads as "before the levels").
 */
@Composable
private fun StarterDisc(size: androidx.compose.ui.unit.Dp) {
    val lilac = androidx.compose.ui.graphics.Color(0xFFA48BE0)
    val twinkle = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(TWINKLE).toPath() }
    Box(
        Modifier.size(size).clip(CircleShape).background(androidx.compose.ui.graphics.lerp(lilac, androidx.compose.ui.graphics.Color.White, 0.84f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size * 0.5f)) {
            val u = this.size.width / 24f
            val p = androidx.compose.ui.graphics.Path().apply { addPath(twinkle); transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) }) }
            drawPath(p, lilac)
        }
    }
}

/** A four-point twinkle in a 24 x 24 box. */
private const val TWINKLE = "M12 2c.6 5.4 4.6 9.4 10 10-5.4.6-9.4 4.6-10 10-.6-5.4-4.6-9.4-10-10 5.4-.6 9.4-4.6 10-10z"

