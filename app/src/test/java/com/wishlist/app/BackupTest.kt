package com.wishlist.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import java.io.File
import java.util.zip.ZipFile

/** Export and Import keep each phone's own notification identity (Backup.phoneOnly, 2.164). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class BackupTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun put(path: String, text: String) = File(c.filesDir, path).apply { parentFile?.mkdirs(); writeText(text) }

    @Test fun phoneOnlyFilesStayOnTheirPhone() = runBlocking {
        // Phone A: a photo, a wish list, and Firebase's own files.
        put("images/wish_1.jpg", "photo")
        put("PersistedInstallation.ABC.json", "identity of phone A")
        put(".com.google.firebase.crashlytics.files.v2:com.wishlist.app/report", "crash data")
        put("datastore/firebase_session_x.preferences_pb", "session")
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        prefs.edit().putString("wishes", "[{\"id\":1}]").putString("push_token", "token A").putLong("push_day", 5L).commit()

        val file = File(c.cacheDir, "backup.zip")
        Backup.export(c, Uri.fromFile(file))
        val names = ZipFile(file).use { z -> z.entries().toList().map { it.name } }
        assertTrue("files/images/wish_1.jpg" in names)
        assertFalse(names.any { it.contains("PersistedInstallation") || it.contains("crashlytics") || it.contains("datastore") })
        assertFalse(ZipFile(file).use { z -> String(z.getInputStream(z.getEntry("prefs.json")).readBytes()) }.contains("token A"))

        // Phone B: its own identity and token; the import brings A's wishes and photo, not A's identity.
        c.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        put("PersistedInstallation.ABC.json", "identity of phone B")
        prefs.edit().clear().putString("push_token", "token B").commit()
        Backup.import(c, Uri.fromFile(file))
        assertEquals("photo", File(c.filesDir, "images/wish_1.jpg").readText())
        assertEquals("identity of phone B", File(c.filesDir, "PersistedInstallation.ABC.json").readText())
        assertEquals("[{\"id\":1}]", prefs.getString("wishes", null))
        assertEquals("token B", prefs.getString("push_token", null))
    }

    @Test fun oldBackupsWithFirebaseFilesAreReadSafely() {
        // Files from backups made before 2.164: skipped on import.
        assertTrue(Backup.phoneOnly("PersistedInstallation.W0RFRkFVTFRd.json"))
        assertTrue(Backup.phoneOnly(".com.google.firebase.crashlytics.files.v2:com.wishlist.app/open-sessions/x/report"))
        assertTrue(Backup.phoneOnly("datastore/firebase_session_x_data.preferences_pb"))
        assertTrue(Backup.phoneOnly("generatefid.lock"))
        assertTrue(Backup.phoneOnly("profileInstalled"))
        assertFalse(Backup.phoneOnly("images/wish_1793541411818.jpg"))
        assertFalse(Backup.phoneOnly("profile_1793643713116.jpg"))
        assertFalse(Backup.phoneOnly("friends/JC6X7SF3MQFN3Z8P4Q9C_1.jpg"))
    }
}
