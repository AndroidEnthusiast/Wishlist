package com.wishlist.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/* The photo editor (see HeroEdit.kt): the editor itself. What it edits lives in HeroEditorState.kt. */

/**
 * Editing the cover photo before it is saved, like the photo editors of shops: rotate left and right, crop (pinch
 * and drag, with a grid), lighter or darker (a slider), a text box (colour, a box behind it, top / middle / bottom or
 * dragged anywhere) and a new photo (the arrow up). The frame is exactly as on the dashboard. Nothing changes until
 * the tick; [onSave] gets the photo (it may be a new one) and how to show it.
 */
@Composable
fun HeroEditor(
    image: String, start: HeroEdit, onSave: (String, HeroEdit) -> Unit, onCancel: () -> Unit,
    // The profile photo (2.88): a round frame (a square photo is saved, shown in a circle), no text tool.
    round: Boolean = false,
    // A wish's photo (owner, 7 November 2026): a square frame, no text tool, and "Fit whole photo" (the whole photo in
    // the frame on white). The result is cut out with [renderWishPhoto].
    wish: Boolean = false,
    // A wishlist's cover (2.89): a Preview button shows the list with the photo as it is in the frame now.
    preview: (@Composable (String, HeroEdit, () -> Unit) -> Unit)? = null,
    // Several screenshots shared at once (owner, 20 November 2026): "2 of 5" as the title, a Skip button, and a short
    // note that the wish before was added.
    step: Pair<Int, Int>? = null,
    onSkip: (() -> Unit)? = null,
    notice: String? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // What is being edited and which tool is open (HeroEditorState); nothing is saved until the tick.
    val st = remember { HeroEditorState(ctx, image, start) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { st.usePhoto(it) } }
    }
    fun cancel() {
        st.dropPicked()
        onCancel()
    }
    val dark = Color(0xFF111111)
    val onDark = Color.White
    Dialog(onDismissRequest = { cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (st.previewing && preview != null) {
            androidx.activity.compose.BackHandler { st.previewing = false }
            preview(st.img, st.edit) { st.previewing = false }
        } else Surface(Modifier.fillMaxSize(), color = dark, contentColor = onDark) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                // Top: back (cancel), the title, the tick (save).
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cancel() }) { Icon(AppIcons.ArrowBack, tr("Cancel"), tint = onDark) }
                    Text(
                        if (step != null) tr("Photo %d of %d", step.first, step.second)
                        else if (round) tr("Edit profile photo") else if (wish) tr("Edit photo") else tr("Edit cover photo"),
                        style = MaterialTheme.typography.titleLarge, color = onDark, maxLines = 1,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    if (preview != null) {
                        Text(
                            tr("Preview"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = onDark,
                            modifier = Modifier.clip(CircleShape).clickable { st.previewing = true }.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    if (onSkip != null) {
                        Text(
                            tr("Skip"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = onDark,
                            modifier = Modifier.clip(CircleShape).clickable { st.dropPicked(); onSkip() }.padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                    }
                    IconButton(onClick = {
                        st.dropUnused()
                        onSave(st.img, st.edit)
                    }) { Icon(AppIcons.Check, tr("Save"), tint = onDark) }
                }
                notice?.let { SavedNotice(it, onDark) }
                // The cover frame stands still in the middle; the photo is behind it. One finger moves the photo, two
                // fingers zoom it (around the fingers), at any zoom. The photo always fills the frame. What is in the
                // frame is exactly what is saved: the crop (edit.cropL/T/W/H) is worked out from the same numbers that
                // draw the photo, and the photo is drawn straight onto the screen (no layout box that could squeeze it).
                val painter = rememberAsyncImagePainter(st.img)
                val d = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
                val raw = d?.let { if (it.intrinsicWidth > 0 && it.intrinsicHeight > 0) it.intrinsicWidth.toFloat() / it.intrinsicHeight else null }
                val density = LocalDensity.current
                // The shape of the frame on the dashboard (16 dp from both sides, 150 dp high).
                // The profile photo: a circle in a square.
                val fa = if (round || wish) 1f else (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120) / 150f
                // The photo's zoom (1 = it just fills the frame) and where its middle is on screen; null: not placed yet.
                var zoomS by remember(st.img, st.edit.rotation) { mutableStateOf<Float?>(null) }
                var midS by remember(st.img, st.edit.rotation) { mutableStateOf<Offset?>(null) }
                // What the frame shows now, for the small cards under a wish photo (HeroEditorPreview.kt).
                var live by remember { mutableStateOf<LiveCut?>(null) }
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
                        val sideways = st.edit.rotation % 180 != 0
                        val ar = if (sideways) 1f / r else r
                        // The photo's size at zoom 1: it just fills the frame.
                        val baseW = maxOf(fw, fh * ar)
                        val baseH = baseW / ar
                        // A wish photo may be smaller than the frame, down to the whole photo in it ("Fit whole photo").
                        val minZ = if (wish) minOf(fw / baseW, fh / baseH) else 1f
                        // Keep the frame filled: the photo may not move so far that an edge shows inside the frame.
                        // A photo smaller than the frame (a wish photo fitted) stays in the middle that way.
                        fun keep(z: Float, c: Offset): Offset {
                            val mx = (baseW * z - fw) / 2f
                            val my = (baseH * z - fh) / 2f
                            return Offset(
                                if (mx <= 0f) frame.center.x else c.x.coerceIn(frame.center.x - mx, frame.center.x + mx),
                                if (my <= 0f) frame.center.y else c.y.coerceIn(frame.center.y - my, frame.center.y + my)
                            )
                        }
                        // Where it starts: as saved before (editing again), or filling the frame in the middle.
                        val start = remember(st.img, st.edit.rotation, aw, ah, r) {
                            if (st.edit.cropW > 0f && st.edit.cropH > 0f) {
                                val z = (fw / (st.edit.cropW * baseW)).coerceIn(minZ, 8f)
                                val w = baseW * z
                                val h = baseH * z
                                z to keep(z, Offset(frame.left - st.edit.cropL * w + w / 2f, frame.top - st.edit.cropT * h + h / 2f))
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
                            st.edit = st.edit.copy(
                                cropL = (frame.left - p.left) / p.width, cropT = (frame.top - p.top) / p.height,
                                cropW = frame.width / p.width, cropH = frame.height / p.height
                            )
                        }
                        // "Fit whole photo": the whole photo in the middle of the frame.
                        androidx.compose.runtime.LaunchedEffect(st.fitAsk) {
                            if (st.fitAsk > 0) { zoomS = minZ; midS = frame.center; keepCrop() }
                        }
                        val pr = photo()
                        val filter = brightness(st.edit.bright)
                        if (wish) androidx.compose.runtime.SideEffect { live = LiveCut(frame, pr, st.edit.rotation, filter) }
                        // The photo, drawn straight onto the screen, and the frame over it (the rest dimmed).
                        Canvas(Modifier.matchParentSize()) {
                            // A wish photo is saved on white: what shows around a fitted photo.
                            if (wish) drawRect(Color.White, frame.topLeft, frame.size)
                            val dw = if (sideways) pr.height else pr.width
                            val dh = if (sideways) pr.width else pr.height
                            rotate(st.edit.rotation.toFloat(), pr.center) {
                                translate(pr.center.x - dw / 2f, pr.center.y - dh / 2f) {
                                    with(painter) { draw(Size(dw, dh), colorFilter = filter) }
                                }
                            }
                            val dim = Color.Black.copy(alpha = 0.6f)
                            if (round) {
                                // Everything outside the circle dimmed, and a white ring: what is inside is the photo.
                                val ring = androidx.compose.ui.graphics.Path().apply { addOval(frame) }
                                clipPath(ring, androidx.compose.ui.graphics.ClipOp.Difference) { drawRect(dim) }
                                drawCircle(Color.White, frame.width / 2f, frame.center, style = Stroke(2.dp.toPx()))
                            } else {
                                drawRect(dim, Offset.Zero, Size(size.width, frame.top))
                                drawRect(dim, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
                                drawRect(dim, Offset(0f, frame.top), Size(frame.left, frame.height))
                                drawRect(dim, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
                                drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
                            }
                            if (st.tool == "crop") {
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
                            Modifier.matchParentSize().pointerInput(st.img, st.edit.rotation, aw, ah, r) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    st.textOn = false
                                    val z0 = zoomNow()
                                    val z1 = (z0 * zoom).coerceIn(minZ, 8f)
                                    val c = centroid + (midNow() - centroid) * (z1 / z0) + pan
                                    zoomS = z1
                                    midS = keep(z1, c)
                                    keepCrop()
                                }
                            }
                        )
                        fun dp(px: Float) = with(density) { px.toDp() }
                        // The text, in the frame: drag it to move it; tap it for corners that make it bigger or smaller.
                        if (!round && st.edit.text.isNotBlank()) {
                            Box(Modifier.offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }.size(dp(frame.width), dp(frame.height))) {
                                EditableText(st.edit, frame.width, frame.height, st.textOn, onSelect = { st.textOn = it }) { st.edit = it }
                            }
                        }
                    }
                }
                // Always there for a wish photo (empty until the photo is placed), so the frame above never moves.
                if (wish) WishPhotoPreviews(painter, live, onDark)
                // The tool that is open.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (st.tool) {
                        "light" -> {
                            Text(tr("Lighter or darker"), style = MaterialTheme.typography.labelLarge, color = onDark.copy(alpha = 0.8f))
                            Slider(value = st.edit.bright, onValueChange = { st.edit = st.edit.copy(bright = it) }, valueRange = -1f..1f)
                        }
                        "text" -> TextTool(st.edit, onDark) { st.edit = it }
                        else -> {}
                    }
                }
                // The tools.
                Surface(color = Color.White, contentColor = Color(0xFF111111)) {
                    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ToolButton(tr("Rotate left"), false, { st.rotate(270) }) {
                            Icon(AppIcons.Refresh, null, Modifier.graphicsLayer { scaleX = -1f })
                        }
                        ToolButton(tr("Rotate right"), false, { st.rotate(90) }) {
                            Icon(AppIcons.Refresh, null)
                        }
                        ToolButton(tr("Crop"), st.tool == "crop", { st.tool = "crop" }) { CropIcon(Color(0xFF111111)) }
                        ToolButton(tr("Lighter or darker"), st.tool == "light", { st.tool = "light" }) { SunIcon(Color(0xFF111111)) }
                        if (wish) ToolButton(tr("Fit whole photo"), false, { st.tool = "crop"; st.fitAsk++ }) { FitIcon(Color(0xFF111111)) }
                        if (!round && !wish) ToolButton(tr("Text"), st.tool == "text", { st.tool = "text" }) {
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
