package com.wishlist.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/* The app's frame (see AppScreen.kt): the app-wide pop-ups. What opens them is in AppState (AppModel.kt). */

/** The pop-ups that can show over any screen (the add form, joining, the + menu, Import, the start-up questions…). */
@Composable
internal fun AppPopups(a: AppState, snack: SnackbarHostState, goStable: (Deep) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (a.adding) key(a.prefill) {
        // Every new wish can choose its wishlist (owner, 7 November 2026): inside a list that list is picked; a link
        // shared into the app starts on the list used last.
        val here = if (a.prefill.isEmpty() && !a.fromShop) (Shared.active ?: MyLists.saveKey(Repo.openMine, false)) else null
        WishDialog(null, a.prefill, chooseList = true, startTarget = a.addTarget ?: here, initialMeta = a.prefillMeta, fromShop = a.fromShop) {
            a.prefillMeta = null; a.fromShop = false
            a.adding = false; a.addTarget = null
        }
    }
    // Joining asks for your name first when it's missing (owner, 2.135).
    val needName = (a.joinDlg != null || a.joinGroupLink != null || a.joinGroupId != null || a.joinExchangeLink != null || a.joinExchangeId != null) && !NameGate.hasName()
    LaunchedEffect(needName) { if (needName) NameGate.ensure {} }
    if (a.account) AccountSheet(
        onClose = { a.account = false },
        // Back from these returns to the screen the sheet was opened on (ROADMAP Stage 1).
        onProfile = { a.account = false; a.nav.openOnTop(Route.PROFILE) },
        onReserved = { a.account = false; a.nav.openOnTop(Route.RESERVED) },
        onReceived = { a.account = false; a.nav.openOnTop(Route.RECEIVED) },
        onBin = { a.account = false; a.nav.openOnTop(Route.BIN) },
        onSettings = { a.account = false; a.nav.openOnTop(Route.SETTINGS) },
        onNotes = { a.account = false; a.nav.openOnTop(Route.NOTES) },
        onTheme = { a.account = false; a.nav.openOnTop(Route.THEME) }
    )
    if (a.plusMenu) PlusMenuSheet(
        onWish = { a.plusMenu = false; a.addWish(Repo.lastSaveTo) },
        onNew = { kind -> a.plusMenu = false; if (kind == "exchange") a.newExchange = true else a.plusNew = kind },
        onClose = { a.plusMenu = false },
        onImport = { a.plusMenu = false; a.importing = "" }
    )
    a.importing?.let { start -> key(start) { ImportSheet(start, onOpen = { a.openMine(it) }, onClose = { a.importing = null }) } }
    // Screenshot to wish: at the end one message, with "Open the list" when the wishes went to one list.
    a.shots?.let { d ->
        key(d) {
            ShotsFlow(d) { msg, route ->
                a.shots = null
                if (msg != null) scope.launch {
                    val r = snack.showToast(msg, if (route != null) tr("Open the list") else null)
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed && route != null) { if (a.openList(route)) { a.closeAll(); a.nav.openTab(Route.list(route)) } }
                }
            }
        }
    }
    // Photos and prices of imported wishes that are still waiting fill in (Import.kt), also after a restart.
    LaunchedEffect(Unit) { ImportFill.start(ctx) }
    when (a.plusNew) {
        // One "New wishlist" sheet (owner, 17 November 2026): My own / With others swap inside it.
        "mine", "collab" -> NewWishlistSheet(
            withOthers = a.plusNew == "collab",
            onCreatedMine = { id -> a.plusNew = null; scope.launch { snack.showToast(tr("“%s” is ready", Repo.mineName(id))) } },
            onCreatedShared = { code -> a.sharedMade(code) },
            onClose = { a.plusNew = null }
        )
        "group" -> NewGroupDialog(
            onCreated = { gid -> a.plusNew = null; goStable(Deep.GroupPage(gid, invite = true)) },
            onClose = { a.plusNew = null }
        )
    }
    if (a.newExchange) NewExchangeDialog(
        onCreated = { gx -> a.newExchange = false; a.openExchange(gx, invite = true) },
        onClose = { a.newExchange = false }
    )
    if (!needName && (a.joinExchangeLink != null || a.joinExchangeId != null)) key(a.joinExchangeLink, a.joinExchangeId) {
        JoinExchangeDialog(
            a.joinExchangeLink, a.joinExchangeId,
            onJoined = { gx -> a.joinExchangeLink = null; a.joinExchangeId = null; a.openExchange(gx) },
            onClose = { a.joinExchangeLink = null; a.joinExchangeId = null }
        )
    }
    a.receivedAsk?.let { id -> ReceivedDialog(id) { a.receivedAsk = null } }
    // "How many will you buy?" for a 2× wish on a gift list (2.128).
    Shared.askCount?.let { w ->
        val code = Shared.active.orEmpty()
        ReserveCountDialog(
            ResCounts.free(code, w), ResCounts.of(code, w.id)?.my ?: 1,
            onPick = { n -> a.reserveCount(w, n) },
            onClose = { a.reserveCount(w, null) }
        )
    }
    if (!needName) a.joinDlg?.let { inviteKey ->
        JoinDialog(
            key = inviteKey,
            onJoined = { code ->
                a.joinDlg = null
                a.openGroup(code)
            },
            onClose = { a.joinDlg = null }
        )
    }
    if (a.inviteDlg) SharedShareDialog { a.inviteDlg = false }
    // A gift list invitation from a Friend (2.142): a tapped notification or chat card.
    a.giftInvite?.let { code ->
        key(code) {
            GiftInviteDialog(
                code,
                onJoined = { c -> a.giftInvite = null; Notifications.markRead(ctx, "gl_invite", "i:$c", 0L); a.openGroup(c) },
                onClose = { a.giftInvite = null }
            )
        }
    }
    if (!needName && (a.joinGroupLink != null || a.joinGroupId != null)) key(a.joinGroupLink, a.joinGroupId) {
        JoinGroupDialog(
            a.joinGroupLink, a.joinGroupId,
            onJoined = { gid -> a.joinGroupLink = null; a.joinGroupId = null; a.openWishGroup(gid) },
            onClose = { a.joinGroupLink = null; a.joinGroupId = null }
        )
    }
    a.takeover?.let { me ->
        TakeoverDialog(
            me, snack,
            onDone = { id ->
                a.takeover = null
                if (id != null) a.openMine(id)
            },
            onClose = { a.takeover = null }
        )
    }
    if (a.welcome) WelcomeFlow { a.welcome = false }
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
    if (newKey && !a.welcome) NewKeyDialog {
        ctx.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().remove("rk_new").apply()
        newKey = false
    }
    // Signed out because the person restored on another phone: only this, over everything (Restore.kt, 2.188).
    if (Moved.away) MovedScreen()
    // Everyone has a name (owner, 2.135): asked once at the start when it's missing, and before sharing.
    LaunchedEffect(a.welcome) { if (!a.welcome) NameGate.askAtStart() }
    NameGate.Host()
    DeleteHost(snack)
    MoveHost(snack)
    // "Chat about this wish" on a shared wishlist: who to chat with (owner, 21 November 2026; ChatPeople.kt).
    ChatAboutHost(snack, goStable)
    AddToMineHost(snack)
    PrivateHost(snack)
    OwnedHost(snack)
    if (a.askBackground) BackgroundQuestion()
    // The share card of one of your lists (owner, 7 November 2026; HomeShare.kt).
    a.shareCard?.let { id -> key(id) { ListShareDialog(id) { a.shareCard = null } } }
    // A "Price alert" notification (or its entry in Notifications): that wish's price history, the alert lit up.
    a.history?.let { id ->
        val w = remember(id, Repo.mineVersion, Repo.myWishes) { Repo.allMine(ctx).flatMap { it.wishes }.firstOrNull { it.id == id } }
        if (w != null) PriceHistoryDialog(w, highlight = true) { a.history = null }
        else LaunchedEffect(id) { a.history = null }
    }
}
