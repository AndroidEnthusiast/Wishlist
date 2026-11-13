package com.wishlist.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * What the app is told about itself (ROADMAP R6; owner, 10 November 2026): the newest version, the oldest version that
 * still works, and since when the newest one is out. It is one small file next to the web pages
 * (`…/Wishlist/app.json`), written by the GitHub job that publishes them from `app/build.gradle.kts` and
 * `web/app.json` (where `min` and `notice` are set by hand). No Supabase function: one moving part less, and the file
 * is still reachable if the database ever moves.
 *
 * - **A new version**: a slim bar on Home, "A new version is ready" with Update and Later. Only once the version has
 *   been out for a day (during development there can be several a day); Later hides it for a week.
 * - **Too old** (below `min`): a full "Please update" screen. Never used lightly: it locks people out until they update.
 *
 * The rules are plain functions ([showBar], [tooOld], [parse]; AppInfoTest).
 */
object AppInfo {
    data class Info(val latest: Int, val name: String, val since: Long, val min: Int, val notice: String)

    var info by mutableStateOf<Info?>(null)
        private set
    var laterUntil by mutableStateOf(0L)
        private set
    private var fetchedAt = 0L

    const val DAY = 24L * 60 * 60 * 1000
    private const val ASK_EVERY = DAY / 2
    private const val LATER = 7 * DAY

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** The file's text as [Info], or null when it isn't what we expect (then nothing is shown). */
    fun parse(text: String?): Info? = try {
        val o = JSONObject(text ?: "")
        val latest = o.getInt("latest")
        val min = o.optInt("min", 0)
        // A file that says "everything is too old" is a mistake, never a reason to lock people out.
        if (latest <= 0 || min > latest) null
        else Info(latest, o.optString("name"), o.optLong("since", 0L), min, o.optString("notice").take(300))
    } catch (e: Exception) {
        null
    }

    /** The bar on Home: a newer version that has been out for a day, and "Later" wasn't tapped in the last week. */
    fun showBar(installed: Int, i: Info?, now: Long, laterUntil: Long): Boolean =
        i != null && installed > 0 && i.latest > installed && i.since > 0 && now - i.since * 1000 >= DAY && now >= laterUntil

    /** This version no longer works with the server. */
    fun tooOld(installed: Int, i: Info?): Boolean = i != null && installed > 0 && installed < i.min

    /** This app's own version number (versionCode), 0 when Android can't say. */
    fun installed(c: Context): Int = try {
        c.packageManager.getPackageInfo(c.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }
    } catch (e: Exception) {
        0
    }

    /** What was fetched last time, so the bar and the screen show at once at the next start (also offline). */
    fun load(c: Context) {
        val p = prefs(c)
        info = parse(p.getString("app_info", null))
        laterUntil = p.getLong("app_later", 0L)
        fetchedAt = p.getLong("app_info_at", 0L)
    }

    /** Asks for the file, at most twice a day (every start while this version is too old: a fix must arrive at once). */
    suspend fun refresh(c: Context) {
        if (Crash.onComputer) return
        val now = System.currentTimeMillis()
        if (now - fetchedAt < ASK_EVERY && !tooOld(installed(c), info)) return
        val text = withContext(Dispatchers.IO) {
            try {
                val u = java.net.URL(Config.SHARE_URL.trimEnd('/') + "/app.json")
                val h = u.openConnection() as java.net.HttpURLConnection
                h.connectTimeout = 10000; h.readTimeout = 10000; h.useCaches = false
                try { if (h.responseCode == 200) h.inputStream.bufferedReader().use { it.readText().take(4000) } else null } finally { h.disconnect() }
            } catch (e: Exception) {
                null
            }
        } ?: return
        val i = parse(text) ?: return
        info = i
        fetchedAt = now
        prefs(c).edit().putString("app_info", text).putLong("app_info_at", now).apply()
    }

    /** "Later" on the bar: away for a week. */
    fun later(c: Context) {
        laterUntil = System.currentTimeMillis() + LATER
        prefs(c).edit().putLong("app_later", laterUntil).apply()
    }

    /** Opens the download of the newest app (the browser downloads it; opening the file installs it over this one). */
    fun download(c: Context): Boolean = try {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }
}

/** The slim bar at the top of Home (owner, 10 November 2026). Nothing when there is nothing to tell. */
@Composable
fun UpdateBar() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    if (!AppInfo.showBar(AppInfo.installed(ctx), AppInfo.info, System.currentTimeMillis(), AppInfo.laterUntil)) return
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth()
            .lookSurface(LocalLook.current, MaterialTheme.shapes.medium, cs.secondaryContainer, depth = 4.dp).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Text(tr("A new version of Wishlist is ready."), style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { AppInfo.later(ctx) }) { Text(tr("Later")) }
            TextButton(onClick = { AppInfo.download(ctx) }) { Text(tr("Update")) }
        }
    }
}

/** The whole screen when this version is too old to work (below `min`). The only way on is the update. */
@Composable
fun TooOldScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val notice = AppInfo.info?.notice.orEmpty()
    Box(Modifier.fillMaxSize().background(cs.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("Please update Wishlist"), style = MaterialTheme.typography.headlineSmall, color = cs.onBackground, textAlign = TextAlign.Center)
            Text(
                tr("This version is too old to work. Install the newest one over it: your wishes stay."),
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
            )
            if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = { AppInfo.download(ctx) }) { Text(tr("Update")) }
        }
    }
}
