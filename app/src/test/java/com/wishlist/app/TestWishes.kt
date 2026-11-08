package com.wishlist.app

import java.util.Locale

/** A wish for tests: only what a test cares about needs naming. */
fun wish(
    id: Long = 1, title: String = "Wish $id", price: Double? = null, cur: String = "EUR", cat: String = "Home",
    got: Boolean = false, star: Boolean = false, qty: Int = 1, bin: Long = 0L, url: String = "", desc: String = "",
    note: String = ""
) = Wish(id, url, title, desc, "", price, null, cur, cat, got = got, star = star, note = note, bin = bin, qty = qty)

/** Runs [f] with the phone set to [l] (amounts and currencies follow the phone's language and country). */
fun <T> withLocale(l: Locale, f: () -> T): T {
    val old = Locale.getDefault()
    Locale.setDefault(l)
    try { return f() } finally { Locale.setDefault(old) }
}
