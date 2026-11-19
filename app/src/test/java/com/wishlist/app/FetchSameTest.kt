package com.wishlist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Locale
import org.robolectric.annotation.Config as Robo

/**
 * The before/after check of price reading in the data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`,
 * "The data rebuild"). Every saved shop page (`tests/shared/same-answers.json` → pages) and a set of extra pages
 * (blocked, not found, Amazon, a price in words, Funda, names with the shop in them) are read, and every rule is given
 * many inputs: cleaned links, numbers, currencies (also per phone country), names, blocked titles, pictures, the reader
 * upgrade and the currency choice. Everything read is written down in full. No page is fetched from the internet.
 * The result must match `src/test/resources/fetch-same.txt` word for word: recorded with Fetch.kt as it was before the
 * rebuild (2.262). A MEANT change: delete the file, run once to record, read the difference in git. (That the app and
 * the web pages agree is SameAnswersTest + tests/shared/test.js.)
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34])
class FetchSameTest {
    private val c get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val golden = File("src/test/resources/fetch-same.txt")
    private val out = StringBuilder()
    private val start = System.currentTimeMillis()

    private fun line(s: String) { out.append(s).append('\n') }

    private fun read(name: String, html: String, url: String, status: Int = 200) {
        val d = Jsoup.parse(html, url)
        val host = java.net.URI(url).host.orEmpty()
        line("page '$name' $url $status")
        line("  meta=${parsePage(d, host, status)}")
        line("  product=${ldProduct(d, url)} shown=${shownPrice(d, host)}")
        line("  foreign=${d.select("a, div, section, li").count { foreign(it, url) }}")
    }

    @Test fun priceReadingIsExactlyTheSame() {
        val p = c.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
        p.edit().clear().commit()
        val shared = listOf("../tests/shared/same-answers.json", "tests/shared/same-answers.json").map(::File).first { it.exists() }
        val pages = JSONObject(shared.readText()).getJSONArray("pages")
        line("## the saved pages")
        for (i in 0 until pages.length()) pages.getJSONObject(i).let { o ->
            read(o.getString("name"), o.getString("html"), o.getString("url"), o.optInt("status", 200))
        }

        line("## extra pages")
        read("robot check", "<html><head><title>Robot Check</title></head><body>Type the characters</body></html>", "https://www.amazon.nl/dp/B01")
        read("captcha with a price", "<html><head><title>Just a moment...</title><meta property='og:price:amount' content='9.99'></head></html>", "https://shop.example/p")
        read("not found", "<html><head><title>Page not found</title></head><body><div class='related'>€ 12,99</div></body></html>", "https://shop.example/x", 404)
        read("amazon deal", "<html><head><title>Lamp : Amazon.nl: Wonen</title></head><body><div id='corePriceDisplay_desktop_feature_div'><span class='a-price priceToPay'><span class='a-offscreen'>€ 24,99</span></span><span class='a-price a-text-price'><span class='a-offscreen'>€ 39,99</span></span></div><img id='landingImage' src='https://m.media-amazon.com/images/I/x.jpg'></body></html>", "https://www.amazon.nl/dp/B0ABC12345")
        read("price in words", "<html><head><title>House</title></head><body><p>Vraagprijs € 725.000 k.k.</p></body></html>", "https://www.funda.nl/koop/x/")
        read("euro thousands", "<html><head><title>Flat</title></head><body><span>€ 1.250.000</span></body></html>", "https://www.funda.nl/koop/y/")
        read("name with shop and number", "<html><head><title>Fiets (m2434550716) — Marktplaats</title><meta property='og:image' content='http://img.example/f.jpg'></head><body>€ 150</body></html>", "https://link.marktplaats.nl/m2434550716")
        read("bol name first", "<html><head><title>bol.com | Mooie lamp</title><meta property='og:title' content='bol.com | Mooie lamp'><meta property='product:price:amount' content='39.00'></head></html>", "https://www.bol.com/nl/nl/p/lamp/1/")
        read("kronor", "<html><head><title>Ljus</title></head><body><span class='price'>1.299,00 kr</span></body></html>", "https://shop.se/p/1")
        read("zloty", "<html><head><title>Świeca</title></head><body><span class='price'>20 zł</span></body></html>", "https://shop.pl/p/1")
        read("empty", "", "https://shop.example/empty")

        line("## links")
        for (u in listOf("https://www.amazon.nl/Some-Name/dp/B0ABC12345/ref=sr_1_1?tag=x&th=1", "https://www.amazon.com/gp/product/B0ABC12345?psc=1",
                "https://amazon.de/dp/B0ABC12345", "https://www.etsy.com/listing/123456/a-nice-mug?ref=shop&ga=1", "https://www.etsy.com/nl/listing/9/x",
                "https://shop.example/p?x=1", "http://shop.example/p", "ftp://x", "", "  https://shop.example/a  ", "javascript:alert(1)"))
            line("cleanUrl '$u' -> ${cleanUrl(u)}")
        for (u in listOf("http://img.example/a.jpg", "https://img.example/a.jpg", "HTTP://x/y", "data:x")) line("secure '$u' -> ${secureImage(u)}")

        line("## numbers and currencies")
        for (s in listOf("12", "12,50", "12.50", "1.299,00", "1,299.00", "1 299,00", "€ 39,99", "1.250.000", "0,99", "abc", "", ".", "12,5", "1.2.3", "99,-", "1'299.50"))
            line("toNum '$s' -> ${s.toNum()}")
        for (s in listOf("€ 1", "EUR 2", "3 kr", "£", "SEK 4", "zł 5", "¥6", "plain 7")) line("namesCurrency '$s' -> ${namesCurrency(s)}")
        for ((t, h) in listOf("12" to "shop.co.uk", "12" to "shop.de", "12" to "shop.se", "12" to "shop.pl", "12" to "shop.ch", "12" to "amazon.com",
                "12" to "amazon.co.jp", "\$12" to "shop.ca", "12 kr" to "shop.no", "€12" to "shop.com", "12" to "", "CHF 1" to "x.de"))
            line("currencyOf '$t' $h -> ${currencyOf(t, h)}")
        val keep = Locale.getDefault()
        for (l in listOf(Locale.US, Locale.UK, Locale("nl", "NL"), Locale("sv", "SE"), Locale("pl", "PL"), Locale("de", "CH"), Locale.JAPAN, Locale("xx", "ZZ"))) {
            Locale.setDefault(l)
            line("phone $l: country=${countryCurrency()} local=${localCurrency()} accept=${acceptLanguage(l)}")
        }
        Locale.setDefault(keep)
        CurrencyChoice.set(c, "SEK"); line("chosen SEK: local=${localCurrency()} saved=${p.getString("new_cur", null)}")
        CurrencyChoice.set(c, ""); CurrencyChoice.load(c); line("automatic: local=${localCurrency()} all=${CurrencyChoice.all}")

        line("## names")
        for ((t, h) in listOf("Lamp : Amazon.nl: Wonen" to "www.amazon.nl", "bol.com | Mooie lamp" to "www.bol.com", "Fiets (m2434550716) — Marktplaats" to "link.marktplaats.nl",
                "Mug - Etsy" to "www.etsy.com", "Plain name" to "shop.example", "Coolblue - alles voor een glimlach" to "www.coolblue.nl", "X | Y | Shop" to "shop.example", "" to ""))
            line("cleanTitle '$t' $h -> '${cleanTitle(t, h)}' word=${shopWord(h)}")
        for (t in listOf("Robot Check", "Just a moment...", "Access Denied", "Pardon Our Interruption", "Are you a human?", "Teapot")) line("blocked '$t' -> ${looksBlocked(t)}")

        line("## the reader upgrade")
        val old = start - 1000L
        line("quiet old=${ReaderUpgrade.quiet(c, old)} new=${ReaderUpgrade.quiet(c, System.currentTimeMillis() + 1000)}")
        ReaderUpgrade.done(c, old); ReaderUpgrade.done(c, old)
        line("after done old=${ReaderUpgrade.quiet(c, old)} saved=${p.getStringSet("rd3_done", null)}")

        val names = LinkedHashMap<String, String>()
        val now = Regex("(?<![0-9])1[0-9]{12}(?![0-9])").replace(out.toString()) { m ->
            if (m.value.toLong() < start - 5000) m.value else names.getOrPut(m.value) { "T${names.size + 1}" }
        }
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(now)
            fail("recorded ${golden.path}: run again")
        }
        assertEquals(golden.readText(), now)
    }
}
