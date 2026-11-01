package com.wishlist.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * How the cover photo (the picture at the top of the dashboard) is shown: [scale] (1 = the photo just covers the
 * frame), how far it is moved ([x], [y], as parts of the frame), [rotation] (0, 90, 180 or 270), [bright] (-1 darker to
 * 1 lighter) and an optional [text] with its [color], a [box] behind it or not, and the place of its middle ([tx], [ty],
 * 0 to 1). Kept as numbers (not a new picture), so the photo can be edited again later.
 */
data class HeroEdit(
    val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f,
    val text: String = "", val tx: Float = 0.5f, val ty: Float = 0.75f,
    val rotation: Int = 0, val bright: Float = 0f, val color: Long = 0xFFFFFFFF, val box: Boolean = false
) {
    fun toJson(): String = JSONObject().put("scale", scale.toDouble()).put("x", x.toDouble()).put("y", y.toDouble())
        .put("text", text).put("tx", tx.toDouble()).put("ty", ty.toDouble())
        .put("rot", rotation).put("bright", bright.toDouble()).put("color", color).put("box", box).toString()

    companion object {
        fun fromJson(s: String?): HeroEdit = try {
            val o = JSONObject(s ?: "{}")
            HeroEdit(
                o.optDouble("scale", 1.0).toFloat(), o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.0).toFloat(),
                o.optString("text", ""), o.optDouble("tx", 0.5).toFloat(), o.optDouble("ty", 0.75).toFloat(),
                o.optInt("rot", 0), o.optDouble("bright", 0.0).toFloat(), o.optLong("color", 0xFFFFFFFF), o.optBoolean("box")
            )
        } catch (e: Exception) {
            HeroEdit()
        }
    }
}

private val heroTextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/** Text colours to choose from. */
private val TEXT_COLOURS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFFE14D, 0xFFFF7EB6, 0xFF7EEDC0, 0xFF8FB8FF)

/** Lighter or darker: [b] from -1 to 1 adds or takes away up to 40% of white. */
private fun brightness(b: Float): ColorFilter? {
    if (b == 0f) return null
    val o = b * 0.4f * 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f))
    )
}

/**
 * The cover photo in its frame, as [edit] says. With [onChange] it can be edited: pinch to zoom, drag to move the
 * photo, drag the text to place it. The photo always covers the whole frame. [grid]: thirds lines to line things up.
 */
@Composable
fun HeroPicture(image: String, edit: HeroEdit, modifier: Modifier, onChange: ((HeroEdit) -> Unit)? = null, grid: Boolean = false) {
    val painter = rememberAsyncImagePainter(image)
    val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val density = LocalDensity.current
    val current by rememberUpdatedState(edit)
    val change by rememberUpdatedState(onChange)
    BoxWithConstraints(modifier.clipToBounds()) {
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
        val s = edit.scale.coerceIn(1f, 5f)
        val tx = clampX(edit.x, s) * w
        val ty = clampY(edit.y, s) * h
        // Turned sideways, the picture is drawn with width and height swapped and then turned.
        val dw = if (sideways) bh else bw
        val dh = if (sideways) bw else bh
        Image(
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
            val col = Color(edit.color)
            val boxCol = if (col.luminance() > 0.5f) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f)
            Text(
                edit.text, color = col, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall.copy(shadow = if (edit.box) null else heroTextShadow),
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

/**
 * Editing the cover photo before it is saved, like the photo editors of shops: rotate left and right, crop (pinch
 * and drag, with a grid), lighter or darker (a slider), a text box (colour, a box behind it, top / middle / bottom or
 * dragged anywhere) and a new photo (the arrow up). The frame is exactly as on the dashboard. Nothing changes until
 * the tick; [onSave] gets the photo (it may be a new one) and how to show it.
 */
@Composable
fun HeroEditor(image: String, start: HeroEdit, onSave: (String, HeroEdit) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var img by remember { mutableStateOf(image) }
    var edit by remember { mutableStateOf(start) }
    var tool by remember { mutableStateOf("crop") }
    // Photos picked here with the arrow: the ones not kept are deleted again.
    var picked by remember { mutableStateOf(listOf<String>()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            ImageStore.import(ctx, uri)?.let {
                picked = picked + it
                img = it
                edit = edit.copy(scale = 1f, x = 0f, y = 0f, rotation = 0, bright = 0f)
            }
        }
    }
    fun cancel() {
        picked.forEach { ImageStore.delete(ctx, it) }
        onCancel()
    }
    val dark = Color(0xFF111111)
    val onDark = Color.White
    Dialog(onDismissRequest = { cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = dark, contentColor = onDark) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                // Top: back (cancel), the title, the tick (save).
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cancel() }) { Icon(AppIcons.ArrowBack, tr("Cancel"), tint = onDark) }
                    Text(
                        tr("Edit cover photo"), style = MaterialTheme.typography.titleLarge, color = onDark,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = {
                        picked.filter { it != img }.forEach { ImageStore.delete(ctx, it) }
                        onSave(img, edit)
                    }) { Icon(AppIcons.Check, tr("Save"), tint = onDark) }
                }
                // The photo, in a frame the size of the one on the dashboard.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HeroPicture(
                        img, edit,
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)),
                        onChange = { edit = it }, grid = tool == "crop"
                    )
                }
                // The tool that is open.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (tool) {
                        "light" -> {
                            Text(tr("Lighter or darker"), style = MaterialTheme.typography.labelLarge, color = onDark.copy(alpha = 0.8f))
                            Slider(value = edit.bright, onValueChange = { edit = edit.copy(bright = it) }, valueRange = -1f..1f)
                        }
                        "text" -> TextTool(edit, onDark) { edit = it }
                        else -> {
                            Text(
                                tr("Pinch to zoom and drag to move the photo."),
                                style = MaterialTheme.typography.bodySmall, color = onDark.copy(alpha = 0.8f)
                            )
                            if (edit.scale != 1f || edit.x != 0f || edit.y != 0f) {
                                TextButton(onClick = { edit = edit.copy(scale = 1f, x = 0f, y = 0f) }) {
                                    Text(tr("Reset zoom and position"))
                                }
                            }
                        }
                    }
                }
                // The tools.
                Surface(color = Color.White, contentColor = Color(0xFF111111)) {
                    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ToolButton(tr("Rotate left"), false, { edit = edit.copy(rotation = (edit.rotation + 270) % 360) }) {
                            Icon(AppIcons.Refresh, null, Modifier.graphicsLayer { scaleX = -1f })
                        }
                        ToolButton(tr("Rotate right"), false, { edit = edit.copy(rotation = (edit.rotation + 90) % 360) }) {
                            Icon(AppIcons.Refresh, null)
                        }
                        ToolButton(tr("Crop"), tool == "crop", { tool = "crop" }) { CropIcon(Color(0xFF111111)) }
                        ToolButton(tr("Lighter or darker"), tool == "light", { tool = "light" }) { SunIcon(Color(0xFF111111)) }
                        ToolButton(tr("Text"), tool == "text", { tool = "text" }) {
                            Text("T", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                        }
                        ToolButton(tr("New photo"), false, {
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { UploadIcon(Color(0xFF111111)) }
                    }
                }
            }
        }
    }
}

/** The text box: the words, their colour, a box behind them, and where they go (or drag them on the photo). */
@Composable
private fun TextTool(edit: HeroEdit, onDark: Color, onChange: (HeroEdit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val w = Color.White
        OutlinedTextField(
            edit.text, { onChange(edit.copy(text = it.take(40))) },
            label = { Text(tr("Text on the photo")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            // Light on the dark editor, whatever the theme.
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = w, unfocusedTextColor = w, cursorColor = w,
                focusedLabelColor = w, unfocusedLabelColor = w.copy(alpha = 0.7f),
                focusedBorderColor = w, unfocusedBorderColor = w.copy(alpha = 0.5f)
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TEXT_COLOURS.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (edit.color == c) 3.dp else 1.dp, if (edit.color == c) Color(0xFF8FB8FF) else onDark.copy(alpha = 0.4f), CircleShape)
                        .clickable { onChange(edit.copy(color = c)) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("Box"), edit.box) { onChange(edit.copy(box = !edit.box)) }
            Chip(tr("Top"), edit.ty < 0.34f) { onChange(edit.copy(tx = 0.5f, ty = 0.22f)) }
            Chip(tr("Middle"), edit.ty in 0.34f..0.66f) { onChange(edit.copy(tx = 0.5f, ty = 0.5f)) }
            Chip(tr("Bottom"), edit.ty > 0.66f) { onChange(edit.copy(tx = 0.5f, ty = 0.78f)) }
        }
        Text(tr("Or drag the text on the photo."), style = MaterialTheme.typography.bodySmall, color = onDark.copy(alpha = 0.8f))
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = if (on) Color(0xFF111111) else Color.White,
        modifier = Modifier.clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun ToolButton(label: String, on: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (on) Color(0xFFE6E6E6) else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

/** Two crop corners. */
@Composable
private fun CropIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(2.2f * u, cap = StrokeCap.Square)
        val a = androidx.compose.ui.graphics.Path().apply { moveTo(6f * u, 2f * u); lineTo(6f * u, 18f * u); lineTo(22f * u, 18f * u) }
        val b = androidx.compose.ui.graphics.Path().apply { moveTo(2f * u, 6f * u); lineTo(18f * u, 6f * u); lineTo(18f * u, 22f * u) }
        drawPath(a, c, style = st)
        drawPath(b, c, style = st)
    }
}

/** A sun: lighter or darker. */
@Composable
private fun SunIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawCircle(c, 4.5f * u, center)
        for (i in 0 until 8) {
            val ang = Math.toRadians(i * 45.0)
            val s = Offset(center.x + (7.5f * u * Math.cos(ang)).toFloat(), center.y + (7.5f * u * Math.sin(ang)).toFloat())
            val e = Offset(center.x + (10.5f * u * Math.cos(ang)).toFloat(), center.y + (10.5f * u * Math.sin(ang)).toFloat())
            drawLine(c, s, e, 2f * u, StrokeCap.Round)
        }
    }
}

/** An arrow up out of a tray: a new photo. */
@Composable
private fun UploadIcon(c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(2.2f * u, cap = StrokeCap.Round)
        drawLine(c, Offset(12f * u, 3f * u), Offset(12f * u, 15f * u), 2.2f * u, StrokeCap.Round)
        val head = androidx.compose.ui.graphics.Path().apply { moveTo(7f * u, 8f * u); lineTo(12f * u, 3f * u); lineTo(17f * u, 8f * u) }
        drawPath(head, c, style = st)
        val tray = androidx.compose.ui.graphics.Path().apply { moveTo(4f * u, 15f * u); lineTo(4f * u, 20f * u); lineTo(20f * u, 20f * u); lineTo(20f * u, 15f * u) }
        drawPath(tray, c, style = st)
    }
}
