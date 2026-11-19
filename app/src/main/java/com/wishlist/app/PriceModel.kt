package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * The state holders of a wish's price page (the rebuild, owner 19 November 2026; MAP "How a screen is built"):
 * [PriceDialogState] for the page ("Price is wrong?" and when the shop was last read) and [TargetState] for its price
 * alert. The page (PriceHistory.kt, PriceChart.kt) only draws; the history is kept by History, alerts by Targets.
 */
@Stable
internal class PriceDialogState(private val ctx: Context, private val w: Wish, private val scope: CoroutineScope) {
    // "Checked 2 hours ago" (owner, 10 November 2026; PriceWatch), or null for a wish without a link.
    var checkedLine by mutableStateOf<String?>(null)
    var askReport by mutableStateOf(false)
    var reporting by mutableStateOf(false)
    var reportErr by mutableStateOf("")

    suspend fun loadChecked() {
        checkedLine = if (w.url.isBlank()) null else PriceWatch.line(PriceWatch.get(ctx, w.id), System.currentTimeMillis())
    }

    /** "Price is wrong?" (PriceReport.kt): the shop's page saved to a file to share; nothing is sent by itself. */
    fun report() {
        askReport = false; reporting = true; reportErr = ""
        scope.launch {
            val f = PriceReport.make(ctx, w)
            reporting = false
            if (f == null || !PriceReport.share(ctx, f)) reportErr = tr("Couldn’t save the shop’s page. Check your connection.")
        }
    }
}

/** A wish's price alert: the amount being typed, its error, and saving or removing it. */
@Stable
internal class TargetState(private val ctx: Context, private val w: Wish) {
    var text by mutableStateOf(Targets.all[w.id]?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "")
    // Goes up after each save (the green tick).
    var saves by mutableStateOf(0)
    var err by mutableStateOf<String?>(null)
    // Editing (the field with its frame) until a target is saved; then the amount in bold, without a frame.
    var editing by mutableStateOf(Targets.all[w.id] == null)

    /** Saves the typed amount. True when it was saved (the keyboard can close). */
    fun save(): Boolean {
        val v = text.replace(',', '.').trim().toDoubleOrNull()
        val now = w.price ?: return false
        err = targetError(v, now, w.currency)
        if (err != null || v == null) return false
        Targets.set(ctx, w, v)
        text = String.format(Locale.ROOT, "%.2f", v)
        saves++
        return true
    }

    /** ✕: the alert goes and the field is back. */
    fun remove() { Targets.set(ctx, w, null); text = ""; err = null; editing = true }
}

/** What is wrong with a price alert of [v] for a price of [now], or null: it has to be an amount below the price now. */
internal fun targetError(v: Double?, now: Double, currency: String): String? = when {
    v == null || v <= 0 -> tr("Fill in an amount.")
    v >= now - 0.005 -> tr("Choose an amount below the price now (%s).", money(now, currency))
    else -> null
}
