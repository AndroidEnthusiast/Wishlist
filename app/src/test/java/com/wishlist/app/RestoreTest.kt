package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import java.io.File

/** Restoring with the recovery key (Restore.kt, 2.188): the secrets it replaces, and a restore finished after a crash. */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class RestoreTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun s(v: String) = JSONObject().put("t", "s").put("v", v)

    private fun values(): JSONObject = JSONObject()
        .put("pf_id", s("PROFILEID"))
        .put("pf_token", s("PROFILETOKEN00000001"))
        .put("code", s("MAINCODE")).put("token", s("MAINTOKEN00000000001"))
        .put("mine_ids", s("[\"main\",\"x1\"]"))
        .put("mine_x1", s(JSONObject().put("name", "Christmas").put("code", "XMASCODE").put("token", "XMASTOKEN00000000001").toString()))
        .put("mine_x2", s(JSONObject().put("name", "Not shared").put("code", "").put("token", "").toString()))
        .put("shared", s(JSONArray().put(JSONObject().put("code", "COLLAB1").put("key", "EDITKEY").put("owner", "COLLABOWNER000000001"))
            .put(JSONObject().put("code", "COLLAB2").put("key", "EDITKEY2").put("owner", "")).toString()))

    @Test fun findsEverySecretOfTheOldPhone() {
        val x = Restore.secretsOf(values())
        assertEquals("PROFILEID", x.profileId)
        assertEquals("PROFILETOKEN00000001", x.profileToken)
        assertEquals(
            setOf("MAINCODE" to "MAINTOKEN00000000001", "XMASCODE" to "XMASTOKEN00000000001", "COLLAB1" to "COLLABOWNER000000001"),
            x.lists.toSet()
        )
    }

    @Test fun noProfileNoLists() {
        val x = Restore.secretsOf(JSONObject().put("wishes", s("[]")))
        assertEquals("", x.profileId)
        assertTrue(x.lists.isEmpty())
    }

    private fun zip(values: JSONObject, chatKeys: JSONObject): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("manifest.json")); z.write(JSONObject().put("app", "Wishlist").put("version", 1).toString().toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("prefs.json")); z.write(values.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("chatkeys.json")); z.write(chatKeys.toString().toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test fun aRestoreThatReachedTheServerIsFinishedAtTheNextStart() {
        ListStore.reset(); WishlistDb.reset()
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("push_token", "this phone").putString("rk", "OLDKEYOLDKEYOLDKEYOLDKEYOLDKEY22").commit()
        File(c.noBackupFilesDir, "restore.zip").writeBytes(zip(values(), JSONObject().put("priv", "chat private key")))
        File(c.noBackupFilesDir, "restore.json").writeText(
            JSONObject().put("PROFILETOKEN00000001", "NEWPROFILETOKEN00001").put("XMASTOKEN00000000001", "NEWXMASTOKEN00000001")
                .put("_ready", "yes").toString()
        )
        assertTrue(Restore.resume(c))
        assertEquals("NEWPROFILETOKEN00001", prefs.getString("pf_token", null))
        assertTrue(prefs.getString("mine_x1", "")!!.contains("NEWXMASTOKEN00000001"))
        assertFalse(prefs.getString("mine_x1", "")!!.contains("XMASTOKEN00000000001\""))
        assertEquals("this phone", prefs.getString("push_token", null))
        assertEquals("chat private key", c.getSharedPreferences("chatkeys", Context.MODE_PRIVATE).getString("priv", null))
        // A new key, to be saved: the old one opened the deleted copy.
        assertTrue(prefs.getBoolean("rk_new", false))
        assertFalse(prefs.getBoolean("rk_saved", true))
        assertEquals(32, prefs.getString("rk", "")!!.length)
        assertTrue(prefs.getString("rk", "") != "OLDKEYOLDKEYOLDKEYOLDKEYOLDKEY22")
        assertFalse(File(c.noBackupFilesDir, "restore.zip").exists())
        assertFalse(File(c.noBackupFilesDir, "restore.json").exists())
    }

    @Test fun aRestoreThatNeverReachedTheServerChangesNothing() {
        val prefs = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("pf_token", "MINE").commit()
        File(c.noBackupFilesDir, "restore.zip").writeBytes(zip(values(), JSONObject()))
        File(c.noBackupFilesDir, "restore.json").writeText(JSONObject().put("_ready", "no").toString())
        assertFalse(Restore.resume(c))
        assertEquals("MINE", prefs.getString("pf_token", null))
        assertFalse(File(c.noBackupFilesDir, "restore.zip").exists())
    }
}
