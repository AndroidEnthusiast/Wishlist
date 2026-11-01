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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
    val rotation: Int = 0, val bright: Float = 0f, val color: Long = 0xFFFFFFFF, val box: Boolean = false,
    /** The text's height as a part of the frame's height (0.16 is about the size of a heading). */
    val textSize: Float = 0.16f,
    /**
     * The part of the (turned) photo in the frame, as parts of its width and height: left, top, width, height
     * (since 2.75; -1 = not chosen, then the middle). The editor sets them; the cover picture is cut out with them.
     */
    val cropL: Float = -1f, val cropT: Float = -1f, val cropW: Float = -1f, val cropH: Float = -1f
) {
    fun toJson(): String = JSONObject().put("scale", scale.toDouble()).put("x", x.toDouble()).put("y", y.toDouble())
        .put("text", text).put("tx", tx.toDouble()).put("ty", ty.toDouble())
        .put("rot", rotation).put("bright", bright.toDouble()).put("color", color).put("box", box).put("tsize", textSize.toDouble())
        .put("cl", cropL.toDouble()).put("ct", cropT.toDouble()).put("cw", cropW.toDouble()).put("ch", cropH.toDouble()).toString()

    companion object {
        fun fromJson(s: String?): HeroEdit = try {
            val o = JSONObject(s ?: "{}")
            HeroEdit(
                o.optDouble("scale", 1.0).toFloat(), o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.0).toFloat(),
                o.optString("text", ""), o.optDouble("tx", 0.5).toFloat(), o.optDouble("ty", 0.75).toFloat(),
                o.optInt("rot", 0), o.optDouble("bright", 0.0).toFloat(), o.optLong("color", 0xFFFFFFFF), o.optBoolean("box"),
                o.optDouble("tsize", 0.16).toFloat(),
                o.optDouble("cl", -1.0).toFloat(), o.optDouble("ct", -1.0).toFloat(),
                o.optDouble("cw", -1.0).toFloat(), o.optDouble("ch", -1.0).toFloat()
            )
        } catch (e: Exception) {
            HeroEdit()
        }
    }
}

private val heroTextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/** Text colours to choose from. */
private val TEXT_COLOURS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFFE14D, 0xFFFF7EB6, 0xFF7EEDC0, 0xFF8FB8FF)

/**
 * Keeps an edit inside the frame: the photo (aspect [raw] before turning) always covers the [w] x [h] frame.
 * Returns the photo's size at scale 1 too.
 */
private fun coverSize(w: Float, h: Float, raw: Float, rotation: Int): Pair<Float, Float> {
    val aspect = if (rotation % 180 != 0) 1f / raw else raw
    return if (aspect > w / h) (h * aspect to h) else (w to w / aspect)
}

private fun clampEdit(e: HeroEdit, w: Float, h: Float, raw: Float): HeroEdit {
    val (bw, bh) = coverSize(w, h, raw, e.rotation)
    val s = e.scale.coerceIn(1f, 8f)
    val mx = ((bw * s - w) / 2f).coerceAtLeast(0f) / w
    val my = ((bh * s - h) / 2f).coerceAtLeast(0f) / h
    return e.copy(scale = s, x = e.x.coerceIn(-mx, mx), y = e.y.coerceIn(-my, my))
}

/** The part of the photo that becomes the cover: centre ([cx], [cy]) and size ([cw], [ch]) as parts of the photo. */
private data class Crop(val cx: Float, val cy: Float, val cw: Float, val ch: Float)

/** The edit as a crop of the photo. [fa]: the frame's shape (width / height). */
private fun toCrop(e: HeroEdit, fa: Float, raw: Float): Crop {
    val (bw, bh) = coverSize(fa, 1f, raw, e.rotation)
    val s = e.scale.coerceIn(1f, 8f)
    return Crop(0.5f - e.x * fa / (bw * s), 0.5f - e.y / (bh * s), fa / (bw * s), 1f / (bh * s))
}

/** A crop of the photo as an edit, kept inside the photo (and at most 8 times zoomed in). */
private fun fromCrop(e: HeroEdit, c: Crop, fa: Float, raw: Float): HeroEdit {
    val (bw, bh) = coverSize(fa, 1f, raw, e.rotation)
    val s = (fa / (bw * c.cw)).coerceIn(1f, 8f)
    val cw = fa / (bw * s)
    val ch = 1f / (bh * s)
    val cx = c.cx.coerceIn(cw / 2f, 1f - cw / 2f)
    val cy = c.cy.coerceIn(ch / 2f, 1f - ch / 2f)
    return e.copy(scale = s, x = (0.5f - cx) * bw * s / fa, y = (0.5f - cy) * bh * s)
}

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
    // The text is selected (tapped): it shows corners to make it bigger or smaller.
    var textOn by remember { mutableStateOf(false) }
    // Photos picked here with the arrow: the ones not kept are deleted again.
    var picked by remember { mutableStateOf(listOf<String>()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            ImageStore.import(ctx, uri)?.let {
                picked = picked + it
                img = it
                edit = edit.copy(scale = 1f, x = 0f, y = 0f, rotation = 0, bright = 0f, cropL = -1f, cropT = -1f, cropW = -1f, cropH = -1f)
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
                // The cover frame stands still in the middle; the photo is behind it. One finger moves the photo, two
                // fingers zoom it (around the fingers), at any zoom. The photo always fills the frame. What is in the
                // frame is exactly what is saved: the crop (edit.cropL/T/W/H) is worked out from the same numbers that
                // draw the photo, and the photo is drawn straight onto the screen (no layout box that could squeeze it).
                val painter = rememberAsyncImagePainter(img)
                val d = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
                val raw = d?.let { if (it.intrinsicWidth > 0 && it.intrinsicHeight > 0) it.intrinsicWidth.toFloat() / it.intrinsicHeight else null }
                val density = LocalDensity.current
                // The shape of the frame on the dashboard (16 dp from both sides, 150 dp high).
                val fa = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120) / 150f
                // The photo's zoom (1 = it just fills the frame) and where its middle is on screen; null: not placed yet.
                var zoomS by remember(img, edit.rotation) { mutableStateOf<Float?>(null) }
                var midS by remember(img, edit.rotation) { mutableStateOf<Offset?>(null) }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    val aw = constraints.maxWidth.toFloat()
                    val ah = constraints.maxHeight.toFloat()
                    val r = raw
                    val m = with(density) { 16.dp.toPx() }
                    // The frame, in the middle: as wide as fits (and not too high).
                    val fw = minOf(aw - 2 * m, (ah - 2 * m) * fa)
                    val fh = fw / fa
                    val frame = Rect(Offset((aw - fw) / 2f, (ah - fh) / 2f), Size(fw, fh))
                    if (r == null) {
                        // The photo only starts loading once it is drawn: draw it (unseen) until its size is known.
                        Image(painter, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0f }, contentScale = ContentScale.Fit)
                    } else {
                        val sideways = edit.rotation % 180 != 0
                        val ar = if (sideways) 1f / r else r
                        // The photo's size at zoom 1: it just fills the frame.
                        val baseW = maxOf(fw, fh * ar)
                        val baseH = baseW / ar
                        // Keep the frame filled: the photo may not move so far that an edge shows inside the frame.
                        fun keep(z: Float, c: Offset): Offset {
                            val mx = (baseW * z - fw) / 2f
                            val my = (baseH * z - fh) / 2f
                            return Offset(c.x.coerceIn(frame.center.x - mx, frame.center.x + mx), c.y.coerceIn(frame.center.y - my, frame.center.y + my))
                        }
                        // Where it starts: as saved before (editing again), or filling the frame in the middle.
                        val start = remember(img, edit.rotation, aw, ah, r) {
                            if (edit.cropW > 0f && edit.cropH > 0f) {
                                val z = (fw / (edit.cropW * baseW)).coerceIn(1f, 8f)
                                val w = baseW * z
                                val h = baseH * z
                                z to keep(z, Offset(frame.left - edit.cropL * w + w / 2f, frame.top - edit.cropT * h + h / 2f))
                            } else 1f to frame.center
                        }
                        fun zoomNow() = zoomS ?: start.first
                        fun midNow() = midS ?: start.second
                        fun photo(): Rect {
                            val w = baseW * zoomNow()
                            val h = baseH * zoomNow()
                            return Rect(Offset(midNow().x - w / 2f, midNow().y - h / 2f), Size(w, h))
                        }
                        // The part in the frame, as parts of the photo.
                        fun keepCrop() {
                            val p = photo()
                            edit = edit.copy(
                                cropL = (frame.left - p.left) / p.width, cropT = (frame.top - p.top) / p.height,
                                cropW = frame.width / p.width, cropH = frame.height / p.height
                            )
                        }
                        val pr = photo()
                        val filter = brightness(edit.bright)
                        // The photo, drawn straight onto the screen, and the frame over it (the rest dimmed).
                        Canvas(Modifier.matchParentSize()) {
                            val dw = if (sideways) pr.height else pr.width
                            val dh = if (sideways) pr.width else pr.height
                            rotate(edit.rotation.toFloat(), pr.center) {
                                translate(pr.center.x - dw / 2f, pr.center.y - dh / 2f) {
                                    with(painter) { draw(Size(dw, dh), colorFilter = filter) }
                                }
                            }
                            val dim = Color.Black.copy(alpha = 0.6f)
                            drawRect(dim, Offset.Zero, Size(size.width, frame.top))
                            drawRect(dim, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
                            drawRect(dim, Offset(0f, frame.top), Size(frame.left, frame.height))
                            drawRect(dim, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
                            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
                            if (tool == "crop") {
                                val g = Color.White.copy(alpha = 0.5f)
                                for (i in 1..2) {
                                    val x = frame.left + frame.width * i / 3f
                                    val y = frame.top + frame.height * i / 3f
                                    drawLine(g, Offset(x, frame.top), Offset(x, frame.bottom), 1.dp.toPx())
                                    drawLine(g, Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
                                }
                            }
                        }
                        // Touch: move the photo with one finger, zoom it with two (around the fingers).
                        Box(
                            Modifier.matchParentSize().pointerInput(img, edit.rotation, aw, ah, r) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    textOn = false
                                    val z0 = zoomNow()
                                    val z1 = (z0 * zoom).coerceIn(1f, 8f)
                                    val c = centroid + (midNow() - centroid) * (z1 / z0) + pan
                                    zoomS = z1
                                    midS = keep(z1, c)
                                    keepCrop()
                                }
                            }
                        )
                        fun dp(px: Float) = with(density) { px.toDp() }
                        // The text, in the frame: drag it to move it; tap it for corners that make it bigger or smaller.
                        if (edit.text.isNotBlank()) {
                            Box(Modifier.offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }.size(dp(frame.width), dp(frame.height))) {
                                EditableText(edit, frame.width, frame.height, textOn, onSelect = { textOn = it }) { edit = it }
                            }
                        }
                    }
                }
                // The tool that is open.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (tool) {
                        "light" -> {
                            Text(tr("Lighter or darker"), style = MaterialTheme.typography.labelLarge, color = onDark.copy(alpha = 0.8f))
                            Slider(value = edit.bright, onValueChange = { edit = edit.copy(bright = it) }, valueRange = -1f..1f)
                        }
                        "text" -> TextTool(edit, onDark) { edit = it }
                        else -> {}
                    }
                }
                // The tools.
                Surface(color = Color.White, contentColor = Color(0xFF111111)) {
                    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ToolButton(tr("Rotate left"), false, { edit = edit.copy(rotation = (edit.rotation + 270) % 360, scale = 1f, x = 0f, y = 0f, cropL = -1f, cropT = -1f, cropW = -1f, cropH = -1f) }) {
                            Icon(AppIcons.Refresh, null, Modifier.graphicsLayer { scaleX = -1f })
                        }
                        ToolButton(tr("Rotate right"), false, { edit = edit.copy(rotation = (edit.rotation + 90) % 360, scale = 1f, x = 0f, y = 0f, cropL = -1f, cropT = -1f, cropW = -1f, cropH = -1f) }) {
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

/**
 * The text on the photo in the editor, inside the frame ([fw] x [fh] pixels on screen). Drag it to move it. Tapped
 * ([selected]) it gets a line around it and four corners: drag one to make the text bigger or smaller.
 */
@Composable
private fun EditableText(edit: HeroEdit, fw: Float, fh: Float, selected: Boolean, onSelect: (Boolean) -> Unit, onChange: (HeroEdit) -> Unit) {
    val density = LocalDensity.current
    val now by rememberUpdatedState(edit)
    val frame by rememberUpdatedState(Size(fw, fh))
    val sel by rememberUpdatedState(selected)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val col = Color(edit.color)
    val boxCol = if (col.luminance() > 0.5f) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f)
    Box(
        Modifier
            .onSizeChanged { size = it }
            .offset {
                IntOffset((edit.tx * fw - size.width / 2f).roundToInt(), (edit.ty * fh - size.height / 2f).roundToInt())
            }
            .pointerInput(Unit) { detectTapGestures { onSelect(!sel) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { onSelect(true) }) { ch, dr ->
                    ch.consume()
                    val e = now
                    onChange(e.copy(tx = (e.tx + dr.x / frame.width).coerceIn(0f, 1f), ty = (e.ty + dr.y / frame.height).coerceIn(0f, 1f)))
                }
            }
            // Room around the words for the corners, so they are inside and can be touched.
            .padding(12.dp)
    ) {
        Text(
            edit.text, color = col, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall.copy(
                shadow = if (edit.box) null else heroTextShadow,
                fontSize = with(density) { (fh * edit.textSize).toSp() }
            ),
            modifier = Modifier
                .then(if (selected) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                .then(
                    if (edit.box) Modifier.clip(RoundedCornerShape(8.dp)).background(boxCol).padding(horizontal = 10.dp, vertical = 4.dp)
                    else Modifier.padding(horizontal = 4.dp)
                )
        )
    }
    if (selected) {
        // The four corners, placed on the text's corners.
        val half = with(density) { 12.dp.toPx() }
        listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
            Box(
                Modifier
                    .offset {
                        val cx = edit.tx * fw + sx * (size.width / 2f - half)
                        val cy = edit.ty * fh + sy * (size.height / 2f - half)
                        IntOffset((cx - half).roundToInt(), (cy - half).roundToInt())
                    }
                    .size(24.dp)
                    .pointerInput(sx, sy) {
                        detectDragGestures { ch, dr ->
                            ch.consume()
                            val e = now
                            val reach = (kotlin.math.hypot(size.width.toFloat(), size.height.toFloat()) / 2f).coerceAtLeast(1f)
                            val grow = (dr.x * sx + dr.y * sy) / reach
                            onChange(e.copy(textSize = (e.textSize * (1f + grow)).coerceIn(0.06f, 0.6f)))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape))
            }
        }
    }
}

/** The text box: the words, then the colours, the rainbow dot (any colour) and the box behind the text in one row. */
@Composable
private fun TextTool(edit: HeroEdit, onDark: Color, onChange: (HeroEdit) -> Unit) {
    var wheel by remember { mutableStateOf(false) }
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
            ), keyboardOptions = Capitalised)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val custom = TEXT_COLOURS.none { it == edit.color }
            TEXT_COLOURS.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (edit.color == c) 3.dp else 1.dp, if (edit.color == c) Color(0xFF8FB8FF) else onDark.copy(alpha = 0.4f), CircleShape)
                        .clickable { onChange(edit.copy(color = c)); wheel = false }
                )
            }
            // Any colour: a rainbow dot that opens the colour ball.
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(Brush.sweepGradient(RAINBOW))
                    .border(if (custom || wheel) 3.dp else 1.dp, if (custom || wheel) Color(0xFF8FB8FF) else onDark.copy(alpha = 0.4f), CircleShape)
                    .clickable { wheel = !wheel }
            )
            Chip(tr("Box"), edit.box) { onChange(edit.copy(box = !edit.box)) }
        }
        if (wheel) ColourBall(Color(edit.color)) { c -> onChange(edit.copy(color = c.toArgb().toLong() and 0xFFFFFFFFL)) }
    }
}

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/**
 * A ball with every colour: the colours around it, white in the middle. Slide a finger over it; the small rectangle on
 * the right shows the colour under your finger.
 */
@Composable
private fun ColourBall(current: Color, onPick: (Color) -> Unit) {
    var at by remember { mutableStateOf<Offset?>(null) }
    fun colourAt(p: Offset, r: Float, c: Offset): Color {
        val dx = p.x - c.x
        val dy = p.y - c.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(r)
        var hue = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
        if (hue < 0) hue += 360f
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, dist / r, 1f)))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            Modifier.size(150.dp).pointerInput(Unit) {
                val r = size.width / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                fun pickAt(p: Offset) {
                    val v = p - c
                    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                    val q = if (len > r) c + v * (r / len) else p
                    at = q
                    onPick(colourAt(q, r, c))
                }
                detectDragGestures(onDragStart = { pickAt(it) }) { ch, _ ->
                    ch.consume()
                    pickAt(ch.position)
                }
            }.pointerInput(Unit) {
                val r = size.width / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                detectTapGestures { p ->
                    val v = p - c
                    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                    val q = if (len > r) c + v * (r / len) else p
                    at = q
                    onPick(colourAt(q, r, c))
                }
            }
        ) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(RAINBOW, center), r, center)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r, center)
            at?.let {
                drawCircle(Color.White, 9.dp.toPx(), it, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.5f), 10.5.dp.toPx(), it, style = Stroke(1.dp.toPx()))
            }
        }
        // The colour under your finger.
        Box(
            Modifier.size(56.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(current)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        )
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

/**
 * Makes the cover picture: exactly the part of [image] that was in the frame ([edit], frame shape [fa]), turned and
 * lightened or darkened, as a JPEG at most 1400 pixels wide. Returns its address, or null when it couldn't be made
 * (the dashboard then works it out from [edit] itself).
 */
suspend fun renderCover(c: android.content.Context, image: String, edit: HeroEdit, fa: Float): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val path = android.net.Uri.parse(image).path ?: return@withContext null
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 2400) sample *= 2
            var bmp = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            if (edit.rotation % 360 != 0) {
                val m = android.graphics.Matrix().apply { postRotate(edit.rotation.toFloat()) }
                bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val raw = bmp.width.toFloat() / bmp.height
            // The part chosen in the editor; when nothing was moved, the middle that fills the frame.
            val cw: Float
            val ch: Float
            val cl: Float
            val ct: Float
            if (edit.cropW > 0f && edit.cropH > 0f) {
                cw = edit.cropW; ch = edit.cropH; cl = edit.cropL; ct = edit.cropT
            } else {
                if (raw > fa) { ch = 1f; cw = fa / raw } else { cw = 1f; ch = raw / fa }
                cl = (1f - cw) / 2f; ct = (1f - ch) / 2f
            }
            val left = (cl * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val top = (ct * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val w = (cw * bmp.width).roundToInt().coerceIn(1, bmp.width - left)
            val h = (ch * bmp.height).roundToInt().coerceIn(1, bmp.height - top)
            val outW = minOf(w, 1400)
            val outH = (outW * h.toFloat() / w).toInt().coerceAtLeast(1)
            val out = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            if (edit.bright != 0f) {
                val k = edit.bright * 0.4f * 255f
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(
                    floatArrayOf(1f, 0f, 0f, 0f, k, 0f, 1f, 0f, 0f, k, 0f, 0f, 1f, 0f, k, 0f, 0f, 0f, 1f, 0f)
                )
            }
            canvas.drawBitmap(bmp, android.graphics.Rect(left, top, left + w, top + h), android.graphics.Rect(0, 0, outW, outH), paint)
            val dir = java.io.File(c.filesDir, "images").apply { mkdirs() }
            val file = java.io.File(dir, "cover_" + System.currentTimeMillis() + ".jpg")
            java.io.FileOutputStream(file).use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        }
    }
