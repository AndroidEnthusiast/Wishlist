package com.wishlist.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether the app is in front, for the whole app instead of one screen window (owner, 8 November 2026: a Friend's list
 * kept spinning after a gift was shared from it into the app). Sharing from inside Wishlist into Wishlist opens a
 * second window on top of the first: the new one starts, then the old one stops. A single on/off switch per window
 * (MainActivity's onStart/onStop until 2.176) ended "off" while the app was on screen, so Live.follow waited forever.
 * Here every visible window counts: in front while at least one is started.
 */
class AppForeground(private val changed: (Boolean) -> Unit) {
    private var started = 0

    @Synchronized
    fun start() {
        started++
        if (started == 1) changed(true)
    }

    @Synchronized
    fun stop() {
        if (started == 0) return
        started--
        if (started == 0) changed(false)
    }

    /** Listens to every window of the app (registered once in [WishlistApp]). */
    fun callbacks(): Application.ActivityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = start()
        override fun onActivityStopped(activity: Activity) = stop()
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
