package com.wishlist.app

import android.content.Intent

/**
 * Every place the app can be opened at from outside: a tapped notification, a link (App Links, wishlist://…) or
 * something shared into the app (ROADMAP S1.3). MainActivity turns an Intent into one [Deep]; App opens it in one place
 * ([App]'s `go`), with the routes of Nav.kt. Before 2.162 each kind flipped its own flag in a different object.
 */
sealed interface Deep {
    /** Where a notification happened, as on the Notifications page: "m:", "s:", "c:", "i:", "v:", "r:", "p:", "g:", "q:", "e:". */
    data class Note(val key: String, val ask: AskFocus? = null) : Deep
    /** A bundle of news that lead to different places: the Notifications page. */
    object Notes : Deep
    /** Several price changes of your own wishes: Price changes. */
    object Prices : Deep
    /** One price change (or a target price) of your own wish: its price page. */
    data class History(val wishId: Long) : Deep
    /** A new message: Messages, at that conversation. */
    data class Chat(val peer: String) : Deep
    /** A birthday: that friend's page on Friends ([fromChat]: their name at the top of your chat with them, so Back returns there). */
    data class Person(val id: String, val fromChat: Boolean = false) : Deep
    /** A friend request or an approval: the Friends tab, at its first page. */
    object FriendsTab : Deep
    /** The Friends tab where you left it ("Find friends" in Messages, Gifts I reserved). */
    object FriendsAsLeft : Deep
    /** A friend link (wishlist://friend, …/friend/?f=): Friends, with "Send … a friend request?". */
    data class FriendLink(val code: String, val name: String) : Deep
    /** A list from its friends' link or code (?c=), or "a gift you reserved is no longer on the list". */
    data class FriendList(val code: String) : Deep
    /** A wishlist group notification (the group or its invitation). */
    data class Group(val id: String, val link: String? = null) : Deep
    /** A group link (?g=), from a link or shared into the app. */
    data class GroupLink(val link: String) : Deep
    /** A browser member's "My link" of a group: move their list into the app. */
    data class GroupMe(val me: Triple<String, String, String>) : Deep
    /** A group on this phone, from inside the app: its page ([invite]: just made, "Invite friends" opens). */
    data class GroupPage(val id: String, val invite: Boolean = false) : Deep
    /** A gift exchange notification: that exchange, or (null) the list of exchanges. */
    data class Exchange(val id: String?, val link: String? = null) : Deep
    /** An exchange on this phone, from inside the app ([invite]: just made); null: the list of exchanges. */
    data class ExchangePage(val id: String?, val invite: Boolean = false) : Deep
    /** A collab wishlist invitation from a Friend (a chat card or its notification). */
    data class GiftInvite(val code: String) : Deep
    /** A gift exchange link (?x=). */
    data class ExchangeLink(val link: String) : Deep
    /** "Your reserved gift has been moved to the bin": the bin, at that wish. */
    data class Bin(val code: String, val wishId: Long) : Deep
    /** A collab wishlist invitation (?j=): the Join screen. */
    data class Invite(val key: String) : Deep
    /** The side sheet (your picture at the top right): Profile, Gifts I reserved, Received gifts, Bin, App settings. */
    object Account : Deep
    /** The share card of one of your own lists (HomeShare.kt), by its id. */
    data class ShareCard(val listId: String) : Deep
    /** A shop link shared into the app: the Add wish form with it. */
    data class AddLink(val url: String) : Deep
}

/**
 * Opens a place from anywhere inside the app (a button, a chat card, a pop-up): the same [App] `go` that notifications
 * and links use. Before 2.192 each screen set a flag in its own small object (ChatNav.openTab, GroupNav.request, …)
 * and the app watched those flags; now there is one way to move between screens (ROADMAP Stage 1, the clean-up).
 */
val LocalGo = androidx.compose.runtime.staticCompositionLocalOf<(Deep) -> Unit> { {} }

/**
 * A web link of ours (App Links, owner 7 November 2026): an invite (?j=), a friends' link (?c=), a group (?g=, or a
 * browser member's My link), a gift exchange (?x=) or a friend link (/friend/?f=). Null when it is none of these.
 */
private fun webLink(u: String): Deep? {
    Groups.parseMe(u)?.let { return Deep.GroupMe(it) }
    Groups.parseLink(u)?.let { return Deep.GroupLink(it) }
    Exchanges.parseLink(u)?.let { return Deep.ExchangeLink(it) }
    if (u.contains("/friend", ignoreCase = true)) Friends.parseFriendLink(u)?.let { (c, n) -> return Deep.FriendLink(c, n) }
    if (u.uppercase().contains("J=")) Sync.parseInvite(u)?.let { return Deep.Invite(it) }
    Regex("[?&]c=([A-Za-z0-9-]{8,11})(?![A-Za-z0-9])", RegexOption.IGNORE_CASE).find(u)?.let { m ->
        Sync.parseCode(m.groupValues[1])?.let { return Deep.FriendList(it) }
    }
    return null
}

/**
 * Turns an Intent into a place to open, and takes it off the Intent (handled once: turning the phone must not open it
 * again). Null when it asks for nothing. Separate from MainActivity so the checks can read links on their own.
 */
internal fun deepOf(i: Intent): Deep? {
    fun take(key: String): Boolean = i.hasExtra(key).also { if (it) i.removeExtra(key) }
    fun str(key: String) = i.getStringExtra(key).orEmpty()
    when {
        i.getBooleanExtra(MainActivity.OPEN_NOTES, false) -> { i.removeExtra(MainActivity.OPEN_NOTES); return Deep.Notes }
        i.hasExtra(MainActivity.OPEN_NOTE) -> str(MainActivity.OPEN_NOTE).also { take(MainActivity.OPEN_NOTE) }.let { return Deep.Note(it) }
        i.getBooleanExtra(MainActivity.OPEN_PRICES, false) -> { i.removeExtra(MainActivity.OPEN_PRICES); return Deep.Prices }
        i.hasExtra(MainActivity.HISTORY_ID) -> i.getLongExtra(MainActivity.HISTORY_ID, 0L).also { take(MainActivity.HISTORY_ID) }.let { return Deep.History(it) }
        i.hasExtra(MainActivity.OPEN_CHAT) -> str(MainActivity.OPEN_CHAT).also { take(MainActivity.OPEN_CHAT) }.let { return Deep.Chat(it) }
        i.hasExtra(MainActivity.OPEN_FRIEND) -> str(MainActivity.OPEN_FRIEND).also { take(MainActivity.OPEN_FRIEND) }.let { return Deep.Person(it) }
        i.hasExtra(MainActivity.OPEN_GROUP) -> str(MainActivity.OPEN_GROUP).also { take(MainActivity.OPEN_GROUP) }.let { return if (it.isEmpty()) null else Deep.Group(it) }
        i.hasExtra(MainActivity.OPEN_EXCHANGE) -> str(MainActivity.OPEN_EXCHANGE).also { take(MainActivity.OPEN_EXCHANGE) }.let { return Deep.Exchange(it.ifEmpty { null }) }
        take(MainActivity.OPEN_FRIENDS) -> return Deep.FriendsTab
        i.hasExtra(MainActivity.FRIEND_CODE) -> str(MainActivity.FRIEND_CODE).also { take(MainActivity.FRIEND_CODE) }.let { return Deep.FriendList(it) }
        i.hasExtra(MainActivity.BIN_ID) -> {
            val r = Deep.Bin(str(MainActivity.BIN_CODE), i.getLongExtra(MainActivity.BIN_ID, 0L))
            take(MainActivity.BIN_ID)
            return r
        }
    }
    if (i.action == Intent.ACTION_VIEW) {
        val d = i.dataString.orEmpty()
        // Opened once: a link must not open again when the phone is turned.
        i.action = null
        // App Links (2.142): one of our web links opened the app. A link it can't read just opens the app.
        if (d.startsWith("https://", ignoreCase = true)) return webLink(d)
        // wishlist://group?g=KEY or a browser member's "My link" (2.108), wishlist://exchange?x=KEY (2.129),
        // wishlist://friend?f=CODE&n=Name (2.139), wishlist://join?j=KEY (the invite page's button).
        Groups.parseMe(d)?.let { return Deep.GroupMe(it) }
        Groups.parseLink(d)?.let { return Deep.GroupLink(it) }
        Exchanges.parseLink(d)?.let { return Deep.ExchangeLink(it) }
        if (d.startsWith("wishlist://friend")) Friends.parseFriendLink(d)?.let { (c, n) -> return Deep.FriendLink(c, n) }
        return Sync.parseInvite(d)?.let { Deep.Invite(it) }
    }
    if (i.action == Intent.ACTION_SEND) {
        val t = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        i.action = null
        Groups.parseMe(t)?.let { return Deep.GroupMe(it) }
        Groups.parseLink(t)?.let { return Deep.GroupLink(it) }
        Exchanges.parseLink(t)?.let { return Deep.ExchangeLink(it) }
        if (t.contains("/friend/?f=")) Friends.parseFriendLink(t)?.let { (c, n) -> return Deep.FriendLink(c, n) }
        // An invite link shared into the app (the share sheet) opens the "Join" screen; a friends' link the list;
        // anything else with a shop link the Add wish form.
        val invite = if (t.uppercase().contains("J=")) Sync.parseInvite(t) else null
        if (invite != null) return Deep.Invite(invite)
        Sync.embeddedCode(t)?.let { return Deep.FriendList(it) }
        return cleanUrl(t)?.let { Deep.AddLink(it) }
    }
    return null
}
