package com.wishlist.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    // Which pop-up is open, and going places (AppState in AppModel.kt).
    val saved = rememberAppSaved()
    val a = remember { AppState(ctx, nav, places, snack, scope, saved) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { a.askBackground = true }

    // The welcome comes first on the very first start (Welcome.kt); the permission questions after it.
    LaunchedEffect(a.welcome) {
        if (a.welcome) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else a.askBackground = true
    }
    // A place the app was asked to open from outside (a notification, a link, something shared): AppState.go.
    LaunchedEffect(deep) {
        deep?.let { a.go(it); onDeepUsed() }
    }

    // (Back from a list, a tab or a section goes Home by itself: Nav.kt and the routes below.)
    // Screens ask for another screen through LocalGo (Deep.kt): one stable function that always calls the newest [go].
    val goNow = rememberUpdatedState<(Deep) -> Unit> { a.go(it) }
    val goStable = remember { { d: Deep -> goNow.value(d) } }

    // The red dots, push, groups, news and upkeep, once at the start (AppState.startUp).
    LaunchedEffect(Unit) { a.startUp() }


    // The bottom bar's tabs (Messages, My shops, Friends) are routes over "main" (Home or a list); Home is "main".
    val tab = route?.takeIf { it in Route.tabs }
    val section = route?.takeIf { it in Route.sections }
    // While the keyboard is open the bottom bar steps aside and the screen above it makes room (edge to edge, 2.102).
    val imeOpen = WindowInsets.isImeVisible
    // The bar shows on Home, a list and the tabs; an open conversation has a back arrow and no bar.
    val inChat = tab == Route.MESSAGES && places.chat != null
    val showBar = (onMain || tab != null || section != null) && !inChat && !imeOpen && !places.selecting
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
                onOpenMine = { a.openMine(it) },
                onOpenGroup = { a.openGroup(it) },
                onProfile = { nav.openOnTop(Route.PROFILE) },
                onAddTo = { t -> a.addWish(t) },
                onOpenWishGroup = { a.openWishGroup(it) },
                onNewExchange = { a.newExchange = true }
            )
        }
        // The open wishlist: one of yours (Repo.openMine) or a collab wishlist (Shared.active). Back (or its ←) goes
        // Home; inside it, search and selecting go back first (their own Back handlers).
        composable(Route.LIST) { entry ->
            val listKey = entry.arguments?.getString("key").orEmpty()
            val here = remember(listKey) { a.openList(listKey) }
            if (!here) LaunchedEffect(listKey) { a.goHome() }
            else key(Shared.active, Repo.openMine) {
                // Wishlist settings as a sheet over the list (owner, 17 November 2026); the route stays for links.
                var settings by rememberSaveable { mutableStateOf(false) }
                HomeScreen(
                    snack,
                    onAdd = { a.prefill = ""; a.adding = true },
                    onInvite = { a.inviteDlg = true },
                    onListSettings = { settings = true },
                    onBack = { a.goHome() }
                )
                if (settings) ListSettingsScreen(snack, onDeleted = { settings = false; nav.backToMain() }, asSheet = true) { settings = false }
            }
        }
        // The tabs. Each remembers where you were (Places.kt); Back on its first page goes Home (the screen's
        // own Back handlers, composed after this one, go back inside the tab first).
        composable(Route.MESSAGES) {
            BackHandler { a.goHome() }
            MessagesScreen(snack, onProfile = { nav.openOnTop(Route.PROFILE) })
        }
        composable(Route.SHOPS) {
            BackHandler { a.goHome() }
            DiscoverScreen(
                snack, onProfile = { nav.openOnTop(Route.PROFILE) },
                onFound = { u, m -> a.addTarget = null; a.prefillMeta = m; a.fromShop = true; a.prefill = u; a.adding = true }
            )
        }
        // A wishlist group (2.108) and Gift exchanges (2.129), opened from Home, a link, an invitation or a notification.
        // Back (inside an exchange: first back to the list of exchanges) returns Home.
        composable(Route.GROUP) { entry ->
            val gid = entry.arguments?.getString("id").orEmpty()
            key(gid) { GroupScreen(gid, snack, onBack = { nav.popBackStack() }, onOpenMine = { a.openMine(it) }) }
        }
        composable(Route.EXCHANGES) {
            ExchangesScreen(snack, onOpenMine = { a.openMine(it) }, onNew = { a.newExchange = true })
        }
        composable(Route.FRIENDS) {
            BackHandler { a.goHome() }
            FriendsScreen(snack, onProfile = { nav.openOnTop(Route.PROFILE) }, onOpenLink = { nav.openOnTop(Route.friend(it)) })
        }
        composable(Route.PROFILE) { RouteBox { ProfileScreen(snack) } }
        composable(Route.SETTINGS) { RouteBox { SettingsScreen(snack) } }
        composable(Route.THEME) { RouteBox { ThemerScreen { nav.popBackStack() } } }
        composable(Route.BIN) { RouteBox { BinScreen(snack) } }
        composable(Route.NOTES) { RouteBox { NotificationsScreen(snack) { key -> a.openNote(key) } } }
        composable(Route.RECEIVED) { RouteBox { ReceivedScreen { nav.popBackStack() } } }
        // "Gifts I reserved" (2.129): tapping a list opens it where it lives (a friend, a group, a gift exchange, a
        // collab wishlist).
        composable(Route.RESERVED) {
            RouteBox {
                ReservedScreen(onClose = { nav.popBackStack() }) { g ->
                    nav.backToMain()
                    if (!g.gift) a.openListByCode(g.code)
                    else if (Shared.lists.any { it.code == g.code }) a.openGroup(g.code)
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
            remember(under) { if (under != null) a.openList(under) }
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
            onHome = { a.goHome() },
            // "Messages" (owner, 2.98) took the place of "Wishlists"; your wishlists are on Home and its number.
            onMessages = {
                // Tapping a tab again goes back to its first page; otherwise it opens where you left it.
                if (tab == Route.MESSAGES) places.chat = null
                nav.openTab(Route.MESSAGES)
            },
            onAdd = {
                val onAList = route == Route.LIST
                // Inside a list: straight to Add a wish for that list (owner, 16 November 2026); elsewhere the menu.
                if (onAList) {
                    a.addWish(Shared.active ?: MyLists.saveKey(Repo.openMine, false))
                } else a.plusMenu = true
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

    AppPopups(a, snack, goStable)
    }
}
