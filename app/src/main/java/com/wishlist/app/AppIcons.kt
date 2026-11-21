package com.wishlist.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The icons of the app (2.210, owner 15 November 2026, docs/styles/identity.md): our own rounded-line family
 * ([OwnIcons]) in every style, bolder in Gen Z chaos. They replaced Google's filled Material icons, which made the app
 * look like Android's own settings. The chosen tab uses the filled versions (HomeFilled …).
 */
object AppIcons {
    private val set: OwnIcons @Composable get() = if (LocalLook.current.design == "chaos") ChaosIcons else QuietIcons

    val Add: ImageVector @Composable get() = set.add
    val ArrowBack: ImageVector @Composable get() = set.back
    val ArrowDropDown: ImageVector @Composable get() = set.dropDown
    val Check: ImageVector @Composable get() = set.check
    val Close: ImageVector @Composable get() = set.close
    val Delete: ImageVector @Composable get() = set.bin
    val Edit: ImageVector @Composable get() = set.edit
    val Favorite: ImageVector @Composable get() = set.heartFilled
    val FavoriteLine: ImageVector @Composable get() = set.heart
    val Home: ImageVector @Composable get() = set.home
    val HomeFilled: ImageVector @Composable get() = set.homeFilled
    val Lock: ImageVector @Composable get() = set.lock
    val ListIcon: ImageVector @Composable get() = set.list
    val Notifications: ImageVector @Composable get() = set.bell
    val MoreVert: ImageVector @Composable get() = set.more
    val Person: ImageVector @Composable get() = set.person
    val PersonFilled: ImageVector @Composable get() = set.personFilled
    val Friends: ImageVector @Composable get() = set.friends
    val FriendsFilled: ImageVector @Composable get() = set.friendsFilled
    val Refresh: ImageVector @Composable get() = set.refresh
    val Search: ImageVector @Composable get() = set.search
    val Settings: ImageVector @Composable get() = set.gear
    val Share: ImageVector @Composable get() = set.share
    /** The buttons on a wish card (⋮, share, bin): the same icons, drawn small and grey by the card (2.207). */
    val CardShare: ImageVector @Composable get() = set.share
    val CardDelete: ImageVector @Composable get() = set.bin
    val Group: ImageVector @Composable get() = set.group
    val Gift: ImageVector @Composable get() = set.gift
    val Bag: ImageVector @Composable get() = set.bag
    val Swap: ImageVector @Composable get() = set.swap
    val Move: ImageVector @Composable get() = set.moveOut
    val Copy: ImageVector @Composable get() = set.copy
    /** Gift exchange in the side menu (owner, 7 November 2026): two gifts with an arrow from each to the other, our own drawing. */
    val Exchange: ImageVector get() = exchangeIcon
    val Store: ImageVector @Composable get() = set.store
    val StoreFilled: ImageVector @Composable get() = set.storeFilled
    val Chevron: ImageVector @Composable get() = set.chevron
    val Palette: ImageVector @Composable get() = set.palette
    val Chat: ImageVector @Composable get() = set.chat
    val ChatFilled: ImageVector @Composable get() = set.chatFilled
    val Gallery: ImageVector @Composable get() = set.gallery
}

private val exchangeIcon: ImageVector by lazy {
    // Line drawing on the 24 × 24 grid of Material icons: a gift bottom left, a gift top right, and two curved arrows
    // swapping them (our own drawing, allowed by the icon rule).
    val b = ImageVector.Builder("Exchange", 24.dp, 24.dp, 24f, 24f)
    fun line(d: String) {
        b.addPath(
            androidx.compose.ui.graphics.vector.addPathNodes(d), fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }
    // The gift bottom left: lid, box, ribbon, bow.
    line("M2,11h9v2.5h-9z"); line("M3,13.5h7v7.5h-7z"); line("M6.5,11v10")
    line("M6.5,11c-1.4,-2.4 -3.4,-1.6 -2.4,0M6.5,11c1.4,-2.4 3.4,-1.6 2.4,0")
    // The gift top right.
    line("M13,4h9v2.5h-9z"); line("M14,6.5h7v6h-7z"); line("M17.5,4v8.5")
    line("M17.5,4c-1.4,-2.4 -3.4,-1.6 -2.4,0M17.5,4c1.4,-2.4 3.4,-1.6 2.4,0")
    // The arrows: up and over to the right, and back under to the left.
    line("M4.5,8.5C4.5,5 6.5,3.5 10,3.5"); line("M8.4,1.9L10,3.5L8.4,5.1")
    line("M19.5,15.5C19.5,19 17.5,20.5 14,20.5"); line("M15.6,18.9L14,20.5L15.6,22.1")
    b.build()
}
