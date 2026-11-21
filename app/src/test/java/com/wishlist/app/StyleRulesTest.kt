package com.wishlist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The whole-app style guide (owner, 21 November 2026; docs/STYLE.md, the owner's style guide page): the rules that a
 * later change could quietly break are read from the code here. Dialog buttons are in DialogRulesTest.
 */
class StyleRulesTest {
    private val dir = File("src/main/java/com/wishlist/app")
    private fun all() = dir.listFiles()!!.filter { it.name.endsWith(".kt") }
    private fun src(f: String) = File(dir, f).readText()

    @Test fun everyMenuIsASheet() {
        // The app's own DropdownMenu is a bottom sheet (MenuSheet.kt); Material's drop-down and its rows aren't used.
        assertTrue(src("MenuSheet.kt").contains("ModalBottomSheet(onDismissRequest = onDismissRequest"))
        val bad = all().filter { f -> f.readText().let { it.contains("material3.DropdownMenuItem") || it.contains("M3DropdownMenu(") } }
        assertTrue("Material's drop-down menus: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun redMenuRowsSayDanger() {
        // A row that leaves or removes is red through danger = true (its tile goes red too), not a red word.
        val bad = all().flatMap { f ->
            Regex("""DropdownMenuItem\(\s*text = \{ Text\([^}]*color = [^}]*error""").findAll(f.readText()).map { "${f.name}: ${it.value.take(60)}" }.toList()
        }
        assertTrue("red words in a menu row: $bad", bad.isEmpty())
    }

    @Test fun formsHaveTheirCloseOnTheRight() {
        val bad = all().filter { it.readText().contains("Alignment.CenterStart).offset(x = (-12).dp)) {\n                    Icon(AppIcons.Close") }
        assertTrue("a ✕ on the left: ${bad.map { it.name }}", bad.isEmpty())
        assertTrue(src("WishForm.kt").contains("modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp)"))
        // Menus have no ✕: the profile sheet closes like a menu.
        assertTrue(src("Account.kt").contains("FormSheetHost(onClose, menu = true)"))
    }

    @Test fun actionsAreButtonsAndWordsGoSomewhere() {
        val b = src("StyledButtons.kt")
        assertTrue(b.contains("fun LinkButton(") && b.contains("CompositionLocalProvider(LocalInDialog provides true) { OutlinedButton(onClick, modifier, enabled, danger, content) }"))
    }

    @Test fun everyEmptyPageHasIconLineAndButton() {
        for (f in listOf("ListScreen.kt", "Friend.kt", "GroupScreen.kt", "ExchangeScreen.kt", "OverviewScreen.kt", "Reserved.kt",
            "NotificationsScreen.kt", "Prices.kt", "ExchangesList.kt", "Reminders.kt")) {
            assertTrue("$f uses EmptyState", src(f).contains("EmptyState("))
        }
        assertTrue(src("EmptyState.kt").contains("Button(onClick = onAction) { Text(action) }"))
    }

    @Test fun bigTitlesInEveryStyle() {
        assertTrue(src("Themed.kt").contains("val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()"))
        assertTrue(src("StyledBars.kt").contains("if (large && scroll != null) {"))
        assertTrue(!src("Themer.kt").contains("large = false"))
    }
}
