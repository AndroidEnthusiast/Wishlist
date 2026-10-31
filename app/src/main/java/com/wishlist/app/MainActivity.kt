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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.load(this)
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

    override fun onStop() {
        super.onStop()
        // Leaving the app (not turning the screen) ends the time for Undo: the delete becomes final.
        if (!isChangingConfigurations) DeleteFlow.finishNow(this)
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
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    // null: closed, "": open and empty, otherwise opened with an invite that was sent to the app.
    var joinDlg by remember { mutableStateOf<String?>(null) }
    var inviteDlg by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun openFriend(code: String) {
        if (code == Repo.shareCode) {
            scope.launch { snack.showSnackbar(tr("That’s your own list. Friends reserve from it, and you can’t peek.")) }
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
                    snack.showSnackbar(tr("No list found for that code."))
                } else {
                    Repo.addFriend(ctx, Friend(code, r.name))
                    friendCode = code; settings = false; binOpen = false
                }
            } catch (e: Exception) {
                snack.showSnackbar(tr("Couldn’t reach the server. Check your connection."))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            prefill = sharedUrl; adding = true; settings = false; binOpen = false; friendCode = null; listSettings = false
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
            binOpen = true; settings = false; friendCode = null; listSettings = false
            scope.launch { drawer.close() }
            onBinUsed()
        }
    }
    LaunchedEffect(sharedInvite) {
        if (sharedInvite != null) {
            joinDlg = sharedInvite; settings = false; binOpen = false; friendCode = null; listSettings = false
            onSharedUsed()
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && (settings || binOpen || friendCode != null)) {
        settings = false; binOpen = false; friendCode = null; listSettings = false
    }
    // Back from a shared wishlist goes to My wishlist.
    BackHandler(enabled = !drawer.isOpen && !settings && !binOpen && friendCode == null && Shared.active != null) {
        Shared.close()
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
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !inListSettings,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(sheetWidth)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Same height as the top bar; the menu button (now an X) sits at the left of this row.
                    Box(
                        Modifier.fillMaxWidth().height(64.dp).padding(start = 60.dp, end = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            Repo.listName, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    DrawerItem(tr("My wishlist"), Icons.Default.Favorite, !settings && !binOpen && friendCode == null && Shared.active == null) {
                        Shared.close(); settings = false; binOpen = false; friendCode = null; listSettings = false
                        scope.launch { drawer.close() }
                    }
                    // A thin line, then the wishlists you edit together with other people.
                    DrawerDivider()
                    Text(
                        tr("Shared wishlists"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
                    )
                    Shared.lists.forEach { sl ->
                        DrawerItem(sl.name, Icons.Default.Share, !settings && !binOpen && friendCode == null && Shared.active == sl.code) {
                            Shared.open(ctx, sl.code); settings = false; binOpen = false; friendCode = null; listSettings = false
                            scope.launch { drawer.close() }
                        }
                    }
                    // A line between the wishlists above and the action below: one is a list, the other makes a list.
                    DrawerDivider()
                    DrawerItem(tr("New shared wishlist"), Icons.Default.Add, false) {
                        newShared = true
                        scope.launch { drawer.close() }
                    }
                    if (Repo.friends.isNotEmpty()) {
                        Text(
                            tr("Friends’ lists"), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
                        )
                        Repo.friends.forEach { f ->
                            DrawerItem(f.name, Icons.Default.Person, !settings && !binOpen && friendCode == f.code) {
                                friendCode = f.code; settings = false; binOpen = false
                                scope.launch { drawer.close() }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    DrawerItem(tr("Bin"), Icons.Default.Delete, binOpen) {
                        binOpen = true; settings = false; friendCode = null; listSettings = false
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("App settings"), Icons.Default.Settings, settings) {
                        settings = true; binOpen = false; friendCode = null; listSettings = false
                        scope.launch { drawer.close() }
                    }
                }
            }
        }
    ) {
        // The screen behind the menu blurs a little while the menu is open.
        Box(Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)) {
            val fr = Repo.friends.firstOrNull { it.code == friendCode }
            when {
                settings -> SettingsScreen(snack, openMenu)
                binOpen -> BinScreen(snack)
                fr != null -> FriendScreen(fr, snack, openMenu) { friendCode = null }
                listSettings -> key(Shared.active) { ListSettingsScreen(snack) { listSettings = false } }
                else -> key(Shared.active) {
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
    }

    // The menu button lives above the menu so it can spin into an X and close it.
    if (!inListSettings) Box(
        Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets).height(64.dp).padding(start = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        IconButton(
            onClick = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } },
            modifier = Modifier.semantics { contentDescription = if (menuOpen) tr("Close menu") else tr("Open menu") }
        ) { AnimatedMenuIcon(menuOpen, MaterialTheme.colorScheme.onSurface) }
    }
    }

    if (adding) key(prefill) { WishDialog(null, prefill, chooseList = prefill.isNotEmpty()) { adding = false } }
    if (catDlg) CategoryDialog { catDlg = false }
    if (newShared) {
        NewSharedDialog(
            onCreated = { code ->
                newShared = false
                Shared.open(ctx, code); settings = false; binOpen = false; friendCode = null; listSettings = false
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
                Shared.open(ctx, code); settings = false; binOpen = false; friendCode = null; listSettings = false
            },
            onClose = { joinDlg = null }
        )
    }
    if (inviteDlg) SharedShareDialog { inviteDlg = false }
    DeleteHost(snack)
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
    var detailId by remember { mutableStateOf<Long?>(null) }
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
            snack.showSnackbar(m)
        }
    }
    val inShared = Shared.active != null
    val pull = rememberPullToRefreshState()
    if (pull.isRefreshing) {
        LaunchedEffect(true) {
            val msg = try {
                if (inShared) {
                    val code = Shared.active
                    if (!Shared.refresh(ctx)) tr("Couldn’t reach the server")
                    else {
                        val n = if (code != null) SharedPrices.check(ctx, code) else 0
                        Shared.refresh(ctx)
                        if (n > 0) tr("Prices checked: %d cheaper", n) else tr("Prices checked")
                    }
                } else {
                    val n = PriceCheck.mine(ctx)
                    if (n > 0) tr("Prices checked: %d cheaper", n) else tr("Prices checked")
                }
            } catch (e: Exception) {
                tr("Couldn’t check the prices. Check your connection.")
            }
            pull.endRefresh()
            snack.showSnackbar(msg)
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

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(Repo.listName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
                        Icon(Icons.Default.Search, if (searching) tr("Close search") else tr("Search"))
                    }
                    // The settings of this list. Refreshing is pulling down on the list.
                    IconButton(onClick = onListSettings) { Icon(Icons.Default.Settings, tr("Wishlist settings")) }
                    IconButton(onClick = { if (inShared) onInvite() else startShare(ctx, scope, snack) }) {
                        Icon(Icons.Default.Share, if (inShared) tr("Invite or share") else tr("Share wishlist"))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(tr("Add wish")) },
                containerColor = cs.primaryContainer,
                contentColor = cs.onPrimaryContainer
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (inShared && Shared.gone) SharedGoneNotice()
            if (searching) SearchField(query, { query = it }) { searching = false; query = "" }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: what you see and in which order (category, sort). Right: how it looks (view buttons).
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f, fill = false)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable { catMenu = true }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                catLabel(sel), style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp)
                            )
                            Icon(Icons.Default.ArrowDropDown, tr("Choose category"))
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
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { catMenu = false; onNewCat() }
                            )
                        }
                    }
                    SortButton(sortMode, withStar = !inShared) { Repo.setSort(ctx, it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ViewButton("list", tr("Large cards"), view == "list") { Repo.setView(ctx, "list") }
                    ViewButton("grid", tr("Two per row"), view == "grid") { Repo.setView(ctx, "grid") }
                    ViewButton("gallery", tr("Images only"), view == "gallery") { Repo.setView(ctx, "gallery") }
                }
            }
            // Pull down on the list to refresh it: the shops are asked for their prices now (and a shared list is
            // fetched again), so price changes show. This took the place of the refresh button in the top bar.
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pull.nestedScrollConnection)) {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) {
                            if (sel != "All") tr("No wishes match “%1\$s” in %2\$s.", query.trim(), catLabel(sel))
                            else tr("No wishes match “%s”.", query.trim())
                        } else tr("Nothing here yet. Tap Add wish, or share a product link to Wishlist from your browser."),
                        color = cs.onSurfaceVariant
                    )
                }
            } else {
                val gap = when (view) { "gallery" -> 8.dp; "grid" -> 12.dp; else -> 16.dp }
                LazyVerticalGrid(
                    columns = when (view) {
                        "gallery" -> GridCells.Adaptive(100.dp)
                        "grid" -> GridCells.Fixed(2)
                        else -> GridCells.Adaptive(300.dp)
                    },
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    gridItems(list, key = { it.id }) { w ->
                        val r = res[w.id]
                        when (view) {
                            "gallery" -> ImageTile(
                                w, onLongPress = { openMenu(w) }, status = reservedLabel(r), dimmed = r == false
                            ) { detailId = w.id }
                            "grid" -> CompactCard(
                                w, onLongPress = { openMenu(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                reserve = if (inShared) Reserving(r) { on -> Shared.reserve(w, on) } else null
                            ) { editing = w }
                            else -> WishCard(
                                w,
                                badge = if (inShared) reservedLabel(r) else if (w.got) tr("Got it") else null,
                                dim = if (inShared) r == false else w.got,
                                onLongPress = { openMenu(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) })
                            ) {
                                IconButton(onClick = { editing = w }) {
                                    Icon(Icons.Default.Edit, tr("Edit"), tint = cs.onSurfaceVariant)
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
                                            Icons.Default.Check, tr("Mark as got"),
                                            tint = if (w.got) cs.primary else cs.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { DeleteFlow.request(ctx, w) }) {
                                    Icon(Icons.Default.Delete, tr("Delete"), tint = cs.onSurfaceVariant)
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
    detailId?.let { id -> source.firstOrNull { it.id == id } }?.let { w ->
        WishDetailDialog(
            w, onEdit = { editing = w },
            reserve = if (inShared) Reserving(res[w.id]) { on -> Shared.reserve(w, on) } else null
        ) { detailId = null }
    }
    menuId?.let { id -> source.firstOrNull { it.id == id } }?.let { w ->
        ItemMenuDialog(
            w,
            onHistory = if (inShared) null else ({ menuId = null; historyId = w.id }),
            onShare = { menuId = null; shareItem(ctx, w) },
            onStar = if (inShared) null else ({ menuId = null; Repo.replace(ctx, w.copy(star = !w.star)) }),
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
private fun ViewButton(kind: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ViewIcon(kind, if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
    }
}

/** Tiny drawn icons: two wide bars, a 2x2 grid, a 3x3 grid. */
@Composable
private fun ViewIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color = tint, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u),
            cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
        )
        when (kind) {
            "list" -> { box(3f, 4f, 18f, 7f); box(3f, 13f, 18f, 7f) }
            "grid" -> { box(3f, 3f, 8f, 8f); box(13f, 3f, 8f, 8f); box(3f, 13f, 8f, 8f); box(13f, 13f, 8f, 8f) }
            else -> repeat(3) { r -> repeat(3) { c -> box(3f + c * 6.8f, 3f + r * 6.8f, 5.2f, 5.2f) } }
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
    w: Wish, onLongPress: (() -> Unit)? = null, onStar: (() -> Unit)? = null, reserve: Reserving? = null, onEdit: () -> Unit
) {
    val ctx = LocalContext.current
    val ui = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)

    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (w.got || reserve?.state == false) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        )
    ) {
        Box(
            Modifier.fillMaxWidth().height(130.dp)
                .background(if (w.image.isEmpty()) cs.primaryContainer else Color.White)
                .combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            if (w.image.isNotEmpty()) {
                AsyncImage(
                    model = w.image, contentDescription = w.title,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            } else Text("🎁", fontSize = 36.sp)
            if ((drop || rise) && newP != null && oldP != null) {
                Surface(
                    color = if (rise) Rise else Green, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text(
                        if (rise) "↑ +${Math.round((newP / oldP - 1) * 100)}%" else "↓ ${Math.round((1 - newP / oldP) * 100)}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (!w.got) {
                if (onStar != null) StarToggle(w.star, onStar, Modifier.align(Alignment.TopEnd).padding(6.dp))
                else if (w.star) MostWantedBadge(false, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            val status = if (w.got) tr("Got it") else reservedLabel(reserve?.state)
            if (status != null) {
                Surface(
                    color = cs.background, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text(
                        status, color = cs.onBackground, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    w.title, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurface
                )
                Text(
                    if (newP != null) money(newP, w.currency) else tr("No price"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, color = if (drop) Green else if (rise) Rise else cs.onSurface,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, tr("More"), tint = cs.onSurfaceVariant)
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
                            text = { Text(if (w.star) tr("Remove from most wanted") else tr("Most wanted")) },
                            onClick = { menu = false; Repo.replace(ctx, w.copy(star = !w.star)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (w.got) tr("Mark as not got") else tr("Mark as got")) },
                            onClick = { menu = false; Repo.replace(ctx, w.copy(got = !w.got)) }
                        )
                    }
                    DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = { menu = false; DeleteFlow.request(ctx, w) })
                }
            }
        }
    }
}

/** Images-only tile: the picture fills the square and the price sits in a tag on top of it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageTile(
    w: Wish, onLongPress: (() -> Unit)? = null, status: String? = null, dimmed: Boolean = false, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .background(if (w.image.isEmpty()) cs.primaryContainer else Color.White)
            .alpha(if (w.got || dimmed) 0.55f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        if (w.image.isNotEmpty()) {
            AsyncImage(
                model = w.image, contentDescription = w.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else Text("🎁", fontSize = 32.sp)
        Surface(
            color = if (drop) Green else if (rise) Rise else Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        ) {
            Text(
                if (newP != null) money(newP, w.currency) else tr("No price"),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (w.star && !w.got) MostWantedBadge(false, Modifier.align(Alignment.TopEnd).padding(6.dp))
        val label = if (w.got) tr("Got it") else status
        if (label != null) {
            Surface(
                color = cs.background, shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Text(
                    label, color = cs.onBackground, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** Opens when you tap an images-only tile: the details, plus everything the cards have as buttons. */
@Composable
fun WishDetailDialog(w: Wish, onEdit: () -> Unit, reserve: Reserving? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val starFx = rememberStarFx()
    val ui = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val label = listOf(host(w.url), w.category.takeIf { it.isNotBlank() })
        .filter { !it.isNullOrBlank() }.joinToString(" · ")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(w.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (w.image.isNotEmpty()) {
                    AsyncImage(
                        model = w.image, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.White)
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val p = w.price
                    val o = w.oldPrice
                    Text(
                        if (p != null) money(p, w.currency) else tr("No price"),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
                    )
                    if (p != null && o != null && (o > p || priceRose(w))) {
                        Text(
                            money(o, w.currency), style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.LineThrough, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (reserve == null) Box(Modifier.starSparkles(starFx)) {
                        IconButton(onClick = { if (!w.star) starFx.play(); Repo.replace(ctx, w.copy(star = !w.star)) }) {
                            StarIcon(
                                w.star, if (w.star) StarGold else cs.onSurfaceVariant, 26.dp,
                                Modifier.starPulse(starFx)
                                    .semantics { contentDescription = if (w.star) tr("Remove from most wanted") else tr("Mark as most wanted") }
                            )
                        }
                    }
                }
                if (label.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                if (w.desc.isNotBlank()) {
                    Text(w.desc, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                if (w.size.isNotBlank()) SizeTag(w.size)
                if (w.note.isNotBlank()) {
                    Text(tr("Note: %s", w.note), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                }
                if (w.by.isNotBlank()) {
                    Text(tr("Added by %s", w.by), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                reservedLabel(reserve?.state)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = cs.primary) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onClose(); onEdit() }) { Text(tr("Edit")) }
                    if (reserve != null) {
                        when (reserve.state) {
                            true -> TextButton(onClick = { reserve.set(false); onClose() }) { Text(tr("Undo reservation")) }
                            null -> TextButton(onClick = { reserve.set(true); onClose() }) { Text(tr("Reserve")) }
                            else -> {}
                        }
                    } else TextButton(onClick = { Repo.replace(ctx, w.copy(got = !w.got)); onClose() }) {
                        Text(if (w.got) tr("Not got") else tr("Got it"))
                    }
                    TextButton(onClick = { DeleteFlow.request(ctx, w); onClose() }) { Text(tr("Delete")) }
                }
            }
        },
        confirmButton = {
            if (w.url.isNotBlank()) {
                TextButton(onClick = { try { ui.openUri(w.url) } catch (e: Exception) { } }) { Text(tr("Open in store")) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Close")) } }
    )
}

/** One wish. [actions] are the buttons at the bottom right, which differ for the owner and for friends. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WishCard(
    w: Wish, badge: String? = null, dim: Boolean = false, onLongPress: (() -> Unit)? = null,
    onStar: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    val ui = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    val label = listOf(host(w.url), w.category.takeIf { it.isNotBlank() && it != "All" })
        .filter { !it.isNullOrBlank() }.joinToString(" · ")

    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        )
    ) {
        Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(if (w.image.isEmpty()) cs.primaryContainer else Color.White)
                .combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            if (w.image.isNotEmpty()) {
                AsyncImage(
                    model = w.image, contentDescription = w.title,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            } else Text("🎁", fontSize = 48.sp)
            if ((drop || rise) && newP != null && oldP != null) {
                Surface(
                    color = if (rise) Rise else Green, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Text(
                        if (rise) "↑ +${Math.round((newP / oldP - 1) * 100)}%" else "↓ " + tr("%d%% off", Math.round((1 - newP / oldP) * 100)),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            // Top right: the star (the owner can tap it), and below it the status such as "Got it".
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StarCorner(w, onStar)
                if (badge != null) {
                    Surface(color = cs.background, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            badge, color = cs.onBackground, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Text(
                w.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurface
            )
            if (w.desc.isNotBlank()) {
                Text(
                    w.desc, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant
                )
            }
            if (w.size.isNotBlank()) SizeTag(w.size, Modifier.padding(top = 4.dp))
            if (w.note.isNotBlank()) {
                Text(
                    tr("Note: %s", w.note), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, color = cs.onSurface
                )
            }
            if (w.by.isNotBlank()) {
                Text(tr("Added by %s", w.by), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (newP != null) money(newP, w.currency) else tr("No price"),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = if (drop) Green else if (rise) Rise else cs.onSurface
                    )
                    if ((drop || rise) && oldP != null) {
                        Text(
                            money(oldP, w.currency), style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.LineThrough, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                actions()
            }
        }
    }
}

/** Adds a new wish, or edits [edit] when it is not null. */
@Composable
fun WishDialog(edit: Wish?, initialUrl: String, chooseList: Boolean = false, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Which wishlist was open when this dialog started. A wish shared in from the browser can be saved to another one.
    val startList = remember { Shared.active }
    val targets = saveTargets(Repo.myName, Shared.lists)
    var url by remember { mutableStateOf(edit?.url ?: initialUrl) }
    var title by remember { mutableStateOf(edit?.title ?: "") }
    var price by remember { mutableStateOf(edit?.price?.let { "%.2f".format(Locale.US, it) } ?: "") }
    var desc by remember { mutableStateOf(edit?.desc ?: "") }
    var image by remember { mutableStateOf(edit?.image ?: "") }
    var cur by remember { mutableStateOf(edit?.currency ?: "USD") }
    var cat by remember { mutableStateOf(if (edit != null) edit.category else (Repo.cats.firstOrNull() ?: "")) }
    var note by remember { mutableStateOf(edit?.note ?: "") }
    var sizeSel by remember { mutableStateOf(edit?.size ?: "") }
    var star by remember { mutableStateOf(edit?.star ?: false) }
    val starFx = rememberStarFx()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var newCatDlg by remember { mutableStateOf(false) }
    var catAction by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(Unit) { if (edit == null && initialUrl.isNotEmpty()) go(initialUrl) }

    AlertDialog(
        onDismissRequest = { close() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (edit == null) tr("Add a wish") else tr("Edit wish")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chooseList && edit == null && targets.size > 1) {
                    SaveToField(
                        targets = targets, selected = Shared.active,
                        onPick = { code ->
                            if (code == null) Shared.close() else Shared.open(ctx, code)
                            // Every wishlist has its own categories.
                            cat = Repo.cats.firstOrNull() ?: ""
                        },
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
                    label = { Text(tr("Product link (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { go(url) }, enabled = !busy && url.isNotBlank()) {
                    Text(if (busy) tr("Reading…") else tr("Fetch details"))
                }
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ImageBlock(image, onPick = { pickImage() }, onRemove = {
                    if (picked.isNotEmpty() && picked == image) { ImageStore.delete(ctx, picked); picked = "" }
                    image = ""
                })
                OutlinedTextField(title, { title = it }, label = { Text(tr("Title")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    price, { price = it }, label = { Text(tr("Price (%s)", cur)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    desc, { desc = it }, label = { Text(tr("Description")) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                SizeField(sizeSel, { sizeSel = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    note, { note = it }, label = { Text(tr("Note for givers (colour, details, …)")) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                // No "most wanted" on a shared list: nobody can say that for the person the gifts are for.
                if (Shared.active == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (!star) starFx.play(); star = !star }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StarIcon(star, if (star) StarGold else MaterialTheme.colorScheme.onSurfaceVariant, 24.dp, Modifier.starPulse(starFx))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Most wanted"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("Shown with a star, so givers look here first"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = star, onCheckedChange = { star = it })
                }
                Text(tr("Category"), style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Same chip as the categories, so they line up exactly.
                    CatChip(label = tr("New"), selected = false, onClick = { newCatDlg = true }, icon = Icons.Default.Add)
                    Repo.cats.forEach { c ->
                        CatChip(
                            label = c, selected = c == cat,
                            onClick = { cat = if (cat == c) "" else c },
                            onLongClick = { catAction = c }
                        )
                    }
                }
                if (Repo.cats.isNotEmpty()) {
                    Text(
                        tr("Long press a category to edit or delete it."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
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
                                star = star && Shared.active == null, note = note.trim(), size = sizeSel, by = Repo.authorName
                            )
                        )
                    } else {
                        Repo.replace(
                            ctx,
                            edit.copy(
                                url = u, title = title.trim(), desc = desc, image = image, price = p,
                                oldPrice = if (p == edit.price) edit.oldPrice else null,
                                currency = cur, category = cat, star = star && Shared.active == null, note = note.trim(), size = sizeSel
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
    catAction?.let { ca ->
        CategoryActionsDialog(
            name = ca,
            onRenamed = { n -> if (cat == ca) cat = n },
            onDeleted = { if (cat == ca) cat = "" },
            onClose = { catAction = null }
        )
    }
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
            Icon(Icons.Default.Add, null, Modifier.size(32.dp), tint = cs.onSurfaceVariant)
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
                Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}
