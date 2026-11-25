package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The app's little real objects (owner, 24 November 2026: the app's own "thing"; docs/decisions/look.md): a small
 * family drawn the same way, with the same light from the top left, shown only at their moment and at rest on the
 * empty pages that fit them. The golden padlock is in Padlock.kt; here the "Got it!" stamp. (A price tag was tried in
 * 2.289 and taken out again by the owner on 25 November 2026.)
 * "Remove animations" shows each one at rest.
 */

/** Berry, the app's own colour, as ink: lighter in dark mode so it reads on a dark card. */
@Composable
internal fun berryInk(): Color = if (LocalLook.current.dark) Color(0xFFF0739F) else Color(0xFFB0245A)

/**
 * The "Got it!" stamp: Berry ink in a double frame, slightly turned. With [press] it comes down from above and lands
 * with a small squash (about a third of a second) and, if [buzz], one soft vibration as it lands.
 */
@Composable
fun GotItStamp(modifier: Modifier = Modifier, press: Boolean = false, buzz: Boolean = false, big: Boolean = true) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val ink = berryInk()
    val go = press && !animationsOff(ctx)
    val s = remember { Animatable(if (go) 1.8f else 1f) }
    val a = remember { Animatable(if (go) 0f else 1f) }
    LaunchedEffect(go) {
        if (!go) return@LaunchedEffect
        a.animateTo(1f, tween(140))
        s.animateTo(0.94f, tween(170, easing = FastOutSlowInEasing))
        if (buzz) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        s.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    val frame = if (big) 3.dp else 2.dp
    Box(
        modifier
            .graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value; rotationZ = -12f }
            .border(frame, ink, MaterialTheme.shapes.small)
            .padding(3.dp)
            .border(1.dp, ink.copy(alpha = 0.7f), MaterialTheme.shapes.small)
            .padding(horizontal = if (big) 14.dp else 8.dp, vertical = if (big) 4.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tr("Got it!"), color = ink, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1, softWrap = false,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge
        )
    }
}

/** The stamp over a wish that is on its way to Received gifts: the card fades back a little under it. */
@Composable
fun StampOverlay(modifier: Modifier = Modifier, big: Boolean = true, buzz: Boolean = true) {
    val bg = MaterialTheme.colorScheme.surface
    // Over the card on purpose, and said aloud by TalkBack (a live region), like a message that floats over the screen.
    Box(
        modifier.drawBehind { drawRect(bg.copy(alpha = 0.7f)) }
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        GotItStamp(press = true, buzz = buzz, big = big)
    }
}

/**
 * The objects at rest on empty pages (owner, 24 November 2026), inside the 80 dp soft circle of [EmptyState]. They are
 * pictures, like an icon: a fixed size that doesn't grow with the phone's text size, so they always fit the circle.
 */
@Composable
private fun AtRest(content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1f)) {
        Box(Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f }.semantics { contentDescription = "" }) { content() }
    }
}

@Composable
fun RestingPadlock() = Padlock(1f, Modifier.size(46.dp))

@Composable
fun RestingStamp() = AtRest { GotItStamp(big = false) }
