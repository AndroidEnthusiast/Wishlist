package com.wishlist.app

import android.text.format.DateUtils
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp)
    )
}

/**
 * Settings for the whole app (menu > Settings): your name, theme, price alerts, deleting, sharing the app.
 * What belongs to one list (name, note, categories, sharing) is in [ListSettingsScreen], the ⚙ on that list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(snack: SnackbarHostState, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var me by remember { mutableStateOf(Shared.lastName) }
    var fontDlg by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("App settings"), fontWeight = FontWeight.Medium) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(tr("Language"))
            // The names of the languages are shown in their own language, so you can always find yours.
            listOf("en" to "English (UK)", "nl" to "Nederlands").forEach { (k, n) ->
                Row(
                    Modifier.fillMaxWidth().clickable { Lang.set(ctx, k) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = Lang.code == k, onClick = { Lang.set(ctx, k) })
                    Text(n)
                }
            }

            SectionTitle(tr("Your name"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(me, { me = it.take(30) }, singleLine = true, label = { Text(tr("Name")) }, modifier = Modifier.weight(1f))
                Button(enabled = me.isNotBlank(), onClick = {
                    Shared.setAppName(ctx, me)
                    me = Shared.lastName
                    scope.launch { snack.showSnackbar(tr("Name saved")) }
                }) { Text(tr("Save")) }
            }
            Text(
                tr("Shown as “Added by” on the shared wishlists you’re on. You can use a different name on one list in its ⚙."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )

            SectionTitle(tr("Deleting wishes"))
            Row(
                Modifier.fillMaxWidth().clickable { Repo.setInstantDelete(ctx, !Repo.instantDelete) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(tr("Instant delete wishes"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr("Turn on to delete wishes without a warning."),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Switch(checked = Repo.instantDelete, onCheckedChange = { Repo.setInstantDelete(ctx, it) })
            }

            SectionTitle(tr("Theme"))
            listOf(
                "system" to tr("System default"), "light" to tr("Light"),
                "dark" to tr("Dark"), "amoled" to tr("AMOLED black")
            ).forEach { (k, n) ->
                Row(
                    Modifier.fillMaxWidth().clickable { Repo.setTheme(ctx, k) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = Repo.theme == k, onClick = { Repo.setTheme(ctx, k) })
                    Text(n)
                }
            }

            SectionTitle(tr("Font"))
            // The font for the whole app. Each name is shown in its own font.
            val current = fontFor(Repo.font)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { fontDlg = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current.name, fontFamily = current.family, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { fontDlg = true }) { Text(tr("Change")) }
            }

            SectionTitle(tr("Colours"))
            // A row of round swatches. The colours work with every mode above. Wallpaper colours need Android 12.
            val mode = effectiveTheme()
            val choices = (if (wallpaperColours) listOf("wallpaper" to tr("Wallpaper")) else emptyList()) +
                PALETTES.map { it.key to tr(it.name) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                choices.forEach { (k, n) ->
                    val on = Repo.palette == k || (k == "indigo" && Repo.palette == "wallpaper" && !wallpaperColours)
                    val sch = schemeFor(ctx, k, mode)
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { Repo.setPalette(ctx, k) }
                            .semantics { contentDescription = n; selected = on }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(sch.primaryContainer)
                                .border(if (on) 3.dp else 1.dp, if (on) cs.onSurface else cs.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(sch.primary), contentAlignment = Alignment.Center) {
                                if (on) Icon(Icons.Default.Check, null, tint = sch.onPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(n, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            SectionTitle(tr("Price alerts"))
            Text(
                tr("Choose how often the app refreshes the prices of your wishes, to show any price drops or increases."),
                color = cs.onSurfaceVariant
            )
            Text(tr("Check every"), style = MaterialTheme.typography.labelMedium)
            listOf(1, 3, 6, 12, 24).forEach { h ->
                val pick = {
                    Repo.setInterval(ctx, h)
                    Alerts.schedule(ctx, h, true)
                }
                Row(Modifier.fillMaxWidth().clickable { pick() }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Repo.intervalHours == h, onClick = { pick() })
                    Text(if (h == 1) tr("Every hour") else tr("Every %d hours", h))
                }
            }
            Text(
                if (Repo.lastCheck == 0L) tr("No background check has finished yet.")
                else tr("Last check: %s", DateUtils.getRelativeTimeSpanString(Repo.lastCheck)),
                color = cs.onSurfaceVariant
            )
            // Which price changes send a notification. Drops are on unless switched off; increases only when switched on.
            Row(
                Modifier.fillMaxWidth().clickable { Repo.setNotifyDrops(ctx, !Repo.notifyDrops) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("Notifications for price drops"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = Repo.notifyDrops, onCheckedChange = { Repo.setNotifyDrops(ctx, it) })
            }
            Row(
                Modifier.fillMaxWidth().clickable { Repo.setNotifyRises(ctx, !Repo.notifyRises) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("Notifications for price surges"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = Repo.notifyRises, onCheckedChange = { Repo.setNotifyRises(ctx, it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Alerts.checkNow(ctx)
                    scope.launch { snack.showSnackbar(tr("Checking prices…")) }
                }) { Text(tr("Check now")) }
                OutlinedButton(onClick = {
                    val ok = Alerts.sendTest(ctx)
                    scope.launch {
                        snack.showSnackbar(
                            if (ok) tr("Test notification sent")
                            else tr("Notifications are off for Wishlist. Open Notification settings.")
                        )
                    }
                }) { Text(tr("Test notification")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Alerts.openNotificationSettings(ctx) }) { Text(tr("Notification settings")) }
                OutlinedButton(onClick = { Alerts.openAppSettings(ctx) }) { Text(tr("Battery settings")) }
            }
            Text(
                tr("Turn off battery optimisation for Wishlist in Battery settings if your prices aren’t updated automatically."),
                color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )

            SectionTitle(tr("Share the app"))
            Text(
                tr("Share this app through your messaging app of choice."),
                color = cs.onSurfaceVariant
            )
            Button(onClick = {
                scope.launch {
                    try {
                        shareApk(ctx)
                    } catch (e: Exception) {
                        snack.showSnackbar(tr("Couldn’t share the app"))
                    }
                }
            }) { Text(tr("Share app (APK)")) }
        }
    }


    if (fontDlg) {
        AlertDialog(
            onDismissRequest = { fontDlg = false },
            title = { Text(tr("Choose a font")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FONTS.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { Repo.setFont(ctx, f.key); fontDlg = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = Repo.font == f.key, onClick = { Repo.setFont(ctx, f.key); fontDlg = false })
                            Text(
                                if (f.key == "google_sans") tr("%s (default)", f.name) else f.name,
                                fontFamily = f.family, style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fontDlg = false }) { Text(tr("Close")) } }
        )
    }
}
