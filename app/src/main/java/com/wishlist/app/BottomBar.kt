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
import androidx.compose.foundation.layout.navigationBarsPadding
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
    FloatingBar {
            // Icons only, a little bigger (owner, 7 November 2026); their names are read out by TalkBack.
            // Moved and copied wishes fly to the Home icon, which bounces as they land (CardMotion.kt).
            BarItem(tr("Home"), current == "home", onHome) { Icon(if (current == "home") AppIcons.HomeFilled else AppIcons.Home, null, tint = it, modifier = Modifier.size(30.dp).motionHome().homeBounce()) }
            BarItem(tr("Messages"), current == "messages", onMessages, dot = messagesDot) { ChatIcon(it, 30.dp, filled = current == "messages") }
            // Add wish: only a + in the accent colour, no words. Its own wider slot (owner, 2.126: more breathing space,
            // "Price watch" seemed closer to the + than "Messages"): the same room on both sides.
            Box(Modifier.width(78.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(if (look.design == "quiet") 52.dp else 50.dp).then(
                    // Quiet 2.207: a small neutral shadow, no coloured glow.
                    if (look.design == "quiet") Modifier.shadow(4.dp, CircleShape, clip = false)
                        .clip(CircleShape).background(look.accent)
                    else Modifier.lookSurface(look, look.fabShape, look.accent, depth = 5.dp)
                ).clickable(onClickLabel = tr("Add wish"), onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Add, tr("Add wish"), tint = look.onAccent, modifier = Modifier.size(30.dp))
            }
            }
            BarItem(tr("My shops"), current == "discover", onDiscover) { Icon(if (current == "discover") AppIcons.StoreFilled else AppIcons.Store, null, tint = it, modifier = Modifier.size(30.dp)) }
            BarItem(tr("Friends"), current == "friends", onFriends, dot = friendsDot) { Icon(if (current == "friends") AppIcons.FriendsFilled else AppIcons.Friends, null, tint = it, modifier = Modifier.size(30.dp)) }
    }
}

/**
 * The floating bar at the bottom (2.97, the preview the owner chose): rounded, a little above the bottom edge, in the
 * style's surface. The app's bar and, while selecting wishes, the action bar (2.209) share it, so both look the same.
 */
@Composable
fun FloatingBar(content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    // Quiet: nearly opaque with a hairline and a soft shadow, like frosted glass.
    val shape = when (look.design) {
        "chaos" -> RoundedCornerShape(20.dp)
        else -> RoundedCornerShape(32.dp)
    }
    val frame = if (look.design == "quiet") Modifier
        .shadow(12.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.18f))
        // Quiet 2.207: white (dark: the lifted grey of what floats), a round pill.
        .clip(shape).background(if (look.dark) look.raised.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)).border(1.dp, look.line, shape)
    else Modifier.lookSurface(look, shape, cs.surfaceVariant, depth = 7.dp)
    Box(
        Modifier.fillMaxWidth().background(cs.background).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
            .then(if (look.design == "chaos") Modifier.rotate(-0.8f) else Modifier)
    ) {
        Row(
            frame.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), // 72 with the words (2.121–2.148)
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically, content = content
        )
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

/**
 * The bottom bar's + outside a list (owner, 16 November 2026: Home has no "Create list" tiles any more): Add a wish
 * first, then a new wishlist, collab wishlist, wishlist group or gift exchange, each with one line that says what it is.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlusMenuSheet(onWish: () -> Unit, onNew: (String) -> Unit, onClose: () -> Unit, onImport: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetGroup(listOf {
                SheetRow({ Icon(AppIcons.Add, null, tint = cs.onSurfaceVariant) }, tr("Add a wish"), onWish, detail = tr("From a link, a photo or just a name."))
            })
            val more = mutableListOf<@Composable () -> Unit>(
                { SheetRow({ Icon(AppIcons.ListIcon, null, tint = cs.onSurfaceVariant) }, tr("New wishlist"), { onNew("mine") }, detail = tr("Your own wishes, or a co-op wishlist with others.")) }
            )
            if (Config.enabled) {
                more += { SheetRow({ Icon(AppIcons.Group, null, tint = cs.onSurfaceVariant) }, tr("New wishlist group"), { onNew("group") }, detail = tr("Everyone you invite can see each other’s wishlists.")) }
                more += { SheetRow({ Icon(AppIcons.Exchange, null, tint = cs.onSurfaceVariant) }, tr("New gift exchange"), { onNew("exchange") }, detail = tr("Link your wishlist, draw names and surprise each other with a thoughtful gift.")) }
            }
            // Import last, under gift exchange (owner, 18 November 2026): a list from another app or your notes.
            more += { SheetRow({ Icon(AppIcons.Swap, null, tint = cs.onSurfaceVariant) }, tr("Import a list"), onImport, detail = tr("From GoWish, Giftful, Amazon, your notes and more.")) }
            SheetGroup(more)
        }
    }
}
