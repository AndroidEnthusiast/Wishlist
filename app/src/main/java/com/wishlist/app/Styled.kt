package com.wishlist.app

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
    // Measured as it is laid out: too wide, it is laid out again a step smaller (5 %), and only drawn once it fits, so
    // nobody sees the steps. (Not with BoxWithConstraints: it crashes inside rows that ask for their height, 2.209.)
    var scale by remember(text, style) { mutableStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text, style = style.copy(fontSize = style.fontSize * scale), color = color, maxLines = 1, softWrap = false,
        textAlign = align,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && scale > 0.6f) scale = (scale - 0.05f).coerceAtLeast(0.6f) else ready = true
        }
    )
}
