package com.wishlist.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icons (owner, 15 November 2026, docs/styles/identity.md: Google's filled icons made the app feel like
 * "a standard Android visual experience"). One family, drawn here on the 24 × 24 grid: even lines with rounded ends
 * and corners ("rounded line"); a few have a filled version for the chosen tab. Our own drawings, so the icon rule
 * (Rule 7) is kept. [stroke] is the line width: Quiet 1.8, Gen Z chaos bolder (2.3).
 */
class OwnIcons(private val stroke: Float) {
    private fun icon(name: String, vararg parts: Pair<String, Boolean>): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for ((d, filled) in parts) {
            b.addPath(
                addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }
    private fun line(d: String) = d to false
    private fun solid(d: String) = d to true

    val add by lazy { icon("Add", line("M12,5v14"), line("M5,12h14")) }
    val close by lazy { icon("Close", line("M6.5,6.5l11,11"), line("M17.5,6.5l-11,11")) }
    val check by lazy { icon("Check", line("M5,12.5l4.5,4.5L19,7.5")) }
    val back by lazy { icon("Back", line("M19,12H5"), line("M11,6l-6,6l6,6")) }
    val chevron by lazy { icon("Chevron", line("M9.5,6l6,6l-6,6")) }
    /** A painter's palette with three dots of paint (Theme, 17 November 2026; drawn here). */
    val palette by lazy {
        icon(
            "Palette",
            line("M12,3.5a8.5,8.5 0 1,0 0,17c1,0 1.6,-0.7 1.6,-1.5c0,-0.9 -0.7,-1.3 -0.7,-2.1c0,-0.9 0.7,-1.5 1.6,-1.5h2c2.2,0 4,-1.8 4,-4C20.5,6.9 16.7,3.5 12,3.5z"),
            line("M7.5,12h0.01"), line("M9.5,8h0.01"), line("M14,7.5h0.01")
        )
    }
    val dropDown by lazy { icon("DropDown", line("M7,10l5,5l5,-5")) }
    val bin by lazy {
        icon(
            "Bin", line("M4.5,7h15"), line("M9.5,7V5.5A1.5,1.5 0 0,1 11,4h2a1.5,1.5 0 0,1 1.5,1.5V7"),
            line("M6.5,7l0.8,11.2a2,2 0 0,0 2,1.8h5.4a2,2 0 0,0 2,-1.8L17.5,7"), line("M10.5,11v5"), line("M13.5,11v5")
        )
    }
    val edit by lazy {
        icon("Edit", line("M4.5,19.5l1,-4.5L15.8,4.7a1.8,1.8 0 0,1 2.5,0l1,1a1.8,1.8 0 0,1 0,2.5L9,18.5z"), line("M14,6.5l3.5,3.5"))
    }
    private val heartPath = "M12,19.5c0,0 -7.5,-4.4 -7.5,-9.7A4.3,4.3 0 0,1 12,7.3a4.3,4.3 0 0,1 7.5,2.5c0,5.3 -7.5,9.7 -7.5,9.7z"
    val heart by lazy { icon("Heart", line(heartPath)) }
    val heartFilled by lazy { icon("HeartFilled", solid(heartPath)) }
    private val housePath = "M4.5,10.5L12,4.5l7.5,6V18a2,2 0 0,1 -2,2h-3v-5h-5v5h-3a2,2 0 0,1 -2,-2z"
    val home by lazy { icon("Home", line(housePath)) }
    val homeFilled by lazy { icon("HomeFilled", solid(housePath)) }
    val lock by lazy {
        icon(
            "Lock", line("M6.5,11h11a1.5,1.5 0 0,1 1.5,1.5v6a1.5,1.5 0 0,1 -1.5,1.5h-11A1.5,1.5 0 0,1 5,18.5v-6A1.5,1.5 0 0,1 6.5,11z"),
            line("M8.5,11V8a3.5,3.5 0 0,1 7,0v3"), line("M12,14.5v2")
        )
    }
    val list by lazy {
        icon("List", line("M9.5,7h9.5"), line("M9.5,12h9.5"), line("M9.5,17h9.5"), line("M5,7h0.01"), line("M5,12h0.01"), line("M5,17h0.01"))
    }
    val bell by lazy { icon("Bell", line("M6.5,16.5V11a5.5,5.5 0 0,1 11,0v5.5l1.5,1.5h-14z"), line("M10,20.5a2.2,2.2 0 0,0 4,0")) }
    val more by lazy {
        icon(
            "More", solid("M10.6,5.5a1.4,1.4 0 1,0 2.8,0a1.4,1.4 0 1,0 -2.8,0z"),
            solid("M10.6,12a1.4,1.4 0 1,0 2.8,0a1.4,1.4 0 1,0 -2.8,0z"), solid("M10.6,18.5a1.4,1.4 0 1,0 2.8,0a1.4,1.4 0 1,0 -2.8,0z")
        )
    }
    private val headPath = "M8.5,8a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0z"
    private val bodyPath = "M5,19.2c0.8,-3.2 3.6,-4.9 7,-4.9s6.2,1.7 7,4.9a0.8,0.8 0 0,1 -0.8,0.8H5.8a0.8,0.8 0 0,1 -0.8,-0.8z"
    val person by lazy { icon("Person", line(headPath), line(bodyPath)) }
    val personFilled by lazy { icon("PersonFilled", solid(headPath), solid(bodyPath)) }
    val refresh by lazy { icon("Refresh", line("M19,12a7,7 0 1,1 -2.05,-4.95"), line("M15.5,7.5h3.5V4")) }
    val search by lazy { icon("Search", line("M4.5,10.5a6,6 0 1,0 12,0a6,6 0 1,0 -12,0z"), line("M15,15l4.5,4.5")) }
    val gear by lazy {
        icon(
            "Gear",
            line(
                "M10.27,5.53 L10.73,3.49 L13.27,3.49 L13.73,5.53 L15.35,6.20 L17.12,5.09 L18.91,6.88 L17.80,8.65 L18.47,10.27 " +
                    "L20.51,10.73 L20.51,13.27 L18.47,13.73 L17.80,15.35 L18.91,17.12 L17.12,18.91 L15.35,17.80 L13.73,18.47 " +
                    "L13.27,20.51 L10.73,20.51 L10.27,18.47 L8.65,17.80 L6.88,18.91 L5.09,17.12 L6.20,15.35 L5.53,13.73 " +
                    "L3.49,13.27 L3.49,10.73 L5.53,10.27 L6.20,8.65 L5.09,6.88 L6.88,5.09 L8.65,6.20Z"
            ),
            line("M9.2,12a2.8,2.8 0 1,0 5.6,0a2.8,2.8 0 1,0 -5.6,0z")
        )
    }
    val share by lazy {
        icon(
            "Share", line("M12,14V4"), line("M8.5,7.5L12,4l3.5,3.5"),
            line("M8.5,10.5H7.5A2,2 0 0,0 5.5,12.5v5.5a2,2 0 0,0 2,2h9a2,2 0 0,0 2,-2v-5.5a2,2 0 0,0 -2,-2h-1")
        )
    }
    val group by lazy {
        icon(
            "Group", line("M6.5,9a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z"), line("M3.5,19c0.6,-2.9 3,-4.5 6,-4.5s5.4,1.6 6,4.5"),
            line("M15.5,6.2a2.8,2.8 0 1,1 0,5.6"), line("M17.5,14.6c1.6,0.6 2.6,2 3,4.4")
        )
    }
    // Friends in the bottom bar (owner, 16 November 2026: one person read as "profile"): a person with a second one
    // behind, filled when the tab is open (the one behind stays a line, so it reads as two, not a blob).
    private val friendHead = "M6.3,8.5a3.2,3.2 0 1,0 6.4,0a3.2,3.2 0 1,0 -6.4,0z"
    private val friendBody = "M3,19.2c0.7,-3 3.2,-4.7 6.5,-4.7s5.8,1.7 6.5,4.7a0.8,0.8 0 0,1 -0.8,0.8H3.8a0.8,0.8 0 0,1 -0.8,-0.8z"
    private val friendBackHead = "M15.2,5.5a3,3 0 1,1 0,6"
    private val friendBackBody = "M17.8,14.4c1.8,0.6 3,2.1 3.3,4.5"
    val friends by lazy { icon("Friends", line(friendHead), line(friendBody), line(friendBackHead), line(friendBackBody)) }
    val friendsFilled by lazy { icon("FriendsFilled", solid(friendHead), solid(friendBody), line(friendBackHead), line(friendBackBody)) }
    val gift by lazy {
        icon(
            "Gift", line("M5,11h14v7.5a1.5,1.5 0 0,1 -1.5,1.5h-11A1.5,1.5 0 0,1 5,18.5z"),
            line("M5,7.5h14a1,1 0 0,1 1,1V11H4V8.5a1,1 0 0,1 1,-1z"), line("M12,7.5V20"),
            line("M12,7.5c-1,-3 -4.5,-3.5 -4.5,-1.5c0,1.3 2,1.5 4.5,1.5"), line("M12,7.5c1,-3 4.5,-3.5 4.5,-1.5c0,1.3 -2,1.5 -4.5,1.5")
        )
    }
    val bag by lazy {
        icon("Bag", line("M6,8h12l-0.8,10.4a1.7,1.7 0 0,1 -1.7,1.6H8.5a1.7,1.7 0 0,1 -1.7,-1.6z"), line("M9,10V7a3,3 0 0,1 6,0v3"))
    }
    val swap by lazy {
        icon("Swap", line("M5,9h13"), line("M15,5.5L18.5,9L15,12.5"), line("M19,15H6"), line("M9,11.5L5.5,15L9,18.5"))
    }
    // Move or copy (owner, 21 November 2026): an arrow out of a box, and two overlapping sheets.
    val moveOut by lazy {
        icon("Move", line("M13,5.5H7A2,2 0 0,0 5,7.5v9a2,2 0 0,0 2,2h9a2,2 0 0,0 2,-2V14"), line("M11,13L19.5,4.5"), line("M14.5,4.5h5v5"))
    }
    val copy by lazy {
        icon("Copy", line("M9.5,8.5h8A1.5,1.5 0 0,1 19,10v8.5A1.5,1.5 0 0,1 17.5,20h-8A1.5,1.5 0 0,1 8,18.5V10A1.5,1.5 0 0,1 9.5,8.5z"),
            line("M16,8.5V6A1.5,1.5 0 0,0 14.5,4.5h-8A1.5,1.5 0 0,0 5,6v8.5A1.5,1.5 0 0,0 6.5,16H8"))
    }
    private val awning = "M4.5,9.5L6,5h12l1.5,4.5a2.5,2.5 0 0,1 -5,0a2.5,2.5 0 0,1 -5,0a2.5,2.5 0 0,1 -5,0z"
    private val shopBody = "M6,12.5v6A1.5,1.5 0 0,0 7.5,20h9a1.5,1.5 0 0,0 1.5,-1.5v-6"
    val store by lazy { icon("Store", line(awning), line(shopBody), line("M10,20v-4h4v4")) }
    val storeFilled by lazy {
        // The awning filled, the shop's front still a line with its door: one clear shop, not a blob.
        icon("StoreFilled", solid(awning), line(shopBody), line("M10,20v-4h4v4"))
    }
    private val bubble = "M4.5,7A2.5,2.5 0 0,1 7,4.5h10A2.5,2.5 0 0,1 19.5,7v7a2.5,2.5 0 0,1 -2.5,2.5H11.5l-4,3.5v-3.5H7A2.5,2.5 0 0,1 4.5,14z"
    val chat by lazy { icon("Chat", line(bubble), line("M8.5,9h7"), line("M8.5,12h4.5")) }
    val chatFilled by lazy { icon("ChatFilled", solid(bubble)) }

    private val eyeShape = "M2.5,12c2.2,-4 5.6,-6.5 9.5,-6.5s7.3,2.5 9.5,6.5c-2.2,4 -5.6,6.5 -9.5,6.5S4.7,16 2.5,12z"
    val eye by lazy { icon("Eye", line(eyeShape), line("M9.2,12a2.8,2.8 0 1,0 5.6,0a2.8,2.8 0 1,0 -5.6,0z")) }
    val eyeOff by lazy { icon("EyeOff", line(eyeShape), line("M9.2,12a2.8,2.8 0 1,0 5.6,0a2.8,2.8 0 1,0 -5.6,0z"), line("M4,4l16,16")) }

    val gallery by lazy {
        icon(
            "Gallery", line("M6,4.5h12A2,2 0 0,1 20,6.5v11a2,2 0 0,1 -2,2H6a2,2 0 0,1 -2,-2v-11A2,2 0 0,1 6,4.5z"),
            line("M4.5,16.5l4.5,-4.5l3.5,3.5l2.5,-2.5l4.5,4.5"), line("M14.5,8.5h0.01")
        )
    }

    /** Every icon, for the checks (all drawn, none empty). */
    val all: List<ImageVector> get() = listOf(
        add, close, check, back, chevron, palette, dropDown, bin, edit, heart, heartFilled, home, homeFilled, lock, list, bell, more,
        person, personFilled, friends, friendsFilled, refresh, search, gear, share, group, gift, bag, swap, store, storeFilled, chat, chatFilled, eye, eyeOff, gallery,
        moveOut, copy
    )
}

/** Quiet's icons and Gen Z chaos's bolder ones. */
val QuietIcons = OwnIcons(1.8f)
val ChaosIcons = OwnIcons(2.3f)
