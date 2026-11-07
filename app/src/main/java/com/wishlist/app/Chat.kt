package com.wishlist.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/*
 * Messages (2.98, with supabase-chat.sql): end-to-end encrypted chat between friends ([ChatCrypto]).
 *  - The key pair lives in its own preferences file "chatkeys", which the backup (Backup.kt) never includes: a new
 *    phone makes a new key pair, and old messages can't be read there (owner's choice: nobody can read them but the
 *    two phones).
 *  - No push service: the open chat asks every few seconds, the conversation list every few seconds while it is on
 *    screen, and the background check ([BinWorker], about every 30 minutes) notifies new messages.
 *  - A message is JSON before locking: {"t":"text","x":"…"} or {"t":"wish","title","price","cur","img","url"}, or
 *    (2.108) a group invitation {"t":"group","gid","name","link"} (ChatContent: text = group id, title = its name,
 *    url = the group link), or (2.109) a photo {"t":"photo","f":name online,"k":its key,"w","h"} (ChatContent: text =
 *    the name, img = the key; see ChatPhotos.kt).
 */

/** What a message says, once unlocked. [readable] false: locked with another phone's key. */
data class ChatContent(
    val readable: Boolean, val kind: String = "text", val text: String = "",
    val title: String = "", val price: Double? = null, val cur: String = "", val img: String = "", val url: String = "",
    val w: Int = 0, val h: Int = 0,
    // A reply (owner, 2.128): the quoted message's id, a short copy of its text and who wrote it (profile id). Travels
    // inside the text message, so older apps just show the message without the quote.
    val replyTo: Long = 0L, val replyText: String = "", val replyBy: String = ""
)

/** [react] / [reactTheirs]: the emoji you and your friend put on this message (owner, 2.128), or "". */
data class ChatMessage(
    val id: Long, val mine: Boolean, val content: ChatContent, val at: Long, val read: Boolean,
    val react: String = "", val reactTheirs: String = ""
)

data class Conversation(
    val id: String, val name: String, val pv: Long, val pubkey: String, val friend: Boolean, val unread: Int,
    val last: ChatMessage?
)

object Chat {
    private fun keys(c: Context) = c.applicationContext.getSharedPreferences("chatkeys", Context.MODE_PRIVATE)
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    /** Messages you haven't read yet, over all conversations (the red dot on Messages). */
    val unreadTotal: Int get() = conversations.sumOf { it.unread }

    private val keyCache = HashMap<String, ByteArray>()

    private fun profile(c: Context): Pair<String, String>? {
        val p = prefs(c)
        val id = p.getString("pf_id", null) ?: return null
        val token = p.getString("pf_token", null) ?: return null
        return id to token
    }

    private fun base(c: Context): JSONObject {
        val (id, token) = profile(c) ?: throw IllegalStateException("no profile")
        return JSONObject().put("p_id", id).put("p_token", token)
    }

    private fun myId(c: Context) = prefs(c).getString("pf_id", "") ?: ""
    private fun myPublic(c: Context) = keys(c).getString("pub", "") ?: ""
    private fun myPrivate(c: Context) = keys(c).getString("priv", "") ?: ""

    /**
     * Makes this phone's key pair the first time and sends its public half to the server (again until that worked).
     * Needs the profile to exist (Friends.ensure).
     */
    suspend fun ensureKey(c: Context) {
        val k = keys(c)
        if (k.getString("priv", null) == null) {
            val (priv, pub) = ChatCrypto.newKeyPair()
            k.edit().putString("priv", priv).putString("pub", pub).remove("sent_for").commit()
        }
        // Sent for this profile? (After importing a backup the profile can be another one: then send it again.)
        if (k.getString("sent_for", null) != myId(c)) {
            Friends.ensure(c)
            Cloud.rpc("wl_pf_key", base(c).put("p_key", myPublic(c)))
            k.edit().putString("sent_for", myId(c)).apply()
        }
    }

    private fun keyWith(c: Context, otherPub: String, otherId: String): ByteArray =
        keyCache.getOrPut(otherPub + "|" + otherId) { ChatCrypto.sharedKey(myPrivate(c), otherPub, myId(c), otherId) }

    /** Unlocks one message row from the server, as seen by this phone. */
    private fun read(c: Context, o: JSONObject, other: String): ChatMessage {
        val mine = o.optBoolean("mine")
        val spk = o.optString("spk"); val rpk = o.optString("rpk")
        val me = myId(c)
        val myPub = myPublic(c)
        // My message: locked with my key (spk) and theirs (rpk); theirs: their key (spk) and mine (rpk).
        val plain = try {
            if (myPub.isEmpty()) null
            else if (mine && spk == myPub && rpk.isNotEmpty()) ChatCrypto.unlock(keyWith(c, rpk, other), o.optString("body"), me, other)
            else if (!mine && rpk == myPub && spk.isNotEmpty()) ChatCrypto.unlock(keyWith(c, spk, other), o.optString("body"), other, me)
            else null
        } catch (e: Exception) {
            null
        }
        val content = plain?.let { parse(it) } ?: ChatContent(false)
        return ChatMessage(o.optLong("id"), mine, content, time(o.optString("at")), o.optBoolean("read"))
    }

    private fun parse(s: String): ChatContent = try {
        val j = JSONObject(s)
        if (j.optString("t") == "group") ChatContent(true, "group", j.optString("gid"), j.optString("name"), url = j.optString("link"))
        // A gift exchange invitation (2.129); "x" holds a plain line with the link for older apps.
        else if (j.optString("t") == "exchange") ChatContent(true, "exchange", j.optString("gid"), j.optString("name"), url = j.optString("link"))
        // A gift list invitation (2.142): only the list's code and name (never its key); "x" is a line for older apps.
        else if (j.optString("t") == "giftlist") ChatContent(true, "giftlist", j.optString("code"), j.optString("name"))
        else if (j.optString("t") == "photo") ChatContent(true, "photo", j.optString("f"), img = j.optString("k"), w = j.optInt("w"), h = j.optInt("h"))
        else if (j.optString("t") == "wish") ChatContent(
            true, "wish", "", j.optString("title"), if (j.has("price") && !j.isNull("price")) j.optDouble("price") else null,
            j.optString("cur"), j.optString("img"), j.optString("url")
        ) else j.optJSONObject("re").let { re ->
            ChatContent(
                true, "text", j.optString("x"),
                replyTo = re?.optLong("id") ?: 0L, replyText = re?.optString("x").orEmpty(), replyBy = re?.optString("by").orEmpty()
            )
        }
    } catch (e: Exception) {
        ChatContent(false)
    }

    private fun time(s: String): Long = try {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    /** The conversation list. Throws when offline (the last list stays on screen). */
    suspend fun refresh(c: Context) = lock.withLock {
        if (profile(c) == null) return@withLock
        ensureKey(c)
        val a = JSONArray(Cloud.rpc("wl_chat_list", base(c)))
        conversations = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.getString("id")
            Conversation(
                id, o.optString("name"), o.optLong("pv"), o.optString("pubkey"), o.optBoolean("friend"), o.optInt("unread"),
                o.optJSONObject("last")?.let { read(c, it, id) }
            )
        }
    }

    /**
     * Messages with [other] after message [after] (0: the newest 300), oldest first. Since 2.105 (owner) fetching
     * doesn't mark them as read any more: [seen] does, once you really looked at the chat. Without supabase-push.sql
     * the old function is used (which still marks them read when fetched).
     */
    suspend fun messages(c: Context, other: String, after: Long): List<ChatMessage> {
        ensureKey(c)
        val body = base(c).put("p_other", other).put("p_after", after)
        var t: String? = null
        if (!oldServer) {
            try {
                t = Cloud.rpc("wl_chat_fetch", body)
            } catch (e: CloudException) {
                val m = e.message.orEmpty()
                if (m.contains("PGRST202") || m.contains("wl_chat_fetch")) oldServer = true else throw e
            }
        }
        val a = JSONArray(t ?: Cloud.rpc("wl_chat_get", body))
        return (0 until a.length()).map { read(c, a.getJSONObject(it), other) }
    }

    /** Saves this phone's push address with your profile (Push.kt, supabase-push.sql). */
    suspend fun setPush(c: Context, token: String) {
        Cloud.rpc("wl_push_set", base(c).put("p_push", token))
    }

    /** True when supabase-push.sql hasn't been run yet (no wl_chat_fetch). */
    @Volatile private var oldServer = false

    /**
     * You have seen the chat with [other] up to their message [upTo] (it was on screen, with the app open): their
     * messages up to it get the two coloured ticks on their phone, and its notification goes.
     */
    suspend fun seen(c: Context, other: String, upTo: Long) {
        Alerts.clearMessage(c, other)
        if (oldServer) return
        Cloud.rpc("wl_chat_seen", base(c).put("p_other", other).put("p_upto", upTo))
        conversations = conversations.map { if (it.id == other) it.copy(unread = 0) else it }
    }

    /** Ids of my messages to [other] (from id [from] on) that they have read: the double ticks. */
    suspend fun reads(c: Context, other: String, from: Long): Set<Long> {
        val a = JSONArray(Cloud.rpc("wl_chat_reads", base(c).put("p_other", other).put("p_from", from)))
        return (0 until a.length()).map { a.getLong(it) }.toSet()
    }

    /** Sends [content] to friend [other]. Returns the new message, or throws (offline, not friends, no key yet). */
    suspend fun send(c: Context, other: String, content: ChatContent): ChatMessage {
        ensureKey(c)
        val theirPub = conversations.firstOrNull { it.id == other }?.pubkey?.takeIf { it.isNotEmpty() }
            ?: Cloud.rpc("wl_chat_key", base(c).put("p_other", other)).trim().removeSurrounding("\"")
        if (theirPub.isEmpty()) throw IllegalStateException("no key")
        val me = myId(c)
        val plain = if (content.kind == "group") JSONObject().put("t", "group").put("gid", content.text)
            .put("name", content.title).put("link", content.url).toString()
        else if (content.kind == "exchange") JSONObject().put("t", "exchange").put("gid", content.text)
            .put("name", content.title).put("link", content.url)
            .put("x", tr("Join my gift exchange “%1\$s”: %2\$s", content.title, content.url)).toString()
        else if (content.kind == "giftlist") JSONObject().put("t", "giftlist").put("code", content.text)
            .put("name", content.title).put("x", tr("I invited you to plan gifts together for “%s”. Update the app to join.", content.title)).toString()
        else if (content.kind == "photo") JSONObject().put("t", "photo").put("f", content.text).put("k", content.img)
            .put("w", content.w).put("h", content.h).toString()
        else if (content.kind == "wish") JSONObject().put("t", "wish").put("title", content.title)
            .put("price", content.price ?: JSONObject.NULL).put("cur", content.cur).put("img", content.img).put("url", content.url).toString()
        else JSONObject().put("t", "text").put("x", content.text).apply {
            if (content.replyTo > 0L) put("re", JSONObject().put("id", content.replyTo).put("x", content.replyText.take(120)).put("by", content.replyBy))
        }.toString()
        val body = ChatCrypto.lock(keyWith(c, theirPub, other), plain, me, other)
        val args = base(c).put("p_other", other).put("p_body", body).put("p_spk", myPublic(c)).put("p_rpk", theirPub)
        val r = JSONObject(
            if (content.kind == "photo") Cloud.rpc("wl_chat_send_photo", args.put("p_file", content.text))
            else Cloud.rpc("wl_chat_send", args)
        )
        return ChatMessage(r.optLong("id"), true, content, time(r.optString("at")), false)
    }

    /** Stores a locked photo for [other] (ChatPhotos.upload); returns its name online. */
    suspend fun putPhoto(c: Context, other: String, data: String): String =
        Cloud.rpc("wl_chat_file_put", base(c).put("p_other", other).put("p_data", data)).trim().removeSurrounding("\"")
            .takeIf { it.isNotEmpty() && it != "null" } ?: throw IllegalStateException("no photo name")

    /** A locked photo of one of your conversations, or null when it is gone. */
    suspend fun getPhoto(c: Context, name: String): String? =
        Cloud.rpc("wl_chat_file_get", base(c).put("p_file", name)).trim().removeSurrounding("\"")
            .takeIf { it.isNotEmpty() && it != "null" }

    /** This phone's profile id (to tell whose message a reply quotes). */
    fun me(c: Context): String = myId(c)

    /** True when the reactions script (supabase-features.sql) isn't there yet: then reactions are left out quietly. */
    @Volatile private var noReactions = false

    /**
     * Puts emoji [emoji] on message [id] of the chat with [other], or takes yours off ("") (owner, 2.128). Locked like
     * a message, so only the two of you can read it. Throws when offline or when the script wasn't run yet.
     */
    suspend fun react(c: Context, other: String, id: Long, emoji: String) {
        val theirPub = conversations.firstOrNull { it.id == other }?.pubkey?.takeIf { it.isNotEmpty() }
            ?: Cloud.rpc("wl_chat_key", base(c).put("p_other", other)).trim().removeSurrounding("\"")
        val locked = if (emoji.isEmpty()) "" else ChatCrypto.lock(keyWith(c, theirPub, other), emoji, myId(c), other)
        Cloud.rpc("wl_chat_react", base(c).put("p_msg", id).put("p_react", locked))
    }

    /** The reactions in the chat with [other]: message id to (yours, theirs). Empty without the script or offline. */
    suspend fun reactions(c: Context, other: String): Map<Long, Pair<String, String>> {
        if (noReactions) return emptyMap()
        val t = try {
            Cloud.rpc("wl_chat_reacts", base(c).put("p_other", other))
        } catch (e: CloudException) {
            if ((e.message ?: "").contains("wl_chat_reacts")) noReactions = true
            return emptyMap()
        }
        val theirPub = conversations.firstOrNull { it.id == other }?.pubkey.orEmpty()
        if (theirPub.isEmpty()) return emptyMap()
        val key = keyWith(c, theirPub, other)
        val me = myId(c)
        val a = JSONArray(t)
        return (0 until a.length()).associate { i ->
            val o = a.getJSONObject(i)
            fun open(s: String, from: String, to: String) =
                if (s.isEmpty()) "" else try { ChatCrypto.unlock(key, s, from, to)?.take(8).orEmpty() } catch (e: Exception) { "" }
            o.optLong("id") to (open(o.optString("mine"), me, other) to open(o.optString("theirs"), other, me))
        }
    }

    suspend fun delete(c: Context, id: Long) {
        Cloud.rpc("wl_chat_delete", base(c).put("p_msg", id))
    }

    /** The background check: a notification for every conversation with a new message since the last one told. */
    suspend fun poll(c: Context) {
        if (profile(c) == null || keys(c).getString("priv", null) == null) return
        refresh(c)
        val p = prefs(c)
        for (cv in conversations) {
            val last = cv.last ?: continue
            if (last.mine || cv.unread == 0) continue
            val key = "chat_told_${cv.id}"
            if (last.id <= p.getLong(key, 0L)) continue
            if (ChatNav.peer != cv.id) Alerts.notifyMessage(c, cv.id, cv.name, preview(last.content))
            p.edit().putLong(key, last.id).apply()
        }
    }

    /** One line about a message: its text, "Wish: …", or that it can't be read here. */
    fun preview(m: ChatContent): String = when {
        !m.readable -> tr("This message can’t be read on this phone")
        m.kind == "wish" -> tr("Wish: %s", m.title)
        m.kind == "group" || m.kind == "exchange" || m.kind == "giftlist" -> tr("Invitation: %s", m.title)
        m.kind == "photo" -> "📷 " + tr("Photo")
        else -> m.text
    }
}

/** Which conversation is open in Messages, and a request to open the Messages tab (from a friend's page or an alert). */
object ChatNav {
    var peer by mutableStateOf<String?>(null)
    var openTab by mutableStateOf(false)
}
