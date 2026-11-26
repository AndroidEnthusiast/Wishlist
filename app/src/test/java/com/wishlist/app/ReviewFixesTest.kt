package com.wishlist.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun src(name: String) = File("src/main/java/com/wishlist/app/$name").readText()

/** The owner's review of 16 November 2026 (2.214): each point has a check that fails without it. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ReviewFixesTest {
    /** "Love it" on your own wishes, "Jorai loves it" for givers, "Loves it" on small cards or without a name. */
    @Test fun loveWords() {
        assertEquals("Love it", loveWords(null, small = false))
        assertEquals("Love it", loveWords(null, small = true))
        assertEquals("Jorai loves it", loveWords("Jorai van Dijk", small = false))
        assertEquals("Loves it", loveWords("Jorai", small = true))
        assertEquals("Loves it", loveWords("", small = false))
        assertEquals("Loves it", loveWords("   ", small = false))
    }

    /** Rows: the compact label on the picture (as two per row), not in the words next to it. */
    @Test fun rowsShowTheLabelOnThePicture() {
        val cards = src("RowCard.kt")
        val row = cards.substring(cards.indexOf("fun RowCard("))
        assertTrue(row.contains("StarCorner(w, onStar, small = true, buttonSize = 28.dp, compact = compact)"))
        assertFalse("no label in the words any more", row.contains("MostWantedBadge(true"))
    }

    /** The compact label: 11 sp words (labelSmall) and a 10 dp heart on small cards. */
    @Test fun smallLabelIsCompact() {
        val badge = src("MostWanted.kt").let { it.substring(it.indexOf("fun MostWantedBadge("), it.indexOf("fun MarkMostWantedButton(")) }
        assertTrue(badge.contains("LoveIcon(true, StarInk, if (small) 10.dp else 14.dp)"))
        assertTrue(badge.contains("if (small) MaterialTheme.typography.labelSmall"))
    }

    /** Givers see the name: a friend's list provides it (LocalLover). */
    @Test fun friendsListsSayWhoseHeartItIs() {
        assertTrue(src("Friend.kt").contains("LocalLover provides"))
        assertFalse("no old star left", (listOf("PeopleParts.kt", "FriendsTab.kt", "FriendsRows.kt", "FriendPage.kt", "AddFriend.kt", "Profile.kt", "ProfileEdit.kt", "DayMonthPicker.kt").joinToString("\n") { src(it) }).contains("Text(\"★\""))
    }

    /** The heart is explained once on your own lists; closing it or using a heart ends it for good. */
    @Test fun loveTipShowsOnceWithThreeWishes() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val w = { id: Long -> Wish(id, "", "W$id", "", "", null, null, "EUR", "") }
        assertFalse("two wishes: not yet", LoveTip.shows(app, listOf(w(1), w(2))))
        assertTrue(LoveTip.shows(app, listOf(w(1), w(2), w(3))))
        assertFalse("one loved: not needed", LoveTip.shows(app, listOf(w(1), w(2), w(3).copy(star = true))))
        LoveTip.finish(app)
        assertFalse("closed: never again", LoveTip.shows(app, listOf(w(1), w(2), w(3))))
        assertTrue(src("ListScreen.kt").contains("LoveTipCard(ctx, Repo.wishes)"))
    }

    /** Friends in the bottom bar is two people, filled when open. */
    @Test fun friendsTabIsTwoPeople() {
        val bar = src("BottomBar.kt")
        assertTrue(bar.contains("AppIcons.FriendsFilled else AppIcons.Friends"))
        assertFalse(bar.contains("AppIcons.PersonFilled else AppIcons.Person"))
    }

    /** Gen Z chaos: every settings screen puts its text on solid cards, never on the coloured circles. */
    @Test fun settingsScreensUseReadingCards() {
        assertTrue(src("Settings.kt").contains("ReadingCard(heading = { SectionTitle(title) }, content = content)"))
        assertEquals(4, Regex("ReadingCard\\(heading").findAll(src("ListSettings.kt")).count())
        assertTrue((listOf("PeopleParts.kt", "FriendsTab.kt", "FriendsRows.kt", "FriendPage.kt", "AddFriend.kt", "Profile.kt", "ProfileEdit.kt", "DayMonthPicker.kt").joinToString("\n") { src(it) }).contains("if (look.design != \"quiet\") { ReadingCard(content = content); return }"))
    }

    /** Going back from a chat "bounced" Messages: no size animation between screens inside a tab. */
    @Test fun movingNeverAnimatesSize() {
        for (k in Motion.Kind.values()) assertNull(Motion.transform(k).sizeTransform)
    }

    /**
     * The shared stores are made at the start, before anything is drawn (GitHub's check failed once: a store made by a
     * background job while Home was being drawn crashed with "a state that was created after the snapshot").
     */
    @Test fun storesAreMadeBeforeDrawing() {
        val m = src("MainActivity.kt")
        val touch = m.indexOf("Exchanges.list; Groups.groups; Chat.conversations; Shared.lists")
        assertTrue(touch > 0 && touch < m.indexOf("setContent {"))
    }

    /** A chat opens at its end without scrolling through it, and shows what it had at once. */
    @Test fun chatOpensAtTheEnd() {
        val m = src("ChatModel.kt") + src("ChatScreen.kt")
        assertTrue(m.contains("mutableStateOf(Chat.opened[peer].orEmpty())"))
        assertTrue(m.contains("if (!chat.placed) { listState.scrollToItem(chat.msgs.size - 1); chat.placed = true }"))
        assertTrue(m.contains(".alpha(if (chat.placed) 1f else 0f)"))
    }

    /** My order: new wishes on top, then the order you made; owned at the bottom. */
    @Test fun myOrder() {
        val w = { id: Long -> Wish(id, "", "W$id", "", "", null, null, "EUR", "") }
        val list = listOf(w(1), w(2), w(3), w(4).copy(got = true), w(5))
        assertEquals(listOf(5L, 3L, 1L, 2L, 4L), sortByOrder(list, listOf(3, 1, 4, 2)).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), moveInOrder(listOf(1, 2, 3), 1, 3))
        assertEquals(listOf(3L, 1L, 2L), moveInOrder(listOf(1, 2, 3), 3, 1))
        assertEquals(listOf(1L, 2L), moveInOrder(listOf(1, 2), 9, 1))
    }

    /** Each list has its own sort; a list never sorted follows your last choice. */
    @Test fun sortPerList() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        Repo.load(app)
        ListOrder.setSort(app, "mine_a", "low")
        ListOrder.save(app, "mine_b", listOf(2, 1))
        assertEquals("low", ListOrder.sortFor(app, "mine_a"))
        assertEquals(ListOrder.MINE, ListOrder.sortFor(app, "mine_b"))
        assertEquals("low", ListOrder.sortFor(app, "mine_c"))
        assertEquals(listOf(2L, 1L), ListOrder.order(app, "mine_b"))
    }
}

/** Hold and move drags a wish into a new place; hold and let go still selects (owner, 16 November 2026). */
class DragOrderTest : NavigationBase() {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    // Own ids per check: the app keeps its objects between checks in one process.
    private val b = (System.nanoTime() % 1_000_000L) * 10
    private val teapot = b + 1
    private val jacket = b + 2
    private val lamp = b + 3

    private var listId = ""

    /** A fresh list of its own with a teapot, a jacket and a lamp, open on screen. */
    private fun threeWishes() {
        compose.runOnUiThread {
            Repo.setView(app, "rows")
            listId = Repo.createMine(app, "Drag test $b", "")
            Repo.openMine(app, listId)
            Repo.addMine(app, Wish(teapot, "https://example.com/a", "Ceramic teapot", "", "", 20.0, null, "EUR", "Home"))
            Repo.addMine(app, Wish(jacket, "https://example.com/b", "Rain jacket", "", "", 80.0, null, "EUR", "Home"))
            Repo.addMine(app, Wish(lamp, "https://example.com/c", "Desk lamp", "", "", 40.0, null, "EUR", "Home"))
        }
        send(open(MainActivity.OPEN_NOTE, "m:$listId"))
        compose.waitForIdle()
    }

    @Test fun holdAndMoveReorders() {
        threeWishes()
        // Newest first: lamp, jacket, teapot. Drag the lamp down past the teapot.
        compose.onAllNodes(hasText("Desk lamp", substring = true)).onFirst().performTouchInput {
            down(center)
            advanceEventTime(1000)
            repeat(40) { moveBy(androidx.compose.ui.geometry.Offset(0f, 40f)); advanceEventTime(16) }
            up()
        }
        compose.waitForIdle()
        assertEquals(ListOrder.MINE, ListOrder.sortFor(app, ListOrder.openKey()))
        val order = ListOrder.order(app, ListOrder.openKey())
        assertTrue("lamp moved below the others: $order", order.indexOf(lamp) > order.indexOf(jacket))
        // Nothing got selected by the drag.
        compose.onAllNodes(hasText("1 selected")).assertCountEquals(0)
        shows("My order")
    }

    @Test fun holdAndLetGoSelects() {
        threeWishes()
        compose.onAllNodes(hasText("Desk lamp", substring = true)).onFirst().performTouchInput { longClick() }
        shows("1 selected")
        assertTrue(ListOrder.order(app, ListOrder.openKey()).isEmpty())
    }

    @Test fun moveOrCopyKeepsTheWishOnBothLists() {
        threeWishes()
        compose.runOnUiThread { Repo.createMine(app, "Household $b", ""); Repo.openMine(app, listId) }
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst().performClick()
        tap("Move or copy…")
        // The action first, then the wishlist (owner, 21 November 2026).
        shows("Move or copy?")
        tap("Copy")
        shows("Copy to…")
        tap("Household $b")
        compose.waitForIdle()
        val household = Repo.mineIds.first { Repo.mineName(it) == "Household $b" }
        assertTrue("copied there", Repo.mine(app, household).wishes.any { it.id == lamp })
        assertTrue("still here", Repo.mine(app, listId).wishes.any { it.id == lamp })
    }
}

/** The wish form trimmed (owner, 16 November 2026). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class WishFormTrimTest {
    @Test fun detailsSummarySaysWhatIsSet() {
        assertEquals("", detailsSummary("", 1, "", ""))
        assertEquals("Size M · 2× · Description", detailsSummary("", 2, "M", "Black"))
    }

    /** Exactly this / Similar is fine is never shown any more; the saved value stays in the data. */
    @Test fun exactIsNotShownButKept() {
        val w = Wish(1, "", "Socks", "", "", null, null, "EUR", "", qty = 3, exact = "similar")
        assertEquals("3×", wishTags(w))
        assertEquals("similar", wishFromJson(wishToJson(w)).exact)
    }

    /** Typed and chosen values in Quiet's fields are full ink, not the dialog's grey (the text style's colour is reset). */
    @Test fun quietFieldTextIsInk() {
        val t = File("src/main/java/com/wishlist/app/ThemedFields.kt").readText()
        assertEquals(2, Regex("textAlign = androidx.compose.ui.text.style.TextAlign.Start, color = androidx.compose.ui.graphics.Color.Unspecified").findAll(t).count())
    }

    /** Category and Save to open a bottom sheet and never take the focus. */
    @Test fun pickersAreSheets() {
        val f = File("src/main/java/com/wishlist/app/FormParts.kt").readText()
        val save = File("src/main/java/com/wishlist/app/SaveTo.kt").readText()
        assertTrue(f.contains("if (open) PickSheet(") && save.contains("if (open) PickSheet("))
        assertFalse(save.contains("DropdownMenu("))
        assertTrue(f.contains("focusProperties { canFocus = false }") && save.contains("focusProperties { canFocus = false }"))
    }
}


/** The friend's page person first, collages, the Home card's ✕ and broken pictures (owner, 16 November 2026). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PersonFirstTest {
    /**
     * Owner, 17 November 2026: no chips under a person (sizes, likes, counts, loved), only "About me" as text (at most
     * 100 letters); the birthday only on a friend's page in the 2 weeks before; sizes gone from the Profile and never
     * sent with a list.
     */
    @Test fun aboutMeInsteadOfChips() {
        // Friends and Profile were split out of FriendsUi.kt in the rebuild (19 November 2026).
        val f = listOf("PeopleParts.kt", "FriendsTab.kt", "FriendsRows.kt", "FriendPage.kt", "AddFriend.kt", "Profile.kt", "ProfileEdit.kt", "DayMonthPicker.kt").joinToString("\n") { src(it) }
        val hero = f.substring(f.indexOf("internal fun PersonHero("), f.indexOf("internal fun AboutChip("))
        assertFalse(hero.contains("FlowRow") || hero.contains("likesOf") || hero.contains("sizes"))
        assertTrue(hero.contains("if (birthdaySoon && days != null && days <= 14)"))
        assertTrue(f.contains("tr(\"About me\")") && !f.contains("tr(\"My sizes\")") && !f.contains("tr(\"I like…\")"))
        assertEquals(100, Friends.BIO_MAX)
        val items = Cloud.listItems(emptyList(), "Hello")
        assertFalse(items.getJSONObject(0).has("sz"))
        val hub = File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.startsWith("Home") && it.name.endsWith(".kt") || it.name == "NewWishlist.kt" }.sortedBy { it.name }.joinToString("\n") { it.readText() }
        val home = hub.substring(hub.indexOf("internal fun HomeHero("))
        assertFalse("no chips on Home", home.substring(0, home.indexOf("\n}\n")).contains("AboutChip"))
    }

    /** No number tiles, the name once, lists as collages, the note as a row with a sheet, Message as a button. */
    @Test fun friendPageIsPersonFirst() {
        val f = src("FriendPage.kt")
        val page = f.substring(f.indexOf("internal fun FriendPage("), f.indexOf("internal fun PrivateNoteSheet("))
        assertFalse(page.contains("FriendStat("))
        assertFalse("no zero shown", page.contains("FriendStat(top"))
        assertFalse("no loved chip (17 November 2026)", page.contains("AboutChip("))
        assertTrue(page.contains("val pics = newestPics(ws)"))
        assertTrue(page.contains("PrivateNoteSheet("))
        assertTrue(page.contains("go(Deep.Chat(f.id))"))
        assertTrue(page.contains("title = {}"))
    }

    @Test fun twoPhotosSideBySide() {
        assertTrue(File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.startsWith("Home") && it.name.endsWith(".kt") || it.name == "NewWishlist.kt" }.sortedBy { it.name }.joinToString("\n") { it.readText() }.contains("pics.size == 2 -> Row("))
    }

    /** ✕ on the recovery card: never again on Home. */
    @Test fun recoveryCardClosesForGood() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        RecoveryKey.later(app)
        assertEquals(3, app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE).getInt("rk_later_n", 0))
        assertFalse(RecoveryKey.showCard(app, System.currentTimeMillis() + 365L * 24 * 3600 * 1000))
    }

    /** A picture that can't load shows the gift on cards, and the form says so. */
    @Test fun brokenPicturesAreShownAsSuch() {
        BrokenPhotos.mark("https://x.example/a.jpg")
        assertTrue(BrokenPhotos.isBroken("https://x.example/a.jpg"))
        BrokenPhotos.clear("https://x.example/a.jpg")
        assertFalse(BrokenPhotos.isBroken("https://x.example/a.jpg"))
        assertTrue(File("src/main/java/com/wishlist/app/StyledPhotos.kt").readText().contains("if (BrokenPhotos.isBroken(url)) {"))
        assertTrue(File("src/main/java/com/wishlist/app/WishFormParts.kt").readText().contains("tr(\"The shop’s picture couldn’t load\")"))
    }
}

/** Home is your profile, the + menu, small menus as pop-ups (owner, 16 November 2026). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class HomeAsProfileTest {
    private val hub get() = File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.startsWith("Home") && it.name.endsWith(".kt") || it.name == "NewWishlist.kt" }.sortedBy { it.name }.joinToString("\n") { it.readText() }

    @Test fun homeIsYourProfile() {
        val screen = File("src/main/java/com/wishlist/app/Home.kt").readText()
        assertFalse("no numbers card", screen.contains("ProfileSummary("))
        assertTrue(screen.contains("HomeHero(onProfile)"))
        assertTrue("one grid, yours first", screen.contains("byRecent(mineItems) + byRecent(groupItems) + byRecent(groupsOfMine)"))
        assertTrue("no New gift exchange block", screen.contains("ExchangesSection(onNewExchange, withNew = false)"))
        assertTrue("same hero as a friend's page", hub.contains("PersonHero("))
    }

    @Test fun plusMenuOffersEveryNewThing() {
        val bar = File("src/main/java/com/wishlist/app/BottomBar.kt").readText()
        val menu = bar.substring(bar.indexOf("fun PlusMenuSheet("))
        // "New collab wishlist" folded into "New wishlist" (owner, 17 November 2026).
        for (t in listOf("Add a wish", "New wishlist", "New wishlist group", "New gift exchange"))
            assertTrue(t, menu.contains("tr(\"$t\")"))
        assertFalse(menu.contains("New collab wishlist"))
        assertTrue(menu.indexOf("Add a wish") < menu.indexOf("New wishlist"))
    }

    @Test fun shortMenusArePopUps() {
        // 17 November 2026 (owner: the pop-up "looks cheap"): a list's ⋮ is always the full-width sheet, X and icon tiles.
        val menu = hub.substring(hub.indexOf("internal fun ListMenuSheet("), hub.indexOf("internal fun VisibilitySheet("))
        assertFalse(menu.contains("PopMenu(") || menu.contains("actions.size <= 4"))
        // Since 21 November 2026 (owner's style guide): every menu is a sheet and menus have no ✕ (swipe down or tap
        // outside); the small pop-up menu is gone altogether.
        assertFalse(menu.contains("AppIcons.Close"))
        assertFalse(File("src/main/java/com/wishlist/app/WishSheet.kt").readText().contains("fun PopMenu("))
    }
}


/**
 * Collab wishlists folded into "New wishlist" (owner, 17 November 2026): "Who is it for? Me / Someone else"; a list
 * for someone else asks their name, never offers them in Invite Friends and warns on the share screen; "collab" is no
 * longer said anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class OneNewWishlistTest {
    @Test fun whoIsItForSwitchesBetweenTheTwo() {
        val app = File("src/main/java/com/wishlist/app/AppPopups.kt").readText()
        // One sheet for both (2.223): the chips swap the fields inside it.
        assertTrue(app.contains("\"mine\", \"collab\" -> NewWishlistSheet("))
        val hub = File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.startsWith("Home") && it.name.endsWith(".kt") || it.name == "NewWishlist.kt" }.sortedBy { it.name }.joinToString("\n") { it.readText() }
        assertTrue(hub.contains("FormSheetHost(onClose) {") && hub.contains("WhoIsItFor(others) { others = it }"))
        val shared = listOf("SharedUi.kt", "SharedManage.kt", "SharedListModel.kt", "InvitePeople.kt").joinToString("\n") { File("src/main/java/com/wishlist/app/$it").readText() }
        // New Co-op lists no longer ask who they're for (owner, 23 November 2026); lists made before keep their warning.
        assertTrue(!shared.contains("Shared.setForName(") && !shared.contains("tr(\"Gifts for someone"))
        assertTrue(shared.contains("tr(\"Don’t share it with %s: it’s a surprise.\", it)"))
        val inv = File("src/main/java/com/wishlist/app/GiftInviteViews.kt").readText()
        assertTrue(inv.contains("val friends = all.filterNot { Shared.isFor(forWho, it.name) }"))
    }

    @Test fun thePersonItIsForIsRecognised() {
        assertTrue(Shared.isFor("Mum", "mum"))
        assertTrue(Shared.isFor("Anna", "Anna de Vries"))
        assertTrue(Shared.isFor("Anna de Vries", "Anna"))
        assertFalse(Shared.isFor("Anna", "Annabel"))
        assertFalse(Shared.isFor("", "Anna"))
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        Shared.setForName(ctx, "CODE1", "  Mum  ")
        assertEquals("Mum", Shared.forName(ctx, "CODE1"))
        assertEquals("", Shared.forName(ctx, "OTHER"))
    }

    @Test fun collabIsNoLongerSaid() {
        File("src/main/java/com/wishlist/app").listFiles()!!.filter { it.name.endsWith(".kt") }.forEach { f ->
            // "Collaborate" is the owner's own word for what a co-op wishlist is (21 November 2026); "collab" as a name isn't.
            assertFalse(f.name, Regex("tr\\(\"[^\"]*[Cc]ollab(?!orat)").containsMatchIn(f.readText()))
        }
    }
}


/** You and your settings as a tall sheet (owner, 17 November 2026, plan step 4): nothing that was there went missing. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SettingsSheetTest {
    @Test fun everyPlaceIsStillThere() {
        val f = File("src/main/java/com/wishlist/app/Account.kt").readText()
        for (t in listOf("Gifts I reserved", "Received gifts", "Notifications", "Bin", "Theme", "App settings", "Share"))
            assertTrue(t, f.contains("tr(\"$t\")"))
        // A menu since 21 November 2026: it closes like every menu (swipe down, tap outside), so no ✕.
        assertTrue(f.contains("onProfile") && f.contains("FormSheetHost(onClose, menu = true)") && !f.contains("AppIcons.Close"))
        assertTrue(QuietIcons.palette in QuietIcons.all && QuietIcons.palette.root.size > 0)
    }
}
