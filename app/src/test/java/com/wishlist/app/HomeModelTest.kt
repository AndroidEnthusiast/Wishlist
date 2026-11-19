package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Home rebuild (owner, 18 November 2026; review point 1): what a list's ⋮ offers is decided in one tested place
 * ([homeActions]); Home's parts are small files; the empty Home offers Import (a 2.226 bug: it never showed).
 */
class HomeModelTest {
    @Test fun aListsMenuInTheRightOrder() {
        assertEquals(listOf("share", "edit", "visibility", "delete"), homeActions(shared = false, creator = false, online = true))
        assertEquals(listOf("share", "edit", "delete"), homeActions(shared = false, creator = false, online = false))
        assertEquals(listOf("share", "edit", "delete"), homeActions(shared = true, creator = true, online = true))
        assertEquals(listOf("share", "edit", "leave"), homeActions(shared = true, creator = false, online = true))
    }

    @Test fun emptyHomeOffersImport() {
        val empty = MineData("main", "My wishlist", "", emptyList(), emptyList(), null, null, emptySet())
        val full = empty.copy(wishes = listOf(Wish(1, "", "Lamp", "", "", null, null, "EUR", "")))
        assertTrue(homeIsEmpty(listOf(empty), emptyList()))
        assertFalse(homeIsEmpty(listOf(empty, full), emptyList()))
        assertTrue(File("src/main/java/com/wishlist/app/Home.kt").readText().contains("if (homeIsEmpty(mine, Shared.lists)) Box(Modifier.padding(top = 12.dp)) { ImportHomeCard"))
    }

    @Test fun homeIsSmallFiles() {
        val dir = File("src/main/java/com/wishlist/app")
        assertFalse("Hub.kt was split", File(dir, "Hub.kt").exists())
        for (f in listOf("Home.kt", "HomeModel.kt", "HomeHero.kt", "HomeBlocks.kt", "HomeMenus.kt", "HomeCards.kt", "NewWishlist.kt"))
            assertTrue("$f under 400 lines", File(dir, f).readLines().size < 400)
        // The screen only lays out: the list's actions and dialogs' state are in HomeModel.
        val home = File(dir, "Home.kt").readText()
        assertFalse(home.contains("var renaming") || home.contains("Repo.deleteMine") || home.contains("Shared.leave"))
    }
}
