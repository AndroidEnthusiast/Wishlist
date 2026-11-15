package com.wishlist.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Smooth squircle corners (owner, 15 November 2026, docs/styles/identity.md): the corner eases out of the straight edge
 * instead of starting a circle arc at once, as Android's plain rounded corners do. "Continuous" corners, like a
 * superellipse; [smoothing] 0 is a plain rounded corner, 0.6 the usual smooth one. Our own geometry (the published
 * construction of a smoothed corner: two curves around a shorter circle arc), no library.
 */
class SquircleShape(
    topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize,
    private val smoothing: Float = 0.6f
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(topStart: Dp, topEnd: Dp = topStart, bottomEnd: Dp = topStart, bottomStart: Dp = topStart, smoothing: Float = 0.6f) :
        this(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing)

    override fun createOutline(
        size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float, layoutDirection: LayoutDirection
    ): Outline {
        val ltr = layoutDirection == LayoutDirection.Ltr
        // Corners in screen order: top-left, top-right, bottom-right, bottom-left.
        return if (ltr) Outline.Generic(squirclePath(size, topStart, topEnd, bottomEnd, bottomStart, smoothing))
        else Outline.Generic(squirclePath(size, topEnd, topStart, bottomStart, bottomEnd, smoothing))
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun equals(other: Any?) = other is SquircleShape && other.topStart == topStart && other.topEnd == topEnd &&
        other.bottomEnd == bottomEnd && other.bottomStart == bottomStart && other.smoothing == smoothing
    override fun hashCode() = listOf(topStart, topEnd, bottomEnd, bottomStart, smoothing).hashCode()
}

/** The measurements of one smoothed corner, along its two edges (from the corner). */
internal class CornerCurve(val r: Float, val p: Float, val a: Float, val b: Float, val c: Float, val d: Float, val arc: Float)

/** One corner of radius [radius] with [smoothing], never longer along an edge than [limit] (half the shorter side). */
internal fun cornerCurve(radius: Float, smoothing: Float, limit: Float): CornerCurve {
    val r = min(radius, limit).coerceAtLeast(0f)
    if (r <= 0f) return CornerCurve(0f, 0f, 0f, 0f, 0f, 0f, 90f)
    // The smoothing shrinks when the corner would run past the middle of the edge.
    val s = min(smoothing, max(0f, limit / r - 1f))
    val p = (1 + s) * r
    val arc = 90f * (1 - s)
    val rad = { deg: Float -> deg * PI.toFloat() / 180f }
    val arcLen = sin(rad(arc / 2)) * r * sqrt(2f)
    val alpha = (90f - arc) / 2
    val p3p4 = r * tan(rad(alpha / 2))
    val beta = 45f * s
    val c = p3p4 * cos(rad(beta))
    val d = c * tan(rad(beta))
    val b = (p - arcLen - c - d) / 3
    return CornerCurve(r, p, 2 * b, b, c, d, arc)
}

/** The outline: straight edges joined by smoothed corners (tl, tr, br, bl in pixels). */
internal fun squirclePath(size: Size, tl: Float, tr: Float, br: Float, bl: Float, smoothing: Float): Path {
    val w = size.width; val h = size.height
    val limit = min(w, h) / 2
    val path = Path()
    // Each corner in its own frame: i = distance back along the edge coming in, o = distance along the edge going out.
    // The four frames are turns of the same corner, so one drawing serves all four.
    val frames: List<Pair<CornerCurve, (Float, Float) -> Offset>> = listOf(
        cornerCurve(tr, smoothing, limit) to { i, o -> Offset(w - i, o) },
        cornerCurve(br, smoothing, limit) to { i, o -> Offset(w - o, h - i) },
        cornerCurve(bl, smoothing, limit) to { i, o -> Offset(i, h - o) },
        cornerCurve(tl, smoothing, limit) to { i, o -> Offset(o, i) },
    )
    val start = frames[3].let { (k, f) -> f(0f, k.p) }
    path.moveTo(start.x, start.y)
    for ((k, f) in frames) {
        fun line(i: Float, o: Float) { val q = f(i, o); path.lineTo(q.x, q.y) }
        fun cubic(i1: Float, o1: Float, i2: Float, o2: Float, i3: Float, o3: Float) {
            val a = f(i1, o1); val b = f(i2, o2); val c = f(i3, o3)
            path.cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
        }
        line(k.p, 0f)
        if (k.r <= 0f) continue
        // In: the curve from the straight edge onto the arc.
        cubic(k.p - k.a, 0f, k.p - k.a - k.b, 0f, k.p - k.a - k.b - k.c, k.d)
        // The circle arc between (centre at r, r), drawn as one curve.
        val si = k.p - k.a - k.b - k.c; val so = k.d
        val ei = k.d; val eo = k.p - k.a - k.b - k.c
        val theta = k.arc * PI.toFloat() / 180f
        val handle = 4f / 3f * tan(theta / 4) * k.r
        fun tangent(i: Float, o: Float, towardI: Float, towardO: Float): Pair<Float, Float> {
            // Perpendicular to the radius, pointing the way the outline goes.
            val ri = i - k.r; val ro = o - k.r
            var ti = -ro; var to = ri
            if (ti * towardI + to * towardO < 0) { ti = -ti; to = -to }
            val n = sqrt(ti * ti + to * to).coerceAtLeast(1e-6f)
            return ti / n to to / n
        }
        val (t1i, t1o) = tangent(si, so, ei - si, eo - so)
        val (t2i, t2o) = tangent(ei, eo, ei - si, eo - so)
        cubic(si + t1i * handle, so + t1o * handle, ei - t2i * handle, eo - t2o * handle, ei, eo)
        // Out: the curve from the arc onto the next straight edge.
        cubic(0f, k.p - k.a - k.b, 0f, k.p - k.a, 0f, k.p)
    }
    path.close()
    return path
}

/** A squircle with the same radius on every corner. */
fun Squircle(radius: Dp) = SquircleShape(radius)

/** A squircle with only its top corners rounded (panels that slide up). */
fun SquircleTop(radius: Dp) = SquircleShape(radius, radius, 0.dp, 0.dp)

/** The style's rounded shape of [radius]: a squircle in Quiet, the style's own rounded corner in Gen Z chaos. */
fun Look.soft(radius: Dp): androidx.compose.ui.graphics.Shape =
    if (design == "quiet") Squircle(radius) else androidx.compose.foundation.shape.RoundedCornerShape(radius)

