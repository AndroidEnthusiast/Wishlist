package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * The state holder of a shared wishlist's dialogs (the rebuild, owner 19 November 2026; MAP "How a screen is built"):
 * everything they change (make one, join, a new invite link, leave, delete for everyone, hand it over). The dialogs
 * (SharedUi.kt, SharedManage.kt) only draw; the lists and the server calls stay in Shared, Members and Cloud.
 */
@Stable
internal class SharedListModel(private val ctx: Context) {
    /**
     * A new wishlist "With others": made online, then who it's for (kept on this phone, the surprise), its description
     * and its occasion (both best effort: without the newest server script they can be added later). Its code.
     */
    suspend fun create(name: String, me: String, forWho: String, notes: String, occ: Occasion?): String {
        val l = Shared.create(ctx, name, me)
        if (forWho.isNotBlank()) Shared.setForName(ctx, l.code, forWho)
        // The notes become the list's note for everyone on it.
        if (notes.isNotBlank()) {
            try {
                Cloud.shAbout(l.key, notes.trim().take(ABOUT_MAX))
            } catch (e: Exception) {
            }
        }
        // The occasion too; without the new script it can be added later in the list's settings.
        occ?.let {
            try {
                Cloud.shOccasion(l.key, it)
            } catch (e: Exception) {
            }
        }
        return l.code
    }

    /** The name of the list an invitation [key] leads to, or null when it doesn't work (any more). */
    suspend fun inviteName(key: String): String? = Cloud.shInfo(key)
    suspend fun join(key: String, me: String): SharedList? = Shared.join(ctx, key, me)
    suspend fun resetKey() = Shared.resetKey(ctx)
    fun leave(code: String) = Shared.leave(ctx, code)
    suspend fun deleteForEveryone() = Shared.deleteForEveryone(ctx)
    suspend fun members(l: SharedList): List<Member> = Members.list(ctx, l)
    /** The person who made the list leaves: [to] becomes its owner first. */
    suspend fun handOverAndLeave(l: SharedList, to: String) { Members.handOver(ctx, l, to); Shared.leave(ctx, l.code) }
}

/** A shared wishlist dialogs' state holder. */
@Composable
internal fun rememberSharedList(): SharedListModel {
    val ctx = LocalContext.current
    return remember { SharedListModel(ctx) }
}
