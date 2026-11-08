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

    @Test fun occasions() {
        for (r in rows("occasion")) {
            val o = Occasion.fromJson(r.getJSONObject(0))
            val today = LocalDate.parse(r.getString(1))
            assertEquals("${r.getJSONObject(0)} on $today", r.getString(2), Occasions.line(o, today) ?: "")
        }
    }
}
