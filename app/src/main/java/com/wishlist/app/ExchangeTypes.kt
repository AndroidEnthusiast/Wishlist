package com.wishlist.app

import java.time.LocalDate

// The shapes of gift exchange data (moved here from Exchange.kt in the data rebuild, owner 19 November 2026).

data class GxGiftee(val id: String, val name: String, val pv: Long, val web: Boolean, val list: GroupList?)

data class GxMe(
    val list: GroupList?, val seen: Boolean, val waiting: Boolean, val hasSanta: Boolean, val giftee: GxGiftee?,
    val unreadGiftee: Int, val unreadSanta: Int
)

data class GxMember(
    val id: String, val name: String, val pv: Long, val web: Boolean, val code: String, val friend: Boolean,
    /** The draw check (organiser only): opened their name. Null for everyone else. */
    val seen: Boolean?,
    val waiting: Boolean,
    /** After the reveal: whom they had. */
    val gaveTo: String?
)

/** A draw that the rules make impossible: these people would only have [left] others to draw between them. */
data class GxStuck(val names: List<String>, val left: Int)

data class Exchange(
    val id: String, val link: String, val name: String, val organiser: String, val organiserName: String,
    val budget: Double?, val currency: String, val day: LocalDate?, val theme: String, val message: String, val pv: Long,
    val drawn: Boolean, val revealed: Boolean, val members: List<GxMember>,
    /** Open invites: profile id to name. */
    val invited: List<Pair<String, String>>,
    /** The rules (organiser only): giver may not draw receiver. */
    val excl: List<Pair<String, String>>,
    val blocked: GxStuck?, val me: GxMe
) {
    fun nameOf(id: String) = members.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: tr("Someone")
}

data class GxInvite(
    val gx: String, val name: String, val link: String, val by: String, val byName: String, val theme: String,
    val budget: Double?, val currency: String, val day: LocalDate?, val count: Int
)

data class GxPreview(
    val id: String, val name: String, val organiser: String, val count: Int, val theme: String, val budget: Double?,
    val currency: String, val day: LocalDate?, val drawn: Boolean
)

data class GxMsg(val id: Long, val mine: Boolean, val body: String, val at: String, val read: Boolean)

/** The themes: their emoji and the words for the countdown. */
object GxThemes {
    val all = listOf("xmas", "sint", "bday", "office", "plain")
    fun label(t: String) = when (t) {
        "sint" -> tr("Sinterklaas"); "bday" -> tr("Birthday"); "office" -> tr("Office"); "plain" -> tr("Plain"); else -> tr("Christmas")
    }
    fun emoji(t: String) = when (t) { "sint" -> "🎁"; "bday" -> "🎈"; "office" -> "☕"; "plain" -> "✨"; else -> "🎄" }
}

/** Which anonymous chat is on screen ("<id>:santa" or "<id>:giftee"), so no notification comes for it. */
object GxChatNav {
    @Volatile
    var open: String? = null
}
