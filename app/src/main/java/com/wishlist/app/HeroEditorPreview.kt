package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The photo editor (see HeroEdit.kt): "How it looks in your list" under a wish photo (owner, 20 November 2026: "make
 * sure they know the picture they edit will look great in their wishlist"). Three small cards in the user's own style
 * (Two per row, Large cards, Rows), drawn live from the frame while the photo is moved, zoomed, turned or made lighter.
 * Each card is laid out at the size it has in a list and shrunk, so its corners, shadows and padding are in proportion.
 */

/** What the editor shows now: the frame and the photo on screen, its turn and its lighter/darker. */
internal data class LiveCut(val frame: Rect, val photo: Rect, val rotation: Int, val filter: ColorFilter?)

/** The part of the photo in the editor's frame, drawn as a [side]-square at [at], on white (as it is saved). */
internal fun DrawScope.drawCut(painter: Painter, cut: LiveCut, at: Offset, side: Float) {
    if (cut.frame.width <= 0f) return
    val k = side / cut.frame.width
    clipRect(at.x, at.y, at.x + side, at.y + side) {
        drawRect(Color.White, at, Size(side, side))
        val p = cut.photo
        val c = Offset(at.x + (p.center.x - cut.frame.left) * k, at.y + (p.center.y - cut.frame.top) * k)
        val sideways = cut.rotation % 180 != 0
        val dw = (if (sideways) p.height else p.width) * k
        val dh = (if (sideways) p.width else p.height) * k
        rotate(cut.rotation.toFloat(), c) {
            translate(c.x - dw / 2f, c.y - dh / 2f) { with(painter) { draw(Size(dw, dh), colorFilter = cut.filter) } }
        }
    }
}

/** The three small cards with their names, under the photo. */
@Composable
internal fun WishPhotoPreviews(painter: Painter, cut: LiveCut?, onDark: Color) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text(tr("How it looks in your list"), style = MaterialTheme.typography.labelLarge, color = onDark.copy(alpha = 0.8f))
        // Widths in proportion to the cards' shapes, so the three are about the same height.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Mini(tr("Two per row"), 170.dp, 214.dp, Modifier.weight(1f), onDark) { PreviewCard(painter, cut, small = true) }
            Mini(tr("Large cards"), 340.dp, 299.dp, Modifier.weight(1.4f), onDark) { PreviewCard(painter, cut, small = false) }
            Mini(tr("Rows"), 340.dp, 124.dp, Modifier.weight(1.9f), onDark) { PreviewRow(painter, cut) }
        }
    }
}

/** One card laid out at its real size ([w] × [h]) and shrunk to fit, with its name under it. */
@Composable
private fun Mini(label: String, w: Dp, h: Dp, modifier: Modifier, onDark: Color, card: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(w / h), contentAlignment = Alignment.Center) {
            val s = maxWidth / w
            Box(Modifier.requiredSize(w, h).graphicsLayer { scaleX = s; scaleY = s }) { card() }
        }
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = onDark.copy(alpha = 0.8f), maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** A card of Two per row ([small]) or Large cards: the picture (square or 4:3) and two lines for the name and price. */
@Composable
private fun PreviewCard(painter: Painter, cut: LiveCut?, small: Boolean) {
    val look = LocalLook.current
    Column(Modifier.fillMaxSize().lookCard(look, 0L, look.cardShape(0L, small))) {
        PreviewPicture(painter, cut, small, Modifier.padding(look.inset(small)).fillMaxWidth().aspectRatio(if (small) 1f else 4f / 3f))
        TextLines(Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

/** A card of Rows: the square picture on the left, the lines on the right. */
@Composable
private fun PreviewRow(painter: Painter, cut: LiveCut?) {
    val look = LocalLook.current
    Row(Modifier.fillMaxSize().lookCard(look, 0L, look.cardShape(0L, small = true)), verticalAlignment = Alignment.CenterVertically) {
        PreviewPicture(painter, cut, small = true, Modifier.padding(look.inset(small = true)).fillMaxHeight().aspectRatio(1f))
        TextLines(Modifier.weight(1f).padding(horizontal = 14.dp))
    }
}

/** Grey bars where the name and the price go: the photo is what this is about. */
@Composable
private fun TextLines(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(ink))
        Box(Modifier.width(48.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(ink))
        Spacer(Modifier.height(0.dp))
    }
}

/**
 * The picture area as the cards draw it (PhotoStage in StyledPhotos.kt), with the cut drawn live: a soft blur of it
 * behind, the photo in the middle on its frame; Quiet: the photo on the light photo surface, its white melted away.
 */
@Composable
private fun PreviewPicture(painter: Painter, cut: LiveCut?, small: Boolean, modifier: Modifier) {
    val look = LocalLook.current
    Box(modifier.lookMat(look, look.pictureShape(0L, small), look.photoSurface(0L), over = true), contentAlignment = Alignment.Center) {
        // Not placed yet (the photo is still loading): the empty picture area.
        if (cut == null) return@Box
        if (look.design == "quiet") {
            val surface = look.photoSurface(0L)
            Canvas(Modifier.padding(if (small) 10.dp else 16.dp).aspectRatio(1f, matchHeightConstraintsFirst = true).clip(Squircle(if (small) 8.dp else 12.dp))) {
                drawCut(painter, cut, Offset.Zero, size.width)
                drawRect(surface, blendMode = BlendMode.Multiply)
            }
        } else {
            Canvas(
                Modifier.fillMaxSize().then(
                    if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(if (small) 14.dp else 20.dp, BlurredEdgeTreatment.Rectangle) else Modifier
                )
            ) {
                val side = maxOf(size.width, size.height)
                drawCut(painter, cut, Offset((size.width - side) / 2f, (size.height - side) / 2f), side)
            }
            Box(Modifier.fillMaxSize().background(if (look.dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.18f)))
            val shape = if (look.design == "chaos") RoundedCornerShape(8.dp) else RoundedCornerShape(if (small) 10.dp else 14.dp)
            val frame = if (look.design == "chaos") Modifier.hardShadow(look.line, shape, 3.dp) else Modifier.shadow(if (small) 3.dp else 5.dp, shape, clip = false)
            Canvas(
                Modifier.padding(if (small) 9.dp else 14.dp).aspectRatio(1f, matchHeightConstraintsFirst = true).then(frame).clip(shape)
                    .then(if (look.edge > 0.dp) Modifier.edgeLine(minOf(look.edge, 2.dp), look.line, shape) else Modifier)
            ) { drawCut(painter, cut, Offset.Zero, size.width) }
        }
    }
}

/**
 * "✓ “Shoes” added to My wishlist" at the top of the editor, after a screenshot's wish was saved and the next one is
 * shown (owner, 20 November 2026: after every saved wish a confirmation). A message at the bottom would be hidden
 * behind the editor, so it is shown here; it fades after a few seconds.
 */
@Composable
internal fun SavedNotice(text: String, onDark: Color) {
    var shown by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(text) { kotlinx.coroutines.delay(4000); shown = false }
    androidx.compose.animation.AnimatedVisibility(shown) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp))
                .background(onDark.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(AppIcons.Check, null, tint = onDark, modifier = Modifier.padding(end = 10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = onDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
