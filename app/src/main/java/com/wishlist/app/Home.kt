package com.wishlist.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/*
 * Home (rebuilt 18 November 2026, owner: review point 1, "built professionally and cleanly"; it was all in Hub.kt):
 * this file only lays the screen out. What it shows and what a list can do come from [HomeModel]; the parts are in
 * HomeHero.kt (you and your numbers), HomeBlocks.kt (the list blocks), HomeMenus.kt (a list's ⋮ and its dialogs),
 * HomeCards.kt (gift exchanges) and NewWishlist.kt. It looks exactly as before (screenshots compared pixel by pixel).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    snack: SnackbarHostState,
    onOpenMine: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onProfile: () -> Unit = {},
    // "+ Add wish" on an empty list's block: the wish form, with that list chosen.
    onAddTo: (String) -> Unit = {},
    // Wishlist groups (2.108): open one.
    onOpenWishGroup: (String) -> Unit = {},
    // The gift exchange card in its season.
    onNewExchange: () -> Unit = {}
) {
    val places = LocalPlaces.current
    val go = LocalGo.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val model = remember { HomeModel(ctx, onOpenMine, onOpenGroup) }
    // Your own lists are read again whenever one changes (mineVersion) or the open one changes.
    val mine = remember(Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.mineIds, Repo.openMine) { Repo.allMine(ctx) }
    val mineItems = remember(mine) { model.mineItems(mine) }
    val groupItems = remember(Shared.lists, Shared.content) { model.sharedItems(Shared.lists) }
    fun actionsOf(item: HubItem) = model.actionsOf(
        item,
        onlyList = { scope.launch { snack.showToast(tr("This is your only wishlist. Make another one first.")) } },
        shareCard = { code -> go(Deep.ShareCard(code)) }
    )

    // Nothing on any list yet (owner, 18 November 2026): under your lists, offer to bring a list over from elsewhere.
    // (Since 2.226 it sat in a part Home didn't show; found and fixed in the rebuild. Under the lists, not above them,
    // so a new person sees their own list first.)
    @Composable
    fun emptyImport() {
        if (homeIsEmpty(mine, Shared.lists)) Box(Modifier.padding(top = 12.dp)) { ImportHomeCard { go(Deep.Import("")) } }
    }

    // All your lists, on the page behind Home's Wishlists number: yours, your groups, then the shared ones.
    @Composable
    fun ListsSection() {
        HubTitle(tr("Your wishlists"), onMore = null)
        HubBlocks(byRecent(mineItems), false, ::actionsOf, onAddTo, tr("Create list"), null, dots = true)
        GroupsHomeSection(false, onOpenWishGroup, {})
        if (groupItems.isNotEmpty()) {
            HubTitle(tr("Co-op wishlists"), onMore = null)
            HubBlocks(byRecent(groupItems), false, ::actionsOf, onAddTo, tr("New wishlist"), null, dots = true)
        }
    }

    val page = places.homePage
    // Home's pages (your lists, all your wishes) slide in over Home and back (Motion.kt); "All wishes" and "Top
    // wishes" inside the page switch as before (the same page). The page that leaves keeps its last name.
    var lastPage by remember { mutableStateOf(page ?: "lists") }
    if (page != null) lastPage = page
    Moving(page != null, depth = { if (it) 1 else 0 }) { onPage ->
        if (onPage) OverviewScreen(places.homePage ?: lastPage, snack, { ListsSection() }, onOpenMine) else Scaffold(
            containerColor = cs.background,
            topBar = {
                TopAppBar(
                    // The app's wordmark instead of "Home" (2.97, in the style's own way).
                    title = { Wordmark() }, large = false,
                    navigationIcon = { MenuSlot(back = false) },
                    actions = { ProfileButton(onProfile) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
                )
            },
            snackbarHost = { AppSnackbarHost(snack) }
        ) { pad ->
            // The screen's height, so the swipe area under the divider can reach the bottom of the screen.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val screenH = maxHeight
            val density = androidx.compose.ui.platform.LocalDensity.current
            var belowTop by remember { mutableStateOf(0f) }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // "A new version is ready" (owner, 10 November 2026; AppInfo.kt): only when there is one.
                UpdateBar()
                // "Keep your wishlists safe on a new phone" until the recovery key is saved (RecoveryKey.kt, 2.187).
                RecoveryCard()
                // Home is your profile (owner, 16 November 2026, after Giftful): you as friends see you (PersonHero), then
                // all your lists in one grid, yours first; no numbers card, no headings, no "Create list" tiles (the
                // bottom bar's + makes new lists).
                Spacer(Modifier.height(16.dp))
                HomeHero(onProfile)
                GroupInvites()
                val groupsOfMine = wishGroupItems(onOpenWishGroup)
                val acts: (HubItem) -> List<Triple<String, Boolean, () -> Unit>> = { item -> if (item.key.startsWith("g:")) emptyList() else actionsOf(item) }
                // A divider between you and your wishlists (owner, 18 November 2026); only what is below it swipes.
                HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp))
                // Everything under the divider: with the swipe on, a swipe anywhere here (down to the bottom of the screen,
                // also under the shorter side) switches the tabs (owner, 20 November 2026).
                // A tab per kind of list (owner, 21 November 2026): Personal | Co-op | Groups | Exchanges, each only while
                // you have one; with only one kind, no tab bar at all.
                val hasExchanges = Config.enabled && Exchanges.list.isNotEmpty()
                val tabs = homeTabs(coop = groupItems.isNotEmpty(), groups = groupsOfMine.isNotEmpty(), exchanges = hasExchanges)
                var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
                val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab.coerceIn(0, tabs.size - 1)) { tabs.size }
                val swipe = Show["home_swipe"] && tabs.size > 1
                Column(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { belowTop = it.positionInParent().y }
                        .then(if (swipe) Modifier.heightIn(min = with(density) { (screenH.toPx() - belowTop - 32.dp.toPx()).coerceAtLeast(0f).toDp() })
                            .swipesPager(pager, scope).testTag("home_below") else Modifier)
                ) {
                if (swipe) {
                    androidx.compose.runtime.LaunchedEffect(pager.currentPage) { tab = pager.currentPage }
                    SegmentedTabs(
                        tabs.map { homeTabLabel(it) }, pager.currentPage.coerceIn(0, tabs.size - 1),
                        { i -> scope.launch { pager.animateScrollToPage(i) } },
                        Modifier.padding(horizontal = 16.dp)
                    )
                    androidx.compose.foundation.pager.HorizontalPager(pager, verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 12.dp)) { p ->
                        when (tabs.getOrNull(p)) {
                            "coop" -> BlockGrid(byRecent(groupItems), true, acts, onAddTo, dots = true)
                            "groups" -> BlockGrid(byRecent(groupsOfMine), true, acts, onAddTo, dots = true)
                            "exchanges" -> ExchangeGrid()
                            else -> Column {
                                BlockGrid(byRecent(mineItems), true, acts, onAddTo, dots = true)
                                emptyImport()
                            }
                        }
                    }
                } else {
                    // No tabs (only one kind, or App settings > Display > Tabs on Home off): everything under each other.
                    BlockGrid(byRecent(mineItems) + byRecent(groupItems) + byRecent(groupsOfMine), true, acts, onAddTo, dots = true)
                    emptyImport()
                    if (hasExchanges) ExchangesSection(onNewExchange, withNew = false)
                }

                // Gift exchange in its season (owner, 7 November 2026): 1 November to 20 December, until you have or are
                // invited to one, or close it; back the next year by itself.
                SeasonCard(onNewExchange)
    
                // No "Price changes" here any more (owner, 2.95): price news is on Price watch and in Notifications.
                }
            }
            }
        }
    }


    HomeListDialogs(model, snack)
}
