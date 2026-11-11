package com.wishlist.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The recovery key (ROADMAP S4.5; owner, 11 November 2026): everything comes back on a new phone with one key, no
 * e-mail. The key is 32 letters and digits (160 random bits), shown in eight groups of four ("K7QM-…"), saved with
 * one tap to the phone's password manager (Android's Credential Manager), shared to a notes app, or copied. The owner's
 * line next to it: "Keep your wishes safe: never share this key with anyone."
 *
 * The key never goes online. From it come ([derive]) the id of the locked copy, a write proof (the server keeps only a
 * fingerprint of it) and the lock (AES-256-GCM). The locked copy is the Export of this phone without picture files,
 * plus the chat keys (Backup.bytesForVault), saved when it changed, at most every hour ([backup]); supabase-vault.sql.
 * Restoring on a new phone, which also signs out the old one, comes in 2.188.
 */
object RecoveryKey {
    /** 32 symbols without look-alikes (no I, O, 0, 1): 5 bits each. */
    private const val ABC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val rnd = SecureRandom()
    const val CREDENTIAL_ID = "Wishlist recovery key"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** A new key: 32 symbols. */
    fun newKey(): String = (1..32).map { ABC[rnd.nextInt(32)] }.joinToString("")

    /** "K7QM-2HXA-…": eight groups of four, for reading and writing down. */
    fun pretty(k: String): String = k.chunked(4).joinToString("-")

    /** A key as someone typed or pasted it (spaces, dashes, small letters, the whole shared text): its 32 symbols, or null. */
    fun parse(s: String): String? {
        val up = s.uppercase()
        // Shared text has words around the key: take the longest run of key symbols and dashes.
        val run = Regex("[A-Z2-9]{4}(?:[- ]?[A-Z2-9]{4}){7}").find(up)?.value ?: return null
        val k = run.filter { it in ABC }
        return if (k.length == 32) k else null
    }

    private fun hmac(key: ByteArray, label: String): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(label.toByteArray(Charsets.UTF_8)) }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** What the key unlocks: the copy's id and write proof (hex, for the server) and the lock (never sent). */
    data class Derived(val id: String, val proof: String, val lock: ByteArray)

    fun derive(k: String): Derived {
        val raw = k.toByteArray(Charsets.UTF_8)
        return Derived(hex(hmac(raw, "wishlist vault id")), hex(hmac(raw, "wishlist vault write")), hmac(raw, "wishlist vault lock"))
    }

    /** Locks [plain] with [lock]: 12 random bytes, then AES-256-GCM, as base64. */
    fun lock(plain: ByteArray, lock: ByteArray): String {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(lock, "AES"), GCMParameterSpec(128, iv))
        return java.util.Base64.getEncoder().encodeToString(iv + c.doFinal(plain))
    }

    /** Unlocks what [lock] made; throws with a wrong key or a damaged copy. */
    fun unlock(locked: String, lock: ByteArray): ByteArray {
        val all = java.util.Base64.getDecoder().decode(locked)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(lock, "AES"), GCMParameterSpec(128, all, 0, 12))
        return c.doFinal(all, 12, all.size - 12)
    }

    /** This phone's key, made the first time it is asked for. */
    fun key(c: Context): String {
        val p = prefs(c)
        p.getString("rk", null)?.takeIf { it.length == 32 }?.let { return it }
        val k = newKey()
        p.edit().putString("rk", k).apply()
        return k
    }

    /** The key was saved somewhere (password manager, shared or copied): the Home card goes. */
    fun saved(c: Context): Boolean = prefs(c).getBoolean("rk_saved", false)
    fun markSaved(c: Context) { prefs(c).edit().putBoolean("rk_saved", true).apply(); cardShown = false }

    /** The card on Home (owner: existing users): until saved; Later hides it a week, at most 3 times. */
    var cardShown by mutableStateOf(true)
    fun showCard(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(c)
        return cardShown && Config.enabled && !saved(c) && p.getInt("rk_later_n", 0) < 3 && now >= p.getLong("rk_later_until", 0L)
    }
    fun later(c: Context) {
        val p = prefs(c)
        p.edit().putInt("rk_later_n", p.getInt("rk_later_n", 0) + 1).putLong("rk_later_until", System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000).apply()
        cardShown = false
    }

    /**
     * Saves the locked copy when what it holds changed, at most once an hour (or a week, to keep it from being cleaned
     * up). Never with an old copy of the lists (ListStore.stale). Before supabase-vault.sql is run, nothing happens.
     */
    suspend fun backup(c: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!Config.enabled || ListStore.stale || Crash.onComputer) return@withContext
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("rk_up_at", 0L) < 60L * 60 * 1000) return@withContext
        val plain = try { Backup.bytesForVault(c) } catch (e: Exception) { Crash.report(e); return@withContext }
        val fp = hex(MessageDigest.getInstance("SHA-256").digest(plain))
        if (!force && fp == p.getString("rk_up_fp", null) && now - p.getLong("rk_up_at", 0L) < 7L * 24 * 60 * 60 * 1000) return@withContext
        val d = derive(key(c))
        val locked = lock(plain, d.lock)
        if (locked.length > 300_000) { Crash.report(IllegalStateException("The recovery copy is too big: ${locked.length}")); return@withContext }
        try {
            Cloud.rpc("wl_vault_put", JSONObject().put("p_id", d.id).put("p_proof", d.proof).put("p_data", locked))
            p.edit().putString("rk_up_fp", fp).putLong("rk_up_at", now).apply()
        } catch (e: Exception) {
            // Offline, or the script isn't run yet: tried again later.
        }
    }

    /** The Activity behind a Compose context (Credential Manager needs one). */
    fun activityOf(c: Context): Activity? {
        var x: Context? = c
        while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
        return null
    }

    /** Opens the phone's password manager to save the key. True when it was saved; false when cancelled or impossible. */
    suspend fun saveToPasswordManager(c: Context): Boolean {
        val a = activityOf(c) ?: return false
        return try {
            androidx.credentials.CredentialManager.create(a).createCredential(
                a, androidx.credentials.CreatePasswordRequest(id = CREDENTIAL_ID, password = pretty(key(c)))
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * The key and the three ways to save it (owner, 11 November 2026): "Save to password manager" first, then Share and
 * Copy. Used in Profile (a dialog), on the Home card's "Save your key" and in the welcome.
 */
@Composable
fun RecoveryKeyBody(snack: SnackbarHostState?) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val key = remember { RecoveryKey.pretty(RecoveryKey.key(ctx)) }
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            tr("With this key you get all your wishlists back on a new phone."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Text(
            key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant).padding(16.dp)
        )
        Text(
            tr("Keep your wishes safe: never share this key with anyone."),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = cs.onSurface, textAlign = TextAlign.Center
        )
        Button(onClick = {
            scope.launch {
                if (RecoveryKey.saveToPasswordManager(ctx)) { RecoveryKey.markSaved(ctx); note = tr("Saved to your password manager") }
                else note = tr("Couldn’t open a password manager. Use Share or Copy instead.")
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr("Save to password manager")) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                RecoveryKey.markSaved(ctx)
                // Only the key and what it is: shared to your own notes or password app (owner, 11 November 2026).
                shareText(ctx, tr("Save your recovery key"), tr("My Wishlist recovery key: %s", key))
            }, modifier = Modifier.weight(1f)) { Text(tr("Share")) }
            OutlinedButton(onClick = {
                clip.setText(AnnotatedString(key))
                RecoveryKey.markSaved(ctx)
                if (android.os.Build.VERSION.SDK_INT < 33) note = tr("Key copied")
            }, modifier = Modifier.weight(1f)) { Text(tr("Copy")) }
        }
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Profile > Recovery key: the key in a dialog. */
@Composable
fun RecoveryKeyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Recovery key")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { RecoveryKeyBody(null) } },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done")) } }
    )
}

/** The card on Home for people who haven't saved their key yet (owner: existing users after the update). */
@Composable
fun RecoveryCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    if (!RecoveryKey.showCard(ctx)) return
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(cs.secondaryContainer).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Text(tr("Keep your wishlists safe on a new phone"), style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer)
        Text(
            tr("Save your recovery key once. With it, everything comes back on a new phone."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer, modifier = Modifier.padding(top = 4.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { RecoveryKey.later(ctx) }) { Text(tr("Later")) }
            TextButton(onClick = { open = true }) { Text(tr("Save your key")) }
        }
    }
    if (open) RecoveryKeyDialog { open = false }
}
