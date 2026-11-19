package com.wishlist.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/* A wish as a card (see WishCards.kt): the card's buttons in one order and the icon size. */

/**
 * The buttons of a wish card, the same on every list (owner, 18 November 2026: "it looks sloppy"): Reserve (or
 * Unreserve) first, at the left in Rows ([spread]) or right after the price on a large card; then the icons (share,
 * bin) and the ⋮ always last, at the right edge, as in Two per row. All icons one size ([cardIcon]).
 */
@Composable
fun RowScope.CardButtons(spread: Boolean, reserve: (@Composable () -> Unit)?, icons: @Composable () -> Unit, more: @Composable () -> Unit) {
    if (reserve != null) Box(Modifier.padding(start = if (spread) 0.dp else 8.dp)) {
        CompositionLocalProvider(LocalCardButton provides true) { reserve() }
    }
    if (spread && reserve != null) Spacer(Modifier.weight(1f))
    icons()
    more()
}

/** The one size of the icons on a wish card: 20 dp in Quiet and in Rows, 24 dp on the large cards of other styles. */
@Composable
fun cardIcon(view: String): androidx.compose.ui.unit.Dp = if (view == "rows" || LocalLook.current.design == "quiet") 20.dp else 24.dp
