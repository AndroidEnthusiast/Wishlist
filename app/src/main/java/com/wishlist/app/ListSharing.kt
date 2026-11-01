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

    /** Switches the link of the open list on or off, online first. */
    suspend fun set(c: Context, id: String, on: Boolean) {
        val code = Repo.shareCode ?: return
        val token = Repo.ownerToken ?: return
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
private enum class Who { ME, FRIENDS, LINK }

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
    val now = when {
        code == null -> Who.ME
        id in ListLink.off -> Who.FRIENDS
        else -> Who.LINK
    }
    var busy by remember { mutableStateOf(false) }
    var askPrivate by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    // A list shared with a link before 2.95 wasn't visible to friends: the link choice includes friends, so it is now.
    LaunchedEffect(id, code) {
        if (code != null && !Friends.isVisible(id) && Config.enabled) {
            try { Friends.setVisible(ctx, id, true) } catch (e: Exception) {}
        }
    }

    fun choose(w: Who) {
        if (w == now || busy) return
        if (w == Who.ME) { askPrivate = true; return }
        busy = true
        scope.launch {
            val msg = try {
                if (Repo.shareCode == null) Sync.publish(ctx)
                ListLink.set(ctx, id, w == Who.LINK)
                Friends.setVisible(ctx, id, true)
                if (w == Who.LINK) tr("Friends and anyone with the link can see this wishlist")
                else tr("Only your friends can see this wishlist")
            } catch (e: Exception) {
                tr("Couldn’t reach the server. Check your connection.")
            }
            busy = false
            tick++
            snack.showToast(msg)
        }
    }

    Text(
        tr("Whoever can see it can reserve gifts. You never see what they reserve. Private wishes always stay hidden."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
    if (!Config.enabled) {
        Text(tr("Online sharing isn’t set up in this build yet."), color = cs.onSurfaceVariant)
        return
    }
    Choice(tr("Only me"), tr("Nobody else sees this wishlist."), now == Who.ME, !busy) { choose(Who.ME) }
    Choice(tr("My friends"), tr("Friends you added in the app."), now == Who.FRIENDS, !busy) { choose(Who.FRIENDS) }
    Choice(
        tr("Friends + anyone with the link"), tr("Also people without the app, in their browser."),
        now == Who.LINK, !busy
    ) { choose(Who.LINK) }

    if (now == Who.LINK && code != null) {
        Text(shareLink(code), color = cs.onSurface, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startShare(ctx, scope, snack) }) { Text(tr("Share link")) }
            OutlinedButton(onClick = {
                clip.setText(AnnotatedString(shareLink(code)))
                scope.launch { snack.showToast(tr("Link copied")) }
            }) { Text(tr("Copy link")) }
        }
        Text(
            tr("Don’t open your own link in a browser, or you’ll see what’s reserved."),
            color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
    }
    if (now != Who.ME && Sync.photoStatus.isNotEmpty()) {
        Text(Sync.photoStatus, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    if (askPrivate) {
        AlertDialog(
            onDismissRequest = { askPrivate = false },
            title = { Text(tr("Only you?")) },
            text = { Text(tr("Friends can no longer see this wishlist, its link stops working and all reservations on it are deleted. Sharing it again later makes a new link.")) },
            confirmButton = {
                TextButton(onClick = {
                    askPrivate = false
                    busy = true
                    scope.launch {
                        val msg = try {
                            if (Friends.isVisible(id)) Friends.setVisible(ctx, id, false)
                            Sync.unpublish(ctx)
                            Friends.forget(ctx, id)
                            ListLink.forget(ctx, id)
                            tr("Only you can see this wishlist now")
                        } catch (e: Exception) {
                            tr("Couldn’t reach the server. Check your connection.")
                        }
                        busy = false
                        snack.showToast(msg)
                    }
                }) { Text(tr("Only me")) }
            },
            dismissButton = { TextButton(onClick = { askPrivate = false }) { Text(tr("Cancel")) } }
        )
    }
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
