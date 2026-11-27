package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToLong

/* The Notifications page (moved from Notifications.kt on 20 November 2026 for the 400-line limit; what it shows is Notifications). */

/** The Notifications page: a small picture, the name, "decreased in price" or "increased in price", and the percentage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(snack: SnackbarHostState, onOpen: (String) -> Unit) {
    val go = LocalGo.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val notes = Notifications.shown
    val scope = rememberCoroutineScope()
    var askClear by remember { mutableStateOf(false) }
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(tr("Delete all notifications?")) },
            confirmButton = {
                TextButton(danger = true, onClick = {
                    askClear = false
                    val gone = Notifications.clear(ctx)
                    scope.launch {
                        snack.currentSnackbarData?.dismiss()
                        val r = snack.showToast(tr("All notifications deleted"), tr("Undo"))
                        if (r == SnackbarResult.ActionPerformed) Notifications.putBack(ctx, gone)
                    }
                }) { Text(tr("Yes")) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text(tr("No")) } }
        )
    }
    // Opening the page counts as seeing them: the red dots go.
    LaunchedEffect(Unit) { Notifications.markAllRead(ctx) }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Notifications")) },
                navigationIcon = { MenuSlot() },
                // Top right: delete them all (after a yes).
                actions = {
                    if (notes.isNotEmpty()) {
                        IconButton(onClick = { askClear = true }) { Icon(AppIcons.Delete, tr("Delete all notifications")) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
        if (notes.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    AppIcons.Notifications, tr("Nothing new yet. Price drops, friend requests and invitations show up here."),
                    tr("Back"), { back?.onBackPressedDispatcher?.onBackPressed() }
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.kind + "_" + it.listKey + "_" + it.wishId }) { n ->
                val id = n.wishId
                val down = n.pct < 0
                val target = n.kind == "target"
                val other = n.kind != "price" && !target
                // Swipe left or right to delete it straight away, with Undo for 5 seconds.
                SwipeAway(n, trash = true, onGone = {
                    val at = Notifications.remove(ctx, n)
                    scope.launch {
                        snack.currentSnackbarData?.dismiss()
                        val r = snack.showToast(tr("Notification deleted"), tr("Undo"))
                        if (r == SnackbarResult.ActionPerformed && at >= 0) Notifications.restore(ctx, n, at)
                    }
                }) {
                Row(
                    Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
                        // A reached target opens the wish's price history; a price change opens its list on that wish.
                        .clickable {
                            // Still there? (owner, 20 November 2026: no dead ends.) Gone says so, in the bin offers Restore.
                            when (val where = NoteCheck.where(ctx, n)) {
                                is NoteWhere.Gone -> { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showToast(where.message) }; return@clickable }
                                is NoteWhere.InBin -> {
                                    scope.launch {
                                        snack.currentSnackbarData?.dismiss()
                                        if (snack.showToast(tr("This wish is in the bin"), tr("Restore")) == SnackbarResult.ActionPerformed && Repo.restoreMine(ctx, where.wishId))
                                            snack.showToast(tr("Restored"))
                                    }
                                    return@clickable
                                }
                                is NoteWhere.Moved -> { WishFocus.target = n.wishId; go(Deep.Note("m:" + where.listId)); return@clickable }
                                NoteWhere.Open -> {}
                            }
                            if (target) go(Deep.History(n.wishId))
                            else {
                                // News about a wish you want or reserved (2.142): the list opens on that wish.
                                val ask = if (n.kind in listOf("ask_sent", "ask_yes", "ask_free", "ask_ended") && n.listKey.length > 2 && n.wishId > 0L)
                                    AskFocus(n.listKey.substring(2), n.wishId, n.kind == "ask_free") else null
                                WishFocus.forNote(n.listKey, n.wishId, n.kind)
                                go(Deep.Note(n.listKey, ask))
                            }
                        }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mat = RoundedCornerShape(10.dp)
                    Box(Modifier.size(44.dp).lookMat(look, mat, look.pictureBg(id, false)), contentAlignment = Alignment.Center) {
                        if (n.image.isNotBlank()) {
                            val person = n.kind.startsWith("friend") || n.kind == "birthday"
                            WishImage(
                                n.image, if (person) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(3.dp),
                                if (person) ContentScale.Crop else ContentScale.Fit
                            )
                        } else PlaceholderIcon(
                            when (n.kind) { "friend_req", "friend_ok", "group_declined", "gl_declined" -> "friend"; "birthday" -> "birthday"; else -> "gift" }, 22.dp
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        // Its wish is in the bin or gone: said on the line itself (owner, 20 November 2026).
                        val state = if (n.kind == "price" || target) remember(n, Repo.myBin, Repo.mineVersion) { NoteCheck.where(ctx, n) } else NoteWhere.Open
                        // A reserved gift that is gone (2.126): the same words as its push notification.
                        val lost = n.kind == "gift_lost"
                        Text(
                            if (lost) Alerts.giftLostTitle(n.currency) else n.title,
                            style = MaterialTheme.typography.titleSmall, color = look.cardInk(id),
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when (n.kind) {
                                "gift_lost" -> Alerts.giftLostText(n.title, n.amount > 0.5)
                                // 2.128: the reminder for givers, the thank-you, and "Did your wishes come true?".
                                "reminder", "thanks" -> n.currency
                                "recv_ask" -> tr("Did your wishes come true? ✨") + " " + n.currency
                                "owner" -> tr("You can renew the link and delete the list.")
                                "target" -> tr("reached your price alert")
                                "friend_req" -> tr("wants to be your friend")
                                "friend_ok" -> tr("approved your friend request")
                                "gift_binned" -> tr("Your reserved gift was moved to the bin")
                                "gift_gone" -> tr("Your reserved gift was taken off the list")
                                "gift_owned" -> tr("Your reserved gift is already owned")
                                "birthday" -> if (n.amount.toInt() == 1) tr("Birthday tomorrow") else tr("Birthday in %d days", n.amount.toInt())
                                "group_invite" -> tr("%s invited you to this wishlist group", n.currency)
                                "group_declined" -> tr("said no thanks to “%s”", n.currency)
                                "group_removed" -> tr("You are removed from this wishlist group")
                                // Gift exchange (2.129): the invite says who; the others carry their own line.
                                "gx_invite" -> tr("%s invited you to this gift exchange", n.currency)
                                // 2.142: gift list invitations, "Still buying …?" and reactions.
                                "gl_invite" -> tr("%s invited you to plan gifts together", n.currency)
                                "gl_declined" -> tr("said no thanks to “%s”", n.currency)
                                "ask", "ask_sent", "ask_yes", "ask_free", "ask_ended" -> Asks.noteLine(n.kind, n.currency)
                                "reacts" -> n.currency
                                else -> if (n.kind.startsWith("gx_")) n.currency
                                    else if (down) tr("decreased in price") else tr("increased in price")
                            },
                            style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id)
                        )
                        when (state) {
                            is NoteWhere.InBin -> Text(tr("In the bin"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            is NoteWhere.Gone -> Text(tr("No longer on your list"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            else -> {}
                        }
                    }
                    if (other) {
                        // No number on the right for these.
                    } else if (target) {
                        Text(
                            money(n.amount, n.currency), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = targetGold(look.dark)
                        )
                    } else {
                        val pct = abs(n.pct).roundToLong().coerceAtLeast(1)
                        Text(
                            (if (down) "−" else "+") + pct + "%",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = if (down) look.drop else look.rise
                        )
                    }
                }
                }
            }
        }
    }
}

/** The small red dot for something new. */
@Composable
fun RedDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(NewDot))
}
