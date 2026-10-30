package com.wishlist.app

import kotlin.math.abs

/** One recorded price. [t] is in seconds since 1970. A point is only stored when the price actually changed. */
data class PricePoint(val t: Long, val p: Double)

/** The most points kept per wish. Older ones are dropped, except the very first. */
const val MAX_HISTORY_POINTS = 400

private const val DAY = 86400L

/** Adds a price to a history. Same price as before: nothing is added, so the history stays tiny. */
fun appendPoint(list: List<PricePoint>, t: Long, p: Double, limit: Int = MAX_HISTORY_POINTS): List<PricePoint> {
    if (list.isNotEmpty() && abs(list.last().p - p) < 0.005) return list
    // Keep time moving forward even if the phone's clock was changed.
    val time = if (list.isEmpty()) t else maxOf(t, list.last().t)
    val next = list + PricePoint(time, p)
    return if (next.size > limit) listOf(next.first()) + next.takeLast(limit - 1) else next
}

/** The price at moment [t]: the last recorded price at or before it (a price holds until it changes). */
fun priceAt(points: List<PricePoint>, t: Long): Double =
    points.lastOrNull { it.t <= t }?.p ?: points.first().p

/** What the graph covers. The time axis runs from the first record to now (at least one day wide). */
data class ChartRange(val t0: Long, val t1: Long, val yMin: Double, val yMax: Double)

fun chartRange(points: List<PricePoint>, nowSec: Long): ChartRange {
    val t0 = points.first().t
    val t1 = maxOf(nowSec, points.last().t, t0 + DAY)
    val lo = points.minOf { it.p }
    val hi = points.maxOf { it.p }
    val pad = if (hi - lo < 0.005) maxOf(hi * 0.05, 1.0) else (hi - lo) * 0.15
    return ChartRange(t0, t1, maxOf(0.0, lo - pad), hi + pad)
}

/** Change from the first to the last recorded price: amount and percent. Null if there is nothing to compare. */
fun changeSince(points: List<PricePoint>): Pair<Double, Double>? {
    if (points.size < 2) return null
    val first = points.first().p
    val last = points.last().p
    if (first <= 0.0 || abs(last - first) < 0.005) return null
    return Pair(last - first, (last - first) / first * 100.0)
}
