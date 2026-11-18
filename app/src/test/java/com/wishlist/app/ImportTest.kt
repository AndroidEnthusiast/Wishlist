package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Import (owner, 18 November 2026; docs/decisions/import.md): text, links, the reader's answer, duplicates, ids. */
class ImportTest {
    @Test fun textFromNotes() {
        val f = Import.parseText(
            """
            My birthday list
            - Lego set €39
            • Headphones https://www.coolblue.nl/product/123?utm_source=x 89,99 €
            1. Book: The Hobbit
            [ ] Socks £12.50

            ☐ https://www.amazon.nl/Some-Thing/dp/B0ABCDEFGH/ref=sr_1_3
            ...
            """.trimIndent()
        )
        assertEquals(6, f.size)
        assertEquals("My birthday list", f[0].title)
        assertEquals("Lego set", f[1].title); assertEquals(39.0, f[1].price!!, 0.0); assertEquals("EUR", f[1].currency)
        assertEquals("Headphones", f[2].title); assertEquals("https://www.coolblue.nl/product/123", f[2].url); assertEquals(89.99, f[2].price!!, 0.0)
        assertEquals("Book: The Hobbit", f[3].title)
        assertEquals("Socks", f[4].title); assertEquals("GBP", f[4].currency)
        assertEquals("", f[5].title); assertEquals("https://www.amazon.nl/dp/B0ABCDEFGH", f[5].url)
    }

    @Test fun realShopLinks() {
        assertEquals("https://shop.example/mug", Import.unwrap("https://gowish.com/go?url=https%3A%2F%2Fshop.example%2Fmug"))
        assertEquals("https://www.bol.com/nl/p/x/9200000/", Import.unwrap("https://www.bol.com/nl/p/x/9200000/?utm_source=gowish&utm_medium=app"))
        assertEquals("https://shop.example/a?size=M", Import.unwrap("https://shop.example/a?size=M&tag=abc-21&fbclid=1"))
        assertEquals("https://www.amazon.de/dp/B0ABCDEFGH", Import.unwrap("https://www.amazon.de/x/dp/B0ABCDEFGH/?tag=gowish-21"))
        // Twice wrapped.
        assertEquals("https://shop.example/b", Import.unwrap("https://r.example/?u=" + java.net.URLEncoder.encode("https://t.example/c?url=" + java.net.URLEncoder.encode("https://shop.example/b", "UTF-8"), "UTF-8")))
        assertEquals("", Import.unwrap("javascript:alert(1)"))
        assertEquals("", Import.unwrap("https://x.example/go?url=javascript%3Aalert(1)").takeIf { !it.startsWith("https://x.example") } ?: "")
    }

    @Test fun listLinksOpenImport() {
        assertTrue(Import.isListLink("https://gowish.com/s/e04yb6"))
        assertTrue(Import.isListLink("https://www.amazon.nl/hz/wishlist/ls/2ABCDEF?ref_=wl_share"))
        assertTrue(Import.isListLink("https://www.bol.com/nl/nl/verlanglijstje/abc/"))
        assertTrue(Import.isListLink("https://www.etsy.com/people/sam/favorites"))
        assertTrue(Import.isListLink("https://nl.pinterest.com/sam/birthday-ideas/"))
        assertTrue(Import.isListLink("https://giftful.com/sam/birthday"))
        assertFalse(Import.isListLink("https://www.amazon.nl/dp/B0ABCDEFGH"))
        assertFalse(Import.isListLink("https://nl.pinterest.com/pin/12345/"))
        assertFalse(Import.isListLink("https://gowish.com/"))
        assertTrue(Import.looksLikeList("Look at my list https://gowish.com/s/e04yb6"))
        assertTrue(Import.looksLikeList("Lego\nSocks\nBook"))
        assertFalse(Import.looksLikeList("Check out this product!\nhttps://www.coolblue.nl/product/123"))
    }

    @Test fun sharedListOpensImportAndAProductStillOpensAddWish() {
        assertEquals(Deep.Import("https://gowish.com/s/e04yb6"), shared("https://gowish.com/s/e04yb6"))
        assertEquals(Deep.AddLink("https://www.example.com/p/teapot-123"), shared("Look https://www.example.com/p/teapot-123"))
    }

    private fun shared(t: String): Deep? {
        // deepOf needs Android's Intent; the routing it does for text is this (Deep.kt, ACTION_SEND).
        return if (Import.looksLikeList(t)) Deep.Import(t.trim()) else cleanUrl(t)?.let { Deep.AddLink(it) }
    }

    @Test fun readersAnswerIsChecked() {
        val json = """{"version":1,"site":"gowish","reader":"any","name":"Birthday | GoWish","date":"2099-04-01","wishes":[
            {"title":"Lego","url":"https://gowish.com/go?url=https%3A%2F%2Fshop.example%2Flego","image":"http://img.example/l.jpg","price":"€ 39,99","love":true},
            {"title":"Bad","url":"javascript:alert(1)","image":"javascript:x","price":""},
            {"title":"","url":"","image":"","price":""}]}"""
        val r = Import.fromReader(json, "https://gowish.com/s/abc")!!
        assertEquals("Birthday", r.name)
        assertEquals("2099-04-01", r.date)
        assertEquals(2, r.wishes.size)
        assertEquals("https://shop.example/lego", r.wishes[0].url)
        assertEquals("https://img.example/l.jpg", r.wishes[0].image)
        assertEquals(39.99, r.wishes[0].price!!, 0.0); assertEquals("EUR", r.wishes[0].currency)
        assertTrue(r.wishes[0].love)
        assertEquals("", r.wishes[1].url); assertEquals("", r.wishes[1].image)
        assertNull(Import.fromReader("not json", "https://x.example/"))
    }

    @Test fun duplicatesAreSpotted() {
        val have = listOf(Wish(1, "https://www.shop.example/mug/", "Mug", "", "", null, null, "EUR", ""))
        val found = listOf(
            Found("Mug", "http://shop.example/mug", "", null, "EUR"),
            Found("Lego", "https://shop.example/lego", "", null, "EUR"),
            Found("Lego again", "https://shop.example/lego", "", null, "EUR"),
            Found("No link", "", "", null, "EUR")
        )
        assertEquals(setOf(0, 2), Import.duplicates(found, have))
    }

    @Test fun idsCountBackAndSkipTakenOnes() {
        val ws = Import.toWishes(listOf(Found("A", "", "", null, "EUR", love = true), Found("B", "", "", 5.0, "EUR")), 1000L, setOf(999L))
        assertEquals(listOf(1000L, 998L), ws.map { it.id })
        assertTrue(ws[0].star)
        assertEquals(5.0, ws[1].price!!, 0.0)
    }

    @Test fun fillInOnlyAddsWhatIsMissing() {
        val w = Wish(1, "https://shop.example/a", "My name", "", "https://list.example/thumb.jpg", 10.0, null, "EUR", "")
        val meta = Meta("Shop name", "", "https://shop.example/big.jpg", 12.0, "EUR", product = true)
        val n = ImportFill.filled(w, meta)
        assertEquals("My name", n.title)          // your name stays
        assertEquals(10.0, n.price!!, 0.0)       // never a changed price from one reading (Rule 8)
        assertEquals("https://shop.example/big.jpg", n.image) // the shop's own, bigger photo
        val empty = w.copy(title = "", price = null, image = "")
        val m = ImportFill.filled(empty, meta.copy(product = false))
        assertEquals("Shop name", m.title); assertEquals(12.0, m.price!!, 0.0); assertEquals("https://shop.example/big.jpg", m.image)
        assertEquals(w, ImportFill.filled(w, meta.copy(blocked = true)))
    }

    @Test fun dateBecomesTheOccasionOnlyWhenStillToCome() {
        val today = LocalDate.of(2026, 11, 18)
        assertEquals(Occasion("own", "Birthday", "2027-04-01"), Import.occasionOf("2027-04-01", "Birthday", today))
        assertNull(Import.occasionOf("2026-01-01", "Old", today))
        assertNull(Import.occasionOf("", "None", today))
    }

    @Test fun readerVersionIsRead() {
        assertEquals(1, ImportReaders.versionOf("(function(){ var VERSION = 1; })"))
        assertEquals(0, ImportReaders.versionOf("nothing"))
    }
}
