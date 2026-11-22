package com.wishlist.app

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/* App settings (see Settings.kt): the pages behind its rows. What they change goes through SettingsModel.kt. */

/** Every notification you may switch off, the price alerts included (owner, 18 November 2026: one place). */
@Composable
internal fun NotificationsPage() {
    val ctx = LocalContext.current
    val model = rememberSettings()
    val cs = MaterialTheme.colorScheme
    // Android can still block them in its own settings: then say so, with a way to switch them back on.
    var blocked by remember { mutableStateOf(Alerts.blockedImportant(ctx)) }
    val life = LocalLifecycleOwner.current
    DisposableEffect(life) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) blocked = Alerts.blockedImportant(ctx) }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    SettingsGroup("") {
        if (blocked.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(blocked.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.weight(1f).padding(end = 12.dp))
            TextButton(onClick = { Alerts.openSettings(ctx) }) { Text(tr("Turn on")) }
        }
        // Only the notifications you may switch off (owner, 2.126). Reserved gifts that are gone and wishlist group
        // invitations always come; "friend request accepted" and group news only show on the Notifications page.
        SettingSwitch(tr("Friend requests"), null, Show["nt_req"]) { model.setShown("nt_req", it) }
        SettingSwitch(tr("Friends’ birthdays"), null, Show["nt_bday"]) { model.setShown("nt_bday", it) }
        SettingSwitch(tr("New messages"), null, Show["nt_msg"]) { model.setShown("nt_msg", it) }
        SettingSwitch(tr("Reactions"), tr("Once a day, in the evening"), Show["nt_react"]) { model.setShown("nt_react", it) }
        SettingSwitch(tr("Price drops"), null, Repo.notifyDrops) { model.setPriceDrops(it) }
        SettingSwitch(tr("Price increases"), null, Repo.notifyRises) { model.setPriceRises(it) }
    }
}

/** How often prices are checked (one row and a small list, owner: the chips took too much room) and the currency. */
@Composable
internal fun PricesPage() {
    val ctx = LocalContext.current
    val model = rememberSettings()
    val cs = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    SettingsGroup("") {
        ValueRow(minHeight = 48.dp, label = tr("Check prices"), value = intervalLabel(Repo.intervalHours)) { picking = true }
        CurrencySetting()
    }
    Text(
        if (Repo.lastCheck == 0L) tr("No background check has finished yet.")
        else tr("Last check: %s", DateUtils.getRelativeTimeSpanString(Repo.lastCheck)),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)
    )
    // Only while Android may hold back the checks (not Unrestricted): a way to allow it after "Not now".
    // Checked again each time you come back to the app (after Android's question).
    var bgOk by remember { mutableStateOf(Background.allowed(ctx)) }
    val life = LocalLifecycleOwner.current
    DisposableEffect(life) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) bgOk = Background.allowed(ctx) }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    if (!bgOk) SettingsGroup("") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Android may hold back price checks while your phone sleeps."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            TextButton(onClick = { Background.request(ctx) }) { Text(tr("Allow")) }
        }
    }
    // A pick-one list is a sheet with a tick (owner, 21 November 2026; was a dialog with Close).
    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
        listOf(1, 3, 6, 12, 24).forEach { h ->
            DropdownMenuItem(text = { Text(intervalLabel(h)) }, selected = Repo.intervalHours == h, onClick = { model.setInterval(h); picking = false })
        }
    }
}

/**
 * About (owner, 18 November 2026): the version; Download only when a newer version exists; Share app; Licences;
 * Privacy (the crash reports are explained on the privacy page). A long press on the version shows the server calls
 * (Calls.kt), for checking problems.
 */
@Composable
internal fun AboutPage(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var licences by remember { mutableStateOf(false) }
    var calls by remember { mutableStateOf(false) }
    val newer = AppInfo.info?.let { it.latest > AppInfo.installed(ctx) } == true
    SettingsGroup("") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).combinedClickable(onLongClick = { calls = true }, onClick = {}),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("Version"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(appVersion(ctx), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (newer) Button(onClick = {
            if (!AppInfo.download(ctx)) scope.launch { snack.showToast(tr("Couldn’t open the download. Check your connection.")) }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr("Download version %s", AppInfo.info?.name.orEmpty())) }
        ValueRow(minHeight = 48.dp, label = tr("Share app"), value = "") {
            scope.launch { try { shareApk(ctx) } catch (e: Exception) { snack.showToast(tr("Couldn’t share the app")) } }
        }
        ValueRow(minHeight = 48.dp, label = tr("Licences"), value = "") { licences = true }
        ValueRow(minHeight = 48.dp, label = tr("Privacy"), value = "") { openPrivacy(ctx) }
    }
    if (licences) LicencesDialog { licences = false }
    if (calls) AlertDialog(
        onDismissRequest = { calls = false },
        title = { Text(tr("Server calls")) },
        text = { ServerCallsRow() },
        confirmButton = { TextButton(onClick = { calls = false }) { Text(tr("Close")) } }
    )
}

/** One switch in App settings: a title, a few words about it only when needed, and the switch (the whole row taps). */
@Composable
internal fun SettingSwitch(title: String, about: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (about != null) Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/**
 * "Currency for new wishes" (owner, 2.122): Automatic (the phone's country, else the euro) or one you pick, for example
 * when you live abroad. Also used for shop pages that don't say. Each wish's own currency can still be changed in its form.
 */
@Composable
private fun CurrencySetting() {
    val ctx = LocalContext.current
    val model = rememberSettings()
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val chosen = CurrencyChoice.chosen
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(tr("Currency"), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            if (chosen.isEmpty()) tr("Automatic (%s)", countryCurrency()) else "${currencySymbol(chosen)} $chosen",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1
        )
        Box {
            Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(20.dp))
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(tr("Automatic (%s)", countryCurrency())) }, selected = chosen.isEmpty(), onClick = { open = false; model.setCurrency("") })
                CurrencyChoice.all.forEach { cur ->
                    DropdownMenuItem(text = { Text("${currencySymbol(cur)}  $cur") }, selected = cur == chosen, onClick = { open = false; model.setCurrency(cur) })
                }
            }
        }
    }
}
