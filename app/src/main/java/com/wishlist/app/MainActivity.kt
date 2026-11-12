package com.wishlist.app

import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.statusBars
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

class MainActivity : ComponentActivity() {
    /** Where a notification, a link or something shared asks the app to open (Deep.kt); App opens it and clears it. */
    private var deep by mutableStateOf<Deep?>(null)

    companion object {
        const val BIN_CODE = "bin_code"
        const val BIN_ID = "bin_id"
        /** A "gift you reserved is no longer on the list" alert: the friend's list to open. */
        const val FRIEND_CODE = "friend_code"
        /** A "Target price reached" alert: the wish whose price history to open. */
        const val HISTORY_ID = "history_id"
        // A notification about several price changes: "Price changes" (owner, 7 November 2026).
        const val OPEN_PRICES = "open_prices"
        /** A friend request (or an approval) alert: open the Friends tab. */
        const val OPEN_FRIENDS = "open_friends"
        /** A new message alert: the friend (profile id) whose conversation to open. */
        const val OPEN_CHAT = "open_chat"
        /** A birthday alert: the friend (profile id) whose page to open. */
        const val OPEN_FRIEND = "open_friend"
        /** A wishlist group alert (2.108): the group (or invite) to open, or "" for just the app. */
        const val OPEN_GROUP = "open_group"
        /** A gift exchange notification (2.129): the exchange's id, or "" for the list of exchanges. */
        const val OPEN_EXCHANGE = "open_exchange"
        /** A "gift you reserved is no longer on …" alert (2.126): where to go, as on the Notifications page ("c:"/"s:"). */
        const val OPEN_NOTE = "open_note"
        /** A bundle of news that lead to different places (7 November 2026): the Notifications page. */
        const val OPEN_NOTES = "open_notes"
        /** "Still buying …?" answered from the notification's buttons (2.142): the question, the answer, the notification. */
        const val ASK_ID = "ask_id"
        const val ASK_YES = "ask_yes"
        const val ASK_NID = "ask_nid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restore that reached the server but not this phone is finished first (Restore.kt, 2.188).
        if (Restore.resume(this)) { Backup.restart(this); return }
        Moved.load(this)
        Repo.load(this)
        Show.load(this)
        Friends.load(this)
        handle(intent)
        addOnNewIntentListener { handle(it) }
        Alerts.ensureChannel(this)
        Alerts.schedule(this, Repo.intervalHours, false)
        Alerts.scheduleBin(this)
        // Whatever didn't reach the server yet (a save made offline) goes online now; nothing waiting: no call.
        Sync.catchUpLater(this)
        // Edge to edge (owner, 2.102): the app draws behind the system bars and handles them itself. Before, Android
        // shrank the whole window for the keyboard ("adjustResize"), also for a keyboard that was still closing in
        // another app when you came back, so the bottom bar jumped up for a moment. Now only what needs it moves.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WishTheme {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    App(deep) { deep = null }
                }
            }
        }
    }

    /**
     * A phone that got the app from an invite page has no link to open: Android doesn't pass anything on through an
     * install. So the page's "Download app" button puts the invitation on the clipboard, and the first time the app
     * starts after an install (or update) it looks for it there and offers to join. It looks only once per install.
     */
    // (Live updates follow the whole app being in front: AppForeground.kt, registered in WishlistApp.)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboardInvite()
    }

    private fun checkClipboardInvite() {
        try {
            val stamp = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            val p = getSharedPreferences("wishlist", MODE_PRIVATE)
            if (p.getLong("inv_clip_for", 0L) == stamp) return
            p.edit().putLong("inv_clip_for", stamp).apply()
            // An invitation that came in as a link or a shared text wins over the clipboard.
            if (deep is Deep.Invite) return
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
            val key = Shared.inviteFromClipboard(this, text) ?: return
            // The invitation was only on the clipboard to get here. Take it off again.
            try {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                else cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            } catch (e: Exception) {
            }
            deep = Deep.Invite(key)
        } catch (e: Exception) {
        }
    }

    /** Reads a tapped notification, an opened link or something shared into the app (ROADMAP S1.3). */
    internal fun handle(i: Intent?) {
        if (i == null) return
        // A tapped push notification: its line on the Notifications page is seen already (owner, 2.126: no red dot).
        if (i.hasExtra(ASK_ID)) {
            Asks.answerFromNotification(this, i.getLongExtra(ASK_ID, 0L), i.getBooleanExtra(ASK_YES, false), i.getIntExtra(ASK_NID, 0))
            i.removeExtra(ASK_ID)
            return
        }
        var ask: AskFocus? = null
        if (i.hasExtra(Alerts.NOTE_KIND)) {
            val kind = i.getStringExtra(Alerts.NOTE_KIND).orEmpty()
            // "X is free. Reserve it now?" / "Your reservation of X has ended" (2.142): the list opens on that wish.
            val list = i.getStringExtra(Alerts.NOTE_LIST).orEmpty()
            val wish = i.getLongExtra(Alerts.NOTE_ID, 0L)
            if ((kind == "ask_free" || kind == "ask_ended" || kind == "ask_yes" || kind == "ask_sent") && wish > 0L && list.length > 2)
                ask = AskFocus(list.substring(2), wish, kind == "ask_free")
            if (kind.isNotEmpty()) Notifications.markRead(this, kind, list, wish)
            // A push about one wish on your own list opens the list on that wish (2.161).
            if (i.hasExtra(OPEN_NOTE)) WishFocus.forNote(list, wish, kind)
            i.removeExtra(Alerts.NOTE_KIND)
        }
        // Each is handled once (removed from the Intent): turning the phone must not open it again.
        val d = deepOf(i)?.let { if (ask != null && it is Deep.Note) it.copy(ask = ask) else it }
        if (d != null) deep = d
    }

}
