package com.wishlist.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/*
 * The bar at the bottom (since 2.65): quick access to what matters most, next to the side menu.
 * Home, Messages, Add wish (just a +), Discover (the shops, since 7 November 2026; Price watch moved to a wish's price
 * page, "All price changes") and Friends.
 * It shows on the dashboard, on a list, on Prices and on Friends; not on settings screens or the bin.
 */

@Composable
fun AppBottomBar(
    current: String,
    onHome: () -> Unit,
    onMessages: () -> Unit,
    onAdd: () -> Unit,
    // Discover, the shops (owner, 7 November 2026: in the place of Price watch).
    onDiscover: () -> Unit,
    onFriends: () -> Unit,
    // Friend requests wait for your approval.
    friendsDot: Boolean = false,
    // Unread messages.
    messagesDot: Boolean = false
) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    // A floating bar (2.97, the preview the owner chose): rounded, a little above the bottom edge, in the style's
    // surface. Quiet: nearly opaque with a hairline and a soft shadow, like frosted glass.
    val shape = when (look.design) {
        "chaos" -> RoundedCornerShape(20.dp)
        "y2k", "pop" -> RoundedCornerShape(32.dp)
        "retro" -> RoundedCornerShape(16.dp)
        "flat" -> RoundedCornerShape(18.dp)
        "grunge" -> RoughShape(101, 2.dp, 9.dp)
        "bauhaus" -> androidx.compose.ui.graphics.RectangleShape
        "doodle" -> WobblyShape(111, 26.dp, 2.dp)
        "japandi" -> RoundedCornerShape(8.dp)
        "riso" -> RoundedCornerShape(10.dp)
        "synth", "cottage" -> RoundedCornerShape(18.dp)
        "anthro" -> RoundedCornerShape(32.dp)
        "swiss" -> androidx.compose.ui.graphics.RectangleShape
        "punk" -> RoughShape(121, 2.6.dp, 9.dp)
        else -> RoundedCornerShape(26.dp)
    }
    val frame = if (look.design == "quiet") Modifier
        .shadow(12.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.18f))
        .clip(shape).background(cs.surfaceVariant.copy(alpha = 0.94f)).border(1.dp, look.line, shape)
    else Modifier.lookSurface(look, shape, cs.surfaceVariant, depth = 7.dp)
    Box(
        Modifier.fillMaxWidth().background(cs.background).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
            .then(
                when (look.design) {
                    "chaos" -> Modifier.rotate(-0.8f)
                    "grunge" -> Modifier.rotate(-0.4f)
                    "doodle" -> Modifier.rotate(-0.5f)
                    "punk" -> Modifier.rotate(-1.2f)
                    else -> Modifier
                }
            )
    ) {
        Row(
            frame.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), // 72 with the words (2.121–2.148)
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically
        ) {
            // Icons only, a little bigger (owner, 7 November 2026); their names are read out by TalkBack.
            BarItem(tr("Home"), current == "home", onHome) { Icon(AppIcons.Home, null, tint = it, modifier = Modifier.size(30.dp)) }
            BarItem(tr("Messages"), current == "messages", onMessages, dot = messagesDot) { ChatIcon(it, 30.dp) }
            // Add wish: only a + in the accent colour, no words. Its own wider slot (owner, 2.126: more breathing space,
            // "Price watch" seemed closer to the + than "Messages"): the same room on both sides.
            Box(Modifier.width(78.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(50.dp).then(
                    if (look.design == "quiet") Modifier.shadow(6.dp, CircleShape, clip = false, ambientColor = look.accent, spotColor = look.accent)
                        .clip(CircleShape).background(look.accent)
                    else Modifier.lookSurface(look, look.fabShape, look.accent, depth = 5.dp, brush = look.accentBrush)
                ).clickable(onClickLabel = tr("Add wish"), onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Add, tr("Add wish"), tint = look.onAccent, modifier = Modifier.size(30.dp))
            }
            }
            BarItem(tr("My shops"), current == "discover", onDiscover) { Icon(AppIcons.Store, null, tint = it, modifier = Modifier.size(30.dp)) }
            BarItem(tr("Friends"), current == "friends", onFriends, dot = friendsDot) { Icon(AppIcons.Person, null, tint = it, modifier = Modifier.size(30.dp)) }
        }
    }
}

/** One place in the bar: its icon and name; the open one in full colour and bold, the others quieter. */
@Composable
private fun RowScope.BarItem(label: String, on: Boolean, onClick: () -> Unit, dot: Boolean = false, icon: @Composable (Color) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (on) cs.onSurface else cs.onSurfaceVariant
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = label; selected = on }
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            icon(tint)
            if (dot) RedDot(Modifier.align(Alignment.TopEnd).offset(4.dp, (-3).dp))
        }
    }
}

/** A bar label: one line, a little smaller, never cut off ("New wishlist" is the longest). */
@Composable
private fun BarLabel(text: String) {
    Text(
        text, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    )
}

/** A wishlist: a page with a heart and two lines (drawn here, in one colour like the other icons). */
@Composable
fun WishlistIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // The page, with a second edge at the right and bottom, like a little book.
        drawRoundRect(tint, Offset(3.5f * u, 2.5f * u), Size(14.5f * u, 17.5f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = st)
        val back = Path().apply {
            moveTo(20.5f * u, 6f * u)
            lineTo(20.5f * u, 21.5f * u)
            lineTo(7f * u, 21.5f * u)
        }
        drawPath(back, tint, style = st)
        // The heart.
        val heart = Path().apply {
            moveTo(10.75f * u, 12f * u)
            cubicTo(7.2f * u, 9.6f * u, 7.4f * u, 6.2f * u, 9.4f * u, 6.1f * u)
            cubicTo(10.2f * u, 6.05f * u, 10.6f * u, 6.6f * u, 10.75f * u, 7.1f * u)
            cubicTo(10.9f * u, 6.6f * u, 11.3f * u, 6.05f * u, 12.1f * u, 6.1f * u)
            cubicTo(14.1f * u, 6.2f * u, 14.3f * u, 9.6f * u, 10.75f * u, 12f * u)
            close()
        }
        drawPath(heart, tint, style = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Two lines: the wishes.
        drawLine(tint, Offset(7.2f * u, 14.6f * u), Offset(14.3f * u, 14.6f * u), 1.9f * u, StrokeCap.Round)
        drawLine(tint, Offset(7.2f * u, 17.2f * u), Offset(14.3f * u, 17.2f * u), 1.9f * u, StrokeCap.Round)
    }
}

/** A small line chart going down: price history. */
@Composable
fun PriceIcon(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        val p = Path().apply {
            moveTo(3f * u, 7f * u)
            lineTo(9f * u, 13f * u)
            lineTo(13f * u, 9f * u)
            lineTo(21f * u, 17f * u)
        }
        drawPath(p, tint, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The arrow head at the end.
        val a = Path().apply {
            moveTo(15.5f * u, 17f * u)
            lineTo(21f * u, 17f * u)
            lineTo(21f * u, 11.5f * u)
        }
        drawPath(a, tint, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(3f * u, 21f * u), Offset(21f * u, 21f * u), 1.2f * u, StrokeCap.Round)
    }
}

/**
 * "New list" (owner, 2.128: one item in the side menu instead of three): your own wishlist, a gift list or a wishlist
 * group, each with the same line as its section on Home; the same forms as the + buttons on Home.
 */
@Composable
fun CreateChoiceDialog(onMine: () -> Unit, onGroup: () -> Unit, onWishGroup: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Create a list")) },
        text = {
            // Owner, 7 November 2026: clear what each makes and where it shows.
            Column {
                Choice(tr("Wishlist"), tr("For things you’d love to get. Shows under Your wishlists.")) { onMine() }
                Choice(tr("Collab wishlist"), tr("Find and save gifts for someone, together with others.")) { onGroup() }
                Choice(tr("Wishlist group"), tr("A circle of people who can see each other’s wishlists.")) { onWishGroup() }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun Choice(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
