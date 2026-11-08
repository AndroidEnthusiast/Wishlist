package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate

/**
 * How many calls this phone makes to the server per day (ROADMAP S3.4, owner 8 November 2026: "App settings > About").
 * Only numbers per kind of call (the server function's name, "photo upload", "live"…), kept on this phone for the
 * last 7 days and never sent anywhere. It shows whether Stage 3 really made the app ask the server less.
 *
 * Its own small store ("calls"), so it never travels in an Export.
 * The counting itself is [CallLog] (no Android, checked in CallLogTest); this object keeps it in the settings store.
 */
object Calls {
    private const val KEY = "server_calls"
    private var app: Context? = null
    private var log: CallLog? = null

    /** Changes with every counted call, so App settings' line updates while it is open. */
    var version by mutableIntStateOf(0)
        private set

    fun attach(c: Context) { app = c.applicationContext }

    @Synchronized
    private fun loaded(): CallLog? {
        log?.let { return it }
        val c = app ?: return null
        val l = try {
            CallLog.fromJson(prefs(c).getString(KEY, null))
        } catch (e: Exception) {
            CallLog()
        }
        log = l
        return l
    }

    /** One call of [kind] made just now. */
    fun count(kind: String) {
        synchronized(this) {
            val l = loaded() ?: return
            l.add(LocalDate.now(), kind)
            // Written at once; a call already takes far longer than this small write.
            app?.let { prefs(it).edit().putString(KEY, l.toJson()).apply() }
        }
        try { version++ } catch (e: Exception) {}
    }

    /** Today's and yesterday's counts per kind. */
    @Synchronized
    fun day(d: LocalDate): Map<String, Int> = loaded()?.day(d) ?: emptyMap()

    private fun prefs(c: Context) = c.getSharedPreferences("calls", Context.MODE_PRIVATE)
}

/** Calls per day and kind, the last [days] days only. */
class CallLog(private val days: Int = 7) {
    private val byDay = sortedMapOf<String, MutableMap<String, Int>>()

    fun add(d: LocalDate, kind: String, n: Int = 1) {
        val m = byDay.getOrPut(d.toString()) { sortedMapOf() }
        m[kind] = (m[kind] ?: 0) + n
        while (byDay.size > days) byDay.remove(byDay.firstKey())
    }

    fun day(d: LocalDate): Map<String, Int> = byDay[d.toString()]?.toMap() ?: emptyMap()

    fun total(d: LocalDate): Int = day(d).values.sum()

    fun toJson(): String {
        val o = JSONObject()
        for ((d, m) in byDay) o.put(d, JSONObject(m as Map<*, *>))
        return o.toString()
    }

    companion object {
        fun fromJson(s: String?): CallLog {
            val l = CallLog()
            if (s.isNullOrBlank()) return l
            val o = JSONObject(s)
            for (d in o.keys()) {
                val day = try { LocalDate.parse(d) } catch (e: Exception) { continue }
                val m = o.optJSONObject(d) ?: continue
                for (k in m.keys()) l.add(day, k, m.optInt(k, 0).coerceAtLeast(0))
            }
            return l
        }
    }
}

/** App settings > About: "Server calls today: 42 (yesterday 51)" and Details (ROADMAP S3.4). */
@Composable
fun ServerCallsRow() {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val v = Calls.version
    val today = LocalDate.now()
    val nToday = Calls.day(today).values.sum()
    val nYday = Calls.day(today.minusDays(1)).values.sum()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr("Server calls today: %1\$d (yesterday %2\$d). Only counted on this phone, never sent.", nToday, nYday),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        TextButton(onClick = { open = true }) { Text(tr("Details")) }
    }
    if (open) ServerCallsDialog { open = false }
}

@Composable
private fun ServerCallsDialog(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val a = Calls.day(today)
    val b = Calls.day(today.minusDays(1))
    val kinds = (a.keys + b.keys).sortedByDescending { (a[it] ?: 0) + (b[it] ?: 0) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Server calls")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("How often this phone asked the server, per kind: today, then yesterday."),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (kinds.isEmpty()) Text(tr("No calls yet."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                for (k in kinds) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Text("${a[k] ?: 0} · ${b[k] ?: 0}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } }
    )
}
