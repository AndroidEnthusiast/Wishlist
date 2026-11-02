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
import androidx.compose.runtime.getValue
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
 * Prices (the bottom bar): every wish with a price on your own wishlists, its price now and how much higher or lower
 * that is than when you added it (the first point of its price history). The biggest drops come first. Tapping a
 * wish shows its price history graph. Group wishlists keep no price history, so they are not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(snack: SnackbarHostState, onProfile: () -> Unit = {}) {
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

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Price watch"), fontWeight = look.titleWeight) },
                navigationIcon = { MenuSlot() },
                actions = { ProfileButton(onProfile) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        if (rows.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr("No prices yet. Wishes with a price show up here, with how much they changed since you added them."),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
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
                                r.w.title, style = MaterialTheme.typography.titleSmall, color = look.cardInk(id),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(r.list, style = MaterialTheme.typography.labelSmall, color = look.cardMuted(id), maxLines = 1)
                            AddedOnText(r.w, look.cardMuted(id))
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
    open?.let { w -> PriceHistoryDialog(w) { open = null } }
}
