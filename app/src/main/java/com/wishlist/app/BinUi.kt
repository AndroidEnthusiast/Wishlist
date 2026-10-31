package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Menu > Bin: the deleted wishes of My wishlist and of every shared wishlist, each with Restore and Delete now.
 * Opened from a "your reserved gift has been moved to the bin" alert, it scrolls to that gift (only when it is out of
 * sight) and lets it glow softly for a few seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // The wish being restored or deleted (its key), so its buttons can't be tapped twice.
    var busy by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf<BinEntry?>(null) }
    var askEmpty by remember { mutableStateOf(false) }
    // The wish that glows, with a counter so the same wish can glow again after a second alert.
    var lit by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(Unit) {
        if (Bin.focus == null) Bin.load(ctx)
    }
    val focus = Bin.focus
    LaunchedEffect(focus) {
        if (focus == null) return@LaunchedEffect
        Bin.load(ctx)
        Bin.focus = null
        val k = focus.first + ":" + focus.second
        val i = Bin.entries.indexOfFirst { it.key == k }
        if (i < 0) {
            snack.showSnackbar(tr("That gift is no longer in the bin."))
            return@LaunchedEffect
        }
        delay(150) // let the list be laid out first
        val row = i + 1 // the explanation at the top is the first row
        val info = listState.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == row }
        if (seen == null || seen.offset < info.viewportStartOffset || seen.offset + seen.size > info.viewportEndOffset) {
            listState.animateScrollToItem(row)
        }
        lit = k to ((lit?.second ?: 0) + 1)
    }

    fun restore(e: BinEntry) {
        busy = e.key
        scope.launch {
            val msg = try {
                Bin.restore(ctx, e)
                tr("“%1\$s” is back on %2\$s.", shortTitle(e.wish.title), e.listName)
            } catch (ex: Exception) {
                tr("Couldn’t save that. Check your connection and try again.")
            }
            busy = null
            snack.showSnackbar(msg)
        }
    }

    fun deleteForGood(e: BinEntry) {
        busy = e.key
        scope.launch {
            try {
                Bin.deleteForGood(ctx, e)
            } catch (ex: Exception) {
                snack.showSnackbar(tr("Couldn’t delete that wish. Check your connection and try again."))
            }
            busy = null
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("Bin"), fontWeight = FontWeight.Medium) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    IconButton(onClick = { scope.launch { Bin.load(ctx) } }, enabled = !Bin.loading) {
                        Icon(Icons.Default.Refresh, tr("Refresh"))
                    }
                    if (Bin.entries.isNotEmpty()) {
                        TextButton(onClick = { askEmpty = true }, enabled = busy == null) { Text(tr("Empty bin")) }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (Bin.entries.isEmpty() && Bin.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "about") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                tr("Deleted wishes stay here for %d days. Then they are deleted for good.", BIN_DAYS),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                            )
                            if (Shared.lists.isNotEmpty()) {
                                Text(
                                    tr("Everyone on a shared wishlist sees its deleted wishes here and can restore them."),
                                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                                )
                            }
                            if (Bin.offline) {
                                Text(
                                    tr("Couldn’t load the bin of every shared wishlist. Check your connection."),
                                    style = MaterialTheme.typography.bodyMedium, color = cs.error
                                )
                            }
                            if (Bin.entries.isEmpty()) {
                                Text(
                                    tr("The bin is empty."),
                                    style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                                )
                            }
                        }
                    }
                    items(Bin.entries, key = { it.key }) { e ->
                        BinRow(
                            e, enabled = busy == null,
                            glow = lit?.takeIf { it.first == e.key }?.second ?: 0,
                            onRestore = { restore(e) },
                            onDelete = { askDelete = e }
                        )
                    }
                }
            }
        }
    }

    askDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { askDelete = null },
            title = { Text(tr("Delete for good?")) },
            text = {
                Text(
                    listOfNotNull(
                        tr("“%s” can’t be brought back after this.", shortTitle(e.wish.title)),
                        if (e.listCode != null) tr("It is gone for everyone on the list.") else null,
                        if (e.reserved != null) tr("Someone reserved this gift: the reservation goes too.") else null
                    ).joinToString(" ")
                )
            },
            confirmButton = {
                TextButton(onClick = { askDelete = null; deleteForGood(e) }) { Text(tr("Delete"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text(tr("Cancel")) } }
        )
    }

    if (askEmpty) {
        val n = Bin.entries.size
        AlertDialog(
            onDismissRequest = { askEmpty = false },
            title = { Text(tr("Empty the bin?")) },
            text = {
                Text(
                    (if (n == 1) tr("1 wish will be deleted for good.") else tr("%d wishes will be deleted for good.", n)) +
                        (if (Bin.entries.any { it.listCode != null }) " " + tr("Wishes from shared wishlists are gone for everyone on them.") else "") +
                        " " + tr("This can’t be undone.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askEmpty = false
                    busy = "all"
                    scope.launch {
                        val ok = Bin.empty(ctx)
                        busy = null
                        if (!ok) snack.showSnackbar(tr("Couldn’t delete everything. Check your connection and try again."))
                    }
                }) { Text(tr("Empty bin"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { askEmpty = false }) { Text(tr("Cancel")) } }
        )
    }
}

/** One wish in the bin. [glow] > 0 lights it up softly (it goes up again for every new alert). */
@Composable
private fun BinRow(e: BinEntry, enabled: Boolean, glow: Int, onRestore: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val w = e.wish
    val shape = RoundedCornerShape(20.dp)
    val g = remember { Animatable(0f) }
    LaunchedEffect(glow) {
        if (glow == 0) return@LaunchedEffect
        // Comes up, breathes once, stays a moment, then fades away.
        g.animateTo(1f, tween(500))
        g.animateTo(0.45f, tween(700))
        g.animateTo(1f, tween(700))
        delay(2500)
        g.animateTo(0f, tween(1500))
    }
    val lit = g.value
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
            .shadow((14 * lit).dp, shape, clip = false, ambientColor = cs.primary, spotColor = cs.primary)
            .then(if (lit > 0.01f) Modifier.border(2.dp, cs.primary.copy(alpha = 0.75f * lit), shape) else Modifier)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (w.image.isEmpty()) cs.primaryContainer else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (w.image.isNotEmpty()) {
                        AsyncImage(
                            model = w.image, contentDescription = w.title, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    } else Text("🎁", fontSize = 28.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        w.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurface
                    )
                    val from = listOfNotNull(e.listName, w.price?.let { money(it, w.currency) }).joinToString(" · ")
                    Text(from, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (w.binBy.isNotBlank()) {
                        Text(tr("Deleted by %s", w.binBy), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                    val d = Bin.daysLeft(w)
                    Text(
                        when (d) {
                            0 -> tr("Deleted for good today")
                            1 -> tr("1 day left")
                            else -> tr("%d days left", d)
                        },
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant
                    )
                    reservedLabel(e.reserved)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = cs.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDelete, enabled = enabled) { Text(tr("Delete now"), color = cs.error) }
                FilledTonalButton(onClick = onRestore, enabled = enabled) { Text(tr("Restore")) }
            }
        }
    }
}

private fun shortTitle(s: String): String = if (s.length <= 40) s else s.take(39).trimEnd() + "…"
