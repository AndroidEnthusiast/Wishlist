package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * New wishlist (owner, 21 November 2026): "Who can see it" in the form, starting on App settings' choice and applying
 * to that wishlist only; a list made from + or Home opens at once, without the "is ready" message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewListChoicesTest {
    private val app get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun prefs() = app.getSharedPreferences("wishlist", android.content.Context.MODE_PRIVATE)

    @Test fun theChoiceInTheFormIsWhatTheListGets() {
        assertNull(ListVisibility.pendingValue(Who.ME))
        assertEquals("friends", ListVisibility.pendingValue(Who.FRIENDS))
        assertEquals("link", ListVisibility.pendingValue(Who.LINK))
        if (!com.wishlist.app.Config.enabled) return
        ListVisibility.startShared(app, "L1", Who.FRIENDS)
        assertEquals("friends", prefs().getString("vis_to_L1", null))
        ListVisibility.startShared(app, "L2", Who.ME)
        assertTrue("L2" !in prefs().getStringSet("vis_pending", null).orEmpty())
    }

    @Test fun pickingInTheFormLeavesTheDefaultAlone() {
        ListVisibility.loadDefault(app)
        val before = ListVisibility.newDefault
        if (com.wishlist.app.Config.enabled) ListVisibility.startShared(app, "L3", if (before == Who.ME) Who.LINK else Who.ME)
        assertEquals(before, ListVisibility.newDefault)
    }

    @Test fun theFormShowsTheChoicesAndPassesThem() {
        val src = File("src/main/java/com/wishlist/app/NewWishlist.kt").readText()
        assertTrue(src.contains("WhoPicker(who)"))
        assertTrue(src.contains("createOwnWishlist(ctx, name, notes, null, picked, if (Config.enabled) who else null)"))
        assertTrue(src.contains("mutableStateOf(ListVisibility.newDefault)"))
    }

    @Test fun aListMadeFromPlusOrHomeOpens() {
        val src = File("src/main/java/com/wishlist/app/AppPopups.kt").readText()
        val at = src.indexOf("onCreatedMine = ")
        val line = src.substring(at, src.indexOf('\n', at))
        assertTrue(line.contains("a.openMine(id)"))
        assertTrue(!line.contains("is ready"))
    }

    /** The owner's review of 2.271 (21 November 2026): no occasion, Personal / Shared, the card above Privacy. */
    @Test fun theFormFitsOnOnePage() {
        val dir = File("src/main/java/com/wishlist/app")
        val own = File(dir, "NewWishlist.kt").readText()
        val shared = File(dir, "SharedUi.kt").readText()
        val ownForm = own.substring(own.indexOf("fun NewListDialog("))
        val sharedForm = shared.substring(shared.indexOf("fun NewSharedDialog("), shared.indexOf("fun JoinDialog("))
        assertTrue(!ownForm.contains("OccasionPicker(") && !sharedForm.contains("OccasionPicker("))
        assertTrue(own.contains("tr(\"Personal\")") && own.contains("tr(\"Co-op\")") && !own.contains("tr(\"Whose wishlist?\")"))
        // "Shared" means "shared with someone" only (owner, 21 November 2026): the made-together kind is "Co-op".
        assertTrue(!own.contains("tr(\"Shared\")"))
        assertTrue(ownForm.indexOf("CopyWishesCard(picked") in 0 until ownForm.indexOf("WhoPicker(who)"))
        assertTrue(File(dir, "ListSharing.kt").readText().contains("Text(tr(\"Privacy\")"))
        assertTrue(!File(dir, "HomeMenus.kt").readText().contains("tr(\"Visibility\")"))
    }

    /** Chosen wishes can be taken out again (owner, 21 November 2026: "if I change my mind I can't remove it"). */
    @Test fun chosenWishesCanBeTakenOut() {
        // The picker opens with what was chosen ticked; with none left, its button says Done and takes them all out.
        assertEquals("Done", copyButtonLabel(0, hadChosen = true))
        assertEquals("Add 1 wish", copyButtonLabel(1, hadChosen = true))
        assertEquals("Add 3 wishes", copyButtonLabel(3, hadChosen = false))
        val dir = File("src/main/java/com/wishlist/app")
        val picker = File(dir, "CopyPicker.kt").readText()
        assertTrue(picker.contains("mutableStateOf(chosen.map { it.id }.toSet())"))
        assertTrue(picker.contains("enabled = picked.isNotEmpty() || chosen.isNotEmpty()"))
        // Both forms that choose wishes for a new list pass what was chosen, and their card has a ✕ per wish.
        for (f in listOf("NewWishlist.kt", "GroupListPick.kt")) {
            val src = File(dir, f).readText()
            assertTrue("$f passes what was chosen", src.contains("chosen = picked)"))
            assertTrue("$f can take one out", src.contains("onRemove = { w -> picked = picked - w }"))
        }
    }
}
