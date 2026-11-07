package com.wishlist.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * "Gifts I reserved" (side menu, owner 6 November 2026): everything this phone reserved, on friends' wishlists (also
 * a group member's or a gift-exchange giftee's list) and on gift lists, grouped per list. Tapping a list opens it.
 * Just the list, no "Bought" tick, and no line about other phones or the browser (owner's choices).
 *  - Friends' lists: what this phone remembered when it reserved (FriendWatch, it also tells you when a reserved gift
 *    is taken off), checked online against the reservations that are really still yours.
 *  - Gift lists: the reservations of this phone online (wl_get_reservations), titles from the list's copy on the phone.
 * Offline it shows what the phone remembers.
 */

/** One list with your reservations on it. [gift]: a gift list (opens as one), otherwise a friend's list. */
data class ReservedGroup(val code: String, val name: String, val gift: Boolean, val titles: List<String>)

object ReservedGifts {
    /** What the phone remembers, without asking the server. */
    fun remembered(c: android.content.Context): List<ReservedGroup> =
        FriendWatch.all(c).map { (code, m) ->
            ReservedGroup(code, FriendWatch.label(c, code).ifBlank { Repo.friends.firstOrNull { it.code == code }?.name.orEmpty() }, false, m.values.toList())
        }

    /** Checked online: only what is still reserved by this phone. Lists that can't be reached keep what the phone remembers. */
    suspend fun load(c: android.content.Context): List<ReservedGroup> {
        val out = ArrayList<ReservedGroup>()
        for ((code, m) in FriendWatch.all(c)) {
            val mine = try {
                Cloud.fetchReservations(code, Repo.deviceId).filterValues { it }.keys
            } catch (e: Exception) {
                null
            }
            val titles = if (mine == null) m.values.toList() else m.filterKeys { it in mine }.values.toList()
            if (titles.isEmpty()) continue
            out += ReservedGroup(code, FriendWatch.label(c, code).ifBlank { Repo.friends.firstOrNull { it.code == code }?.name.orEmpty() }, false, titles)
        }
        for (sl in Shared.lists) {
            val mine = try {
                Cloud.fetchReservations(sl.code, Repo.deviceId).filterValues { it }.keys
            } catch (e: Exception) {
                continue
            }
            if (mine.isEmpty()) continue
            val wishes = Shared.cachedWishes(c, sl.code).associateBy { it.id }
            val titles = mine.mapNotNull { id -> wishes[id]?.takeIf { it.bin == 0L }?.label }
            if (titles.isNotEmpty()) out += ReservedGroup(sl.code, sl.name, true, titles)
        }
        return out
    }
}

/** The "Gifts I reserved" page: full screen, ← closes it. */
@Composable
fun ReservedScreen(onClose: () -> Unit, onOpen: (ReservedGroup) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var groups by remember { mutableStateOf(ReservedGifts.remembered(ctx)) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        groups = ReservedGifts.load(ctx)
        loading = false
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(AppIcons.ArrowBack, tr("Back")) }
                    Text(tr("Gifts I reserved"), style = MaterialTheme.typography.titleLarge, fontWeight = LocalLook.current.titleWeight, modifier = Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                }
                // Every empty page says what to do next (owner, 7 November 2026).
                if (groups.isEmpty() && !loading) {
                    Text(
                        tr("Nothing reserved yet. Open a friend’s wishlist, tap Reserve on a gift you’ll buy, and it shows here. They never see who reserved it."),
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = { onClose(); FriendsNav.openTab = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(tr("Go to Friends"))
                    }
                }
                groups.forEach { g ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(g) }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (g.gift) AppIcons.Gift else AppIcons.Favorite, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                g.name.ifBlank { tr("A friend’s wishlist") }, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        g.titles.forEach { t ->
                            Text("• $t", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 26.dp))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ("Price watch > Gifts I reserved" of 2.138 and its ReservedPrices were dropped, owner 7 November 2026.)
