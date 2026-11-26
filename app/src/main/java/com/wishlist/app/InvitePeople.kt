package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Sharing a Co-op list (owner, 26 November 2026, a screen recording: "messy and busy", the friend she had just invited
 * wasn't shown, a naked link): one sheet, the same right after making the list and later. A line on what people can
 * do, the people on it and invited with their status, "+ Add friends" last, one "Share invite link" (the phone's share
 * menu, which can copy too), a short trust note. No link on screen. docs/decisions/people.md, last section.
 */

/** Someone on the list or invited: their profile id, name and whether they joined yet. */
data class CoopPerson(val id: String, val name: String, val joined: Boolean)

/** The sheet's state holder: who is on the list and invited, loading, and opening the friend picker. */
@Stable
internal class InvitePeopleState(private val ctx: Context, val list: SharedList, private val scope: CoroutineScope) {
    var people by mutableStateOf<List<CoopPerson>?>(null)
    var failed by mutableStateOf(false)
    var adding by mutableStateOf(false)
    var attempt by mutableIntStateOf(0)

    /** Friends who could still be invited (the "+ Add friends" row shows when there is one). */
    fun friendsLeft(): Int {
        val on = people?.map { it.id }?.toSet() ?: return 0
        val forWho = Shared.forName(ctx, list.code)
        return Friends.state?.friends.orEmpty().count { it.id !in on && !Shared.isFor(forWho, it.name) }
    }

    suspend fun load() {
        if (!Friends.hasProfile(ctx)) { people = emptyList(); return }
        failed = false
        people = try {
            val (members, invited) = GiftInvites.who(ctx, list)
            val me = Chat.me(ctx)
            // Names: your friends', else the list's people (names come from other phones: only ever plain text).
            val names = SharedPeople.cached(ctx, list.code).orEmpty().associate { it.id to it.name } +
                Friends.state?.friends.orEmpty().associate { it.id to it.name }
            members.filter { it != me }.map { CoopPerson(it, names[it].orEmpty(), true) } +
                invited.filter { it != me }.map { CoopPerson(it, names[it].orEmpty(), false) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
            null
        }
    }

    fun share(title: String) {
        scope.launch { ShareCard.share(ctx, ShareCard.Kind.INVITE, title, inviteLink(list.key)) }
    }
}

/**
 * The share sheet of Co-op list [l] named [title]; [justMade]: right after making it ("“Name” is ready"). Swipe down or
 * tap outside to close ([onClose]).
 */
@Composable
fun InvitePeopleSheet(l: SharedList, title: String, justMade: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember(l.code) { InvitePeopleState(ctx, l, scope) }
    LaunchedEffect(st.attempt) { st.load() }
    // "+ Add friends": the friend picker, then back here with the new invitations showing.
    if (st.adding) { GiftInviteFriendsDialog(l) { st.adding = false; st.attempt++ }; return }
    val forWho = remember(l.code) { Shared.forName(ctx, l.code) }
    PeopleShareSheet(
        title = if (justMade) tr("“%s” is ready", title) else tr("Share “%s”", title),
        note = tr("Everyone on it can add, edit and reserve gifts."),
        warn = forWho.takeIf { it.isNotBlank() }?.let { tr("Don’t share it with %s: it’s a surprise.", it) },
        people = if (Friends.hasProfile(ctx)) st.people else emptyList(), failed = st.failed, onRetry = { st.attempt++ },
        left = st.friendsLeft(), onAdd = { st.adding = true }, onShare = { st.share(title) }, onClose = onClose
    )
}

/**
 * The one share sheet for Co-op lists, wishlist groups and gift exchanges (owner, 26 November 2026: neat, the same
 * everywhere): [title], [note] on what people can do, a [warn]ing (the surprise), the [people] on it and invited (null
 * while loading), "+ Add friends" ([left] friends could still be invited), one "Share invite link", the trust note.
 */
@Composable
fun PeopleShareSheet(
    title: String, note: String, warn: String?, people: List<CoopPerson>?, failed: Boolean, onRetry: () -> Unit,
    left: Int, onAdd: () -> Unit, onShare: () -> Unit, onClose: () -> Unit
) {
    PickSheet(title, onClose = onClose, note = note, action = tr("Share invite link"), onAction = onShare) {
        if (warn != null) PickNote(warn)
        PickHeading(tr("People"))
        when {
            failed -> {
                PickNote(tr("Couldn’t reach the server. Check your connection."), error = true)
                Box(Modifier.padding(horizontal = 16.dp)) { TextButton(onClick = onRetry) { Text(tr("Try again")) } }
            }
            people == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            else -> {
                val rows = people.map<CoopPerson, @Composable () -> Unit> { p ->
                    {
                        SheetRow(
                            { Avatar(Friends.photos[p.id], p.name, 32.dp) }, p.name.ifBlank { tr("Someone") }, {},
                            detail = if (p.joined) tr("On the list") else tr("Invited"), tile = false
                        )
                    }
                } + listOf<@Composable () -> Unit> {
                    SheetRow(
                        { Icon(AppIcons.Add, null, tint = MaterialTheme.colorScheme.primary) }, tr("Add friends"), onAdd,
                        enabled = left > 0, detail = if (left == 0) tr("All your friends are on it or invited") else null
                    )
                }
                SheetGroup(rows)
            }
        }
        PickNote(tr("Only share the link with people you trust."))
    }
}

/** A wishlist group's share sheet: its members and open invitations, "+ Add friends" ([onAdd]), the group link. */
@Composable
fun GroupShareSheet(g: Group, onAdd: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val me = remember { Chat.me(ctx) }
    val friends = Friends.state?.friends.orEmpty()
    val names = friends.associate { it.id to it.name }
    val people = g.members.filter { it.id != me }.map { CoopPerson(it.id, it.name, true) } +
        g.invited.filter { id -> id != me && g.members.none { it.id == id } }.map { CoopPerson(it, names[it].orEmpty(), false) }
    val taken = people.map { it.id }.toSet() + me
    PeopleShareSheet(
        title = tr("Share “%s”", g.name), note = tr("Everyone in it can see each other’s wishlists."), warn = null,
        people = people, failed = false, onRetry = {}, left = friends.count { it.id !in taken }, onAdd = onAdd,
        onShare = { Groups.share(ctx, g) }, onClose = onClose
    )
}

/** A gift exchange's share sheet: its people and open invitations, "+ Add friends" ([onAdd]), the exchange link. */
@Composable
fun ExchangeShareSheet(x: Exchange, onAdd: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val me = remember { Chat.me(ctx) }
    val friends = Friends.state?.friends.orEmpty()
    val people = x.members.filter { it.id != me }.map { CoopPerson(it.id, it.name, true) } +
        x.invited.filter { (id, _) -> id != me && x.members.none { it.id == id } }.map { (id, n) -> CoopPerson(id, n, false) }
    val taken = people.map { it.id }.toSet() + me
    PeopleShareSheet(
        title = tr("Share “%s”", x.name), note = tr("Everyone in it draws a name and gives a gift."), warn = null,
        people = people, failed = false, onRetry = {}, left = friends.count { it.id !in taken }, onAdd = onAdd,
        onShare = { Exchanges.share(ctx, x) }, onClose = onClose
    )
}
