package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.robolectric.annotation.Config as Robo

/**
 * The risks the owner chose for the Import rebuild (19 November 2026), in plain words; ImportSameTest checks everything
 * together against the record made before the rebuild. No shop page is fetched.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class ImportPartsTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    @Before fun aFreshPhone() {
        prefs.edit().clear().commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)
    }

    @Test fun theSameInputGivesTheSameWishes() {
        val f = Import.parseText("- Lego set €39\n* Book https://shop.example/book?utm_source=notes\n\n•")
        assertEquals(listOf("Lego set", "Book"), f.map { it.title })
        assertEquals(39.0, f[0].price!!, 0.0)
        assertEquals("tracking removed", "https://shop.example/book", f[1].url)
        assertEquals("a redirect becomes the real shop page", "https://shop.example/p/1",
            Import.unwrap("https://go.example/r?url=https%3A%2F%2Fshop.example%2Fp%2F1"))
        assertEquals("only http(s)", "", Import.unwrap("javascript:alert(1)"))
        assertEquals("already on the list", setOf(0), Import.duplicates(f.drop(1), listOf(Wish(1, "http://www.shop.example/book/", "B", "", "", null, null, "EUR", ""))))
    }

    @Test fun theFillInNeverOverwrites() {
        val mine = Wish(5, "https://shop.example/t", "My name", "", "https://mine.example/p.jpg", 10.0, null, "EUR", "")
        val shop = Meta("Shop name", "", "https://shop.example/big.jpg", 25.0, "GBP", product = true)
        val after = ImportFill.filled(mine, shop)
        assertEquals("My name", after.title)
        assertEquals("the price you have stays (Rule 8)", 10.0, after.price!!, 0.0)
        assertEquals("EUR", after.currency)
        val empty = ImportFill.filled(mine.copy(title = "", price = null), shop)
        assertEquals("Shop name", empty.title)
        assertEquals(25.0, empty.price!!, 0.0)
    }

    @Test fun aNewerReaderIsUsedAndABrokenOneIsNot() {
        val file = File(c.filesDir, "import-reader.js")
        val builtIn = ImportReaders.versionOf(runBlocking { ImportReaders.script(c) })
        file.writeText("var VERSION = ${builtIn + 1}; window.WLImport={}")
        assertEquals(builtIn + 1, ImportReaders.versionOf(runBlocking { ImportReaders.script(c) }))
        file.writeText("broken")
        assertEquals(builtIn, ImportReaders.versionOf(runBlocking { ImportReaders.script(c) }))
        file.delete()
    }

    @Test fun aWaitingListFromBeforeTheUpdateIsFinished() {
        val list = Repo.createMine(c, "Imported", "")
        prefs.edit().putString("import_fill", JSONObject().put("gone_list", JSONArray().put(1)).put(list, JSONArray().put(555)).toString()).commit()
        ImportFill::class.java.getDeclaredField("running").apply { isAccessible = true }.setBoolean(ImportFill, false)
        ImportFill.start(c)
        runBlocking {
            val scope = ImportFill::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(ImportFill) as CoroutineScope
            repeat(50) { val k = scope.coroutineContext.job.children.toList(); if (k.isEmpty()) return@runBlocking; k.forEach { it.join() } }
        }
        assertTrue("nothing left waiting, nothing lost twice", JSONObject(prefs.getString("import_fill", "{}")!!).length() == 0)
    }
}
