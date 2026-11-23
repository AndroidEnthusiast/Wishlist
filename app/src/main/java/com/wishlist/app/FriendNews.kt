package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * New wishes of friends (owner, 2.139; per list since 23 November 2026). What you've seen is kept on this phone per
 * friend (the wish ids). A friend's page shows a red badge with "how many new" on each of their wishlists; opening a
 * list makes its wishes seen, and the new ones carry a purple "NEW" ribbon until you leave the list ([NewRibbon]).
 * Opening their page no longer counts as seeing everything (owner: the dot on the friend went away, but you couldn't
 * tell which list had the new wishes). The first time a friend is counted, everything counts as seen, so nobody starts
 * with a pile of "new" wishes. The friend's row shows "3 new wishes" without the red dot (owner, 23 November 2026).
 */
object FriendNews {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    // Goes up when something was seen: the badges on a friend's page draw again.
    var seenTick by mutableIntStateOf(0)
        private set
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun key(id: String) = "fseen_$id"
    internal fun ids(lists: Collection<RemoteList>) =
        lists.flatMap { it.wishes }.filter { it.bin == 0L && it.id != ABOUT_ITEM_ID && !it.got }.map { it.id.toString() }.toSet()

    suspend fun refresh(c: Context, friends: List<Person>) {
        val out = HashMap<String, Int>()
        for (f in friends) {
            val lists = f.lists.mapNotNull { l -> try { Friends.fetchList(c, f.id, l.code) } catch (e: Exception) { null } }
            if (lists.size < f.lists.size && f.lists.isNotEmpty()) { counts[f.id]?.let { out[f.id] = it }; continue }
            out[f.id] = start(c, f.id, lists)
        }
        counts = out
    }

    /** How many of [lists] friend [id] has that you haven't seen; the first time, all of them become seen (0). */
    fun start(c: Context, id: String, lists: Collection<RemoteList>): Int {
        val now = ids(lists)
        val seen = prefs(c).getStringSet(key(id), null)
        if (seen == null) { prefs(c).edit().putStringSet(key(id), now).apply(); return 0 }
        return (now - seen).size
    }

    /** The wishes on list [r] of friend [id] that you haven't seen (none before the friend was first counted). */
    fun newIds(c: Context, id: String, r: RemoteList): Set<Long> {
        val seen = prefs(c).getStringSet(key(id), null) ?: return emptySet()
        return (ids(listOf(r)) - seen).mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** List [r] of friend [id] was opened: its wishes are seen; the friend's count goes down by as many. */
    fun markListSeen(c: Context, id: String, r: RemoteList) {
        val fresh = newIds(c, id, r)
        val seen = prefs(c).getStringSet(key(id), null) ?: emptySet()
        prefs(c).edit().putStringSet(key(id), seen + ids(listOf(r))).apply()
        if (fresh.isNotEmpty()) {
            counts = counts + (id to ((counts[id] ?: 0) - fresh.size).coerceAtLeast(0))
            seenTick++
        }
    }
}

/** The royal purple of the "NEW" ribbon: the same in every theme, light and dark (owner, 23 November 2026). */
val NewRibbonColour = Color(0xFF6A3FB5)

/**
 * The "NEW" ribbon across the top right corner of a new wish on a friend's list (owner, 23 November 2026: "a diagonal
 * ribbon, not too flashy but something that stands out, royal purple, suitable for every theme"). Goes over the card,
 * clipped to the card's [shape].
 */
@Composable
fun BoxScope.NewRibbon(shape: Shape) {
    val label = tr("New")
    Box(Modifier.matchParentSize().clip(shape).semantics { contentDescription = label }) {
        Box(Modifier.align(Alignment.TopEnd).size(64.dp)) {
            // A band 16 to 36 dp from the corner along both edges.
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val a = 16.dp.toPx(); val b = 36.dp.toPx()
                drawPath(Path().apply {
                    moveTo(w - b, 0f); lineTo(w - a, 0f); lineTo(w, a); lineTo(w, b); close()
                }, NewRibbonColour)
            }
            Text(
                label.uppercase(), color = Color.White,
                style = TextStyle(fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                modifier = Modifier.align(Alignment.Center).offset(19.dp, (-19).dp).rotate(45f)
            )
        }
    }
}
