package com.wishlist.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo

/** Photos as files (PeoplePhotos, ROADMAP S4.1, 2.185). */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class PeoplePhotosTest {
    private val ours = Config.SUPABASE_URL + "/storage/v1/object/public/people-photos/p/someone/abc.jpg"

    @Test fun anEmbeddedPhotoFromAnOlderAppStillReads() = runBlocking {
        assertArrayEquals("ABC".toByteArray(), PeoplePhotos.bytes("\"QUJD\""))
        assertNull(PeoplePhotos.bytes(""))
    }

    @Test fun onlyOurOwnBucketIsEverDownloaded() = runBlocking {
        assertTrue(PeoplePhotos.isOurs(ours))
        assertFalse(PeoplePhotos.isOurs("https://evil.example/storage/v1/object/public/people-photos/p/x/abc.jpg"))
        assertFalse(PeoplePhotos.isOurs(Config.SUPABASE_URL + "/storage/v1/object/public/wish-photos/x/abc.jpg"))
        assertFalse(PeoplePhotos.isOurs(Config.SUPABASE_URL + "/storage/v1/object/public/people-photos/../secret"))
        assertNull(PeoplePhotos.bytes("https://evil.example/x.jpg"))
    }

    @Test fun namesCantBeGuessedAndChangeEveryTime() {
        val a = PeoplePhotos.newName()
        val b = PeoplePhotos.newName()
        assertTrue(Regex("[A-Za-z0-9]{32}\\.jpg").matches(a))
        assertTrue(a != b)
        // The server accepts names of 16 to 64 letters and digits (wl_people_url_ok).
        assertEquals(36, a.length)
    }
}
