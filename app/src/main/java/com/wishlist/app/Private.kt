package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The eye with a line through it: "nobody else can see this". Our own rounded-line drawing since 2.210 (it was Google's
 * "visibility_off"; owner, 15 November 2026: the app's own icons, docs/styles/identity.md).
 */
val PrivateIcon: ImageVector get() = QuietIcons.eyeOff

/**
 * The open eye: "friends can see this wish" (owner, 2.126: the card's button shows an open eye on a visible wish and the
 * crossed-out eye on a private one, never in blue). Our own drawing since 2.210.
 */
val VisibleIcon: ImageVector get() = QuietIcons.eye

/**
 * The round badge on a private wish, in every view. Since 25 November 2026 (owner: "the eye's lid closes when a wish
 * becomes private, opens when visible"): it appears with the lid closing, and when the wish becomes visible again the
 * lid opens as the badge goes. Shown as it is when the list opens (no animation then). [shown]: the wish is private.
 */
@Composable
fun PrivateBadge(modifier: Modifier = Modifier, size: Dp = 18.dp, onClick: (() -> Unit)? = null, shown: Boolean = true) {
    androidx.compose.animation.AnimatedVisibility(
        shown, modifier,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220), initialScale = 0.7f),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200, delayMillis = 260)) +
            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200, delayMillis = 260), targetScale = 0.8f)
    ) {
        val closed by transition.animateFloat(
            transitionSpec = { androidx.compose.animation.core.tween(320, delayMillis = if (targetState == androidx.compose.animation.EnterExitState.Visible) 60 else 0, easing = androidx.compose.animation.core.FastOutSlowInEasing) },
            label = "lid"
        ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
        // A light shadow keeps it visible on a white product picture. Tapping it (the owner) makes the wish visible again.
        val m = if (onClick != null) Modifier.clip(CircleShape).clickable(onClickLabel = tr("Visible to friends"), role = Role.Button, onClick = onClick) else Modifier
        Surface(color = MaterialTheme.colorScheme.background, shape = CircleShape, shadowElevation = 2.dp, modifier = m) {
            Eye(closed, MaterialTheme.colorScheme.onBackground, Modifier.padding(5.dp).size(size), tr("Private wish"))
        }
    }
}

/**
 * Our own eye with a lid (owner, 25 November 2026): [closed] 0 is open (friends can see it), 1 is shut with its
 * lashes (only you); in between the lid comes down over the eye. Rounded lines, like the app's icons.
 */
@Composable
fun Eye(closed: Float, tint: Color, modifier: Modifier = Modifier, label: String? = null) {
    val chaos = LocalLook.current.design == "chaos"
    androidx.compose.foundation.Canvas(if (label != null) modifier.semantics { contentDescription = label } else modifier) {
        val u = this.size.minDimension / 24f
        val c = closed.coerceIn(0f, 1f)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(
            (if (chaos) 2.3f else 1.8f) * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        // The lower lid stays; the upper lid comes down until it lies on the lower one.
        val topY = (3f + 17f * c) * u
        val lower = androidx.compose.ui.graphics.Path().apply { moveTo(2f * u, 12f * u); quadraticTo(12f * u, 20f * u, 22f * u, 12f * u) }
        val upper = androidx.compose.ui.graphics.Path().apply { moveTo(2f * u, 12f * u); quadraticTo(12f * u, topY, 22f * u, 12f * u) }
        // The pupil, only where the eye is still open.
        if (c < 0.98f) {
            val inside = androidx.compose.ui.graphics.Path().apply {
                moveTo(2f * u, 12f * u); quadraticTo(12f * u, topY, 22f * u, 12f * u); quadraticTo(12f * u, 20f * u, 2f * u, 12f * u); close()
            }
            clipPath(inside) { drawCircle(tint, 3.2f * u, androidx.compose.ui.geometry.Offset(12f * u, 12f * u)) }
        }
        drawPath(lower, tint, style = st)
        drawPath(upper, tint, style = st)
        // The lashes come as it shuts.
        val lash = ((c - 0.75f) / 0.25f).coerceIn(0f, 1f)
        if (lash > 0f) for ((x, dx) in listOf(7f to -1.2f, 12f to 0f, 17f to 1.2f)) {
            val y = if (x == 12f) 16f else 14.8f
            drawLine(tint.copy(alpha = lash), androidx.compose.ui.geometry.Offset(x * u, y * u), androidx.compose.ui.geometry.Offset((x + dx) * u, (y + 2.6f) * u), st.width, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** The eye for a switch or menu row about privacy: it shuts or opens when [private] changes. */
@Composable
fun PrivacyEye(private: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val c by androidx.compose.animation.core.animateFloatAsState(if (private) 1f else 0f, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "eye")
    Eye(c, tint, modifier)
}

/**
 * "Make private" / "Visible to friends" on a wish of My wishlist (the menus, or tapping the eye). The main screen
 * then shows "Wish is now private" / "Wish is now visible to friends" for 5 seconds, with Undo ([PrivateHost]).
 */
object PrivateFlow {
    /** The latest change that can still be undone: the wish and whether it became private. */
    var changed by mutableStateOf<Pair<Long, Boolean>?>(null)
        private set

    fun set(c: Context, w: Wish, on: Boolean) {
        if (w.priv == on) return
        Repo.setPrivate(c, w.id, on)
        changed = w.id to on
    }

    fun undo(c: Context) {
        val (id, on) = changed ?: return
        changed = null
        Repo.setPrivate(c, id, !on)
    }

    fun done() {
        changed = null
    }
}

fun togglePrivate(c: Context, w: Wish) = PrivateFlow.set(c, w, !w.priv)

/** The message with Undo after a wish was made private or visible. Placed once, in the main screen, like DeleteHost. */
@Composable
fun PrivateHost(snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val ch = PrivateFlow.changed
    LaunchedEffect(ch) {
        if (ch == null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        // Material only has 4 or 10 seconds: this one stays 5.
        val timer = launch { delay(5000); snack.currentSnackbarData?.dismiss() }
        val r = snack.showToast(
            message = if (ch.second) tr("Wish is now private") else tr("Wish is now visible to friends"),
            actionLabel = tr("Undo"),
            duration = SnackbarDuration.Indefinite
        )
        timer.cancel()
        if (r == SnackbarResult.ActionPerformed) PrivateFlow.undo(ctx) else if (PrivateFlow.changed === ch) PrivateFlow.done()
    }
}
