package com.wishlist.app

import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The app's own parts in the chosen style, split by kind in the rebuild (owner, 19 November 2026): buttons and the
 * switch (StyledButtons.kt), dialogs, top bar and badges (StyledBars.kt), the icons (AppIcons.kt), the "Saved" tick and
 * photos (StyledPhotos.kt), and here messages at the bottom, swiping away and text that fits.
 */

/**
 * Shows a message at the bottom (a "toast" with or without Undo). Every message stays 5 seconds ([AppSnackbarHost]
 * takes it away then), whatever duration is asked, and can be swiped away left or right before that (owner, 2.84).
 */
suspend fun SnackbarHostState.showToast(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    @Suppress("UNUSED_PARAMETER") duration: SnackbarDuration = SnackbarDuration.Indefinite
): SnackbarResult =
    // Right after "Please try again in a moment" (ServerNotice), a screen's own "Couldn't save. Check your connection."
    // would only cover it with something untrue; a message with a button (Undo) always shows.
    if (actionLabel == null && ServerNotice.covers(message)) SnackbarResult.Dismissed
    else showSnackbar(message, actionLabel, withDismissAction, SnackbarDuration.Indefinite)

/** The place where messages show: each one for 5 seconds, and swipe it left or right to dismiss it. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        LaunchedEffect(data) {
            delay(5000)
            data.dismiss()
        }
        SwipeAway(data, onGone = { data.dismiss() }) { ThemedSnackbar(data) }
    }
}

/**
 * Swipe it left or right (far enough) and it slides away; not far enough and it springs back. [trash]: while sliding,
 * a red block with a bin icon grows on the side that is uncovered (deleting a notification).
 */
@Composable
fun SwipeAway(key: Any, onGone: () -> Unit, trash: Boolean = false, content: @Composable () -> Unit) {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember(key) { mutableStateOf(1f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        if (trash && offset.value != 0f) {
            val shown = with(density) { kotlin.math.abs(offset.value).toDp() }
            Box(
                Modifier.matchParentSize().padding(vertical = 2.dp),
                contentAlignment = if (offset.value > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(
                    Modifier.fillMaxHeight().width(shown).clip(RoundedCornerShape(14.dp)).background(Color(0xFFD32F2F)),
                    contentAlignment = Alignment.Center
                ) {
                    if (shown > 32.dp) Icon(AppIcons.Delete, null, tint = Color.White)
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset.value
                    if (!trash) alpha = 1f - (kotlin.math.abs(offset.value) / width).coerceIn(0f, 1f) * 0.8f
                }
                .pointerInput(key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (kotlin.math.abs(offset.value) > width * 0.3f) {
                                    offset.animateTo(if (offset.value > 0) width else -width, tween(160))
                                    onGone()
                                } else offset.animateTo(0f, tween(180))
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } }
                    ) { ch, drag ->
                        ch.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    }
                }
        ) { content() }
    }
}

/**
 * One line of text that is never cut off (2.209, owner 15 November 2026: "€1,434.56" was cut off on Home in Gen Z
 * chaos, "unacceptable"): when it doesn't fit, it gets smaller, down to 60 % of [style]'s size, before anything is
 * cut. Used for numbers and amounts.
 */
@Composable
fun FitText(
    text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier,
    align: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center
) {
    // Measured once, in the space it gets, and drawn at once at the size that fits (the layout inspector, 20 November
    // 2026: the old way shrank in invisible steps, and an amount on Home stayed invisible when they didn't finish).
    // It answers questions about its height too, so it works inside rows that ask (BoxWithConstraints crashed there,
    // 2.209). Below 60 % it ends in "…" rather than being cut.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val holder = remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
    fun fit(maxW: Int): androidx.compose.ui.text.TextLayoutResult {
        val loose = androidx.compose.ui.unit.Constraints()
        val full = measurer.measure(text, style, maxLines = 1, softWrap = false, constraints = loose)
        val s = if (full.size.width <= maxW || maxW == androidx.compose.ui.unit.Constraints.Infinity) 1f
            else (maxW.toFloat() / full.size.width).coerceIn(0.6f, 1f)
        return measurer.measure(
            text, style.copy(fontSize = style.fontSize * s, color = color, textAlign = align), maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxW), layoutDirection = dir
        )
    }
    val policy = remember(text, style, color, align) {
        object : androidx.compose.ui.layout.MeasurePolicy {
            override fun androidx.compose.ui.layout.MeasureScope.measure(
                measurables: List<androidx.compose.ui.layout.Measurable>, constraints: androidx.compose.ui.unit.Constraints
            ): androidx.compose.ui.layout.MeasureResult {
                val r = fit(constraints.maxWidth)
                holder[0] = r
                val w = r.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
                return layout(w, r.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {}
            }
            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int) =
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int) =
                (measurer.measure(text, style, maxLines = 1, softWrap = false).size.width * 0.6f).toInt()
            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int) =
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.height
            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int) =
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.height
        }
    }
    androidx.compose.ui.layout.Layout(
        content = {},
        modifier = modifier
            .semantics {
                this.text = androidx.compose.ui.text.AnnotatedString(text)
                getTextLayoutResult { out -> holder[0]?.let { out.add(it) } != null }
            }
            .drawBehind {
                val r = holder[0] ?: return@drawBehind
                // Centred (or as [align] says) in the width it was given.
                val x = when (align) {
                    androidx.compose.ui.text.style.TextAlign.Center -> (size.width - r.size.width) / 2f
                    androidx.compose.ui.text.style.TextAlign.End, androidx.compose.ui.text.style.TextAlign.Right -> size.width - r.size.width
                    else -> 0f
                }
                drawText(r, topLeft = androidx.compose.ui.geometry.Offset(x, 0f))
            },
        measurePolicy = policy
    )
}
