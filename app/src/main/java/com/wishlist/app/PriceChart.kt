package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/* A wish's price page (see PriceHistory.kt): the price history, its chart and the lowest / highest / changes. */

@Composable
internal fun HistoryBody(points: List<PricePoint>, currency: String, nowSec: Long, gold: Color? = null, w: Wish) {
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
            style = MaterialTheme.typography.headlineMedium.number(look), fontWeight = FontWeight.Bold,
            color = if (atTarget) gold!! else cs.onSurface
        )
        val ch = changeSince(points)
        if (now && ch != null && ch.first < 0) {
            // A drop: the paper price tag swings in while the price counts down (owner, 24 November 2026; Objects.kt).
            PriceTag("−" + Math.round(abs(ch.second)) + "%", TAG_INK, Modifier.padding(start = 12.dp), swing = counting != null)
        } else if (now && ch != null) {
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
