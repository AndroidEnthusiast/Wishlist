package com.wishlist.app

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics

/*
 * Crash reports (owner, D2, 8 November 2026; ROADMAP S0.5), with Firebase Crashlytics (free, same Firebase project as
 * the message notifications in Push.kt). When the app crashes, Crashlytics sends where it went wrong (the lines of
 * code), the app version, the phone model and the Android version; never wishes, names, notes or chat, because the
 * app never hands it any (no custom keys, logs or user ids). Said in App settings > About Wishlist.
 * Started in WishlistApp, before anything else, so a crash on the very first screen is reported too. Reports show in
 * the Firebase console (Crashlytics) and are read at the start of each session (ROADMAP Stage 5).
 */
object Crash {
    fun start(c: Context) {
        try {
            if (!Push.init(c)) return
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } catch (e: Throwable) {
            // Crash reports must never be the reason the app doesn't start.
        }
    }

    /** A problem that didn't crash the app but should be fixed (for example a stored list that couldn't be read). */
    fun report(e: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(e)
        } catch (x: Throwable) {
        }
    }
}
