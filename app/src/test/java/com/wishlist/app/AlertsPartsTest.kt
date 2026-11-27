package com.wishlist.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Config as Robo

/**
 * The risks the owner chose for the notifications rebuild (19 November 2026), in plain words; AlertsSameTest checks
 * every notification together against the record made before the rebuild.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class AlertsPartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val nm get() = c.getSystemService(NotificationManager::class.java)
    private fun shown(id: Int): Notification = nm.activeNotifications.first { it.id == id }.notification
    private fun tap(id: Int) = shadowOf(shown(id).contentIntent).savedIntent

    @Before fun aFreshPhone() {
        c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(c, Configuration.Builder().build())
    }

    @Test fun sameTextAndTappingOpensTheSamePlace() {
        Alerts.notifyDrop(c, 11, "Teapot", 40.0, 30.0, "EUR", wishId = 101)
        assertEquals("Price drop on your wishlist", shown(11).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Teapot is now €30.00 (was €40.00, -25%)", shown(11).extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("its price page", 101L, tap(11).getLongExtra(MainActivity.HISTORY_ID, 0))
        Alerts.notifyMessage(c, "p1", "Robin", "Hi!")
        assertEquals("the chat", "p1", tap("p1".hashCode() * 31 + 23).getStringExtra(MainActivity.OPEN_CHAT))
        Alerts.notifyGroup(c, "G1", "Bob invited you", "Join?", "group_invite", "g:G1")
        assertEquals("the group", "G1", tap(7_000_203).getStringExtra(MainActivity.OPEN_GROUP))
    }

    @Test fun theChannelsKeepTheirIdsSoYourAndroidSettingsStay() {
        Alerts.ensureChannel(c)
        assertEquals(
            listOf("birthdays", "exchange", "friends", "gift_lists", "groups", "messages", "occasions", "price_drops", "reactions", "reserved_bin"),
            nm.notificationChannels.map { it.id }.sorted()
        )
        // A channel you muted stays muted when the app sets its channels up again.
        nm.getNotificationChannel("messages").let { it.importance = NotificationManager.IMPORTANCE_NONE; nm.createNotificationChannel(it) }
        Alerts.ensureChannel(c)
        assertEquals(NotificationManager.IMPORTANCE_NONE, nm.getNotificationChannel("messages").importance)
    }

    @Test fun severalPriceChangesComeAsOneSummaryAndBundlesGather() = runBlocking {
        Alerts.collectPrices(c) {
            Alerts.notifyDrop(c, 21, "Mug", 10.0, 8.0, "EUR")
            Alerts.notifyDrop(c, 22, "Pan", 50.0, 25.0, "EUR")
        }
        assertEquals("2 wishes got cheaper", shown(7_000_001).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue("not one each", nm.activeNotifications.none { it.id == 21 || it.id == 22 })
        Alerts.notifyFriend(c, "p1", "Robin", true); Alerts.notifyFriend(c, "p2", "Kim", true)
        assertEquals("2 people want to be your friend", shown(7_000_201).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("only the first of a burst buzzes", 1, nm.activeNotifications.count { it.id != 7_000_099 && it.notification.groupAlertBehavior != NotificationCompatSilent })
    }

    /**
     * Owner, 27 November 2026 (a screen recording): a summary of Co-op price drops opened Price changes, which only
     * shows your own lists. Now each list gets its own notification, and a summary opens that list.
     */
    @Test fun priceSummariesComeOnePerListAndOpenThatList() = runBlocking {
        Alerts.collectPrices(c) {
            Alerts.notifyDrop(c, 51, "Polo", 90.0, 60.0, "EUR", "Price drop on Mum", 501, "s:COOP1")
            Alerts.notifyDrop(c, 52, "Sweat", 120.0, 80.0, "EUR", "Price drop on Mum", 502, "s:COOP1")
            Alerts.notifyDrop(c, 53, "Jacket", 470.0, 400.0, "EUR", wishId = 503, listKey = "m:L1")
            Alerts.notifyDrop(c, 54, "Scarf", 40.0, 30.0, "EUR", wishId = 504, listKey = "m:L2")
            Alerts.notifyDrop(c, 55, "Hat", 30.0, 20.0, "EUR", wishId = 505, listKey = "m:L2")
        }
        val coop = summaryId(true, "s:COOP1")
        assertEquals("2 wishes got cheaper", shown(coop).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("the Co-op list opens", "s:COOP1", tap(coop).getStringExtra(MainActivity.OPEN_NOTE))
        assertTrue("not Price changes", !tap(coop).getBooleanExtra(MainActivity.OPEN_PRICES, false))
        val own = summaryId(true, "m:L2")
        assertEquals("your own list opens too", "m:L2", tap(own).getStringExtra(MainActivity.OPEN_NOTE))
        assertTrue("not Price changes", !tap(own).getBooleanExtra(MainActivity.OPEN_PRICES, false))
        assertTrue("one per list", coop != own)
        assertEquals("one drop on a list is its own notification: the price page", 503L, tap(53).getLongExtra(MainActivity.HISTORY_ID, 0))
        assertTrue("not told one by one", nm.activeNotifications.none { it.id in listOf(51, 52, 54, 55) })
        assertTrue("no summary of everything", nm.activeNotifications.none { it.id == 7_000_001 })
    }

    @Test fun theBackgroundCheckKeepsYourChoiceAndNeverRunsTwice() {
        Alerts.schedule(c, 6, false)
        Alerts.schedule(c, 12, true)
        Alerts.schedule(c, 3, false) // at a start: your choice is kept
        val works = WorkManager.getInstance(c).getWorkInfosForUniqueWork("prices").get()
        assertEquals("one background check", 1, works.size)
        assertEquals(TimeUnit.HOURS.toMillis(12), works.single().periodicityInfo!!.repeatIntervalMillis)
    }

    private companion object {
        /** What NotificationCompat.setSilent(true) sets on a grouped notification. */
        const val NotificationCompatSilent = Notification.GROUP_ALERT_SUMMARY
    }
}
