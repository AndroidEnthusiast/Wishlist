package com.wishlist.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Instant message notifications (owner, 3 November 2026; 2.105), with Firebase Cloud Messaging (free).
 *  - The phone asks Firebase for its push address (a "token") and saves it with its profile (wl_push_set,
 *    supabase-push.sql).
 *  - When a message is saved on the server, the database calls our Supabase function "push"
 *    (supabase/functions/push), which asks Firebase to wake the friend's phone. The push carries no text: the
 *    messages stay end-to-end encrypted, Google only sees that a message arrived.
 *  - The woken phone fetches its messages as the background check does (Chat.poll) and shows the notification.
 * Until the Firebase values are in Config (FCM_*), nothing here runs and the background check (15–30 minutes) stays.
 */
object Push {
    val configured: Boolean
        get() = Config.enabled && Config.FCM_APP_ID.isNotBlank() && Config.FCM_API_KEY.isNotBlank() &&
            Config.FCM_PROJECT_ID.isNotBlank() && Config.FCM_SENDER_ID.isNotBlank()

    /** Sets up Firebase with our project's values (there is no google-services.json). False when not configured. */
    fun init(c: Context): Boolean {
        if (!configured) return false
        return try {
            if (FirebaseApp.getApps(c).isEmpty()) {
                FirebaseApp.initializeApp(
                    c.applicationContext,
                    FirebaseOptions.Builder()
                        .setApplicationId(Config.FCM_APP_ID)
                        .setApiKey(Config.FCM_API_KEY)
                        .setProjectId(Config.FCM_PROJECT_ID)
                        .setGcmSenderId(Config.FCM_SENDER_ID)
                        .build()
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** At start: asks for this phone's push address and saves it online (only once you have a profile). */
    fun start(c: Context) {
        if (!init(c)) return
        val app = c.applicationContext
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                if (!t.isNullOrBlank()) Sync.later { register(app, t) }
            }
        } catch (e: Exception) {
        }
    }

    /** Saves [token] with your profile, when it changed (or once a day, so the server knows it is still used). */
    suspend fun register(c: Context, token: String) {
        if (!Friends.hasProfile(c)) return
        val p = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        val day = System.currentTimeMillis() / 86_400_000L
        if (p.getString("push_token", null) == token && p.getLong("push_day", 0L) == day) return
        Chat.setPush(c, token)
        p.edit().putString("push_token", token).putLong("push_day", day).apply()
    }
}

/** Receives the pushes: a new message (or a new push address for this phone). */
class PushService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
    }

    override fun onNewToken(token: String) {
        val c = applicationContext
        Sync.later { Push.register(c, token) }
    }

    // Runs on a background thread, with about 10 seconds to finish.
    override fun onMessageReceived(message: RemoteMessage) {
        val c = applicationContext
        runBlocking {
            withTimeoutOrNull(9_000) {
                try {
                    Repo.load(c)
                    Chat.poll(c)
                } catch (e: Exception) {
                }
            }
        }
    }
}
