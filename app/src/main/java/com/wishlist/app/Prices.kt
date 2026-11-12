package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Price changes (until 7 November 2026 the "Price watch" tab; now opened from a wish's price page, owner): every wish with a price on your own wishlists, its price now and how much higher or lower
 * that is than when you added it (the first point of its price history). The biggest drops come first. Tapping a
 * wish shows its price history graph. Group wishlists keep no price history, so they are not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(snack: SnackbarHostState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val lists = remember(Repo.mineVersion, Repo.myWishes, Repo.mineIds) { Repo.allMine(ctx) }
    class PriceRow(val list: String, val w: Wish, val change: Double?)
    val rows = lists.flatMap { d ->
        val name = if (d.name == "My wishlist") tr(d.name) else d.name
        d.wishes.filter { it.price != null && !it.got }.map { w ->
            val first = History.of(w.id).firstOrNull()?.p
            val now = w.price ?: 0.0
            PriceRow(name, w, if (first != null && first > 0) (now - first) / first * 100 else null)
        }
    }.distinctBy { it.w.id }.sortedBy { it.change ?: 0.0 } // a copied wish (same id) shows once
    var open by remember { mutableStateOf<Wish?>(null) }
    // (The "Gifts I reserved" tab of 2.138 was dropped, owner 7 November 2026.)
    var targetFor by remember { mutableStateOf<Wish?>(null) }
    Targets.load(ctx)

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Price changes"), fontWeight = look.titleWeight) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr("No prices yet. Wishes with a price show up here, with how much they changed since you added them."),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rows, key = { it.w.id }) { r ->
                    val id = r.w.id
                    Row(
                        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                            .clickable { open = r.w }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val mat = RoundedCornerShape(12.dp)
                        Box(Modifier.size(52.dp).lookMat(look, mat, look.pictureBg(id, false)), contentAlignment = Alignment.Center) {
                            if (r.w.image.isNotBlank()) {
                                WishImage(r.w.image, Modifier.fillMaxSize().padding(4.dp))
                            } else PlaceholderIcon("gift", 22.dp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                            Text(
                                r.w.label, style = MaterialTheme.typography.titleSmall, color = look.cardInk(id),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(r.list, style = MaterialTheme.typography.labelSmall, color = look.cardMuted(id), maxLines = 1)
                            AddedOnText(r.w, look.cardMuted(id))
                            // The price alert on the card (owner, 2.138; "Price alert" since 7 November 2026): gold when
                            // reached; "Set price alert" without one.
                            val t = Targets.all[r.w.id]
                            if (t != null) Text(
                                "🔔 " + tr("Alert at %s", money(t, r.w.currency)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = if (Targets.reached(r.w)) targetGold(look.dark) else look.cardMuted(id),
                                modifier = Modifier.clickable { targetFor = r.w }.padding(top = 2.dp)
                            ) else Text(
                                "🔔 " + tr("Set price alert"), style = MaterialTheme.typography.labelMedium, color = cs.primary,
                                modifier = Modifier.clickable { targetFor = r.w }.padding(top = 2.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                money(r.w.price ?: 0.0, r.w.currency), style = MaterialTheme.typography.titleMedium,
                                fontWeight = look.priceWeight, fontFamily = look.display, color = look.cardInk(id)
                            )
                            val ch = r.change
                            val pct = if (ch == null) 0L else Math.round(ch)
                            // Both prices: when it was added and now (only when it changed).
                            val first = History.of(r.w.id).firstOrNull()?.p
                            if (first != null && pct != 0L) {
                                Text(
                                    money(first, r.w.currency) + " → " + money(r.w.price ?: 0.0, r.w.currency),
                                    style = MaterialTheme.typography.labelSmall, color = look.cardMuted(id)
                                )
                            }
                            Text(
                                when {
                                    ch == null -> ""
                                    pct < 0 -> tr("−%d%% since added", -pct)
                                    pct > 0 -> tr("+%d%% since added", pct)
                                    else -> tr("No price change yet")
                                },
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = when {
                                    pct < 0 -> look.drop
                                    pct > 0 -> look.rise
                                    else -> look.cardMuted(id)
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }
    open?.let { w -> PriceHistoryDialog(w, showAll = false) { open = null } }
    targetFor?.let { w -> TargetDialog(w) { targetFor = null } }
}

/** "Set price alert" from a card (owner, 2.138): below the price now; "Remove" takes it away. */
@Composable
private fun TargetDialog(w: Wish, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val now = Targets.all[w.id]
    var text by remember { mutableStateOf(now?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "") }
    val v = text.replace(',', '.').toDoubleOrNull()
    val ok = v != null && v > 0 && (w.price == null || v < w.price)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Price alert")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("You get a notification when “%s” costs this or less.", w.title), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    text, { text = it.take(10) }, singleLine = true, prefix = { Text(currencySymbol(w.currency) + " ") },
                    label = { Text(tr("Alert at")) }, isError = text.isNotEmpty() && !ok,
                    supportingText = { Text(tr("Now %s", money(w.price ?: 0.0, w.currency))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { Targets.set(ctx, w, v); onClose() }) { Text(tr("Save")) } },
        dismissButton = {
            if (now != null) TextButton(onClick = { Targets.set(ctx, w, null); onClose() }) { Text(tr("Remove")) }
            else TextButton(onClick = onClose) { Text(tr("Cancel")) }
        }
    )
}
