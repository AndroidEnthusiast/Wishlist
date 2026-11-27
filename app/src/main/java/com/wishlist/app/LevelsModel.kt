package com.wishlist.app

/*
 * The state of the levels page (LevelsScreen.kt; owner, 27 November 2026): your Home total, its level, the next one
 * and how far to go. No screens here (LevelsModelTest).
 */
internal class LevelsState(val amount: Double, val cur: String, val total: String, val level: ValueLevel?) {
    /** Euros count as euros when the currency is unknown, so the page still reads. */
    private val factor = ValueLevels.perEuro(cur) ?: 1.0

    /** Where level [l] starts in your currency. */
    fun startOf(l: ValueLevel): Double = l.from * factor

    val next: ValueLevel? = ValueLevel.entries.firstOrNull { it.ordinal > (level?.ordinal ?: -1) }

    /** How much more until [next] (null at the top). */
    val toGo: Double? = next?.let { (startOf(it) - amount).coerceAtLeast(0.0) }

    /** How far from this level's start to the next one's (1 at the top). */
    val progress: Float = next?.let { n ->
        val lo = level?.let { startOf(it) } ?: 0.0
        ((amount - lo) / (startOf(n) - lo)).toFloat().coerceIn(0f, 1f)
    } ?: 1f

    companion object {
        /** From your own wishes, as Home's Total value counts them. */
        fun of(ws: List<Wish>): LevelsState {
            val sums = homeSums(ws)
            val top = sums.firstOrNull()
            return LevelsState(top?.second ?: 0.0, top?.first ?: localCurrency(), homeTotal(ws), ValueLevels.of(sums))
        }
    }
}

/** The big line at the top of the page. */
internal fun levelHeadline(l: ValueLevel?): String = when (l) {
    null -> tr("Your journey starts here")
    ValueLevel.RISING -> tr("You’re rising")
    ValueLevel.MAIN -> tr("You’re the main character")
    ValueLevel.ENERGY -> tr("Big wish energy")
    ValueLevel.ICONIC -> tr("You’re Iconic")
}

/** Each level's one-liner (owner's words, 27 November 2026). */
internal fun levelLine(l: ValueLevel): String = when (l) {
    ValueLevel.RISING -> tr("Your wishlist is officially taking off.")
    ValueLevel.MAIN -> tr("All eyes on your wishlist.")
    ValueLevel.ENERGY -> tr("Dreaming big looks good on you.")
    ValueLevel.ICONIC -> tr("Legendary taste, unlocked.")
}
