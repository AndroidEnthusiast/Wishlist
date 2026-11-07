package com.wishlist.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The free crop of your own wish photo (owner, 7 November 2026: "you want the user to know for sure the picture is
 * perfect"). The photo fills the screen; a frame with four corners lies on it. Drag a corner to resize the frame in any
 * shape, or drag inside it to move it. "Done" keeps only what is inside; "Use whole photo" (and Back) keeps it all.
 */
@Composable
fun CropDialog(src: String, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bmp by produceState<android.graphics.Bitmap?>(null, src) {
        value = withContext(Dispatchers.IO) { try { BitmapFactory.decodeFile(Uri.parse(src).path) } catch (e: Exception) { null } }
    }
    // The frame, as parts (0..1) of the photo's width and height.
    var l by remember { mutableStateOf(0f) }
    var t by remember { mutableStateOf(0f) }
    var r by remember { mutableStateOf(1f) }
    var b by remember { mutableStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    val handle = with(LocalDensity.current) { 36.dp.toPx() }
    val min = 0.12f

    Dialog(onDismissRequest = { if (!busy) onDone(src) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (!busy) onDone(src) }
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Text(
                tr("Drag the corners to crop your photo."), color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val pic = bmp
                if (pic == null) {
                    CircularProgressIndicator()
                } else {
                    // Where the photo sits on screen (it fits the box, centred).
                    val boxW = constraints.maxWidth.toFloat()
                    val boxH = constraints.maxHeight.toFloat()
                    val scale = minOf(boxW / pic.width, boxH / pic.height)
                    val pw = pic.width * scale
                    val ph = pic.height * scale
                    val ox = (boxW - pw) / 2
                    val oy = (boxH - ph) / 2
                    val image = remember(pic) { pic.asImageBitmap() }
                    Box(Modifier.fillMaxSize()) {
                        Image(image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(pic, pw, ph) {
                                // 0 move, 1 top-left, 2 top-right, 3 bottom-left, 4 bottom-right
                                var mode = -1
                                detectDragGestures(
                                    onDragStart = { p ->
                                        val corners = listOf(
                                            Offset(ox + l * pw, oy + t * ph), Offset(ox + r * pw, oy + t * ph),
                                            Offset(ox + l * pw, oy + b * ph), Offset(ox + r * pw, oy + b * ph)
                                        )
                                        val near = corners.withIndex().minByOrNull { (it.value - p).getDistance() }!!
                                        mode = when {
                                            (near.value - p).getDistance() < handle * 1.4f -> near.index + 1
                                            Rect(corners[0], corners[3]).contains(p) -> 0
                                            else -> -1
                                        }
                                    },
                                    onDrag = { change, d ->
                                        change.consume()
                                        val dx = d.x / pw
                                        val dy = d.y / ph
                                        when (mode) {
                                            0 -> {
                                                val mx = dx.coerceIn(-l, 1f - r)
                                                val my = dy.coerceIn(-t, 1f - b)
                                                l += mx; r += mx; t += my; b += my
                                            }
                                            1 -> { l = (l + dx).coerceIn(0f, r - min); t = (t + dy).coerceIn(0f, b - min) }
                                            2 -> { r = (r + dx).coerceIn(l + min, 1f); t = (t + dy).coerceIn(0f, b - min) }
                                            3 -> { l = (l + dx).coerceIn(0f, r - min); b = (b + dy).coerceIn(t + min, 1f) }
                                            4 -> { r = (r + dx).coerceIn(l + min, 1f); b = (b + dy).coerceIn(t + min, 1f) }
                                        }
                                    }
                                )
                            }
                        ) {
                            val fl = ox + l * pw; val ft = oy + t * ph; val fr = ox + r * pw; val fb = oy + b * ph
                            val dim = Color.Black.copy(alpha = 0.55f)
                            // Everything outside the frame is dimmed.
                            drawRect(dim, Offset(ox, oy), Size(pw, ft - oy))
                            drawRect(dim, Offset(ox, fb), Size(pw, oy + ph - fb))
                            drawRect(dim, Offset(ox, ft), Size(fl - ox, fb - ft))
                            drawRect(dim, Offset(fr, ft), Size(ox + pw - fr, fb - ft))
                            drawRect(Color.White, Offset(fl, ft), Size(fr - fl, fb - ft), style = Stroke(2.dp.toPx()))
                            // The corners: short thick L shapes.
                            val k = 22.dp.toPx(); val w = 4.dp.toPx()
                            listOf(Offset(fl, ft) to Offset(1f, 1f), Offset(fr, ft) to Offset(-1f, 1f),
                                Offset(fl, fb) to Offset(1f, -1f), Offset(fr, fb) to Offset(-1f, -1f)).forEach { (c, dir) ->
                                drawLine(Color.White, c, Offset(c.x + dir.x * k, c.y), w)
                                drawLine(Color.White, c, Offset(c.x, c.y + dir.y * k), w)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(enabled = !busy, onClick = { onDone(src) }) { Text(tr("Use whole photo"), color = Color.White) }
                Button(enabled = !busy && bmp != null, onClick = {
                    val whole = l <= 0.001f && t <= 0.001f && r >= 0.999f && b >= 0.999f
                    if (whole) onDone(src) else {
                        busy = true
                        scope.launch { onDone(ImageStore.crop(ctx, src, l, t, r, b) ?: src) }
                    }
                }) { Text(tr("Done")) }
            }
        }
    }
}
