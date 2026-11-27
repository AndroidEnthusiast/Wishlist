package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Quiet's own switch (owner, 15 November 2026, docs/styles/identity.md: Google's switch was one of the "standard
 * Android" parts): a slightly wider track, a squircle knob that springs across, your colour when on. No vibration
 * (owner: only at a few relevant moments). TalkBack reads it as a switch, as before. 52 × 32, in a 48 dp touch area.
 */
@Composable
fun QuietSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val look = LocalLook.current
    val off = animationsOff(LocalContext.current)
    val x by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        if (off) tween(0) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "knob"
    )
    val track by animateColorAsState(
        // Off: the line colour; in dark a lighter grey, so the track shows on a sheet's raised cards too (the review of
        // 20 November 2026: only the white knob was visible there).
        if (checked) look.accent else if (look.dark) look.scheme.onSurface.copy(alpha = 0.34f) else look.line,
        if (off) tween(0) else tween(180), label = "track"
    )
    val tap = if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    else Modifier
    Box(modifier.then(tap).size(width = 56.dp, height = 48.dp).alpha(if (enabled) 1f else 0.4f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 52.dp, height = 32.dp).clip(Squircle(16.dp)).background(track)) {
            Box(
                Modifier.offset(x = x, y = 3.dp).size(26.dp)
                    .shadow(2.dp, Squircle(10.dp), clip = false).clip(Squircle(10.dp)).background(Color.White)
            )
        }
    }
}

/**
 * Quiet's own dialog (owner, 15 November 2026): centred, the title in Fraunces, the text centred, the buttons full
 * width and stacked with the main one filled ([LocalDialogSlot]); it opens with a soft spring. Forms and lists inside
 * keep their own left alignment where they set it (fields, rows). On white, a lifted warm grey in dark.
 */
@Composable
fun QuietDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    properties: DialogProperties,
    secondButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val look = LocalLook.current
    val off = animationsOff(LocalContext.current)
    val grow = remember { Animatable(if (off) 1f else 0.92f) }
    val show = remember { Animatable(if (off) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (off) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            launch { grow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
            launch { show.animateTo(1f, tween(140)) }
        }
    }
    Dialog(onDismissRequest, properties) {
        // Text and icons in the dialog take the page's ink unless they set their own: without this they were black on
        // the dark dialog ("Household" in Move to…, owner's screenshot, 15 November 2026).
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides look.scheme.onSurface) {
        Column(
            modifier.widthIn(max = 420.dp).fillMaxWidth().scale(grow.value).alpha(show.value)
                // Gen Z chaos keeps its own surface (thick line, hard shadow); Quiet its raised white / lifted grey.
                .then(if (look.design == "quiet") Modifier.clip(look.dialogShape).background(quietRaised(look, look.scheme.surface))
                    else Modifier.padding(6.dp).lookSurface(look, look.dialogShape, look.scheme.surfaceContainerHigh, depth = 10.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) { icon() }
            if (title != null) ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, color = look.scheme.onSurface)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) { title() }
            }
            if (text != null) ProvideTextStyle(
                MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center, color = look.scheme.onSurfaceVariant)
            ) {
                Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { text() }
            }
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // The filled main action only when there is a choice; a lone button ("Cancel", "Close", "Got it") is the
                // calm tonal one, never a loud Berry "Cancel" (found in the screenshots, 15 November 2026).
                // One button alone closes the dialog ("Close", "Got it", "Cancel"): plain words, like every Cancel
                // (owner, 21 November 2026; before, a tonal pill).
                CompositionLocalProvider(LocalDialogSlot provides if (dismissButton != null || secondButton != null) "confirm" else "only") { confirmButton() }
                if (secondButton != null) CompositionLocalProvider(LocalDialogSlot provides "second") { secondButton() }
                if (dismissButton != null) CompositionLocalProvider(LocalDialogSlot provides "dismiss") { dismissButton() }
            }
        }
        }
    }
}

