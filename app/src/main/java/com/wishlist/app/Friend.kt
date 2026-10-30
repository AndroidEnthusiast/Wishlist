package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Someone else's list: you can see it and reserve items. The owner never sees reservations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(f: Friend, snack: SnackbarHostState, onMenu: () -> Unit, onRemoved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var remote by remember(f.code) { mutableStateOf<RemoteList?>(null) }
    var res by remember(f.code) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var state by remember(f.code) { mutableStateOf("loading") }
    var busy by remember(f.code) { mutableStateOf<Long?>(null) }
    var cat by remember(f.code) { mutableStateOf("All") }
    var tick by remember { mutableStateOf(0) }
    var confirmRemove by remember { mutableStateOf(false) }

    // Load now, then refresh every 20 seconds while this screen is open.
    LaunchedEffect(f.code, tick) {
        while (true) {
            try {
                val r = Cloud.fetchList(f.code)
                if (r == null) {
                    state = "gone"
                } else {
                    val m = Cloud.fetchReservations(f.code, Repo.deviceId)
                    remote = r
                    res = m
                    state = "ok"
                    if (r.name != f.name) Repo.renameFriend(ctx, f.code, r.name)
                }
            } catch (e: Exception) {
                if (remote == null) state = "offline"
            }
            delay(20000)
        }
    }

    fun toggle(w: Wish, reserve: Boolean) {
        scope.launch {
            busy = w.id
            var msg: String? = null
            try {
                if (reserve) {
                    if (!Cloud.reserve(f.code, w.id, Repo.deviceId)) msg = tr("Someone else just reserved that one.")
                } else {
                    Cloud.unreserve(f.code, w.id, Repo.deviceId)
                }
                res = Cloud.fetchReservations(f.code, Repo.deviceId)
            } catch (e: Exception) {
                msg = tr("No connection. Try again.")
            }
            busy = null
            msg?.let { snack.showSnackbar(it) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(f.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { tick++ }) { Icon(Icons.Default.Refresh, tr("Refresh")) }
                    IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Default.Delete, tr("Remove this list")) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        val r0 = remote
        Column(Modifier.padding(pad)) {
            when {
                state == "gone" -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        tr("This list isn’t shared any more."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                r0 != null -> {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("All") + r0.cats) { c ->
                            FilterChip(selected = c == cat, onClick = { cat = c }, label = { Text(catLabel(c)) })
                        }
                    }
                    val shown = r0.wishes.filter { cat == "All" || it.category == cat }
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                            Text(tr("Nothing here yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(300.dp),
                            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            gridItems(shown, key = { it.id }) { w ->
                                val r = res[w.id]
                                WishCard(
                                    w,
                                    badge = when {
                                        w.got -> tr("Got it")
                                        r == true -> tr("Reserved by you")
                                        r == false -> tr("Reserved")
                                        else -> null
                                    },
                                    dim = w.got || r == false
                                ) {
                                    if (!w.got) {
                                        if (r == null) {
                                            Button(onClick = { toggle(w, true) }, enabled = busy == null) {
                                                Text(tr("Reserve"))
                                            }
                                        } else if (r == true) {
                                            OutlinedButton(onClick = { toggle(w, false) }, enabled = busy == null) {
                                                Text(tr("Undo"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                state == "offline" -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("Couldn’t load the list. Check your connection."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { tick++ }) { Text(tr("Try again")) }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(tr("Remove this list?")) },
            text = { Text(tr("It disappears from your menu. Your reservations stay reserved. You can add it again with its code.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    Repo.removeFriend(ctx, f.code)
                    onRemoved()
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("Cancel")) } }
        )
    }
}
