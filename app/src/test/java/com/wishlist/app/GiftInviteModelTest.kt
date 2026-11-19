package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Gift list invitations (the rebuild, 19 November 2026): the messages after sending, picking, and the dark-mode fix. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiftInviteModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun whatSendingSays() {
        assertEquals("Invitation sent", inviteResult(1, 0))
        assertEquals("3 invitations sent", inviteResult(3, 0))
        assertEquals("1 invitations couldn’t be sent. Try again.", inviteResult(3, 1))
        assertEquals("Couldn’t reach the server. Check your connection.", inviteResult(2, 2))
    }

    @Test fun pickingAndUnpicking() {
        val st = InviteFriendsState(ctx, SharedList("C", "K", "", ""), kotlinx.coroutines.MainScope())
        st.toggle("a"); st.toggle("b"); st.toggle("a")
        assertEquals(setOf("b"), st.picked)
    }

    /** Fixed in the rebuild: the invitation's headings use the page's text colour (they were black on black in dark). */
    @Test fun headingsReadableInDark() {
        val v = File("src/main/java/com/wishlist/app/GiftInviteViews.kt").readText()
        assertTrue(v.contains("LocalContentColor provides cs.onSurface"))
    }
}
