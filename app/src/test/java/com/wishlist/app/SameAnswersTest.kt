package com.wishlist.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The app's half of tests/shared/same-answers.json (ROADMAP S0.2): Fetch.kt and Occasion.kt must give the answers
 * listed there, the same ones web/parse.js and web/occasion.js give (tests/shared/test.js checks those).
 */
class SameAnswersTest {
    private val cases: JSONObject = run {
        // Gradle runs the tests in app/; from the repository root it is one folder up.
        val f = listOf("../tests/shared/same-answers.json", "tests/shared/same-answers.json").map(::File).first { it.exists() }
        JSONObject(f.readText())
    }

    private fun rows(name: String): List<JSONArray> = cases.getJSONArray(name).let { a -> (0 until a.length()).map { a.getJSONArray(it) } }

    @Test fun prices() {
        for (r in rows("toNum")) {
            val want = if (r.isNull(1)) null else r.getDouble(1)
            assertEquals("toNum \"${r.getString(0)}\"", want, r.getString(0).toNum())
        }
    }

    @Test fun currencies() {
        for (r in rows("currency")) assertEquals("${r.getString(0)} on ${r.getString(1)}", r.getString(2), currencyOf(r.getString(0), r.getString(1)))
    }

    /** Whole product pages (2.178): the same page, the same price, currency and "the page named the currency itself". */
    @Test fun pages() {
        val a = cases.getJSONArray("pages")
        for (i in 0 until a.length()) {
            val pg = a.getJSONObject(i)
            val name = pg.getString("name")
            val got = parsePage(org.jsoup.Jsoup.parse(pg.getString("html"), pg.getString("url")), pg.getString("host"), pg.getInt("status"))
            assertEquals("$name: price", if (pg.isNull("price")) null else pg.getDouble("price"), got?.price)
            if (pg.has("currency")) assertEquals("$name: currency", pg.getString("currency"), got?.currency)
            if (pg.has("sure")) assertEquals("$name: sure", pg.getBoolean("sure"), got?.currencySure)
            if (pg.has("product")) assertEquals("$name: product", pg.getBoolean("product"), got?.product)
        }
    }

    @Test fun occasions() {
        for (r in rows("occasion")) {
            val o = Occasion.fromJson(r.getJSONObject(0))
            val today = LocalDate.parse(r.getString(1))
            assertEquals("${r.getJSONObject(0)} on $today", r.getString(2), Occasions.line(o, today) ?: "")
        }
    }
}
