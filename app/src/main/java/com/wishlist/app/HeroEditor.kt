package com.wishlist.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * How the dashboard picture is shown: [scale] (1 = the photo just covers the frame), how far it is moved ([x], [y],
 * as parts of the frame's width and height) and an optional [text] with the place of its middle ([tx], [ty], 0 to 1).
 * Kept as numbers (not a new picture), so the photo can be adjusted again later.
 */
data class HeroEdit(
    val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f,
    val text: String = "", val tx: Float = 0.5f, val ty: Float = 0.75f
) {
    fun toJson(): String = JSONObject().put("scale", scale.toDouble()).put("x", x.toDouble()).put("y", y.toDouble())
        .put("text", text).put("tx", tx.toDouble()).put("ty", ty.toDouble()).toString()

    companion object {
        fun fromJson(s: String?): HeroEdit = try {
            val o = JSONObject(s ?: "{}")
            HeroEdit(
                o.optDouble("scale", 1.0).toFloat(), o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.0).toFloat(),
                o.optString("text", ""), o.optDouble("tx", 0.5).toFloat(), o.optDouble("ty", 0.75).toFloat()
            )
        } catch (e: Exception) {
            HeroEdit()
        }
    }
}

private val heroTextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/**
 * The dashboard picture in its frame, as [edit] says. With [onChange] it can be adjusted: pinch to zoom, drag to move
 * the photo, drag the text to place it. The photo always covers the whole frame.
 */
@Composable
fun HeroPicture(image: String, edit: HeroEdit, modifier: Modifier, onChange: ((HeroEdit) -> Unit)? = null) {
    val painter = rememberAsyncImagePainter(image)
    val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val density = LocalDensity.current
    val current by rememberUpdatedState(edit)
    val change by rememberUpdatedState(onChange)
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val aspect = drawable?.let { d ->
            if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) d.intrinsicWidth.toFloat() / d.intrinsicHeight else null
        } ?: (w / h)
        // The photo's size when it just covers the frame (scale 1).
        val bw = if (aspect > w / h) h * aspect else w
        val bh = if (aspect > w / h) h else w / aspect
        fun clampX(x: Float, s: Float): Float {
            val m = ((bw * s - w) / 2f).coerceAtLeast(0f) / w
            return x.coerceIn(-m, m)
        }
        fun clampY(y: Float, s: Float): Float {
            val m = ((bh * s - h) / 2f).coerceAtLeast(0f) / h
            return y.coerceIn(-m, m)
        }
        val s = edit.scale.coerceIn(1f, 5f)
        val tx = clampX(edit.x, s) * w
        val ty = clampY(edit.y, s) * h
        Image(
            painter, null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.align(Alignment.Center)
                .requiredSize(with(density) { bw.toDp() }, with(density) { bh.toDp() })
                .graphicsLayer { scaleX = s; scaleY = s; translationX = tx; translationY = ty }
        )
        if (onChange != null) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val e = current
                        val ns = (e.scale * zoom).coerceIn(1f, 5f)
                        change?.invoke(e.copy(scale = ns, x = clampX(e.x + pan.x / w, ns), y = clampY(e.y + pan.y / h, ns)))
                    }
                }
            )
        }
        if (edit.text.isNotBlank()) {
            var size by remember { mutableStateOf(IntSize.Zero) }
            Text(
                edit.text, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall.copy(shadow = heroTextShadow),
                modifier = Modifier
                    .onSizeChanged { size = it }
                    .offset {
                        IntOffset(
                            (edit.tx * w - size.width / 2f).roundToInt(),
                            (edit.ty * h - size.height / 2f).roundToInt()
                        )
                    }
                    .then(
                        if (onChange == null) Modifier
                        else Modifier.pointerInput(Unit) {
                            detectDragGestures { ch, drag ->
                                ch.consume()
                                val e = current
                                change?.invoke(
                                    e.copy(tx = (e.tx + drag.x / w).coerceIn(0f, 1f), ty = (e.ty + drag.y / h).coerceIn(0f, 1f))
                                )
                            }
                        }
                    )
            )
        }
    }
}

/**
 * Adjusting a new (or the current) dashboard picture before it is saved: the frame is exactly as on the dashboard.
 * Pinch to zoom, drag to move, add text if you like and drag it into place. Nothing changes until Save.
 */
@Composable
fun HeroEditor(image: String, start: HeroEdit, onSave: (HeroEdit) -> Unit, onCancel: () -> Unit) {
    var edit by remember { mutableStateOf(start) }
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text(tr("Cancel")) }
                    Text(
                        tr("Adjust the picture"), style = MaterialTheme.typography.titleLarge,
                        fontWeight = LocalLook.current.titleWeight, modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    Button(onClick = { onSave(edit) }) { Text(tr("Save")) }
                }
                Text(
                    tr("Pinch to zoom and drag to move the photo. Drag the text to place it."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                // Same width and height as on the dashboard (16 dp from the sides, 150 dp high).
                HeroPicture(
                    image, edit,
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)),
                    onChange = { edit = it }
                )
                OutlinedTextField(
                    edit.text, { edit = edit.copy(text = it.take(40)) },
                    label = { Text(tr("Text on the picture")) }, placeholder = { Text(tr("Optional")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                if (edit != HeroEdit(text = edit.text, tx = edit.tx, ty = edit.ty)) {
                    TextButton(
                        onClick = { edit = edit.copy(scale = 1f, x = 0f, y = 0f) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text(tr("Reset zoom and position")) }
                }
            }
        }
    }
}
