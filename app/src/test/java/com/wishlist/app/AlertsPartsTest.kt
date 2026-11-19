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
