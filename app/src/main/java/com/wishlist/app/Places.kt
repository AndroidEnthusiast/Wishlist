package com.wishlist.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where you are inside the tabs and sections (ROADMAP Stage 1, the clean-up step 2, 2.193): the open chat in Messages,
 * a friend's page (and one of their lists) in Friends, the open gift exchange and a page of Home. [App] owns one of
 * these and keeps it with the screen (rememberSaveable), so it comes back by itself after Android closed the app in
 * the background (ROADMAP R9). Until 2.192 this lived in the global objects ChatNav, FriendsNav, the open exchange of
 * ExchangeNav and HubNav, and KeepPlaces.kt copied them in and out. A tab still opens where you left it (owner,
 * 8 November 2026): this holder lives as long as the app's screen, not as long as one tab.
 *
 * Screens read it with `LocalPlaces.current`. Requests to open a place still go through LocalGo (Deep.kt).
 */
@Stable
class Places {
    /** The conversation open in Messages, or null (the list of conversations). */
    var chat by mutableStateOf<String?>(null)

    /** Friends: the friend whose page is open. */
    var person by mutableStateOf<String?>(null)
    /** One of their lists, open on top of their page (kept by its code: turning the phone keeps it open). */
    var friendList by mutableStateOf<FriendList?>(null)
    /** On a friend's page: their "wishes" or "top" page (2.95), or null. */
    var friendPage by mutableStateOf<String?>(null)
    /** The chat the friend's page was opened from (tap their name at the top of a chat): Back returns there (2.105). */
    var fromChat by mutableStateOf<String?>(null)
    /** A friend link that was opened (owner, 2.139): their code and the name in the link. */
    var addCode by mutableStateOf<String?>(null)
    var addName by mutableStateOf("")

    /** The gift exchange open in Gift exchanges, or null (the list of exchanges). */
    var exchange by mutableStateOf<String?>(null)

    /** Which page of Home is open: null (Home itself), "lists", "wishes" or "top". */
    var homePage by mutableStateOf<String?>(null)

    /** A group or exchange just made (its id): its page opens with "Invite friends" once (was GroupNav/ExchangeNav). */
    var inviteNow by mutableStateOf<String?>(null)
    /** "X is free. Reserve it now?" and friends (2.142): the list that opens next opens on that wish (was AskNav). */
    var askFocus by mutableStateOf<AskFocus?>(null)
    /** A wish saved from a shop (2.116): how many so far and the list's name, so the shop says "Added to …" (was ShopNav). */
    var shopSaves by mutableStateOf(0)
    var shopSavedTo by mutableStateOf("")
    fun shopSaved(listName: String) { shopSavedTo = listName; shopSaves++ }

    /** Friends back at its first page. The loaded lists are kept (FriendCache, owner 8 November 2026). */
    fun resetFriends() { person = null; friendList = null; friendPage = null; FriendCache.loadedFor = null; fromChat = null }

    /**
     * Back from a friend's page: to the chat it was opened from, or to Friends. Going back to the chat, their page
     * stays until Messages is on screen and is only then cleared (App, Deep.Chat): clearing it first showed the
     * Friends overview for a moment (owner, 2.107).
     */
    fun leavePerson(go: (Deep) -> Unit) {
        val back = fromChat
        if (back != null) go(Deep.Chat(back)) else resetFriends()
    }

    /** Opens the list with [code] if it belongs to a friend (for example from a "gift no longer on the list" alert). */
    fun openFriendList(code: String): Boolean {
        val f = Friends.state?.friends?.firstOrNull { p -> p.lists.any { it.code == code } } ?: return false
        person = f.id
        friendList = f.lists.first { it.code == code }
        return true
    }

    companion object {
        /** What Android keeps with the screen: the ids only (a friend's list and the loaded lists load again). */
        val Saver = listSaver<Places, String?>(
            save = {
                listOf(it.chat, it.person, it.friendPage, it.fromChat, it.addCode, it.addName, it.exchange, it.homePage, it.friendList?.code)
            },
            restore = { l ->
                Places().apply {
                    if (l.size == 9) {
                        chat = l[0]; person = l[1]; friendPage = if (l[1] != null) l[2] else null
                        fromChat = l[3]; addCode = l[4]; addName = l[5].orEmpty(); exchange = l[6]; homePage = l[7]
                        // The friend's list opens again when their lists are in memory (turning the phone); after
                        // Android closed the app, their page shows (the lists load again), as before 2.193.
                        val code = l[8]
                        if (person != null && code != null) {
                            friendList = Friends.state?.friends?.firstOrNull { it.id == person }?.lists?.firstOrNull { it.code == code }
                        }
                    }
                }
            }
        )
    }
}

/**
 * The lists of the friend whose page is open, as loaded from the server: data, not where you are, so it outlives the
 * screen (turning the phone shows their wishes at once). Was FriendsNav.loaded until 2.192.
 */
object FriendCache {
    /** The friend's lists by code (for the blocks and the numbers). */
    var loaded by mutableStateOf<Map<String, RemoteList>>(emptyMap())
    /** Whose lists [loaded] holds once they finished loading (so a page never says "No wishes" while loading). */
    var loadedFor by mutableStateOf<String?>(null)
}

/** A list ([code]) to open on a wish ([id]); [reserve]: offer "Reserve it now?" when it is free. */
data class AskFocus(val code: String, val id: Long, val reserve: Boolean)

/** The [Places] of the app's screen (provided by [App]). */
val LocalPlaces = staticCompositionLocalOf { Places() }

/**
 * The conversation open in Messages, for the message check (Chat.kt), which runs outside the screens and must not
 * notify about the chat you are reading. Set by [App] from [Places.chat]; the same as ChatNav.peer was until 2.192.
 */
object ChatOnScreen {
    @Volatile
    var peer: String? = null
}
