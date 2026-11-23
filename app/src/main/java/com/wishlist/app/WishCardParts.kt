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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val lock: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        androidx.compose.material3.Icon(AppIcons.Lock, null, Modifier.size(16.dp).align(androidx.compose.ui.Alignment.CenterVertically))
        Spacer(Modifier.size(6.dp))
    }
    when (state) {
        "reserve" -> Button(onClick = { set(true) }, modifier = modifier) { androidx.compose.material3.Text(text, maxLines = 1) }
        "mine" -> OutlinedButton(onClick = { asking = true }, modifier = modifier) { lock(); FitWords(text) }
        "taken" -> Box(modifier, propagateMinConstraints = true) {
            OutlinedButton(onClick = {}, enabled = false) { lock(); FitWords(text) }
            if (onTaken != null) Box(
                Modifier.matchParentSize().clip(LocalLook.current.buttonShape)
                    .clickable(onClickLabel = tr("Is it still being bought?")) { onTaken() }
            )
        }
    }
    if (asking) UnreserveDialog(w, onYes = { asking = false; set(false) }, onNo = { asking = false })
}

/**
 * "Unreserve?" (owner, 23 November 2026): a pop-up like every other (docs/STYLE.md "Dialogs"): a padlock that opens
 * as it appears, the question, then "Yes, unreserve" (the main action) and "No, keep it" (the way out).
 */
@Composable
fun UnreserveDialog(w: Wish, onYes: () -> Unit, onNo: () -> Unit) = AlertDialog(
    onDismissRequest = onNo,
    title = { androidx.compose.material3.Text(tr("Unreserve?")) },
    text = {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            OpeningPadlock(Modifier.size(56.dp))
            androidx.compose.material3.Text(
                tr("“%s” becomes free again, so someone else can reserve it.", w.label),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
    },
    confirmButton = { Button(onClick = onYes) { androidx.compose.material3.Text(tr("Yes, unreserve")) } },
    dismissButton = { TextButton(onClick = onNo) { androidx.compose.material3.Text(tr("No, keep it")) } }
)

/**
 * The padlock of "Unreserve?": drawn closed, then its shackle lifts and swings open (about half a second), in the
 * app's own line style ([OwnIcons] lock). With animations turned off on the phone it is shown open at once.
 */
@Composable
fun OpeningPadlock(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val col = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val stroke = if (LocalLook.current.design == "chaos") 2.3f else 1.8f
    val t = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (animationsOff(ctx)) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        t.animateTo(1f, androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = tr("Padlock opening") }) {
        val u = size.minDimension / 24f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        // The body and the keyhole stay put.
        drawRoundRect(col, androidx.compose.ui.geometry.Offset(5f * u, 11f * u), androidx.compose.ui.geometry.Size(14f * u, 9f * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u), style = st)
        drawLine(col, androidx.compose.ui.geometry.Offset(12f * u, 14.5f * u), androidx.compose.ui.geometry.Offset(12f * u, 16.5f * u), stroke * u, androidx.compose.ui.graphics.StrokeCap.Round)
        // The shackle: lifts 2.5 units, then turns open around its left leg. Its legs reach down into the body, so
        // the left one stays in the lock when it opens (a real padlock); the part inside the body is never drawn.
        val p = t.value
        val lift = (p * 2f).coerceAtMost(1f) * 2.5f * u
        val turn = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f) * -28f
        val shackle = androidx.compose.ui.graphics.Path().apply {
            moveTo(8.5f * u, 13.5f * u); lineTo(8.5f * u, 8f * u)
            arcTo(androidx.compose.ui.geometry.Rect(8.5f * u, 4.5f * u, 15.5f * u, 11.5f * u), 180f, 180f, false)
            lineTo(15.5f * u, 13.5f * u)
        }
        clipRect(bottom = 11f * u) {
            translate(top = -lift) {
                rotate(turn, androidx.compose.ui.geometry.Offset(8.5f * u, 13.5f * u)) { drawPath(shackle, col, style = st) }
            }
        }
    }
}

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
