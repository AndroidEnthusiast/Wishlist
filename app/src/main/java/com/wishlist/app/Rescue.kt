package com.wishlist.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * When the phone's database can't be opened, or its file was lost (ROADMAP R1; owner, 10 November 2026: a stop screen
 * with ways out). Before 2.178 the app then silently showed, and sent online, the copy of your lists that was frozen in
 * 2.171. Now [RescueScreen] says what happened and nothing is sent ([ListStore.stale]) until one of three things:
 *
 * - **Try again** (only when the database couldn't be read): the app starts again; a hiccup is over then.
 * - **Get my lists back**: every list of yours that is online is fetched with its owner key ([Cloud.mineGet], also
 *   lists whose link is off) and takes the place of the old copy; see [merge] for what the phone's copy still adds.
 *   Lists that never went online, the bin and the price history stay as the old copy has them. A list made after the
 *   old copy isn't known to this phone any more: it can't be brought back this way (a backup file can).
 * - **Import a backup file**: the usual Import (Backup.kt).
 */
object Rescue {
    /**
     * One list after "Get my lists back": the wishes as the server has them (the newest truth for everything friends
     * can see), in the server's order, with two things only the phone knows: a photo of your own that is still on the
     * phone stays that file, and the old copy's private wishes are added (they never go online).
     */
    fun merge(old: List<Wish>, online: List<Wish>, hasFile: (String) -> Boolean): List<Wish> {
        val before = old.associateBy { it.id }
        val out = online.filter { it.bin == 0L }.map { w ->
            val o = before[w.id]
            if (o != null && ImageStore.isLocal(o.image) && hasFile(o.image)) w.copy(image = o.image) else w
        }
        val ids = out.map { it.id }.toSet()
        return out + old.filter { it.priv && it.bin == 0L && it.id !in ids }
    }

    /**
     * Fetches every online list of yours and puts it in place of the old copy. Throws when the server can't be reached:
     * nothing is changed then. Returns true when the app has to start again ([ListStore.rescued]).
     */
    suspend fun run(c: Context): Boolean {
        val lists = Repo.allMine(c)
        // Everything is fetched first: a connection that drops halfway changes nothing.
        val online = HashMap<String, RemoteList>()
        for (d in lists) {
            val code = d.code ?: continue
            val token = d.token ?: continue
            Cloud.mineGet(code, token)?.let { online[d.id] = it }
        }
        for (d in lists) {
            val r = online[d.id] ?: continue
            Repo.updateMine(c, d.id) { old -> merge(old, r.wishes) { img -> try { java.io.File(Uri.parse(img).path.orEmpty()).exists() } catch (e: Exception) { false } } }
            // The next save sends the whole list once, so the phone and the server agree again.
            d.code?.let { MineSync.forget(c, it) }
        }
        return ListStore.rescued(c)
    }
}

/** The stop screen (owner, 10 November 2026). Shown over everything while the lists are an old copy. */
@Composable
fun RescueScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf<Uri?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importing = uri }
    Column(
        Modifier.fillMaxSize().background(cs.background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(tr("Wishlist couldn’t open your lists"), style = MaterialTheme.typography.headlineSmall, color = cs.onBackground, textAlign = TextAlign.Center)
        Text(
            tr("Nothing was deleted, and nothing is sent to your friends until this is solved."),
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.error, textAlign = TextAlign.Center)
        Button(
            onClick = {
                busy = true; err = ""
                scope.launch {
                    try {
                        if (Rescue.run(ctx)) Backup.restart(ctx)
                    } catch (e: Exception) {
                        err = tr("Couldn’t reach the server. Check your connection and try again.")
                    }
                    busy = false
                }
            },
            enabled = !busy && Config.enabled, modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) tr("Getting your lists…") else tr("Get my lists back")) }
        Text(
            tr("Brings back every wishlist you shared. Private wishes you added lately may be missing."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        // Only when the database couldn't be read: starting again may be all it needs.
        if (!ListStore.live) OutlinedButton(onClick = { Backup.restart(ctx) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("Try again")) }
        OutlinedButton(
            onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text(tr("Import a backup file")) }
    }
    importing?.let { uri ->
        AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text(tr("Replace everything on this phone?")) },
            text = { Text(tr("Everything on this phone will be replaced by the backup. This can’t be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    importing = null; busy = true; err = ""
                    scope.launch {
                        try {
                            Backup.import(ctx, uri)
                            Backup.restart(ctx)
                        } catch (e: Exception) {
                            busy = false
                            err = tr("That file isn’t a Wishlist backup.")
                        }
                    }
                }) { Text(tr("Import"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { importing = null }) { Text(tr("Cancel")) } }
        )
    }
}
