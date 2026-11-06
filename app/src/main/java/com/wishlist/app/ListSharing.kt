package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/*
 * "Who can see this list" (2.95, owner): one choice per list of your own, instead of a separate friends' link and a
 * "Visible to friends" switch that quietly switched each other on.
 *  - Only me: nothing online (the list is taken offline; its reservations go).
 *  - My friends: friends in the app see it and reserve; its friends' link doesn't open it (wl_lists.link_off).
 *  - Friends + anyone with the link: as above, and the link works too (for people without the app).
 */

object ListLink {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Ids of your lists whose friends' link is switched off. */
    var off by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        off = prefs(c).getStringSet("link_off", null)?.toSet() ?: emptySet()
    }

    /** Switches the friends' link of your list [id] (open or not) on or off, online first. */
    suspend fun set(c: Context, id: String, on: Boolean) {
        val d = Repo.mine(c, id)
        val code = d.code ?: return
        val token = d.token ?: return
        Cloud.rpc("wl_set_link", JSONObject().put("p_code", code).put("p_token", token).put("p_on", on))
        off = if (on) off - id else off + id
        prefs(c).edit().putStringSet("link_off", off).apply()
    }

    /** The list went offline or was deleted: next time it starts with its link on again. */
    fun forget(c: Context, id: String) {
        load(c)
        if (id !in off) return
        off = off - id
        prefs(c).edit().putStringSet("link_off", off).apply()
    }
}

/** The three choices. */
enum class Who { ME, FRIENDS, LINK }

/*
 * Who can see a list, for any of your own lists, open or not (2.105: also from the long-press menu on Home, "Visible
 * to"). New lists start on "My friends" (owner, 3 November 2026); lists that were "Only me" before 2.105 were switched
 * once, silently (owner). Switching needs the server: a list that couldn't be switched yet stays in [pending] and is
 * tried again at the next start.
 */
object ListVisibility {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Goes up on every change, so screens show the new choice. */
    var version by mutableIntStateOf(0)
        private set

    fun of(c: Context, id: String): Who {
        @Suppress("UNUSED_VARIABLE") val v = version
        ListLink.load(c)
        val d = Repo.mine(c, id)
        return when {
            d.code == null -> Who.ME
            // Online only for wishlist groups (2.108): friends don't see it and its link is off.
            id in ListLink.off && !Friends.isVisible(id) -> Who.ME
            id in ListLink.off -> Who.FRIENDS
            else -> Who.LINK
        }
    }

    /** Sets [w] for your list [id]. Returns the message to show. Throws when the server can't be reached. */
    suspend fun set(c: Context, id: String, w: Who): String {
        dropPending(c, id)
        if (w == Who.ME) {
            if (Friends.isVisible(id)) Friends.setVisible(c, id, false)
            // A list brought to a wishlist group stays online for the group (owner, 2.108): only friends and its link
            // lose it.
            if (Groups.groupsOf(c, id).isNotEmpty()) {
                ListLink.set(c, id, false)
                version++
                return tr("Only you and your wishlist groups can see this wishlist now")
            }
            Sync.unpublishFor(c, id)
            Friends.forget(c, id)
            ListLink.forget(c, id)
            version++
            return tr("Only you can see this wishlist now")
        }
        Sync.publishFor(c, id)
        ListLink.set(c, id, w == Who.LINK)
        Friends.setVisible(c, id, true)
        version++
        return if (w == Who.LINK) tr("Friends and anyone with the link can see this wishlist")
        else tr("Only your friends can see this wishlist")
    }

    /** A new list: "My friends" (now, or at a later start when offline). */
    fun startShared(c: Context, id: String) {
        if (!Config.enabled) return
        val p = prefs(c)
        p.edit().putStringSet("vis_pending", (p.getStringSet("vis_pending", null).orEmpty() + id).toSet()).apply()
        Sync.later { trySwitch(c) }
    }

    private fun dropPending(c: Context, id: String) {
        val p = prefs(c)
        val now = p.getStringSet("vis_pending", null) ?: return
        if (id in now) p.edit().putStringSet("vis_pending", (now - id).toSet()).apply()
    }

    /**
     * At every start: once (2.105) every "Only me" list is marked to become "My friends"; then each marked list is
     * switched. Lists you set to "Only me" yourself after that stay so.
     */
    suspend fun trySwitch(c: Context) {
        if (!Config.enabled) return
        val p = prefs(c)
        if (!p.getBoolean("vis_mig_2105", false)) {
            val me = Repo.allMine(c).filter { it.code == null }.map { it.id }.toSet()
            p.edit().putStringSet("vis_pending", (p.getStringSet("vis_pending", null).orEmpty() + me).toSet())
                .putBoolean("vis_mig_2105", true).apply()
        }
        val todo = p.getStringSet("vis_pending", null)?.toSet().orEmpty()
        for (id in todo) {
            if (id !in Repo.mineIds) { dropPending(c, id); continue }
            if (of(c, id) != Who.ME) { dropPending(c, id); continue }
            try {
                set(c, id, Who.FRIENDS)
            } catch (e: Exception) {
                return // offline: next start
            }
        }
    }
}

/** List settings > "Who can see it", for a list of your own (the open one). */
@Composable
fun WhoCanSeeSection(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    ListLink.load(ctx)
    Friends.load(ctx)
    val id = Repo.openMine
    val code = Repo.shareCode

    // A list shared with a link before 2.95 wasn't visible to friends: the link choice includes friends, so it is now.
    LaunchedEffect(id, code) {
        // (Not a list that is online only for wishlist groups: its link is off, 2.108.)
        if (code != null && !Friends.isVisible(id) && id !in ListLink.off && Config.enabled) {
            try { Friends.setVisible(ctx, id, true) } catch (e: Exception) {}
        }
    }

    Text(
        tr("They can reserve gifts; you never see what. Private wishes stay hidden."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
    if (!Config.enabled) {
        Text(tr("Online sharing isn’t set up in this build yet."), color = cs.onSurfaceVariant)
        return
    }
    val now = WhoChoices(id, snack)

    if (now == Who.LINK && code != null) {
        Text(shareLink(code), color = cs.onSurface, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { NameGate.ensureListName { startShare(ctx, scope, snack) } }) { Text(tr("Share link")) }
            OutlinedButton(onClick = {
                clip.setText(AnnotatedString(shareLink(code)))
                scope.launch { snack.showToast(tr("Link copied")) }
            }) { Text(tr("Copy link")) }
        }
        Text(
            tr("Don’t open your own link: you’d see what’s reserved."),
            color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
    }
    if (now != Who.ME && Sync.photoStatus.isNotEmpty()) {
        Text(Sync.photoStatus, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The three radio choices for your list [id] (list settings, and "Visible to" in the long-press menu on Home), with
 * the "Only you?" question. Returns the current choice.
 */
@Composable
fun WhoChoices(id: String, snack: SnackbarHostState): Who {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val now = remember(id, tick, ListVisibility.version, Repo.mineVersion, Repo.shareCode, ListLink.off) { ListVisibility.of(ctx, id) }
    var busy by remember { mutableStateOf(false) }
    var askPrivate by remember { mutableStateOf(false) }

    fun go(w: Who) {
        busy = true
        scope.launch {
            val msg = try {
                ListVisibility.set(ctx, id, w)
            } catch (e: Exception) {
                tr("Couldn’t reach the server. Check your connection.")
            }
            busy = false
            tick++
            snack.showToast(msg)
        }
    }
    fun choose(w: Who) {
        if (w == now || busy) return
        if (w == Who.ME) askPrivate = true else go(w)
    }

    val inGroups = remember(id, Groups.groups, Repo.mineVersion) { Groups.groupsOf(ctx, id) }
    Choice(
        tr("Only me"),
        if (inGroups.isEmpty()) tr("Nobody else sees this wishlist.") else tr("Only your wishlist groups see it."),
        now == Who.ME, !busy
    ) { choose(Who.ME) }
    Choice(tr("My friends"), tr("Friends you added in the app."), now == Who.FRIENDS, !busy) { choose(Who.FRIENDS) }
    Choice(
        tr("Friends + anyone with the link"), tr("Also people without the app, in their browser."),
        now == Who.LINK, !busy
    ) { choose(Who.LINK) }

    if (askPrivate) {
        AlertDialog(
            onDismissRequest = { askPrivate = false },
            title = { Text(tr("Only you?")) },
            text = {
                Text(
                    if (inGroups.isEmpty()) tr("Friends will lose access, the link will stop working and reservations will be deleted.")
                    else tr("Friends will lose access and the link will stop working. Your groups still see it; reservations stay.")
                )
            },
            confirmButton = {
                TextButton(onClick = { askPrivate = false; go(Who.ME) }) { Text(tr("Only me")) }
            },
            dismissButton = { TextButton(onClick = { askPrivate = false }) { Text(tr("Cancel")) } }
        )
    }
    return now
}

@Composable
private fun Choice(title: String, detail: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = on, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
