package com.wishlist.app

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

// The app's frame (ROADMAP Stage 1): the routes (Nav.kt), the bottom bar, the places opened from outside
// (Deep.kt) and the app-wide pop-ups. Moved out of MainActivity.kt in 2.163.

/** The back arrow at the left of the top bar of a screen opened on top of another (nothing on the tabs). */
@Composable
fun MenuSlot(back: Boolean = true) {
    // No side menu any more (owner, 7 November 2026): screens reached from the profile sheet get a back arrow (the
    // phone's Back does the same); the tabs (Home, Messages, My shops, Friends) start with their title.
    if (!back) return
    val d = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(onClick = { d?.onBackPressed() }) { Icon(AppIcons.ArrowBack, tr("Back")) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App(deep: Deep?, onDeepUsed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    // Every screen is a route (ROADMAP Stage 1, Nav.kt); "main" is Home.
    val nav = rememberNavController()
    // Where you are inside the tabs (the open chat, a friend's page, an exchange, Home's page), kept with the screen.
    val places = rememberSaveable(saver = Places.Saver) { Places() }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val onMain = route == null || route == Route.MAIN
    var adding by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }
    // What "Find a gift" already read from the shop page on screen (2.114), so the form needn't load it again.
    var prefillMeta by remember { mutableStateOf<Meta?>(null) }
    var fromShop by remember { mutableStateOf(false) }
    // "What did you get?" for one of your lists (2.128). (Received gifts itself is a route, Nav.kt.)
    var receivedAsk by remember { mutableStateOf<String?>(null) }
    // "New gift exchange" (2.129): from Home and from Gift exchanges, so app-wide.
    var newExchange by remember { mutableStateOf(false) }

    /** Closes every screen above Home (a list, a tab, a section, a screen on top): Home shows. */
    fun closeAll() {
        nav.backToMain()
    }
    // "Add wish" from the bottom bar: the form with "Save to", starting at the list on screen (or the last one used).
    var addTarget by remember { mutableStateOf<String?>(null) }
    // null: closed, "": open and empty, otherwise opened with an invite that was sent to the app.
    var joinDlg by remember { mutableStateOf<String?>(null) }
    var inviteDlg by remember { mutableStateOf(false) }
    // The background question comes once, after the notifications question has been answered (or wasn't needed).
    var askBackground by remember { mutableStateOf(false) }
    // Pop-ups opened from anywhere through [go] (they lived in GroupNav, ExchangeNav, GiftInviteNav and HistoryNav
    // until 2.192). Kept with the screen, so they come back after Android closed the app in the background.
    var history by rememberSaveable { mutableStateOf<Long?>(null) }
    var account by rememberSaveable { mutableStateOf(false) }
    var shareCard by rememberSaveable { mutableStateOf<String?>(null) }
    var giftInvite by rememberSaveable { mutableStateOf<String?>(null) }
    var joinGroupLink by rememberSaveable { mutableStateOf<String?>(null) }
    var joinGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var takeover by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var joinExchangeLink by rememberSaveable { mutableStateOf<String?>(null) }
    var joinExchangeId by rememberSaveable { mutableStateOf<String?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askBackground = true }

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
                else scope.launch { snack.showToast(tr("That collab wishlist isn’t on this phone any more.")) }
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

    // The welcome comes first on the very first start (Welcome.kt); the permission questions after it.
    var welcome by remember { mutableStateOf(Welcome.needed(ctx)) }
    LaunchedEffect(welcome) {
        if (welcome) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else askBackground = true
    }
    /**
     * Opens a place the app was asked to open from outside: a tapped notification, a link or something shared into
     * the app (Deep.kt, ROADMAP S1.3). One place, with the routes; what was open before closes (as before), except
     * the Notifications page and Price changes, which open on top so Back returns there.
     */
    fun go(d: Deep) {
        when (d) {
            is Deep.Note -> { if (d.ask != null) places.askFocus = d.ask; openNote(d.key) }
            Deep.Account -> account = true
            is Deep.ShareCard -> shareCard = d.listId
            Deep.Notes -> nav.openOnTop(Route.NOTES)
            Deep.Prices -> nav.openOnTop(Route.PRICES)
            // The price page is a pop-up over whatever is on screen.
            is Deep.History -> history = d.wishId
            is Deep.Chat -> {
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
        }
    }
    LaunchedEffect(deep) {
        deep?.let { go(it); onDeepUsed() }
    }

    // (Back from a list, a tab or a section goes Home by itself: Nav.kt and the routes below.)
    // Screens ask for another screen through LocalGo (Deep.kt): one stable function that always calls the newest [go].
    val goNow = rememberUpdatedState<(Deep) -> Unit> { go(it) }
    val goStable = remember { { d: Deep -> goNow.value(d) } }

    // Friend requests waiting for you show as a red dot on Friends from the start.
    LaunchedEffect(Unit) {
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

    // The bottom bar's tabs (Messages, My shops, Friends) are routes over "main" (Home or a list); Home is "main".
    val tab = route?.takeIf { it in Route.tabs }
    val section = route?.takeIf { it in Route.sections }
    /** Home, as the bar's Home button and Back on a tab's first page (owner, 8 November 2026). */
    fun goHome() { places.homePage = null; closeAll() }
    // While the keyboard is open the bottom bar steps aside and the screen above it makes room (edge to edge, 2.102).
    val imeOpen = WindowInsets.isImeVisible
    // The bar shows on Home, a list and the tabs; an open conversation has a back arrow and no bar.
    val inChat = tab == Route.MESSAGES && places.chat != null
    val showBar = (onMain || tab != null || section != null) && !inChat && !imeOpen
    CompositionLocalProvider(LocalGo provides goStable, LocalPlaces provides places) {
    Column(Modifier.fillMaxSize()) {
    // With the bottom bar, the screens above it don't leave room for the phone's own navigation bar again.
    Box(Modifier.weight(1f).imePadding().then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
    // Every screen is a route (ROADMAP Stage 1, Nav.kt): Home at the bottom, then a list, a tab or a section, then
    // perhaps one screen on top. The pop-ups below show over every route.
    NavHost(
        nav, startDestination = Route.MAIN,
        // Screens move like Signal's (owner, 12 November 2026; Motion.kt): deeper slides in from the right, back slides
        // out, the bar's tabs fade through. Instant with the phone's "Remove animations".
        enterTransition = { Motion.enter(Motion.routeKind(initialState.destination.route, targetState.destination.route)) },
        exitTransition = { Motion.exit(Motion.routeKind(initialState.destination.route, targetState.destination.route)) },
        popEnterTransition = { Motion.enter(Motion.routeKind(initialState.destination.route, targetState.destination.route)) },
        popExitTransition = { Motion.exit(Motion.routeKind(initialState.destination.route, targetState.destination.route)) }
    ) {
        // Home (Hub.kt), the start screen.
        composable(Route.MAIN) {
            HubScreen(
                snack,
                onOpenMine = { openMine(it) },
                onOpenGroup = { openGroup(it) },
                // A new collab wishlist: it appears on Home and its invitation comes up so you can ask people in.
                onCollabCreated = { code -> Shared.open(ctx, code); closeAll(); inviteDlg = true },
                onProfile = { nav.openOnTop(Route.PROFILE) },
                onAddTo = { t -> addTarget = t; prefill = ""; adding = true },
                onOpenWishGroup = { openWishGroup(it) },
                onFriends = { places.resetFriends(); nav.openTab(Route.FRIENDS) },
                onReserved = { nav.openOnTop(Route.RESERVED) },
                onAddWish = { addTarget = Repo.lastSaveTo; prefill = ""; adding = true },
                onNewExchange = { newExchange = true }
            )
        }
        // The open wishlist: one of yours (Repo.openMine) or a collab wishlist (Shared.active). Back (or its ←) goes
        // Home; inside it, search and selecting go back first (their own Back handlers).
        composable(Route.LIST) { entry ->
            val listKey = entry.arguments?.getString("key").orEmpty()
            val here = remember(listKey) { openListOf(ctx, listKey) }
            if (!here) LaunchedEffect(listKey) { goHome() }
            else key(Shared.active, Repo.openMine) {
                HomeScreen(
                    snack,
                    onAdd = { prefill = ""; adding = true },
                    onInvite = { inviteDlg = true },
                    onListSettings = { nav.openOnTop(Route.LIST_SETTINGS) },
                    onBack = { goHome() }
                )
            }
        }
        // The tabs. Each remembers where you were (Places.kt); Back on its first page goes Home (the screen's
        // own Back handlers, composed after this one, go back inside the tab first).
        composable(Route.MESSAGES) {
            BackHandler { goHome() }
            MessagesScreen(snack, onProfile = { nav.openOnTop(Route.PROFILE) })
        }
        composable(Route.SHOPS) {
            BackHandler { goHome() }
            DiscoverScreen(
                snack, onProfile = { nav.openOnTop(Route.PROFILE) },
                onFound = { u, m -> addTarget = null; prefillMeta = m; fromShop = true; prefill = u; adding = true }
            )
        }
        // A wishlist group (2.108) and Gift exchanges (2.129), opened from Home, a link, an invitation or a notification.
        // Back (inside an exchange: first back to the list of exchanges) returns Home.
        composable(Route.GROUP) { entry ->
            val gid = entry.arguments?.getString("id").orEmpty()
            key(gid) { GroupScreen(gid, snack, onBack = { nav.popBackStack() }, onOpenMine = { openMine(it) }) }
        }
        composable(Route.EXCHANGES) {
            ExchangesScreen(snack, onOpenMine = { openMine(it) }, onNew = { newExchange = true })
        }
        composable(Route.FRIENDS) {
            BackHandler { goHome() }
            FriendsScreen(snack, onProfile = { nav.openOnTop(Route.PROFILE) }, onOpenLink = { nav.openOnTop(Route.friend(it)) })
        }
        composable(Route.PROFILE) { RouteBox { ProfileScreen(snack) } }
        composable(Route.SETTINGS) { RouteBox { SettingsScreen(snack) } }
        composable(Route.BIN) { RouteBox { BinScreen(snack) } }
        composable(Route.NOTES) { RouteBox { NotificationsScreen(snack) { key -> openNote(key) } } }
        composable(Route.RECEIVED) { RouteBox { ReceivedScreen { nav.popBackStack() } } }
        // "Gifts I reserved" (2.129): tapping a list opens it where it lives (a friend, a group, a gift exchange, a
        // collab wishlist).
        composable(Route.RESERVED) {
            RouteBox {
                ReservedScreen(onClose = { nav.popBackStack() }) { g ->
                    nav.backToMain()
                    if (!g.gift) openListByCode(g.code)
                    else if (Shared.lists.any { it.code == g.code }) openGroup(g.code)
                }
            }
        }
        // A list opened from its link or code (Friends > a link, a shared link, a notification). Removing it from this
        // phone (its 🗑) closes it.
        composable(Route.FRIEND) { entry ->
            val code = entry.arguments?.getString("code").orEmpty()
            val fr = Repo.friends.firstOrNull { it.code == code }
            if (fr == null) LaunchedEffect(code) { nav.popBackStack() }
            else RouteBox { key(code) { FriendScreen(fr, snack, onRemoved = { nav.popBackStack() }) } }
        }
        composable(Route.PRICES) { RouteBox { PricesScreen(snack, onBack = { nav.popBackStack() }) } }
        // The list open on the main route underneath (Shared.active or Repo.openMine). After Android closed the app in
        // the background only this screen is rebuilt, not the list under it, so that list is opened again from its
        // route first (ROADMAP R9, 2.183): otherwise the settings of another list would show.
        composable(Route.LIST_SETTINGS) {
            val under = nav.previousBackStackEntry?.takeIf { it.destination.route == Route.LIST }?.arguments?.getString("key")
            remember(under) { if (under != null) openListOf(ctx, under) }
            RouteBox {
                key(Shared.active, Repo.openMine) {
                    ListSettingsScreen(snack, onDeleted = { nav.backToMain() }) { nav.popBackStack() }
                }
            }
        }
    }

    }
    // The bar slides away under a chat or a screen on top, and comes back, with the screens (Motion.kt); for the
    // keyboard it steps aside at once, as before.
    AnimatedVisibility(
        visible = showBar,
        enter = if (imeOpen) EnterTransition.None else fadeIn(tween(Motion.MS)) + expandVertically(tween(Motion.MS)),
        exit = if (imeOpen) ExitTransition.None else fadeOut(tween(Motion.MS / 2)) + shrinkVertically(tween(Motion.MS))
    ) {
        AppBottomBar(
            current = when {
                tab == Route.SHOPS -> "discover"; tab == Route.FRIENDS -> "friends"; tab == Route.MESSAGES -> "messages"
                onMain || (section != null && section != Route.LIST) -> "home"; else -> ""
            },
            onHome = { goHome() },
            // "Messages" (owner, 2.98) took the place of "Wishlists"; your wishlists are on Home and its number.
            onMessages = {
                // Tapping a tab again goes back to its first page; otherwise it opens where you left it.
                if (tab == Route.MESSAGES) places.chat = null
                nav.openTab(Route.MESSAGES)
            },
            onAdd = {
                val onAList = route == Route.LIST
                addTarget = if (onAList) (Shared.active ?: MyLists.saveKey(Repo.openMine, false)) else Repo.lastSaveTo
                prefill = ""; adding = true
            },
            onDiscover = { nav.openTab(Route.SHOPS) },
            onFriends = {
                if (tab == Route.FRIENDS) places.resetFriends()
                nav.openTab(Route.FRIENDS)
            },
            friendsDot = Friends.waiting > 0,
            messagesDot = Chat.unreadTotal > 0
        )
    }
    }

    if (adding) key(prefill) {
        // Every new wish can choose its wishlist (owner, 7 November 2026): inside a list that list is picked; a link
        // shared into the app starts on the list used last.
        val here = if (prefill.isEmpty() && !fromShop) (Shared.active ?: MyLists.saveKey(Repo.openMine, false)) else null
        WishDialog(null, prefill, chooseList = true, startTarget = addTarget ?: here, initialMeta = prefillMeta, fromShop = fromShop) {
            prefillMeta = null; fromShop = false
            adding = false; addTarget = null
        }
    }
    // Joining asks for your name first when it's missing (owner, 2.135).
    val needName = (joinDlg != null || joinGroupLink != null || joinGroupId != null || joinExchangeLink != null || joinExchangeId != null) && !NameGate.hasName()
    LaunchedEffect(needName) { if (needName) NameGate.ensure {} }
    if (account) AccountSheet(
        onClose = { account = false },
        // Back from these returns to the screen the sheet was opened on (ROADMAP Stage 1).
        onProfile = { account = false; nav.openOnTop(Route.PROFILE) },
        onReserved = { account = false; nav.openOnTop(Route.RESERVED) },
        onReceived = { account = false; nav.openOnTop(Route.RECEIVED) },
        onBin = { account = false; nav.openOnTop(Route.BIN) },
        onSettings = { account = false; nav.openOnTop(Route.SETTINGS) }
    )
    if (newExchange) NewExchangeDialog(
        onCreated = { gx -> newExchange = false; openExchange(gx, invite = true) },
        onClose = { newExchange = false }
    )
    if (!needName && (joinExchangeLink != null || joinExchangeId != null)) key(joinExchangeLink, joinExchangeId) {
        JoinExchangeDialog(
            joinExchangeLink, joinExchangeId,
            onJoined = { gx -> joinExchangeLink = null; joinExchangeId = null; openExchange(gx) },
            onClose = { joinExchangeLink = null; joinExchangeId = null }
        )
    }
    receivedAsk?.let { id -> ReceivedDialog(id) { receivedAsk = null } }
    // "How many will you buy?" for a 2× wish on a gift list (2.128).
    Shared.askCount?.let { w ->
        val code = Shared.active.orEmpty()
        ReserveCountDialog(
            ResCounts.free(code, w), ResCounts.of(code, w.id)?.my ?: 1,
            onPick = { n -> Shared.askCount = null; Shared.reserveCount(w, n) },
            onClose = { Shared.askCount = null }
        )
    }
    if (!needName) joinDlg?.let { inviteKey ->
        JoinDialog(
            key = inviteKey,
            onJoined = { code ->
                joinDlg = null
                openGroup(code)
            },
            onClose = { joinDlg = null }
        )
    }
    if (inviteDlg) SharedShareDialog { inviteDlg = false }
    // A gift list invitation from a Friend (2.142): a tapped notification or chat card.
    giftInvite?.let { code ->
        key(code) {
            GiftInviteDialog(
                code,
                onJoined = { c -> giftInvite = null; Notifications.markRead(ctx, "gl_invite", "i:$c", 0L); openGroup(c) },
                onClose = { giftInvite = null }
            )
        }
    }
    if (!needName && (joinGroupLink != null || joinGroupId != null)) key(joinGroupLink, joinGroupId) {
        JoinGroupDialog(
            joinGroupLink, joinGroupId,
            onJoined = { gid -> joinGroupLink = null; joinGroupId = null; openWishGroup(gid) },
            onClose = { joinGroupLink = null; joinGroupId = null }
        )
    }
    takeover?.let { me ->
        TakeoverDialog(
            me, snack,
            onDone = { id ->
                takeover = null
                if (id != null) openMine(id)
            },
            onClose = { takeover = null }
        )
    }
    if (welcome) WelcomeFlow { welcome = false }
    // "Please try again in a moment" when the server's limits against abuse were hit (ServerNotice, ROADMAP S4.2).
    ServerNotice.text?.let { msg ->
        LaunchedEffect(msg) {
            ServerNotice.text = null
            snack.showToast(msg)
        }
    }
    // The newest and the oldest working version (AppInfo.kt, ROADMAP R6): asked at most twice a day.
    LaunchedEffect(Unit) { AppInfo.load(ctx); AppInfo.refresh(ctx) }
    // A version that no longer works with the server: only the update is offered, over everything else.
    if (AppInfo.tooOld(AppInfo.installed(ctx), AppInfo.info)) TooOldScreen()
    // The lists couldn't be opened and what is in memory is an old copy (Rescue.kt, ROADMAP R1): say so, with ways out.
    if (ListStore.stale) RescueScreen()
    // After a restore: the new recovery key to save (Restore.kt, 2.188).
    var newKey by remember { mutableStateOf(ctx.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).getBoolean("rk_new", false)) }
    if (newKey && !welcome) NewKeyDialog {
        ctx.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().remove("rk_new").apply()
        newKey = false
    }
    // Signed out because the person restored on another phone: only this, over everything (Restore.kt, 2.188).
    if (Moved.away) MovedScreen()
    // Everyone has a name (owner, 2.135): asked once at the start when it's missing, and before sharing.
    LaunchedEffect(welcome) { if (!welcome) NameGate.askAtStart() }
    NameGate.Host()
    DeleteHost(snack)
    MoveHost(snack)
    PrivateHost(snack)
    OwnedHost(snack)
    if (askBackground) BackgroundQuestion()
    // The share card of one of your lists (owner, 7 November 2026; HomeShare.kt).
    shareCard?.let { id -> key(id) { ListShareDialog(id) { shareCard = null } } }
    // A "Price alert" notification (or its entry in Notifications): that wish's price history, the alert lit up.
    history?.let { id ->
        val w = remember(id, Repo.mineVersion, Repo.myWishes) { Repo.allMine(ctx).flatMap { it.wishes }.firstOrNull { it.id == id } }
        if (w != null) PriceHistoryDialog(w, highlight = true) { history = null }
        else LaunchedEffect(id) { history = null }
    }
    }
}
