package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import java.util.zip.ZipInputStream

/** The recovery key (RecoveryKey.kt, ROADMAP S4.5, 2.187). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class RecoveryKeyTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun aKeyIs32SymbolsWithoutLookAlikes() {
        val k = RecoveryKey.newKey()
        assertTrue(Regex("[A-HJ-NP-Z2-9]{32}").matches(k))
        assertNotEquals(k, RecoveryKey.newKey())
        assertEquals(39, RecoveryKey.pretty(k).length) // eight groups of four, seven dashes
    }

    @Test fun aKeyIsReadBackHoweverItWasSavedOrPasted() {
        val k = RecoveryKey.newKey()
        val p = RecoveryKey.pretty(k)
        assertEquals(k, RecoveryKey.parse(p))
        assertEquals(k, RecoveryKey.parse(p.lowercase()))
        assertEquals(k, RecoveryKey.parse(p.replace("-", " ")))
        assertEquals(k, RecoveryKey.parse("My Wishlist recovery key: $p"))
        assertNull(RecoveryKey.parse("ABCD-EFGH"))
        assertNull(RecoveryKey.parse(""))
    }

    @Test fun theKeyGivesAnIdAProofAndALockThatDiffer() {
        val d = RecoveryKey.derive("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        assertTrue(Regex("[0-9a-f]{64}").matches(d.id) && Regex("[0-9a-f]{64}").matches(d.proof))
        assertNotEquals(d.id, d.proof)
        assertEquals(32, d.lock.size)
        // Always the same for the same key (a new phone finds the copy again).
        assertEquals(d.id, RecoveryKey.derive("ABCDEFGHJKLMNPQRSTUVWXYZ23456789").id)
    }

    @Test fun onlyTheRightKeyUnlocksTheCopy() {
        val right = RecoveryKey.derive(RecoveryKey.newKey())
        val wrong = RecoveryKey.derive(RecoveryKey.newKey())
        val plain = "lists, wishes and keys".toByteArray()
        val locked = RecoveryKey.lock(plain, right.lock)
        assertFalse(String(java.util.Base64.getDecoder().decode(locked)).contains("wishes"))
        assertArrayEquals(plain, RecoveryKey.unlock(locked, right.lock))
        assertTrue(runCatching { RecoveryKey.unlock(locked, wrong.lock) }.isFailure)
    }

    @Test fun theCopyHoldsTheSettingsAndChatKeysButNoPictures() {
        c.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit().putString("pf_id", "me_me_me_me_me_me_1").commit()
        c.getSharedPreferences("chatkeys", Context.MODE_PRIVATE).edit().putString("priv", "secret-chat-key").commit()
        java.io.File(c.filesDir, "images").mkdirs()
        java.io.File(c.filesDir, "images/photo.jpg").writeBytes(ByteArray(10_000))
        val names = ArrayList<String>()
        var text = ""
        ZipInputStream(Backup.bytesForVault(c).inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; names += e.name; text += String(z.readBytes()) }
        }
        assertTrue(names.containsAll(listOf("manifest.json", "prefs.json", "chatkeys.json")))
        assertTrue(names.none { it.startsWith("files/") })
        assertTrue(text.contains("me_me_me_me_me_me_1") && text.contains("secret-chat-key"))
        // The key stays the same once made (the copy must be found again with it).
        assertEquals(RecoveryKey.key(c), RecoveryKey.key(c))
    }
}
