package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * The Iconic prize on screen (owner, 27 November 2026, from the preview; Iconic.kt): the "You're Iconic" pop-up, App
 * settings › Iconic with a switch and a personal Preview per prize.
 */

/** The three prizes: Show key, name, the pop-up's line. */
private class Prize(val key: String, val name: String, val line: String)

private fun prizes() = listOf(
    Prize(Iconic.RING, tr("Holo ring"), tr("A shimmering ring round your photo, wherever friends see you.")),
    Prize(Iconic.CARD, tr("Holo share card"), tr("Share a wishlist and its card gets the holo treatment.")),
    Prize(Iconic.GLOW, tr("Glowing reactions"), tr("Your reactions glow on your friends’ wishes."))
)

/** The white crown on a holographic disc. */
@Composable
fun IconicCrown(size: Dp) {
    val crown = remember { PathParser().parsePathString("M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5z").toPath() }
    Box(
        Modifier.size(size).rotate(-6f).clip(CircleShape).drawBehind { drawRect(Brush.linearGradient(HoloColours)) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * 0.55f)) {
            val u = this.size.width / 24f
            val p = androidx.compose.ui.graphics.Path().apply { addPath(crown); transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) }) }
            drawPath(p, Color.White)
        }
    }
}

/** A small picture of each prize, with your own photo for the ring. */
@Composable
private fun PrizeArt(key: String) {
    val cs = MaterialTheme.colorScheme
    when (key) {
        Iconic.RING -> Avatar(Friends.myPhoto, Shared.lastName, 44.dp, ring = true)
        Iconic.CARD -> Box(
            Modifier.size(width = 48.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant).holoEdge(true, 8.dp),
            contentAlignment = Alignment.Center
        ) { PlaceholderIcon("gift", 18.dp) }
        else -> GlowChip(Reactions.recentFirst(LocalContext.current), "2")
    }
}

/** A reaction chip that glows (the glowing reactions prize). */
@Composable
private fun GlowChip(emoji: String, count: String, glow: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = CHIP_HEIGHT).holoGlow(glow).clip(CircleShape).background(cs.surfaceVariant).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text("$emoji $count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun PrizeRow(p: Prize) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant.copy(alpha = 0.5f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { PrizeArt(p.key) }
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Text(p.line, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** "You're Iconic": once, when Home's total first reaches Iconic; and again from "See your prizes" on the levels page. */
@Composable
fun IconicDialog(onClose: () -> Unit, onChoose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { IconicCrown(72.dp) },
        title = { Text(tr("You’re Iconic")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val cur = Iconic.currency.ifBlank { localCurrency() }
                Text(tr("Your wishlist just hit %s. Legendary taste deserves a little shine:", wholeMoney(ValueLevel.ICONIC.from * (ValueLevels.perEuro(cur) ?: 1.0), cur)))
                prizes().forEach { PrizeRow(it) }
                Text(tr("You can change these any time in App settings › Iconic."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Love it")) } },
        secondButton = { TextButton(onClick = onChoose) { Text(tr("Choose what’s on")) } }
    )
}

/** App settings › Iconic: a switch per prize, each with a Preview. Only reached once you're Iconic. */
@Composable
internal fun IconicSettingsPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<String?>(null) }
    fun set(key: String, on: Boolean) { Iconic.set(ctx, key, on); scope.launch { Iconic.sync(ctx) } }
    SettingsGroup("") {
        val about = mapOf(
            Iconic.RING to tr("Round your photo, for you and your friends."),
            Iconic.CARD to tr("When you share a wishlist, and at its top for friends."),
            Iconic.GLOW to tr("Your reactions on friends’ wishes.")
        )
        prizes().forEach { p ->
            Column {
                SettingSwitch(p.name, about[p.key], Show[p.key]) { set(p.key, it) }
                TextButton(onClick = { preview = p.key }) { Text(tr("Preview")) }
            }
        }
    }
    Text(
        tr("They stay yours, even when your total drops."), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)
    )
    preview?.let { key -> IconicPreviewSheet(key, onTurnOn = { set(key, true); preview = null }) { preview = null } }
}

/** A prize as it will look, with your own photo, your own list and your own last emoji. */
@Composable
private fun IconicPreviewSheet(key: String, onTurnOn: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val p = prizes().first { it.key == key }
    val on = Show[key]
    PickSheet(p.name, onClose = onClose, action = if (on) null else tr("Turn on"), onAction = onTurnOn) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val caption = when (key) {
                Iconic.RING -> {
                    Avatar(Friends.myPhoto, Shared.lastName, 96.dp, ring = true)
                    Text(Shared.lastName.ifBlank { tr("You") }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    tr("Your own photo, as friends see you: in their friends, groups and chats.")
                }
                Iconic.CARD -> {
                    val d = remember { Repo.allMine(ctx).firstOrNull() }
                    val name = d?.name?.let { if (it == "My wishlist") tr(it) else it } ?: tr("My wishlist")
                    val n = d?.wishes?.count { !it.priv && it.bin == 0L } ?: 0
                    val pic = remember(name, n) { IconicCard.draw(ctx, name, n).asImageBitmap() }
                    Image(pic, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                    tr("Your list “%s”, as friends get it on WhatsApp or Signal.", name)
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        GlowChip(Reactions.recentFirst(ctx), "1")
                        GlowChip("🔥", "2", glow = false)
                    }
                    tr("Your reaction on a friend’s wish, as they and everyone on the list see it.")
                }
            }
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            if (on) Text(tr("This is on."), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
    }
}
