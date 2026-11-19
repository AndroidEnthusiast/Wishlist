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
import androidx.compose.material3.DropdownMenuItem
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
 * The view switch above the wishes and a wish's ⋮ button (split out of ListScreen.kt in the rebuild, owner 19 November
 * 2026; MAP "How a screen is built").
 */

/** One of the three view buttons in the row above the wishes. */
@Composable
fun ViewButton(kind: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    // The classic themes show only the chosen one filled (as before); the designs frame both, as buttons.
    val framed = true
    Box(
        Modifier.size(40.dp)
            .then(if (framed) Modifier.lookChip(look, on = selected) else Modifier.clip(look.chipShape))
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ViewIcon(kind, look.chipContent(selected))
    }
}

/**
 * ⋮ on a wish in Rows and Large cards (owner, 2.135; a sheet since 12 November 2026, WishSheet.kt): the same sheet as
 * in Two per row. The bin stays next to it.
 */
@Composable
fun WishMoreButton(w: Wish, tint: Color, ico: androidx.compose.ui.unit.Dp, actions: WishActions) {
    var menu by remember { mutableStateOf(false) }
    IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More"), tint = tint, modifier = Modifier.size(ico)) }
    if (menu) WishSheet(w, actions) { menu = false }
}

/** The view in use as one icon; tapping it opens the three views with their names (owner, 2.130). */
@Composable
fun ViewMenuButton(view: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val names = listOf("rows" to tr("Rows"), "grid" to tr("Two per row"), "list" to tr("Large cards"))
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .semantics { contentDescription = tr("View: %s", names.firstOrNull { it.first == view }?.second ?: "") }
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) { ViewIcon(view, cs.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { (k, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (k == view) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { ViewIcon(k, if (k == view) cs.primary else cs.onSurfaceVariant) },
                    trailingIcon = if (k == view) ({ Icon(AppIcons.Check, null) }) else null,
                    onClick = { open = false; onPick(k) }
                )
            }
        }
    }
}

/** Tiny drawn icons that look like each view: Rows, Two per row (a 2x2 grid) and Large cards. */
@Composable
fun ViewIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color = tint, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u),
            cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
        )
        when (kind) {
            // Large cards: one big picture with a line of text under it.
            "list" -> { box(3f, 3f, 18f, 12f); box(3f, 17f, 18f, 2.5f); box(3f, 20.5f, 11f, 2.5f) }
            // Rows: a small square with two lines next to it, twice.
            "rows" -> {
                box(3f, 3f, 8f, 8f); box(13f, 4f, 8f, 2.5f); box(13f, 7.5f, 5f, 2.5f)
                box(3f, 13f, 8f, 8f); box(13f, 14f, 8f, 2.5f); box(13f, 17.5f, 5f, 2.5f)
            }
            else -> { box(3f, 3f, 8f, 8f); box(13f, 3f, 8f, 8f); box(3f, 13f, 8f, 8f); box(13f, 13f, 8f, 8f) }
        }
    }
}
