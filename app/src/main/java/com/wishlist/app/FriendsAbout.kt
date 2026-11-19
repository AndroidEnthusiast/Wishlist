package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Your birthday and "About me", and friends' birthday reminders (a part of [Friends] since the data rebuild, owner 19 November 2026). */
internal object FriendAbout {
    /** Your birthday as "MM-DD", or "". */
    var myBirthday by mutableStateOf("")
        internal set

    /** Your "About me" line (was "I like…"; at most [BIO_MAX] letters, shown under your name). */
    var myBio by mutableStateOf("")
        internal set
}

// ---- what you can do with it (callers keep writing Friends.…, as before the rebuild) ----

/** Saves your birthday and "I like…" on the phone, then online (an error is thrown when offline: saved next time). */
suspend fun Friends.setAbout(c: Context, birthday: String, bio: String) {
    FriendAbout.myBirthday = birthday
    FriendAbout.myBio = bio.trim().take(BIO_MAX)
    // "Not sent yet" until the server has it: an offline save goes online with the next refresh ([ensure]).
    prefs(c).edit().putString("pf_bday", myBirthday).putString("pf_bio", myBio).putBoolean("pf_about_dirty", true).apply()
    ensure(c)
    pushAbout(c)
}

internal suspend fun Friends.pushAbout(c: Context) {
    Cloud.rpc("wl_pf_about", base(c).put("p_birthday", myBirthday).put("p_bio", myBio))
    prefs(c).edit().putBoolean("pf_about_dirty", false).apply()
}

/** Days until the next birthday "MM-DD" (0 = today), or null without one. */
fun Friends.daysUntil(birthday: String, today: java.time.LocalDate = java.time.LocalDate.now()): Int? {
    val m = Regex("^(\\d{2})-(\\d{2})$").find(birthday) ?: return null
    val month = m.groupValues[1].toInt()
    val day = m.groupValues[2].toInt()
    fun on(year: Int): java.time.LocalDate? = try {
        // 29 February in a year without it: the 28th.
        java.time.LocalDate.of(year, month, if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) 28 else day)
    } catch (e: Exception) {
        null
    }
    var next = on(today.year) ?: return null
    if (next.isBefore(today)) next = on(today.year + 1) ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
}

/**
 * Birthday reminders (owner, 2.95): 14 days before a friend's birthday, and again 7 days before only when you
 * haven't reserved anything on their lists yet. Each once a year. Runs with the background check.
 */
internal suspend fun Friends.birthdays(c: Context, s: FriendsState) {
    val p = prefs(c)
    val year = java.time.LocalDate.now().year
    for (f in s.friends) {
        val d = daysUntil(f.birthday) ?: continue
        val key14 = "bd14_${f.id}_$year"
        val key7 = "bd7_${f.id}_$year"
        if (d in 8..14 && !p.getBoolean(key14, false)) {
            Alerts.notifyBirthday(c, f.id, f.name, d)
            Notifications.add(c, "birthday", "p:${f.id}", f.name, photos[f.id] ?: "", amount = d.toDouble())
            p.edit().putBoolean(key14, true).apply()
        } else if (d in 1..7 && !p.getBoolean(key7, false)) {
            p.edit().putBoolean(key7, true).putBoolean(key14, true).apply()
            // Only when nothing on their lists is reserved by this phone.
            val reserved = f.lists.any { l ->
                try {
                    Cloud.fetchReservations(l.code, Repo.deviceId).values.any { it }
                } catch (e: Exception) {
                    true // can't tell: better no reminder than a wrong one
                }
            }
            if (!reserved) {
                Alerts.notifyBirthday(c, f.id, f.name, d)
                Notifications.add(c, "birthday", "p:${f.id}", f.name, photos[f.id] ?: "", amount = d.toDouble())
            }
        }
    }
}
