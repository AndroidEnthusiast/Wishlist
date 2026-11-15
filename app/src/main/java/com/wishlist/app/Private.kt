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

/** The private-or-visible button on a wish (large cards and Rows): the eye that says how it is now. */
@Composable
fun PrivateToggle(w: Wish, tint: Color, iconSize: Dp = 24.dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.IconButton(onClick = { togglePrivate(ctx, w) }) {
        Icon(
            if (w.priv) PrivateIcon else VisibleIcon,
            if (w.priv) tr("Private wish. Make visible to friends") else tr("Visible to friends. Make private"),
            tint = tint, modifier = Modifier.size(iconSize)
        )
    }
}

/** The round badge in the top left corner of a private wish, in both views. */
@Composable
fun PrivateBadge(modifier: Modifier = Modifier, size: Dp = 18.dp, onClick: (() -> Unit)? = null) {
    // A light shadow keeps it visible on a white product picture. Tapping it (the owner) makes the wish visible again.
    val m = if (onClick != null) modifier.clip(CircleShape).clickable(onClickLabel = tr("Visible to friends"), role = Role.Button, onClick = onClick) else modifier
    Surface(color = MaterialTheme.colorScheme.background, shape = CircleShape, shadowElevation = 2.dp, modifier = m) {
        Icon(
            PrivateIcon, tr("Private wish"), tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(5.dp).size(size)
        )
    }
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
