package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Wish cards that move to where they go (owner, 25 November 2026: "animations that show clearly the user is doing
 * something", and "similar effects should be the same"; docs/decisions/look.md, last section). One family each:
 *  - leaving: the card shrinks and tips into where it goes: the bin (its own bin button, or the selection bar's
 *    Delete) when deleted, the Home icon in the bottom bar when moved to another list;
 *  - a copy peels off: Copy and Add to my wishlist lift a copy off the card, which flies to the Home icon (the
 *    original stays);
 *  - backwards: Undo of a delete brings the card out of the bin again;
 *  - arriving: a new wish (saved, imported, made from a screenshot, added from a friend's list, copied) drops into
 *    its place in the list.
 * A card is drawn into its own layer ([motionCard]), so the flight shows a real picture of it, drawn above the
 * screen ([FlightLayer]) while the list underneath has already changed. Kept in the app frame's state holder
 * ([LocalCardMotion], AppScreen.kt), not in a global object. "Remove animations": nothing flies, it just happens.
 */

/** One card in flight. [toBin]: draw the bin at [to] (its lid opens and shuts). [peel]: lift first. [back]: reversed. */
@Stable
class Flight(
    val id: Long, val image: ImageBitmap, val from: Rect, val to: Rect,
    val toBin: Boolean, val peel: Boolean, val back: Boolean = false, val home: Boolean = false,
    // What changes the data once the card is in the bin (a delete waits for it, so the list doesn't jump underneath).
    val onLand: (() -> Unit)? = null
)

@Stable
class CardMotion(private val scope: CoroutineScope, private val off: () -> Boolean) {
    internal val layers = HashMap<Long, Snapper>()
    internal val bounds = HashMap<Long, Rect>()
    internal val bins = HashMap<Long, Rect>()
    internal var homeAt: Rect? = null
    internal var deleteAt: Rect? = null

    var flights by mutableStateOf(listOf<Flight>())
        private set
    /** Goes up when something lands on the Home icon, which gives a small bounce. */
    var homeBounce by mutableIntStateOf(0)
        private set
    /** Cards already seen on this run, so a card that scrolls back into sight doesn't arrive again. */
    private val seen = HashSet<Long>()
    private var batchAt = 0L
    private var batchN = 0
    /** Cards kept invisible while their picture flies back to them (Undo). */
    var hidden by mutableStateOf(setOf<Long>())
        private set
    /** Cards whose own bin button is left out while their picture is taken (the real bin is drawn in its place). */
    var binless by mutableStateOf(setOf<Long>())
        private set
    private var lastDelete: List<Flight> = emptyList()

    private fun target(kind: String, id: Long): Rect? = when (kind) {
        "bin" -> bins[id]
        "delete" -> deleteAt
        else -> homeAt
    }

    /**
     * The cards [ids] go to [kind] ("bin": their own bin button; "delete": the selection bar's Delete; "home": the
     * Home icon); [peel]: a copy lifts off and the card stays. [then] changes the data at once (the flight covers it).
     */
    fun fly(ids: Collection<Long>, kind: String, peel: Boolean = false, then: () -> Unit) {
        if (off()) { then(); return }
        scope.launch {
            if (kind == "bin") binless = binless + ids
            val made = ids.mapNotNull { id ->
                val layer = layers[id] ?: return@mapNotNull null
                val from = bounds[id] ?: return@mapNotNull null
                val to = target(kind, id) ?: return@mapNotNull null
                val image = try { layer.snap() } catch (e: Exception) {
                    // No picture (a card off screen, a phone that can't): it simply happens without a flight.
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                } ?: return@mapNotNull null
                Flight(id, image, from, to, toBin = kind != "home", peel = peel, home = kind == "home")
            }
            // A move can take a moment online: the moving cards stay hidden while they fly (and a little after), so
            // they are never on screen twice; if the move fails they simply show again.
            if (kind == "home" && !peel) hidden = hidden + made.map { it.id }
            if (peel || made.isEmpty()) {
                then()
                binless = binless - ids.toSet()
                if (made.isEmpty()) return@launch
                flights = flights + made
                return@launch
            }
            // Moved: like a delete, its place stays until it reaches the Home icon (no jump underneath).
            if (kind == "home") {
                var moved = false
                val go = { if (!moved) { moved = true; then() } }
                flights = flights + made.map { Flight(it.id, it.image, it.from, it.to, toBin = false, peel = false, home = true, onLand = go) }
                return@launch
            }
            // Into the bin (owner, 26 November 2026: the list jumped underneath): the card leaves its place empty while
            // it flies, and the wish is deleted when it is in the bin; then the list closes the gap.
            hidden = hidden + made.map { it.id }
            binless = binless - ids.toSet()
            lastDelete = made
            var done = false
            val once = { if (!done) { done = true; then() } }
            flights = flights + made.map { Flight(it.id, it.image, it.from, it.to, toBin = true, peel = false, onLand = once) }
        }
    }

    /** Undo of a delete: the cards come back out of the bin to where they were. */
    fun back(ids: Collection<Long>, then: () -> Unit) {
        val l = lastDelete.filter { it.id in ids }
        lastDelete = emptyList()
        if (off() || l.isEmpty()) { then(); return }
        hidden = hidden + l.map { it.id }
        then()
        // The same path played backwards (the bin stays at its place).
        flights = flights + l.map { Flight(it.id, it.image, it.from, it.to, toBin = true, peel = false, back = true) }
    }

    /**
     * Whether card [id] is new and should drop into its place, and after how long. A wish's id is the moment it was
     * made, so one made in the last minute is new wherever it was made (the form, an import, a screenshot, Add
     * to my wishlist, a copy), all with the same animation. New cards that show together arrive one by one, 90 ms
     * apart (an import), at most about a second in all. Null: not new, or already seen.
     */
    internal fun claimArrival(id: Long, now: Long = System.currentTimeMillis()): Int? {
        if (!seen.add(id) || off()) return null
        if (now - id !in 0L..60_000L) return null
        if (now - batchAt > 250L) { batchAt = now; batchN = 0 }
        return (batchN++ * 90).coerceAtMost(900)
    }

    internal fun landed(f: Flight) {
        flights = flights - f
        f.onLand?.invoke()
        if (f.home) {
            homeBounce++
            // A move can take a moment online: the card stays hidden a little longer; if it failed, it shows again.
            if (!f.peel) scope.launch { delay(1500); hidden = hidden - f.id }
        } else if (f.back || f.onLand != null) hidden = hidden - f.id
    }
}

val LocalCardMotion = androidx.compose.runtime.staticCompositionLocalOf<CardMotion?> { null }

@Composable
fun rememberCardMotion(): CardMotion {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember { CardMotion(scope) { animationsOff(ctx) } }
}

/**
 * A wish card that can fly and arrive: it is drawn into its own layer (for the picture in flight), says where it is,
 * hides while its picture flies back to it, and drops into its place when it is new.
 */
fun Modifier.motionCard(id: Long): Modifier = composed {
    val m = LocalCardMotion.current ?: return@composed this
    val snapper = remember { Snapper() }
    androidx.compose.runtime.DisposableEffect(id) {
        m.layers[id] = snapper
        onDispose { if (m.layers[id] === snapper) { m.layers.remove(id); m.bounds.remove(id) } }
    }
    // Arriving: from a little above, a little bigger and see-through, it settles in its place.
    val wait = remember(id) { m.claimArrival(id) }
    val t = remember(id) { Animatable(if (wait != null) 0f else 1f) }
    if (wait != null) LaunchedEffect(id) {
        if (wait > 0) delay(wait.toLong())
        t.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val gone = id in m.hidden
    this.onGloballyPositioned { m.bounds[id] = it.boundsInRoot() }
        .graphicsLayer {
            val p = t.value
            alpha = if (gone) 0f else p.coerceIn(0f, 1f)
            translationY = (1f - p) * -40f * density
            val s = 1f + (1f - p) * 0.08f
            scaleX = s; scaleY = s
        }
        .drawWithContent { snapper.draw(this) }
}

/**
 * Takes a picture of a card when asked ([snap]): on its next drawing it is drawn into a recording, which becomes the
 * picture and is drawn on screen as usual. Only then, so cards cost nothing extra otherwise.
 */
@Stable
internal class Snapper {
    private var want by mutableStateOf(false)
    private var result: kotlinx.coroutines.CompletableDeferred<ImageBitmap?>? = null

    suspend fun snap(): ImageBitmap? {
        val d = kotlinx.coroutines.CompletableDeferred<ImageBitmap?>()
        result = d
        want = true
        return kotlinx.coroutines.withTimeoutOrNull(400) { d.await() }
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope) = with(scope) {
        val r = result
        if (!want || r == null) { drawContent(); return@with }
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) { drawContent(); r.complete(null); return@with }
        val picture = android.graphics.Picture()
        val canvas = androidx.compose.ui.graphics.Canvas(picture.beginRecording(w, h))
        draw(this, layoutDirection, canvas, size) { this@with.drawContent() }
        picture.endRecording()
        drawIntoCanvas { it.nativeCanvas.drawPicture(picture) }
        result = null
        // A picture of what was just drawn (photos included); an older phone that can't makes no flight.
        val bmp = try {
            if (android.os.Build.VERSION.SDK_INT >= 28) android.graphics.Bitmap.createBitmap(picture)
            else android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { android.graphics.Canvas(it).drawPicture(picture) }
        } catch (e: RuntimeException) {
            if (e is IllegalStateException || e is IllegalArgumentException || e is UnsupportedOperationException) null else throw e
        }
        r.complete(bmp?.asImageBitmap())
        // Asked once: the next drawing is plain again.
        want = false
    }
}

/** Where a card's own bin button is (its picture flies into it). */
fun Modifier.motionBin(id: Long): Modifier = composed {
    val m = LocalCardMotion.current ?: return@composed this
    val off = id in m.binless
    onGloballyPositioned { m.bins[id] = it.boundsInRoot() }.graphicsLayer { alpha = if (off) 0f else 1f }
}

/** Where the Home icon of the bottom bar is (moved and copied wishes fly to it). */
fun Modifier.motionHome(): Modifier = composed {
    val m = LocalCardMotion.current ?: return@composed this
    onGloballyPositioned { m.homeAt = it.boundsInRoot() }
}

/** Where the selection bar's Delete is (several deleted wishes fly into it). */
fun Modifier.motionDelete(): Modifier = composed {
    val m = LocalCardMotion.current ?: return@composed this
    onGloballyPositioned { m.deleteAt = it.boundsInRoot() }
}

/** The Home icon's small bounce when something lands on it. */
fun Modifier.homeBounce(): Modifier = composed {
    val m = LocalCardMotion.current ?: return@composed this
    val n = m.homeBounce
    val s = remember { Animatable(1f) }
    LaunchedEffect(n) {
        if (n == 0) return@LaunchedEffect
        s.snapTo(1.22f)
        s.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Above every screen: the cards in flight (they never take taps). */
@Composable
fun FlightLayer(m: CardMotion) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    // Undo follows the card's place as the list settles, so it lands exactly where the card shows (no jump).
    // The bin's disc stands out from the page in light and dark (dark: a lighter tone than the page).
    val disc = if (LocalLook.current.dark) cs.surfaceContainerHighest else cs.surface
    m.flights.forEach { f -> key(f) { FlightView(f, cs.onSurface, disc, { m.bounds[f.id] }) { m.landed(f) } } }
}

@Composable
private fun FlightView(f: Flight, ink: Color, disc: Color, live: () -> Rect?, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // The bin has several beats (disc, lid, card, lid, bump, disc), each with its own ease, so its clock runs evenly.
        t.animateTo(1f, tween(if (f.toBin) 700 else if (f.peel) 680 else 560, easing = if (f.toBin) androidx.compose.animation.core.LinearEasing else FastOutSlowInEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = if (f.back) 1f - t.value else t.value
        if (f.toBin) {
            // Into a clear bin (owner, 26 November 2026: "the trashcan animation isn't clear"): it pops up on its own disc
            // where the card's bin button was, the lid opens, the card shrinks to the bin's opening and sinks in (cut off
            // at the rim), the lid shuts with a bump, and the disc goes. Undo plays it backwards to where the card is now.
            val bin = binAt(f.to)
            val from = if (f.back) live() ?: f.from else f.from
            drawDisc(bin, discScale(p), disc)
            // The card travels from 10 % to 62 % of the time, easing in and out, so you see it go in.
            val go = FastOutSlowInEasing.transform(((p - 0.1f) / 0.52f).coerceIn(0f, 1f))
            val rim = bin.center.y - 3f * density
            if (go > 0.6f) clipRect(bottom = rim) { drawIntoBin(f, from, bin, go) } else drawIntoBin(f, from, bin, go)
            drawBin(bin, lidOpen(p), bump(p, f.back), ink)
        } else drawFlight(f, p)
    }
}
