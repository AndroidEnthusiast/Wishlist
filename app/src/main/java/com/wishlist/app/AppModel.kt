package com.wishlist.app

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The state holder of the app's frame (the rebuild, owner 19 November 2026; MAP "How a screen is built"): which
 * app-wide pop-up is open, and going places (a list, a tab, a group, an exchange, a friend, a notification, a link:
 * [go]). The frame (AppScreen.kt, AppPopups.kt) only draws. Moved out of AppScreen.kt, which had it since 2.163.
 */

/**
 * The pop-ups that come back after Android closed the app in the background (they lived in GroupNav, ExchangeNav,
 * GiftInviteNav and HistoryNav until 2.192): kept with the screen ([rememberAppSaved]).
 */
internal class AppSaved(
    val history: MutableState<Long?>, val account: MutableState<Boolean>, val shareCard: MutableState<String?>,
    val giftInvite: MutableState<String?>, val joinGroupLink: MutableState<String?>, val joinGroupId: MutableState<String?>,
    val joinExchangeLink: MutableState<String?>, val joinExchangeId: MutableState<String?>
)

@Composable
internal fun rememberAppSaved() = AppSaved(
    rememberSaveable { mutableStateOf<Long?>(null) }, rememberSaveable { mutableStateOf(false) },
    rememberSaveable { mutableStateOf<String?>(null) }, rememberSaveable { mutableStateOf<String?>(null) },
    rememberSaveable { mutableStateOf<String?>(null) }, rememberSaveable { mutableStateOf<String?>(null) },
    rememberSaveable { mutableStateOf<String?>(null) }, rememberSaveable { mutableStateOf<String?>(null) }
)

@Stable
internal class AppState(
    private val ctx: Context, val nav: NavHostController, val places: Places, private val snack: SnackbarHostState,
    private val scope: CoroutineScope, saved: AppSaved
) {
    var adding by mutableStateOf(false)
    var prefill by mutableStateOf("")
    // What "Find a gift" already read from the shop page on screen (2.114), so the form needn't load it again.
    var prefillMeta by mutableStateOf<Meta?>(null)
    var fromShop by mutableStateOf(false)
    // "What did you get?" for one of your lists (2.128). (Received gifts itself is a route, Nav.kt.)
    var receivedAsk by mutableStateOf<String?>(null)
    // "New gift exchange" (2.129): from Home and from Gift exchanges, so app-wide.
    var newExchange by mutableStateOf(false)
    // The bottom bar's + outside a list (owner, 16 November 2026): Add a wish, or a new list, group or exchange.
    var plusMenu by mutableStateOf(false)
    // The Import sheet (owner, 18 November 2026): null = closed, else what to start with (a shared link or text).
    var importing by mutableStateOf<String?>(null)
    // Screenshot to wish (owner, 20 November 2026): pictures shared into the app, being turned into wishes (ShotsFlow.kt).
    var shots by mutableStateOf<Deep.Shots?>(null)
    var plusNew by mutableStateOf<String?>(null)
    // "Add wish" from the bottom bar: the form with "Save to", starting at the list on screen (or the last one used).
    var addTarget by mutableStateOf<String?>(null)
    // null: closed, "": open and empty, otherwise opened with an invite that was sent to the app.
    var joinDlg by mutableStateOf<String?>(null)
    var inviteDlg by mutableStateOf(false)
    // The background question comes once, after the notifications question has been answered (or wasn't needed).
    var askBackground by mutableStateOf(false)
    var takeover by mutableStateOf<Triple<String, String, String>?>(null)
    // The welcome comes first on the very first start (Welcome.kt); the permission questions after it.
    var welcome by mutableStateOf(Welcome.needed(ctx))
    // Pop-ups opened from anywhere through [go], kept with the screen ([AppSaved]).
    var history by saved.history
    var account by saved.account
    var shareCard by saved.shareCard
    var giftInvite by saved.giftInvite
    var joinGroupLink by saved.joinGroupLink
    var joinGroupId by saved.joinGroupId
    var joinExchangeLink by saved.joinExchangeLink
    var joinExchangeId by saved.joinExchangeId

    /** Closes every screen above Home (a list, a tab, a section, a screen on top): Home shows. */
    fun closeAll() {
        nav.backToMain()
    }

    /** Home, as the bar's Home button and Back on a tab's first page (owner, 8 November 2026). */
    fun goHome() { places.homePage = null; closeAll() }

    /** The add form: for list [target] (null: the list on screen or used last), with a link to read first. */
    fun addWish(target: String?, url: String = "") { addTarget = target; prefill = url; adding = true }

    /** Opens one of your own wishlists. */
    fun openMine(id: String) {
        Shared.close()
        Repo.openMine(ctx, id)
        closeAll(); nav.openTab(Route.list("m:$id"))
    }

    /** Opens a group wishlist. */
    fun openGroup(code: String) {
        Shared.open(ctx, code)
        closeAll(); nav.openTab(Route.list("s:$code"))
    }

    /** A wishlist group's page (2.108); Home puts the group you opened last first in its section (2.110). */
    fun openWishGroup(gid: String, invite: Boolean = false) {
        if (invite) places.inviteNow = gid
        closeAll(); places.homePage = null
        nav.openTab(Route.group(gid))
        Repo.touchRecent(ctx, "g:$gid")
    }

    /** A group invitation (a link, a chat card, a notification): its page when it is on this phone, else Join. */
    fun groupInvite(gid: String?, link: String?) {
        if (gid != null && Groups.group(gid) != null) { openWishGroup(gid); return }
        joinGroupId = gid?.takeIf { it.isNotEmpty() }
        joinGroupLink = link
    }

    /** Gift exchanges (2.129): one exchange ([gx]), or the list of them (null). */
    fun openExchange(gx: String?, invite: Boolean = false) {
        if (invite && gx != null) places.inviteNow = gx
        places.exchange = gx
        closeAll(); places.homePage = null
        nav.openTab(Route.EXCHANGES)
    }

    /** An exchange invitation: the exchange when it is on this phone, otherwise Join (or the list, with neither). */
    fun exchangeInvite(gx: String?, link: String?) {
        if (gx != null && Exchanges.get(gx) != null) { openExchange(gx); return }
        joinExchangeId = gx?.takeIf { it.isNotEmpty() }
        joinExchangeLink = link
        openExchange(null)
    }

    fun openFriend(code: String) {
        // A link to one of your own wishlists opens it (owner, 7 November 2026: App Links open the app).
        Repo.allMine(ctx).firstOrNull { it.code == code }?.let { openMine(it.id); return }
        if (code == Repo.shareCode) {
            scope.launch { snack.showToast(tr("That’s your own list. Friends reserve from it, and you can’t peek.")) }
            return
        }
        // A list of one of your friends (Friends tab) opens there.
        if (places.openFriendList(code)) {
            closeAll()
            nav.openTab(Route.FRIENDS)
            return
        }
        if (Repo.friends.any { it.code == code }) {
            closeAll(); nav.openOnTop(Route.friend(code))
            return
        }
        scope.launch {
            try {
                val r = Cloud.fetchList(code)
                if (r == null) {
                    snack.showToast(tr("No list found for that code."))
                } else {
                    Repo.addFriend(ctx, Friend(code, r.name))
                    closeAll(); nav.openOnTop(Route.friend(code))
                }
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t reach the server. Check your connection."))
            }
        }
    }

    /**
     * Before a notification opens its wish (owner, 20 November 2026: no dead ends): false when it is gone (said) or in
     * the bin (said, with Restore); a wish moved to another of your lists opens there.
     */
    private fun stillThere(key: String, wishId: Long?, kind: String = "price"): Boolean {
        if (wishId == null || wishId <= 0L) return true
        if (kind == "price" && !key.startsWith("m:") && !key.startsWith("s:")) return true
        return when (val w = NoteCheck.where(ctx, kind, key, wishId)) {
            NoteWhere.Open -> true
            is NoteWhere.Moved -> if (kind == "target") true else { openMine(w.listId); false }
            is NoteWhere.Gone -> { WishFocus.target = null; scope.launch { snack.showToast(w.message) }; false }
            is NoteWhere.InBin -> {
                WishFocus.target = null
                scope.launch {
                    if (snack.showToast(tr("This wish is in the bin"), tr("Restore")) == androidx.compose.material3.SnackbarResult.ActionPerformed &&
                        Repo.restoreMine(ctx, w.wishId)) snack.showToast(tr("Restored"))
                }
                false
            }
        }
    }

    /**
     * A wish card in a chat (owner, 20 November 2026): the wish in its list, glowing. Your own list opens on it; someone
     * else's list (a friend's, a group member's) opens and then finds it (Friend.kt says so when it is gone).
     */
    fun openWishIn(code: String, id: Long) {
        val own = Repo.allMine(ctx).firstOrNull { it.code == code }
        if (own != null) {
            if (own.wishes.any { it.id == id && it.bin == 0L }) WishFocus.target = id
            else scope.launch { snack.showToast(tr("This wish is no longer on the list")) }
            openMine(own.id)
            return
        }
        // A shared wishlist you're on (owner, 21 November 2026: chat about its wishes): it opens on the wish.
        if (Shared.lists.any { it.code == code }) {
            openGroup(code)
            // Not in the copy on this phone yet (just added): the list opens without the glow, never with a wrong message.
            val kept = Shared.content.wishes.firstOrNull { it.id == id }
            if (kept == null || kept.bin == 0L) WishFocus.target = id
            else scope.launch { snack.showToast(tr("This wish is no longer on the list")) }
            return
        }
        places.askFocus = AskFocus(code, id, false)
        openListByCode(code)
    }

    /**
     * A list someone else owns, from its code (a notification): a group member's list or a giftee's list opens where it
     * lives, otherwise as a friend's list (2.142, as Gifts I reserved does).
     */
    fun openListByCode(code: String) {
        val group = Groups.groups.firstOrNull { gr -> gr.members.any { it.list?.code == code } }
        val exchange = Exchanges.list.firstOrNull { it.me.giftee?.list?.code == code }
        when {
            group != null -> openWishGroup(group.id)
            exchange != null -> openExchange(exchange.id)
            else -> openFriend(code)
        }
    }

    /**
     * Opens where a notification happened (the Notifications page, or a tapped push): the list the wish is on, a
     * friend's list, Friends, that friend, or a group invitation.
     */
    fun openNote(key: String) {
        nav.backToMain()
        when {
            key.startsWith("m:") -> key.removePrefix("m:").takeIf { it in Repo.mineIds }?.let { openMine(it) }
            key.startsWith("s:") -> {
                val code = key.removePrefix("s:")
                if (Shared.lists.any { it.code == code }) openGroup(code)
                else scope.launch { snack.showToast(tr("That wishlist isn’t on this phone any more.")) }
            }
            key.startsWith("c:") -> openListByCode(key.removePrefix("c:"))
            // A gift list invitation (2.142).
            key.startsWith("i:") -> giftInvite = key.removePrefix("i:")
            // A "Still buying …?" question (2.142): answered in Gifts I reserved.
            key == "v:" -> { closeAll(); nav.openOnTop(Route.RESERVED) }
            key.startsWith("r:") -> { places.resetFriends(); nav.openTab(Route.FRIENDS) }
            key.startsWith("p:") -> { places.resetFriends(); places.person = key.removePrefix("p:"); nav.openTab(Route.FRIENDS) }
            key.startsWith("g:") -> groupInvite(key.removePrefix("g:"), null)
            key.startsWith("q:") -> receivedAsk = key.removePrefix("q:")
            // Gift exchange (2.129): "e:<id>", or "e:" for the list of exchanges.
            key.startsWith("e:") -> exchangeInvite(key.removePrefix("e:").ifEmpty { null }, null)
        }
    }

    /**
     * Opens a place the app was asked to open from outside: a tapped notification, a link or something shared into
     * the app (Deep.kt, ROADMAP S1.3). One place, with the routes; what was open before closes (as before), except
     * the Notifications page and Price changes, which open on top so Back returns there.
     */
    fun go(d: Deep) {
        when (d) {
            is Deep.Note -> {
                // A tapped price notification about a wish that is gone or in the bin says so (owner, 20 November 2026).
                if (!stillThere(d.key, WishFocus.target)) return
                if (d.ask != null) places.askFocus = d.ask; openNote(d.key)
            }
            Deep.Account -> account = true
            is Deep.ShareCard -> shareCard = d.listId
            Deep.Notes -> nav.openOnTop(Route.NOTES)
            Deep.Prices -> nav.openOnTop(Route.PRICES)
            // The price page is a pop-up over whatever is on screen.
            is Deep.History -> if (stillThere("", d.wishId, kind = "target")) history = d.wishId
            is Deep.WishIn -> openWishIn(d.code, d.id)
            is Deep.Chat -> {
                places.chatAbout = d.about
                places.chat = d.peer
                closeAll(); nav.openTab(Route.MESSAGES)
                if (places.fromChat != null) places.resetFriends()
            }
            is Deep.Person -> {
                places.resetFriends(); places.person = d.id
                if (d.fromChat) places.fromChat = d.id
                closeAll(); nav.openTab(Route.FRIENDS)
            }
            Deep.FriendsTab -> { places.resetFriends(); closeAll(); nav.openTab(Route.FRIENDS) }
            Deep.FriendsAsLeft -> { closeAll(); nav.openTab(Route.FRIENDS) }
            // "Send … a friend request?" on Friends (owner, 2.139).
            is Deep.FriendLink -> {
                places.resetFriends(); places.addName = d.name; places.addCode = d.code
                closeAll(); nav.openTab(Route.FRIENDS)
            }
            is Deep.FriendList -> openFriend(d.code)
            // Groups and exchanges: their own screen when it is on this phone, otherwise the Join screen.
            is Deep.Group -> groupInvite(d.id, d.link)
            is Deep.GroupLink -> groupInvite(null, d.link)
            is Deep.GroupPage -> openWishGroup(d.id, d.invite)
            is Deep.GroupMe -> takeover = d.me
            is Deep.Exchange -> exchangeInvite(d.id, d.link)
            is Deep.ExchangeLink -> exchangeInvite(null, d.link)
            is Deep.ExchangePage -> openExchange(d.id, d.invite)
            is Deep.GiftInvite -> giftInvite = d.code
            is Deep.Bin -> { Bin.focus = d.code to d.wishId; closeAll(); nav.openOnTop(Route.BIN) }
            is Deep.Invite -> { closeAll(); joinDlg = d.key }
            is Deep.AddLink -> { closeAll(); addTarget = null; prefill = d.url; adding = true }
            is Deep.Import -> { closeAll(); importing = d.text }
            is Deep.Shots -> { closeAll(); adding = false; shots = d }
        }
    }

    /** Opens a list by its route key ("m:…" or "s:…"), false when it isn't on this phone (Nav.kt). */
    fun openList(key: String): Boolean = openListOf(ctx, key)

    /** A new shared wishlist was made: it opens, with its invitation. */
    fun sharedMade(code: String) { plusNew = null; Shared.open(ctx, code); closeAll(); inviteDlg = true }

    /** "How many will you buy?" answered for a 2× wish on a gift list (2.128); null: closed without choosing. */
    fun reserveCount(w: Wish, n: Int?) { Shared.askCount = null; if (n != null) Shared.reserveCount(w, n) }

    /** What runs once when the app starts, in the background: the red dots, push, groups, news, upkeep. */
    suspend fun startUp() {
        // Friend requests waiting for you show as a red dot on Friends from the start.
        Friends.load(ctx)
        if (Config.enabled && Friends.hasProfile(ctx)) try { Friends.refresh(ctx) } catch (e: Exception) {}
        // Unread messages show as a red dot on Messages from the start.
        if (Config.enabled && Friends.hasProfile(ctx)) try { Chat.refresh(ctx) } catch (e: Exception) {}
        // Instant message notifications (Push.kt), when Firebase is set up.
        if (Friends.hasProfile(ctx)) Push.start(ctx)
        // Lists waiting to become "My friends" (new lists, and the one-time switch of 2.105).
        try { ListVisibility.trySwitch(ctx) } catch (e: Exception) {}
        // Wishlist groups and invites (2.108).
        if (Config.enabled && Friends.hasProfile(ctx)) try { Groups.refresh(ctx) } catch (e: Exception) {}
        // News (2.142): gift list invitations and "Still buying …?"; it also links this phone's reservations to you,
        // so a question about a gift you reserved reaches you at once.
        if (Config.enabled && Friends.hasProfile(ctx)) try { News.poll(ctx) } catch (e: Exception) {}
        // Wish names without article numbers and the shop's name, once (owner, 2.135).
        try { Repo.cleanTitlesOnce(ctx) } catch (e: Exception) {}
        // Gift exchanges and their invitations (2.129): the red dot on the menu item.
        Exchanges.load(ctx)
        if (Config.enabled && Friends.hasProfile(ctx)) try { Exchanges.refresh(ctx) } catch (e: Exception) {}
        // Your profile photo moves from the database to a file, once (PeoplePhotos, ROADMAP S4.1, 2.185).
        if (Config.enabled && Friends.hasProfile(ctx)) try { Friends.movePhoto(ctx) } catch (e: Exception) {}
        // "The app was opened", once a day; lists removed online after a year come back (Opened.kt, ROADMAP S4.3).
        try { Opened.run(ctx) } catch (e: Exception) {}
        // The recovery key's locked copy, when something changed (RecoveryKey.kt, 2.187).
        try { RecoveryKey.backup(ctx) } catch (e: Exception) {}
    }
}
