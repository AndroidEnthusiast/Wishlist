package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/* A wish as a card (see WishCards.kt): the price line with its old price and the drop or rise. */

/**
 * The price with, after a change, the old price crossed out next to it (owner, 2.126: also on Two per row and Rows,
 * like the large cards). Wraps to a second line when both don't fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceLine(
    w: Wish, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier,
    // Two per row (owner, 25 November 2026): the old price always on its own line under the price, and that line kept
    // empty when there is none, so cards side by side are always the same height (it used to wrap only sometimes).
    stacked: Boolean = false
) {
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    if (stacked) {
        androidx.compose.foundation.layout.Column(modifier) {
            Text(
                if (newP != null) money(newP, w.currency) else tr("No price"),
                style = style, fontWeight = look.priceWeight, fontFamily = look.display, maxLines = 1,
                color = if (drop) look.drop else if (rise) look.rise else ink
            )
            val was = if ((drop || rise) && oldP != null) money(oldP, w.currency) else null
            // No old price: an empty line of the same height (no strike-through, or it drew a small dash).
            if (was != null) OldPrice(w, was, Modifier) else Text(" ", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        return
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        Text(
            if (newP != null) money(newP, w.currency) else tr("No price"),
            style = style, fontWeight = look.priceWeight, fontFamily = look.display, maxLines = 1,
            color = if (drop) look.drop else if (rise) look.rise else ink,
            modifier = Modifier.alignByBaseline()
        )
        if ((drop || rise) && oldP != null) OldPrice(w, money(oldP, w.currency), Modifier.alignByBaseline())
    }
}

/**
 * The old price, crossed out. The first time you see a price change on a wish the line is drawn across it, left to
 * right (owner, 25 November 2026: "only the first time you see that drop"); after that, and with Remove animations,
 * it is simply crossed out.
 */
@Composable
private fun OldPrice(w: Wish, text: String, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val look = LocalLook.current
    val key = "${w.id}:${w.oldPrice}:${w.price}"
    val first = androidx.compose.runtime.remember(key) { SeenChanges.firstLook(ctx, key) && !animationsOff(ctx) }
    val t = androidx.compose.runtime.remember(key) { androidx.compose.animation.core.Animatable(if (first) 0f else 1f) }
    if (first) androidx.compose.runtime.LaunchedEffect(key) {
        kotlinx.coroutines.delay(250)
        t.animateTo(1f, androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val ink = look.cardMuted(w.id)
    Text(
        text, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = ink,
        modifier = modifier.drawWithContent {
            drawContent()
            val y = size.height * 0.56f
            drawLine(ink, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * t.value, y), 1.2.dp.toPx())
        }.semantics { contentDescription = tr("Was %s", text) }
    )
}

/** Which price changes this phone has shown (the line is drawn the first time only); the last 400 are kept. */
internal object SeenChanges {
    fun firstLook(c: android.content.Context, key: String): Boolean {
        val p = c.applicationContext.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE)
        val seen = p.getString("seen_changes", "").orEmpty().split('|').filter { it.isNotEmpty() }
        if (key in seen) return false
        p.edit().putString("seen_changes", (seen + key).takeLast(400).joinToString("|")).apply()
        return true
    }
}
