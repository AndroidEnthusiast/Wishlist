package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Restoring with the recovery key (ROADMAP S4.5, 2.188; owner, 11–12 November 2026). "I already have lists" on the
 * welcome's first step, or Profile > "Restore with a recovery key":
 *  1. the key comes from the password manager by itself, or is typed or pasted;
 *  2. the locked copy is read (wl_vault_get) and unlocked on the phone ([RecoveryKey.unlock]);
 *  3. the profile and every list made on the old phone get NEW secrets on the server and the copy is deleted, in one
 *     call (wl_vault_restore, supabase-vault.sql): the old phone is signed out at once ([Moved]);
 *  4. the copy replaces what is on this phone (Backup.apply, with the new secrets swapped in), a new recovery key is
 *     made, and the app starts again with "Your wishlists are back" and the new key to save ([NewKeyDialog]).
 * Between 3 and 4 the unlocked copy waits in a file outside the backed-up folders ([pending]); a start that finds it
 * finishes the restore ([resume]), so a crash at that moment loses nothing.
 */
object Restore {
    enum class Outcome { DONE, NOT_FOUND, OFFLINE }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun pending(c: Context) = File(c.noBackupFilesDir, "restore.zip")
    private fun pendingSwap(c: Context) = File(c.noBackupFilesDir, "restore.json")

    /** The saved key from the phone's password manager (Credential Manager), or null when there is none or it was closed. */
    suspend fun fromPasswordManager(c: Context): String? {
        val a = RecoveryKey.activityOf(c) ?: return null
        return try {
            val r = androidx.credentials.CredentialManager.create(a).getCredential(
                a, androidx.credentials.GetCredentialRequest(listOf(androidx.credentials.GetPasswordOption()))
            )
            (r.credential as? androidx.credentials.PasswordCredential)?.password?.let { RecoveryKey.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** The secrets in a backup's settings that a restore replaces: the profile's token and each list's owner token. */
    data class Secrets(val profileId: String, val profileToken: String, val lists: List<Pair<String, String>>)

    /** Reads [Secrets] from a backup's prefs.json (the format Backup.write makes; DbMove.toStore for the lists). */
    fun secretsOf(values: JSONObject): Secrets {
        fun str(k: String): String = values.optJSONObject(k)?.takeIf { it.optString("t") == "s" }?.optString("v").orEmpty()
        val lists = ArrayList<Pair<String, String>>()
        fun add(code: String, token: String) { if (code.isNotEmpty() && token.length >= 16) lists += code to token }
        add(str("code"), str("token"))
        for (k in values.keys()) {
            if (!k.startsWith("mine_") || k == "mine_ids") continue
            val o = try { JSONObject(str(k)) } catch (e: Exception) { continue }
            add(o.optString("code"), o.optString("token"))
        }
        val shared = try { JSONArray(str("shared").ifEmpty { "[]" }) } catch (e: Exception) { JSONArray() }
        for (i in 0 until shared.length()) shared.optJSONObject(i)?.let { add(it.optString("code"), it.optString("owner")) }
        return Secrets(str("pf_id"), str("pf_token"), lists.distinct())
    }

    /** Restores with [key] (its 32 symbols). Nothing on this phone changes unless the answer is [Outcome.DONE]. */
    suspend fun run(c: Context, key: String): Outcome = withContext(Dispatchers.IO) {
        val d = RecoveryKey.derive(key)
        val locked = try {
            val t = Cloud.rpc("wl_vault_get", JSONObject().put("p_id", d.id).put("p_proof", d.proof)).trim()
            if (t == "null" || t.isEmpty()) return@withContext Outcome.NOT_FOUND
            JSONArray("[$t]").optString(0)
        } catch (e: Exception) {
            return@withContext Outcome.OFFLINE
        }
        val plain = try { RecoveryKey.unlock(locked, d.lock) } catch (e: Exception) { return@withContext Outcome.NOT_FOUND }
        val parts = try { Backup.read(plain.inputStream()) } catch (e: Exception) { Crash.report(e); return@withContext Outcome.NOT_FOUND }
        val s = secretsOf(parts.values)
        // New secrets for everything the old phone could change; the same new token for a list used twice.
        val swap = LinkedHashMap<String, String>()
        if (s.profileToken.length >= 16) swap[s.profileToken] = Sync.newKey()
        for ((_, t) in s.lists) if (t !in swap) swap[t] = Sync.newKey()
        // Kept until the copy is on this phone: the server call below makes the old secrets useless.
        pending(c).writeBytes(plain)
        pendingSwap(c).writeText(JSONObject(swap as Map<*, *>).put("_ready", "no").toString())
        val lists = JSONArray()
        for ((code, t) in s.lists) lists.put(JSONObject().put("c", code).put("o", t).put("n", swap[t]))
        try {
            Cloud.rpc(
                "wl_vault_restore",
                JSONObject().put("p_vault", d.id).put("p_proof", d.proof).put("p_id", s.profileId)
                    .put("p_old", s.profileToken).put("p_new", swap[s.profileToken].orEmpty()).put("p_lists", lists)
            )
        } catch (e: Exception) {
            pending(c).delete(); pendingSwap(c).delete()
            return@withContext if ((e.message ?: "").contains("not allowed")) Outcome.NOT_FOUND else Outcome.OFFLINE
        }
        pendingSwap(c).writeText(JSONObject(swap as Map<*, *>).put("_ready", "yes").toString())
        finish(c)
        Outcome.DONE
    }

    /** Puts the waiting copy on this phone with the new secrets, makes a new recovery key, and clears the waiting files. */
    private suspend fun finish(c: Context) {
        val o = JSONObject(pendingSwap(c).readText())
        val swap = LinkedHashMap<String, String>()
        for (k in o.keys()) if (k != "_ready") swap[k] = o.getString(k)
        Backup.apply(c, Backup.read(pending(c).inputStream()), swap)
        // The old key opened the old copy: a new key, a new copy (saved soon after the start), the new key shown.
        prefs(c).edit()
            .putString("rk", RecoveryKey.newKey()).putBoolean("rk_saved", false).putBoolean("rk_new", true)
            .remove("rk_up_fp").remove("rk_up_at").remove("rk_later_n").remove("rk_later_until")
            .remove("opened_at").remove(Moved.PREF)
            .commit()
        pending(c).delete(); pendingSwap(c).delete()
    }

    /**
     * At the start, before anything is read: a restore that reached the server but not the phone is finished now.
     * True when it was (the caller starts the app again). An unfinished one that never reached the server is dropped.
     */
    fun resume(c: Context): Boolean {
        val f = pendingSwap(c)
        if (!f.exists()) return false
        val ready = try { JSONObject(f.readText()).optString("_ready") == "yes" } catch (e: Exception) { false }
        if (!ready || !pending(c).exists()) { f.delete(); pending(c).delete(); return false }
        return try {
            kotlinx.coroutines.runBlocking { finish(c) }
            true
        } catch (e: Exception) {
            Crash.report(e)
            false
        }
    }

    /** Lists of your own and collab wishlists on this phone, for the "Delete the 2 wishlists" question. */
    fun listsHere(c: Context): Int = Repo.allMine(c).size + Shared.lists.size

    /** True when this phone holds something a restore would delete (wishes, more than one list, collab lists, a profile). */
    fun hasSomething(c: Context): Boolean =
        Repo.allMine(c).let { l -> l.size > 1 || l.any { it.wishes.isNotEmpty() } } || Shared.lists.isNotEmpty() || Friends.hasProfile(c)
}

/**
 * The old phone after the person restored on another one (owner, 12 November 2026). Its calls are refused ("not
 * allowed"); then it asks the server once whether its secrets were replaced by a restore (wl_signed_out). If so, only
 * [MovedScreen] shows, with one button, Start fresh.
 */
object Moved {
    const val PREF = "moved_away"
    var away by mutableStateOf(false)
    private var lastCheck = 0L
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun load(c: Context) {
        app = c.applicationContext
        away = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).getBoolean(PREF, false)
    }

    /** Called by Cloud.rpc when the server said "not allowed": checks, at most every 10 minutes. */
    fun suspect(fn: String) {
        if (fn == "wl_signed_out" || fn == "wl_vault_get" || fn == "wl_vault_restore" || away) return
        val c = app ?: return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 10 * 60 * 1000L) return
        lastCheck = now
        scope.launch { try { check(c) } catch (e: Exception) {} }
    }

    /** This phone's secrets: the profile's token and the owner tokens of its lists. */
    private fun secrets(c: Context): JSONArray {
        val a = JSONArray()
        c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).getString("pf_token", null)?.let { a.put(it) }
        for (d in Repo.allMine(c)) d.token?.let { a.put(it) }
        for (l in Shared.lists) if (l.owner.isNotEmpty()) a.put(l.owner)
        return a
    }

    suspend fun check(c: Context) {
        val s = secrets(c)
        if (s.length() == 0) return
        val r = Cloud.rpc("wl_signed_out", JSONObject().put("p_tokens", s)).trim()
        if (r == "true") {
            c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().putBoolean(PREF, true).commit()
            withContext(Dispatchers.Main) { away = true }
        }
    }

    /** Start fresh: everything of the app on this phone goes, and it starts again with the welcome. */
    fun startFresh(c: Context) {
        ListStore.forImport(c)
        c.filesDir.listFiles()?.forEach { if (!Backup.phoneOnly(it.name)) it.deleteRecursively() }
        for (n in listOf("wishlist", "chatkeys", "sent", "pulse")) c.getSharedPreferences(n, Context.MODE_PRIVATE).edit().clear().commit()
        Backup.restart(c)
    }
}

/** "Your wishlists are on your new phone" (owner's words, 12 November 2026), over everything, with Start fresh. */
@Composable
fun MovedScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize().lookPage(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(
                Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                PlaceholderIcon("gift", 56.dp, LocalLook.current.textAccent)
                Text(tr("Your wishlists are on your new phone"), style = MaterialTheme.typography.headlineSmall, color = cs.onBackground, textAlign = TextAlign.Center)
                Text(
                    tr("You used your recovery key on another phone, so your wishlists, friends and chats moved there. This phone is signed out."),
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
                Button(onClick = { Moved.startFresh(ctx) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(tr("Start fresh")) }
            }
        }
    }
}

/**
 * "Restore your wishlists" (owner's words, 12 November 2026). From the welcome ([confirmFirst] false: nothing to lose
 * yet) or from Profile (asks "Delete the 2 wishlists on this phone?" first when there is something on it).
 */
@Composable
fun RestoreDialog(confirmFirst: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    // The password manager offers the saved key by itself (owner, 11 November 2026).
    LaunchedEffect(Unit) { Restore.fromPasswordManager(ctx)?.let { text = RecoveryKey.pretty(it) } }
    fun go() {
        val k = RecoveryKey.parse(text)
        if (k == null) { err = tr("This key doesn’t match any saved wishlists. Check it and try again."); return }
        busy = true; err = ""
        scope.launch {
            when (Restore.run(ctx, k)) {
                Restore.Outcome.DONE -> Backup.restart(ctx)
                Restore.Outcome.NOT_FOUND -> { busy = false; err = tr("This key doesn’t match any saved wishlists. Check it and try again.") }
                Restore.Outcome.OFFLINE -> { busy = false; err = tr("No internet. Connect and try again.") }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Restore your wishlists")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Enter the recovery key you saved on your old phone."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                OutlinedTextField(
                    text, { text = it.take(200); err = "" }, label = { Text(tr("Recovery key")) }, enabled = !busy,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false),
                    isError = err.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                if (confirmFirst && Restore.hasSomething(ctx)) asking = true else go()
            }) { Text(if (busy) tr("Restoring…") else tr("Restore")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
    if (asking) {
        val n = remember { Restore.listsHere(ctx) }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(if (n == 1) tr("Delete the wishlist on this phone?") else tr("Delete the %d wishlists on this phone?", n)) },
            text = { Text(if (n == 1) tr("It is replaced by the wishlists saved with your recovery key.") else tr("They are replaced by the wishlists saved with your recovery key.")) },
            confirmButton = { TextButton(onClick = { asking = false; go() }) { Text(tr("Delete and restore"), color = cs.error) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("Cancel")) } }
        )
    }
}

/** After a restore (owner's words, 12 November 2026): "Your wishlists are back" and the new key to save. */
@Composable
fun NewKeyDialog(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Your wishlists are back")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tr("Your recovery key has changed. Save the new one now: the old key no longer works."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                )
                RecoveryKeyBody(null)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done")) } }
    )
}
