package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* A wish's price page (rebuilt 19 November 2026: its state holders are in PriceModel.kt, the chart in PriceChart.kt). */

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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // "Price is wrong?" and when the shop was last read (PriceDialogState in PriceModel.kt).
    val st = remember(w.id) { PriceDialogState(ctx, w, scope) }
    if (st.askReport) AlertDialog(
        onDismissRequest = { st.askReport = false },
        title = { Text(tr("Report a wrong price")) },
        text = {
            Text(tr("This saves the shop’s page, as your phone reads it, to a file you can send to the app’s maker. Nothing is sent by itself. If you’re signed in to this shop inside Wishlist, the page can show your name."))
        },
        confirmButton = {
            TextButton(onClick = { st.report() }) { Text(tr("Save and share")) }
        },
        dismissButton = { TextButton(onClick = { st.askReport = false }) { Text(tr("Cancel")) } }
    )
    LaunchedEffect(w.id, w.price) { st.loadChecked() }
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
            if (st.checkedLine != null) Text(st.checkedLine!!, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            // "Price is wrong?" (owner, 10 November 2026; PriceReport.kt) and "All price changes" as two small links.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (w.url.isNotBlank()) Text(
                    if (st.reporting) tr("Saving the shop’s page…") else tr("Price is wrong?"),
                    style = MaterialTheme.typography.labelLarge, color = cs.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !st.reporting) { st.askReport = true }
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
            if (st.reportErr.isNotEmpty()) Text(st.reportErr, style = MaterialTheme.typography.bodySmall, color = cs.error)
            // "View in shop" is the main button, "Close" a plain one under it (owner).
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (w.url.isNotBlank()) Button(
                    onClick = { try { ui.openUri(w.url) } catch (e: Exception) { } }, modifier = Modifier.fillMaxWidth()
                ) { Text(tr("View in shop")) }
                // The way out: plain words, the app's own (docs/STYLE.md; it was Android's own button until 22 November 2026).
                LinkButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Close")) }
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
    // The amount, its error and saving it (TargetState in PriceModel.kt).
    val st = remember(w.id) { TargetState(ctx, w) }
    val glow = if (reached) Shadow(gold.copy(alpha = if (highlight) 0.8f else 0.55f), Offset.Zero, 18f) else null
    fun save() {
        if (st.save()) { keyboard?.hide(); focus.clearFocus() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
            Text(
                tr("Price alert"), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall.copy(shadow = glow),
                color = if (reached) gold else cs.onSurface, modifier = Modifier.padding(end = 10.dp)
            )
            if (st.editing || target == null) {
                OutlinedTextField(
                    st.text, { st.text = it.take(10); st.err = null }, singleLine = true,
                    prefix = { Text(currencySymbol(w.currency)) },
                    isError = st.err != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save(); if (st.err == null) st.editing = false }),
                    modifier = Modifier.width(112.dp)
                )
            } else {
                // Set: the amount in bold (gold when reached). Tap it to change it.
                Text(
                    money(target ?: 0.0, w.currency),
                    style = MaterialTheme.typography.titleMedium.copy(shadow = glow), fontWeight = FontWeight.Bold,
                    color = if (reached) gold else cs.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { st.editing = true }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            SavedFeedback(st.saves, Modifier.padding(start = 6.dp), compact = true)
            if (target != null) {
                // ✕: the target goes and the field is back.
                IconButton(onClick = { st.remove() }) {
                    Icon(AppIcons.Close, tr("Remove the price alert"), tint = cs.onSurfaceVariant)
                }
            }
        }
        st.err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
        Text(
            tr("You’ll get a notification when the price drops to this amount or lower."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}
