package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/*
 * One wish on the open list (split out of ListScreen.kt in the rebuild, owner 19 November 2026; MAP "How a screen is
 * built"): its card in the chosen view (Rows, Two per row, Large cards), the dragged card following the finger, the
 * soft glow of a wish opened from elsewhere, the tick while selecting, and its buttons (CardButtons). What a tap
 * changes goes through [ListModel].
 */
@Composable
internal fun androidx.compose.foundation.lazy.grid.LazyGridItemScope.ListWishItem(
    w: Wish, r: Boolean?, view: String, inShared: Boolean, model: ListModel, drag: DragState, onHold: (Wish) -> Unit
) {
    val ctx = LocalContext.current
    // A co-op list: the counts of a 2× wish, and what the reservation button shows (owner, 23 November 2026).
    val c = if (inShared) ResCounts.of(Shared.active.orEmpty(), w.id) else null
    val rs = if (inShared) reserveState(w, c, r) else null
    val look = LocalLook.current
    val dragged = w.id == drag.dragId && drag.dragArmed
    // Where each card is (for the drag), then the dragged one follows the finger, lifted a little.
    Box(
        Modifier.zIndex(if (dragged) 1f else 0f)
            .then(if (dragged) Modifier else Modifier.animateItem())
            .onGloballyPositioned { drag.cardBounds[w.id] = androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize()) }
            .graphicsLayer {
                if (dragged) {
                    val at = drag.cardBounds[w.id]?.topLeft ?: drag.dragPointer
                    val to = drag.dragPointer - drag.dragGrab - at
                    translationX = to.x; translationY = to.y
                    scaleX = 1.03f; scaleY = 1.03f
                    shadowElevation = 12.dp.toPx()
                }
            }
    ) {
    // A wish opened from "All your wishes" glows softly twice (WishFocus).
    GlowBox(w.id, small = view == "grid") {
    Selectable(model.selecting, w.id in model.picked, { model.toggle(w) }) {
    when (view) {
        "grid" -> CompactCard(
            // Two per row: the ⋮ holds everything; a long press starts selecting (every list).
            w, onLongPress = { onHold(w) },
            onStar = if (inShared) null else ({ model.toggleLove(w) }),
            reserve = if (inShared) Reserving(r, reserveLine(w, c), c) { on -> model.reserve(w, on) } else null,
            onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
            onMove = { MoveFlow.ask(setOf(w.id)) },
            chatAbout = if (inShared) chatOnShared(w) else null,
            onHistory = if (inShared || w.url.isBlank()) null else ({ model.historyId = w.id })
        ) { model.editing = w }
        // The buttons of Rows and Large cards: Share, the eye (or Reserve on a gift list), Delete.
        else -> {
        // Rows has smaller icons than the large cards (owner, 2.127: bigger than the private badge,
        // smaller than before); the tap area stays the same.
        // Quiet (2.207): 20 dp in every view, so the buttons stay quieter than the wish.
        val ico = cardIcon(view)
        // Reserve left, then share, the bin and the ⋮ last (owner, 18 November 2026; CardButtons).
        val actions: @Composable RowScope.() -> Unit = { Row(Modifier.hiddenWhileSelecting().then(if (view == "rows" && inShared) Modifier.weight(1f) else Modifier), verticalAlignment = Alignment.CenterVertically) {
            CardButtons(
                spread = view == "rows",
                // Reserving takes the place of "got it" on a shared list.
                // One button for every state (owner, 23 November 2026; ReserveButton).
                reserve = rs?.let { state -> {
                    ReserveButton(w, state, reserveButtonText(w, c, state), { on -> model.reserve(w, on) }, onTaken = LocalWishDeco.current?.onBadge?.let { b -> { b(w) } })
                } },
                icons = {
                    // Share as its own button too (owner, 12 November 2026: sharing a wish should be one tap).
                    // On a shared wishlist with Reserve showing, Reserve + share + bin + ⋮ don't fit:
                    // Share is then only in the ⋮ (owner, 18 November 2026).
                    if (rs == null) IconButton(onClick = { shareItem(ctx, w, idea = inShared) }) {
                        Icon(AppIcons.CardShare, tr("Share"), tint = look.cardMuted(w.id), modifier = Modifier.size(ico))
                    }
                    IconButton(onClick = { DeleteFlow.request(ctx, w) }) {
                        Icon(AppIcons.CardDelete, tr("Delete"), tint = look.cardMuted(w.id), modifier = Modifier.size(ico))
                    }
                }
            ) {
                // The bin stays, the rest is in one ⋮ (owner, 2.135: calmer rows), the same sheet as Two
                // per row on every list (12 November 2026; a collab wishlist had only Share here).
                WishMoreButton(
                    w, look.cardMuted(w.id), ico,
                    if (inShared) WishActions(
                        reserve = Reserving(r, reserveLine(w, c), c) { on -> model.reserve(w, on) },
                        onEdit = { model.editing = w }, onMove = { MoveFlow.ask(setOf(w.id)) }, shareAsIdea = true,
                        chatAbout = chatOnShared(w)
                    ) else WishActions(
                        onEdit = { model.editing = w }, onMove = { MoveFlow.ask(setOf(w.id)) }, own = true,
                        onHistory = if (w.url.isBlank()) null else ({ model.historyId = w.id })
                    )
                )
            }
        } }
        if (view == "rows") RowCard(
            w,
            badge = if (inShared) reserveLine(w, c) else if (w.got) tr("Owned") else null,
            dim = if (inShared) rs == "taken" else w.got,
            onLongPress = { onHold(w) },
            onStar = if (inShared) null else ({ model.toggleLove(w) }),
            onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
            actions = actions
        ) else WishCard(
            w,
            badge = if (inShared) reserveLine(w, c) else if (w.got) tr("Owned") else null,
            dim = if (inShared) rs == "taken" else w.got,
            onLongPress = { onHold(w) },
            onStar = if (inShared) null else ({ model.toggleLove(w) }),
            onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
            actions = actions
        )
        }
    }
    }
    }
    }
}

/**
 * "Chat about this wish" on the open shared wishlist (owner, 21 November 2026): who to chat with is asked next
 * (ChatPeople.kt). Not without the server, or on a list that isn't open as a shared one.
 */
internal fun chatOnShared(w: Wish): (() -> Unit)? {
    val code = Shared.active ?: return null
    if (!Config.enabled) return null
    return { ChatAboutFlow.ask(code, w) }
}
