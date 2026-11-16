package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The one-time tip that introduces the heart (owner, 16 November 2026: "Love it" means nothing without context):
 * "Tap ♡ on what you love most. Friends see it first.", with the real heart drawn in the words. Only on your own
 * lists, once a list has 3 wishes you still want and none is loved yet. It never comes back once closed or once you
 * used a heart anywhere.
 */
object LoveTip {
    private const val KEY = "love_tip_done"
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun done(c: Context): Boolean { @Suppress("UNUSED_EXPRESSION") version; return prefs(c).getBoolean(KEY, false) }

    fun finish(c: Context) { prefs(c).edit().putBoolean(KEY, true).apply(); version++ }

    /** Whether the tip shows above [wishes] (the open list, your own). */
    fun shows(c: Context, wishes: List<Wish>): Boolean =
        !done(c) && wishes.count { !it.got && it.bin == 0L } >= 3 && wishes.none { it.star }
}

@Composable
fun LoveTipCard(ctx: Context, wishes: List<Wish>) {
    if (!LoveTip.shows(ctx, wishes)) return
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).lookSurface(look, look.cardShape(21L, small = true), look.cardFill(21L), depth = 4.dp)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(StarGold), contentAlignment = Alignment.Center) {
            LoveIcon(true, StarInk, 20.dp)
        }
        val words = tr("Tap %s on what you love most. Friends see it first.")
        val at = words.indexOf("%s")
        val text = buildAnnotatedString {
            if (at < 0) append(words) else {
                append(words.substring(0, at)); appendInlineContent("heart", "♡"); append(words.substring(at + 2))
            }
        }
        val heart = mapOf("heart" to InlineTextContent(Placeholder(1.1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            LoveIcon(false, cs.onSurface, 18.dp, Modifier.fillMaxSize())
        })
        Text(
            text, inlineContent = heart, style = MaterialTheme.typography.bodyMedium, color = look.cardInk(21L),
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)
        )
        IconButton(onClick = { LoveTip.finish(ctx) }) {
            Icon(AppIcons.Close, tr("Close tip"), tint = look.cardMuted(21L), modifier = Modifier.size(20.dp))
        }
    }
}
