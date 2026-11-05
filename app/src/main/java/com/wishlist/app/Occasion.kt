package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * Occasions (owner, 5 November 2026; see DECISIONS "Occasions"). A list can have one occasion: Birthday, Christmas or
 * Sinterklaas (own lists), Birthday, Valentine's Day, Mother's Day, Father's Day or Anniversary (gift lists), or one of
 * your own with its own name. Friends and everyone on a gift list see "Birthday · in 3 weeks" at the top of the list.
 *  - Stored as {"k": kind, "n": own name, "d": date}: "MM-DD" for Birthday and Anniversary, "YYYY-MM-DD" for your own
 *    occasion, nothing for the ones whose date fills itself (Dutch dates: Mother's Day is the 2nd Sunday of May,
 *    Father's Day the 3rd Sunday of June).
 *  - Yearly occasions move on to next year by themselves; your own occasion disappears the day after its date.
 *  - Own lists keep it on the phone ("occ_m:<id>") and send it online in the list's "about" item (Cloud.pushList), so
 *    friends see it; gift lists keep it online only (the "about" item, wl_sh_set_occasion in supabase-shared.sql).
 *  - The same rules are in web/occasion.js (friends' page and browser editor). Change both together.
 */

data class Occasion(val kind: String, val name: String = "", val date: String = "") {
    fun toJson(): JSONObject = JSONObject().put("k", kind).apply {
        if (name.isNotEmpty()) put("n", name)
        if (date.isNotEmpty()) put("d", date)
    }

    companion object {
        fun fromJson(o: JSONObject?): Occasion? {
            if (o == null) return null
            val k = o.optString("k", "")
            if (k.isEmpty()) return null
            return Occasion(k, o.optString("n", "").take(Occasions.NAME_MAX), o.optString("d", ""))
        }

        fun parse(s: String?): Occasion? = try {
            if (s.isNullOrBlank()) null else fromJson(JSONObject(s))
        } catch (e: Exception) {
            null
        }
    }
}

object Occasions {
    const val NAME_MAX = 40
    val OWN_KINDS = listOf("birthday", "christmas", "sinterklaas", "own")
    val GIFT_KINDS = listOf("birthday", "valentine", "mother", "father", "anniversary", "own")

    /** Whether the date is asked after picking [kind] (the others fill themselves). */
    fun asksDate(kind: String) = kind == "birthday" || kind == "anniversary" || kind == "own"

    fun label(o: Occasion): String = when (o.kind) {
        "birthday" -> tr("Birthday")
        "christmas" -> tr("Christmas")
        "sinterklaas" -> tr("Sinterklaas")
        "valentine" -> tr("Valentine’s Day")
        "mother" -> tr("Mother’s Day")
        "father" -> tr("Father’s Day")
        "anniversary" -> tr("Anniversary")
        else -> o.name.ifBlank { tr("Occasion") }
    }

    /** The label of a choice in the picker ("Your own…" for an own occasion). */
    fun choiceLabel(kind: String): String = if (kind == "own") tr("Your own…") else label(Occasion(kind))

    private fun nthSunday(year: Int, month: Int, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.SUNDAY))

    /** [month]-[day] in [year]; 29 February becomes the 28th in other years. */
    private fun dayIn(year: Int, month: Int, day: Int): LocalDate =
        LocalDate.of(year, month, minOf(day, java.time.YearMonth.of(year, month).lengthOfMonth()))

    private fun monthDay(d: String): Pair<Int, Int>? {
        val m = Regex("^(?:\\d{4}-)?(\\d{2})-(\\d{2})$").find(d) ?: return null
        val mo = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        return if (mo in 1..12 && day in 1..31) mo to day else null
    }

    /** The day the occasion is next (today counts), or null when it has passed (your own) or has no date. */
    fun next(o: Occasion, today: LocalDate = LocalDate.now()): LocalDate? {
        fun yearly(f: (Int) -> LocalDate): LocalDate = f(today.year).let { if (it.isBefore(today)) f(today.year + 1) else it }
        return when (o.kind) {
            "christmas" -> yearly { dayIn(it, 12, 25) }
            "sinterklaas" -> yearly { dayIn(it, 12, 5) }
            "valentine" -> yearly { dayIn(it, 2, 14) }
            "mother" -> yearly { nthSunday(it, 5, 2) }
            "father" -> yearly { nthSunday(it, 6, 3) }
            "birthday", "anniversary" -> monthDay(o.date)?.let { (m, d) -> yearly { y -> dayIn(y, m, d) } }
            "own" -> try {
                LocalDate.parse(o.date).takeIf { !it.isBefore(today) }
            } catch (e: Exception) {
                null
            }
            else -> null
        }
    }

    /** "today", "tomorrow", "in 5 days" (under 2 weeks), "in 3 weeks" (under 9 weeks), "in 4 months". */
    fun countdown(days: Long): String = when {
        days <= 0L -> tr("today")
        days == 1L -> tr("tomorrow")
        days < 14L -> tr("in %d days", days.toInt())
        days < 63L -> tr("in %d weeks", ((days + 3) / 7).toInt())
        else -> tr("in %d months", Math.round(days / 30.44).toInt())
    }

    /** "Birthday · in 3 weeks", or null when there is nothing to show. */
    fun line(o: Occasion?, today: LocalDate = LocalDate.now()): String? {
        if (o == null) return null
        val d = next(o, today) ?: return null
        return label(o) + " · " + countdown(ChronoUnit.DAYS.between(today, d))
    }

    /** The date as people read it: "14 July" (yearly) or "14 July 2027" (your own). */
    fun dateText(o: Occasion): String {
        val d = next(o) ?: return ""
        val locale = if (Lang.code == "nl") java.util.Locale("nl") else java.util.Locale.UK
        val base = d.dayOfMonth.toString() + " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        return if (o.kind == "own") base + " " + d.year else base
    }

    /** "MM-DD" for the picker from a stored date. */
    fun pickerValue(o: Occasion?): String = o?.date?.let { monthDay(it) }?.let { (m, d) -> "%02d-%02d".format(m, d) } ?: ""

    /** An own occasion's day and month become the next time that date comes (today counts). */
    fun ownDate(mmdd: String, today: LocalDate = LocalDate.now()): String {
        val (m, d) = monthDay(mmdd) ?: return ""
        val t = dayIn(today.year, m, d)
        return (if (t.isBefore(today)) dayIn(today.year + 1, m, d) else t).toString()
    }

    /** Complete enough to save: a date where one is asked, a name for your own. */
    fun complete(o: Occasion?): Boolean = when {
        o == null -> true
        o.kind == "own" -> o.name.isNotBlank() && next(o) != null
        asksDate(o.kind) -> monthDay(o.date) != null
        else -> true
    }

    // ---- your own lists: kept on the phone, sent online with the list ----

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Goes up on every change, so screens show the new occasion. */
    var version by mutableIntStateOf(0)
        private set

    /** The occasion of your own list [id]. */
    fun ofMine(c: Context, id: String): Occasion? {
        @Suppress("UNUSED_VARIABLE") val v = version
        return Occasion.parse(prefs(c).getString("occ_m:$id", null))
    }

    /** The occasion of the list that is open (your own, or a gift list's from its online copy). */
    fun ofOpen(c: Context): Occasion? = if (Shared.active != null) Shared.content.occ else ofMine(c, Repo.openMine)

    /** Sets (or with null removes) the occasion of the list that is open. */
    fun setOpen(c: Context, o: Occasion?) {
        if (Shared.active != null) {
            Shared.setOccasion(c, o)
            return
        }
        setMine(c, Repo.openMine, o)
    }

    fun setMine(c: Context, id: String, o: Occasion?) {
        val e = prefs(c).edit()
        if (o == null) e.remove("occ_m:$id") else e.putString("occ_m:$id", o.toJson().toString())
        e.apply()
        version++
        // Friends see it: the list goes online again (when it is online).
        if (id == Repo.openMine) Sync.push()
        else Repo.mine(c, id).takeIf { it.code != null }?.let { Sync.pushStored(c, it, Repo.binnedOf(id)) }
    }

    /** Your list [id] was deleted. */
    fun forget(c: Context, id: String) {
        prefs(c).edit().remove("occ_m:$id").apply()
        version++
    }

    /** Your list [from] now lives under the id [to] (see Repo.deleteMine). */
    fun move(c: Context, from: String, to: String) {
        val p = prefs(c)
        val t = p.getString("occ_m:$from", null) ?: return
        p.edit().putString("occ_m:$to", t).remove("occ_m:$from").apply()
        version++
    }
}

/**
 * Picking an occasion (owner: pick one or type your own; only after picking, the date is asked, never both at once).
 * [gift]: the choices of a gift list. [value] null = none. [onChange] gets the new choice (maybe not complete yet:
 * see [Occasions.complete]).
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OccasionPicker(gift: Boolean, value: Occasion?, onChange: (Occasion?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            (if (gift) Occasions.GIFT_KINDS else Occasions.OWN_KINDS).forEach { k ->
                FilterChip(
                    selected = value?.kind == k,
                    onClick = {
                        if (value?.kind == k) onChange(null)
                        else {
                            // Your own birthday fills in from your profile (owner).
                            val d = if (k == "birthday" && !gift) Friends.myBirthday else ""
                            onChange(Occasion(k, date = d))
                        }
                    },
                    label = { Text(Occasions.choiceLabel(k)) }
                )
            }
        }
        if (value != null) {
            if (value.kind == "own") {
                OutlinedTextField(
                    value.name, { onChange(value.copy(name = it.take(Occasions.NAME_MAX))) },
                    label = { Text(tr("Occasion")) }, placeholder = { Text(tr("Wedding")) }, singleLine = true,
                    keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                )
            }
            if (Occasions.asksDate(value.kind)) {
                DayMonthPicker(Occasions.pickerValue(value), removable = false) { mmdd ->
                    onChange(value.copy(date = if (value.kind == "own") Occasions.ownDate(mmdd) else mmdd))
                }
            } else {
                Text(Occasions.dateText(value), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** List settings > Occasion: the same choices, saved with Save; "Remove" takes it off. */
@Composable
fun OccasionDialog(gift: Boolean, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val start = remember { Occasions.ofOpen(ctx) }
    var o by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Occasion")) },
        text = { OccasionPicker(gift, o) { o = it } },
        confirmButton = {
            TextButton(enabled = Occasions.complete(o), onClick = {
                if (o != start) Occasions.setOpen(ctx, o)
                onClose()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}
