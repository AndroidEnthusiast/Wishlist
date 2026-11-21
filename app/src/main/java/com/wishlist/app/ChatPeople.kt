package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * "Chat about this wish" on a shared (collab) wishlist (owner, 21 November 2026; docs/decisions/wish-cards.md, last
 * section): the ⋮ asks ChatAboutFlow; the host shows who is on the list. Friends can be tapped (their chat opens with
 * the wish attached); the others are greyed, "Not your friend yet", with Add friend. Exactly one other person who is
 * your friend: their chat opens at once. The last answer is kept on the phone, so this works offline too.
 */

/** The wish waiting for a person to chat with, and the shared list it is on. */
object ChatAboutFlow {
    var asking by mutableStateOf<Pair<String, ChatWish>?>(null)

    /** "Chat about this wish" in the ⋮ of [w] on the shared list [code]. */
    fun ask(code: String, w: Wish) { asking = code to ChatWish.of(w, code) }
}

/** The picker's state holder: the people (kept answer first, then the server's), and friend requests sent here. */
@Stable
internal class ChatPeopleModel(private val ctx: Context, private val list: SharedList, private val scope: CoroutineScope) {
    var people by mutableStateOf<List<ListPerson>?>(null)
    // "loading" (asking the server), "ok", "offline" (the server couldn't be reached and nothing was kept) or "old" (the
    // server doesn't know this yet).
    var status by mutableStateOf("loading")
    var sent by mutableStateOf(emptySet<String>())

    private fun mine(p: List<ListPerson>) =
        SharedPeople.withFriends(p, Friends.state?.friends?.map { it.id }?.toSet().orEmpty())

    fun load() {
        SharedPeople.cached(ctx, list.code)?.let { people = mine(it) }
        status = "loading"
        scope.launch {
            status = try { people = mine(SharedPeople.fetch(ctx, list)); "ok" } catch (e: Exception) {
                when {
                    people != null -> "ok"
                    // The server script (supabase-features.sql, version 2) wasn't run yet: not the person's connection.
                    (e.message ?: "").let { it.contains("wl_sh_people") || it.contains("PGRST202") } -> "old"
                    else -> "offline"
                }
            }
        }
    }

    /** Add friend: the normal request with approval; says what happened. */
    fun befriend(p: ListPerson, snack: SnackbarHostState) {
        sent = sent + p.id
        scope.launch {
            val msg = try {
                when (SharedPeople.befriend(ctx, list, p.id)) {
                    "friends" -> {
                        people = people?.map { if (it.id == p.id) it.copy(friend = "yes") else it }
                        Friends.refresh(ctx)
                        tr("You and %s are friends now", p.name)
                    }
                    "already" -> tr("Your friend request to %s is waiting for approval", p.name)
                    else -> tr("Friend request sent to %s", p.name)
                }
            } catch (e: Exception) {
                sent = sent - p.id
                tr("Couldn’t reach the server. Check your connection.")
            }
            snack.toastSoon(msg)
        }
    }
}

/** Shows the picker while ChatAboutFlow asks (AppPopups). */
@Composable
fun ChatAboutHost(snack: SnackbarHostState, go: (Deep) -> Unit) {
    val (code, wish) = ChatAboutFlow.asking ?: return
    val list = Shared.lists.firstOrNull { it.code == code }
    if (list == null) { LaunchedEffect(code) { ChatAboutFlow.asking = null }; return }
    androidx.compose.runtime.key(code, wish.id) {
        ChatPeopleSheet(list, snack, onClose = { ChatAboutFlow.asking = null }) { id ->
            ChatAboutFlow.asking = null
            go(Deep.Chat(id, wish))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPeopleSheet(list: SharedList, snack: SnackbarHostState, onClose: () -> Unit, onChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(list.code) { ChatPeopleModel(ctx, list, scope) }
    LaunchedEffect(list.code) { model.load() }
    val people = model.people
    // Only one other person, and a friend: straight to the chat (owner, 21 November 2026).
    val direct = people?.let { SharedPeople.direct(it) }
    if (direct != null) { LaunchedEffect(direct) { onChat(direct) }; return }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        ChatPeopleList(people, model.status, model.sent, onChat, onAdd = { model.befriend(it, snack) }, onRetry = { model.load() })
    }
}

/**
 * What the picker shows (only draws; ChatPeopleScreenshotTest): the people, a circle while asking the first time,
 * "Can't see who's on this list right now" with Try again offline, or why nobody can be picked yet.
 */
@Composable
internal fun ChatPeopleList(
    people: List<ListPerson>?, status: String, sent: Set<String>,
    onChat: (String) -> Unit, onAdd: (ListPerson) -> Unit, onRetry: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(tr("Chat about this wish with…"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        when {
            people == null && status == "loading" -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            people == null -> Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    if (status == "old") tr("This needs an update of the app’s server first. Try again later.")
                    else tr("Can’t see who’s on this list right now. Check your connection."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(tr("Try again")) }
            }
            people.isEmpty() -> Text(
                tr("Nobody else on this list can chat yet. People show here once they’ve opened the list in the latest app."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            else -> people.forEach { p ->
                PersonRow(p, requested = p.id in sent, onChat = { onChat(p.id) }, onAdd = { onAdd(p) })
            }
        }
    }
}

/** One person: a friend to tap, or greyed with Add friend (the names come from other phones: plain text only). */
@Composable
private fun PersonRow(p: ListPerson, requested: Boolean, onChat: () -> Unit, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .then(if (p.isFriend) Modifier.clickable(onClickLabel = tr("Chat"), onClick = onChat) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.alpha(if (p.isFriend) 1f else 0.6f)) { Avatar(Friends.photos[p.id], p.name, 40.dp) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(p.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (p.isFriend) cs.onSurface else cs.onSurfaceVariant)
            if (!p.isFriend) Text(
                if (p.friend == "asked" || requested) tr("Friend request waiting") else tr("Not your friend yet"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        if (!p.isFriend && p.friend != "asked") TextButton(enabled = !requested, onClick = onAdd) {
            Text(if (requested) tr("Request sent") else tr("Add friend"))
        }
    }
}
