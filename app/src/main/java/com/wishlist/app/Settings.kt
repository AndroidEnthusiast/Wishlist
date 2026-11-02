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
    var licences by remember { mutableStateOf(false) }
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
        snackbarHost = { AppSnackbarHost(snack) }
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

            // No "Instant delete wishes" any more (owner, 2.95): deleting never asks, the bin and Undo are enough.

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
            // What you see (owner, 2.101): for all your lists, only on this phone. Everything on by default.
            SectionTitle(tr("What you see"))
            SettingSwitch(tr("Price change on pictures"), tr("The −20% or +5% label on a wish’s picture."), Show["show_badge"]) { Show.set(ctx, "show_badge", it) }
            SettingSwitch(tr("Descriptions"), tr("The shop’s description on the large cards."), Show["show_desc"]) { Show.set(ctx, "show_desc", it) }
            SettingSwitch(tr("List summary"), tr("The number of wishes and the total value at the top of a list."), Show["show_summary"]) { Show.set(ctx, "show_summary", it) }

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
                            .lookChip(look, on)
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
                            color = look.chipContent(on)
                        )
                    }
                }
            }
            Text(
                if (Repo.lastCheck == 0L) tr("No background check has finished yet.")
                else tr("Last check: %s", DateUtils.getRelativeTimeSpanString(Repo.lastCheck)),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
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

            // Every kind of notification the app sends, each with its switch (owner, 2.101: "this helps me too to
            // remember what notifications are being sent"). Off ones are also left off the Notifications page.
            SectionTitle(tr("Notifications"))
            SettingSwitch(tr("Price drops"), tr("A wish got cheaper."), Repo.notifyDrops) { Repo.setNotifyDrops(ctx, it) }
            SettingSwitch(tr("Price increases"), tr("A wish got more expensive."), Repo.notifyRises) { Repo.setNotifyRises(ctx, it) }
            SettingSwitch(tr("Reserved gift moved to the bin"), tr("Someone moved a gift you reserved to the bin."), Show["nt_bin"]) { Show.set(ctx, "nt_bin", it) }
            SettingSwitch(tr("Reserved gift taken off a list"), tr("A gift you reserved is no longer on a friend’s list."), Show["nt_gone"]) { Show.set(ctx, "nt_gone", it) }
            SettingSwitch(tr("Friend requests"), tr("Someone wants to be your friend."), Show["nt_req"]) { Show.set(ctx, "nt_req", it) }
            SettingSwitch(tr("Friend request accepted"), tr("A friend approved your request."), Show["nt_ok"]) { Show.set(ctx, "nt_ok", it) }
            SettingSwitch(tr("Friends’ birthdays"), tr("A few days before a friend’s birthday."), Show["nt_bday"]) { Show.set(ctx, "nt_bday", it) }
            SettingSwitch(tr("New messages"), tr("A friend sent you a message."), Show["nt_msg"]) { Show.set(ctx, "nt_msg", it) }

            // Export and import, to move to a new phone (owner, 2.95).
            SectionTitle(tr("Backup"))
            BackupSection(snack)

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
                    scope.launch { snack.showToast(tr("Couldn’t open the download. Check your connection.")) }
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
                        snack.showToast(tr("Couldn’t share the app"))
                    }
                }
            }) { Text(tr("Share app (APK)")) }
        }
    }
    if (licences) LicencesDialog { licences = false }
}

/** One switch in App settings: a title, one quiet line about it, and the switch (the whole row can be tapped). */
@Composable
private fun SettingSwitch(title: String, about: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
