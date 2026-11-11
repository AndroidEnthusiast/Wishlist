package com.wishlist.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Where you were inside a tab or section, kept with the screen (ROADMAP R9, 2.183). Android may close the app while it
 * is in the background and later rebuild only the screen (the routes in Nav.kt come back by themselves, with what they
 * name). What is open *inside* a tab lives in small objects that start empty again then: the open chat (ChatNav), a
 * friend's page (FriendsNav), a gift exchange (ExchangeNav) and a page of Home (HubNav). Without this you came back to
 * the tab's first page. Android keeps these few ids with the screen; they are put back once, only when the object is
 * empty (after a rotation, nothing changed and nothing is put back).
 *
 * New screen state belongs in the route or in rememberSaveable from the start (ROADMAP Stage 5); this file is for the
 * old objects until their screens are rebuilt.
 */
object Places {
    /** The ids to keep: chat, friend, friend's page, exchange, Home's page. */
    fun now(): List<String?> = listOf(ChatNav.peer, FriendsNav.person, FriendsNav.page, ExchangeNav.open, HubNav.page)

    /** Puts back what was kept into the objects that are empty. */
    fun putBack(kept: List<String?>) {
        if (kept.size != 5) return
        if (ChatNav.peer == null) ChatNav.peer = kept[0]
        if (FriendsNav.person == null && kept[1] != null) {
            FriendsNav.person = kept[1]
            if (FriendsNav.page == null) FriendsNav.page = kept[2]
        }
        if (ExchangeNav.open == null) ExchangeNav.open = kept[3]
        if (HubNav.page == null) HubNav.page = kept[4]
    }

    /** As one text for Android to keep ("" for nothing; ids never hold a tab or a line break). */
    fun pack(l: List<String?>): String = l.joinToString("\t") { it.orEmpty().replace("\t", " ").replace("\n", " ") }
    fun unpack(s: String): List<String?> = s.split("\t").map { it.ifEmpty { null } }
}

/**
 * Asked by Android when it keeps the screen: the ids as they are at that moment; given back when the screen is rebuilt
 * from what was kept.
 */
private val placesSaver = Saver<Boolean, String>(
    save = { Places.pack(Places.now()) },
    restore = { Places.putBack(Places.unpack(it)); true }
)

@Composable
fun KeepPlaces() {
    rememberSaveable(saver = placesSaver) { true }
}
