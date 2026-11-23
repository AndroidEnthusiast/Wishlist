package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Reminders around an occasion (owner, 6 November 2026; DECISIONS "Review interview"):
 *  - #1 For givers: 7 days before the occasion of a list you reserved something on, one reminder ("7 days to Sam’s
 *    birthday 🎈 · Got the AirPods yet?"). Only lists with an occasion. A phone that missed the day tells it once in
 *    the next 3 days (with the real number of days); never twice for the same date.
 *  - #6 For you: the day after the occasion of one of your lists, "Did your wishes come true? ✨"; it opens "What did
 *    you get?", where ticked wishes go to "Received gifts" (Repo.receive). Same catch-up: within 3 days.
 * Both run with the background check (BinWorker) and when a friend's list is looked at (FriendWatch.check).
 */
object Reminders {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** The occasion's date when today is 7 to 4 days before it (the reminder day plus 3 catch-up days), else null. */
    fun due(occ: Occasion?, today: LocalDate = LocalDate.now()): LocalDate? {
        val d = occ?.let { Occasions.next(it, today) } ?: return null
        val days = ChronoUnit.DAYS.between(today, d)
        return if (days in 4L..7L) d else null
    }

    /** "Sam’s birthday" (a friend's list with their name), else the occasion's name, or the gift list’s name. */
    private fun what(occ: Occasion, owner: String, listName: String, gift: Boolean): String = when {
        gift -> "“$listName”"
        owner.isNotBlank() && occ.kind == "birthday" -> tr("%s’s birthday", owner)
        owner.isNotBlank() && occ.kind == "anniversary" -> tr("%s’s anniversary", owner)
        else -> Occasions.label(occ)
    }

    /** #1: tells a giver once about the gifts [titles] they reserved on list [code], when its occasion is near. */
    fun giver(c: Context, code: String, gift: Boolean, occ: Occasion?, owner: String, listName: String, titles: List<String>) {
        if (occ == null || titles.isEmpty()) return
        val d = due(occ) ?: return
        val key = "rem_${code}_$d"
        val p = prefs(c)
        if (p.getBoolean(key, false)) return
        p.edit().putBoolean(key, true).apply()
        val days = ChronoUnit.DAYS.between(LocalDate.now(), d).toInt()
        Alerts.notifyReminder(c, code, gift, days, what(occ, owner, listName, gift), titles)
    }

    /** The date of [occ] when it was 1 to 3 days ago, else null. */
    fun justPassed(occ: Occasion?, today: LocalDate = LocalDate.now()): LocalDate? {
        if (occ == null) return null
        for (k in 1L..3L) {
            val day = today.minusDays(k)
            if (Occasions.next(occ, day) == day) return day
        }
        return null
    }

    /** #6: "Did your wishes come true?" once, the day after the occasion of each of your lists that has wishes. */
    fun checkMine(c: Context) {
        val p = prefs(c)
        for (id in Repo.mineIds) {
            val day = justPassed(Occasions.ofMine(c, id)) ?: continue
            val key = "recv_ask_${id}_$day"
            if (p.getBoolean(key, false)) continue
            if (Repo.mine(c, id).wishes.none { !it.priv }) continue
            p.edit().putBoolean(key, true).apply()
            Alerts.notifyReceivedAsk(c, id, Repo.mineName(id))
        }
    }
}

/** "What did you get?" (owner, 2.128): the wishes of your list [listId], tick what you received, then Done. */
@Composable
fun ReceivedDialog(listId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val wishes = remember(listId) { if (listId in Repo.mineIds) Repo.mine(ctx, listId).wishes else emptyList() }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    // The ticked wishes go straight to Received gifts, as "I have it" does (owner, 20 November 2026: no question).
    fun receiveTicked() {
        val (withCopies, single) = picked.partition { Repo.hasCopies(it) }
        withCopies.forEach { Repo.receiveEverywhere(ctx, it) }
        Repo.receive(ctx, listId, single.toSet())
        onClose()
    }
    // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt): tick, then Done.
    PickSheet(
        tr("What did you get?"), onClose = onClose,
        note = tr("Tick the wishes you have. They move to Received gifts, and whoever gave them gets a thank-you."),
        action = tr("Done"), onAction = { if (picked.isEmpty()) onClose() else receiveTicked() }
    ) {
        if (wishes.isEmpty()) PickNote(tr("This wishlist has no wishes."))
        else SheetGroup(wishes.map { w ->
            {
                SheetRow(
                    { PickWishPhoto(w) }, w.label, { picked = if (w.id in picked) picked - w.id else picked + w.id },
                    chosen = w.id in picked, tile = false
                )
            }
        })
    }
}

/** "Received gifts" (owner, 2.128): every wish you ticked in "What did you get?", newest first; "Put back" returns one. */
@Composable
fun ReceivedList(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val items = Repo.myBin.filter { it.recv > 0L }.sortedByDescending { it.recv }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
        if (items.isEmpty()) EmptyState(
            AppIcons.Gift, tr("No received gifts yet. After a wishlist’s occasion you can mark the wishes you got, and they show up here."),
            tr("Back"), { back?.onBackPressedDispatcher?.onBackPressed() }
        )
        items.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val d = java.time.Instant.ofEpochMilli(w.recv).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    Text(
                        Repo.binListName(w.id) + " · " + d.dayOfMonth + " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK) + " " + d.year,
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                TextButton(onClick = { Repo.unreceive(ctx, w.id) }) { Text(tr("Put back")) }
            }
        }
    }
}

/** The "Received gifts" page (profile sheet): a route (Nav.kt, 2.156), ← or Back returns to where it was opened. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun ReceivedScreen(onClose: () -> Unit) {
    // "Mark as received" at any time (owner, 2.129), not only after an occasion: pick a list, then tick.
    var pickList by remember { mutableStateOf(false) }
    var askList by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    // The app's own frame and top bar (owner, 22 November 2026): the big title that shrinks as you scroll, like every
    // screen; it had its own small header with Android's own back button before.
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Received gifts")) },
                navigationIcon = { IconButton(onClick = onClose) { androidx.compose.material3.Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
                TextButton(
                    onClick = { if (Repo.mineIds.size == 1) askList = Repo.mineIds.first() else pickList = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    androidx.compose.material3.Icon(AppIcons.Add, null)
                    Text(tr("Mark as received"), modifier = Modifier.padding(start = 6.dp))
                }
                ReceivedList()
            }
    }
        if (pickList) PickSheet(tr("Which wishlist?"), onClose = { pickList = false }) {
            SheetGroup(Repo.mineIds.map { id ->
                { SheetRow({ androidx.compose.material3.Icon(listKindIcon(false), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, Repo.mineName(id), { pickList = false; askList = id }) }
            })
        }
        askList?.let { id -> ReceivedDialog(id) { askList = null } }
}
