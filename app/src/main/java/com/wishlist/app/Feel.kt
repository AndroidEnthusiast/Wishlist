package com.wishlist.app

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * The app's own tap feel (owner, 15 November 2026, docs/styles/identity.md: "soft and springy", no Android ripple).
 * Two parts:
 * - [SoftPress]: every tappable thing gets a soft, quick shade while pressed instead of the ripple wave.
 * - [pressScale]: cards and buttons sink a little (to 97 %) while pressed and spring back with a slight bounce.
 * "Remove animations" on the phone turns the sinking off (the shade stays: it is feedback, not movement).
 */

/** Whether the phone asks for no animations (Settings > Accessibility > Remove animations). */
fun animationsOff(c: android.content.Context): Boolean = try {
    Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) { false }

/** The shade while pressed, in the content's own ink (so it shows on light and dark). */
object SoftPress : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SoftPressNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}

private class SoftPressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private val shade = Animatable(0f)
    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                }
                launch { shade.animateTo(if (presses > 0) 1f else 0f, tween(if (presses > 0) 90 else 220)) }
            }
        }
        coroutineScope.launch { androidx.compose.runtime.snapshotFlow { shade.value }.collect { invalidateDraw() } }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (shade.value > 0f) {
            val ink = currentValueOf(LocalLook).scheme.onSurface
            drawRect(ink.copy(alpha = 0.07f * shade.value))
        }
    }
}

/** Cards and buttons sink a little while pressed and spring back (owner: "soft and springy"). */
fun Modifier.pressScale(): Modifier = this then PressScaleElement

private object PressScaleElement : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode()
    override fun update(node: PressScaleNode) {}
    override fun hashCode() = 11
    override fun equals(other: Any?) = other === this
}

private class PressScaleNode : Modifier.Node(), PointerInputModifierNode, LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    private val scale = Animatable(1f)
    private var down = false
    private var off = false

    override fun onAttach() { off = animationsOff(currentValueOf(LocalContext)) }

    private fun sink(on: Boolean) {
        if (off || on == down) return
        down = on
        coroutineScope.launch {
            if (on) scale.animateTo(0.97f, tween(90))
            // Back with a slight bounce.
            else scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        // Only watched, never used up: taps, long presses and scrolling work exactly as before.
        if (pass != PointerEventPass.Final) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        when (pointerEvent.type) {
            PointerEventType.Press -> sink(true)
            PointerEventType.Release -> sink(false)
            PointerEventType.Move -> {
                // A scroll took over (it used the movement up) or the finger left: back up.
                val p: Offset = change.position
                if (change.isConsumed || p.x < 0 || p.y < 0 || p.x > bounds.width || p.y > bounds.height) sink(false)
            }
            PointerEventType.Exit -> sink(false)
        }
    }

    override fun onCancelPointerInput() = sink(false)

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        return layout(p.width, p.height) {
            p.placeWithLayer(0, 0) { scaleX = scale.value; scaleY = scale.value }
        }
    }
}
