package com.wishlist.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The one reservation button of a card, in every view and on every list where you reserve (owner, 23 November 2026:
 * "Reserved by you" moved the button down and made cards busy). [state] from [reserveState]:
 *  - "reserve": the filled Reserve button;
 *  - "mine": "Reserved by you" (or "You reserved 2") with a padlock; one tap opens the "Unreserve?" pop-up, like every
 *    other pop-up, with a padlock that opens as it appears (owner, later the same day: not two taps on the button);
 *  - "taken": a greyed "Reserved" with a padlock (the card is darkened); tapping it asks whether it is still being
 *    bought ([onTaken], LocalWishDeco.onBadge), as the old label did.
 */
@Composable
fun ReserveButton(
    w: Wish, state: String, text: String, set: (Boolean) -> Unit, onTaken: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var asking by androidx.compose.runtime.saveable.rememberSaveable(w.id) { androidx.compose.runtime.mutableStateOf(false) }
    // Just tapped Reserve (owner, 24 November 2026): the golden padlock on "Reserved by you" swings shut once.
    var justReserved by androidx.compose.runtime.saveable.rememberSaveable(w.id) { androidx.compose.runtime.mutableStateOf(false) }
    // Someone else reserved it while you look (owner, 25 November 2026): their grey padlock drops in and shuts, so you
    // see live that it's taken. Not when the list opens with it already reserved, and not for your own tap.
    val was = androidx.compose.runtime.remember(w.id) { arrayOf(state) }
    // Not while the list is still loading (a fresher answer than the one kept on the phone isn't "live").
    val shownAt = androidx.compose.runtime.remember(w.id) { System.currentTimeMillis() }
    val takenNow = state == "taken" && was[0] == "reserve" && !justReserved && System.currentTimeMillis() - shownAt > 2_000L
    var takenLive by androidx.compose.runtime.saveable.rememberSaveable(w.id) { androidx.compose.runtime.mutableStateOf(false) }
    val playTaken = takenNow || takenLive
    androidx.compose.runtime.SideEffect { if (takenNow) takenLive = true; was[0] = state }
    val lock: @Composable androidx.compose.foundation.layout.RowScope.(Boolean) -> Unit = { grey ->
        ClosingPadlock(
            play = if (grey) playTaken else justReserved, grey = grey, onDone = { if (grey) takenLive = false else justReserved = false },
            // 18 dp + 4 dp: the same room as the old 16 dp line icon + 6 dp, so "Reserved by you" still fits on the
            // narrowest card with large text (CardLayoutTest).
            modifier = Modifier.size(18.dp).align(androidx.compose.ui.Alignment.CenterVertically)
        )
        Spacer(Modifier.size(4.dp))
    }
    when (state) {
        "reserve" -> Button(onClick = { justReserved = true; set(true) }, modifier = modifier) { androidx.compose.material3.Text(text, maxLines = 1) }
        "mine" -> OutlinedButton(onClick = { asking = true }, modifier = modifier) { lock(false); FitWords(text) }
        "taken" -> Box(modifier, propagateMinConstraints = true) {
            OutlinedButton(onClick = {}, enabled = false) { lock(true); FitWords(text) }
            if (onTaken != null) Box(
                Modifier.matchParentSize().clip(LocalLook.current.buttonShape)
                    .clickable(onClickLabel = tr("Is it still being bought?")) { onTaken() }
            )
        }
    }
    if (asking) UnreserveDialog(w, onYes = { asking = false; set(false) }, onNo = { asking = false })
}

/**
 * "Unreserve?" (owner, 23 November 2026): a pop-up like every other (docs/STYLE.md "Dialogs"). Since 25 November 2026
 * (owner: "a picture of the gift instead of so much text"; the padlock "prominent at the top"): the golden padlock
 * opening above the title, then the gift's photo and name and one short line, then "Yes, unreserve" (the main
 * action) and "No, keep it" (the way out).
 */
@Composable
fun UnreserveDialog(w: Wish, onYes: () -> Unit, onNo: () -> Unit) = AlertDialog(
    onDismissRequest = onNo,
    title = {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            OpeningPadlock(Modifier.size(72.dp))
            androidx.compose.material3.Text(tr("Unreserve?"))
        }
    },
    text = {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                WishThumb(w, 64.dp)
                androidx.compose.material3.Text(
                    w.label, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)
                )
            }
            androidx.compose.material3.Text(
                tr("Someone else can reserve it again."),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
    },
    confirmButton = { Button(onClick = onYes) { androidx.compose.material3.Text(tr("Yes, unreserve")) } },
    dismissButton = { TextButton(onClick = onNo) { androidx.compose.material3.Text(tr("No, keep it")) } }
)

/**
 * The words of a reservation button: a little smaller rather than cut off when the card is narrow (Two per row on a
 * small phone showed "Reserved by", owner's guide: nothing that looks wrong).
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.FitWords(text: String) = FitText(
    // Trimmed line spacing, so the words' middle is the padlock's middle (owner, 23 November 2026: not aligned).
    text, androidx.compose.material3.LocalTextStyle.current.copy(
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
        )
    ), androidx.compose.material3.LocalContentColor.current,
    Modifier.weight(1f, fill = false).align(androidx.compose.ui.Alignment.CenterVertically), androidx.compose.ui.text.style.TextAlign.Start,
    minScale = 0.5f
)

/** How see-through a card is: darkened when someone else reserved it or you have it, smoothly when it changes. */
@Composable
fun dimmed(dim: Boolean): Float =
    androidx.compose.animation.core.animateFloatAsState(if (dim) 0.6f else 1f, androidx.compose.animation.core.tween(380), label = "dim").value
