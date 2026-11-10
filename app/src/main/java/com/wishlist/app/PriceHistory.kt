package com.wishlist.app

import android.content.Context
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

private val DropGreen = Color(0xFF0B7A3E)

/** Shares one wish as plain text: its name, price and link. Works in any app. */
fun shareItem(c: Context, w: Wish, idea: Boolean = Shared.active != null) {
    // Owner, 2.105: "Look what’s on my wishlist" and the shop's link (it works for anyone, whoever can see the list).
    // 2.111 (owner): with an exclamation mark at the end of the sentence. [idea]: a collab wishlist or someone else's
    // list (owner, 7 November 2026: a Friend's wish is shared as a gift idea with its shop link).
    val text = if (idea) {
        if (w.url.isNotBlank()) tr("Look at this gift idea") + "\n" + w.url else tr("Look at this gift idea") + ": " + w.title
    } else {
        if (w.url.isNotBlank()) tr("Look what’s on my wishlist!") + "\n" + w.url else tr("Look what’s on my wishlist: %s!", w.title)
    }
    shareText(c, tr("Share"), text)
}

/**
 * What a long press on a wish opens (owner, 2.105): Edit and Private (with a tick); Price history and Share only in the
 * two-per-row view (2.111: the large card has its own share icon, and Price watch has the history).
 * Top wish is the star on the picture. On a gift list there is no private wish and no price history (null).
 */
@Composable
fun ItemMenuDialog(
    w: Wish, onEdit: () -> Unit, onHistory: (() -> Unit)?, onShare: (() -> Unit)?, onPrivate: (() -> Unit)? = null,
    onOwned: (() -> Unit)? = null, onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // Tighter than a normal dialog (owner, 2.111: too much white space), but not cramped: a smaller title and rows.
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(w.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                MenuRow(tr("Edit"), onEdit, compact = true) { Icon(AppIcons.Edit, null, tint = cs.onSurfaceVariant) }
                if (onPrivate != null) MenuRow(tr("Private"), onPrivate, compact = true, trailing = {
                    Checkbox(checked = w.priv, onCheckedChange = null)
                }) {
                    Icon(PrivateIcon, null, tint = cs.onSurfaceVariant)
                }
                if (onOwned != null) MenuRow(tr("Owned"), onOwned, compact = true, trailing = {
                    Checkbox(checked = w.got, onCheckedChange = null)
                }) {
                    Icon(AppIcons.Check, null, tint = cs.onSurfaceVariant)
                }
                if (onHistory != null) MenuRow(tr("Price history"), onHistory, compact = true) { HistoryIcon(cs.onSurfaceVariant) }
                if (onShare != null) MenuRow(tr("Share"), onShare, compact = true) { Icon(AppIcons.Share, null, tint = cs.onSurfaceVariant) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** "Private" with a tick box on the right, in the ⋮ menu of a wish (2.105). */
@Composable
fun PrivateMenuItem(on: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(tr("Private")) },
        trailingIcon = { Checkbox(checked = on, onCheckedChange = null) },
        onClick = onClick
    )
}

@Composable
fun MenuRow(
    label: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null, compact: Boolean = false,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 14.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A small line-graph icon. */
@Composable
private fun HistoryIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val p = Path().apply {
            moveTo(3f * u, 17f * u); lineTo(9f * u, 11f * u); lineTo(13f * u, 14f * u); lineTo(21f * u, 6f * u)
        }
        drawPath(p, tint, style = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Gold for a reached target price: deeper on light backgrounds, brighter on dark ones (readable on both). */
fun targetGold(dark: Boolean): Color = if (dark) Color(0xFFFFC94D) else Color(0xFFA87400)

/**
 * The price graph of one wish (long press a wish > Price history, or a wish on the Prices tab). Below it the target
 * price (TargetRow, see Targets.kt); a target that is reached shows in gold, bold, with a soft glow ([highlight]: opened
 * from the "Target price reached" alert). "View product" opens the shop's page.
 */
@Composable
fun PriceHistoryDialog(w: Wish, highlight: Boolean = false, showAll: Boolean = true, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val points = History.of(w.id)
    val target = Targets.all[w.id]
    val reached = Targets.reached(w)
    val gold = targetGold(LocalLook.current.dark)
    var checkedLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(w.id, w.price) {
        checkedLine = if (w.url.isBlank()) null else PriceWatch.line(PriceWatch.get(ctx, w.id), System.currentTimeMillis())
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Price history")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    w.label, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (points.isEmpty()) {
                    Text(tr("No price recorded yet. Add a price to this wish and the history starts from there."))
                } else {
                    HistoryBody(points, w.currency, nowSec, if (reached) gold else null)
                }
                // Under "Tracking since…": the price alert, gold when the price is at or below it.
                if (w.price != null) TargetRow(w, reached, gold, highlight)
                // When the shop was last read, or since when it can't be (owner, 10 November 2026; PriceWatch).
                if (checkedLine != null) Text(checkedLine!!, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                // "All price changes" (owner, 7 November 2026): the overview that was the Price watch tab.
                if (showAll) Text(
                    tr("All price changes") + " ›", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClose(); PricesNav.open = true }
                        .padding(vertical = 8.dp, horizontal = 2.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } },
        dismissButton = if (w.url.isNotBlank()) ({
            TextButton(onClick = { try { ui.openUri(w.url) } catch (e: Exception) { } }) { Text(tr("View in shop")) }
        }) else null
    )
}

/**
 * "Target price" in bold with the amount next to it. The keyboard's tick saves it (a green tick shows), the ✕ removes
 * it. A target has to be below the price now (otherwise it would be reached already). When the price comes down to it
 * (a price check), the words and the amount are gold, with a soft glow ([highlight]: opened from the alert).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TargetRow(w: Wish, reached: Boolean, gold: Color, highlight: Boolean) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val target = Targets.all[w.id]
    var text by remember(w.id) { mutableStateOf(target?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "") }
    var saves by remember { mutableStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    val glow = if (reached) Shadow(gold.copy(alpha = if (highlight) 0.8f else 0.55f), Offset.Zero, 18f) else null
    fun save() {
        val v = text.replace(',', '.').trim().toDoubleOrNull()
        val now = w.price ?: return
        when {
            v == null || v <= 0 -> err = tr("Fill in an amount.")
            v >= now - 0.005 -> err = tr("Choose an amount below the price now (%s).", money(now, w.currency))
            else -> {
                Targets.set(ctx, w, v)
                text = String.format(Locale.ROOT, "%.2f", v)
                err = null
                saves++
                keyboard?.hide()
                focus.clearFocus()
            }
        }
    }
    // Editing (the field with its frame) until a target is saved; then the amount in bold, without a frame.
    var editing by remember(w.id) { mutableStateOf(target == null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
            Text(
                tr("Price alert"), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall.copy(shadow = glow),
                color = if (reached) gold else cs.onSurface, modifier = Modifier.padding(end = 10.dp)
            )
            if (editing || target == null) {
                OutlinedTextField(
                    text, { text = it.take(10); err = null }, singleLine = true,
                    prefix = { Text(currencySymbol(w.currency)) },
                    isError = err != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save(); if (err == null) editing = false }),
                    modifier = Modifier.width(112.dp)
                )
            } else {
                // Set: the amount in bold (gold when reached). Tap it to change it.
                Text(
                    money(target ?: 0.0, w.currency),
                    style = MaterialTheme.typography.titleMedium.copy(shadow = glow), fontWeight = FontWeight.Bold,
                    color = if (reached) gold else cs.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editing = true }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            SavedFeedback(saves, Modifier.padding(start = 6.dp), compact = true)
            if (target != null) {
                // ✕: the target goes and the field is back.
                IconButton(onClick = { Targets.set(ctx, w, null); text = ""; err = null; editing = true }) {
                    Icon(AppIcons.Close, tr("Remove the price alert"), tint = cs.onSurfaceVariant)
                }
            }
        }
        err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
        Text(
            tr("You’ll get a notification when the price drops to this amount or lower."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryBody(points: List<PricePoint>, currency: String, nowSec: Long, gold: Color? = null) {
    val cs = MaterialTheme.colorScheme
    val range = remember(points, nowSec) { chartRange(points, nowSec) }
    var frac by remember(points) { mutableStateOf(1f) }
    val span = (range.t1 - range.t0).toDouble()
    val curT = range.t0 + (frac * span).toLong()
    val curP = priceAt(points, curT)
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    fun day(t: Long): String = fmt.format(Date(t * 1000))

    // Price and date under your finger. Without touching: today's price.
    Row(verticalAlignment = Alignment.Bottom) {
        // At or below the target (gold given): the price in gold and bold.
        val atTarget = gold != null && frac >= 0.995f
        Text(
            money(curP, currency), style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (atTarget) FontWeight.Bold else FontWeight.SemiBold,
            color = if (atTarget) gold!! else Color.Unspecified
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (frac >= 0.995f) tr("Now") else day(curT),
            style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant
        )
    }
    PriceChart(
        points, range, frac, { frac = it },
        line = cs.primary, grid = cs.outline.copy(alpha = 0.3f), dotInner = cs.surface,
        modifier = Modifier.fillMaxWidth().height(180.dp)
            .semantics { contentDescription = tr("Price history graph. Drag to see the price on a date.") }
    )
    Row(Modifier.fillMaxWidth()) {
        Text(day(range.t0), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(tr("Today"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }

    val lo = points.minOf { it.p }
    val hi = points.maxOf { it.p }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            Text(tr("Lowest"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(money(lo, currency), style = MaterialTheme.typography.titleSmall)
        }
        Column {
            Text(tr("Highest"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(money(hi, currency), style = MaterialTheme.typography.titleSmall)
        }
        Column {
            Text(tr("Changes"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text((points.size - 1).toString(), style = MaterialTheme.typography.titleSmall)
        }
    }
    val ch = changeSince(points)
    if (ch == null) {
        Text(
            if (points.size < 2) tr("Tracking since %s. No price change yet.", day(range.t0))
            else tr("Back at the first price since %s.", day(range.t0)),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    } else {
        val down = ch.first < 0
        Text(
            (if (down) "−" else "+") + tr("%1\$s (%2\$d%%) since %3\$s", money(abs(ch.first), currency), Math.round(abs(ch.second)), day(range.t0)),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = if (down) DropGreen else cs.error
        )
    }
}

/** A step graph: a price stays the same until the next change. Touch or drag to move the marker along it. */
@Composable
private fun PriceChart(
    points: List<PricePoint>,
    range: ChartRange,
    frac: Float,
    onFrac: (Float) -> Unit,
    line: Color,
    grid: Color,
    dotInner: Color,
    modifier: Modifier
) {
    Canvas(
        modifier.pointerInput(points) {
            val padX = 8.dp.toPx()
            fun toFrac(x: Float): Float = ((x - padX) / (size.width - 2 * padX)).coerceIn(0f, 1f)
            awaitEachGesture {
                val down = awaitFirstDown()
                onFrac(toFrac(down.position.x))
                do {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (ch.pressed) {
                        onFrac(toFrac(ch.position.x))
                        ch.consume()
                    }
                } while (ch.pressed)
            }
        }
    ) {
        val padX = 8.dp.toPx()
        val padY = 10.dp.toPx()
        val w = size.width - 2 * padX
        val h = size.height - 2 * padY
        val bottom = padY + h
        val span = (range.t1 - range.t0).toFloat()
        val yr = (range.yMax - range.yMin).toFloat()
        fun x(t: Long): Float = padX + (t - range.t0).toFloat() / span * w
        fun y(p: Double): Float = padY + ((range.yMax - p).toFloat() / yr) * h

        // faint guide lines
        listOf(padY, padY + h / 2f, bottom).forEach {
            drawLine(grid, Offset(0f, it), Offset(size.width, it), strokeWidth = 1.dp.toPx())
        }

        fun trace(p: Path) {
            p.moveTo(x(points[0].t), y(points[0].p))
            for (i in 1 until points.size) {
                p.lineTo(x(points[i].t), y(points[i - 1].p))
                p.lineTo(x(points[i].t), y(points[i].p))
            }
            p.lineTo(x(range.t1), y(points.last().p))
        }

        val stroke = Path().also { trace(it) }
        val area = Path().also {
            trace(it)
            it.lineTo(x(range.t1), bottom)
            it.lineTo(x(points[0].t), bottom)
            it.close()
        }
        drawPath(area, line.copy(alpha = 0.14f))
        drawPath(stroke, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (points.size <= 80) {
            points.forEach { drawCircle(line, 3.5.dp.toPx(), Offset(x(it.t), y(it.p))) }
        }

        // the marker you move with your finger
        val cx = padX + frac * w
        val curT = range.t0 + (frac * span).toLong()
        drawLine(line.copy(alpha = 0.5f), Offset(cx, padY), Offset(cx, bottom), strokeWidth = 1.5.dp.toPx())
        drawCircle(line, 6.dp.toPx(), Offset(cx, y(priceAt(points, curT))))
        drawCircle(dotInner, 2.5.dp.toPx(), Offset(cx, y(priceAt(points, curT))))
    }
}
