package com.wishlist.app

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Home's My wishlists | Shared swipe (owner, 20 November 2026, a bug: the empty space under the shorter side didn't
 * swipe). The pager only covers its own pages, which are as tall as their lists; this lets the whole area under the
 * divider move the pager too, following the finger, and settle on the nearest side (or the side it was flung to).
 */

/** Which page a swipe ends on: from [settled], moved to [position] (page + fraction), flung at [velocity] px/s. */
internal fun swipeTarget(settled: Int, position: Float, velocity: Float, pages: Int): Int {
    val moved = position - settled
    val t = when {
        velocity < -400f || moved > 0.15f -> settled + 1
        velocity > 400f || moved < -0.15f -> settled - 1
        else -> settled
    }
    return t.coerceIn(0, pages - 1)
}

/** Horizontal swipes on this area (where the pager itself isn't) move [pager]. */
internal fun Modifier.swipesPager(pager: PagerState, scope: CoroutineScope): Modifier = pointerInput(pager) {
    val tracker = VelocityTracker()
    detectHorizontalDragGestures(
        onDragStart = { tracker.resetTracking() },
        onDragEnd = {
            val v = tracker.calculateVelocity().x
            val target = swipeTarget(pager.settledPage, pager.currentPage + pager.currentPageOffsetFraction, v, pager.pageCount)
            scope.launch { pager.animateScrollToPage(target) }
        },
        onDragCancel = { scope.launch { pager.animateScrollToPage(pager.settledPage) } },
        onHorizontalDrag = { change, dx ->
            tracker.addPosition(change.uptimeMillis, change.position)
            if (abs(dx) > 0f) { change.consume(); pager.dispatchRawDelta(-dx) }
        }
    )
}

