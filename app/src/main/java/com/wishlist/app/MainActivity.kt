package com.wishlist.app

import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
        // A notification about several price changes: "Price changes" (owner, 7 November 2026).
        const val OPEN_PRICES = "open_prices"
        /** A friend request (or an approval) alert: open the Friends tab. */
        const val OPEN_FRIENDS = "open_friends"
        /** A new message alert: the friend (profile id) whose conversation to open. */
        const val OPEN_CHAT = "open_chat"
        /** A birthday alert: the friend (profile id) whose page to open. */
        const val OPEN_FRIEND = "open_friend"
        /** A wishlist group alert (2.108): the group (or invite) to open, or "" for just the app. */
        const val OPEN_GROUP = "open_group"
        /** A gift exchange notification (2.129): the exchange's id, or "" for the list of exchanges. */
        const val OPEN_EXCHANGE = "open_exchange"
        /** A "gift you reserved is no longer on …" alert (2.126): where to go, as on the Notifications page ("c:"/"s:"). */
        const val OPEN_NOTE = "open_note"
        /** A bundle of news that lead to different places (7 November 2026): the Notifications page. */
        const val OPEN_NOTES = "open_notes"
        /** "Still buying …?" answered from the notification's buttons (2.142): the question, the answer, the notification. */
        const val ASK_ID = "ask_id"
        const val ASK_YES = "ask_yes"
        const val ASK_NID = "ask_nid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.load(this)
        Show.load(this)
        Friends.load(this)
        handle(intent)
        addOnNewIntentListener { handle(it) }
        Alerts.ensureChannel(this)
        Alerts.schedule(this, Repo.intervalHours, false)
        Alerts.scheduleBin(this)
        Sync.push()
        // Edge to edge (owner, 2.102): the app draws behind the system bars and handles them itself. Before, Android
        // shrank the whole window for the keyboard ("adjustResize"), also for a keyboard that was still closing in
        // another app when you came back, so the bottom bar jumped up for a moment. Now only what needs it moves.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WishTheme {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    App(sharedUrl, sharedCode, sharedInvite, binTarget, { binTarget = null }) {
                        sharedUrl = null; sharedCode = null; sharedInvite = null
                    }
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

    /** A friend link: the Friends tab with "Send … a friend request?" (owner, 2.139). */
    private fun openFriendLink(code: String, name: String) {
        FriendsNav.reset()
        FriendsNav.addName = name
        FriendsNav.addCode = code
        FriendsNav.openTab = true
    }

    /**
     * A web link of ours (App Links, owner 7 November 2026): an invite (?j=), a friends' link (?c=), a group (?g=, or a
     * browser member's My link), a gift exchange (?x=) or a friend link (/friend/?f=). False when it is none of these.
     */
    private fun openWebLink(u: String): Boolean {
        Groups.parseMe(u)?.let { GroupNav.takeover = it; GroupNav.request++; return true }
        Groups.parseLink(u)?.let { GroupNav.openInvite(null, it); return true }
        Exchanges.parseLink(u)?.let { ExchangeNav.openInvite(null, it); return true }
        if (u.contains("/friend", ignoreCase = true)) Friends.parseFriendLink(u)?.let { (c, n) -> openFriendLink(c, n); return true }
        if (u.uppercase().contains("J=")) Sync.parseInvite(u)?.let { sharedInvite = it; return true }
        Regex("[?&]c=([A-Za-z0-9-]{8,11})(?![A-Za-z0-9])", RegexOption.IGNORE_CASE).find(u)?.let { m ->
            Sync.parseCode(m.groupValues[1])?.let { sharedCode = it; return true }
        }
        return false
    }

    private fun handle(i: Intent?) {
        // A tapped push notification: its line on the Notifications page is seen already (owner, 2.126: no red dot).
        if (i != null && i.hasExtra(ASK_ID)) {
            Asks.answerFromNotification(this, i.getLongExtra(ASK_ID, 0L), i.getBooleanExtra(ASK_YES, false), i.getIntExtra(ASK_NID, 0))
            i.removeExtra(ASK_ID)
            return
        }
        if (i != null && i.hasExtra(Alerts.NOTE_KIND)) {
            val kind = i.getStringExtra(Alerts.NOTE_KIND).orEmpty()
            // "X is free. Reserve it now?" / "Your reservation of X has ended" (2.142): the list opens on that wish.
            val list = i.getStringExtra(Alerts.NOTE_LIST).orEmpty()
            val wish = i.getLongExtra(Alerts.NOTE_ID, 0L)
            if ((kind == "ask_free" || kind == "ask_ended" || kind == "ask_yes" || kind == "ask_sent") && wish > 0L && list.length > 2)
                AskNav.focus = Triple(list.substring(2), wish, kind == "ask_free")
            if (kind.isNotEmpty()) Notifications.markRead(this, kind, i.getStringExtra(Alerts.NOTE_LIST).orEmpty(), i.getLongExtra(Alerts.NOTE_ID, 0L))
            i.removeExtra(Alerts.NOTE_KIND)
        }
        if (i != null && i.getBooleanExtra(OPEN_NOTES, false)) {
            NotesNav.open = true
            i.removeExtra(OPEN_NOTES)
            return
        }
        if (i != null && i.hasExtra(OPEN_NOTE)) {
            NoteNav.key = i.getStringExtra(OPEN_NOTE)
            i.removeExtra(OPEN_NOTE)
            return
        }
        if (i != null && i.getBooleanExtra(OPEN_PRICES, false)) {
            PricesNav.open = true
            i.removeExtra(OPEN_PRICES)
            return
        }
        if (i != null && i.hasExtra(HISTORY_ID)) {
            HistoryNav.open = i.getLongExtra(HISTORY_ID, 0L)
            i.removeExtra(HISTORY_ID)
            return
        }
        if (i != null && i.hasExtra(OPEN_CHAT)) {
            ChatNav.peer = i.getStringExtra(OPEN_CHAT)
            i.removeExtra(OPEN_CHAT)
            ChatNav.openTab = true
            return
        }
        if (i != null && i.hasExtra(OPEN_FRIEND)) {
            val id = i.getStringExtra(OPEN_FRIEND)
            i.removeExtra(OPEN_FRIEND)
            FriendsNav.reset()
            FriendsNav.person = id
            FriendsNav.openTab = true
            return
        }
        if (i != null && i.hasExtra(OPEN_GROUP)) {
            val gid = i.getStringExtra(OPEN_GROUP).orEmpty()
            i.removeExtra(OPEN_GROUP)
            if (gid.isNotEmpty()) GroupNav.openInvite(gid, null)
            return
        }
        if (i != null && i.hasExtra(OPEN_EXCHANGE)) {
            val gx = i.getStringExtra(OPEN_EXCHANGE).orEmpty()
            i.removeExtra(OPEN_EXCHANGE)
            ExchangeNav.openInvite(gx.ifEmpty { null }, null)
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
            val d = i.dataString.orEmpty()
            // App Links (2.142): one of our web links opened the app. A link it can't read just opens the app.
            if (d.startsWith("https://", ignoreCase = true)) { openWebLink(d); return }
            // Wishlist groups (2.108): a group link (wishlist://group?g=KEY) or a browser member's "My link".
            Groups.parseMe(d)?.let { GroupNav.takeover = it; GroupNav.request++; return }
            Groups.parseLink(d)?.let { GroupNav.openInvite(null, it); return }
            // Gift exchange (2.129): wishlist://exchange?x=KEY.
            Exchanges.parseLink(d)?.let { ExchangeNav.openInvite(null, it); return }
            // A friend link (2.139): wishlist://friend?f=CODE&n=Name.
            if (d.startsWith("wishlist://friend")) Friends.parseFriendLink(d)?.let { (c, n) -> openFriendLink(c, n); return }
            Sync.parseInvite(d)?.let { sharedInvite = it }
            return
        }
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            // A group link or "My link" shared into the app (2.108).
            Groups.parseMe(t)?.let { GroupNav.takeover = it; GroupNav.request++; return }
            Groups.parseLink(t)?.let { GroupNav.openInvite(null, it); return }
            Exchanges.parseLink(t)?.let { ExchangeNav.openInvite(null, it); return }
            if (t.contains("/friend/?f=")) Friends.parseFriendLink(t)?.let { (c, n) -> openFriendLink(c, n); return }
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
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, dot: Boolean = false, snow: Boolean = false, onClick: () -> Unit) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    // The chosen item looks like the style's chosen chip (2.97): Quiet a soft tinted pill, the others their own frame.
    val shape = look.chipShape
    val framed = selected && look.design != "quiet"
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
            selectedContainerColor = if (framed) Color.Transparent else cs.primaryContainer,
            selectedTextColor = if (framed) look.chipContent(true) else cs.onPrimaryContainer,
            selectedIconColor = if (framed) look.chipContent(true) else cs.onPrimaryContainer,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = cs.onSurface, unselectedIconColor = cs.onSurfaceVariant
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).then(if (framed) Modifier.lookChip(look, true) else Modifier)
            // A very light snowfall inside the item, only during the gift exchange season (owner, 7 November 2026).
            .then(if (snow) Modifier.clip(shape).snowfall(look.dark) else Modifier)
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
    // What "Find a gift" already read from the shop page on screen (2.114), so the form needn't load it again.
    var prefillMeta by remember { mutableStateOf<Meta?>(null) }
    var fromShop by remember { mutableStateOf(false) }
    var catDlg by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    // The bin (menu > Bin): deleted wishes of every list, for 30 days.
    var binOpen by remember { mutableStateOf(false) }
    // "Received gifts" and "What did you get?" for one of your lists (2.128).
    var receivedOpen by remember { mutableStateOf(false) }
    var receivedAsk by remember { mutableStateOf<String?>(null) }
    // "Gifts I reserved" (menu, 2.129, Reserved.kt).
    var reservedOpen by remember { mutableStateOf(false) }
    // Gift exchange (menu, 2.129, ExchangeUi.kt): your exchanges, or one of them (ExchangeNav.open).
    var exchangesOpen by remember { mutableStateOf(false) }
    var newExchange by remember { mutableStateOf(false) }
    // The settings of the open list (its ⚙), apart from the app's Settings in the menu.
    var listSettings by remember { mutableStateOf(false) }
    var friendCode by remember { mutableStateOf<String?>(null) }
    var newShared by remember { mutableStateOf(false) }
    // The dashboard (Hub.kt) is the start screen; tapping a list opens it, Back returns to the dashboard.
    var hub by remember { mutableStateOf(true) }
    var newMine by remember { mutableStateOf(false) }
    // The bottom bar's pages and actions (BottomBar.kt).
    // "Price changes" (owner, 7 November 2026: no longer a tab; a wish's price page has "All price changes").
    var pricesOpen by remember { mutableStateOf(false) }
    // Discover, the shops (bottom bar, owner 7 November 2026).
    var discoverOpen by remember { mutableStateOf(false) }
    // Notifications (menu, above Bin): price drops and rises, with a red dot while there is something new.
    var notesOpen by remember { mutableStateOf(false) }
    var friendsOpen by remember { mutableStateOf(false) }
    // Messages (bottom bar, 2.98): the conversations, or one conversation (ChatNav.peer).
    var messagesOpen by remember { mutableStateOf(false) }
    // Your profile (menu > Profile, or your picture on Friends): photo, name and friend code (FriendsUi.kt).
    var profileOpen by remember { mutableStateOf(false) }
    // A wishlist group on screen (2.108, GroupsUi.kt), by its id.
    var groupOpen by remember { mutableStateOf<String?>(null) }
    var newWishGroup by remember { mutableStateOf(false) }

    /**
     * Closes every screen that lies on top of Home or a list (settings, bin, a friend's link list, a list's settings,
     * Price watch, Friends, Notifications, Profile). One place, so none is forgotten (2.95: these were 30 hand-written
     * copies, and a forgotten one kept Profile on screen when Notifications was opened from the menu).
     */
    fun closeAll() {
        settings = false; binOpen = false; friendCode = null; listSettings = false
        pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; messagesOpen = false
        groupOpen = null; exchangesOpen = false; discoverOpen = false
    }
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
        closeAll(); hub = false
    }

    /** Opens a group wishlist. */
    fun openGroup(code: String) {
        Shared.open(ctx, code)
        closeAll(); hub = false
    }

    fun openFriend(code: String) {
        // A link to one of your own wishlists opens it (owner, 7 November 2026: App Links open the app).
        Repo.allMine(ctx).firstOrNull { it.code == code }?.let { openMine(it.id); return }
        if (code == Repo.shareCode) {
            scope.launch { snack.showToast(tr("That’s your own list. Friends reserve from it, and you can’t peek.")) }
            return
        }
        // A list of one of your friends (Friends tab) opens there.
        if (FriendsNav.openByCode(code)) {
            closeAll()
            friendsOpen = true
            return
        }
        if (Repo.friends.any { it.code == code }) {
            closeAll(); friendCode = code
            return
        }
        scope.launch {
            try {
                val r = Cloud.fetchList(code)
                if (r == null) {
                    snack.showToast(tr("No list found for that code."))
                } else {
                    Repo.addFriend(ctx, Friend(code, r.name))
                    closeAll(); friendCode = code
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
            group != null -> GroupNav.openGroup(group.id)
            exchange != null -> ExchangeNav.openExchange(exchange.id)
            else -> openFriend(code)
        }
    }

    /**
     * Opens where a notification happened (the Notifications page, or a tapped push): the list the wish is on, a
     * friend's list, Friends, that friend, or a group invitation.
     */
    fun openNote(key: String) {
        notesOpen = false; profileOpen = false
        when {
            key.startsWith("m:") -> key.removePrefix("m:").takeIf { it in Repo.mineIds }?.let { openMine(it) }
            key.startsWith("s:") -> {
                val code = key.removePrefix("s:")
                if (Shared.lists.any { it.code == code }) openGroup(code)
                else scope.launch { snack.showToast(tr("That collab wishlist isn’t on this phone any more.")) }
            }
            key.startsWith("c:") -> openListByCode(key.removePrefix("c:"))
            // A gift list invitation (2.142).
            key.startsWith("i:") -> GiftInviteNav.open = key.removePrefix("i:")
            // A "Still buying …?" question (2.142): answered in Gifts I reserved.
            key == "v:" -> { closeAll(); reservedOpen = true }
            key.startsWith("r:") -> { FriendsNav.reset(); friendsOpen = true }
            key.startsWith("p:") -> { FriendsNav.reset(); FriendsNav.person = key.removePrefix("p:"); friendsOpen = true }
            key.startsWith("g:") -> GroupNav.openInvite(key.removePrefix("g:"), null)
            key.startsWith("q:") -> receivedAsk = key.removePrefix("q:")
            // Gift exchange (2.129): "e:<id>", or "e:" for the list of exchanges.
            key.startsWith("e:") -> ExchangeNav.openInvite(key.removePrefix("e:").ifEmpty { null }, null)
        }
    }
    // A tapped "gift you reserved is no longer on …" push (2.126) opens that list.
    LaunchedEffect(NoteNav.key) {
        NoteNav.key?.let { k -> NoteNav.key = null; openNote(k) }
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
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            closeAll(); prefill = sharedUrl; adding = true
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
            closeAll(); binOpen = true
            scope.launch { drawer.close() }
            onBinUsed()
        }
    }
    LaunchedEffect(sharedInvite) {
        if (sharedInvite != null) {
            closeAll(); joinDlg = sharedInvite
            onSharedUsed()
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && (settings || binOpen || friendCode != null)) {
        closeAll()
    }
    // Back from a shared wishlist goes to My wishlist.
    // Back from a list goes to the dashboard; back from Prices or Friends to where you were.
    BackHandler(enabled = !drawer.isOpen && !settings && !binOpen && friendCode == null && !listSettings && !hub && !pricesOpen && !discoverOpen && !friendsOpen && !notesOpen && !messagesOpen) {
        hub = true
    }
    BackHandler(enabled = !drawer.isOpen && !settings && !binOpen && friendCode == null && (pricesOpen || discoverOpen || friendsOpen || notesOpen || messagesOpen)) {
        // Back from Price changes goes to where it was opened (a list or Home); from a tab to where you were.
        if (pricesOpen) pricesOpen = false
        else { discoverOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; messagesOpen = false }
    }
    // A new message alert, or "Message" on a friend's page: Messages, at that conversation.
    LaunchedEffect(ChatNav.openTab) {
        if (ChatNav.openTab) {
            ChatNav.openTab = false
            closeAll()
            messagesOpen = true
            // Back from a friend's page opened from this chat: their page is cleared now that the chat shows.
            if (FriendsNav.fromChat != null) FriendsNav.reset()
        }
    }

    // Wishlist groups (2.108): open a group, or show its join screen (a link, an invite, a notification, a chat
    // message), or move a browser member's list into the app.
    LaunchedEffect(GroupNav.request) {
        val g = GroupNav.open
        if (g != null) {
            GroupNav.open = null
            closeAll(); hub = true; HubNav.page = null
            groupOpen = g
            // Home puts the group you opened last first in its section (2.110).
            Repo.touchRecent(ctx, "g:$g")
            scope.launch { drawer.close() }
        }
    }
    BackHandler(enabled = !drawer.isOpen && groupOpen != null && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen && !profileOpen) {
        groupOpen = null
    }

    // Gift exchange (2.129): a link, an invitation, a notification or the menu asked for it.
    LaunchedEffect(ExchangeNav.show) {
        if (ExchangeNav.show) {
            ExchangeNav.show = false
            closeAll(); hub = true; HubNav.page = null
            exchangesOpen = true
            scope.launch { drawer.close() }
        }
    }
    BackHandler(enabled = !drawer.isOpen && exchangesOpen && ExchangeNav.open == null && !settings && !binOpen && friendCode == null && !notesOpen && !profileOpen) {
        exchangesOpen = false; hub = true
    }

    // The bell at the top right (2.122): Notifications.
    LaunchedEffect(NotesNav.open) {
        if (NotesNav.open) {
            NotesNav.open = false
            closeAll(); notesOpen = true
            scope.launch { drawer.close() }
        }
    }
    // "All price changes" on a wish's price page, or a notification about several prices (owner, 7 November 2026).
    LaunchedEffect(PricesNav.open) {
        if (PricesNav.open) {
            PricesNav.open = false
            settings = false; binOpen = false; notesOpen = false; profileOpen = false
            discoverOpen = false; friendsOpen = false; messagesOpen = false
            pricesOpen = true
            scope.launch { drawer.close() }
        }
    }
    // Back from Profile goes to where you were (Friends, when you came from your picture there).
    BackHandler(enabled = !drawer.isOpen && profileOpen) { profileOpen = false }
    // A friend request alert was tapped: the Friends tab.
    LaunchedEffect(FriendsNav.openTab) {
        if (FriendsNav.openTab) {
            FriendsNav.openTab = false
            closeAll()
            friendsOpen = true
        }
    }
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
        !profileOpen && !pricesOpen && !discoverOpen && !friendsOpen && !messagesOpen
    // Price changes has a back arrow (owner, 7 November 2026: opened from a wish, no longer a tab).
    val inPrices = pricesOpen && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen && !profileOpen
    // A wishlist group has a back arrow too.
    val inGroup = groupOpen != null && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen && !profileOpen &&
        !pricesOpen && !discoverOpen && !friendsOpen && !messagesOpen
    // An open conversation has a back arrow too, and no bottom bar.
    val inChat = messagesOpen && ChatNav.peer != null && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen && !profileOpen
    // An open gift exchange has a back arrow too (2.129).
    val inExchange = exchangesOpen && ExchangeNav.open != null && !settings && !binOpen && friendCode == null && !listSettings &&
        !notesOpen && !profileOpen && !pricesOpen && !discoverOpen && !friendsOpen && !messagesOpen
    // An open wishlist has a back arrow to Home instead of the menu button (owner, 2.110: "for some it's intuitive").
    val inList = !hub && !settings && !binOpen && friendCode == null && !listSettings && !notesOpen && !profileOpen &&
        !pricesOpen && !discoverOpen && !friendsOpen && !messagesOpen && groupOpen == null
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !inListSettings && !inThemer && !inFriend && !inOverview && !inChat && !inGroup && !inList && !inExchange && !inPrices,
        drawerContent = {
            val look = LocalLook.current
            ModalDrawerSheet(
                modifier = Modifier.width(sheetWidth).then(
                    // A line along the edge of the menu: thick ink (Neobrutalism, chaos) or thin neon (Cyberpunk).
                    if (look.design == "neo" || look.design == "chaos" || look.design == "cyber") Modifier.drawWithContent {
                        drawContent()
                        val w = when (look.design) { "chaos" -> 3.dp; "cyber" -> 1.dp; else -> 2.dp }.toPx()
                        drawRect(look.line, Offset(size.width - w, 0f), Size(w, size.height))
                    } else Modifier
                ),
                drawerContainerColor = MaterialTheme.colorScheme.background
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
                    // The menu holds no lists (owner, 2.129): Home and the Friends tab show them. Three groups with
                    // dividers and no headings (owner, 2.130): the core features, your things, then Bin and settings.
                    val elsewhere = settings || binOpen || friendCode != null || notesOpen || pricesOpen || discoverOpen || friendsOpen || profileOpen || messagesOpen || groupOpen != null || exchangesOpen
                    DrawerItem(tr("Home"), AppIcons.Home, hub && !elsewhere) {
                        HubNav.page = null
                        closeAll(); hub = true
                        scope.launch { drawer.close() }
                    }
                    // One "New list" (owner, 2.128): it opens the choice of My wishlist, Gift list or Group.
                    DrawerItem(tr("New list"), AppIcons.Add, false) {
                        createChoice = true
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("Gift exchange"), AppIcons.Exchange, exchangesOpen && !settings && !binOpen && !notesOpen && !profileOpen, dot = Exchanges.unread, snow = Snow.season()) {
                        ExchangeNav.open = null
                        closeAll(); hub = true; exchangesOpen = true
                        scope.launch { drawer.close() }
                    }
                    // Your things (owner, 2.130). Notifications is not in the menu: the bell on Home.
                    DrawerDivider()
                    DrawerItem(tr("Profile"), AppIcons.Person, profileOpen) {
                        closeAll()
                        profileOpen = true
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("Gifts I reserved"), AppIcons.Bag, reservedOpen) {
                        reservedOpen = true
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("Received gifts"), AppIcons.Gift, receivedOpen) {
                        receivedOpen = true
                        scope.launch { drawer.close() }
                    }
                    DrawerDivider()
                    DrawerItem(tr("Bin"), AppIcons.Delete, binOpen) {
                        closeAll(); binOpen = true
                        scope.launch { drawer.close() }
                    }
                    DrawerItem(tr("App settings"), AppIcons.Settings, settings) {
                        closeAll(); settings = true
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
        // While the keyboard is open the bottom bar steps aside and the screen above it makes room (edge to edge, 2.102).
        val imeOpen = WindowInsets.isImeVisible
        val showBar = !settings && !binOpen && fr == null && !listSettings && !notesOpen && !profileOpen && !inChat && !imeOpen
        Column(Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)) {
        // With the bottom bar, the screens above it don't leave room for the phone's own navigation bar again.
        Box(Modifier.weight(1f).imePadding().then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            when {
                profileOpen -> ProfileScreen(snack)
                settings -> SettingsScreen(snack, openMenu)
                binOpen -> BinScreen(snack)
                fr != null -> FriendScreen(fr, snack, openMenu, onRemoved = { friendCode = null })
                listSettings -> key(Shared.active, Repo.openMine) {
                    ListSettingsScreen(snack, onDeleted = { listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false; hub = true }) { listSettings = false; pricesOpen = false; friendsOpen = false; notesOpen = false; profileOpen = false }
                }
                notesOpen -> NotificationsScreen(snack) { key -> openNote(key) }
                pricesOpen -> PricesScreen(snack, onBack = { pricesOpen = false })
                discoverOpen -> DiscoverScreen(
                    snack, onProfile = { profileOpen = true },
                    onFound = { u, m -> addTarget = null; prefillMeta = m; fromShop = true; prefill = u; adding = true }
                )
                friendsOpen -> FriendsScreen(snack, onProfile = { profileOpen = true }, onOpenLink = { friendCode = it })
                messagesOpen -> MessagesScreen(snack, onProfile = { profileOpen = true })
                exchangesOpen -> ExchangesScreen(snack, onOpenMine = { openMine(it) }, onNew = { newExchange = true })
                groupOpen != null -> key(groupOpen) {
                    GroupScreen(groupOpen!!, snack, onBack = { groupOpen = null; hub = true }, onOpenMine = { openMine(it) })
                }
                hub -> HubScreen(
                    snack,
                    onOpenMine = { openMine(it) },
                    onOpenGroup = { openGroup(it) },
                    onNewMine = { newMine = true },
                    onNewGroup = { newShared = true },
                    onProfile = { profileOpen = true },
                    onAddTo = { t -> addTarget = t; prefill = ""; adding = true },
                    onOpenWishGroup = { GroupNav.openGroup(it) },
                    onNewWishGroup = { newWishGroup = true },
                    onFriends = { FriendsNav.reset(); closeAll(); friendsOpen = true },
                    onReserved = { reservedOpen = true },
                    onCreateList = { createChoice = true },
                    onAddWish = { addTarget = Repo.lastSaveTo; prefill = ""; adding = true },
                    onNewExchange = { newExchange = true }
                )
                else -> key(Shared.active, Repo.openMine) {
                    HomeScreen(
                        snack, openMenu,
                        onAdd = { prefill = ""; adding = true },
                        onNewCat = { catDlg = true },
                        onInvite = { inviteDlg = true },
                        onListSettings = { listSettings = true },
                        onBack = { HubNav.page = null; closeAll(); hub = true }
                    )
                }
            }
        }
        if (showBar) {
            AppBottomBar(
                current = when {
                    pricesOpen -> ""; discoverOpen -> "discover"; friendsOpen -> "friends"; messagesOpen -> "messages"
                    hub -> "home"; else -> ""
                },
                onHome = { HubNav.page = null; closeAll(); hub = true },
                // "Messages" (owner, 2.98) took the place of "Wishlists"; your wishlists are on Home and its number.
                onMessages = {
                    if (messagesOpen) ChatNav.peer = null
                    pricesOpen = false; discoverOpen = false; friendsOpen = false; messagesOpen = true
                },
                onAdd = {
                    val onAList = !hub && !pricesOpen && !discoverOpen && !friendsOpen && !messagesOpen
                    addTarget = if (onAList) (Shared.active ?: MyLists.saveKey(Repo.openMine, false)) else Repo.lastSaveTo
                    prefill = ""; adding = true
                },
                onDiscover = { discoverOpen = true; pricesOpen = false; friendsOpen = false; messagesOpen = false },
                onFriends = {
                    // Tapping Friends again goes back to its first page.
                    if (friendsOpen) FriendsNav.reset()
                    friendsOpen = true; pricesOpen = false; discoverOpen = false; messagesOpen = false
                },
                friendsDot = Friends.waiting > 0,
                messagesDot = Chat.unreadTotal > 0
            )
        }
        }
    }

    // The menu button lives above the menu so it can spin into an X and close it.
    if (!inListSettings && !inThemer && !inFriend && !inOverview && !inChat && !inGroup && !inList && !inExchange && !inPrices) Box(
        Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets).height(64.dp).padding(start = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        IconButton(
            onClick = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } },
            modifier = Modifier.semantics { contentDescription = if (menuOpen) tr("Close menu") else tr("Open menu") }
        ) {
            Box {
                AnimatedMenuIcon(menuOpen, MaterialTheme.colorScheme.onSurface)
                // (Until 2.122 a red dot here showed news in Notifications; now the bell at the top right does.)
            }
        }
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
    val needName = (joinDlg != null || GroupNav.joinLink != null || GroupNav.joinGroup != null || ExchangeNav.joinLink != null || ExchangeNav.joinGx != null) && !NameGate.hasName()
    LaunchedEffect(needName) { if (needName) NameGate.ensure {} }
    if (receivedOpen) ReceivedScreen { receivedOpen = false }
    // "Gifts I reserved" (2.129): tapping a list opens it where it lives (a friend, a group, a gift exchange, a gift list).
    if (reservedOpen) ReservedScreen(onClose = { reservedOpen = false }) { g ->
        reservedOpen = false
        if (g.gift) {
            if (Shared.lists.any { it.code == g.code }) openGroup(g.code)
        } else {
            val group = Groups.groups.firstOrNull { gr -> gr.members.any { it.list?.code == g.code } }
            val exchange = Exchanges.list.firstOrNull { it.me.giftee?.list?.code == g.code }
            when {
                group != null -> GroupNav.openGroup(group.id)
                exchange != null -> ExchangeNav.openExchange(exchange.id)
                else -> openFriend(g.code)
            }
        }
    }
    if (newExchange) NewExchangeDialog(
        onCreated = { gx -> newExchange = false; ExchangeNav.inviteNow = gx; ExchangeNav.openExchange(gx) },
        onClose = { newExchange = false }
    )
    if (!needName && (ExchangeNav.joinLink != null || ExchangeNav.joinGx != null)) key(ExchangeNav.joinLink, ExchangeNav.joinGx) {
        JoinExchangeDialog(
            ExchangeNav.joinLink, ExchangeNav.joinGx,
            onJoined = { gx -> ExchangeNav.joinLink = null; ExchangeNav.joinGx = null; ExchangeNav.openExchange(gx) },
            onClose = { ExchangeNav.joinLink = null; ExchangeNav.joinGx = null }
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
    if (createChoice) {
        CreateChoiceDialog(
            onMine = { createChoice = false; newMine = true },
            onGroup = { createChoice = false; newShared = true },
            onWishGroup = { createChoice = false; newWishGroup = true },
            onClose = { createChoice = false }
        )
    }
    if (catDlg) CategoryDialog { catDlg = false }
    if (newShared) {
        NewSharedDialog(
            onCreated = { code ->
                newShared = false
                // It appears under Group wishlists on the dashboard; the invite comes up so you can ask people in.
                Shared.open(ctx, code); closeAll()
                hub = true
                inviteDlg = true
            },
            onClose = { newShared = false }
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
    GiftInviteNav.open?.let { code ->
        key(code) {
            GiftInviteDialog(
                code,
                onJoined = { c -> GiftInviteNav.open = null; Notifications.markRead(ctx, "gl_invite", "i:$c", 0L); openGroup(c) },
                onClose = { GiftInviteNav.open = null }
            )
        }
    }
    if (newMine) {
        NewListDialog(
            onCreated = { id ->
                newMine = false
                // It appears under My wishlists on the dashboard.
                closeAll(); hub = true
                scope.launch { snack.showToast(tr("“%s” is ready", Repo.mineName(id))) }
            },
            onClose = { newMine = false }
        )
    }
    if (newWishGroup) {
        NewGroupDialog(
            onCreated = { gid ->
                newWishGroup = false
                GroupNav.inviteNow = gid
                GroupNav.openGroup(gid)
            },
            onClose = { newWishGroup = false }
        )
    }
    if (!needName && (GroupNav.joinLink != null || GroupNav.joinGroup != null)) key(GroupNav.joinLink, GroupNav.joinGroup) {
        JoinGroupDialog(
            GroupNav.joinLink, GroupNav.joinGroup,
            onJoined = { gid -> GroupNav.joinLink = null; GroupNav.joinGroup = null; GroupNav.openGroup(gid) },
            onClose = { GroupNav.joinLink = null; GroupNav.joinGroup = null }
        )
    }
    GroupNav.takeover?.let { me ->
        TakeoverDialog(
            me, snack,
            onDone = { id ->
                GroupNav.takeover = null
                if (id != null) openMine(id)
            },
            onClose = { GroupNav.takeover = null }
        )
    }
    if (welcome) WelcomeFlow { welcome = false }
    // Everyone has a name (owner, 2.135): asked once at the start when it's missing, and before sharing.
    LaunchedEffect(welcome) { if (!welcome) NameGate.askAtStart() }
    NameGate.Host()
    DeleteHost(snack)
    MoveHost(snack)
    PrivateHost(snack)
    OwnedHost(snack)
    if (askBackground) BackgroundQuestion()
    // The share card of one of your lists (owner, 7 November 2026; HomeShare.kt).
    ShareNav.list?.let { id -> key(id) { ListShareDialog(id) { ShareNav.list = null } } }
    // A "Price alert" notification (or its entry in Notifications): that wish's price history, the alert lit up.
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
    onListSettings: () -> Unit, onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var cat by remember { mutableStateOf("All") }
    var catMenu by remember { mutableStateOf(false) }
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
    // Selecting several wishes (owner, 7 November 2026): a long press on your own list ticks that wish; then taps tick
    // more, and the top bar offers Move to… and Delete. Back or ✕ stops. Collab wishlists keep their long-press menu.
    var picked by remember(Repo.openMine, Shared.active) { mutableStateOf(setOf<Long>()) }
    val startSelect: (Wish) -> Unit = { w ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        picked = setOf(w.id)
    }
    val selecting = picked.isNotEmpty() && Shared.active == null
    BackHandler(enabled = selecting) { picked = emptySet() }
    fun toggle(w: Wish) { picked = if (w.id in picked) picked - w.id else picked + w.id }
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
    // Home puts the list opened last first in its section (2.110).
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
    // A wish tapped on "All your wishes" or "Top wishes": scroll to it (showing every category if it was hidden by
    // the filter or a search) and let it glow (owner, 2.91).
    val gridState = rememberLazyGridState()
    // The item that holds the summary card and the filters: 1 with a cover photo above it, else 0 (see stickyTop).
    val stickyIndex = if (remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) != null }) 1 else 0
    val pinned by remember(stickyIndex) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > stickyIndex ||
                (gridState.firstVisibleItemIndex == stickyIndex && gridState.firstVisibleItemScrollOffset > 0)
        }
    }
    var stickyHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(WishFocus.target, sel, query) {
        val t = WishFocus.target ?: return@LaunchedEffect
        val i = list.indexOfFirst { it.id == t }
        if (i < 0) {
            if (sel != "All" || query.isNotEmpty()) {
                cat = "All"; query = ""; searching = false
            } else WishFocus.target = null
            return@LaunchedEffect
        }
        // The target is cleared only at the end: clearing it is a change of this effect's key, which would stop it
        // before the scroll and the glow (the bug in 2.91).
        delay(150)
        // Before the wishes: the cover photo (when there is one), then the summary card and filters (pinned at the
        // top once scrolled, 2.111: the wish must not hide under them). Only scrolls when the wish isn't fully in sight
        // (2.94: it also scrolled when it was, so the filters jumped away).
        val pre = stickyIndex + 1
        val info = gridState.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == i + pre }
        val inSight = seen != null && seen.offset.y >= info.viewportStartOffset + stickyHeight &&
            seen.offset.y + seen.size.height <= info.viewportEndOffset
        if (!inSight) gridState.animateScrollToItem(i + pre, -stickyHeight)
        WishFocus.glow(t)
        WishFocus.target = null
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            if (selecting) TopAppBar(
                title = { Text(if (picked.size == 1) tr("1 selected") else tr("%d selected", picked.size), fontWeight = look.titleWeight) },
                navigationIcon = { IconButton(onClick = { picked = emptySet() }) { Icon(AppIcons.Close, tr("Stop selecting")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    MoveBusy(Modifier.padding(end = 8.dp))
                    TextButton(onClick = { MoveFlow.ask(picked); picked = emptySet() }) { Text(tr("Move to…")) }
                    IconButton(onClick = {
                        val ws = Repo.wishes.filter { it.id in picked }
                        picked = emptySet()
                        DeleteFlow.requestMany(ctx, ws)
                    }) { Icon(AppIcons.Delete, tr("Delete selected")) }
                }
            ) else TopAppBar(
                title = {
                    // No "Owned by you" any more (owner, 2.135): friends see "Owned by Jorai", and a list is never
                    // called "My wishlist" when shared (NameGate).
                    ListTitle(Repo.listName, Repo.wishes.size)
                },
                // Back to Home (2.110, owner): the menu button gives way to this arrow on an open list.
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back to Home")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                actions = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
                        Icon(AppIcons.Search, if (searching) tr("Close search") else tr("Search"))
                    }
                    // The settings of this list. Refreshing is pulling down on the list.
                    IconButton(onClick = onListSettings) { Icon(AppIcons.Settings, tr("Wishlist settings")) }
                    IconButton(onClick = { if (inShared) NameGate.ensure(onInvite) else NameGate.ensureListName { ShareNav.list = Repo.openMine } }) {
                        Icon(AppIcons.Share, if (inShared) tr("Invite or share") else tr("Share wishlist"))
                    }
                }
            )
        },
        // No floating "Add wish" button any more (owner, 2.112): the + in the bottom bar does the same.
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        // Behind the list: the blobs, glows or dots of the design (nothing in Material 3).
        Column(Modifier.padding(pad).lookBackdrop(look)) {
            if (inShared && Shared.gone) SharedGoneNotice()
            if (searching) SearchField(query, { query = it }) { searching = false; query = "" }
            // The category and sort choices and the view buttons. Since 2.89 they scroll with the list, under the
            // cover photo (when chosen) and the summary card.
            // [live]: this copy may open the category menu (the list's own copy is hidden under the pinned one while
            // that one shows, and the menu must open only once).
            val filters: @Composable (Boolean) -> Unit = { live ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: what you see and in which order (category, sort). Right: how it looks (view buttons).
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f, fill = false)) {
                            // Plain text, no filled chip (owner, 2.130: a calmer top).
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).clickable { catMenu = true }
                                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    catLabel(sel), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp)
                                )
                                Icon(AppIcons.ArrowDropDown, tr("Choose category"))
                            }
                            DropdownMenu(expanded = catMenu && live, onDismissRequest = { catMenu = false }) {
                                (listOf("All") + Repo.cats).forEach { c ->
                                    val n = if (c == "All") Repo.wishes.size else Repo.wishes.count { it.category == c }
                                    // Editing categories is in the wish form's "Edit categories" (2.110), not a long press here.
                                    CatMenuRow(
                                        text = catLabel(c) + " ($n)", bold = c == sel,
                                        onClick = { cat = c; catMenu = false }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(tr("New category…")) },
                                    leadingIcon = { Icon(AppIcons.Add, null) },
                                    onClick = { catMenu = false; onNewCat() }
                                )
                            }
                        }
                        // Room between the category and the sort button (owner, 2.127: they touched).
                        Spacer(Modifier.width(8.dp))
                        SortButton(sortMode, withStar = !inShared, quiet = true) { Repo.setSort(ctx, it) }
                        Spacer(Modifier.width(8.dp))
                    }
                    // One icon for the view (owner, 2.130): it shows the view in use and opens Rows, Two per row, Large cards.
                    ViewMenuButton(view) { Repo.setView(ctx, it) }
                }
            }
            // The top that stays in sight while scrolling (owner, 2.111): the summary card (your own lists) and the
            // category, sort and view choices. The cover photo above it scrolls away.
            val hasCover = remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) != null }
            // "Viewed 23 times" (owner, 2.136): only your own lists that are online; read when the list opens and on
            // every pull to refresh. Offline or before supabase-features.sql: nothing is shown.
            var views by remember(Repo.openMine, Shared.active) { mutableIntStateOf(0) }
            LaunchedEffect(Repo.openMine, Shared.active, pull.isRefreshing) {
                if (inShared) return@LaunchedEffect
                val d = Repo.mine(ctx, Repo.openMine)
                val code = d.code ?: return@LaunchedEffect
                val token = d.token ?: return@LaunchedEffect
                views = try {
                    Cloud.rpc("wl_view_count", org.json.JSONObject().put("p_code", code).put("p_token", token)).trim().toIntOrNull() ?: 0
                } catch (e: Exception) { views }
            }
            // Reactions (2.142): your own lists show them (no reacting), gift lists let every giver react. On a gift list a
            // tapped "Reserved" label of someone else asks "Is it still being bought?" (2.142).
            var askBought by remember { mutableStateOf<Wish?>(null) }
            var reserveNow by remember { mutableStateOf<Wish?>(null) }
            val listCode = if (inShared) Shared.active else Repo.mine(ctx, Repo.openMine).code
            ReactionsLoader(listCode)
            val listDeco = listCode?.let { code ->
                WishDeco(code, canReact = inShared, onBadge = if (!inShared) null else ({ w ->
                    val c = ResCounts.of(code, w.id)
                    if (res[w.id] == false || (w.qty > 1 && c != null && c.full && c.my == 0)) askBought = w
                }))
            }
            askBought?.let { w ->
                AskDialog(Shared.active.orEmpty(), w, Occasions.ofOpen(ctx), onClose = { askBought = null }) { m -> scope.launch { snack.showToast(m) } }
            }
            // A tapped "X is free" or "Your reservation of X has ended" on a gift list (2.142): it opens on that wish.
            LaunchedEffect(AskNav.focus, Shared.active, res) {
                val (code, id, reserve) = AskNav.focus ?: return@LaunchedEffect
                if (!inShared || code != Shared.active) return@LaunchedEffect
                val w = Repo.wishes.firstOrNull { it.id == id && it.bin == 0L } ?: return@LaunchedEffect
                AskNav.focus = null
                WishFocus.target = id
                if (reserve && res[id] == null) reserveNow = w
            }
            reserveNow?.let { w -> ReserveNowDialog(w, onReserve = { Shared.reserve(w, true) }, onClose = { reserveNow = null }) }
            val stickyTop: @Composable (Boolean) -> Unit = { live ->
                // More room between the summary and the choices (owner, 2.130: calmer, without making things smaller).
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The occasion shows in the summary card (owner, 2.124); on a gift list, or with the summary
                    // switched off, the card shows only the occasion.
                    val occ = Occasions.line(Occasions.ofOpen(ctx))
                    val counts = !inShared && Show["show_summary"]
                    if (counts || occ != null) ListSummaryCard(Repo.wishes, occasion = occ, counts = counts, views = views)
                    filters(live)
                }
            }
            // Pull down on the list to refresh it: the shops are asked for their prices now (and a shared list is
            // fetched again), so price changes show. This took the place of the refresh button in the top bar.
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pull.nestedScrollConnection)) {
            if (list.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ListHeader(Repo.wishes, snack)
                    if (hasCover) Spacer(Modifier.height(10.dp))
                    stickyTop(true)
                    Text(
                        if (query.isNotBlank()) {
                            if (sel != "All") tr("No wishes match “%1\$s” in %2\$s.", query.trim(), catLabel(sel))
                            else tr("No wishes match “%s”.", query.trim())
                        } else tr("Nothing here yet. Tap +, or share a product from any shop to Wishlist."),
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val gap = if (view == "list") 16.dp else 12.dp
                androidx.compose.runtime.CompositionLocalProvider(LocalWishDeco provides listDeco) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (view == "grid") GridCells.Fixed(2) else GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), // no floating button to keep clear of any more (2.112)
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    if (hasCover) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { ListHeader(Repo.wishes, snack) }
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { stickyTop(!pinned) }
                    gridItems(list, key = { it.id }) { w ->
                        val r = res[w.id]
                        // A wish opened from "All your wishes" glows softly twice (WishFocus).
                        GlowBox(w.id, small = view == "grid") {
                        Selectable(selecting, w.id in picked, { toggle(w) }) {
                        when (view) {
                            "grid" -> CompactCard(
                                // Two per row: the ⋮ holds everything; a long press on your own list starts selecting.
                                w, onLongPress = if (inShared) null else ({ startSelect(w) }),
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                reserve = if (inShared) Reserving(r, reserveLabel(Shared.active.orEmpty(), w, r)) { on -> Shared.reserve(w, on) } else null,
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
                                onMove = if (inShared) null else ({ MoveFlow.ask(setOf(w.id)) })
                            ) { editing = w }
                            // The buttons of Rows and Large cards: Share, the eye (or Reserve on a gift list), Delete.
                            else -> {
                            // Rows has smaller icons than the large cards (owner, 2.127: bigger than the private badge,
                            // smaller than before); the tap area stays the same.
                            val ico = if (view == "rows") 20.dp else 24.dp
                            val actions: @Composable RowScope.() -> Unit = {
                                // The bin stays, the rest is in one ⋮ (owner, 2.135: calmer rows).
                                if (!inShared) WishMoreButton(w, look.cardMuted(w.id), ico) { editing = w }
                                else IconButton(onClick = { shareItem(ctx, w) }) {
                                    Icon(AppIcons.Share, tr("Share"), tint = look.cardMuted(w.id), modifier = Modifier.size(ico))
                                }
                                if (inShared) {
                                    // Reserving takes the place of "got it" on a shared list.
                                    if (r == true) {
                                        OutlinedButton(onClick = { Shared.reserve(w, false) }) { Text(tr("Undo")) }
                                    } else if (r == null) {
                                        Button(onClick = { Shared.reserve(w, true) }) { Text(tr("Reserve")) }
                                    }
                                }
                                IconButton(onClick = { DeleteFlow.request(ctx, w) }) {
                                    Icon(AppIcons.Delete, tr("Delete"), tint = look.cardMuted(w.id), modifier = Modifier.size(ico))
                                }
                            }
                            if (view == "rows") RowCard(
                                w,
                                badge = if (inShared) reserveLabel(Shared.active.orEmpty(), w, r) else if (w.got) tr("Owned") else null,
                                dim = if (inShared) r == false else w.got,
                                onLongPress = { if (inShared) openMenu(w) else startSelect(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
                                actions = actions
                            ) else WishCard(
                                w,
                                badge = if (inShared) reserveLabel(Shared.active.orEmpty(), w, r) else if (w.got) tr("Owned") else null,
                                dim = if (inShared) r == false else w.got,
                                onLongPress = { if (inShared) openMenu(w) else startSelect(w) },
                                onStar = if (inShared) null else ({ Repo.replace(ctx, w.copy(star = !w.star)) }),
                                onPrivate = if (inShared) null else ({ PrivateFlow.set(ctx, w, false) }),
                                actions = actions
                            )
                            }
                        }
                        }
                        }
                    }
                }
                }
            }
            // The pinned copy of the top, once the list's own copy starts to scroll away under it.
            if (pinned && list.isNotEmpty()) Box(
                Modifier.fillMaxWidth().background(cs.background).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .onSizeChanged { stickyHeight = it.height }
            ) { stickyTop(true) }
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
            onEdit = { menuId = null; editing = w },
            // Only collab wishlists still have this long-press menu (Edit); on your own lists a long press selects
            // (owner, 7 November 2026). Share and Private are on the card, Price history is on Price watch.
            onHistory = null,
            onShare = null,
            onOwned = if (inShared) null else ({ menuId = null; OwnedFlow.toggle(ctx, w) }),
            onClose = { menuId = null }
        )
    }
    historyId?.let { id -> Repo.wishes.firstOrNull { it.id == id } }?.let { w ->
        PriceHistoryDialog(w) { historyId = null }
    }
}

/** One of the three view buttons in the row above the wishes. */
@Composable
fun ViewButton(kind: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    // The classic themes show only the chosen one filled (as before); the designs frame both, as buttons.
    val framed = true
    Box(
        Modifier.size(40.dp)
            .then(if (framed) Modifier.lookChip(look, on = selected) else Modifier.clip(look.chipShape))
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ViewIcon(kind, look.chipContent(selected))
    }
}

/** ⋮ on a wish of your own list (owner, 2.135): Edit, Share, Private (with its tick), Mark as owned. The bin stays next to it. */
@Composable
fun WishMoreButton(w: Wish, tint: Color, ico: androidx.compose.ui.unit.Dp, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, tr("More"), tint = tint, modifier = Modifier.size(ico)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Edit")) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(tr("Share")) }, onClick = { menu = false; shareItem(ctx, w) })
            PrivateMenuItem(w.priv) { menu = false; togglePrivate(ctx, w) }
            DropdownMenuItem(
                text = { Text(if (w.got) tr("Mark as not owned") else tr("Mark as owned")) },
                onClick = { menu = false; OwnedFlow.toggle(ctx, w) }
            )
            // Owner, 7 November 2026: to another of your wishlists.
            DropdownMenuItem(text = { Text(tr("Move to…")) }, onClick = { menu = false; MoveFlow.ask(setOf(w.id)) })
        }
    }
}

/** The view in use as one icon; tapping it opens the three views with their names (owner, 2.130). */
@Composable
fun ViewMenuButton(view: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val names = listOf("rows" to tr("Rows"), "grid" to tr("Two per row"), "list" to tr("Large cards"))
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .semantics { contentDescription = tr("View: %s", names.firstOrNull { it.first == view }?.second ?: "") }
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) { ViewIcon(view, cs.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { (k, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (k == view) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { ViewIcon(k, if (k == view) cs.primary else cs.onSurfaceVariant) },
                    trailingIcon = if (k == view) ({ Icon(AppIcons.Check, null) }) else null,
                    onClick = { open = false; onPick(k) }
                )
            }
        }
    }
}

/** Tiny drawn icons that look like each view: Rows, Two per row (a 2x2 grid) and Large cards. */
@Composable
fun ViewIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color = tint, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u),
            cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
        )
        when (kind) {
            // Large cards: one big picture with a line of text under it.
            "list" -> { box(3f, 3f, 18f, 12f); box(3f, 17f, 18f, 2.5f); box(3f, 20.5f, 11f, 2.5f) }
            // Rows: a small square with two lines next to it, twice.
            "rows" -> {
                box(3f, 3f, 8f, 8f); box(13f, 4f, 8f, 2.5f); box(13f, 7.5f, 5f, 2.5f)
                box(3f, 13f, 8f, 8f); box(13f, 14f, 8f, 2.5f); box(13f, 17.5f, 5f, 2.5f)
            }
            else -> { box(3f, 3f, 8f, 8f); box(13f, 3f, 8f, 8f); box(3f, 13f, 8f, 8f); box(13f, 13f, 8f, 8f) }
        }
    }
}

/** Reserving a wish on a shared list. [state]: true = reserved on this phone, false = by someone else, null = still free. */
class Reserving(val state: Boolean?, val label: String? = null, val set: (Boolean) -> Unit)

/** "2×" and "Exactly this" / "Similar is fine" (owner, 2.128), as one short line, or "". */
fun wishTags(w: Wish): String = listOfNotNull(
    if (w.qty > 1) "${w.qty}×" else null,
    when (w.exact) { "exact" -> tr("Exactly this"); "similar" -> tr("Similar is fine"); else -> null }
).joinToString(" · ")

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
    onPrivate: (() -> Unit)? = null, emoji: String = "🎁", inert: Boolean = false,
    // Someone else's list (a Friend's, 7 November 2026): the menu only has Share and Reserve.
    readOnly: Boolean = false,
    // "Move to…" in the ⋮ menu (your own lists, owner 7 November 2026).
    onMove: (() -> Unit)? = null,
    onEdit: () -> Unit
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
            Modifier.padding(look.inset(small = true)).fillMaxWidth().aspectRatio(1f)
                .lookMat(look, look.pictureShape(w.id, small = true), look.pictureBg(w.id, w.image.isNotEmpty()), over = true)
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
                PhotoStage(w.image, w.label, small = true)
            } else PlaceholderIcon("gift", 36.dp)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (Show["show_badge"] && (drop || rise) && newP != null && oldP != null) {
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
            val status = if (w.got) tr("Owned") else reserve?.label ?: reservedLabel(reserve?.state)
            if (status != null) {
                TappableBadge(w, status, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        if (look.pictureLine > 0.dp) Box(Modifier.fillMaxWidth().height(look.pictureLine).background(look.line))
        Row(Modifier.padding(start = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            // The name and price lead to the shop page.
            Column(
                Modifier.weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(
                    enabled = w.url.isNotBlank() || onLongPress != null,
                    onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                    onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                    onLongClick = onLongPress
                )
            ) {
                Text(
                    w.label, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink
                )
                    // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                    wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                        Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                    }
                // The old price crossed out next to it after a change (owner, 2.126).
                PriceLine(w, MaterialTheme.typography.titleMedium, Modifier.padding(top = 2.dp, bottom = 6.dp))
                // No "Added on" here (owner, 2.100): too busy on a list; it stays in Price watch.
                // Reactions (2.142): only on lists that show them (LocalWishDeco).
                ReactionBar(w, Modifier.padding(bottom = 6.dp))
            }
            // The ⋮ lines up with the first line of the name, the bin under it (owner, 7 November 2026: Delete left the
            // menu). The 48 dp touch areas stay; only the icons move up next to the name.
            Column(Modifier.offset(y = (-6).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                IconButton(onClick = { if (!inert) menu = true }) {
                    Icon(AppIcons.MoreVert, tr("More"), tint = muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // ⋮ (owner, 2.113): Share, Edit, Private (with a tick), Mark as owned, Move to…; on a collab wishlist
                    // Reserve takes the place of Private/owned. No Top wish here: the star on the picture does that.
                    // Delete is the bin under the ⋮ (owner, 7 November 2026).
                    DropdownMenuItem(text = { Text(tr("Share")) }, onClick = { menu = false; shareItem(ctx, w) })
                    if (!readOnly) DropdownMenuItem(text = { Text(tr("Edit")) }, onClick = { menu = false; onEdit() })
                    if (reserve == null && !readOnly) PrivateMenuItem(w.priv) { menu = false; togglePrivate(ctx, w) }
                    if (reserve != null) {
                        when (reserve.state) {
                            true -> DropdownMenuItem(text = { Text(tr("Undo reservation")) }, onClick = { menu = false; reserve.set(false) })
                            null -> DropdownMenuItem(text = { Text(tr("Reserve")) }, onClick = { menu = false; reserve.set(true) })
                            else -> {}
                        }
                    } else if (!readOnly) {
                        DropdownMenuItem(
                            text = { Text(if (w.got) tr("Mark as not owned") else tr("Mark as owned")) },
                            onClick = { menu = false; OwnedFlow.toggle(ctx, w) }
                        )
                    }
                    if (onMove != null) DropdownMenuItem(text = { Text(tr("Move to…")) }, onClick = { menu = false; onMove() })
                }
            }
            if (!readOnly) IconButton(onClick = { if (!inert) DeleteFlow.request(ctx, w) }) {
                Icon(AppIcons.Delete, tr("Delete"), tint = muted)
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
            Modifier.padding(look.inset(small = false)).fillMaxWidth().aspectRatio(4f / 3f)
                .lookMat(look, look.pictureShape(w.id, small = false), look.pictureBg(w.id, w.image.isNotEmpty()), over = true)
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
                PhotoStage(w.image, w.label, small = false)
            } else PlaceholderIcon("gift", 48.dp)
            // Top left: the price change.
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (Show["show_badge"] && (drop || rise) && newP != null && oldP != null) {
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
                    TappableBadge(w, badge, small = false)
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
                    // Cyberpunk shows it as a small spaced-out line in capitals.
                    Text(
                        if (look.design == "cyber") label.uppercase() else label,
                        style = MaterialTheme.typography.labelMedium, color = muted,
                        letterSpacing = if (look.design == "cyber") 1.2.sp else TextUnit.Unspecified
                    )
                }
                Text(
                    w.label, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink
                )
                    // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                    wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                        Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                    }
                if (Show["show_desc"] && w.desc.isNotBlank()) {
                    Text(
                        w.desc, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = muted
                    )
                }
                if (w.note.isNotBlank()) {
                    Text(
                        tr("Note: %s", w.note), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, color = ink
                    )
                }
                if (w.by.isNotBlank()) {
                    Text(tr("Added by %s", w.by), style = MaterialTheme.typography.labelMedium, color = muted)
                }
                // No "Added on" here (owner, 2.100): too busy on a list; it stays in Price watch.
            }
            // The size label and the reactions share one row (owner, 7 November 2026; LocalWishDeco).
            ReactionBar(w, lead = if (w.size.isNotBlank()) ({ SizeTag(w.size) }) else null)
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

/**
 * "Rows" (owner, 2.126; the default view for new installs): the picture on the left (a third of the width, square),
 * on the right the shop and category, the name (two lines at most), the price with the old price crossed out after a
 * change, and below them the buttons ([actions]: Share, the eye, Delete; on a gift list Share, Reserve, Delete). The
 * Top wish star, Reserved / Owned and the private eye sit on the picture, as on the other views.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowCard(
    w: Wish, badge: String? = null, dim: Boolean = false, onStar: (() -> Unit)? = null, onPrivate: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit
) {
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val muted = look.cardMuted(w.id)
    val ui = LocalUriHandler.current
    var viewing by remember { mutableStateOf(false) }
    if (viewing) ImageViewer(w) { viewing = false }
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    val label = listOf(host(w.url), w.category.takeIf { it.isNotBlank() && it != "All" })
        .filter { !it.isNullOrBlank() }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f).then(
            if (onLongPress != null) Modifier.pointerInput(w.id) { detectTapGestures(onLongPress = { onLongPress() }) }
            else Modifier
        ).lookCard(look, w.id, look.cardShape(w.id, small = true)),
        verticalAlignment = Alignment.Top
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            Box(
                Modifier.padding(look.inset(small = true)).fillMaxWidth(0.36f).aspectRatio(1f)
                    .lookMat(look, look.pictureShape(w.id, small = true), look.pictureBg(w.id, w.image.isNotEmpty()), over = true)
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
                if (w.image.isNotEmpty()) PhotoStage(w.image, w.label, small = true) else PlaceholderIcon("gift", 32.dp)
                if (w.priv) PrivateBadge(Modifier.align(Alignment.BottomStart).padding(6.dp), size = 12.dp, onClick = onPrivate)
                Column(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!w.got) {
                        if (onStar != null) StarToggle(w.star, onStar, size = 28.dp)
                        else if (w.star) MostWantedBadge(false)
                    }
                    if (badge != null) TappableBadge(w, badge, small = true)
                }
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, top = 10.dp, end = 8.dp, bottom = 2.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(
                        enabled = w.url.isNotBlank() || onLongPress != null,
                        onClickLabel = if (w.url.isNotBlank()) tr("View in shop") else null,
                        onClick = { if (w.url.isNotBlank()) { try { ui.openUri(w.url) } catch (e: Exception) { } } },
                        onLongClick = onLongPress
                    )
                ) {
                    if (label.isNotEmpty()) Text(
                        if (look.design == "cyber") label.uppercase() else label,
                        style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(w.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (w.title.isBlank()) ink.copy(alpha = 0.55f) else ink)
                        // "2×" and "Exactly this" / "Similar is fine" (owner, 2.128).
                        wishTags(w).takeIf { it.isNotEmpty() }?.let { t ->
                            Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
                        }
                    PriceLine(w, MaterialTheme.typography.titleMedium, Modifier.padding(top = 2.dp))
                }
                // Reactions (2.142): only on lists that show them (LocalWishDeco).
                ReactionBar(w)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
        }
    }
}

/**
 * The price with, after a change, the old price crossed out next to it (owner, 2.126: also on Two per row and Rows,
 * like the large cards). Wraps to a second line when both don't fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceLine(w: Wish, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val ink = look.cardInk(w.id)
    val oldP = w.oldPrice
    val newP = w.price
    val drop = newP != null && oldP != null && oldP > newP
    val rise = priceRose(w)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        Text(
            if (newP != null) money(newP, w.currency) else tr("No price"),
            style = style, fontWeight = look.priceWeight, fontFamily = look.display, maxLines = 1,
            color = if (drop) look.drop else if (rise) look.rise else ink,
            modifier = Modifier.alignByBaseline()
        )
        if ((drop || rise) && oldP != null) Text(
            money(oldP, w.currency), style = MaterialTheme.typography.bodySmall, maxLines = 1,
            textDecoration = TextDecoration.LineThrough, color = look.cardMuted(w.id),
            modifier = Modifier.alignByBaseline()
        )
    }
}

/** Adds a new wish, or edits [edit] when it is not null. */
@Composable
fun WishDialog(
    edit: Wish?, initialUrl: String, chooseList: Boolean = false, startTarget: String? = null,
    // Already read from the page (Find a gift, 2.114): used instead of loading [initialUrl] again.
    initialMeta: Meta? = null,
    // Opened from a shop in Find a gift: saving tells the shop, which stays open (2.116).
    fromShop: Boolean = false,
    onClose: () -> Unit
) {
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
    var cur by remember { mutableStateOf(edit?.currency ?: localCurrency()) }
    // A new wish starts without a category: you pick one yourself if you want (2.64).
    var cat by remember { mutableStateOf(edit?.category ?: "") }
    var note by remember { mutableStateOf(edit?.note ?: "") }
    var sizeSel by remember { mutableStateOf(edit?.size ?: "") }
    // How many, and "Exactly this" / "Similar is fine" (owner, 2.128).
    var qty by remember { mutableStateOf(edit?.qty ?: 1) }
    var exact by remember { mutableStateOf(edit?.exact ?: "") }
    var star by remember { mutableStateOf(edit?.star ?: false) }
    // "Private wish": only on this phone, friends never see it (My wishlist only). Off unless chosen.
    var priv by remember { mutableStateOf(edit?.priv ?: false) }
    val starFx = rememberStarFx()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    // True after Save was tapped without a name: the name field turns red with a short message.
    var nameErr by remember { mutableStateOf(false) }
    var newCatDlg by remember { mutableStateOf(false) }
    var editCats by remember { mutableStateOf(false) }
    // A picture picked in this dialog but not saved yet. It is deleted again if you cancel or replace it.
    var picked by remember { mutableStateOf("") }

    // "+ New wishlist" from the Save to menu: the usual form, then this wish goes there (owner, 7 November 2026).
    var newListDlg by remember { mutableStateOf(false) }
    // Take photo / Choose photo, then a free crop (owner, 7 November 2026: "so the picture is perfect").
    var photoMenu by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<String?>(null) }
    fun imported(p: String?) {
        if (p == null) err = tr("Couldn’t use that picture.") else { cropping = p; err = "" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { imported(ImageStore.import(ctx, uri)) }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "wish_camera.jpg") }
    val cameraUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { imported(ImageStore.import(ctx, cameraUri)) }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun takePhoto() = try { camera.launch(cameraUri) } catch (e: Exception) { err = tr("Couldn’t open the camera.") }

    fun close() {
        if (picked.isNotEmpty()) ImageStore.delete(ctx, picked)
        // Cancelled after choosing another wishlist: go back to the one that was open.
        if (Shared.active != startList) {
            if (startList == null) Shared.close() else Shared.open(ctx, startList)
        }
        onClose()
    }

    // Fills the form from what was read from a page (a link, or the shop page on screen in Find a gift).
    fun useMeta(m: Meta?) {
        if (m == null) {
            err = tr("Couldn’t read that page. Fill in the details yourself.")
        } else if (m.blocked) {
            err = tr("Couldn’t read this shop’s page. Fill in the name and price yourself.")
        } else {
            // The shop's sales text no longer fills Description (owner, 2.137): it stays free for your own words.
            title = m.title; cur = m.currency
            // A picture you chose yourself is kept. Otherwise the image comes from the link.
            if (!ImageStore.isLocal(image)) image = if (m.image.isNotEmpty() || edit == null) m.image else image
            if (m.price != null || edit == null) price = m.price?.let { "%.2f".format(Locale.US, it) }.orEmpty()
            if (m.price == null) err = tr("No price found on that page. Add it to track price drops.")
        }
    }

    // The same product already on one of your lists (owner, 2.137): said under the link; saving still works.
    val dupOf = remember(url) {
        val u = cleanUrl(url)
        if (u == null || edit != null) null
        else Repo.allMine(ctx).firstOrNull { d -> d.wishes.any { w -> w.bin == 0L && cleanUrl(w.url) == u } }
            ?.let { if (it.name == "My wishlist") tr(it.name) else it.name }
    }
    // A product link on the clipboard (owner, 2.137): offered with one tap, never pasted by itself.
    val clip = remember {
        if (edit != null || initialUrl.isNotEmpty()) null else try {
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val c = cm.primaryClip
            val t = if (c != null && c.itemCount > 0) c.getItemAt(0).coerceToText(ctx)?.toString() else null
            t?.let { Regex("https?://\\S+").find(it)?.value }?.let { cleanUrl(it) }
        } catch (e: Exception) { null }
    }

    fun go(raw: String) {
        val u = cleanUrl(raw)
        if (u == null) { err = tr("Enter a full product link"); return }
        url = u; busy = true; err = ""
        scope.launch {
            val m = fetchMeta(u, ctx)
            busy = false
            useMeta(m)
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
        if (edit == null && initialUrl.isNotEmpty()) {
            if (initialMeta != null) { url = initialUrl; useMeta(initialMeta) } else go(initialUrl)
        }
    }

    AlertDialog(
        onDismissRequest = { close() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (edit == null) tr("Add a wish") else tr("Edit wish")) },
        text = {
            // One scrolling page with three headings (owner, 2.128): Wish, Details, Visibility. Nothing folded away.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormHeading(tr("Wish"))
                if (chooseList && edit == null) {
                    SaveToField(
                        targets = targets, selected = Shared.active ?: MyLists.saveKey(Repo.openMine, false),
                        onPick = { pickTarget(it) },
                        modifier = Modifier.fillMaxWidth(),
                        onNew = { newListDlg = true }
                    )
                }
                if (clip != null && url.isBlank()) {
                    val host = try { java.net.URI(clip).host.orEmpty().removePrefix("www.") } catch (e: Exception) { "" }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { go(clip) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📋", modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Paste the link you copied"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (host.isNotEmpty()) Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
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
                dupOf?.let { Text(tr("This item is already on “%s”", it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                // (No tip under the link any more: removed by the owner, 2.118.)
                Box {
                    ImageBlock(image, onPick = { photoMenu = true }, onRemove = {
                        if (picked.isNotEmpty() && picked == image) { ImageStore.delete(ctx, picked); picked = "" }
                        image = ""
                    })
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Take photo")) }, onClick = { photoMenu = false; takePhoto() })
                        DropdownMenuItem(text = { Text(tr("Choose photo")) }, onClick = { photoMenu = false; pickImage() })
                    }
                }
                // Name and price side by side: the price only needs room for "€9999,99", the name gets the rest and
                // stays on one line (a long name scrolls inside the field instead of taking three lines).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    // A name is needed only for a wish with a link (owner, 7 November 2026); without a link a name, a
                    // photo or the link itself is enough. Saving with too little marks the field red.
                    OutlinedTextField(
                        title, { title = it; if (it.isNotBlank()) nameErr = false },
                        label = { Text(if (url.isNotBlank()) tr("Name*") else tr("Name")) }, singleLine = true,
                        isError = nameErr,
                        supportingText = if (nameErr) ({
                            Text(if (url.isNotBlank()) tr("What are you wishing for?") else tr("Add a name, a photo or a link."))
                        }) else null,
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
                FormHeading(tr("Details"))
                // One line, however many categories there are (a row of chips got too long).
                CategoryField(cat, Repo.cats, onPick = { cat = it }, onNew = { newCatDlg = true }, onEdit = { editCats = true }, modifier = Modifier.fillMaxWidth())
                QuantityField(qty) { qty = it }
                ExactField(exact) { exact = it }
                SizeField(sizeSel, { sizeSel = it }, Modifier.fillMaxWidth())
                // No "Note for givers" field any more (owner, 2.137): Description is for your own words now. A note
                // written before stays with the wish and keeps showing.
                OutlinedTextField(
                    desc, { desc = it }, label = { Text(tr("Description")) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
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
                if (Shared.active == null) FormHeading(tr("Visibility"))
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank() && (url.isNotBlank() || image.isBlank())) { nameErr = true; return@TextButton }
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
                                priv = priv && Shared.active == null, qty = qty, exact = exact
                            )
                        )
                        if (chooseList) Repo.setLastSaveTo(ctx, Shared.active ?: MyLists.saveKey(Repo.openMine, false))
                        if (fromShop) ShopNav.saved(Repo.listName)
                    } else {
                        Repo.replace(
                            ctx,
                            edit.copy(
                                url = u, title = title.trim(), desc = desc, image = image, price = p,
                                oldPrice = if (p == edit.price) edit.oldPrice else null,
                                currency = cur, category = cat, star = star && Shared.active == null, note = note.trim(), size = sizeSel,
                                priv = priv && Shared.active == null, qty = qty, exact = exact
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
    // The free crop after taking or choosing a photo (owner, 7 November 2026). Cancel keeps the photo as it was taken.
    // Since 2.144 the same editor as the profile and cover photo (owner, 7 November 2026): a square frame, move and
    // pinch, turn, lighter or darker, and "Fit whole photo". What is in the frame becomes the wish's photo. The back
    // arrow drops the new photo (the wish keeps the picture it had).
    cropping?.let { src ->
        HeroEditor(
            src, HeroEdit(), wish = true,
            onSave = { chosen, e ->
                cropping = null
                scope.launch {
                    val out = renderWishPhoto(ctx, chosen, e) ?: chosen
                    if (out != chosen) ImageStore.delete(ctx, chosen)
                    if (src != chosen && src != out) ImageStore.delete(ctx, src)
                    if (picked.isNotEmpty() && picked != out) ImageStore.delete(ctx, picked)
                    picked = out; image = out; if (title.isBlank()) nameErr = false
                }
            },
            onCancel = { cropping = null; ImageStore.delete(ctx, src) }
        )
    }
    if (newListDlg) NewListDialog(
        onCreated = { id -> newListDlg = false; pickTarget(MyLists.saveKey(id, false)) },
        onClose = { newListDlg = false }
    )
    if (newCatDlg) CategoryDialog(onAdded = { cat = it }) { newCatDlg = false }
    // The picked category follows a rename there; a deleted one becomes "No category".
    if (editCats) EditCategoriesScreen(onRenamed = { o, n -> if (cat == o) cat = n }) { editCats = false; if (cat.isNotEmpty() && cat !in Repo.cats) cat = "" }
}

/**
 * The picture of a wish (owner, 7 November 2026: not too small, not overpowering): a 128 dp square tile on the left.
 * Empty: a dashed tile with a plus and "Add photo". Filled: the photo fills the tile (wish photos are square since
 * 2.144; a picture from a shop page sits whole on white), tap to change, ✕ to remove.
 */
@Composable
fun ImageBlock(image: String, onPick: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline
    val tile = Modifier.size(128.dp).clip(RoundedCornerShape(16.dp))
    if (image.isEmpty()) {
        Column(
            tile.background(cs.surfaceVariant)
                .drawBehind {
                    drawRoundRect(
                        color = line, cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    )
                }
                .semantics { contentDescription = tr("Add photo") }
                .clickable(onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(AppIcons.Add, null, Modifier.size(32.dp), tint = cs.onSurfaceVariant)
            Text(tr("Add photo"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
    } else {
        Box(
            tile.background(Color.White)
                .semantics { contentDescription = tr("Change photo") }
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            // ✕ in the corner: 32 dp to see, a 48 dp area to tap (docs/STYLE.md).
            Box(
                Modifier.align(Alignment.TopEnd).size(48.dp)
                    .semantics { contentDescription = tr("Remove photo") }
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}
