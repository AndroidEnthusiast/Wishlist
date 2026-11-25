package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
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
            Text(
                was ?: " ", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                textDecoration = if (was != null) TextDecoration.LineThrough else null, color = look.cardMuted(w.id)
            )
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
        if ((drop || rise) && oldP != null) Text(
            money(oldP, w.currency), style = MaterialTheme.typography.bodySmall, maxLines = 1,
            textDecoration = TextDecoration.LineThrough, color = look.cardMuted(w.id),
            modifier = Modifier.alignByBaseline()
        )
    }
}
