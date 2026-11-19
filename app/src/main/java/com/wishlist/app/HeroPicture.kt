package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlin.math.roundToInt

/* The photo editor (see HeroEdit.kt): a cover photo in its frame, as an edit says. */

/**
 * The cover photo in its frame, as [edit] says. With [onChange] it can be edited: pinch to zoom, drag to move the
 * photo, drag the text to place it. The photo always covers the whole frame. [grid]: thirds lines to line things up.
 */
@Composable
fun HeroPicture(
    image: String, edit: HeroEdit, modifier: Modifier, onChange: ((HeroEdit) -> Unit)? = null, grid: Boolean = false,
    showOutside: Boolean = false, cut: String = ""
) {
    val painter = rememberAsyncImagePainter(image)
    val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val density = LocalDensity.current
    val current by rememberUpdatedState(edit)
    val change by rememberUpdatedState(onChange)
    // showOutside (the editor): the whole photo shows, the part outside the frame dimmed, so you see what you cut off.
    val dim = Color.Black.copy(alpha = 0.6f)
    BoxWithConstraints(
        if (showOutside) modifier.drawWithContent {
            drawContent()
            val big = 4000f
            drawRect(dim, Offset(-big, -big), Size(size.width + 2 * big, big))
            drawRect(dim, Offset(-big, size.height), Size(size.width + 2 * big, big))
            drawRect(dim, Offset(-big, 0f), Size(big, size.height))
            drawRect(dim, Offset(size.width, 0f), Size(big, size.height))
            drawRect(Color.White, Offset.Zero, size, style = Stroke(1.5.dp.toPx()))
        } else modifier.clipToBounds()
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val sideways = edit.rotation % 180 != 0
        val raw = drawable?.let { d ->
            if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) d.intrinsicWidth.toFloat() / d.intrinsicHeight else null
        } ?: (w / h)
        // The photo as it looks after turning it.
        val aspect = if (sideways) 1f / raw else raw
        // Its size when it just covers the frame (scale 1).
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
        val s = edit.scale.coerceIn(1f, 8f)
        val tx = clampX(edit.x, s) * w
        val ty = clampY(edit.y, s) * h
        // Turned sideways, the picture is drawn with width and height swapped and then turned.
        val dw = if (sideways) bh else bw
        val dh = if (sideways) bw else bh
        // The saved cover (exactly the part that was in the frame, turned and lightened) when there is one.
        if (cut.isNotEmpty()) Image(
            rememberAsyncImagePainter(cut), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()
        ) else Image(
            painter, null, contentScale = ContentScale.FillBounds, colorFilter = brightness(edit.bright),
            modifier = Modifier.align(Alignment.Center)
                .requiredSize(with(density) { dw.toDp() }, with(density) { dh.toDp() })
                .graphicsLayer {
                    scaleX = s; scaleY = s; rotationZ = edit.rotation.toFloat()
                    translationX = tx; translationY = ty
                }
        )
        if (grid) {
            Canvas(Modifier.matchParentSize()) {
                val c = Color.White.copy(alpha = 0.55f)
                for (i in 1..2) {
                    drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
                    drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
                }
            }
        }
        if (onChange != null && !showOutside) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val e = current
                        val ns = (e.scale * zoom).coerceIn(1f, 8f)
                        change?.invoke(e.copy(scale = ns, x = clampX(e.x + pan.x / w, ns), y = clampY(e.y + pan.y / h, ns)))
                    }
                }
            )
        }
        if (edit.text.isNotBlank()) {
            var size by remember { mutableStateOf(IntSize.Zero) }
            val col = Color(edit.color)
            val boxCol = if (col.luminance() > 0.5f) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f)
            Text(
                edit.text, color = col, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall.copy(
                    shadow = if (edit.box) null else heroTextShadow,
                    fontSize = with(density) { (h * edit.textSize).toSp() }
                ),
                modifier = Modifier
                    .onSizeChanged { size = it }
                    .offset {
                        IntOffset(
                            (edit.tx * w - size.width / 2f).roundToInt(),
                            (edit.ty * h - size.height / 2f).roundToInt()
                        )
                    }
                    .then(
                        if (edit.box) Modifier.clip(RoundedCornerShape(8.dp)).background(boxCol)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                        else Modifier
                    )
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
