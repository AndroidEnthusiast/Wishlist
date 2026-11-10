package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update notice's rules (AppInfo.kt, ROADMAP R6): when the bar shows, when a version is too old, a broken file. */
class AppInfoTest {
    private val day = AppInfo.DAY
    private val out = 1_000_000L // the newest version came out at this second
    private val info = AppInfo.Info(latest = 181, name = "2.180", since = out, min = 170, notice = "")
    private val now = out * 1000 + 2 * day

    @Test fun theBarShowsForANewerVersionThatIsADayOld() {
        assertTrue(AppInfo.showBar(180, info, now, 0L))
        // Not on the newest version, and not for a version that came out an hour ago (several a day while building).
        assertFalse(AppInfo.showBar(181, info, now, 0L))
        assertFalse(AppInfo.showBar(180, info, out * 1000 + day / 24, 0L))
        // Nothing known, or the app can't tell its own version: nothing.
        assertFalse(AppInfo.showBar(180, null, now, 0L))
        assertFalse(AppInfo.showBar(0, info, now, 0L))
    }

    @Test fun laterHidesItForAWeek() {
        assertFalse(AppInfo.showBar(180, info, now, now + 7 * day))
        assertTrue(AppInfo.showBar(180, info, now + 7 * day, now + 7 * day))
    }

    @Test fun tooOldOnlyBelowTheMinimum() {
        assertTrue(AppInfo.tooOld(169, info))
        assertFalse(AppInfo.tooOld(170, info))
        assertFalse(AppInfo.tooOld(0, info))
        assertFalse(AppInfo.tooOld(169, null))
    }

    @Test fun theFileIsRead() {
        val i = AppInfo.parse("""{"latest": 181, "name": "2.180", "since": 1000000, "min": 170, "notice": "Hello"}""")!!
        assertEquals(info.copy(notice = "Hello"), i)
        // Only "min" and "notice" (the file as it is in the project, before GitHub fills it in): nothing is shown.
        assertNull(AppInfo.parse("""{"min": 0, "notice": ""}"""))
    }

    @Test fun aBrokenFileNeverLocksPeopleOut() {
        assertNull(AppInfo.parse(null))
        assertNull(AppInfo.parse("<html>Not found</html>"))
        assertNull(AppInfo.parse("""{"latest": 0}"""))
        // A minimum above the newest version would make every phone "too old".
        assertNull(AppInfo.parse("""{"latest": 181, "min": 999}"""))
    }
}
