package com.wishlist.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Drag to reorder (owner, 16 November 2026; split out of ListScreen.kt in the rebuild, 19 November 2026): hold a wish
 * and move your finger to drag it, or hold and let go to select it as before. While held, [DragState.holding] waits for
 * one or the other; [DragState.dragId] is the wish being dragged and [DragState.dragIds] the order on screen meanwhile.
 */
@Stable
internal class DragState {
    var holding by mutableStateOf<Wish?>(null)
    var dragId by mutableStateOf<Long?>(null)
    var dragIds by mutableStateOf<List<Long>?>(null)
    var dragArmed by mutableStateOf(false)
    var dragPointer by mutableStateOf(Offset.Zero)
    var dragGrab by mutableStateOf(Offset.Zero)
    /** The wish ticked by a hold: lifting that same finger isn't a tap that unticks it (cleared at the next touch). */
    var heldTick: Long? = null
    /** Where each card is on screen (in the root), for finding the card under the finger. */
    val cardBounds = mutableStateMapOf<Long, Rect>()
    /** The list's grid, for scrolling along near its edges. */
    var gridBox by mutableStateOf<LayoutCoordinates?>(null)

    fun reset() { holding = null; dragId = null; dragIds = null; dragArmed = false }
}

/**
 * The drag gesture on the list's grid. It reads before the cards (Initial pass): a card's own long press takes every
 * later move for itself, so the list would never see the drag. Until the finger really moves nothing is taken here, so
 * taps, the card's long press and scrolling work as before. [list]: the wishes on screen now. [onEnd]: the hold ended,
 * true when the finger was lifted (moved = keep the new order; not moved = select the wish).
 */
internal fun Modifier.reorderGesture(
    d: DragState, keys: Array<Any?>, enabled: () -> Boolean, list: () -> List<Wish>,
    gridState: LazyGridState, scope: CoroutineScope, onArm: () -> Unit = {}, onEnd: (Boolean) -> Unit
): Modifier = pointerInput(*keys) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        d.heldTick = null
        // Asked at each new touch (owner, 27 November 2026): the hold selects the wish at once, and this touch must go
        // on to a drag if the finger moves, so selecting may not restart the gesture.
        if (!enabled()) return@awaitEachGesture
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ended = c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        // A tap, a scroll or a lifted finger before the hold: not ours.
        if (early != null) return@awaitEachGesture
        val root = d.gridBox?.localToRoot(down.position) ?: return@awaitEachGesture
        val hit = d.cardBounds.entries.firstOrNull { it.value.contains(root) && list().any { w -> w.id == it.key } }
            ?: return@awaitEachGesture
        d.dragId = hit.key; d.dragIds = list().map { it.id }
        d.dragPointer = root; d.dragGrab = root - hit.value.topLeft
        var lifted = false
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) { lifted = true; if (d.dragArmed) c.consume(); break }
            d.dragPointer += c.position - c.previousPosition
            val id = hit.key
            // A real move, not a trembling finger: from now on it is a drag, not a selection.
            if (!d.dragArmed && (d.dragPointer - root).getDistance() > 12.dp.toPx()) { d.dragArmed = true; onArm() }
            if (!d.dragArmed) continue
            c.consume()
            val size = d.cardBounds[id]?.size ?: continue
            val centre = d.dragPointer - d.dragGrab + Offset(size.width / 2, size.height / 2)
            val target = d.cardBounds.entries.firstOrNull { it.key != id && it.value.contains(centre) && d.dragIds?.contains(it.key) == true }
            if (target != null) d.dragIds = d.dragIds?.let { moveInOrder(it, id, target.key) }
            // Near the top or bottom edge the list scrolls along.
            d.gridBox?.let { g ->
                val y = d.dragPointer.y - g.positionInRoot().y
                val edge = 64.dp.toPx()
                val speed = when {
                    y < edge -> -(edge - y) / 4f
                    y > g.size.height - edge -> (y - (g.size.height - edge)) / 4f
                    else -> 0f
                }
                if (speed != 0f) scope.launch { gridState.scrollBy(speed) }
            }
        }
        onEnd(lifted)
    }
}
