package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.appendInlineContent

/*
 * The labels of a wish card, the same in every view (owner, 20 November 2026, after screenshots of labels that
 * overlapped, weren't lined up or didn't fit; docs/decisions/wish-cards.md, last section):
 *  - on the photo, ONE row: the price label at the left, the Love label at the right, centred on one line; when both
 *    don't fit (a small photo, large phone text) the Love label becomes just the heart, so they never overlap;
 *  - a reservation ("Reserved by you", "Reserved", "You reserved 2", "Owned") is a line under the photo, above the
 *    Reserve / Unreserve button, never on the photo;
 *  - the old price never breaks over two lines: it goes under the price when it doesn't fit beside it.
 */

/** The row on top of a photo: [left] (the price label) and [right] (the Love label; compact = the heart only). */
@Composable
fun PictureTopRow(padding: Dp, left: (@Composable () -> Unit)?, right: @Composable (compact: Boolean) -> Unit, modifier: Modifier = Modifier) {
    SubcomposeLayout(modifier.fillMaxWidth().padding(padding)) { c ->
        val loose = Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight)
        val gap = 6.dp.roundToPx()
        val l = left?.let { subcompose("left", it).map { m -> m.measure(loose) } }.orEmpty()
        val lw = l.maxOfOrNull { it.width } ?: 0
        var r = subcompose("right") { right(false) }.map { it.measure(loose) }
        val rw = r.maxOfOrNull { it.width } ?: 0
        if (lw > 0 && rw > 0 && lw + gap + rw > c.maxWidth) {
            r = subcompose("compact") { right(true) }.map { it.measure(loose.copy(maxWidth = (c.maxWidth - lw - gap).coerceAtLeast(0))) }
        }
        val h = maxOf(l.maxOfOrNull { it.height } ?: 0, r.maxOfOrNull { it.height } ?: 0)
        layout(c.maxWidth, h) {
            l.forEach { it.place(0, (h - it.height) / 2) }
            r.forEach { it.place(c.maxWidth - it.width, (h - it.height) / 2) }
        }
    }
}

/**
 * A wish's reservation or status, under its photo (never on it): yours in the accent colour with a tick and a pulse
 * when it arrives, someone else's in grey. Tapping it does what tapping the old label did (LocalWishDeco.onBadge:
 * "Is it still being bought?").
 */
@Composable
fun StatusLine(w: Wish, text: String, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val mine = reservedByMe(text)
    val fx = rememberAppearPulse(mine)
    val col = if (mine) look.textAccent else look.cardMuted(w.id)
    val on = LocalWishDeco.current?.onBadge
    // One height for every status line (48 dp, the touch size, also when it can't be tapped), so cards keep the same
    // rhythm; the words may take two lines on a narrow card with large text rather than being cut off.
    Row(
        modifier.heightIn(min = 48.dp).starPulse(fx, TransformOrigin(0f, 0.5f))
            .then(if (on != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable { on(w) } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The icon is part of the text, so on two lines it stays at the start of the first one.
        val icon = if (mine || w.got) AppIcons.Check else AppIcons.Bag
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                appendInlineContent("i", "•"); append("\u2002"); append(text)
            },
            inlineContent = mapOf("i" to androidx.compose.foundation.text.InlineTextContent(
                androidx.compose.ui.text.Placeholder(1.15.em, 1.15.em, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
            ) { Icon(icon, null, tint = col, modifier = Modifier.fillMaxWidth()) }),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = col, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** The price and, after a change, the old price: beside it when it fits, under it when it doesn't (never broken). */
@Composable
fun PriceAndOld(price: @Composable () -> Unit, old: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    Layout(content = { price(); old?.invoke() }, modifier = modifier) { ms, c ->
        val gap = 8.dp.roundToPx()
        val p = ms[0].measure(c.copy(minWidth = 0, minHeight = 0))
        val o = ms.getOrNull(1)?.measure(Constraints())
        // At least as wide as offered: a narrower layout would be centred in its space (the price then moved right).
        fun w(x: Int) = x.coerceIn(c.minWidth, c.maxWidth)
        when {
            o == null -> layout(w(p.width), p.height) { p.place(0, 0) }
            p.width + gap + o.width <= c.maxWidth -> {
                val h = maxOf(p.height, o.height)
                // Bottoms level, as the two texts sat before.
                layout(w(p.width + gap + o.width), h) { p.place(0, h - p.height); o.place(p.width + gap, h - o.height - 2.dp.roundToPx().coerceAtMost(h - o.height)) }
            }
            else -> layout(w(maxOf(p.width, o.width)), p.height + o.height) { p.place(0, 0); o.place(0, p.height) }
        }
    }
}

/**
 * The price line of a large card: the price (with the old price, [PriceAndOld]) and the buttons on one line when
 * they fit; otherwise the price on its own line and the buttons under it (owner, 20 November 2026: on a narrow phone
 * with large text the price was cut off, "€111.9").
 */
@Composable
fun PriceThenButtons(
    price: @Composable () -> Unit, old: (@Composable () -> Unit)?, buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { c ->
        // No gap of its own: the buttons start with theirs (CardButtons, 8 dp).
        val gap = 0
        val loose = Constraints(maxWidth = c.maxWidth)
        val b = subcompose("buttons") { Row(verticalAlignment = Alignment.CenterVertically) { buttons() } }.map { it.measure(loose) }
        val bw = b.maxOfOrNull { it.width } ?: 0
        val bh = b.maxOfOrNull { it.height } ?: 0
        // The price alone, as wide as it wants: it must never be squeezed.
        val need = subcompose("probe", price).maxOfOrNull { it.measure(Constraints()).width } ?: 0
        if (bw == 0 || need + gap + bw <= c.maxWidth) {
            val pc = Constraints(minWidth = 0, maxWidth = (c.maxWidth - (if (bw > 0) bw + gap else 0)).coerceAtLeast(0))
            val p = subcompose("price") { PriceAndOld(price, old) }.map { it.measure(pc) }
            val ph = p.maxOfOrNull { it.height } ?: 0
            val h = maxOf(ph, bh)
            layout(c.maxWidth, h) {
                p.forEach { it.place(0, (h - it.height) / 2) }
                b.forEach { it.place(c.maxWidth - it.width, (h - it.height) / 2) }
            }
        } else {
            val p = subcompose("price") { PriceAndOld(price, old) }.map { it.measure(loose) }
            val ph = p.maxOfOrNull { it.height } ?: 0
            layout(c.maxWidth, ph + bh) {
                p.forEach { it.place(0, 0) }
                // The buttons under the price, at the right where they always are.
                b.forEach { it.place(c.maxWidth - it.width, ph) }
            }
        }
    }
}
