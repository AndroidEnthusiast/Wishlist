package com.wishlist.app

import android.content.Context
import android.content.Intent

// Friend codes and links (a part of [Friends] since the data rebuild, owner 19 November 2026; callers keep writing
// Friends.pretty, Friends.parseFriendLink, Friends.shareCode).

/** The friend code as people read it: "K7Q-4MZ". */
fun Friends.pretty(code: String) = if (code.length == 6) code.substring(0, 3) + "-" + code.substring(3) else code


/** A friend link (…/friend/?f=CODE&n=Name, or wishlist://friend?f=…): the code and the name, or null. */
fun Friends.parseFriendLink(s: String): Pair<String, String>? {
    val m = Regex("[?&]f=([A-Za-z0-9]{6})(?![A-Za-z0-9])").find(s) ?: return null
    val n = Regex("[?&]n=([^&\\s#]+)").find(s)?.groupValues?.get(1)?.let {
        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { "" }
    }.orEmpty().take(30)
    return m.groupValues[1].uppercase() to n
}

fun Friends.shareCode(c: Context, code: String) {
    // A link that adds you with one tap (owner, 2.139); the code still works by hand.
    val link = Config.SHARE_URL.trimEnd('/') + "/friend/?f=" + code +
        (if (Shared.lastName.isNotBlank()) "&n=" + java.net.URLEncoder.encode(Shared.lastName, "UTF-8") else "")
    val text = tr(
        "Let’s be friends on Wishlist, so we can see each other’s wishlists. Tap to add me: %1\$s (or enter my friend code %2\$s under Friends > Add a friend).",
        link, pretty(code)
    )
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    c.startActivity(Intent.createChooser(i, tr("Share your friend code")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
