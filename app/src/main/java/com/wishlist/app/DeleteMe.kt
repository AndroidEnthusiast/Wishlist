package com.wishlist.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Delete my profile and data" (ROADMAP S4.7; owner, 12 November 2026; supabase-privacy.sql). Profile, at the very
 * bottom, in red. First what other people are on is handed over, with the same "pick who takes over" step as a gift
 * exchange's hand-over: collab wishlists made here, groups and gift exchanges you organise. Only what nobody else is on
 * is deleted; a gift exchange where nobody else has the app (nobody can organise it) is cancelled. Then the warning
 * ("Delete your profile and data?", with "Save a copy first"), then everything goes: online (own lists and their
 * pictures, the profile photo, the recovery copy, wl_delete_me for the rest) and on this phone (Moved.startFresh).
 */
object DeleteMe {
    /** Something other people are on: [people] who can take over (empty: it is deleted or, an exchange, cancelled). */
    data class Item(val kind: String, val id: String, val name: String, val people: List<Pair<String, String>>)

    /** What needs a decision before deleting: collab wishlists made here, groups and exchanges you organise. */
    suspend fun plan(c: Context): List<Item> {
        val out = ArrayList<Item>()
        val me = Friends.auth(c)?.optString("p_id").orEmpty()
        for (l in Shared.lists) if (l.owner.isNotEmpty()) {
            val people = if (me.isEmpty()) emptyList() else Members.list(c, l).map { it.id to it.name }
            if (people.isNotEmpty()) out += Item("collab", l.code, l.name, people)
        }
        if (me.isNotEmpty()) {
            Groups.refresh(c)
            for (g in Groups.groups) if (g.organiser == me) {
                val people = g.members.filter { it.id != me }.map { it.id to it.name }
                if (people.isNotEmpty()) out += Item("group", g.id, g.name, people)
            }
            Exchanges.refresh(c)
            for (x in Exchanges.list) if (x.organiser == me) {
                val others = x.members.filter { it.id != me }
                if (others.isEmpty()) continue
                // Only people with the app can organise; nobody: it is cancelled (owner, 12 November 2026).
                out += Item("exchange", x.id, x.name, others.filter { !it.web }.map { it.id to it.name })
            }
        }
        return out
    }

    /** Hands over what was picked ([next]: item id to person), then deletes everything online and on this phone. */
    suspend fun run(c: Context, items: List<Item>, next: Map<String, String>) {
        val auth = Friends.auth(c)
        // 1. Hand-overs (the same server calls as the screens use).
        for (it in items) {
            val to = next[it.id] ?: continue
            when (it.kind) {
                "collab" -> Shared.lists.firstOrNull { l -> l.code == it.id }?.let { l -> Members.handOver(c, l, to) }
                "group" -> Groups.leave(c, it.id, to)
                "exchange" -> Exchanges.leave(c, it.id, to)
            }
        }
        // 2. Collab wishlists made here that nobody else is on: deleted for good.
        val handed = items.filter { it.kind == "collab" }.map { it.id }.toSet()
        for (l in Shared.lists) if (l.owner.isNotEmpty() && l.code !in handed) Cloud.deleteList(l.code, l.owner)
        // 3. Your own lists online, with their pictures.
        for (d in Repo.allMine(c)) if (d.code != null) Sync.unpublishStored(d)
        // 4. Your profile photo (a file), and the recovery copy.
        if (auth != null) try {
            PeoplePhotos.save("p/" + auth.optString("p_id"), auth, "wl_pf_photo_url", JSONObject(auth.toString()), null)
        } catch (e: Exception) {
        }
        try {
            val d = RecoveryKey.derive(RecoveryKey.key(c))
            Cloud.rpc("wl_vault_delete", JSONObject().put("p_id", d.id).put("p_proof", d.proof))
        } catch (e: Exception) {
            if (!MineSync.missingFunction(e)) throw e
        }
        // 5. Everything else in one go: the profile and what hangs on it, reservations, "Added by" your name.
        val names = JSONArray()
        for (l in Shared.lists) for (n in setOf(l.me, Shared.lastName)) if (n.isNotBlank()) names.put(JSONObject().put("k", l.key).put("n", n))
        Cloud.rpc(
            "wl_delete_me",
            JSONObject().put("p_id", auth?.optString("p_id").orEmpty()).put("p_token", auth?.optString("p_token").orEmpty())
                .put("p_devices", JSONArray().put(Repo.deviceId)).put("p_names", names)
        )
        // 6. This phone: empty, and the welcome again.
        Moved.startFresh(c)
    }
}

/** The red line at the bottom of Profile and everything it opens. */
@Composable
fun DeleteMeSection() {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.padding(top = 24.dp)) {
        Text(tr("Delete my profile and data"), color = cs.error)
    }
    if (open) DeleteMeFlow { open = false }
}

@Composable
private fun DeleteMeFlow(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var items by remember { mutableStateOf<List<DeleteMe.Item>?>(null) }
    var next by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var picking by remember { mutableStateOf<DeleteMe.Item?>(null) }
    var step by remember { mutableStateOf(0) } // 0 loading, 1 hand-overs, 2 warning
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            err = try { Backup.export(ctx, uri); "" } catch (e: Exception) { tr("Couldn’t save the copy.") }
        }
    }
    LaunchedEffect(Unit) {
        try {
            val p = DeleteMe.plan(ctx)
            items = p
            step = if (p.isEmpty()) 2 else 1
        } catch (e: Exception) {
            err = tr("No internet. Connect and try again.")
            step = 1
            items = emptyList()
        }
    }
    fun kindLabel(k: String) = when (k) { "collab" -> tr("Collab wishlist"); "group" -> tr("Wishlist group"); else -> tr("Gift exchange") }

    if (step == 0) AlertDialog(
        onDismissRequest = onClose, title = { Text(tr("Delete my profile and data")) },
        text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
    if (step == 1) {
        val list = items.orEmpty()
        val ready = err.isEmpty() && list.all { it.people.isEmpty() || next[it.id] != null }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(tr("Before you go")) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (err.isNotEmpty()) Text(err, color = cs.error)
                    else Text(tr("Other people are on these. Pick who takes over."), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    list.forEach { it ->
                        Column {
                            Text(it.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(kindLabel(it.kind), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            if (it.people.isEmpty()) Text(
                                tr("%s is cancelled: nobody else has the app to organise it.", it.name),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                            ) else {
                                val who = next[it.id]?.let { id -> it.people.firstOrNull { p -> p.first == id }?.second }
                                OutlinedButton(onClick = { picking = it }) {
                                    Text(if (who == null) tr("Pick who takes over") else tr("Taken over by %s", who.ifBlank { tr("Someone") }), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = ready, onClick = { step = 2 }) { Text(tr("Next")) } },
            dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
        )
    }
    if (step == 2) AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Delete your profile and data?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Your profile, friends, chats, reservations and wishlists are deleted, on this phone and online. This can’t be undone."))
                OutlinedButton(enabled = !busy, onClick = { export.launch(Backup.fileName()) }) { Text(tr("Save a copy first")) }
                if (err.isNotEmpty()) Text(err, color = cs.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true; err = ""
                scope.launch {
                    try {
                        DeleteMe.run(ctx, items.orEmpty(), next)
                    } catch (e: Exception) {
                        busy = false
                        err = tr("Couldn’t delete everything. Check your connection and try again.")
                    }
                }
            }) { Text(if (busy) tr("Deleting…") else tr("Delete everything"), color = cs.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
    picking?.let { item ->
        var chosen by remember(item.id) { mutableStateOf(next[item.id]) }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(tr("Who takes over %s?", item.name)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    item.people.forEach { (id, name) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { chosen = id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = chosen == id, onClick = { chosen = id })
                            Text(name.ifBlank { tr("Someone") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chosen != null, onClick = {
                    chosen?.let { next = next + (item.id to it) }
                    picking = null
                }) { Text(tr("Choose")) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(tr("Cancel")) } }
        )
    }
}

/** Opens the privacy page in the browser (App settings > About, the welcome). */
fun openPrivacy(c: Context) {
    try {
        c.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Config.PRIVACY_URL))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}
