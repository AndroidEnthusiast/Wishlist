package com.wishlist.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/*
 * Inviting Friends to a gift list (owner, 7 November 2026 evening; supabase-together.sql):
 *  - Everyone on a gift list can invite their Friends from its share screen ("Invite Friends"), next to the link.
 *  - The Friend gets an invitation to accept: a notification and a chat card (no card on Home, owner's choice). Both
 *    open the invitation: who invited, the list, its occasion, the givers and a few pictures, with Join / No thanks.
 *  - The list's edit key is never in the chat or the invitation: the server only gives it to the invited person when
 *    they press Join. "No thanks" tells whoever invited, quietly (their Notifications page, no buzz).
 *
 * News (the same script): the server keeps short news for each phone (an invitation, a "Still buying …?" question and
 * its answers) and wakes the phone with a push at once. [News.poll] fetches it, tells it, and deletes it online.
 */

data class GiftInviteInfo(
    val code: String, val name: String, val by: String, val givers: List<String>, val count: Int,
    val images: List<String>, val occ: Occasion?
)

/** Which invitation to show (a tapped notification or chat card). */
object GiftInviteNav {
    var open by mutableStateOf<String?>(null)
}

object GiftInvites {
    private fun str(t: String) = t.trim().removeSurrounding("\"")

    /** What the invitation shows, or null when you aren't invited (any more). Throws when offline. */
    suspend fun info(c: Context, code: String): GiftInviteInfo? {
        val auth = Friends.auth(c) ?: return null
        val t = Cloud.rpc("wl_sh_inv_info", auth.put("p_code", code)).trim()
        if (t.isEmpty() || t == "null") return null
        val o = JSONObject(t)
        fun names(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        return GiftInviteInfo(
            o.optString("code"), o.optString("name"), o.optString("by"), names(o.optJSONArray("givers")),
            o.optInt("count"), names(o.optJSONArray("images")), Occasion.fromJson(o.optJSONObject("occ"))
        )
    }

    /** Join: the server gives the edit key now; the list is added to this phone. Null when the invitation is gone. */
    suspend fun accept(c: Context, code: String): SharedList? {
        val auth = Friends.auth(c) ?: return null
        val key = str(Cloud.rpc("wl_sh_inv_accept", auth.put("p_code", code)))
        if (key.isEmpty() || key == "null") return null
        Notifications.markRead(c, "gl_invite", "i:$code", 0L)
        return Shared.join(c, key, Shared.lastName)
    }

    suspend fun decline(c: Context, code: String) {
        val auth = Friends.auth(c) ?: return
        Cloud.rpc("wl_sh_inv_decline", auth.put("p_code", code))
    }

    /** Who of your Friends is on list [l] already, or invited: profile ids. Throws when offline. */
    suspend fun taken(c: Context, l: SharedList): Set<String> {
        val auth = Friends.auth(c) ?: return emptySet()
        val o = JSONObject(Cloud.rpc("wl_sh_invited", auth.put("p_key", l.key)))
        val out = HashSet<String>()
        for (k in listOf("members", "invited")) o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) out.add(a.optString(i)) }
        return out
    }

    /**
     * Invites Friends [ids] to list [l]: online (the notification) and a chat card (only the list's code and name, never
     * its key). Returns how many couldn't be invited.
     */
    suspend fun invite(c: Context, l: SharedList, ids: List<String>): Int {
        val name = l.name.ifBlank { Repo.listName }
        var failed = 0
        for (id in ids) {
            val auth = Friends.auth(c) ?: return ids.size
            try {
                Cloud.rpc("wl_sh_invite", auth.put("p_key", l.key).put("p_friend", id))
            } catch (e: Exception) {
                failed++
                continue
            }
            try {
                Chat.send(c, id, ChatContent(true, "giftlist", l.code, name))
            } catch (e: Exception) {
            }
        }
        return failed
    }

    /** News: you were invited. A notification (always, like group invitations) and a line on the Notifications page. */
    fun told(c: Context, code: String, name: String, by: String) {
        if (Shared.lists.any { it.code == code }) return
        val who = by.ifBlank { tr("Someone") }
        Notifications.add(c, "gl_invite", "i:$code", name, extra = who)
        Alerts.notifyGiftInvite(c, code, who, name)
    }
}

/** The server's news for this phone, told once (see the top of this file). */
object News {
    private val lock = Mutex()
    private var linked = false

    /** Fetches and tells the news. Quietly does nothing without a profile or connection. */
    suspend fun poll(c: Context) {
        if (Friends.auth(c) == null) return
        lock.withLock {
            // This phone's device id (used for reservations) belongs to this profile, so a question about a gift you
            // reserved reaches you with a push.
            if (!linked) try {
                Cloud.rpc("wl_dev_link", Friends.auth(c)!!.put("p_device", Repo.deviceId)); linked = true
            } catch (e: Exception) {
            }
            val a = JSONArray(Cloud.rpc("wl_news_get", Friends.auth(c)!!.put("p_device", Repo.deviceId)))
            var upto = 0L
            for (i in 0 until a.length()) {
                val n = a.getJSONObject(i)
                upto = maxOf(upto, n.optLong("id"))
                val code = n.optString("code")
                val d = n.optJSONObject("data") ?: JSONObject()
                try {
                    when (val kind = n.optString("kind")) {
                        "sh_invite" -> GiftInvites.told(c, code, d.optString("name"), d.optString("by"))
                        // Only on the Notifications page, no buzz (owner).
                        "sh_declined" -> Notifications.add(c, "gl_declined", "s:$code", d.optString("who").ifBlank { tr("Someone") }, extra = d.optString("name"))
                        // A year without opening the app: a month before the lists are removed online (S4.3, 2.186).
                        "cleanup" -> Alerts.notifyCleanup(c)
                        else -> if (kind.startsWith("ask")) Asks.told(c, kind, code, d)
                    }
                } catch (e: Exception) {
                }
            }
            if (upto > 0) Cloud.rpc("wl_news_done", Friends.auth(c)!!.put("p_device", Repo.deviceId).put("p_upto", upto))
        }
    }
}

// ---------- the share screen's "Invite Friends" ----------

/** Pick Friends to invite to gift list [l]; those on it or invited already are shown as such. */
@Composable
fun GiftInviteFriendsDialog(l: SharedList, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val friends = Friends.state?.friends ?: emptyList()
    var taken by remember { mutableStateOf<Set<String>?>(null) }
    var err by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        err = ""
        taken = try {
            GiftInvites.taken(ctx, l)
        } catch (e: Exception) {
            err = if ((e.message ?: "").contains("wl_sh_invited")) tr("Inviting Friends needs an update in Supabase first.")
            else tr("Couldn’t reach the server. Check your connection.")
            null
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Invite Friends")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    tr("They get an invitation to join “%s”.", l.name.ifBlank { Repo.listName }),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp)
                )
                val t = taken
                if (err.isNotEmpty()) {
                    Text(err, color = cs.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { attempt++ }) { Text(tr("Try again")) }
                } else if (t == null) {
                    CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp))
                } else if (friends.isEmpty()) {
                    Text(tr("No Friends yet. Add Friends on the Friends tab, or send the invite link."), color = cs.onSurfaceVariant)
                } else friends.forEach { f ->
                    val on = f.id in t
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !on && !busy) { picked = if (f.id in picked) picked - f.id else picked + f.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(Friends.photos[f.id], f.name, 36.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(f.name.ifBlank { tr("Someone") }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (on) Text(tr("On the list or invited"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Checkbox(checked = on || f.id in picked, enabled = !on && !busy, onCheckedChange = { v -> picked = if (v) picked + f.id else picked - f.id })
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && picked.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val n = picked.size
                    val failed = try { GiftInvites.invite(ctx, l, picked.toList()) } catch (e: Exception) { n }
                    busy = false
                    toast(
                        ctx, when {
                            failed == n -> tr("Couldn’t reach the server. Check your connection.")
                            failed > 0 -> tr("%d invitations couldn’t be sent. Try again.", failed)
                            n == 1 -> tr("Invitation sent")
                            else -> tr("%d invitations sent", n)
                        }
                    )
                    if (failed < n) onClose()
                }
            }) { Text(tr("Invite")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel")) } }
    )
}

// ---------- the invitation ----------

/**
 * The invitation to a gift list (owner: "well designed, not too flat, inviting and fun to join"): a colourful top with
 * the list's pictures fanned out (or a big gift), who invited you, the list's name and occasion, the givers, then a big
 * Join (confetti, then the list opens) and No thanks. [onJoined] gets the list's code.
 */
@Composable
fun GiftInviteDialog(code: String, onJoined: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    var info by remember(code) { mutableStateOf<GiftInviteInfo?>(null) }
    var state by remember(code) { mutableStateOf("loading") }
    var attempt by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var party by remember { mutableStateOf(0) }

    // Already on the list (joined from another invitation or the link): open it.
    LaunchedEffect(code) {
        if (Shared.lists.any { it.code == code }) { onJoined(code); return@LaunchedEffect }
    }
    LaunchedEffect(code, attempt) {
        state = "loading"
        state = try {
            if (Friends.auth(ctx) == null) "gone" else {
                info = GiftInvites.info(ctx, code)
                if (info == null) "gone" else "ok"
            }
        } catch (e: Exception) {
            if ((e.message ?: "").contains("wl_sh_inv_info")) "missing" else "offline"
        }
    }

    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(20.dp).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surface)
                    .verticalScroll(rememberScrollState())
            ) {
                // The colourful top: the accent fading into a warm pink, with confetti dots drawn here.
                Box(
                    Modifier.fillMaxWidth().height(190.dp)
                        .background(Brush.linearGradient(listOf(look.accent, Color(0xFFFF7AA2), Color(0xFFFFB86B)))),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val rnd = kotlin.random.Random(code.hashCode())
                        val dots = listOf(Color.White, Color(0xFFFFE082), Color(0xFFB3E5FC), Color(0xFFC8E6C9))
                        repeat(26) { i ->
                            val r = 3f + rnd.nextFloat() * 6f
                            drawCircle(dots[i % dots.size].copy(alpha = 0.55f + rnd.nextFloat() * 0.4f), r,
                                Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
                        }
                    }
                    val pics = info?.images.orEmpty()
                    if (pics.isNotEmpty()) {
                        val angles = when (pics.size) { 1 -> listOf(0f); 2 -> listOf(-7f, 7f); else -> listOf(-10f, 0f, 10f) }
                        val shifts = when (pics.size) { 1 -> listOf(0); 2 -> listOf(-34, 34); else -> listOf(-62, 0, 62) }
                        pics.take(3).forEachIndexed { i, p ->
                            Box(
                                Modifier.offset(x = shifts[i].dp, y = if (i == 1 && pics.size >= 3) (-6).dp else 4.dp).rotate(angles[i])
                                    .size(104.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(6.dp)
                            ) { WishImage(p, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), ContentScale.Crop) }
                        }
                    } else {
                        Box(
                            Modifier.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) { GiftIcon(look.accent, 60.dp) }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val i = info
                    when {
                        state == "loading" -> CircularProgressIndicator(Modifier.padding(16.dp))
                        state == "gone" -> {
                            Text(tr("This invitation is no longer open."), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text(tr("Ask for a new invitation or the invite link."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            TextButton(onClick = onClose) { Text(tr("Close")) }
                        }
                        state == "missing" -> {
                            Text(tr("Invitations need an update in Supabase first."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            TextButton(onClick = onClose) { Text(tr("Close")) }
                        }
                        state == "offline" || i == null -> {
                            Text(tr("Couldn’t reach the server. Check your connection."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            Row {
                                TextButton(onClick = onClose) { Text(tr("Close")) }
                                Button(onClick = { attempt++ }) { Text(tr("Try again")) }
                            }
                        }
                        else -> {
                            Text(
                                tr("%s invited you to plan gifts for", i.by.ifBlank { tr("Someone") }),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                            )
                            Text(
                                i.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Occasions.line(i.occ)?.let { o ->
                                Text(o, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = look.accent)
                            }
                            val bits = listOfNotNull(
                                if (i.count == 1) tr("1 gift idea") else tr("%d gift ideas", i.count),
                                when (i.givers.size) {
                                    0 -> null
                                    1 -> tr("%s is in", i.givers[0])
                                    2 -> tr("%1\$s and %2\$s are in", i.givers[0], i.givers[1])
                                    else -> tr("%1\$s and %2\$d others are in", i.givers[0], i.givers.size - 1)
                                }
                            )
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            Text(
                                tr("Add ideas, reserve what you’ll buy, and keep it a surprise."),
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                            )
                            if (err.isNotEmpty()) Text(err, color = cs.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Button(
                                enabled = !busy && party == 0,
                                onClick = {
                                    busy = true; err = ""
                                    scope.launch {
                                        try {
                                            val l = GiftInvites.accept(ctx, code)
                                            if (l == null) { busy = false; state = "gone"; return@launch }
                                            party++
                                            delay(1300)
                                            onJoined(l.code)
                                        } catch (e: Exception) {
                                            busy = false
                                            err = tr("Couldn’t join. Check your connection.")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)
                            ) { Text(if (busy) tr("Joining…") else tr("Join") + " 🎉", style = MaterialTheme.typography.titleMedium) }
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        GiftInvites.decline(ctx, code)
                                        Notifications.markRead(ctx, "gl_invite", "i:$code", 0L)
                                        onClose()
                                    } catch (e: Exception) {
                                        busy = false
                                        err = tr("Couldn’t reach the server. Check your connection.")
                                    }
                                }
                            }) { Text(tr("No thanks")) }
                        }
                    }
                }
            }
            Confetti(party, Modifier.matchParentSize())
        }
    }
}

/** The chat card of a gift list invitation: a small colourful gift tile, the list's name and "Tap to join". */
@Composable
fun GiftListChatCard(title: String, mine: Boolean, ink: Color) {
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(look.accent, Color(0xFFFF7AA2), Color(0xFFFFB86B))))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { GiftIcon(Color.White, 30.dp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(tr("Plan gifts with me 🎁"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Text(title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (mine) tr("Invitation sent") else tr("Tap to join"), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
