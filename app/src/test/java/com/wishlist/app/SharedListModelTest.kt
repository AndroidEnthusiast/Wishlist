package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A shared wishlist's dialogs (the rebuild, 19 November 2026): dangerous actions are red, as everywhere. */
class SharedListModelTest {
    private fun src(n: String) = File("src/main/java/com/wishlist/app/$n").readText()

    /** Fixed in the rebuild: Leave and Delete for everyone used the theme colour instead of the red of a dangerous action. */
    @Test fun leaveAndDeleteAreRed() {
        val m = src("SharedManage.kt")
        assertEquals(0, Regex("""TextButton\((enabled = [^,]+, )?onClick = \{\s*(model\.leave|busy = true\s*scope\.launch \{\s*try \{\s*model\.deleteForEveryone)""").findAll(m).count())
        assertTrue(m.contains("TextButton(danger = true, onClick = {\n                    model.leave(l.code)"))
        assertTrue(m.contains("TextButton(danger = true, enabled = !busy, onClick = {\n                    busy = true\n                    scope.launch {\n                        try {\n                            model.deleteForEveryone()"))
    }

    @Test fun theDialogsChangeNothingThemselves() {
        for (f in listOf("SharedUi.kt", "SharedManage.kt")) assertTrue(f, !src(f).contains("Shared.leave(") && !src(f).contains("Shared.join("))
    }
}
