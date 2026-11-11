package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Please try again in a moment" when a limit against abuse is hit (ServerNotice, ROADMAP S4.2). */
class ServerNoticeTest {
    @Test fun oneKindMessageThatIsntCoveredByAnUntrueOne() {
        ServerNotice.text = null
        ServerNotice.limited()
        assertEquals("Please try again in a moment", ServerNotice.text)
        // A screen's own "check your connection" right after would only cover it; a message with Undo is not asked.
        assertTrue(ServerNotice.covers("Couldn’t save. Check your connection."))
        assertFalse(ServerNotice.covers("Please try again in a moment"))
        // Several refused calls at once give one message.
        ServerNotice.text = null
        ServerNotice.limited()
        assertNull(ServerNotice.text)
    }
}
