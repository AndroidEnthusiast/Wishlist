package com.wishlist.app

import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** The app starts with crash reports set up (Crash.kt), without throwing (ROADMAP S0.5), and sends none from a check. */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class StartupTest {
    @Test fun startsWithCrashReports() {
        // Robolectric starts WishlistApp from the manifest, which runs Crash.start.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(app is WishlistApp)
        assertTrue(FirebaseApp.getApps(app).isNotEmpty())
        // Not swallowed here: without the build id (build.gradle.kts, resValue) Crashlytics throws at this point.
        val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
        assertFalse(crashlytics.didCrashOnPreviousExecution())
        // The checks never send reports to the real Crashlytics page (2.155): Crash.start switches collection off here.
        assertTrue(Crash.onComputer)
        // A second start (or Push.init later) changes nothing and doesn't throw.
        Crash.start(app)
        Crash.report(IllegalStateException("test"))
    }
}
