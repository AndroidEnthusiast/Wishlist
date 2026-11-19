package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * App settings' state holder (the rebuild, owner 19 November 2026; MAP "How a screen is built"): everything a switch
 * or a choice in App settings changes. The pages (Settings.kt, SettingsPages.kt) only draw; the settings themselves
 * are kept by Show (what is shown and which notifications come), Repo (price alerts, how often) and CurrencyChoice.
 */
@Stable
internal class SettingsModel(private val ctx: Context) {
    /** A switch kept by Show: "show_summary", "home_summary", "home_swipe", "nt_req", "nt_bday", "nt_msg", "nt_react". */
    fun setShown(key: String, on: Boolean) = Show.set(ctx, key, on)
    fun setPriceDrops(on: Boolean) = Repo.setNotifyDrops(ctx, on)
    fun setPriceRises(on: Boolean) = Repo.setNotifyRises(ctx, on)
    /** How often prices are checked: the background check is planned again at once. */
    fun setInterval(hours: Int) { Repo.setInterval(ctx, hours); Alerts.schedule(ctx, hours, true) }
    /** The currency for new wishes ("" = automatic). */
    fun setCurrency(cur: String) = CurrencyChoice.set(ctx, cur)
}

/** App settings' state holder, one per page. */
@Composable
internal fun rememberSettings(): SettingsModel {
    val ctx = LocalContext.current
    return remember { SettingsModel(ctx) }
}
