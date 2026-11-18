@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wishlist.app

import android.net.Uri
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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
    if (t.isBlank()) return
    // Quiet (2.207): a small grey title above each white block, like the grouped settings of the phone itself.
    val quiet = LocalLook.current.design == "quiet"
    Text(
        t, style = if (quiet) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
        color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, start = if (quiet) 4.dp else 0.dp)
    )
}

/**
 * The settings of one section: in Quiet (2.207) together on one white block (dark: a card grey); in Gen Z chaos on a
 * solid card too (owner, 16 November 2026: the big coloured circles behind the text made settings hard to read).
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val look = LocalLook.current
    // Gen Z chaos: the title goes on the card too, so no text sits on the circles.
    if (look.design != "quiet") { ReadingCard(heading = { SectionTitle(title) }, content = content); return }
    SectionTitle(title)
    Column(
        Modifier.fillMaxWidth().lookCard(look, 3L, look.cardShape(3L, small = true)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

/**
 * A solid card behind settings in Gen Z chaos (owner, 16 November 2026), so the circles and dots never sit behind
 * text; not tilted, so fields and switches stay straight. In other styles just the column. Used on every settings
 * screen (App settings, a list's settings, Profile); `ReadingCardTest` checks they all use it.
 */
@Composable
fun ReadingCard(
    heading: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val look = LocalLook.current
    // Other styles: the heading above, as before. Chaos: the heading on the card too, so no text sits on the circles.
    if (look.design != "chaos") heading?.invoke()
    Column(
        if (look.design == "chaos") Modifier.padding(top = 8.dp, bottom = 6.dp).fillMaxWidth()
            .lookSurface(look, look.cardShape(3L, small = true), look.cardFill(3L), depth = 6.dp)
            .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 14.dp)
        else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (look.design == "chaos") heading?.invoke()
        content()
    }
}

/**
 * App settings (owner, 18 November 2026: "insanely long and bloated with copy"; docs/decisions/screens.md, last
 * section): one short list of rows, each with its icon in a soft tile, its current value in grey and ›, each opening
 * its own small page. No explanation lines unless a label is unclear. The Theme lives in the profile sheet (its own
 * route, [Route.THEME]); what belongs to one list is in [ListSettingsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(snack: SnackbarHostState) {
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    Moving(page, depth = { if (it.isEmpty()) 0 else 1 }) { p ->
        if (p.isEmpty()) SettingsMain(snack) { page = it }
        else SettingsPage(p, snack) { page = "" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMain(snack: SnackbarHostState, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("App settings")) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val blocked = remember { Alerts.blockedImportant(ctx) }
            ListVisibility.loadDefault(ctx)
            SettingsGroup("") {
                NavRow({ Icon(AppIcons.Notifications, null) }, tr("Notifications"), if (blocked.isNotEmpty()) tr("Blocked") else null, warn = blocked.isNotEmpty()) { onOpen("notes") }
                NavRow({ Icon(AppIcons.Bag, null) }, tr("Prices"), intervalLabel(Repo.intervalHours) + " · " + currencySymbol(CurrencyChoice.chosen.ifEmpty { countryCurrency() })) { onOpen("prices") }
                if (Config.enabled) NavRow({ Icon(AppIcons.Lock, null) }, tr("New wishlists"), whoLabel(ListVisibility.newDefault)) { onOpen("lists") }
                NavRow({ Icon(AppIcons.ListIcon, null) }, tr("Display"), null) { onOpen("display") }
                NavRow({ Icon(AppIcons.Refresh, null) }, tr("Backup"), null) { onOpen("backup") }
                NavRow({ Icon(AppIcons.Gift, null) }, tr("About"), appVersion(ctx)) { onOpen("about") }
            }
        }
    }
}

/** "Every 3 hours", "Every hour". */
private fun intervalLabel(h: Int): String = if (h == 1) tr("Every hour") else tr("Every %d hours", h)

private fun whoLabel(w: Who): String = when (w) {
    Who.ME -> tr("Only me"); Who.FRIENDS -> tr("My friends"); Who.LINK -> tr("Friends + link")
}

private fun appVersion(c: android.content.Context): String =
    try { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }

/**
 * A row that opens a page: the icon in a soft tile (like the + menu, [SheetRow]), the name, the current value in grey
 * and ›. 56 dp tall.
 */
@Composable
private fun NavRow(icon: @Composable () -> Unit, label: String, value: String?, warn: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = look.scheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (look.design == "quiet") {
            Box(
                Modifier.size(32.dp).clip(Squircle(9.dp)).background(cs.primaryContainer)
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent { drawContent(); drawRect(cs.onPrimaryContainer, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop) },
                contentAlignment = Alignment.Center
            ) { Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() } }
        } else Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (value != null) Text(
            value, style = MaterialTheme.typography.bodyMedium, color = if (warn) cs.error else cs.onSurfaceVariant,
            maxLines = 1, modifier = Modifier.padding(start = 8.dp)
        )
        Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

/** One page behind a row of App settings, with its own back arrow (the phone's Back does the same). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(page: String, snack: SnackbarHostState, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    val title = when (page) {
        "notes" -> tr("Notifications"); "prices" -> tr("Prices"); "lists" -> tr("New wishlists")
        "display" -> tr("Display"); "backup" -> tr("Backup"); else -> tr("About")
    }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (page) {
                "notes" -> NotificationsPage()
                "prices" -> PricesPage()
                "lists" -> SettingsGroup("") { NewListVisibilitySetting() }
                "display" -> SettingsGroup("") {
                    // The only "What you see" switch left (owner, 18 November 2026): price labels on photos and the
                    // descriptions on the large cards are always shown now.
                    SettingSwitch(tr("List summary"), tr("Wishes and total value at the top of a list."), Show["show_summary"]) { Show.set(ctx, "show_summary", it) }
                }
                "backup" -> SettingsGroup("") { BackupSection(snack) }
                else -> AboutPage(snack)
            }
        }
    }
}

/** Every notification you may switch off, the price alerts included (owner, 18 November 2026: one place). */
@Composable
private fun NotificationsPage() {
    val ctx = LocalContext.current
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
        SettingSwitch(tr("Friend requests"), null, Show["nt_req"]) { Show.set(ctx, "nt_req", it) }
        SettingSwitch(tr("Friends’ birthdays"), null, Show["nt_bday"]) { Show.set(ctx, "nt_bday", it) }
        SettingSwitch(tr("New messages"), null, Show["nt_msg"]) { Show.set(ctx, "nt_msg", it) }
        SettingSwitch(tr("Reactions"), tr("Once a day, in the evening"), Show["nt_react"]) { Show.set(ctx, "nt_react", it) }
        SettingSwitch(tr("Price drops"), null, Repo.notifyDrops) { Repo.setNotifyDrops(ctx, it) }
        SettingSwitch(tr("Price increases"), null, Repo.notifyRises) { Repo.setNotifyRises(ctx, it) }
    }
}

/** How often prices are checked (one row and a small list, owner: the chips took too much room) and the currency. */
@Composable
private fun PricesPage() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    SettingsGroup("") {
        ValueRow(tr("Check prices"), intervalLabel(Repo.intervalHours)) { picking = true }
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
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text(tr("Check prices")) },
        text = {
            Column {
                listOf(1, 3, 6, 12, 24).forEach { h ->
                    val on = Repo.intervalHours == h
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .selectable(selected = on, role = Role.RadioButton) {
                                Repo.setInterval(ctx, h)
                                Alerts.schedule(ctx, h, true)
                                picking = false
                            }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(intervalLabel(h), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                        if (on) Icon(AppIcons.Check, tr("Chosen"), tint = cs.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text(tr("Close")) } }
    )
}

/** A setting's name with its value in grey and ›; tapping it opens its choices. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1)
        Icon(AppIcons.Chevron, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

/**
 * About (owner, 18 November 2026): the version; Download only when a newer version exists; Share app; Licences;
 * Privacy (the crash reports are explained on the privacy page). A long press on the version shows the server calls
 * (Calls.kt), for checking problems.
 */
@Composable
private fun AboutPage(snack: SnackbarHostState) {
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
        ValueRow(tr("Share app"), "") {
            scope.launch { try { shareApk(ctx) } catch (e: Exception) { snack.showToast(tr("Couldn’t share the app")) } }
        }
        ValueRow(tr("Licences"), "") { licences = true }
        ValueRow(tr("Privacy"), "") { openPrivacy(ctx) }
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
private fun SettingSwitch(title: String, about: String?, on: Boolean, onChange: (Boolean) -> Unit) {
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
                DropdownMenuItem(text = { Text(tr("Automatic (%s)", countryCurrency())) }, onClick = { open = false; CurrencyChoice.set(ctx, "") })
                CurrencyChoice.all.forEach { cur ->
                    DropdownMenuItem(text = { Text("${currencySymbol(cur)}  $cur") }, onClick = { open = false; CurrencyChoice.set(ctx, cur) })
                }
            }
        }
    }
}
