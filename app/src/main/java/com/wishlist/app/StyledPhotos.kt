package com.wishlist.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Precision
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* The "Saved" tick and wish photos in the chosen style (see Styled.kt). */

/**
 * A green tick with "Saved" next to a field, for about a second after each save ([saves] goes up by one each time).
 * The tick draws itself: from its short end down to the point, then up. It always takes its room, so the field next
 * to it keeps its width.
 */
@Composable
fun SavedFeedback(saves: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val green = if (LocalLook.current.dark) Color(0xFF6FD39A) else Color(0xFF1E7B45)
    var visible by remember { mutableStateOf(false) }
    val draw = remember { Animatable(0f) }
    // Little lines that circle around the tick once and fade (owner, 2.111: the tick alone wasn't always noticed).
    val spin = remember { Animatable(0f) }
    LaunchedEffect(saves) {
        if (saves == 0) return@LaunchedEffect
        draw.snapTo(0f)
        spin.snapTo(0f)
        visible = true
        launch { spin.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(1100)
        visible = false
    }
    // compact: only the tick (where there is little room, like next to the target price).
    Box(modifier.width(if (compact) 26.dp else 96.dp), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(400))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(20.dp)) {
                    val u = size.width / 24f
                    // Eight short lines around the tick (drawn a little outside the 20 dp, which is fine: no clipping),
                    // turning a third of a circle while they fade out.
                    val fade = (1f - spin.value).coerceIn(0f, 1f)
                    if (fade > 0f) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        for (k in 0 until 8) {
                            val a = Math.toRadians((k * 45.0) + spin.value * 120.0)
                            val r1 = (13f + 2f * spin.value) * u
                            val r2 = r1 + 4f * u
                            drawLine(
                                green.copy(alpha = 0.85f * fade),
                                c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()),
                                c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()),
                                strokeWidth = 2f * u, cap = StrokeCap.Round
                            )
                        }
                    }
                    val path = Path().apply {
                        moveTo(4.5f * u, 12.5f * u)
                        lineTo(9.5f * u, 17.5f * u)
                        lineTo(19.5f * u, 6.5f * u)
                    }
                    val pm = PathMeasure()
                    pm.setPath(path, false)
                    val part = Path()
                    pm.getSegment(0f, pm.length * draw.value, part, true)
                    drawPath(part, green, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (!compact) Text(
                    tr("Saved!"), color = green, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** A wish photo in a small spot (lists, rows, chat): drawn from memory when it was shown before (no flash). */
@Composable
fun WishImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Image(rememberWishPhoto(url).first, null, modifier, contentScale = contentScale)
}

/**
 * Blur stage (owner, 2.100): a soft blur of the photo fills the whole picture area and the photo itself ([PhotoCard])
 * sits big and uncut in the middle. The blur is a tiny copy of the photo stretched out (soft on every Android version),
 * blurred once more on Android 12 and newer; a light veil (dark in dark mode) keeps it calm. Colours come from the photo,
 * the theme stays as it is.
 */
/** The size (pixels) every wish photo is loaded at, so all screens and views share one copy in memory. */
const val PHOTO_PX = 720

/**
 * A wish photo, loaded once and then drawn straight from memory (owner, 2.102: "switch between pages where products
 * are listed without the images loading or doing something"). A photo that was shown before is in Coil's memory
 * under its own key (url + size); it is then drawn on the very first frame, without a loading step that could flash.
 * Otherwise it loads as usual (from the phone's disk cache or the shop). Returns the painter and the photo's
 * width/height (null while it loads). [exact]: exactly this small (the blur behind a photo).
 */
@Composable
fun rememberWishPhoto(url: String, px: Int = PHOTO_PX, exact: Boolean = false): Pair<androidx.compose.ui.graphics.painter.Painter, Float?> {
    val ctx = LocalContext.current
    val key = "$url#$px"
    val cached = remember(key) {
        try { ctx.imageLoader.memoryCache?.get(MemoryCache.Key(key))?.bitmap } catch (e: Exception) { null }
    }
    if (cached != null) {
        val p = remember(cached) { androidx.compose.ui.graphics.painter.BitmapPainter(cached.asImageBitmap()) }
        return p to (if (cached.height > 0) cached.width.toFloat() / cached.height else 1f)
    }
    val request = remember(key) {
        // An http:// picture saved before 2.215 loads over https (Android refuses plain http).
        ImageRequest.Builder(ctx).data(secureImage(url)).size(px).memoryCacheKey(key)
            .apply { if (exact) precision(Precision.EXACT) }.build()
    }
    val painter = rememberAsyncImagePainter(request)
    // A picture that can't be loaded (a shop that refuses, a broken address, offline) is remembered, so the cards show
    // the gift instead of a white box (owner, 16 November 2026, after a Barnes & Noble review and Baracuta).
    if (painter.state is AsyncImagePainter.State.Error) LaunchedEffect(url) { BrokenPhotos.mark(url) }
    else if (painter.state is AsyncImagePainter.State.Success) LaunchedEffect(url) { BrokenPhotos.clear(url) }
    val d = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    val ratio = d?.let { if (it.intrinsicWidth > 0 && it.intrinsicHeight > 0) it.intrinsicWidth.toFloat() / it.intrinsicHeight else null }
    return painter to ratio
}

/**
 * Wish pictures that couldn't be loaded since the app started (in memory only: online again, a new start tries again).
 * Cards then show the gift as for a wish without a picture; the wish form says so and offers your own photo.
 */
object BrokenPhotos {
    private val broken = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    fun mark(url: String) { if (url.isNotBlank()) broken[url] = true }
    fun clear(url: String) { broken.remove(url) }
    fun isBroken(url: String): Boolean = broken[url] == true
}

@Composable
fun PhotoStage(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (BrokenPhotos.isBroken(url)) {
        // Still asked for in the background (so it comes back once it loads), the gift on top.
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(rememberWishPhoto(url).first, null, Modifier.size(1.dp))
            PlaceholderIcon("gift", if (small) 32.dp else 48.dp, look.photoInk)
        }
        return
    }
    if (look.design == "quiet") { QuietPhoto(url, description, small, modifier); return }
    val tiny = rememberWishPhoto(url, 24, exact = true).first
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            tiny, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(
                if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(if (small) 14.dp else 20.dp, BlurredEdgeTreatment.Rectangle)
                else Modifier
            )
        )
        Box(Modifier.fillMaxSize().background(if (look.dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.18f)))
        PhotoCard(url, description, small)
    }
}

/**
 * Quiet's photo (2.207, docs/styles/quiet.md): the shop photo whole, on the card's one light photo surface, with small
 * rounded corners and no frame or shadow of its own. It is laid over the surface with "multiply" (a colour filter), so the white
 * background most shop photos have takes the surface's colour and the product seems to stand on the card itself
 * (owner: one photo surface, no frame in a frame).
 */
@Composable
private fun QuietPhoto(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val (painter, ratio) = rememberWishPhoto(url)
    Box(modifier.fillMaxSize().padding(if (small) 10.dp else 16.dp), contentAlignment = Alignment.Center) {
        Image(
            painter, description, contentScale = ContentScale.Fit,
            // Multiplying by the surface's colour is the same as laying the photo over it with "multiply".
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalLook.current.photoSurface(0), androidx.compose.ui.graphics.BlendMode.Multiply),
            modifier = (if (ratio == null) Modifier.fillMaxSize() else Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true))
                .clip(Squircle(if (small) 8.dp else 12.dp))
        )
    }
}

/**
 * A shop photo shown as a small rounded card in the middle of the picture area, which has a soft colour of the theme
 * (the owner found the big white areas of white-background shop photos dull, 2.63). The card takes the photo's own
 * shape, so its corners and shadow sit on the photo itself. Neobrutalism gives it an ink line and a hard shadow.
 */
@Composable
fun PhotoCard(url: String, description: String, small: Boolean, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    // One fixed size everywhere, drawn from memory when it was shown before (rememberWishPhoto, 2.101/2.102).
    val (painter, ratio) = rememberWishPhoto(url)
    Box(modifier.fillMaxSize().padding(if (small) 9.dp else 14.dp), contentAlignment = Alignment.Center) {
        if (ratio == null) {
            // Still loading (or it failed): nothing to frame yet. The painter needs to be drawn to load.
            Image(painter, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            // The photo on a calm frame that fits the style (owner, 13 November 2026): its corners, its line and its
            // shadow; Retro gives it the white border of an old print, Grunge a rough edge, Bauhaus square corners.
            val shape = when (look.design) {
                "chaos" -> RoundedCornerShape(8.dp)
                else -> RoundedCornerShape(if (small) 10.dp else 14.dp)
            }
            val frame = when (look.design) {
                "chaos" -> Modifier.hardShadow(look.line, shape, 3.dp)
                else -> Modifier.shadow(if (small) 3.dp else 5.dp, shape, clip = false)
            }
            Image(
                painter, description, contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .then(frame).clip(shape).background(Color.White)
                    .then(if (look.edge > 0.dp) Modifier.edgeLine(minOf(look.edge, 2.dp), look.line, shape) else Modifier)
            )
        }
    }
}

/**
 * A wish's photo as a small square (owner, 25 November 2026: "just text makes it unclear"): Received gifts and the
 * "Unreserve?" pop-up. The gift placeholder when it has none.
 */
@Composable
fun WishThumb(w: Wish, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val look = LocalLook.current
    Box(
        Modifier.size(size).clip(Squircle(12.dp)).background(look.pictureBg(w.id, w.image.isNotBlank())),
        contentAlignment = Alignment.Center
    ) {
        if (w.image.isNotBlank()) WishImage(w.image, Modifier.fillMaxSize().padding(4.dp))
        else PlaceholderIcon("gift", size * 0.42f)
    }
}
