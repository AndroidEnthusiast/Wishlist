package com.wishlist.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * Export and import (owner, 2.95): App settings > Backup. "Export everything" makes one .zip file with all the app's
 * settings and data (the "wishlist" preferences: your lists, wishes, keys of your lists, your profile and friends, the
 * wishlists for someone you're on, notifications, price history) and every file the app keeps (your own photos,
 * covers, profile photo). You keep the file yourself (Drive, e-mail, USB). "Import" on another phone replaces
 * everything there with the file (after a warning) and restarts the app. No password (owner's choice): the file holds
 * your keys, so the screen says to keep it safe.
 */
object Backup {
    private const val PREFS = "wishlist"
    private const val VERSION = 1

    /** Writes everything to [uri] (a file the person chose). */
    suspend fun export(c: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        val values = JSONObject()
        for ((k, v) in prefs) {
            val o = JSONObject()
            when (v) {
                is String -> o.put("t", "s").put("v", v)
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is Set<*> -> o.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            values.put(k, o)
        }
        val manifest = JSONObject().put("app", "Wishlist").put("version", VERSION)
            .put("files", c.filesDir.absolutePath).put("time", System.currentTimeMillis())
        val out = c.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("no file")
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(manifest.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("prefs.json")); z.write(values.toString().toByteArray()); z.closeEntry()
            c.filesDir.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry("files/" + f.relativeTo(c.filesDir).path))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** A name for the file: "Wishlist backup 2026-11-02.zip". */
    fun fileName(): String = "Wishlist backup " + SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date()) + ".zip"

    /**
     * Reads a backup from [uri] and replaces everything on this phone with it. Throws when the file isn't a Wishlist
     * backup (nothing is changed then). The caller restarts the app afterwards ([restart]).
     */
    suspend fun import(c: Context, uri: Uri) = withContext(Dispatchers.IO) {
        // First read it all into memory and check it, so a wrong file changes nothing.
        var manifest: JSONObject? = null
        var values: JSONObject? = null
        val files = LinkedHashMap<String, ByteArray>()
        val input = c.contentResolver.openInputStream(uri) ?: throw IllegalStateException("no file")
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val bytes = z.readBytes()
                when {
                    e.name == "manifest.json" -> manifest = JSONObject(String(bytes))
                    e.name == "prefs.json" -> values = JSONObject(String(bytes))
                    e.name.startsWith("files/") && !e.isDirectory -> files[e.name.removePrefix("files/")] = bytes
                }
            }
        }
        val m = manifest ?: throw IllegalArgumentException("not a backup")
        val v = values ?: throw IllegalArgumentException("not a backup")
        if (m.optString("app") != "Wishlist") throw IllegalArgumentException("not a backup")
        // Pictures are stored as addresses in this phone's folder; another phone may have another folder.
        val oldDir = m.optString("files")
        val newDir = c.filesDir.absolutePath
        fun fix(s: String) = if (oldDir.isNotEmpty() && oldDir != newDir) s.replace(oldDir, newDir) else s

        // Replace the files.
        c.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        val root = c.filesDir.canonicalFile
        for ((path, bytes) in files) {
            val f = File(c.filesDir, path).canonicalFile
            // Never write outside the app's own folder.
            if (!f.path.startsWith(root.path + File.separator)) continue
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        // Replace the settings and data.
        val e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        for (k in v.keys()) {
            val o = v.getJSONObject(k)
            when (o.optString("t")) {
                "s" -> e.putString(k, fix(o.getString("v")))
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                "set" -> {
                    val a = o.getJSONArray("v")
                    e.putStringSet(k, (0 until a.length()).map { fix(a.getString(it)) }.toSet())
                }
            }
        }
        // This phone has seen the welcome (the backup's person has).
        e.putBoolean("welcomed", true)
        e.commit()
    }

    /** Starts the app again, so everything is read from the imported data. */
    fun restart(c: Context) {
        val i = c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        c.startActivity(i)
        Runtime.getRuntime().exit(0)
    }
}

/** App settings > Backup: export everything, or import a backup (replaces everything, after a warning). */
@Composable
fun BackupSection(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val msg = try {
                    Backup.export(ctx, uri)
                    tr("Backup saved. Keep it somewhere safe.")
                } catch (e: Exception) {
                    tr("Couldn’t make the backup.")
                }
                busy = false
                snack.showToast(msg)
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importing = uri
    }
    Text(
        tr("Moving to a new phone? Export everything to one file: your wishlists, wishes, photos, profile, friends and the gift lists you’re on. Then import it on the new phone."),
        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { save.launch(Backup.fileName()) }, enabled = !busy) { Text(tr("Export everything")) }
        OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
            Text(tr("Import"))
        }
    }
    Text(
        tr("The file holds your keys: whoever has it can act as you. Keep it somewhere safe, for example your own Google Drive."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
    importing?.let { uri ->
        AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text(tr("Replace everything on this phone?")) },
            text = { Text(tr("Everything in Wishlist on this phone is replaced by the backup, and the app starts again. This can’t be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    importing = null
                    busy = true
                    scope.launch {
                        try {
                            Backup.import(ctx, uri)
                            Backup.restart(ctx)
                        } catch (e: Exception) {
                            busy = false
                            snack.showToast(tr("That file isn’t a Wishlist backup."))
                        }
                    }
                }) { Text(tr("Import"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { importing = null }) { Text(tr("Cancel")) } }
        )
    }
}
