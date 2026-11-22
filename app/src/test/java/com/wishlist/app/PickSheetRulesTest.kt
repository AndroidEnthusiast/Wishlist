package com.wishlist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pickers are bottom sheets (owner, 22 November 2026: "Move or copy?" looked out of place as a centred pop-up; "make
 * sure they are all built and that it looks flawless"; docs/STYLE.md "Dialogs"). Every "choose what to do" and "pick a
 * list, a wish or a person" uses PickSheet; this fails when one of them goes back to a centred AlertDialog.
 */
class PickSheetRulesTest {
    private val src = File("src/main/java/com/wishlist/app")

    /** File → the picker titles in it. */
    private val pickers = mapOf(
        "MoveChoose.kt" to listOf("Move or copy?", "Copy to…", "Move to…"),
        "OverviewScreen.kt" to listOf("Move from which list?", "Delete from which list?"),
        "AddToMine.kt" to listOf("Add to my wishlist"),
        "Reminders.kt" to listOf("What did you get?", "Which wishlist?"),
        "ChatParts.kt" to listOf("Send a wish"),
        "GiftInviteViews.kt" to listOf("Invite Friends"),
        "GroupMembers.kt" to listOf("Invite friends"),
        "ExchangeRows.kt" to listOf("Invite friends"),
        "GroupNew.kt" to listOf("Add friends")
    )

    @Test fun everyPickerIsASheet() {
        for ((file, titles) in pickers) {
            val text = File(src, file).readText()
            assertTrue("$file should use PickSheet", "PickSheet(" in text)
            for (t in titles) {
                assertTrue("$file: \"$t\" is a centred pop-up again", "title = { Text(tr(\"$t\")) }" !in text)
                assertTrue("$file: \"$t\" missing", "tr(\"$t\")" in text)
            }
        }
    }

    // One sheet for both steps of Move or copy (owner: "same sheet, next step"), with ← back to step 1.
    @Test fun moveOrCopyIsOneSheetWithABackStep() {
        val host = File(src, "Move.kt").readText()
        assertTrue("(MoveFlow.choosing ?: MoveFlow.picking)" in host)
        assertTrue("MoveToDialog" !in host && "MoveOrCopyDialog" !in File(src, "MoveChoose.kt").readText())
    }
}
