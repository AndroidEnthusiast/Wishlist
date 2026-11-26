package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/*
 * The state holder of the gift exchange screens (the rebuild, owner 19 November 2026; MAP "How a screen is built"):
 * the screens (ExchangeScreen.kt and the others) only draw; everything that changes an exchange goes through here.
 * The data and the server calls stay in Exchanges (Exchange.kt).
 */
@Stable
internal class ExchangeModel(private val ctx: Context) {
    // ---- starting, joining, leaving ----
    suspend fun create(name: String, listId: String, budget: Double?, currency: String, day: LocalDate?, theme: String, message: String): String =
        Exchanges.create(ctx, name, listId, budget, currency, day, theme, message)
    suspend fun join(link: String?, gx: String?, listId: String): String? = Exchanges.join(ctx, link, gx, listId)
    suspend fun decline(gx: String) = Exchanges.decline(ctx, gx)
    suspend fun setList(gx: String, listId: String) = Exchanges.setList(ctx, gx, listId)
    /** A wishlist that was "Only me" goes online for the exchange with its friends' link off (as for groups). */
    suspend fun listToFriends(listId: String): String = ListVisibility.set(ctx, listId, Who.FRIENDS)
    suspend fun leave(gx: String, next: String?) = Exchanges.leave(ctx, gx, next)
    suspend fun cancel(gx: String) = Exchanges.cancel(ctx, gx)

    // ---- the organiser ----
    suspend fun invite(x: Exchange, ids: List<String>): Int = Exchanges.invite(ctx, x, ids)
    suspend fun addLate(gx: String, member: String) = Exchanges.addLate(ctx, gx, member)
    suspend fun remove(gx: String, member: String) = Exchanges.remove(ctx, gx, member)
    suspend fun addRule(gx: String, giver: String, receiver: String, both: Boolean): GxStuck? = Exchanges.addRule(ctx, gx, giver, receiver, both)
    suspend fun removeRule(gx: String, giver: String, receiver: String, both: Boolean) = Exchanges.removeRule(ctx, gx, giver, receiver, both)
    suspend fun draw(gx: String, without: Boolean): Exchanges.DrawResult = Exchanges.draw(ctx, gx, without)
    suspend fun reveal(gx: String): Boolean = Exchanges.reveal(ctx, gx)
    suspend fun handover(gx: String, next: String) = Exchanges.handover(ctx, gx, next)
    suspend fun saveSettings(gx: String, name: String, budget: Double?, currency: String, day: LocalDate?, theme: String, message: String) =
        Exchanges.saveSettings(ctx, gx, name, budget, currency, day, theme, message)
    suspend fun setPhoto(gx: String, src: String?, edit: HeroEdit, fa: Float) = Exchanges.setPhoto(ctx, gx, src, edit, fa)

    // ---- your name and the anonymous chats ----
    /** Opening the name you drew (it counts as seen for the organiser's draw check). */
    suspend fun open(gx: String): Exchange? = Exchanges.open(ctx, gx)
    suspend fun send(gx: String, side: String, body: String): Boolean = Exchanges.send(ctx, gx, side, body)
    suspend fun seen(gx: String, side: String, upto: Long) = Exchanges.seen(ctx, gx, side, upto)
}

/** The gift exchange screens' state holder, one per screen. */
@Composable
internal fun rememberExchange(): ExchangeModel {
    val ctx = LocalContext.current
    return remember { ExchangeModel(ctx) }
}

/** What is open on an exchange's screen (its menu, which dialog, the draw's questions, the confetti). */
@Stable
internal class ExchangeScreenState {
    var menu by mutableStateOf(false)
    var sharing by mutableStateOf(false)
    var inviting by mutableStateOf(false)
    var changing by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var leaving by mutableStateOf(false)
    var removing by mutableStateOf<GxMember?>(null)
    var adding by mutableStateOf<GxMember?>(null)
    var addingRule by mutableStateOf(false)
    var drawAsk by mutableStateOf(false)
    var pendingAsk by mutableStateOf<List<String>?>(null)
    var revealAsk by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var confetti by mutableStateOf(0)
    var showList by mutableStateOf(false)
    var chatSide by mutableStateOf<String?>(null)
}
