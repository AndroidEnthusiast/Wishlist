package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The state holder of the reactions under one wish (the rebuild, owner 19 November 2026; MAP "How a screen is
 * built"): which dialog is open and reacting. The row (ReactionBar in ReactionViews.kt) only draws; the reactions are
 * kept by Reactions (Reactions.kt). [code]: the list's code.
 */
@Stable
internal class ReactionBarState(private val ctx: Context, private val code: String, private val scope: CoroutineScope) {
    var picking by mutableStateOf(false)
    // You react once per wish (owner, 18 November 2026): once you have, "+ React" hides to keep the card clean.
    // Tapping your reaction takes it away at once, and "+ React" is back (owner, 20 November 2026: before, a first tap
    // only brought "+ React" back and your reaction stayed until a second tap).
    // The emoji whose people are shown (a long press), and every reaction (the "+N" circle).
    var who by mutableStateOf<String?>(null)
    var all by mutableStateOf(false)
    // A message when a reaction couldn't be saved (shown once).
    var msg by mutableStateOf<String?>(null)

    /** Reacts on wish [item] with [emoji] ("" takes yours away). It shows at once; a "no" puts it back with a message. */
    fun react(item: Long, emoji: String) {
        scope.launch { msg = Reactions.set(ctx, code, item, emoji) }
    }

    /** A tap on a reaction chip: who reacted (when you may not react), your own (takes it away), or the same as someone's. */
    fun tapChip(item: Long, emoji: String, mine: Boolean, canReact: Boolean) {
        if (!canReact) who = emoji
        else react(item, if (mine) "" else emoji)
    }
}
