package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of Import in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`, "The
 * data rebuild"). Import's rules get many inputs (links of every site, pasted notes, the reader's answers, also bad
 * ones), adding to a list, the fill-in's rule (only what is missing, Rule 8) and its waiting list (also one left by an
 * older version, and after a restart), and the reader's choice between the built-in and a downloaded copy. Shop pages
 * are never fetched here (that would need the internet): the fill-in's own bookkeeping is checked.
 * The result must match `src/test/resources/import-same.txt` word for word: recorded with Import.kt as it was before
 * the rebuild (2.261). A MEANT change: delete the file, run once to record, read the difference in git.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class ImportSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs get() = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val golden = File("src/test/resources/import-same.txt")
    private val out = StringBuilder()
    private val start = System.currentTimeMillis()

    private fun line(s: String) { out.append(s).append('\n') }

    /** Waits until the fill-in has finished its round. */
    private fun idle() = runBlocking {
        val scope = ImportFill::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(ImportFill) as CoroutineScope
        repeat(50) { val k = scope.coroutineContext.job.children.toList(); if (k.isEmpty()) return@runBlocking; k.forEach { it.join() } }
    }

    private fun fillRunning(on: Boolean) = ImportFill::class.java.getDeclaredField("running").apply { isAccessible = true }.setBoolean(ImportFill, on)

    private fun normal(s: String): String {
        val names = LinkedHashMap<String, String>()
        return Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(s) { m ->
            if (m.value.toLong() < start) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
    }

    @Test fun importDoesExactlyTheSame() {
        prefs.edit().clear().putString("device", "device-1").commit()
        ListStore.reset(); WishlistDb.reset(); c.deleteDatabase("wishlist.db")
        Repo::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Repo, false)
        Repo.load(c)

        line("## sites")
        line("max=${Import.MAX} sites=${Import.SITES}")
        for (k in listOf("gowish", "giftful", "amazon", "bol", "etsy", "pinterest", "x")) line("name $k=${Import.siteName(k)}")
        for (s in Import.SITES.map { it.name } + "Other") line("tip $s=${Import.tip(s)}")
        val urls = listOf(
            "https://gowish.com/wishlist/abc", "https://www.gowish.com/", "https://dk.gowish.com/w/1", "https://giftful.com/u/list",
            "https://www.amazon.nl/hz/wishlist/ls/ABC", "https://amazon.com/registry/wedding/1", "https://www.amazon.de/dp/B01",
            "https://www.bol.com/nl/nl/verlanglijst/123", "https://bol.com/nl/p/x", "https://www.etsy.com/people/me/favorites",
            "https://etsy.com/listing/1", "https://nl.pinterest.com/ann/gifts/", "https://pinterest.com/pin/123/", "https://pinterest.com/search/x/",
            "https://shop.example/p/1", "not a link", "")
        for (u in urls) line("link '$u' key=${Import.siteKey(u)} list=${Import.isListLink(u)}")

        line("## what opens Import")
        for (t in listOf("https://gowish.com/w/1", "I found this: https://a.aliexpress.com/x", "https://a.example/1\nhttps://b.example/2",
                "Lego\nBook\nScarf", "Lego\nBook", "", "- \n* \n•"))
            line("list? '${t.replace("\n", "\\n")}' -> ${Import.looksLikeList(t)}")

        line("## real shop links")
        for (u in listOf(
            "https://go.example/r?url=https%3A%2F%2Fshop.example%2Fp%2F1%3Futm_source%3Dx%26color%3Dred",
            "https://click.example/?u=https%3A%2F%2Ftrack.example%2F%3Ftarget%3Dhttps%253A%252F%252Fshop.example%252Fdeep",
            "https://www.amazon.nl/Some-Name/dp/B0ABC123/ref=sr_1_1?tag=aff-21&th=1", "https://shop.example/p?ref=x&size=M#top",
            "https://shop.example/p?utm_medium=a&gclid=b", "javascript:alert(1)", "https://x.example/?redirect=javascript:alert(1)",
            "ftp://x.example/f", "  https://SHOP.example/Path/  "))
            line("unwrap '$u' -> '${Import.unwrap(u)}' key='${Import.linkKey(u)}'")

        line("## prices")
        for ((t, h) in listOf("€ 39,99" to "", "EUR 39.99" to "", "£12.50" to "", "$5" to "", "1.299,00 kr" to "shop.se", "12" to "bol.com",
                "free" to "", "" to "", "0" to "", "99999999" to "", "CHF 20" to "", "20 zł" to "shop.pl"))
            line("price '$t' $h -> ${Import.price(t, h)}")

        line("## notes")
        val notes = """
            - Lego set €39
            * [x] Book https://shop.example/book?utm_source=notes
            1. Scarf, blue — 25 EUR
            2) https://www.amazon.nl/dp/B0XYZ12345?tag=x
            ☐ Candle | £8.50
            [ ]
            •
            Just a name with a link at the end https://shop.example/a.
        """.trimIndent()
        Import.parseText(notes).forEach { line("found $it") }
        line("many=${Import.parseText((1..400).joinToString("\n") { "Wish $it" }).size}")

        line("## the reader's answers")
        val good = JSONObject().put("name", "  Ann's list | GoWish ").put("date", "2027-12-24").put("site", "gowish").put("version", 3)
            .put("wishes", JSONArray()
                .put(JSONObject().put("title", "  Teapot \n big ").put("url", "https://go.example/r?url=https%3A%2F%2Fshop.example%2Ft").put("image", "http://img.example/t.jpg").put("price", "€ 20,00").put("love", true))
                .put(JSONObject().put("title", "Bad link").put("url", "javascript:alert(1)").put("image", "data:image/png;base64,x").put("price", ""))
                .put(JSONObject().put("title", "").put("url", ""))
                .put(JSONObject().put("title", "x".repeat(300)).put("url", "https://shop.example/x").put("price", "12")))
        line("read ${Import.fromReader(good.toString(), "https://gowish.com/w/1")}")
        line("read bad date ${Import.fromReader(JSONObject().put("name", "x").put("date", "soon").toString(), "https://x.example")}")
        line("read not json ${Import.fromReader("<html>", "https://x.example")} null ${Import.fromReader(null, "")}")
        val many = JSONObject().put("wishes", JSONArray((1..350).map { JSONObject().put("title", "W$it") }))
        line("read many ${Import.fromReader(many.toString(), "https://x.example")?.wishes?.size}")
        for (n in listOf("Gifts - Amazon.nl", "Board · Pinterest", "Favourites – Etsy UK", "Plain name", "  spaced   out  ", "y".repeat(80)))
            line("clean '$n' -> '${Import.cleanName(n)}'")

        line("## duplicates, ids, occasion")
        val found = Import.parseText("A https://shop.example/1\nB https://www.shop.example/1/\nC https://shop.example/2\nD\nE https://shop.example/3")
        val existing = listOf(Wish(1, "http://shop.example/2", "C", "", "", null, null, "EUR", ""), Wish(2, "https://shop.example/3", "E", "", "", null, null, "EUR", "", bin = 5L))
        line("duplicates=${Import.duplicates(found, existing).sorted()}")
        Import.toWishes(found, 1000L, setOf(999L, 997L)).forEach { line("wish ${it.id} ${it.title} ${it.url} ${it.price} ${it.currency} star=${it.star}") }
        val today = LocalDate.of(2027, 1, 10)
        for ((d, n) in listOf("2027-12-24" to "Xmas", "2027-01-10" to "", "2026-12-24" to "Old", "nope" to "x", "2027-02-01" to "z".repeat(80)))
            line("occasion $d '$n' -> ${Import.occasionOf(d, n, today)}")

        line("## adding to a list (the fill-in waits)")
        fillRunning(true)
        val list = Repo.createMine(c, "Imported", "")
        Thread.sleep(5) // the list's id (a moment) never equals a new wish's id (moments counted back)
        val add = listOf(Found("Teapot", "https://shop.example/t", "", 20.0, "EUR", love = true), Found("Note only", "", "", null, "EUR"))
        line("added=${Import.add(c, list, add)} to a gone list=${Import.add(c, "gone", add)} nothing=${Import.add(c, list, emptyList())}")
        Repo.mine(c, list).wishes.forEach { line("on the list ${it.id} ${it.title} ${it.url} ${it.price} star=${it.star}") }
        line("waiting=${prefs.getString("import_fill", null)} history=${History.all.keys.sorted()}")

        line("## the fill-in's rule: only what is missing")
        val w = Wish(5, "https://shop.example/t", "", "", "https://list.example/small.jpg", null, null, "EUR", "")
        for (m in listOf(
            Meta("Shop name", "", "http://shop.example/big.jpg", 25.0, "GBP", product = true),
            Meta("Shop name", "", "https://shop.example/big.jpg", 25.0, "GBP", product = false),
            Meta("Blocked", "", "https://x/b.jpg", 1.0, "EUR", blocked = true),
            Meta("", "", "", null, "EUR")))
            line("filled empty -> ${ImportFill.filled(w, m)} / set -> ${ImportFill.filled(w.copy(title = "Mine", price = 10.0, image = ""), m)}")

        line("## the waiting list after an update and a restart")
        // Left by an older version: a deleted list, a wish that is gone, a wish without a link.
        Repo.updateMine(c, list) { ws -> ws + Wish(777, "", "No link", "", "", null, null, "EUR", "") }
        prefs.edit().putString("import_fill", JSONObject().put("gone_list", JSONArray().put(1).put(2)).put(list, JSONArray().put(555).put(777)).toString()).commit()
        fillRunning(false)
        ImportFill.start(c); idle()
        line("waiting after=${prefs.getString("import_fill", null)} tries=${prefs.getString("import_fill_tries", null)}")
        fillRunning(true)
        ImportFill.queue(c, list, emptyList())
        ImportFill.queue(c, list, listOf(42L, 43L)); ImportFill.queue(c, "other", listOf(7L))
        line("queued=${prefs.getString("import_fill", null)}")

        line("## the reader")
        line("version ${ImportReaders.versionOf("var VERSION = 7;")} ${ImportReaders.versionOf("nothing")} url=${ImportReaders.URL}")
        val built = runBlocking { ImportReaders.script(c) }
        line("built in v${ImportReaders.versionOf(built)} has reader=${built.contains("WLImport")}")
        val file = File(c.filesDir, "import-reader.js")
        for (dl in listOf("var VERSION = 999; window.WLImport={}", "var VERSION = 0; window.WLImport={}", "broken")) {
            file.writeText(dl)
            line("downloaded '${dl.take(20)}' -> uses v${ImportReaders.versionOf(runBlocking { ImportReaders.script(c) })}")
        }
        file.delete()
        line("asked online at=${prefs.getLong("import_reader_at", 0L)}")
        line("nothing found report: ${Import.NothingFound("", 3).message} / ${Import.NothingFound("etsy", 1).message}")

        val now = normal(out.toString())
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
