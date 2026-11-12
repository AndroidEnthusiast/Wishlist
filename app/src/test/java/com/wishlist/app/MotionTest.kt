package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which movement each screen change gets (Motion.kt; owner, 12 November 2026: like Signal). */
class MotionTest {
    private val D = Motion.Kind.DEEPER
    private val B = Motion.Kind.BACK
    private val T = Motion.Kind.THROUGH

    @Test fun theBarsTabsFadeThrough() {
        assertEquals(T, Motion.routeKind(Route.MAIN, Route.MESSAGES))
        assertEquals(T, Motion.routeKind(Route.MESSAGES, Route.FRIENDS))
        assertEquals(T, Motion.routeKind(Route.FRIENDS, Route.MAIN))
        // A tab tapped while a list is open is a side step, not a step back.
        assertEquals(T, Motion.routeKind(Route.LIST, Route.SHOPS))
    }

    @Test fun deeperSlidesInAndBackSlidesOut() {
        assertEquals(D, Motion.routeKind(Route.MAIN, Route.LIST))
        assertEquals(D, Motion.routeKind(Route.MAIN, Route.GROUP))
        assertEquals(D, Motion.routeKind(Route.FRIENDS, Route.PROFILE))
        assertEquals(D, Motion.routeKind(Route.LIST, Route.LIST_SETTINGS))
        assertEquals(B, Motion.routeKind(Route.LIST, Route.MAIN))
        assertEquals(B, Motion.routeKind(Route.LIST_SETTINGS, Route.LIST))
        // Closing a screen on top of a tab goes back to that tab.
        assertEquals(B, Motion.routeKind(Route.PROFILE, Route.FRIENDS))
    }

    @Test fun screensOnTopOfEachOtherFadeThrough() {
        assertEquals(T, Motion.routeKind(Route.SETTINGS, Route.BIN))
    }

    @Test fun insideATab() {
        assertEquals(D, Motion.kind(0, 1))
        assertEquals(B, Motion.kind(2, 1))
        assertEquals(T, Motion.kind(1, 1))
    }
}
