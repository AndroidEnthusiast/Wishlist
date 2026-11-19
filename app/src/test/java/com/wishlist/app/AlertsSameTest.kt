package com.wishlist.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of notifications in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`,
 * "The data rebuild"). Every kind of notification the app shows is made with sample data, some twice (bundles: "3
 * gifts you reserved…", one buzz per burst), price changes also during a check (one summary), and the background
 * checks are scheduled, changed and scheduled again. After each step it writes down every notification on the phone
 * (channel, title, text, lines, number, group, silent, buttons, picture, colour, and where a tap goes: the intent's
 * extras and flags), the notification channels (their ids carry the owner's Android settings) and the planned work.
 * The result must match `src/test/resources/alerts-same.txt` word for word: recorded with Alerts.kt as it was before
 * the rebuild (2.260). A MEANT change: delete the file, run once to record, read the difference in git.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class AlertsSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/alerts-same.txt")
    private val out = StringBuilder()
    private val start = System.currentTimeMillis()
    private val nm get() = c.getSystemService(NotificationManager::class.java)

    private fun intentText(i: Intent?): String {
        if (i == null) return "none"
        val ex = i.extras?.let { b -> b.keySet().sorted().joinToString(",") { "$it=${b.get(it)}" } } ?: ""
        return "${i.component?.shortClassName} flags=${i.flags} {$ex}"
    }

    private fun describe(id: Int, n: Notification): String {
        val e = n.extras
        val lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" | ")
        val pi = n.contentIntent?.let { shadowOf(it) }
        return "notification $id channel=${n.channelId} title='${e.getCharSequence(Notification.EXTRA_TITLE)}' " +
            "text='${e.getCharSequence(Notification.EXTRA_TEXT)}' big='${e.getCharSequence(Notification.EXTRA_BIG_TEXT)}' " +
            "lines='${lines}' summary='${e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)}' number=${n.number} " +
            "group=${n.group} groupSummary=${n.flags and Notification.FLAG_GROUP_SUMMARY != 0} autoCancel=${n.flags and Notification.FLAG_AUTO_CANCEL != 0} " +
            "alertOnce=${n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0} groupAlert=${n.groupAlertBehavior} priority=${n.priority} " +
            "sound=${n.sound} vibrate=${n.vibrate?.toList()} defaults=${n.defaults} color=${n.color} largeIcon=${n.getLargeIcon() != null} " +
            "actions=${n.actions?.map { a -> a.title.toString() + "->" + intentText(shadowOf(a.actionIntent).savedIntent) + " code=" + shadowOf(a.actionIntent).requestCode }} " +
            "tap=${pi?.let { intentText(it.savedIntent) + " code=" + it.requestCode + " flags=" + it.flags }}\n"
    }

    private fun note(step: String) {
        out.append("## ").append(step).append('\n')
        nm.activeNotifications.sortedBy { it.id }.forEach { s -> out.append(describe(s.id, s.notification)) }
        nm.notificationChannels.sortedBy { it.id }.forEach { ch ->
            out.append("channel ${ch.id} '${ch.name}' '${ch.description}' importance=${ch.importance}\n")
        }
        prefs.all.toSortedMap().filterKeys { it.startsWith("nb_") || it == "nt_last_buzz" }.forEach { (k, v) -> out.append("pref $k=$v\n") }
        val wm = WorkManager.getInstance(c)
        for (name in listOf("prices", "bin")) wm.getWorkInfosForUniqueWork(name).get().forEach { w ->
            out.append("work $name ${w.state} every=${w.periodicityInfo?.repeatIntervalMillis} network=${w.constraints.requiredNetworkType} tags=${w.tags.filter { !it.contains('-') }.sorted()}\n")
        }
        val once = wm.getWorkInfosByTag(PriceWorker::class.java.name).get().count { it.periodicityInfo == null } to
            wm.getWorkInfosByTag(BinWorker::class.java.name).get().count { it.periodicityInfo == null }
        out.append("one-off work: price checks=${once.first} bin checks=${once.second}\n")
    }

    private fun normal(s: String): String {
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(s) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    @Test fun notificationsAreExactlyTheSame() {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        WorkManagerTestInitHelper.initializeTestWorkManager(c, Configuration.Builder().build())
        shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
        Alerts.ensureChannel(c)
        out.append("blocked=${Alerts.blockedImportant(c)}\n")
        note("channels")

        Alerts.schedule(c, 6, false); Alerts.schedule(c, 12, true); Alerts.schedule(c, 3, false); Alerts.checkNow(c)
        Alerts.scheduleBin(c); Alerts.scheduleBin(c)
        note("background checks: 6 hours, changed to 12, kept; a check now; the bin check twice")

        Alerts.notifyDrop(c, 11, "Teapot", 40.0, 30.0, "EUR", wishId = 101)
        Alerts.notifyRise(c, 12, "Lamp", 20.0, 25.0, "GBP")
        out.append("test=${Alerts.sendTest(c)}\n")
        note("a drop, a rise, the test notification")
        runBlocking {
            Alerts.collectPrices(c) {
                Alerts.notifyDrop(c, 21, "Mug", 10.0, 8.0, "EUR", wishId = 201)
                Alerts.collectPrices(c) { Alerts.notifyDrop(c, 22, "Pan", 50.0, 25.0, "EUR") }
                for (i in 0 until 7) Alerts.notifyDrop(c, 30 + i, "Thing $i", 10.0 + i, 9.0, "EUR")
                Alerts.notifyRise(c, 40, "Kettle", 30.0, 33.0, "EUR", wishId = 401)
            }
        }
        note("a price check: drops gathered into one summary, one rise on its own")

        Alerts.notifyGiftLost(c, "SAMLIST1", false, 5, "AirPods", "", "Sam’s wishlist", false)
        Alerts.notifyGiftLost(c, "SAMLIST1", false, 6, "Scarf", "", "Sam’s wishlist", true)
        Alerts.notifyGiftLost(c, "MUMGIFT1", true, 7, "Vase", "", "Mum’s 60th", false)
        Alerts.notifyCleanup(c)
        Alerts.notifyReminder(c, "SAMLIST1", false, 1, "Sam’s birthday", listOf("AirPods"))
        Alerts.notifyReminder(c, "MUMGIFT1", true, 7, "Mum’s 60th", listOf("Vase", "Book"))
        Alerts.notifyThanks(c, "SAMLIST1", 5, "AirPods", "Sam"); Alerts.notifyThanks(c, "SAMLIST1", 6, "Scarf", "Sam")
        Alerts.notifyReceivedAsk(c, MAIN_LIST, "My wishlist")
        Alerts.notifyReactions(c, "m:main", "Kim reacted", "❤️ on Teapot")
        Alerts.notifyAsk(c, "ask", 77, "v:", 5, "Still buying the AirPods?", "Sam’s birthday is close.")
        Alerts.notifyAsk(c, "ask_free", 78, "c:SAMLIST1", 6, "Free again", "Someone let it go.")
        Alerts.notifyGiftInvite(c, "MUMGIFT2", "Bob", "Dad’s 70th"); Alerts.notifyGiftInvite(c, "MUMGIFT3", "Jo", "Xmas")
        out.append("titles ${Alerts.reminderTitle(1, "x")} | ${Alerts.reminderTitle(3, "x")} | ${Alerts.reminderText(listOf("a"))} | ${Alerts.reminderText(listOf("a", "b"))} | ${Alerts.giftLostTitle("p")} | ${Alerts.giftLostText("t", true)}\n")
        note("your lists and gifts")

        Alerts.notifyFriend(c, "p1", "Robin", true); Alerts.notifyFriend(c, "p2", "", true); Alerts.notifyFriend(c, "p3", "Kim", false)
        Alerts.notifyBirthday(c, "p4", "Sam", 10); Alerts.notifyBirthday(c, "p5", "Lee", 12)
        Alerts.notifyGroup(c, "G1", "Bob invited you to “Family”", "Pick the wishlist you want to bring, then join.", "group_invite", "g:G1")
        Alerts.notifyGroup(c, "", "You are removed from “Old”", "The organiser removed you.", "group_removed", "x:G9")
        Alerts.notifyExchange(c, "X1", "Names are drawn", "Open yours.", "gx_drawn", "e:X1")
        Alerts.notifyExchange(c, "X1", "Your Secret Santa wrote", "In “Santa”.", "gx_msg", "e:X1", msg = true)
        Alerts.notifyExchange(c, "X1", "Kim answered", "In “Santa”.", "gx_msg", "e:X1", msg = true)
        Alerts.notifyMessage(c, "p1", "Robin", "Hi!"); Alerts.notifyMessage(c, "p1", "Robin", "Are you there?"); Alerts.notifyMessage(c, "p6", "", "Hello")
        note("people")

        Alerts.clearMessage(c, "p1"); Alerts.cancel(c, 7_000_201)
        note("a chat seen, a notification cancelled")

        // Switched off by the owner in Android's settings: the warning in App settings.
        nm.getNotificationChannel("reserved_bin")?.let { ch -> ch.importance = NotificationManager.IMPORTANCE_NONE; nm.createNotificationChannel(ch) }
        shadowOf(nm).setNotificationsEnabled(true)
        out.append("blocked=${Alerts.blockedImportant(c)}\n")
        shadowOf(nm).setNotificationsEnabled(false)
        out.append("all off: blocked=${Alerts.blockedImportant(c)} test=${Alerts.sendTest(c)}\n")
        note("notifications switched off")

        val now = normal(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
