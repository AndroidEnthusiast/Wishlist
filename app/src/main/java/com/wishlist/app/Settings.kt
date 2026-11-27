package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
internal fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
                ValueRow(icon = { Icon(AppIcons.Notifications, null) }, label = tr("Notifications"), value = if (blocked.isNotEmpty()) tr("Blocked") else null, warn = blocked.isNotEmpty()) { onOpen("notes") }
                ValueRow(icon = { Icon(AppIcons.Bag, null) }, label = tr("Prices"), value = intervalLabel(Repo.intervalHours) + " · " + currencySymbol(CurrencyChoice.chosen.ifEmpty { countryCurrency() })) { onOpen("prices") }
                if (Config.enabled) ValueRow(icon = { Icon(AppIcons.Lock, null) }, label = tr("New wishlists"), value = whoLabel(ListVisibility.newDefault)) { onOpen("lists") }
                ValueRow(icon = { Icon(AppIcons.ListIcon, null) }, label = tr("Display"), value = null) { onOpen("display") }
                ValueRow(icon = { Icon(AppIcons.Gift, null) }, label = tr("About"), value = appVersion(ctx)) { onOpen("about") }
            }
            // One-tap delete (owner, 27 November 2026): also turned on from "Delete wish?", whose message points here.
            val model = rememberSettings()
            Show.load(ctx)
            SettingsGroup("") {
                SettingSwitch(tr("One-tap delete"), tr("Delete wishes without asking first. Undo is still there."), Show[DeleteFlow.ONE_TAP]) { model.setOneTap(it) }
            }
        }
    }
}

/** "Every 3 hours", "Every hour". */
internal fun intervalLabel(h: Int): String = if (h == 1) tr("Every hour") else tr("Every %d hours", h)

private fun whoLabel(w: Who): String = when (w) {
    Who.ME -> tr("Only me"); Who.FRIENDS -> tr("My friends"); Who.LINK -> tr("Friends + link")
}

internal fun appVersion(c: android.content.Context): String =
    try { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }

/** One page behind a row of App settings, with its own back arrow (the phone's Back does the same). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(page: String, snack: SnackbarHostState, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val model = rememberSettings()
    androidx.activity.compose.BackHandler(onBack = onBack)
    val title = when (page) {
        "notes" -> tr("Notifications"); "prices" -> tr("Prices"); "lists" -> tr("New wishlists")
        "display" -> tr("Display"); else -> tr("About")
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
                    SettingSwitch(tr("List summary"), tr("Wishes and total value at the top of a list."), Show["show_summary"]) { model.setShown("show_summary", it) }
                    // Home (owner, 18 November 2026): the three blocks next to your photo, and the tabs (Personal | Co-op | Groups | Exchanges since 21 November 2026).
                    SettingSwitch(tr("Home summary"), tr("Wishes, wishlists and total value next to your photo."), Show["home_summary"]) { model.setShown("home_summary", it) }
                    SettingSwitch(tr("Tabs on Home"), tr("Personal, Co-op, Groups and Exchanges: swipe between them."), Show["home_swipe"]) { model.setShown("home_swipe", it) }
                    // Value levels (owner, 27 November 2026): only on your own Total value; on at first.
                    SettingSwitch(tr("Value levels"), tr("A sticker on Total value as your wishes add up."), Show[ValueLevels.SHOW]) { model.setShown(ValueLevels.SHOW, it) }
                }
                else -> AboutPage(snack)
            }
        }
    }
}
