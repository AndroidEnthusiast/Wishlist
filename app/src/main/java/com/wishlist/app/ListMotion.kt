package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/* The list as a whole when its view or order changes (owner, 25 November 2026); a card's own motion is CardMotion.kt. */

/**
 * Changing the view (Rows, Two per row, Large cards): the new layout fades in with a small settle (owner, 25 November
 * 2026). Cards reshaping one by one looked muddled in the frame check, so the whole list fades through instead; a
 * change of order still glides card by card (animateItem). Nothing the first time the list shows.
 */
fun Modifier.fadeThroughOn(key: Any): Modifier = composed {
    val a = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        a.snapTo(0f)
        a.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 10f * density }
}

/**
 * How a card glides to a new place: when the order changes it glides (owner, 25 November 2026); right after the view
 * changed it doesn't (the whole list fades through then, [fadeThroughOn]), so cards never slide across each other.
 */
@Composable
fun glideUnless(view: String): androidx.compose.animation.core.FiniteAnimationSpec<IntOffset>? {
    var calmFor by remember { mutableStateOf(view) }
    LaunchedEffect(view) { delay(400); calmFor = view }
    return if (calmFor != view) null
    else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))
}
