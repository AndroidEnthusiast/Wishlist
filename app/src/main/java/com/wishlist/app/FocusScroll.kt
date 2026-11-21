package com.wishlist.app

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos

/*
 * Bringing a wish fully into sight before it glows (owner, 21 November 2026, a recording: after tapping a price-drop
 * notification or a wish in a chat, the wish glowed half hidden under the filters that stay at the top). Used by every
 * list that opens on a wish: your own and co-op lists (ListScreen.kt) and someone else's list (Friend.kt: a friend's,
 * a group member's, a giftee's), in every view.
 */

/**
 * How far to scroll so an item from [top] to [bottom] (px, in the list's own coordinates) sits whole between [from]
 * and [to]: 0 when it already does. Above (or taller than the space): its top goes to [from]; below: its bottom to [to].
 */
internal fun focusScrollBy(top: Int, bottom: Int, from: Int, to: Int): Int = when {
    top >= from && bottom <= to -> 0
    top < from || bottom - top > to - from -> top - from
    else -> bottom - to
}

/**
 * Scrolls item [index] fully into sight, below what stays pinned at the top ([cover], px, asked again after each
 * step because the pinned copy only appears, and is only measured, once the list has scrolled) and [margin] px clear
 * of the edges so the glow around it shows too. A few steps at most; it stops as soon as nothing needs to move.
 */
internal suspend fun LazyGridState.showWhole(index: Int, cover: () -> Int, margin: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) animateScrollToItem(index, -(cover() + margin))
    repeat(3) {
        // Let the pinned copy appear and be measured first.
        withFrameNanos { }; withFrameNanos { }
        val info = layoutInfo
        val it = info.visibleItemsInfo.firstOrNull { v -> v.index == index } ?: return
        val by = focusScrollBy(
            it.offset.y, it.offset.y + it.size.height,
            info.viewportStartOffset + cover() + margin, info.viewportEndOffset - margin
        )
        if (by == 0) return
        animateScrollBy(by.toFloat())
    }
}
