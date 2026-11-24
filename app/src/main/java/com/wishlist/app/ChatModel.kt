package com.wishlist.app

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/*
 * One chat's state holder (the rebuild, owner 19 November 2026; MAP "How a screen is built"): the messages, what you
 * are typing or answering, which dialog is open, and everything that changes the chat (send, react, delete, "read").
 * The chat screen (ChatScreen.kt) only draws; the data and the server calls stay in Chat (Chat.kt) and ChatPhotos.
 */
@Stable
internal class ChatModel(
    private val ctx: Context, private val peer: String, private val scope: CoroutineScope, private val snack: SnackbarHostState
) {
    // A chat opened before shows its messages at once, at the end (Chat.opened); the server's answer follows.
    var msgs by mutableStateOf(Chat.opened[peer].orEmpty())
    var loading by mutableStateOf(Chat.opened[peer] == null)
    var text by mutableStateOf("")
    var sending by mutableStateOf(false)
    var deleting by mutableStateOf<ChatMessage?>(null)
    var chatMenu by mutableStateOf(false)
    var reporting by mutableStateOf(false)
    var blocking by mutableStateOf(false)
    var wishPick by mutableStateOf(false)
    // The + next to the text box (owner, 24 November 2026): its menu, the wishlist picker, and an "Only me" list
    // waiting for "Make it visible to friends?".
    var plusMenu by mutableStateOf(false)
    var listPick by mutableStateOf(false)
    var askVisible by mutableStateOf<MineData?>(null)
    // Reactions and replies (owner, 2.128): the long-press menu, the message being answered, and the emoji per message.
    var menuFor by mutableStateOf<ChatMessage?>(null)
    var replying by mutableStateOf<ChatMessage?>(null)
    var reacts by mutableStateOf<Map<Long, Pair<String, String>>>(emptyMap())
    // Photos (2.109): picked or taken, then shown full screen with Send (owner: a preview first); [viewing] is a
    // photo opened from the chat.
    var preview by mutableStateOf<String?>(null)
    var viewing by mutableStateOf<File?>(null)
    // The first time the messages are there, the list jumps to the end while still invisible (owner, 16 November
    // 2026: it scrolled through the whole conversation in front of you); after that a new message slides in.
    var placed by mutableStateOf(msgs.isNotEmpty())
    var seenUpTo by mutableStateOf(0L)

    /**
     * One round of asking for news. Every 10th time (about every 30 seconds when polling; every 5th signal when live)
     * the whole chat is loaded again, so a message its sender deleted disappears here too; in between only new
     * messages and read ticks are fetched. Reactions can change on any message, so they are fetched for the whole chat
     * (every other round; every round when live, since a round then only happens when something changed).
     */
    suspend fun refresh(live: Boolean, round: Int) {
        try {
            val full = round % (if (live) 5 else 10) == 0
            val after = if (full) 0L else msgs.lastOrNull()?.id ?: 0L
            val fresh = Chat.messages(ctx, peer, after)
            if (full) msgs = fresh
            else {
                val reads = msgs.firstOrNull { it.mine && !it.read }?.let { Chat.reads(ctx, peer, it.id) }.orEmpty()
                var all = msgs.map { if (it.mine && it.id in reads) it.copy(read = true) else it }
                if (fresh.isNotEmpty()) all = (all + fresh).distinctBy { it.id }
                msgs = all
            }
        } catch (e: Exception) {
        }
        if (live || round % 2 == 0) try { reacts = Chat.reactions(ctx, peer) } catch (e: Exception) {}
        loading = false
    }

    /** Kept for the next time the chat opens (Chat.opened). */
    fun keep() { if (msgs.isNotEmpty()) Chat.opened[peer] = msgs }

    /** "Read" (owner, 2.105): their messages up to [newest] were really seen. */
    suspend fun markSeen(newest: Long) {
        try {
            Chat.seen(ctx, peer, newest)
            seenUpTo = newest
        } catch (e: Exception) {
        }
    }

    fun sendPhoto(image: String) {
        sending = true
        scope.launch {
            try {
                val content = ChatPhotos.upload(ctx, peer, image)
                val m = Chat.send(ctx, peer, content)
                msgs = (msgs + m).distinctBy { it.id }
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t send the photo. Check your connection."))
            }
            ImageStore.delete(ctx, image)
            sending = false
        }
    }

    fun react(m: ChatMessage, emoji: String) {
        val before = reacts
        val now = reacts[m.id] ?: ("" to "")
        // Tapping the emoji you already chose takes it off.
        val mine = if (now.first == emoji) "" else emoji
        reacts = reacts + (m.id to (mine to now.second))
        scope.launch {
            try {
                Chat.react(ctx, peer, m.id, mine)
            } catch (e: Exception) {
                reacts = before
                snack.showToast(
                    if ((e.message ?: "").contains("wl_chat_react")) tr("Reactions need an update in Supabase first.")
                    else tr("Couldn’t react. Check your connection.")
                )
            }
        }
    }

    // "Chat about this wish": the wish attached to what you type; and the card sent for it without words.
    var about by mutableStateOf<ChatWish?>(null)
    private var aboutSent: ChatContent? = null

    fun send(content: ChatContent) {
        sending = true
        scope.launch {
            try {
                val m = Chat.send(ctx, peer, content)
                msgs = (msgs + m).distinctBy { it.id }
                if (content.kind == "text" || content.about != null || content === aboutSent) { text = ""; replying = null; about = null }
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t send. Check your connection."))
            }
            sending = false
        }
    }

    /**
     * The text you typed, as an answer to [replying] when you chose Reply, with the wish [about] when one is attached
     * (owner, 20 November 2026). A wish without words goes as a wish card.
     */
    fun sendText() {
        val a = about
        if (text.isBlank()) {
            if (a == null) return
            val card = ChatContent(true, "wish", "", a.title, a.price, a.cur, a.img, a.url, code = a.code, wishId = a.id)
            aboutSent = card
            send(card)
            return
        }
        val r = replying
        send(
            if (r == null) ChatContent(true, "text", text.trim(), about = a)
            else ChatContent(
                true, "text", text.trim(), replyTo = r.id, replyText = Chat.preview(r.content),
                replyBy = if (r.mine) Chat.me(ctx) else peer, about = a
            )
        )
    }

    /** One of your wishes as a card (a photo only kept on this phone isn't sent). */
    /** A wish from your own list [code] (the gift button): its card opens it in that list (owner, 20 November 2026). */
    fun sendWish(w: Wish, code: String? = null) =
        send(ChatContent(
            true, "wish", "", w.label, w.price, w.currency, if (ImageStore.isLocal(w.image)) "" else w.image, w.url,
            code = code.orEmpty(), wishId = if (code.isNullOrEmpty()) 0L else w.id
        ))

    /**
     * One of your own wishlists as a card (the +'s "A whole wishlist"). A list only you can see first asks to become
     * visible to friends ([askVisible]), or your friend couldn't open it (owner, 24 November 2026).
     */
    fun sendList(d: MineData) {
        if (d.code == null || ListVisibility.of(ctx, d.id) == Who.ME) { askVisible = d; return }
        send(listCard(d, listCover(ctx, d)))
    }

    /** "Make visible and send": friends can see the list now (as in its settings), then the card goes. */
    fun makeVisibleAndSend(d: MineData) {
        askVisible = null
        sending = true
        scope.launch {
            val now = try {
                ListVisibility.set(ctx, d.id, Who.FRIENDS)
                Repo.mine(ctx, d.id)
            } catch (e: Exception) {
                // Offline or the server said no: told below. A closed chat stops here.
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            sending = false
            if (now?.code == null) snack.showToast(tr("Couldn’t make it visible. Check your connection and try again."))
            else send(listCard(now, listCover(ctx, now)))
        }
    }

    /** Delete your own message for both of you. */
    fun delete(m: ChatMessage) {
        scope.launch {
            try {
                Chat.delete(ctx, m.id)
                ChatPhotos.forget(ctx, m.content)
                msgs = msgs.filter { it.id != m.id }
            } catch (e: Exception) {
                snack.showToast(tr("Couldn’t reach the server. Check your connection."))
            }
        }
    }
}

/** The card for your wishlist [d] (it must have its friends' code): its name, its picture [cover] and how many wishes. */
internal fun listCard(d: MineData, cover: String) = ChatContent(
    true, "list", d.code.orEmpty(), if (d.name == "My wishlist") tr(d.name) else d.name, img = cover,
    count = d.wishes.count { !it.priv && it.bin == 0L }
)

/** The list's cover as friends see it online, else the first wish picture that is online; "" for none. */
internal fun listCover(c: Context, d: MineData): String {
    val cut = ListCovers.get(c, "m:${d.id}")?.cut?.takeIf { ImageStore.isLocal(it) }?.substringAfterLast('/')
    if (d.code != null && cut != null && cut in d.photos) return Cloud.photoUrl(d.code, cut)
    return d.wishes.firstOrNull { !it.priv && it.bin == 0L && ChatWish.safeLink(it.image).isNotEmpty() }?.image.orEmpty()
}
