package com.wishlist.app

import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun shareItem(c: Context, w: Wish) {
    val price = w.price?.let { money(it, w.currency) }
    val head = listOfNotNull(w.title, price).joinToString(" - ")
    shareText(c, tr("Share item"), if (w.url.isNotBlank()) head + "\n" + w.url else head)
}

/** What a long press on a wish opens. */
@Composable
/** The long-press menu. On a shared list there is no most wanted and no price history: leave those out (null). */
fun ItemMenuDialog(w: Wish, onHistory: (() -> Unit)?, onShare: () -> Unit, onStar: (() -> Unit)?, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(w.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (onStar != null) MenuRow(if (w.star) tr("Remove from most wanted") else tr("Most wanted"), onStar) {
                    StarIcon(w.star, if (w.star) StarGold else cs.onSurfaceVariant, 24.dp)
                }
                if (onHistory != null) MenuRow(tr("Price history"), onHistory) { HistoryIcon(cs.onSurfaceVariant) }
                MenuRow(tr("Share item"), onShare) { Icon(Icons.Default.Share, null, tint = cs.onSurfaceVariant) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

@Composable
fun MenuRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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

/** The price graph of one wish. It is not always on screen: long press a wish and choose Price history. */
@Composable
fun PriceHistoryDialog(w: Wish, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val points = History.of(w.id)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Price history")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    w.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (points.isEmpty()) {
                    Text(tr("No price recorded yet. Add a price to this wish and the history starts from there."))
                } else {
                    HistoryBody(points, w.currency, nowSec)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } }
    )
}

@Composable
private fun HistoryBody(points: List<PricePoint>, currency: String, nowSec: Long) {
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
        Text(money(curP, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
            (if (down) "↓ " else "↑ ") + tr("%1\$s (%2\$d%%) since %3\$s", money(abs(ch.first), currency), Math.round(abs(ch.second)), day(range.t0)),
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
