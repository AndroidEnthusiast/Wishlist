package com.wishlist.app

import android.content.Context
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
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
internal fun HistoryIcon(tint: Color) {
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryDialog(w: Wish, highlight: Boolean = false, showAll: Boolean = true, onClose: () -> Unit) {
    val go = LocalGo.current
    val ctx = LocalContext.current
    val ui = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val points = History.of(w.id)
    val target = Targets.all[w.id]
    val reached = Targets.reached(w)
    val gold = targetGold(LocalLook.current.dark)
    var checkedLine by remember { mutableStateOf<String?>(null) }
    var askReport by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var reportErr by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (askReport) AlertDialog(
        onDismissRequest = { askReport = false },
        title = { Text(tr("Report a wrong price")) },
        text = {
            Text(tr("This saves the shop’s page, as your phone reads it, to a file you can send to the app’s maker. Nothing is sent by itself. If you’re signed in to this shop inside Wishlist, the page can show your name."))
        },
        confirmButton = {
            TextButton(onClick = {
                askReport = false; reporting = true; reportErr = ""
                scope.launch {
                    val f = PriceReport.make(ctx, w)
                    reporting = false
                    if (f == null || !PriceReport.share(ctx, f)) reportErr = tr("Couldn’t save the shop’s page. Check your connection.")
                }
            }) { Text(tr("Save and share")) }
        },
        dismissButton = { TextButton(onClick = { askReport = false }) { Text(tr("Cancel")) } }
    )
    LaunchedEffect(w.id, w.price) {
        checkedLine = if (w.url.isBlank()) null else PriceWatch.line(PriceWatch.get(ctx, w.id), System.currentTimeMillis())
    }
    // A tall bottom sheet (2.212, owner 15 November 2026: one screen with everything, a clear product photo, "View in
    // shop" the main button above a plain "Close"). It fits a 360 dp phone; only with very big text does it scroll.
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The wish: its photo on the light photo surface, the name and the shop.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val look = LocalLook.current
                Box(
                    Modifier.size(72.dp).clip(look.soft(16.dp)).background(look.photoSurface(w.id)),
                    contentAlignment = Alignment.Center
                ) {
                    if (w.image.isNotBlank()) PhotoStage(w.image, w.label, small = true)
                    else PlaceholderIcon("gift", 30.dp, look.photoInk)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        w.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    val shop = remember(w.url) { try { java.net.URI(w.url).host?.removePrefix("www.") } catch (e: Exception) { null } }
                    Text(
                        listOfNotNull(tr("Price history"), shop).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (points.isEmpty()) {
                Text(
                    tr("No price recorded yet. Add a price to this wish and the history starts from there."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
            } else {
                HistoryBody(points, w.currency, nowSec, if (reached) gold else null, w)
            }
            // The price alert, gold when the price is at or below it.
            if (w.price != null) TargetRow(w, reached, gold, highlight)
            // When the shop was last read (owner, 10 November 2026; PriceWatch), small.
            if (checkedLine != null) Text(checkedLine!!, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            // "Price is wrong?" (owner, 10 November 2026; PriceReport.kt) and "All price changes" as two small links.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (w.url.isNotBlank()) Text(
                    if (reporting) tr("Saving the shop’s page…") else tr("Price is wrong?"),
                    style = MaterialTheme.typography.labelLarge, color = cs.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !reporting) { askReport = true }
                        .padding(vertical = 12.dp, horizontal = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                if (showAll) Text(
                    tr("All price changes") + " ›", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClose(); go(Deep.Prices) }
                        .padding(vertical = 12.dp, horizontal = 2.dp)
                )
            }
            if (reportErr.isNotEmpty()) Text(reportErr, style = MaterialTheme.typography.bodySmall, color = cs.error)
            // "View in shop" is the main button, "Close" a plain one under it (owner).
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (w.url.isNotBlank()) Button(
                    onClick = { try { ui.openUri(w.url) } catch (e: Exception) { } }, modifier = Modifier.fillMaxWidth()
                ) { Text(tr("View in shop")) }
                androidx.compose.material3.TextButton(
                    onClick = onClose, modifier = Modifier.pressScale().fillMaxWidth().heightIn(min = 48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = cs.onSurface)
                ) { Text(tr("Close")) }
            }
        }
    }
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
private fun HistoryBody(points: List<PricePoint>, currency: String, nowSec: Long, gold: Color? = null, w: Wish) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val range = remember(points, nowSec) { chartRange(points, nowSec) }
    var frac by remember(points) { mutableStateOf(1f) }
    val span = (range.t1 - range.t0).toDouble()
    val curT = range.t0 + (frac * span).toLong()
    val curP = priceAt(points, curT)
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    fun day(t: Long): String = fmt.format(Date(t * 1000))
    // A price drop counts down to the new price when the page opens (Celebrate.kt, owner 15 November 2026).
    val counting = rememberCountDown(w)
    val now = frac >= 0.995f
    val shown = if (now && counting != null) counting else curP

    // The price under your finger, big and bold (equal-width figures); without touching: today's price and its change.
    Row(verticalAlignment = Alignment.CenterVertically) {
        val atTarget = gold != null && now
        Text(
            money(shown, currency),
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
            color = if (atTarget) gold!! else cs.onSurface
        )
        val ch = changeSince(points)
        if (now && ch != null) {
            val down = ch.first < 0
            val tint = if (down) look.drop else look.rise
            Text(
                (if (down) "−" else "+") + Math.round(abs(ch.second)) + "%",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = tint,
                modifier = Modifier.padding(start = 10.dp).clip(look.soft(8.dp)).background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(if (now) tr("Now") else day(curT), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PriceChart(
            points, range, frac, { frac = it },
            line = cs.primary, grid = cs.outline.copy(alpha = 0.3f), dotInner = cs.surface,
            modifier = Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = tr("Price history graph. Drag to see the price on a date.") }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(day(range.t0), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(tr("Today"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
    // Lowest, highest and the number of changes as three small chips.
    val lo = points.minOf { it.p }
    val hi = points.maxOf { it.p }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun chip(label: String, value: String, m: Modifier) = Column(
            m.clip(look.soft(12.dp)).background(cs.primaryContainer.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            FitText(value, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), cs.onSurface, align = androidx.compose.ui.text.style.TextAlign.Start)
        }
        chip(tr("Lowest"), money(lo, currency), Modifier.weight(1f))
        chip(tr("Highest"), money(hi, currency), Modifier.weight(1f))
        chip(tr("Changes"), (points.size - 1).toString(), Modifier.weight(0.8f))
    }
    val ch = changeSince(points)
    Text(
        if (ch == null) {
            if (points.size < 2) tr("Tracking since %s. No price change yet.", day(range.t0))
            else tr("Back at the first price since %s.", day(range.t0))
        } else (if (ch.first < 0) "−" else "+") + tr("%1\$s (%2\$d%%) since %3\$s", money(abs(ch.first), currency), Math.round(abs(ch.second)), day(range.t0)),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
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
