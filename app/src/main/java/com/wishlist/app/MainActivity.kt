package com.wishlist.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.border
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var sharedCode by mutableStateOf<String?>(null)
    private var sharedInvite by mutableStateOf<String?>(null)
    /** A "your reserved gift has been moved to the bin" alert was tapped: the list code and the wish to show in the bin. */
    private var binTarget by mutableStateOf<Pair<String, Long>?>(null)

    companion object {
        const val BIN_CODE = "bin_code"
        const val BIN_ID = "bin_id"
        /** A "gift you reserved is no longer on the list" alert: the friend's list to open. */
        const val FRIEND_CODE = "friend_code"
        /** A "Target price reached" alert: the wish whose price history to open. */
        const val HISTORY_ID = "history_id"
        /** A friend request (or an approval) alert: open the Friends tab. */
        const val OPEN_FRIENDS = "open_friends"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.load(this)
        Friends.load(this)
        handle(intent)
        addOnNewIntentListener { handle(it) }
        Alerts.ensureChannel(this)
        Alerts.schedule(this, Repo.intervalHours, false)
        Alerts.scheduleBin(this)
        Sync.push()
        setContent {
            WishTheme {
                App(sharedUrl, sharedCode, sharedInvite, binTarget, { binTarget = null }) {
                    sharedUrl = null; sharedCode = null; sharedInvite = null
                }
            }
        }
    }

    /**
     * A phone that got the app from an invite page has no link to open: Android doesn't pass anything on through an
     * install. So the page's "Download app" button puts the invitation on the clipboard, and the first time the app
     * starts after an install (or update) it looks for it there and offers to join. It looks only once per install.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboardInvite()
    }

    private fun checkClipboardInvite() {
        try {
            val stamp = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            val p = getSharedPreferences("wishlist", MODE_PRIVATE)
            if (p.getLong("inv_clip_for", 0L) == stamp) return
            p.edit().putLong("inv_clip_for", stamp).apply()
            // An invitation that came in as a link or a shared text wins over the clipboard.
            if (sharedInvite != null) return
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
            val key = Shared.inviteFromClipboard(this, text) ?: return
            // The invitation was only on the clipboard to get here. Take it off again.
            try {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                else cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            } catch (e: Exception) {
            }
            sharedInvite = key
        } catch (e: Exception) {
        }
    }

    private fun handle(i: Intent?) {
        if (i != null && i.hasExtra(HISTORY_ID)) {
            HistoryNav.open = i.getLongExtra(HISTORY_ID, 0L)
            i.removeExtra(HISTORY_ID)
            return
        }
        if (i != null && i.hasExtra(OPEN_FRIENDS)) {
            i.removeExtra(OPEN_FRIENDS)
            FriendsNav.reset()
            FriendsNav.openTab = true
            return
        }
        if (i != null && i.hasExtra(FRIEND_CODE)) {
            sharedCode = i.getStringExtra(FRIEND_CODE)
            i.removeExtra(FRIEND_CODE)
            return
        }
        if (i != null && i.hasExtra(BIN_ID)) {
            binTarget = (i.getStringExtra(BIN_CODE) ?: "") to i.getLongExtra(BIN_ID, 0L)
            // Handled once: turning the phone must not open the bin again.
            i.removeExtra(BIN_ID)
            return
        }
        // An invite link that was opened (wishlist://join?j=KEY), for example from the button on the invite web page.
        if (i?.action == Intent.ACTION_VIEW) {
            i.dataString?.let { Sync.parseInvite(it) }?.let { sharedInvite = it }
            return
        }
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            // An invite link shared into the app (the share sheet) opens the "Join" screen.
            val invite = if (t.uppercase().contains("J=")) Sync.parseInvite(t) else null
            val code = Sync.embeddedCode(t)
            if (invite != null) sharedInvite = invite
            else if (code != null) sharedCode = code
            else cleanUrl(t)?.let { sharedUrl = it }
        }
    }

}

private val Green = Color(0xFF0B7A3E)
/** A price that went up: the opposite of the green of a drop. */
private val Rise = Color(0xFFBA1A1A)

/** True when a price check found a higher price than before (old price lower than the price now). */
fun priceRose(w: Wish): Boolean {
    val p = w.price
    val o = w.oldPrice
    return p != null && o != null && p > o + 0.005
}

private fun host(url: String): String = try {
    java.net.URI(url).host.removePrefix("www.")
} catch (e: Exception) {
    url
}

/** The thin line between groups in the side menu. */
@Composable
private fun DrawerDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Empty space in the top bar where the floating menu button is drawn. */
@Composable
fun MenuSlot() {
    Spacer(Modifier.size(48.dp))
}

/** Three bars that spin and merge into an X while the menu is open. */
@Composable
fun AnimatedMenuIcon(open: Boolean, tint: Color) {
    val p by animateFloatAsState(if (open) 1f else 0f, tween(350, easing = FastOutSlowInEasing), label = "menuIcon")
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun bar(dy: Float, angle: Float, alpha: Float) {
            val y = cy + dy
            rotate(angle, Offset(cx, y)) {
                drawLine(
                    color = tint, start = Offset(cx - 8f * u, y), end = Offset(cx + 8f * u, y),
                    strokeWidth = 2f * u, cap = StrokeCap.Round, alpha = alpha
                )
            }
        }
        rotate(180f * p, Offset(cx, cy)) {
            bar(-6f * u * (1f - p), 45f * p, 1f)
            bar(0f, 0f, 1f - p)
            bar(6f * u * (1f - p), -45f * p, 1f)
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, dot: Boolean = false, onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    if (look.classic) {
        NavigationDrawerItem(
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = selected,
            onClick = onClick,
            icon = { Icon(icon, null) },
            badge = if (dot) ({ RedDot() }) else null,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        return
    }
    // The designs: the chosen item looks like the design's chosen button (filled, with its lines or shadow).
    val shape = look.chipShape
    val frame = if (!selected) Modifier else when (look.design) {
        "neo" -> Modifier.hardShadow(look.line, shape, 3.dp).border(2.dp, look.line, shape)
        "clay" -> Modifier.clay(look, shape, look.accent, 6.dp)
        else -> Modifier
    }
    val on = look.design == "neo" || look.design == "clay"
    NavigationDrawerItem(
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else null)
        },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        badge = if (dot) ({ RedDot() }) else null,
        shape = shape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = if (look.design == "clay") Color.Transparent else if (on) look.accent else cs.primaryContainer,
            selectedTextColor = if (on) look.onAccent else cs.onPrimaryContainer,
            selectedIconColor = if (on) look.onAccent else cs.onPrimaryContainer,
            unselectedTextColor = cs.onSurface, unselectedIconColor = cs.onSurfaceVariant
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).then(frame)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App(
    sharedUrl: String?, sharedCode: String?, sharedInvite: String?,
    binTarget: Pair<String, Long>?, onBinUsed: () -> Unit, onSharedUsed: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var adding by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }
    var catDlg by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    // The bin (menu > Bin): deleted wishes of every list, for 10 days.
    var binOpen by remember { mutableStateOf(false) }
    // The settings of the open list (its ⚙), apart from the app's Settings in the menu.
    var listSettings by remember { mutableStateOf(false) }
    var friendCode by remember { mutableStateOf<String?>(null) }
    var newShared by remember { mutableStateOf(false) }
    // The dashboard (Hub.kt) is the start screen; tapping a list opens it, Back returns to the dashboard.
    var hub by remember { mutableStateOf(true) }
    var newMine by remember { mutableStateOf(false) }
    // The bottom bar's pages and actions (BottomBar.kt).
    var pricesOpen by remember { mutableStateOf(false) }
    // Notifications (menu, above Bin): price drops and rises, with a red dot while there is something new.
    var notesOpen by remember { mutableStateOf(false) }
    var friendsOpen by remember { mutableStateOf(false) }
    // Your profile (menu > Profile, or your picture on Friends): photo, name and friend code (FriendsUi.kt).
    var profileOpen by remember { mutableStateOf(false) }
    var createChoice by remember { mutableStateOf(false) }
    // "Add wish" from the bottom bar: the form with "Save to", starting at the list on screen (or the last one used).
    var addTarget by remember { mutableStateOf<String?>(null) }
    // null: closed, "": open and empty, otherwise opened with an invite that was sent to the app.
    var joinDlg by remember { mutableStateOf<String?>(null) }
    var inviteDlg by remember { mutableStateOf(false) }
    // The background question comes once, after the notifications question has been answered (or wasn't needed).
    var askBackground by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askBackground = true }

    /** Opens one of your own wishlists. */
    fun openMine(id: String) {
        Shared.close()
        Repo.openMine(ctx, id)
        settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = false
    }

    /** Opens a group wishlist. */
    fun openGroup(code: String) {
        Shared.open(ctx, code)
        settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = false
    }

    fun openFriend(code: String) {
        if (code == Repo.shareCode) {
            scope.launch { snack.showToast(tr("That’s your own list. Friends reserve from it, and you can’t peek.")) }
            return
        }
        // A list of one of your friends (Friends tab) opens there.
        if (FriendsNav.openByCode(code)) {
            settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; notesOpen = false; profileOpen = false
            friendsOpen = true
            return
        }
        if (Repo.friends.any { it.code == code }) {
            friendCode = code; settings = false; binOpen = false
            return
        }
        scope.launch {
            try {
                val r = Cloud.fetchList(code)
                if (r == null) {
                    snack.showToast(tr("No list found for that code."))
                } else {
                    Repo.addFriend(ctx, Friend(code, r.name))
                    friendCode = code; settings = false; binOpen = false
                }
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t reach the server. Check your connection."))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else askBackground = true
    }
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            prefill = sharedUrl; adding = true; settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
            onSharedUsed()
        }
    }
    LaunchedEffect(sharedCode) {
        if (sharedCode != null) {
            openFriend(sharedCode)
            onSharedUsed()
        }
    }
    LaunchedEffect(binTarget) {
        if (binTarget != null) {
            Bin.focus = binTarget
            binOpen = true; settings = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
            scope.launch { drawer.close() }
            onBinUsed()
        }
    }
    LaunchedEffect(sharedInvite) {
        if (sharedInvite != null) {
            joinDlg = sharedInvite; settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
            onSharedUsed()
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && (settings || binOpen || friendCode != null)) {
        settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
    }
    // Back from a shared wishlist goes to My wishlist.
    // Back from a list goes to the dashboard; back from Prices or Friends to where you were.
    BackHandler(enabled = !drawer.isOpen && !settings && !binOpen && friendCode == null && !listSettings && !hub && !pricesOpen && !friendsOpen && !notesOpen) {
        hub = true
    }
    BackHandler(enabled = !drawer.isOpen && !settings && !binOpen && friendCode == null && (pricesOpen || friendsOpen || notesOpen)) {
        pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
    }

    // Back from Profile goes to where you were (Friends, when you came from your picture there).
    BackHandler(enabled = !drawer.isOpen && profileOpen) { profileOpen = false }
    // A friend request alert was tapped: the Friends tab.
    LaunchedEffect(FriendsNav.openTab) {
        if (FriendsNav.openTab) {
            FriendsNav.openTab = false
            settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; notesOpen = false; profileOpen = false
            friendsOpen = true
        }
    }
    // Friend requests waiting for you show as a red dot on Friends from the start.
    LaunchedEffect(Unit) {
        Friends.load(ctx)
        if (Config.enabled && Friends.hasProfile(ctx)) try { Friends.refresh(ctx) } catch (e: Exception) {}
    }

    val openMenu: () -> Unit = { scope.launch { drawer.open() } }

    // The side menu takes about 72% of the screen, so a strip of the list stays visible. Tapping that strip closes the menu.
    val cfg = LocalConfiguration.current
    val sheetWidth = (cfg.screenWidthDp * 0.72f).coerceIn(240f, 320f).dp
    val menuOpen = drawer.targetValue == DrawerValue.Open
    val blurRadius by animateDpAsState(if (menuOpen) 10.dp else 0.dp, tween(300), label = "menuBlur")

    Box(Modifier.fillMaxSize()) {
    // A list's settings screen has only its back arrow: no menu button and no swiping the menu open.
    val inListSettings = listSettings && !settings && !binOpen && friendCode == null
    // The Themer (App settings > Theme) has only its back arrow too.
    val inThemer = settings && ThemerNav.open
    // A friend's page or list (Friends tab) has a back arrow instead of the menu button.
    val inFriend = friendsOpen && FriendsNav.deep && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen && !profileOpen
    // A page behind one of the numbers on Home (Overview.kt) has a back arrow too.
    val inOverview = hub && HubNav.page != null && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen &&
        !profileOpen && !pricesOpen && !friendsOpen
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !inListSettings && !inThemer && !inFriend && !inOverview,
        drawerContent = {
            val look = LocalLook.current
            ModalDrawerSheet(
                modifier = Modifier.width(sheetWidth).then(
                    // Neobrutalism: a thick ink line along the edge of the menu.
                    if (look.design == "neo") Modifier.drawWithContent {
                        drawContent()
                        val w = 2.dp.toPx()
                        drawRect(look.line, Offset(size.width - w, 0f), Size(w, size.height))
                    } else Modifier
                ),
                drawerContainerColor = if (look.classic) DrawerDefaults.containerColor else MaterialTheme.colorScheme.surface
            ) {
                val appVersion = remember {
                    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
                }
                // The items take all the room (they only scroll on a very small screen); the version is at the bottom.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Same height as the top bar; the menu button (now an X) sits at the left of this row.
                    Box(
                        Modifier.fillMaxWidth().height(64.dp).padding(start = 60.dp, end = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // "Menu": you have several wishlists, so not the name of one of them (owner, 2.88).
                        Text(
                            tr("Menu"), style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Only the screen that is open is highlighted: one item at a time.
                    val elsewhere = settings || binOpen || friendCode != null || notesOpen || pricesOpen || friendsOpen || profileOpen
                    val onList = !elsewhere && !hub
                    DrawerItem(tr("Home"), AppIcons.Home, hub && !elsewhere) {
                        HubNav.page = null
                        settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = true
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("Profile"), AppIcons.Person, profileOpen) {
                        settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false
                        profileOpen = true
                        scope.launch { drawer.close() }
                    }
                    // Your own wishlists, then a way to make a new one.
                    DrawerDivider()
                    Text(
                        tr("My wishlists"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
                    )
                    Repo.mineIds.forEach { id ->
                        DrawerItem(Repo.mineName(id), AppIcons.Favorite, onList && Shared.active == null && Repo.openMine == id) {
                            openMine(id)
                            scope.launch { drawer.close() }
                        }
                    }
                    DrawerItem(tr("New wishlist"), AppIcons.Add, false) {
                        newMine = true
                        scope.launch { drawer.close() }
                    }
                    // Then the wishlists you make together with other givers (group wishlists).
                    DrawerDivider()
                    Text(
                        tr("Group wishlists"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
                    )
                    Shared.lists.forEach { sl ->
                        DrawerItem(sl.name, AppIcons.Share, onList && Shared.active == sl.code) {
                            openGroup(sl.code)
                            scope.launch { drawer.close() }
                        }
                    }
                    DrawerItem(tr("New group wishlist"), AppIcons.Add, false) {
                        newShared = true
                        scope.launch { drawer.close() }
                    }
                    DrawerDivider()
                    if (Repo.friends.isNotEmpty()) {
                        Text(
                            tr("Friends’ lists"), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
                        )
                        Repo.friends.forEach { f ->
                            DrawerItem(f.name, AppIcons.Person, !settings && !binOpen && !notesOpen && friendCode == f.code) {
                                friendCode = f.code; settings = false; binOpen = false
                                scope.launch { drawer.close() }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    DrawerItem(tr("Notifications"), AppIcons.Notifications, notesOpen && !settings && !binOpen, dot = Notifications.unread) {
                        settings = false; binOpen = false; friendCode = null; listSettings = false
                        pricesOpen = false; friendsOpen = false; notesOpen = true
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("Bin"), AppIcons.Delete, binOpen) {
                        binOpen = true; settings = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("App settings"), AppIcons.Settings, settings) {
                        settings = true; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
                        scope.launch { drawer.close() }
                    }
                }
                // Bottom left of the menu: the version of the app.
                Text(
                    tr("Version %s", appVersion), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 16.dp)
                )
            }
        }
    ) {
        // The screen behind the menu blurs a little while the menu is open.
        val fr = Repo.friends.firstOrNull { it.code == friendCode }
        val showBar = !settings && !binOpen && fr == null && !listSettings && !notesOpen && !profileOpen
        Column(Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)) {
        // With the bottom bar, the screens above it don't leave room for the phone's own navigation bar again.
        Box(Modifier.weight(1f).then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            when {
                profileOpen -> ProfileScreen(snack)
                settings -> SettingsScreen(snack, openMenu)
                binOpen -> BinScreen(snack)
                fr != null -> FriendScreen(fr, snack, openMenu, onRemoved = { friendCode = null })
                listSettings -> key(Shared.active, Repo.openMine) {
                    ListSettingsScreen(snack, onDeleted = { listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = true }) { listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false }
                }
                notesOpen -> NotificationsScreen(snack) { key ->
                    notesOpen = false; profileOpen = false
                    // Opens the list the wish is on.
                    if (key.startsWith("m:")) key.removePrefix("m:").takeIf { it in Repo.mineIds }?.let { openMine(it) }
                    else key.removePrefix("s:").takeIf { k -> Shared.lists.any { it.code == k } }?.let { openGroup(it) }
                }
                pricesOpen -> PricesScreen(snack, onProfile = { profileOpen = true })
                friendsOpen -> FriendsScreen(snack, onProfile = { profileOpen = true }, onOpenLink = { friendCode = it })
                hub -> HubScreen(
                    snack,
                    onOpenMine = { openMine(it) },
                    onOpenGroup = { openGroup(it) },
                    onNewMine = { newMine = true },
                    onNewGroup = { newShared = true },
                    onProfile = { profileOpen = true }
                )
                else -> key(Shared.active, Repo.openMine) {
                    HomeScreen(
                        snack, openMenu,
                        onAdd = { prefill = ""; adding = true },
                        onNewCat = { catDlg = true },
                        onInvite = { inviteDlg = true },
                        onListSettings = { listSettings = true }
                    )
                }
            }
        }
        if (showBar) {
            AppBottomBar(
                current = when { pricesOpen -> "prices"; friendsOpen -> "friends"; hub -> "home"; else -> "" },
                onHome = { HubNav.page = null; settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = true },
                onCreate = { createChoice = true },
                onAdd = {
                    val onAList = !hub && !pricesOpen && !friendsOpen
                    addTarget = if (onAList) (Shared.active ?: MyLists.saveKey(Repo.openMine, false)) else Repo.lastSaveTo
                    prefill = ""; adding = true
                },
                onPrices = { pricesOpen = true; friendsOpen = false },
                onFriends = {
                    // Tapping Friends again goes back to its first page.
                    if (friendsOpen) FriendsNav.reset()
                    friendsOpen = true; pricesOpen = false
                },
                friendsDot = Friends.waiting > 0
            )
        }
        }
    }

    // The menu button lives above the menu so it can spin into an X and close it.
    if (!inListSettings && !inThemer && !inFriend && !inOverview) Box(
        Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets).height(64.dp).padding(start = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        IconButton(
            onClick = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } },
            modifier = Modifier.semantics { contentDescription = if (menuOpen) tr("Close menu") else tr("Open menu") }
        ) {
            Box {
                AnimatedMenuIcon(menuOpen, MaterialTheme.colorScheme.onSurface)
                // Something new in Notifications: a red dot on the menu button too.
                if (Notifications.unread && !menuOpen) RedDot(Modifier.align(Alignment.TopEnd).offset(4.dp, (-3).dp))
            }
        }
    }
    }

    if (adding) key(prefill) {
        WishDialog(null, prefill, chooseList = prefill.isNotEmpty() || addTarget != null, startTarget = addTarget) {
            adding = false; addTarget = null
        }
    }
    if (createChoice) {
        CreateChoiceDialog(
            onMine = { createChoice = false; newMine = true },
            onGroup = { createChoice = false; newShared = true },
            onClose = { createChoice = false }
        )
    }
    if (catDlg) CategoryDialog { catDlg = false }
    if (newShared) {
        NewSharedDialog(
            onCreated = { code ->
                newShared = false
                // It appears under Group wishlists on the dashboard; the invite comes up so you can ask people in.
                Shared.open(ctx, code); settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false
                hub = true
                inviteDlg = true
            },
            onClose = { newShared = false }
        )
    }
    joinDlg?.let { inviteKey ->
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
    if (newMine) {
        NewListDialog(
            onCreated = { id ->
                newMine = false
                // It appears under My wishlists on the dashboard.
                settings = false; binOpen = false; friendCode = null; listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = true
                scope.launch { snack.showToast(tr("“%s” is ready", Repo.mineName(id))) }
            },
            onClose = { newMine = false }
        )
    }
    DeleteHost(snack)
    PrivateHost(snack)
    if (askBackground) BackgroundQuestion()
    // A "Target price reached" alert (or its entry in Notifications): that wish's price history, the target lit up.
    HistoryNav.open?.let { id ->
        val w = remember(id, Repo.mineVersion, Repo.myWishes) { Repo.allMine(ctx).flatMap { it.wishes }.firstOrNull { it.id == id } }
        if (w != null) PriceHistoryDialog(w, highlight = true) { HistoryNav.open = null }
        else LaunchedEffect(id) { HistoryNav.open = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snack: SnackbarHostState, onMenu: () -> Unit, onAdd: () -> Unit, onNewCat: () -> Unit, onInvite: () -> Unit,
    onListSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var cat by remember { mutableStateOf("All") }
    var catMenu by remember { mutableStateOf(false) }
    var catAction by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }
    var editing by remember { mutableStateOf<Wish?>(null) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    var historyId by remember { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current
    val openMenu: (Wish) -> Unit = { w ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuId = w.id
    }
    // A shared wishlist is refreshed every 20 seconds while this screen is in front.
    val lifeOwner = ctx as? LifecycleOwner
    var foreground by remember { mutableStateOf(true) }
    DisposableEffect(lifeOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                foreground = true
            } else if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
            }
        }
        lifeOwner?.lifecycle?.addObserver(obs)
        onDispose { lifeOwner?.lifecycle?.removeObserver(obs) }
    }
    LaunchedEffect(foreground) {
        if (foreground && Shared.active != null) {
            while (true) {
                Shared.refresh(ctx)
                delay(20_000)
            }
        }
    }
    LaunchedEffect(Shared.notice) {
        val m = Shared.notice
        if (m.isNotEmpty()) {
            Shared.clearNotice()
            snack.showToast(m)
        }
    }
    val inShared = Shared.active != null
    // For "Recently opened" on the dashboard.
    LaunchedEffect(Shared.active, Repo.openMine) {
        Repo.touchRecent(ctx, Shared.active?.let { "s:$it" } ?: ("m:" + Repo.openMine))
    }
    val pull = rememberPullToRefreshState()
    if (pull.isRefreshing) {
        LaunchedEffect(true) {
            // Asking every shop can take long (some are read in a hidden browser, one wish at a time), so the circle
            // shows for at most 2.5 seconds; the check goes on in the background, prices change on screen as they
            // come in, and a message says when it's done.
            val job = PriceRefresh.start(ctx, if (inShared) Shared.active else null) { msg -> snack.showToast(msg) }
            val finished = withTimeoutOrNull(2500) { job.join() } != null
            pull.endRefresh()
            if (!finished) snack.showToast(tr("Checking prices…"), duration = SnackbarDuration.Short)
        }
    }
    // A shared wishlist is put together by the givers, for someone else: there is no "most wanted" (nobody can say
    // that for the person) and no "got it" (reserving is what counts). Wishes are shown without them.
    val sortMode = if (inShared && Repo.sort == "star") "new" else Repo.sort
    val res = if (inShared) Shared.reserved else emptyMap()
    val source = if (inShared) Repo.wishes.map { it.copy(star = false, got = false) } else Repo.wishes

    val sel = if (cat == "All" || cat in Repo.cats) cat else "All"
    val list = sortWishes(
        source.filter { (sel == "All" || it.category == sel) && matchesQuery(it, query) },
        sortMode
    )
    val view = Repo.view
    val look = LocalLook.current

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    ListTitle(Repo.listName, Repo.wishes.size)
                },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
                        Icon(AppIcons.Search, if (searching) tr("Close search") else tr("Search"))
                    }
                    // The settings of this list. Refreshing is pulling down on the list.
                    IconButton(onClick = onListSettings) { Icon(AppIcons.Settings, tr("Wishlist settings")) }
                    IconButton(onClick = { if (inShared) onInvite() else startShare(ctx, scope, snack) }) {
                        Icon(AppIcons.Share, if (inShared) tr("Invite or share") else tr("Share wishlist"))
                    }
                }
            )
        },
        floatingActionButton = { AddWishButton(onAdd) },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        // Behind the list: the blobs, glows or dots of the design (nothing in Material 3).
        Column(Modifier.padding(pad).lookBackdrop(look)) {
            if (inShared && Shared.gone) SharedGoneNotice()
            if (searching) SearchField(query, { query = it }) { searching = false; query = "" }
            // The category and sort choices and the view buttons. Since 2.89 they scroll with the list, under the
            // cover photo (when chosen) and the summary card.
            val filters: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: what you see and in which order (category, sort). Right: how it looks (view buttons).
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f, fill = false)) {
                            Row(
                                Modifier.lookChip(look).clickable { catMenu = true }
                                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    catLabel(sel), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp)
                                )
                                Icon(AppIcons.ArrowDropDown, tr("Choose category"))
                            }
                            DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                                (listOf("All") + Repo.cats).forEach { c ->
                                    val n = if (c == "All") Repo.wishes.size else Repo.wishes.count { it.category == c }
                                    // Long press a category to edit or delete it ("All" can't be changed).
                                    CatMenuRow(
                                        text = catLabel(c) + " ($n)", bold = c == sel,
                                        onClick = { cat = c; catMenu = false },
                                        onLongClick = if (c == "All") null else ({
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            catMenu = false
                                            catAction = c
                                        })
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(tr("New category…")) },
                                    leadingIcon = { Icon(AppIcons.Add, null) },
                                    onClick = { catMenu = false; onNewCat() }
                                )
                            }
                        }
                        SortButton(sortMode, withStar = !inShared) { Repo.setSort(ctx, it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(if (look.classic) 2.dp else 6.dp)) {
                        ViewButton("list", tr("Large cards"), view == "list") { Repo.setView(ctx, "list") }
                        ViewButton("grid", tr("Two per row"), view == "grid") { Repo.setView(ctx, "grid") }
                    }
                }
            }
            // Pull down on the list to refresh it: the shops are asked for their prices now (and a shared list is
            // fetched again), so price changes show. This took the place of the refresh button in the top bar.
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pull.nestedScrollConnection)) {
            if (list.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ListHeader(Repo.wishes)
                    Spacer(Modifier.height(8.dp))
                    filters()
                    Text(
                        if (query.isNotBlank()) {
                            if (sel != "All") tr("No wishes match “%1\$s” in %2\$s.", query.trim(), catLabel(sel))
                            else tr("No wishes match “%s”.", query.trim())
                        } else tr("Nothing here yet. Tap Add wish, or share a product link to Wishlist from your browser."),
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val gap = if (view == "grid") 12.dp else 16.dp
                LazyVerticalGrid(
                    columns = if (view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) { ListHeader(Repo.wishes) }
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { filters() }
                    gridItems(list, key = { it.id }) { w ->
                        val r = res[w.id]
                        when (view) {
                            "grid" -> CompactCard(
                                w, onLongPress = { openMenu(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                reserve = if (inShared) Reserving(r) { on -> Shared.reserve(w, on) } else null,
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) })
                            ) { editing = w }
                            else -> WishCard(
                                w,
                                badge = if (inShared) reservedLabel(r) else if (w.got) tr("Got it") else null,
                                dim = if (inShared) r == false else w.got,
                                onLongPress = { openMenu(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) })
                            ) {
                                IconButton(onClick = { editing = w }) {
                                    Icon(AppIcons.Edit, tr("Edit"), tint = look.cardMuted(w.id))
                                }
                                if (inShared) {
                                    // Reserving takes the place of "got it" on a shared list.
                                    if (r == true) {
                                        OutlinedButton(onClick = { Shared.reserve(w, false) }) { Text(tr("Undo")) }
                                    } else if (r == null) {
                                        Button(onClick = { Shared.reserve(w, true) }) { Text(tr("Reserve")) }
                                    }
                                } else {
                                    IconButton(onClick = { Repo.replace(ctx, w.copy(got = !w.got)) }) {
                                        Icon(
                                            AppIcons.Check, tr("Mark as got"),
                                            tint = if (w.got) cs.primary else look.cardMuted(w.id)
                                        )
                                    }
                                }
                                IconButton(onClick = { DeleteFlow.request(ctx, w) }) {
                                    Icon(AppIcons.Delete, tr("Delete"), tint = look.cardMuted(w.id))
                                }
                            }
                        }
                    }
                }
            }
            // Only while pulling or refreshing: at rest the circle with the arrow must not show at all.
            if (pull.progress > 0f || pull.isRefreshing) {
                PullToRefreshContainer(state = pull, modifier = Modifier.align(Alignment.TopCenter))
            }
            }
        }
    }

    editing?.let { w -> key(w.id) { WishDialog(w, "") { editing = null } } }
    menuId?.let { id -> source.firstOrNull { it.id == id } }?.let { w ->
        ItemMenuDialog(
            w,
            onHistory = if (inShared) null else ({ menuId = null; historyId = w.id }),
            onShare = { menuId = null; shareItem(ctx, w) },
            onStar = if (inShared) null else ({ menuId = null; Repo.replace(ctx, w.copy(star = !w.star)) }),
            onPrivate = if (inShared) null else ({ menuId = null; togglePrivate(ctx, w) }),
            onClose = { menuId = null }
        )
    }
    historyId?.let { id -> Repo.wishes.firstOrNull { it.id == id } }?.let { w ->
        PriceHistoryDialog(w) { historyId = null }
    }
    catAction?.let { ca ->
        CategoryActionsDialog(
            name = ca,
            onRenamed = { n -> if (cat == ca) cat = n },
            onDeleted = {},
            onClose = { catAction = null }
        )
    }
}

/** One of the three view buttons in the row above the wishes. */
@Composable
fun ViewButton(kind: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    // The classic themes show only the chosen one filled (as before); the designs frame both, as buttons.
    val framed = !look.classic || selected
    Box(
        Modifier.size(if (look.classic) 44.dp else 40.dp)
            .then(if (framed) Modifier.lookChip(look, on = selected) else Modifier.clip(look.chipShape))
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ViewIcon(kind, look.chipContent(selected))
    }
}

/** Tiny drawn icons: two wide bars, a 2x2 grid, a 3x3 grid. */
@Composable
fun ViewIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color = tint, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u),
            cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
        )
        when (kind) {
            "list" -> { box(3f, 4f, 18f, 7f); box(3f, 13f, 18f, 7f) }
            else -> { box(3f, 3f, 8f, 8f); box(13f, 3f, 8f, 8f); box(3f, 13f, 8f, 8f); box(13f, 13f, 8f, 8f) }
        }
    }
}

/** Reserving a wish on a shared list. [state]: true = reserved on this phone, false = by someone else, null = still free. */
class Reserving(val state: Boolean?, val set: (Boolean) -> Unit)

/** The label for a reservation on a shared list, or null when the wish is still free. */
fun reservedLabel(r: Boolean?): String? = when (r) {
    true -> tr("Reserved by you")
    false -> tr("Reserved")
    else -> null
}

/**
 * Two-per-row card: picture, title and price, with edit / got it / remove in a small menu.
 * On a shared list ([reserve] set) the menu has Reserve instead of most wanted and got it.
 * The owner gets a round star on the picture to tap (no text, there is no room); friends only see it when it is set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactCard(
    w: Wish, onLongPress: (() -> Unit)? = null, onStar: (() -> Unit)? = null, reserve: Reserving? = null,
    onPrivate: (() -> Unit)? = null, emoji: String = "🎁", inert: Boolean = false, onEdit: () -> Unit
) {
    val ctx = LocalContext.current
    // inert: a sample card (the Themer's preview) that does nothing when tapped.
    val look = LocalLook.current
    // The text on the card: dark ink on the bright cards of neobrutalism Midnight, the theme's own text otherwise.
    val ink = look.cardInk(w.id)
    val muted = look.cardMuted(w.id)
    val ui = LocalUriHandler.current
    // Tapping the picture shows it big (ImageViewer); tapping the words opens the shop page.
    var viewing by remember { mutableStateOf(false) }
    if (viewing) ImageViewer(w) { viewing = false }
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)

    // The frame comes from the design (Design.kt): shape, colour, lines and shadow.
    Column(
        Modifier.fillMaxWidth().alpha(if (w.got || reserve?.state == false) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = true))
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
        Box(
            Modifier.padding(look.inset(small = true)).fillMaxWidth().height(130.dp)
                .lookMat(look, look.pictureShape(w.id, small = true), look.pictureBg(w.id, w.image.isNotEmpty()))
                .combinedClickable(
                    enabled = w.image.isNotEmpty() || w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.image.isNotEmpty()) tr("View picture") else null,
                    onClick = {
                        if (w.image.isNotEmpty()) viewing = true
                        else if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } }
                    },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            if (w.image.isNotEmpty()) {
                PhotoCard(w.image, w.title, small = true)
            } else Text(emoji, fontSize = 36.sp)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if ((drop || rise) && newP != null && oldP != null) {
                    PriceBadge(
                        if (rise) "+${Math.round((newP / oldP - 1) * 100)}%" else "−${Math.round((1 - newP / oldP) * 100)}%",
                        rise, small = true
                    )
                }
            }
            // Bottom left: the eye of a private wish (the price change has the top left to itself).
            if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(8.dp), size = 14.dp, onClick = onPrivate)
            if (!w.got) {
                if (onStar != null) StarToggle(w.star, onStar, Modifier.align(Alignment.TopEnd).padding(6.dp))
                else if (w.star) MostWantedBadge(false, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            val status = if (w.got) tr("Got it") else reservedLabel(reserve?.state)
            if (status != null) {
                StatusBadge(status, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            // The name and price lead to the shop page.
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                )
            ) {
                Text(
                    w.title, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = ink
                )
                Text(
                    if (newP != null) money(newP, w.currency) else tr("No price"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = look.priceWeight, fontFamily = look.display,
                    maxLines = 1, color = if (drop) look.drop else if (rise) look.rise else ink,
                    modifier = Modifier.padding(top = 2.dp)
                )
                AddedOnText(w, muted, Modifier.padding(bottom = 6.dp))
            }
            Box {
                IconButton(onClick = { if (!inert) menu = true }) {
                    Icon(AppIcons.MoreVert, tr("More"), tint = muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Edit")) }, onClick = { menu = false; onEdit() })
                    if (reserve != null) {
                        when (reserve.state) {
                            true -> DropdownMenuItem(text = { Text(tr("Undo reservation")) }, onClick = { menu = false; reserve.set(false) })
                            null -> DropdownMenuItem(text = { Text(tr("Reserve")) }, onClick = { menu = false; reserve.set(true) })
                            else -> {}
                        }
                    } else {
                        DropdownMenuItem(
                            text = { Text(if (w.star) tr("Remove top wish") else tr("Top wish")) },
                            onClick = { menu = false; Repo.replace(ctx, w.copy(star = !w.star)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (w.got) tr("Mark as not got") else tr("Mark as got")) },
                            onClick = { menu = false; Repo.replace(ctx, w.copy(got = !w.got)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (w.priv) tr("Visible to friends") else tr("Make private")) },
                            onClick = { menu = false; togglePrivate(ctx, w) }
                        )
                    }
                    DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = { menu = false; DeleteFlow.request(ctx, w) })
                }
            }
        }
        }
    }
}

/** One wish. [actions] are the buttons at the bottom right, which differ for the owner and for friends. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WishCard(
    w: Wish, badge: String? = null, dim: Boolean = false, onLongPress: (() -> Unit)? = null,
    onStar: (() -> Unit)? = null, onPrivate: (() -> Unit)? = null, emoji: String = "🎁",
    actions: @Composable RowScope.() -> Unit
) {
    val look = LocalLook.current
    // The text on the card: dark ink on the bright cards of neobrutalism Midnight, the theme's own text otherwise.
    val ink = look.cardInk(w.id)
    val muted = look.cardMuted(w.id)
    val ui = LocalUriHandler.current
    // Tapping the picture shows it big (ImageViewer); tapping the words opens the shop page.
    var viewing by remember { mutableStateOf(false) }
    if (viewing) ImageViewer(w) { viewing = false }
    val cs = MaterialTheme.colorScheme
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    val label = listOf(host(w.url), w.category.takeIf { it.isNotBlank() && it != "All" })
        .filter { !it.isNullOrBlank() }.joinToString(" · ")

    // The frame comes from the design (Design.kt): shape, colour, lines and shadow.
    Column(
        Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = false))
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
        Box(
            Modifier.padding(look.inset(small = false)).fillMaxWidth().height(160.dp)
                .lookMat(look, look.pictureShape(w.id, small = false), look.pictureBg(w.id, w.image.isNotEmpty()))
                .combinedClickable(
                    enabled = w.image.isNotEmpty() || w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.image.isNotEmpty()) tr("View picture") else null,
                    onClick = {
                        if (w.image.isNotEmpty()) viewing = true
                        else if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } }
                    },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            if (w.image.isNotEmpty()) {
                PhotoCard(w.image, w.title, small = false)
            } else Text(emoji, fontSize = 48.sp)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if ((drop || rise) && newP != null && oldP != null) {
                    PriceBadge(
                        if (rise) "+${Math.round((newP / oldP - 1) * 100)}%" else "−${Math.round((1 - newP / oldP) * 100)}%",
                        rise, small = false
                    )
                }
            }
            // Bottom left: the eye of a private wish (the price change has the top left to itself).
            if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(12.dp), onClick = onPrivate)
            // Top right: the star (the owner can tap it), and below it the status such as "Got it".
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StarCorner(w, onStar)
                if (badge != null) {
                    StatusBadge(badge, small = false)
                }
            }
        }
        if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
        Column(Modifier.padding(16.dp)) {
            // The name and details lead to the shop page.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                )
            ) {
                if (label.isNotEmpty()) {
                    // Organic shows it as a small spaced-out line in capitals, like its list title.
                    Text(
                        if (look.design == "organic") label.uppercase() else label,
                        style = MaterialTheme.typography.labelMedium, color = muted,
                        letterSpacing = if (look.design == "organic") 1.2.sp else TextUnit.Unspecified
                    )
                }
                Text(
                    w.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = ink
                )
                if (w.desc.isNotBlank()) {
                    Text(
                        w.desc, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = muted
                    )
                }
                if (w.size.isNotBlank()) SizeTag(w.size, Modifier.padding(top = 4.dp))
                if (w.note.isNotBlank()) {
                    Text(
                        tr("Note: %s", w.note), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, color = ink
                    )
                }
                if (w.by.isNotBlank()) {
                    Text(tr("Added by %s", w.by), style = MaterialTheme.typography.labelMedium, color = muted)
                }
                AddedOnText(w, muted, Modifier.padding(top = 2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (newP != null) money(newP, w.currency) else tr("No price"),
                        style = MaterialTheme.typography.titleLarge, fontWeight = look.priceWeight,
                        color = if (drop) look.drop else if (rise) look.rise else ink
                    )
                    if ((drop || rise) && oldP != null) {
                        Text(
                            money(oldP, w.currency), style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.LineThrough, color = muted,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                actions()
            }
        }
        }
    }
}

/** Adds a new wish, or edits [edit] when it is not null. */
@Composable
fun WishDialog(edit: Wish?, initialUrl: String, chooseList: Boolean = false, startTarget: String? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Which wishlist was open when this dialog started. A wish shared in from the browser can be saved to another one.
    val startList = remember { Shared.active }
    val targets = saveTargets(Repo.mineIds.map { it to (Repo.mineNames[it] ?: "My wishlist") }, Shared.lists)
    var url by remember { mutableStateOf(edit?.url ?: initialUrl) }
    var title by remember { mutableStateOf(edit?.title ?: "") }
    var price by remember { mutableStateOf(edit?.price?.let { "%.2f".format(Locale.US, it) } ?: "") }
    var desc by remember { mutableStateOf(edit?.desc ?: "") }
    var image by remember { mutableStateOf(edit?.image ?: "") }
    var cur by remember { mutableStateOf(edit?.currency ?: "USD") }
    // A new wish starts without a category: you pick one yourself if you want (2.64).
    var cat by remember { mutableStateOf(edit?.category ?: "") }
    var note by remember { mutableStateOf(edit?.note ?: "") }
    var sizeSel by remember { mutableStateOf(edit?.size ?: "") }
    var star by remember { mutableStateOf(edit?.star ?: false) }
    // "Private wish": only on this phone, friends never see it (My wishlist only). Off unless chosen.
    var priv by remember { mutableStateOf(edit?.priv ?: false) }
    val starFx = rememberStarFx()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    // True after Save was tapped without a name: the name field turns red with a short message.
    var nameErr by remember { mutableStateOf(false) }
    var newCatDlg by remember { mutableStateOf(false) }
    // A picture picked in this dialog but not saved yet. It is deleted again if you cancel or replace it.
    var picked by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val p = ImageStore.import(ctx, uri)
                if (p == null) {
                    err = tr("Couldn’t use that picture.")
                } else {
                    if (picked.isNotEmpty()) ImageStore.delete(ctx, picked)
                    picked = p; image = p; err = ""
                }
            }
        }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun close() {
        if (picked.isNotEmpty()) ImageStore.delete(ctx, picked)
        // Cancelled after choosing another wishlist: go back to the one that was open.
        if (Shared.active != startList) {
            if (startList == null) Shared.close() else Shared.open(ctx, startList)
        }
        onClose()
    }

    fun go(raw: String) {
        val u = cleanUrl(raw)
        if (u == null) { err = tr("Enter a full product link"); return }
        url = u; busy = true; err = ""
        scope.launch {
            val m = fetchMeta(u, ctx)
            busy = false
            if (m == null) {
                err = tr("Couldn’t read that page. Fill in the details yourself.")
            } else if (m.blocked) {
                err = tr("The store blocked the automatic read. Enter the title and price yourself.")
            } else {
                title = m.title; desc = m.desc; cur = m.currency
                // A picture you chose yourself is kept. Otherwise the image comes from the link.
                if (!ImageStore.isLocal(image)) image = if (m.image.isNotEmpty() || edit == null) m.image else image
                if (m.price != null || edit == null) price = m.price?.let { "%.2f".format(Locale.US, it) }.orEmpty()
                if (m.price == null) err = tr("No price found on that page. Add it to track price drops.")
            }
        }
    }
    // A wish shared in from the browser goes where the last one went (My wishlist, private, or a shared list).
    fun pickTarget(key: String) {
        val mineId = MyLists.idOfSaveKey(key)
        if (mineId != null) {
            Shared.close()
            Repo.openMine(ctx, mineId)
        } else if (Shared.lists.any { it.code == key }) {
            Shared.open(ctx, key); priv = false
        }
        // Every wishlist has its own categories; none is picked for you.
        cat = ""
    }
    LaunchedEffect(Unit) {
        if (chooseList && edit == null) pickTarget(startTarget ?: Repo.lastSaveTo)
        if (edit == null && initialUrl.isNotEmpty()) go(initialUrl)
    }

    AlertDialog(
        onDismissRequest = { close() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (edit == null) tr("Add a wish") else tr("Edit wish")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chooseList && edit == null) {
                    SaveToField(
                        targets = targets, selected = Shared.active ?: MyLists.saveKey(Repo.openMine, false),
                        onPick = { pickTarget(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    url,
                    { new ->
                        // Pasting a whole link fills in the details by itself.
                        val pasted = edit == null && new.length - url.length > 3 &&
                            Regex("https?://\\S+\\.\\S+").containsMatchIn(new)
                        url = new
                        if (pasted) go(new)
                    },
                    label = { Text(tr("Product link")) },
                    // No "Fetch details" button any more: pasting a link reads the page, and so does the keyboard's
                    // Done key after typing or changing one. A small circle shows while the page is being read.
                    trailingIcon = if (busy) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy && url.isNotBlank()) go(url) }),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ImageBlock(image, onPick = { pickImage() }, onRemove = {
                    if (picked.isNotEmpty() && picked == image) { ImageStore.delete(ctx, picked); picked = "" }
                    image = ""
                })
                // Name and price side by side: the price only needs room for "€9999,99", the name gets the rest and
                // stays on one line (a long name scrolls inside the field instead of taking three lines).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    // The * shows the name is the one thing a wish needs. Saving without one marks the field red.
                    OutlinedTextField(
                        title, { title = it; if (it.isNotBlank()) nameErr = false }, label = { Text(tr("Name*")) }, singleLine = true,
                        isError = nameErr,
                        supportingText = if (nameErr) ({ Text(tr("What are you wishing for?")) }) else null,
                        modifier = Modifier.weight(1f), keyboardOptions = Capitalised)
                    OutlinedTextField(
                        // Narrow, sized for "9999,99"; any amount can be typed, a longer one scrolls inside the field.
                        price, { price = it },
                        label = { Text(tr("Price (%s)", cur), maxLines = 1) }, singleLine = true,
                        // The symbol of the currency in the label: € for EUR, $ for USD, £ for GBP, …
                        prefix = { Text(currencySymbol(cur)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(104.dp)
                    )
                }
                // One line, however many categories there are (a row of chips got too long).
                CategoryField(cat, Repo.cats, onPick = { cat = it }, onNew = { newCatDlg = true }, modifier = Modifier.fillMaxWidth())
                // No "Top wish" on a shared list: nobody can say that for the person the gifts are for.
                if (Shared.active == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (!star) starFx.play(); star = !star }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StarIcon(star, if (star) StarGold else MaterialTheme.colorScheme.onSurfaceVariant, 24.dp, Modifier.starPulse(starFx))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Top wish"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            italics(tr("Shows people you _really_ like this")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = star, onCheckedChange = { star = it })
                }
                // Private wish: My wishlist only (a shared list is for the givers, there is nothing to hide there).
                if (Shared.active == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { priv = !priv }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(PrivateIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Private wish"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("Hides this wish from everyone you share your wishlist with"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = priv, onCheckedChange = { priv = it })
                }
                // "Other information" (description, size, note for givers) folds away, so the switches above stay in sight.
                ProductInfo {
                    OutlinedTextField(
                        desc, { desc = it }, label = { Text(tr("Description")) },
                        maxLines = 3, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                    SizeField(sizeSel, { sizeSel = it }, Modifier.fillMaxWidth())
                    OutlinedTextField(
                        note, { note = it }, label = { Text(tr("Note for givers (colour, details, …)")) },
                        maxLines = 3, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) { nameErr = true; return@TextButton }
                    val p = price.replace(',', '.').toDoubleOrNull()
                    val u = cleanUrl(url) ?: url.trim()
                    // A picture that was replaced or removed in this dialog is no longer needed.
                    if (edit != null && edit.image != image) ImageStore.delete(ctx, edit.image)
                    picked = ""
                    if (edit == null) {
                        Repo.add(
                            ctx,
                            Wish(
                                System.currentTimeMillis(), u, title.trim(), desc, image, p, null, cur, cat,
                                star = star && Shared.active == null, note = note.trim(), size = sizeSel, by = Repo.authorName,
                                priv = priv && Shared.active == null
                            )
                        )
                        if (chooseList) Repo.setLastSaveTo(ctx, Shared.active ?: MyLists.saveKey(Repo.openMine, false))
                    } else {
                        Repo.replace(
                            ctx,
                            edit.copy(
                                url = u, title = title.trim(), desc = desc, image = image, price = p,
                                oldPrice = if (p == edit.price) edit.oldPrice else null,
                                currency = cur, category = cat, star = star && Shared.active == null, note = note.trim(), size = sizeSel,
                                priv = priv && Shared.active == null
                            ),
                            base = edit
                        )
                    }
                    onClose()
                }
            ) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text(tr("Cancel")) } }
    )

    // A category made here is chosen for this wish straight away.
    if (newCatDlg) CategoryDialog(onAdded = { cat = it }) { newCatDlg = false }
}

/** The picture of a wish. Empty: a dashed block with a plus and "Add image". Filled: the picture, tap to change, X to remove. */
@Composable
fun ImageBlock(image: String, onPick: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline
    if (image.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
                .background(cs.surfaceVariant)
                .drawBehind {
                    drawRoundRect(
                        color = line, cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    )
                }
                .semantics { contentDescription = tr("Add image") }
                .clickable(onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(AppIcons.Add, null, Modifier.size(32.dp), tint = cs.onSurfaceVariant)
            Text(tr("Add image"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
    } else {
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .semantics { contentDescription = tr("Change image") }
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .semantics { contentDescription = tr("Remove image") }
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Close, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}
