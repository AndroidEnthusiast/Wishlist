package com.wishlist.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The half-hourly check asks only about what changed (Pulse.kt, ROADMAP S3.3). */
class PulseTest {
    private val answer = JSONObject("""{"fr":"a1","chat":"b1","gr":"c1","gx":"d1","news":"e1",
        "lists":{"FRIEND01":"f1","FRIEND02":"g1"},
        "shared":{"key-of-collab-one-xx":{"fp":"h1","claim":false},"key-of-collab-two-xx":{"fp":"i1","claim":true},
                  "a-key-this-phone-forgot":{"fp":"gone","claim":false}}}""")
    private val codes = mapOf("key-of-collab-one-xx" to "COLLAB01", "key-of-collab-two-xx" to "COLLAB02")

    @Test fun readsTheAnswer() {
        val (m, claims) = PulsePlan.parse(answer) { codes[it] }
        assertEquals("a1", m["fr"]); assertEquals("f1", m["l:FRIEND01"]); assertEquals("i1", m["s:COLLAB02"])
        assertEquals(setOf("COLLAB02"), claims)
        assertEquals(9, m.size) // the key this phone forgot is left out
    }

    @Test fun onlyWhatChangedSinceItWasLastHandled() {
        val (fresh, claims) = PulsePlan.parse(answer) { codes[it] }
        val old = fresh + mapOf("chat" to "b0", "l:FRIEND02" to "g0", "s:COLLAB01" to "h0")
        val plan = PulsePlan(false, fresh, old, claims)
        assertFalse(plan.due("fr")); assertTrue(plan.due("chat")); assertFalse(plan.due("news"))
        assertEquals(setOf("FRIEND02"), plan.lists("l:", listOf("FRIEND01", "FRIEND02")))
        assertEquals(setOf("COLLAB01"), plan.lists("s:", listOf("COLLAB01", "COLLAB02")))
        // A part the server didn't answer (e.g. its script missing): asked as before.
        assertTrue(plan.due("something new"))
        assertEquals(setOf("NEWLIST1"), plan.lists("l:", listOf("NEWLIST1")))
        // Handled: remembered. Not handled (the call failed): asked again next time.
        plan.ok("chat")
        val next = PulsePlan(false, fresh, plan.remembered(), emptySet())
        assertFalse(next.due("chat"))
        assertTrue(next.due("l:FRIEND02"))
    }

    @Test fun theFirstCheckOfTheDayAndNoAnswerAskEverything() {
        val (fresh, claims) = PulsePlan.parse(answer) { codes[it] }
        val daily = PulsePlan(true, fresh, fresh, claims)
        assertTrue(daily.due("fr"))
        assertNull(daily.lists("l:", listOf("FRIEND01")))
        // No answer at all: everything is asked, and what was remembered stays.
        val none = PulsePlan(true, emptyMap(), fresh, emptySet())
        assertTrue(none.due("chat"))
        assertEquals(fresh, none.remembered())
    }

    @Test fun listsNoLongerFollowedAreForgotten() {
        val plan = PulsePlan(false, mapOf("fr" to "a"), mapOf("fr" to "a", "l:OLD" to "x"), emptySet())
        assertEquals(mapOf("fr" to "a"), plan.remembered())
    }
}
