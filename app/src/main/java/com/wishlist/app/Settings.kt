package com.wishlist.app

import android.net.Uri
import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.Color
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(snack: SnackbarHostState, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var me by remember { mutableStateOf(Shared.lastName) }
    var licences by remember { mutableStateOf(false) }
    // Counts the saves: each one shows a green tick with "Saved" for a moment (SavedFeedback).
    var nameSaved by remember { mutableStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The Themer: its own screen, opened from the Theme section. Back returns here. (The menu button hides meanwhile.)
    if (ThemerNav.open) {
        ThemerScreen { ThemerNav.open = false }
        return
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tr("App settings"), fontWeight = LocalLook.current.titleWeight) },
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
            // No Save button: the keyboard's tick saves the name and closes the keyboard. (An empty name isn't saved:
            // the old one comes back.)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    me, { me = it.take(30) }, singleLine = true, label = { Text(tr("Name")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = {
                        if (me.isNotBlank()) {
                            Shared.setAppName(ctx, me)
                            nameSaved++
                        }
                        me = Shared.lastName
                        keyboard?.hide()
                        focus.clearFocus()
                    }),
                    modifier = Modifier.weight(1f)
                )
                // Always takes its room, so the field keeps the same width when "Saved" shows.
                SavedFeedback(nameSaved)
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
            // Everything about the look is in the Themer: the standard themes (Light, Dark, AMOLED black, each with
            // colour sets) and the four designs. (No "System default": the first start picks Light or Dark to match
            // the phone, see Repo.load.)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        tr("Themes and colours to personalise your wishlist"),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
                Button(onClick = { ThemerNav.open = true }) { Text(tr("Themer")) }
            }

            SectionTitle(tr("Price alerts"))
            Text(
                tr("Choose how often the app refreshes the prices of your wishes, to show any price drops or increases."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            // "Check every" with the choices next to each other, as boxes that finish the sentence.
            Text(tr("Check every"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            val look = LocalLook.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 3, 6, 12, 24).forEach { h ->
                    val on = Repo.intervalHours == h
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .then(
                                if (look.classic) Modifier.clip(shape)
                                    .background(if (on) cs.primaryContainer else Color.Transparent)
                                    .border(1.dp, if (on) cs.primary else cs.outlineVariant, shape)
                                else Modifier.lookChip(look, on)
                            )
                            .selectable(selected = on, role = Role.RadioButton) {
                                Repo.setInterval(ctx, h)
                                Alerts.schedule(ctx, h, true)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (h == 1) tr("Hour") else tr("%d hours", h),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            fontWeight = if (on) FontWeight.Bold else null,
                            color = if (look.classic) (if (on) cs.onPrimaryContainer else cs.onSurfaceVariant) else look.chipContent(on)
                        )
                    }
                }
            }
            Text(
                if (Repo.lastCheck == 0L) tr("No background check has finished yet.")
                else tr("Last check: %s", DateUtils.getRelativeTimeSpanString(Repo.lastCheck)),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            // A sub-heading, as in Android's own settings: the switches below are about notifications.
            Text(tr("Notifications"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            // Drops are on unless switched off; increases only when switched on.
            Row(
                Modifier.fillMaxWidth().clickable { Repo.setNotifyDrops(ctx, !Repo.notifyDrops) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("Price drops"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = Repo.notifyDrops, onCheckedChange = { Repo.setNotifyDrops(ctx, it) })
            }
            Row(
                Modifier.fillMaxWidth().clickable { Repo.setNotifyRises(ctx, !Repo.notifyRises) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("Price surges"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = Repo.notifyRises, onCheckedChange = { Repo.setNotifyRises(ctx, it) })
            }
            // Only while Android may hold back the checks (not Unrestricted): a way to allow it after "Not now".
            // Checked again each time you come back to the app (after Android's question).
            var bgOk by remember { mutableStateOf(Background.allowed(ctx)) }
            val life = LocalLifecycleOwner.current
            DisposableEffect(life) {
                val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) bgOk = Background.allowed(ctx) }
                life.lifecycle.addObserver(obs)
                onDispose { life.lifecycle.removeObserver(obs) }
            }
            if (!bgOk) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(tr("Checks while your phone sleeps"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("Off: price alerts may wait until you open the app."),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { Background.request(ctx) }) { Text(tr("Allow")) }
                }
            }

            SectionTitle(tr("Update the app"))
            val version = remember {
                try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
            }
            Text(
                tr("You have version %s. Download the newest version and install it over this one: your wishes stay.", version),
                color = cs.onSurfaceVariant
            )
            // The browser downloads it straight away; open the download to install it.
            Button(onClick = {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    scope.launch { snack.showSnackbar(tr("Couldn’t open the download. Check your connection.")) }
                }
            }) { Text(tr("Download app")) }

            SectionTitle(tr("About"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("The icons, fonts and building blocks in Wishlist, and their licences."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                OutlinedButton(onClick = { licences = true }) { Text(tr("Licences")) }
            }

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
    if (licences) LicencesDialog { licences = false }
}
