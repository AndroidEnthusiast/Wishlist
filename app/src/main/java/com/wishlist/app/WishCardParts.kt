package com.wishlist.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * "Unreserve?" waiting for a second tap (owner, 23 November 2026): the wish whose "Reserved by you" was tapped, and
 * where its button is on the screen. A tap anywhere else puts it back ([dismissReserveAsk] on the app's root).
 */
object ReserveAsk {
    var armed by androidx.compose.runtime.mutableStateOf<Long?>(null)
    // Where each "Reserved by you" button is, so the second tap on it is never taken for a tap elsewhere.
    internal val bounds = HashMap<Long, androidx.compose.ui.geometry.Rect>()
}

/** On the app's root: a tap outside the waiting "Unreserve?" button puts it back to "Reserved by you". */
fun Modifier.dismissReserveAsk(): Modifier = composed {
    var here by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    onGloballyPositioned { here = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (ReserveAsk.armed != null) {
                val at = here?.takeIf { it.isAttached }?.localToWindow(down.position) ?: down.position
                if (ReserveAsk.armed?.let { ReserveAsk.bounds[it] }?.contains(at) != true) ReserveAsk.armed = null
            }
        }
    }
}

/**
 * The one reservation button of a card, in every view and on every list where you reserve (owner, 23 November 2026:
 * "Reserved by you" moved the button down and made cards busy). [state] from [reserveState]:
 *  - "reserve": the filled Reserve button;
 *  - "mine": "Reserved by you" (or "You reserved 2") with a closed padlock; a tap turns it into "Unreserve?" with an
 *    open padlock, a second tap unreserves, a tap anywhere else puts it back (owner: no pop-up, and it waits);
 *  - "taken": a greyed "Reserved" with a padlock (the card is darkened); tapping it asks whether it is still being
 *    bought ([onTaken], LocalWishDeco.onBadge), as the old label did.
 */
@Composable
fun ReserveButton(
    w: Wish, state: String, text: String, set: (Boolean) -> Unit, onTaken: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lock: @Composable (open: Boolean) -> Unit = { open ->
        androidx.compose.material3.Icon(if (open) AppIcons.LockOpen else AppIcons.Lock, null, Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
    }
    when (state) {
        "reserve" -> Button(onClick = { set(true) }, modifier = modifier) { androidx.compose.material3.Text(text, maxLines = 1) }
        "mine" -> {
            val asking = ReserveAsk.armed == w.id
            // Leaving the list (or the wish going) never leaves a question waiting.
            androidx.compose.runtime.DisposableEffect(w.id) { onDispose { ReserveAsk.bounds.remove(w.id); if (ReserveAsk.armed == w.id) ReserveAsk.armed = null } }
            OutlinedButton(
                onClick = { if (asking) { ReserveAsk.armed = null; set(false) } else ReserveAsk.armed = w.id },
                modifier = modifier.onGloballyPositioned { ReserveAsk.bounds[w.id] = it.boundsInWindow() },
                danger = asking
            ) { lock(asking); FitWords(if (asking) tr("Unreserve?") else text) }
        }
        "taken" -> Box(modifier, propagateMinConstraints = true) {
            OutlinedButton(onClick = {}, enabled = false) { lock(false); FitWords(text) }
            if (onTaken != null) Box(
                Modifier.matchParentSize().clip(LocalLook.current.buttonShape)
                    .clickable(onClickLabel = tr("Is it still being bought?")) { onTaken() }
            )
        }
    }
}

/**
 * The words of a reservation button: a little smaller rather than cut off when the card is narrow (Two per row on a
 * small phone showed "Reserved by", owner's guide: nothing that looks wrong).
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.FitWords(text: String) = FitText(
    text, androidx.compose.material3.LocalTextStyle.current, androidx.compose.material3.LocalContentColor.current,
    Modifier.weight(1f, fill = false).align(androidx.compose.ui.Alignment.CenterVertically), androidx.compose.ui.text.style.TextAlign.Start,
    minScale = 0.5f
)
