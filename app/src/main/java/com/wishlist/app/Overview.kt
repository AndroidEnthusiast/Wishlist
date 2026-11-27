package com.wishlist.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The top of Home (2.89, owner): your picture with your name under it, and three numbers next to it: wishlists,
 * wishes and top wishes. Each number opens a page: all your wishlists (your own, then group wishlists), all wishes of
 * your own lists, or only the top wishes (with a star to take a wish off again).
 */

/**
 * A wish opened from "All your wishes" or "Top wishes" (2.91), or a notification about it (2.161): the list scrolls to [target] and lets it glow softly
 * twice ([GlowBox]).
 */
object WishFocus {
    var target by mutableStateOf<Long?>(null)
    /** The wish that glows and a counter, so the same wish can glow again. */
    var glowing by mutableStateOf<Pair<Long, Int>?>(null)
        private set

    /**
     * A notification about one wish on your own list or a collab wishlist (a price change, a gift moved, …), tapped on
     * the Notifications page or as a push: its list opens on that wish (owner, 8 November 2026, 2.161). Friends'
     * lists load from the server first, so they open at the top; "Still buying …?" news has its own (AskNav).
     */
    fun forNote(listKey: String, wishId: Long, kind: String) {
        if (wishId <= 0L || kind.startsWith("ask")) return
        if (listKey.startsWith("m:") || listKey.startsWith("s:")) target = wishId
    }

    fun glow(id: Long) { glowing = id to ((glowing?.second ?: 0) + 1) }

    /** The glow of [id] is over: the card goes back to how it was. */
    fun done(id: Long) { if (glowing?.first == id) glowing = null }
}

/**
 * Around a wish card on a list: a soft glow in the theme's colour that pulses twice and fades, when [WishFocus] says
 * so. One smooth wave (up, down, up, down) with no stops between (2.94: in 2.93 the steps each slowed down at their
 * end, so the last fade seemed to pause). The card is always inside the same Box: only the glow comes and goes, so
 * the card is never rebuilt and its photo doesn't flash (it did in 2.93). When it is over, or stopped halfway,
 * [WishFocus] forgets it.
 */
@Composable
fun GlowBox(id: Long, small: Boolean, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val col = MaterialTheme.colorScheme.primary
    val p = remember { Animatable(0f) }
    val n = WishFocus.glowing?.takeIf { it.first == id }?.second ?: 0
    LaunchedEffect(n) {
        if (n == 0) return@LaunchedEffect
        try {
            p.snapTo(0f)
            p.animateTo(1f, tween(1800, easing = LinearEasing))
        } finally {
            // No suspending here (it may be a cancellation): forgetting it is enough, lit is 0 then.
            WishFocus.done(id)
        }
    }
    // Two pulses: 0 → 1 → 0 → 1 → 0, as a smooth wave.
    val lit = if (n == 0) 0f else ((1f - kotlin.math.cos(4f * Math.PI.toFloat() * p.value)) / 2f)
    val shape = look.cardShape(id, small)
    Box(
        if (lit > 0.01f) Modifier
            .shadow((18 * lit).dp, shape, clip = false, ambientColor = col, spotColor = col)
            .border(3.dp, col.copy(alpha = 0.8f * lit), shape)
        else Modifier
    ) { content() }
}


/**
 * A wish with the lists it is on (your own). A wish copied into other lists keeps its id, so it is one wish: counted
 * and shown once, with every list in "Added to …" (owner, 2.90). Top wish when any of its copies is.
 */
internal data class Placed(val w: Wish, val listIds: List<String>, val listNames: List<String>) {
    val starred: Boolean get() = w.star
}

private fun listLabel(name: String) = if (name == "My wishlist") tr(name) else name

@Composable
internal fun placedWishes(): List<Placed> {
    val ctx = LocalContext.current
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    return mine.flatMap { d -> d.wishes.map { Triple(it, d.id, listLabel(d.name)) } }
        .groupBy { it.first.id }
        .map { (_, copies) ->
            val w = copies.first().first.copy(star = copies.any { it.first.star })
            Placed(w, copies.map { it.second }, copies.map { it.third })
        }
        .sortedByDescending { it.w.id }
}

/**
 * Your picture on the left; next to it your name in a heading size and under it the three tiles (owner, 7 November
 * 2026): Friends (the Friends tab), Wishes (all your wishes) and Total value (what your own wishlists still wish for,
 * collab wishlists left out; only a number, no tap). At 0 a tile invites: "+ Add friends", "+ Add wish".
 */
@Composable
fun ProfileSummary(onProfile: () -> Unit, onFriends: () -> Unit = {}, onAddWish: () -> Unit = {}) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    Friends.load(ctx)
    val all = placedWishes()
    val friends = Friends.state?.friends?.size ?: 0
    val total = homeTotal(all.map { it.w })
    val look = LocalLook.current
    if (look.design == "quiet") {
        // Quiet (2.207, docs/styles/quiet.md): your picture and name in Fraunces, "Add friends" a proper button while
        // you have none, and the three numbers in even columns across the card with hairlines between them.
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth()
                .lookCard(look, 5L, look.cardShape(5L, small = true)).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = tr("Profile"), onClick = onProfile)) {
                    Avatar(Friends.myPhoto, Shared.lastName, 56.dp, ring = Iconic.myRing)
                }
                Text(
                    Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = look.cardInk(5L),
                    modifier = Modifier.weight(1f).padding(start = 14.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onProfile).padding(horizontal = 2.dp)
                )
                if (friends == 0 && Config.enabled) FilledTonalButton(onClick = onFriends) {
                    Icon(AppIcons.Add, null, Modifier.size(18.dp)); Text(tr("Add friends"), Modifier.padding(start = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                val line = look.scheme.outlineVariant
                Stat(friends.toString(), false, if (friends == 1) tr("Friend") else tr("Friends"), Modifier.weight(1f), null, onFriends, onFriends, plain = true)
                Box(Modifier.width(1.dp).fillMaxHeight(0.8f).background(line))
                Stat(all.size.toString(), all.isEmpty(), if (all.size == 1) tr("Wish") else tr("Wishes"), Modifier.weight(1f), tr("Add wish"), onAddWish, onClick = { places.homePage = "wishes" }, plain = true)
                Box(Modifier.width(1.dp).fillMaxHeight(0.8f).background(line))
                Stat(total, false, tr("Total value"), Modifier.weight(1.3f), onClick = null, plain = true)
            }
        }
        return
    }
    // One soft card in the style's own look around you and your numbers (owner, 7 November 2026), set apart from the
    // lists below; inside it the numbers are plain, with thin lines between them (no cards in a card). Since 2.209 the
    // numbers have the card's full width under your photo and name, as in Quiet: next to the photo "€1,434.56" was cut
    // off (owner's screenshot, 15 November 2026).
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth()
            .lookCard(look, 5L, look.cardShape(5L, small = true)).padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = tr("Profile"), onClick = onProfile)) {
                Avatar(Friends.myPhoto, Shared.lastName, 56.dp, ring = Iconic.myRing)
            }
            Text(
                Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.titleLarge,
                fontWeight = look.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, color = look.cardInk(5L),
                modifier = Modifier.weight(1f).padding(start = 14.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onProfile).padding(horizontal = 2.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            val line = look.cardMuted(5L).copy(alpha = 0.3f)
            Stat(friends.toString(), friends == 0, if (friends == 1) tr("Friend") else tr("Friends"), Modifier.weight(1f), if (Config.enabled) tr("Add friends") else null, onFriends, onFriends, plain = true)
            Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(line))
            Stat(all.size.toString(), all.isEmpty(), if (all.size == 1) tr("Wish") else tr("Wishes"), Modifier.weight(1f), tr("Add wish"), onAddWish, onClick = { places.homePage = "wishes" }, plain = true)
            Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(line))
            Stat(total, false, tr("Total value"), Modifier.weight(1.3f), onClick = null, plain = true)
        }
    }
}

/**
 * The small tile behind a number (owner, 2.96: "separate tiles, but subtle"). Quiet: the card colour, a little see-
 * through, no shadow, a faint edge in dark. The other styles (owner, 2.103: the tiles must follow the theme): a small
 * version of their own card, with its lines, hard shadow, cut corners or raised neumorphic shape.
 */
@Composable
fun Modifier.statTile(): Modifier {
    val look = LocalLook.current
    return when (look.design) {
        "chaos" -> lookSurface(look, RoundedCornerShape(14.dp), look.cardFill(5L), depth = 3.dp)
        "quiet" -> {
            val shape = RoundedCornerShape(12.dp)
            clip(shape).background(look.cardFill(5L).copy(alpha = 0.75f))
                .then(if (look.dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), shape) else Modifier)
        }
        // The other styles: a small version of their own card (its shape, line and shadow).
        else -> lookSurface(look, MaterialTheme.shapes.medium, look.cardFill(5L), depth = 3.dp)
    }
}

/**
 * One tile: the number (or amount) and its label; at 0 ([zero]) with an [invite], a "+" and the invite in the accent
 * colour instead. [onClick] null: only shown, not tappable (Total value).
 */
@Composable
private fun Stat(
    value: String, zero: Boolean, label: String, modifier: Modifier = Modifier, invite: String? = null, onInvite: () -> Unit = {},
    onClick: (() -> Unit)?,
    // Inside the profile card: no tile of its own (owner, 7 November 2026).
    plain: Boolean = false
) {
    val look = LocalLook.current
    val inviting = zero && invite != null
    val tap = if (inviting) onInvite else onClick
    Column(
        modifier.then(if (plain) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier.statTile())
            .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The tile's own ink (dark on the bright cards of some styles) and the style's display font for the number.
        val ink = if (inviting) MaterialTheme.colorScheme.primary else look.cardInk(5L)
        // A number or amount is never cut off: it gets a little smaller instead (2.209, FitText).
        FitText(
            if (inviting) "+" else value, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = look.display), ink
        )
        Text(
            if (inviting) invite!! else label, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (inviting) FontWeight.SemiBold else null,
            color = if (inviting) ink else look.cardMuted(5L), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
