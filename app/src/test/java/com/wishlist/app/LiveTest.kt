package com.wishlist.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live updates (Live.kt, ROADMAP S3.2). The checks never connect to the server, so "live" is pretended. */
class LiveTest {
    @Test fun channelsAreTheSameFingerprintsAsTheServers() {
        // tests/sql/sync_test.sql checks wl_live_topic gives exactly this.
        assertEquals("wll-66c508165d9e2f6674dbfa85793a0cfe400eaecd18791709668802c3b79b6ac6", Live.listTopic("MINE0001"))
        assertTrue(Live.chatTopic("token").startsWith("wlc-"))
        assertTrue(Live.listTopic("token") != Live.chatTopic("token"))
    }

    @Test fun pollsWithoutLiveFetchesOnSignalsAndRestsInTheBackground() = runBlocking {
        Live.MIN_GAP = 0
        val topic = Live.listTopic("TEST0001")
        val calls = ArrayList<Boolean>()
        val job = launch { Live.follow(topic, 150) { live -> synchronized(calls) { calls += live } } }
        // Not live (no connection in the checks): fetched at once and then every 150 ms, as before.
        delay(400)
        val polled = synchronized(calls) { calls.size }
        assertTrue("polled $polled times", polled in 2..4)
        // Live: no more polling, only on a signal.
        Live.pretend(topic, joined = true, signal = Live.Signal.STATE)
        delay(50)
        val before = synchronized(calls) { calls.size }
        delay(400)
        assertEquals(before, synchronized(calls) { calls.size })
        Live.pretend(topic, joined = true, signal = Live.Signal.CHANGED)
        delay(100)
        assertEquals(before + 1, synchronized(calls) { calls.size })
        assertEquals(true, synchronized(calls) { calls.last() })
        // In the background: nothing, even when polling; back in front: one fetch at once.
        Live.foreground(false)
        delay(400)
        assertEquals(before + 1, synchronized(calls) { calls.size })
        Live.foreground(true)
        delay(100)
        assertEquals(before + 2, synchronized(calls) { calls.size })
        // The screen leaves: its channel is let go.
        job.cancel(); job.join()
        assertEquals(0, Live.count(topic))
    }
}
