package com.wishlist.app

/*
 * Value levels (owner, 27 November 2026, chosen from previews; docs/decisions/home-and-kinds.md): your Total value
 * earns a level, shown only to you (Home summary and your own lists' summary; never friends' pages, groups, Co-op
 * lists or the web), and switched off in App settings > Display > Value levels. No screens here (ValueLevelsTest).
 */

/** The four levels, from the amount in euros (or a currency close to it) where each starts. */
enum class ValueLevel(val label: String, val from: Int) {
    RISING("Rising", 1_000), MAIN("Main character", 2_500), ENERGY("Big wish energy", 5_000), ICONIC("Iconic", 10_000)
}

object ValueLevels {
    /** Show's key of the App settings switch (on at first). */
    const val SHOW = "value_levels"

    /** Currencies close enough to the euro that the same numbers count (owner: "same numbers, converted when far off"). */
    private val NEAR = setOf("EUR", "USD", "GBP", "CHF", "CAD", "AUD", "NZD", "SGD")

    /** Roughly how many of a far-off currency make one euro (fixed in the app; levels only need the rough size). */
    private val PER_EURO = mapOf(
        "JPY" to 160.0, "KRW" to 1450.0, "CNY" to 7.7, "HKD" to 8.4, "TWD" to 34.0, "INR" to 90.0, "IDR" to 17000.0,
        "THB" to 37.0, "PHP" to 60.0, "MYR" to 4.8, "SEK" to 11.5, "NOK" to 11.7, "DKK" to 7.46, "ISK" to 150.0,
        "PLN" to 4.3, "CZK" to 25.0, "HUF" to 400.0, "RON" to 5.0, "BGN" to 1.96, "TRY" to 36.0, "UAH" to 45.0,
        "BRL" to 6.0, "MXN" to 20.0, "CLP" to 1000.0, "COP" to 4500.0, "ZAR" to 20.0, "ILS" to 4.0, "AED" to 4.0,
        "SAR" to 4.0, "EGP" to 52.0, "NGN" to 1700.0
    )

    /** How many of [cur] count as one euro here; null for a currency the app doesn't know (then no level). */
    fun perEuro(cur: String): Double? = if (cur.uppercase() in NEAR) 1.0 else PER_EURO[cur.uppercase()]

    /** The level of [amount] in [cur], or null below the first one. */
    fun of(amount: Double, cur: String): ValueLevel? {
        val f = perEuro(cur) ?: return null
        return ValueLevel.entries.lastOrNull { amount >= it.from * f }
    }

    /** The level of a total shown as its biggest currency (as on the tiles): [sums] per currency. */
    fun of(sums: List<Pair<String, Double>>): ValueLevel? = sums.maxByOrNull { it.second }?.let { of(it.second, it.first) }

    /** Home's level: the same wishes and amounts as Home's Total value. */
    fun ofHome(ws: List<Wish>): ValueLevel? = of(homeSums(ws))

    /** A list's level: the same amounts as its summary's Total value. */
    fun ofList(ws: List<Wish>): ValueLevel? = of(listSums(ws))
}
