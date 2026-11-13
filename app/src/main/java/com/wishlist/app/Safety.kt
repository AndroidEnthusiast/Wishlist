package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Block and report (ROADMAP S4.8; owner, 12 November 2026; supabase-privacy.sql). Both are in a chat's menu and in a
 * friend's menu (next to Unfriend), nowhere else.
 *  - Block: no longer friends, no friend requests from them, the chat hidden for you; they aren't told. Profile >
 *    Blocked people undoes it (entering their friend code again does too).
 *  - Report: a reason, a note of at least 5 characters (spaces don't count), and the last 20 messages of your chat,
 *    unlocked on this phone (the pop-up says so). The owner reads reports in Supabase.
 */
object Safety {
    data class Blocked(val id: String, val name: String)

    var blocked by mutableStateOf<List<Blocked>>(emptyList())

    /** A note counts from 5 characters, spaces and line breaks not counted (owner, 12 November 2026). */
    fun noteOk(note: String): Boolean = note.count { !it.isWhitespace() } >= 5 && note.length <= 2000

    suspend fun block(c: Context, id: String) {
        val a = Friends.auth(c) ?: throw CloudException("no profile")
        Cloud.rpc("wl_block", a.put("p_other", id))
        Friends.refresh(c)
        try { Chat.refresh(c) } catch (e: Exception) {}
        loadBlocked(c)
    }

    suspend fun unblock(c: Context, id: String) {
        val a = Friends.auth(c) ?: throw CloudException("no profile")
        Cloud.rpc("wl_unblock", a.put("p_other", id))
        loadBlocked(c)
    }

    suspend fun loadBlocked(c: Context) {
        val a = Friends.auth(c) ?: return
        val arr = try { JSONArray(Cloud.rpc("wl_blocked_list", a)) } catch (e: Exception) { return }
        blocked = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { Blocked(it.optString("id"), it.optString("name")) } }
    }

    /** The last 20 messages of the chat with [id], as this phone reads them (empty when there is no chat). */
    suspend fun lastMessages(c: Context, id: String): JSONArray {
        val a = JSONArray()
        val msgs = try { Chat.messages(c, id, 0L) } catch (e: Exception) { emptyList() }
        for (m in msgs.takeLast(20)) a.put(
            JSONObject().put("mine", m.mine).put("at", m.at).put("text", Chat.preview(m.content).take(1500))
        )
        return a
    }

    suspend fun report(c: Context, id: String, reason: String, note: String) {
        val a = Friends.auth(c) ?: throw CloudException("no profile")
        Cloud.rpc(
            "wl_report",
            a.put("p_other", id).put("p_reason", reason).put("p_note", note.trim()).put("p_messages", lastMessages(c, id))
        )
    }
}

/** "Block Ben?" (Claude's wording, from the owner's decisions of 12 November 2026). */
@Composable
fun BlockDialog(id: String, name: String, snack: SnackbarHostState, onDone: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Block %s?", name)) },
        text = { Text(tr("You’re no longer friends, they can’t send you a friend request, and your chat is hidden. They aren’t told.")) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        Safety.block(ctx, id)
                        onDone()
                        snack.toastSoon(tr("%s is blocked", name))
                    } catch (e: Exception) {
                        busy = false
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Block"), color = cs.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** "Report Ben": a reason, a required note, and the last 20 messages go along (the pop-up says so). */
@Composable
fun ReportDialog(id: String, name: String, snack: SnackbarHostState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val reasons = listOf("spam" to tr("Spam"), "hurtful" to tr("Hurtful or threatening"), "other" to tr("Something else"))
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Report %s", name)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("The last 20 messages of your chat are sent with your report."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
                reasons.forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy) { reason = k },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == k, onClick = { reason = k }, enabled = !busy)
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(
                    note, { note = it.take(2000); err = "" }, label = { Text(tr("What happened?")) }, enabled = !busy,
                    supportingText = { Text(tr("At least 5 characters")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) Text(err, color = cs.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && reason.isNotEmpty() && Safety.noteOk(note), onClick = {
                busy = true
                scope.launch {
                    try {
                        Safety.report(ctx, id, reason, note)
                        onClose()
                        snack.toastSoon(tr("Thanks. Your report was sent."))
                    } catch (e: Exception) {
                        busy = false
                        err = tr("Couldn’t send. Check your connection.")
                    }
                }
            }) { Text(if (busy) tr("Sending…") else tr("Send report")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Profile > Blocked people (only when there are any), with Unblock. */
@Composable
fun BlockedPeople(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { Safety.loadBlocked(ctx) }
    if (Safety.blocked.isEmpty()) return
    ProfileTitle(tr("Blocked people"))
    Safety.blocked.forEach { b ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(b.name.ifBlank { tr("Someone") }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    try {
                        Safety.unblock(ctx, b.id)
                        snack.toastSoon(tr("%s is unblocked", b.name.ifBlank { tr("Someone") }))
                    } catch (e: Exception) {
                        snack.toastSoon(tr("Couldn’t do that. Check your connection."))
                    }
                }
            }) { Text(tr("Unblock")) }
        }
    }
}
