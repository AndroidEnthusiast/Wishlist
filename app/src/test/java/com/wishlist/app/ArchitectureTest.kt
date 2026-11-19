package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guards that keep the app built cleanly as it grows (owner, 19 November 2026: "I don't want to repeat this
 * exercise over and over again"; docs/decisions/roadmap.md and MAP "How a screen is built"). They fail the build, so
 * mess can't creep back unnoticed:
 *  1. Every app file stays under [LIMIT] lines. Files that were bigger when the guards came are in [ALLOWED] with
 *     their size then: they may shrink, never grow, and leave the list once under the limit. Never add a file to it.
 *  2. Rebuilt screens only draw ([REBUILT]): they never change shared data themselves (your lists, shared lists,
 *     friends, groups, exchanges, visibility, occasions); that goes through their state holder.
 *  3. Your own data has one front door (the data rebuild, owner 19 November 2026): the parts of Repo (OwnLists, OwnBin,
 *     OwnCopies, AppPrefs, FriendLists) are only touched in Data.kt and the Repo*.kt files; everything else reads and
 *     changes them through Repo (Repo.myWishes, Repo.trash, …), so saving always goes through Repo.persist. The same for
 *     Friends: its parts (FriendPhotos, FriendAbout, FriendVisibility) only in Friends.kt and FriendsPhotos, -About,
 *     -Visible, -Links. And Shared: its save engine and SharedReservations only in Shared.kt and its four part files.
 *     And Groups: GroupPhotos only in Groups.kt, GroupsMembership, GroupsPhotos, GroupsLinks.
 *     And gift exchanges: ExchangePhotos only in Exchange.kt and ExchangeNews, -Membership, -Draw, -Types.
 *     And notifications: PriceAlertQueue only in Alerts.kt and AlertsPrices, -Lists, -People.
 */
class ArchitectureTest {
    private val dir = File("src/main/java/com/wishlist/app")
    private fun lines(f: String) = File(dir, f).readLines().size

    companion object {
        const val LIMIT = 400

        /** Over the limit on 19 November 2026, with their size then. Only shrinks. */
        val ALLOWED: Map<String, Int> = mapOf(
        "Fetch.kt" to 473
        )

        /** Screens rebuilt to "layout + state holder": they only draw. Every newly rebuilt screen joins this list. */
        val REBUILT = listOf(
            "Home.kt", "HomeHero.kt", "HomeBlocks.kt", "HomeMenus.kt", "HomeCards.kt", "NewWishlist.kt",
            // Friends and Profile (19 November 2026): their state holder is PeopleModel.kt.
            "PeopleParts.kt", "FriendsTab.kt", "FriendsRows.kt", "FriendPage.kt", "AddFriend.kt", "Profile.kt", "ProfileEdit.kt", "DayMonthPicker.kt",
            // The open list (19 November 2026): its state holder is ListModel.kt.
            "ListScreen.kt", "ListWishItem.kt", "ListViewButtons.kt", "ListDrag.kt",
            // Gift exchange (19 November 2026): its state holder is ExchangeModel.kt.
            "ExchangeParts.kt", "ExchangesList.kt", "ExchangeNew.kt", "ExchangeScreen.kt", "ExchangeDialogs.kt",
            "ExchangeRows.kt", "ExchangeSettings.kt", "ExchangeChat.kt",
            // Wishlist groups (19 November 2026): their state holder is GroupModel.kt.
            "GroupsParts.kt", "GroupListPick.kt", "GroupNew.kt", "GroupJoin.kt", "GroupScreen.kt", "GroupMembers.kt", "GroupSettings.kt",
            // Messages (19 November 2026): one chat's state holder is ChatModel.kt.
            "MessagesList.kt", "ChatScreen.kt", "ChatBubbles.kt", "ChatParts.kt",
            // The photo editor (19 November 2026): its state holder is HeroEditorState.kt.
            "HeroEditor.kt", "HeroEditorParts.kt", "HeroPicture.kt",
            // My shops (19 November 2026): its state holders are in ShopsModel.kt.
            "ShopsScreen.kt", "ShopBrowser.kt",
            // The app's frame (19 November 2026): its state holder is AppModel.kt.
            "AppScreen.kt", "AppPopups.kt",
            // The Add / Edit wish form (19 November 2026): its state holder is WishFormModel.kt.
            "WishForm.kt", "WishFormParts.kt",
            // A friend's list (19 November 2026): its state holder is FriendListModel.kt.
            "Friend.kt",
            // App settings (19 November 2026): its state holder is SettingsModel.kt.
            "Settings.kt", "SettingsPages.kt",
            // A list's cover photo (19 November 2026): its state holder is CoverModel.kt.
            "CoverViews.kt",
            // A wish's price page (19 November 2026): its state holders are in PriceModel.kt.
            "PriceHistory.kt", "PriceChart.kt",
            // Gift list invitations (19 November 2026): their state holders are in GiftInviteModel.kt.
            "GiftInviteViews.kt",
            // A shared wishlist's dialogs (19 November 2026): their state holder is SharedListModel.kt.
            "SharedUi.kt", "SharedManage.kt",
            // Reactions on wishes (19 November 2026): the row's state holder is ReactionModel.kt.
            "ReactionViews.kt",
            // The pages behind Home's numbers (19 November 2026): their state holder is OverviewModel.kt.
            "OverviewScreen.kt"
        )

        /** A store's data set straight away ("Exchanges.list = …"; only the checks' pretend data may). */
        val STORE_SET = Regex("\\b(Repo|Shared|Friends|Groups|Exchanges|Chat)\\.[a-z][A-Za-z]*(\\[[^\\]]*])? = ")

        /** A call that changes shared data: Store.set…(), .delete…(), .leave(), … */
        val WRITES = Regex("\\b(Repo|Shared|Friends|Groups|Exchanges|ListVisibility|Occasions|MyLists|Chat|ChatPhotos|Shops|Show|CurrencyChoice|ListCovers|Targets|GiftInvites|Reactions)\\.(set|add|delete|remove|rename|create|leave|put|update|replace|trash|reserve|move|copy|open(?!Key)|hide|join|answer|new|cancel|invite|decline|send|seen|save(?!Key)|reveal|handover|draw|settings|takeover|react|upload|forget|mark|restore|detach|accept)[A-Za-z]*\\(")
    }

    @Test fun everyFileStaysSmall() {
        val tooBig = dir.listFiles()!!.filter { it.name.endsWith(".kt") }
            .filter { it.readLines().size > LIMIT && it.name !in ALLOWED }
            .map { "${it.name}: ${it.readLines().size} lines" }
        assertTrue("Split these files (under $LIMIT lines each; MAP \"How a screen is built\"): $tooBig", tooBig.isEmpty())
    }

    @Test fun bigFilesOnlyShrink() {
        for ((f, was) in ALLOWED) {
            assertTrue("$f is gone or renamed: take it off ArchitectureTest.ALLOWED", File(dir, f).exists())
            val now = lines(f)
            assertTrue("$f grew from $was to $now lines: split it instead (it is over the limit)", now <= was)
            assertTrue("$f is under $LIMIT lines now: take it off ArchitectureTest.ALLOWED", now > LIMIT)
        }
    }

    @Test fun rebuiltScreensOnlyDraw() {
        for (f in REBUILT) {
            val hits = File(dir, f).readLines().mapIndexedNotNull { i, l -> (WRITES.find(l) ?: STORE_SET.find(l))?.let { "$f:${i + 1} ${it.value}" } }
            assertTrue("A rebuilt screen changes shared data itself; move it into its state holder: $hits", hits.isEmpty())
        }
    }

    @Test fun ownDataOnlyThroughRepo() {
        val part = Regex("\\b(OwnLists|OwnBin|OwnCopies|AppPrefs|FriendLists)\\.")
        val hits = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name != "Data.kt" && !it.name.startsWith("Repo") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> part.find(l)?.let { "${f.name}:${i + 1} ${l.trim()}" } } }
        assertTrue("Use Repo's doors (Repo.trash, Repo.myWishes, …), not its parts directly: $hits", hits.isEmpty())
        assertTrue(part.containsMatchIn("OwnLists.myWishes = x"))
        val friendPart = Regex("\\b(FriendPhotos|FriendAbout|FriendVisibility)\\.")
        val friendHits = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name !in setOf("Friends.kt", "FriendsPhotos.kt", "FriendsAbout.kt", "FriendsVisible.kt", "FriendsLinks.kt") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> friendPart.find(l)?.let { "${f.name}:${i + 1} ${l.trim()}" } } }
        assertTrue("Use Friends' doors (Friends.setPhoto, Friends.visible, …), not its parts directly: $friendHits", friendHits.isEmpty())
        // Shared (2.258): its save engine and reservation state only inside its own files.
        val sharedPart = Regex("\\b(SharedReservations\\.|Shared\\.(scope|lock|pending|stamp|write|saveLists|readCache|writeCache|rememberName)\\b)")
        val sharedFiles = setOf("Shared.kt", "SharedMembership.kt", "SharedWishes.kt", "SharedDetails.kt", "SharedReservations.kt")
        val sharedHits = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name !in sharedFiles }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> sharedPart.find(l)?.let { "${f.name}:${i + 1} ${l.trim()}" } } }
        assertTrue("Use Shared's doors (Shared.put, Shared.reserve, …), not its insides: $sharedHits", sharedHits.isEmpty())
        // Groups (2.259): its photo part only inside its own files.
        val groupFiles = setOf("Groups.kt", "GroupsMembership.kt", "GroupsPhotos.kt", "GroupsLinks.kt")
        val groupHits = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name !in groupFiles }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> Regex("\\bGroupPhotos\\.").find(l)?.let { "${f.name}:${i + 1} ${l.trim()}" } } }
        assertTrue("Use Groups' doors (Groups.photos, Groups.setPhoto, …), not its parts: $groupHits", groupHits.isEmpty())
        // Gift exchanges (2.260): its photo part only inside its own files.
        val gxFiles = setOf("Exchange.kt", "ExchangeNews.kt", "ExchangeMembership.kt", "ExchangeDraw.kt", "ExchangeTypes.kt")
        val gxHits = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name !in gxFiles }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> Regex("\\bExchangePhotos\\.").find(l)?.let { "${f.name}:${i + 1} ${l.trim()}" } } }
        assertTrue("Use Exchanges' doors (Exchanges.photos, Exchanges.setPhoto, …), not its parts: $gxHits", gxHits.isEmpty())
        // Notifications (2.261): the price queue only inside Alerts' own files.
        val alertFiles = setOf("Alerts.kt", "AlertsPrices.kt", "AlertsLists.kt", "AlertsPeople.kt")
        val alertHits = dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name !in alertFiles }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> Regex("\\bPriceAlertQueue\\.").find(l)?.let { "${f.name}:${i + 1} ${l.trim()}" } } }
        assertTrue("Use Alerts' doors (Alerts.collectPrices, Alerts.notifyDrop, …), not its insides: $alertHits", alertHits.isEmpty())
        assertFalse(part.containsMatchIn("MyOwnLists.x"))
    }

    @Test fun theGuardSeesWrites() {
        assertTrue(WRITES.containsMatchIn("Repo.deleteMine(ctx, code)"))
        assertTrue(WRITES.containsMatchIn("Shared.leave(ctx, code)"))
        assertTrue(WRITES.containsMatchIn("Friends.newCode(ctx)"))
        assertTrue(WRITES.containsMatchIn("Exchanges.draw(ctx, gid, without)"))
        assertTrue(WRITES.containsMatchIn("Exchanges.saveSettings(ctx, x.id, name, b, c, d, t, m)"))
        assertFalse(WRITES.containsMatchIn("Exchanges.countdown(x)"))
        assertTrue(WRITES.containsMatchIn("Friends.setNote(ctx, f.id, note)"))
        assertFalse(WRITES.containsMatchIn("Repo.mineName(id)"))
        assertFalse(WRITES.containsMatchIn("Friends.load(ctx)"))
        assertTrue(WRITES.containsMatchIn("Groups.settings(ctx, g.id, name, inviteAll)"))
        assertTrue(WRITES.containsMatchIn("Groups.takeover(ctx, a, b, c)"))
        assertTrue(WRITES.containsMatchIn("Chat.send(ctx, peer, content)"))
        assertTrue(WRITES.containsMatchIn("ChatPhotos.upload(ctx, peer, image)"))
        assertFalse(WRITES.containsMatchIn("Chat.preview(r.content)"))
        assertTrue(STORE_SET.containsMatchIn("Chat.opened[peer] = msgs"))
        assertTrue(WRITES.containsMatchIn("Shops.opened(ctx, s)"))
        assertTrue(WRITES.containsMatchIn("Shops.markOffered(ctx, h)"))
        assertFalse(WRITES.containsMatchIn("Shops.has(ctx, host)"))
        assertTrue(WRITES.containsMatchIn("Show.set(ctx, \"nt_req\", it)"))
        // A list's key ("m:…"), not a save.
        assertFalse(WRITES.containsMatchIn("MyLists.saveKey(Repo.openMine, false)"))
        // Which list is open, not opening one.
        assertFalse(WRITES.containsMatchIn("ListCovers.openKey()"))
        assertTrue(WRITES.containsMatchIn("ListCovers.detach(ctx, key)"))
        assertTrue(STORE_SET.containsMatchIn("Groups.unsent = Groups.unsent - g.id"))
    }
}
