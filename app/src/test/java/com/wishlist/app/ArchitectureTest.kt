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
 */
class ArchitectureTest {
    private val dir = File("src/main/java/com/wishlist/app")
    private fun lines(f: String) = File(dir, f).readLines().size

    companion object {
        const val LIMIT = 400

        /** Over the limit on 19 November 2026, with their size then. Only shrinks. */
        val ALLOWED: Map<String, Int> = mapOf(
        "ExchangeUi.kt" to 1406,
        "FriendsUi.kt" to 1370,
        "Data.kt" to 1110,
        "GroupsUi.kt" to 1086,
        "Messages.kt" to 1036,
        "HeroEditor.kt" to 890,
        "Shops.kt" to 869,
        "Styled.kt" to 849,
        "Themed.kt" to 826,
        "Shared.kt" to 710,
        "AppScreen.kt" to 628,
        "ListScreen.kt" to 627,
        "Exchange.kt" to 608,
        "Alerts.kt" to 585,
        "WishCards.kt" to 576,
        "WishForm.kt" to 564,
        "Design.kt" to 556,
        "Import.kt" to 516,
        "Friends.kt" to 514,
        "Overview.kt" to 511,
        "Groups.kt" to 506,
        "Reactions.kt" to 495,
        "SharedUi.kt" to 486,
        "Fetch.kt" to 473,
        "GiftInvites.kt" to 456,
        "PriceHistory.kt" to 455,
        "Covers.kt" to 445,
        "Settings.kt" to 441,
        "Friend.kt" to 408
        )

        /** Screens rebuilt to "layout + state holder": they only draw. Every newly rebuilt screen joins this list. */
        val REBUILT = listOf("Home.kt", "HomeHero.kt", "HomeBlocks.kt", "HomeMenus.kt", "HomeCards.kt", "NewWishlist.kt")

        /** A call that changes shared data: Store.set…(), .delete…(), .leave(), … */
        val WRITES = Regex("\\b(Repo|Shared|Friends|Groups|Exchanges|ListVisibility|Occasions|MyLists)\\.(set|add|delete|remove|rename|create|leave|put|update|replace|trash|reserve|move|copy|open|hide|join|answer)[A-Za-z]*\\(")
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
            val hits = File(dir, f).readLines().mapIndexedNotNull { i, l -> WRITES.find(l)?.let { "$f:${i + 1} ${it.value}" } }
            assertTrue("A rebuilt screen changes shared data itself; move it into its state holder: $hits", hits.isEmpty())
        }
    }

    @Test fun theGuardSeesWrites() {
        assertTrue(WRITES.containsMatchIn("Repo.deleteMine(ctx, code)"))
        assertTrue(WRITES.containsMatchIn("Shared.leave(ctx, code)"))
        assertFalse(WRITES.containsMatchIn("Repo.mineName(id)"))
        assertFalse(WRITES.containsMatchIn("Friends.load(ctx)"))
    }
}
