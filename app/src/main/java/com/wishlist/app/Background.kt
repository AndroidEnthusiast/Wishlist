package com.wishlist.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Price checks while the phone sleeps. Android holds back background work of apps that are "optimised" for battery
 * (and Samsung puts unused apps to sleep), so price alerts could wait until the app was opened. An app cannot make
 * itself Unrestricted; it can only show Android's own question "Let Wishlist always run in the background?", and
 * Allow there makes it Unrestricted.
 */
object Background {
    private const val ASKED = "asked_background"

    /** True when Wishlist may run in the background without battery limits (Unrestricted). */
    fun allowed(c: Context): Boolean = try {
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
    } catch (e: Exception) {
        true
    }

    /** Opens Android's question; if a phone doesn't have it, the app's own settings page (Battery is there). */
    fun request(c: Context) {
        try {
            c.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            try {
                c.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
            }
        }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    fun asked(c: Context): Boolean = prefs(c).getBoolean(ASKED, false)
    fun markAsked(c: Context) = prefs(c).edit().putBoolean(ASKED, true).apply()
}

/** The one-time question, shown once when the app starts (after the notifications question), unless already allowed. */
@Composable
fun BackgroundQuestion() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(!Background.asked(ctx) && !Background.allowed(ctx)) }
    if (!show) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Allow price checks in the background?")) },
        text = {
            Text(
                tr("Then Wishlist can check prices while your phone is asleep, so price alerts arrive on time instead of when you open the app. Android asks you to confirm once.")
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Background.markAsked(ctx)
                show = false
                Background.request(ctx)
            }) { Text(tr("Allow")) }
        },
        dismissButton = {
            TextButton(onClick = {
                Background.markAsked(ctx)
                show = false
            }) { Text(tr("Not now")) }
        }
    )
}
