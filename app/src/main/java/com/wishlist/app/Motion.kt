package com.wishlist.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * How screens change (owner, 12 November 2026, "like Signal"; until then every change was instant):
 * - **Deeper** (a list, a chat, a friend's page, Profile, App settings…): the new screen slides in a short way from
 *   the right while it fades in; the old one moves a little to the left and fades. **Back** plays it in reverse.
 * - **Side by side** (the bottom bar's tabs, Home's own pages): a fade-through: the old screen fades out quickly, the
 *   new one fades in while growing slightly.
 * About a quarter of a second. Android's "Remove animations" (and the app's checks, which switch animations off) make
 * every change instant again: Compose follows the phone's animation scale by itself.
 * Predictive back (the peek while swiping) is not used (owner: not now).
 */
object Motion {
    const val MS = 250
    /** The fade-through's first part, the old screen fading out. */
    const val OUT_MS = 90

    enum class Kind { DEEPER, BACK, THROUGH }

    /** Which movement goes from a screen at depth [from] to one at depth [to]. */
    fun kind(from: Int, to: Int): Kind = when {
        to > from -> Kind.DEEPER
        to < from -> Kind.BACK
        else -> Kind.THROUGH
    }

    /**
     * How deep a route is: Home and the bar's tabs 0; a list, a group and Gift exchanges (opened from Home, bar still
     * there) 1; a screen on top (Profile, App settings, Bin, a friend's list from its link…) 2.
     */
    fun depthOf(route: String?): Int = when (route) {
        null, Route.MAIN -> 0
        in Route.tabs -> 0
        in Route.sections -> 1
        else -> 2
    }

    /**
     * The movement between two routes. A tab of the bottom bar always fades through (tapped from a list it is a
     * side step, not a step back), unless a screen on top closes back onto it.
     */
    fun routeKind(from: String?, to: String?): Kind =
        if (to in Route.tabs && depthOf(from) < 2) Kind.THROUGH else kind(depthOf(from), depthOf(to))

    // Material's "emphasized decelerate": quick start, soft landing.
    private val ease = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun enter(k: Kind): EnterTransition = when (k) {
        Kind.DEEPER -> slideInHorizontally(tween(MS, easing = ease)) { it / 4 } + fadeIn(tween(MS, easing = ease))
        Kind.BACK -> slideInHorizontally(tween(MS, easing = ease)) { -it / 10 } + fadeIn(tween(MS, easing = ease))
        Kind.THROUGH -> fadeIn(tween(MS - OUT_MS, delayMillis = OUT_MS, easing = ease)) +
            scaleIn(tween(MS - OUT_MS, delayMillis = OUT_MS, easing = ease), initialScale = 0.92f)
    }

    fun exit(k: Kind): ExitTransition = when (k) {
        Kind.DEEPER -> slideOutHorizontally(tween(MS, easing = ease)) { -it / 10 } + fadeOut(tween(MS / 2))
        Kind.BACK -> slideOutHorizontally(tween(MS, easing = ease)) { it / 4 } + fadeOut(tween(MS / 2))
        Kind.THROUGH -> fadeOut(tween(OUT_MS))
    }

    fun transform(k: Kind): ContentTransform = (enter(k) togetherWith exit(k)).apply {
        // Going back, the screen that leaves stays on top while it slides away.
        targetContentZIndex = if (k == Kind.BACK) -1f else 1f
    }
}

/**
 * A place inside a screen that changes without being a route of its own (the open chat in Messages, a friend's page,
 * an exchange, Home's pages, the Themer): [content] is drawn for [target], moving like a route would. [depth] says
 * how deep each state is (deeper slides in, shallower slides back, the same depth fades through); [key] says which
 * states count as the same screen (no movement between them).
 */
@Composable
fun <S> Moving(
    target: S,
    depth: (S) -> Int,
    modifier: Modifier = Modifier,
    key: (S) -> Any? = { it },
    content: @Composable (S) -> Unit
) {
    AnimatedContent(
        target, modifier,
        transitionSpec = { Motion.transform(Motion.kind(depth(initialState), depth(targetState))) },
        contentKey = key,
        label = "moving"
    ) { content(it) }
}
