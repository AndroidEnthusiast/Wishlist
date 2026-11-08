package com.wishlist.app

import android.app.Application

/** Runs once when Android starts the app, before any screen or background job: only what must come first. */
class WishlistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.start(this)
        Calls.attach(this)
    }
}
