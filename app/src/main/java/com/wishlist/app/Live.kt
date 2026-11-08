package com.wishlist.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Live updates (ROADMAP S3.2, owner 8 November 2026): collab wishlists, a friend's list and the chat hear "this
 * changed" from Supabase Realtime within a second, instead of asking the server every 20 (lists) or 3–8 (chat)
 * seconds. The signal never carries wishes or messages: the screen then fetches through the same checked functions as
 * before. supabase-sync.sql sends the signals.
 *
 * One connection for the whole app, open only while a live screen is open (and the app is in front). When it can't
 * connect (no network, Supabase's limit of 200 connections on the free plan, the script not run), the screens quietly
 * poll as before (owner's choice) and the connection is tried again with growing pauses.
 *
 * Channels are fingerprints (the same as `wl_live_topic` in supabase-sync.sql): a list's from its code, your chat's
 * from your profile's secret token. Anyone could send a false "changed" to a channel they know; that only makes the
 * phone fetch again, so a screen fetches at most once every 2 seconds.
 */
object Live {
    /** What a screen hears: something changed (fetch), or the connection came or went (only re-think the waiting). */
    enum class Signal { CHANGED, STATE }

    fun listTopic(code: String) = topic("l", code)
    fun chatTopic(token: String) = topic("c", token)
    internal fun topic(kind: String, id: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("wishlist-$kind:$id".toByteArray(Charsets.UTF_8))
        return "wl$kind-" + d.joinToString("") { "%02x".format(it) }
    }

    class Sub internal constructor(val topic: String) {
        val signals = Channel<Signal>(Channel.CONFLATED)
        @Volatile var joined = false
            internal set
        @Volatile internal var everJoined = false
        fun close() = Live.unsubscribe(this)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subs = ArrayList<Sub>()
    private var ws: WebSocket? = null
    private var open = false
    private var ref = 0
    private var heartbeat: Job? = null
    private var retry: Job? = null
    private var closer: Job? = null
    private var failures = 0
    /** The app is in front. In the background no screen fetches and the connection is closed (battery). */
    private val fg = MutableStateFlow(true)

    /** From MainActivity's onStart / onStop. */
    @Synchronized
    fun foreground(on: Boolean) {
        if (fg.value == on) return
        fg.value = on
        if (!on) {
            closer?.cancel(); closer = null
            disconnect()
            for (s in subs) { s.joined = false; s.signals.trySend(Signal.STATE) }
        } else if (subs.isNotEmpty() && ws == null) {
            connect()
        }
    }

    private val client by lazy { OkHttpClient.Builder().pingInterval(0, TimeUnit.SECONDS).build() }

    @Synchronized
    fun subscribe(topic: String): Sub {
        val s = Sub(topic)
        subs += s
        closer?.cancel(); closer = null
        if (ws == null) { if (fg.value) connect() } else if (open && subs.count { it.topic == topic } == 1) join(topic) else if (open) {
            // Another screen already joined this channel.
            s.joined = subs.any { it !== s && it.topic == topic && it.joined }
            s.everJoined = s.joined
        }
        return s
    }

    @Synchronized
    private fun unsubscribe(s: Sub) {
        if (!subs.remove(s)) return
        s.signals.close()
        if (open && subs.none { it.topic == s.topic }) send(s.topic, "phx_leave", JSONObject())
        if (subs.isEmpty()) {
            // A short pause, so going from one live screen to the next keeps the connection.
            closer = scope.launch {
                delay(10_000)
                synchronized(Live) { if (subs.isEmpty()) disconnect() }
            }
        }
    }

    private fun connect() {
        if (!Config.enabled || Crash.onComputer) return // The app's checks never reach the server: they poll.
        retry?.cancel(); retry = null
        Calls.count("live connect")
        val url = Config.SUPABASE_URL.trimEnd('/').replaceFirst("https://", "wss://") +
            "/realtime/v1/websocket?apikey=" + Config.SUPABASE_KEY + "&vsn=1.0.0"
        ws = try {
            client.newWebSocket(Request.Builder().url(url).build(), Listener())
        } catch (e: Exception) {
            null
        }
        if (ws == null) scheduleRetry()
    }

    private fun disconnect() {
        heartbeat?.cancel(); heartbeat = null
        retry?.cancel(); retry = null
        try { ws?.close(1000, null) } catch (e: Exception) {}
        ws = null
        open = false
        failures = 0
    }

    private fun send(topic: String, event: String, payload: JSONObject) {
        ref++
        val m = JSONObject().put("topic", if (topic == "phoenix") topic else "realtime:$topic").put("event", event)
            .put("payload", payload).put("ref", ref.toString())
        try { ws?.send(m.toString()) } catch (e: Exception) {}
    }

    private fun join(topic: String) {
        val config = JSONObject().put("broadcast", JSONObject().put("self", false).put("ack", false))
            .put("presence", JSONObject().put("key", "")).put("private", false)
        send(topic, "phx_join", JSONObject().put("config", config))
    }

    /** No connection for now: every screen polls; try again after 2, 4, 8 … up to 120 seconds. */
    private fun scheduleRetry() {
        val wait = (2_000L shl failures.coerceAtMost(6)).coerceAtMost(120_000L)
        failures++
        retry = scope.launch {
            delay(wait)
            synchronized(Live) { if (subs.isNotEmpty() && ws == null && fg.value) connect() }
        }
    }

    @Synchronized
    private fun lost(sock: WebSocket) {
        if (ws !== sock) return
        ws = null
        open = false
        heartbeat?.cancel(); heartbeat = null
        for (s in subs) {
            if (s.joined) { s.joined = false; s.signals.trySend(Signal.STATE) }
        }
        if (subs.isNotEmpty() && fg.value) scheduleRetry()
    }

    private class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(Live) {
                if (ws !== webSocket) return
                open = true
                for (t in subs.map { it.topic }.distinct()) join(t)
                heartbeat = scope.launch {
                    while (true) {
                        delay(25_000)
                        synchronized(Live) { if (ws === webSocket) send("phoenix", "heartbeat", JSONObject()) }
                    }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val m = try { JSONObject(text) } catch (e: Exception) { return }
            val topic = m.optString("topic").removePrefix("realtime:")
            val event = m.optString("event")
            synchronized(Live) {
                if (ws !== webSocket) return
                when (event) {
                    "phx_reply" -> {
                        val ok = m.optJSONObject("payload")?.optString("status") == "ok"
                        if (!ok || topic == "phoenix") return
                        failures = 0
                        for (s in subs.filter { it.topic == topic && !it.joined }) {
                            s.joined = true
                            // Joined again after a gap: something may have changed meanwhile.
                            s.signals.trySend(if (s.everJoined) Signal.CHANGED else Signal.STATE)
                            s.everJoined = true
                        }
                    }
                    "broadcast" -> {
                        if (m.optJSONObject("payload")?.optString("event") == "changed") {
                            for (s in subs.filter { it.topic == topic }) s.signals.trySend(Signal.CHANGED)
                        }
                    }
                    "phx_error", "phx_close" -> {
                        for (s in subs.filter { it.topic == topic && it.joined }) {
                            s.joined = false
                            s.signals.trySend(Signal.STATE)
                        }
                        // Only this channel stopped (the connection is still there): the screen polls; join again soon.
                        if (subs.any { it.topic == topic }) scope.launch {
                            delay(5_000)
                            synchronized(Live) {
                                if (ws === webSocket && open && subs.any { it.topic == topic && !it.joined }) join(topic)
                            }
                        }
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
    }

    /** For the app's checks (they never connect): what the server would say. */
    @Synchronized
    internal fun pretend(topic: String, joined: Boolean, signal: Signal) {
        for (s in subs.filter { it.topic == topic }) {
            s.joined = joined
            if (joined) s.everJoined = true
            s.signals.trySend(signal)
        }
    }

    @Synchronized internal fun count(topic: String) = subs.count { it.topic == topic }

    /** Smallest time between two fetches of one screen (false signals can't make it fetch more often). */
    internal var MIN_GAP = 2_000L
    /** While live: still fetch now and then, in case a signal got lost. */
    const val SAFETY = 10 * 60_000L

    /**
     * The loop of a live screen: fetch now, then fetch again when [topic] hears "changed"; while not live, every
     * [every] milliseconds as before. Stops when the screen goes ([active] false or the screen leaves).
     */
    suspend fun follow(topic: String?, every: Long, refresh: suspend (live: Boolean) -> Unit) {
        val sub = topic?.let { subscribe(it) }
        try {
            var due = true
            var last = 0L
            while (true) {
                if (!fg.value) {
                    // In the background: nothing is fetched; back in front: fetch at once.
                    fg.first { it }
                    due = true
                }
                if (due) {
                    val gap = MIN_GAP - (System.currentTimeMillis() - last)
                    if (gap > 0) delay(gap)
                    last = System.currentTimeMillis()
                    refresh(sub?.joined == true)
                }
                val live = sub?.joined == true
                val s = if (sub == null) { delay(every); null } else withTimeoutOrNull(if (live) SAFETY else every) { sub.signals.receive() }
                due = s != Signal.STATE
            }
        } finally {
            sub?.close()
        }
    }
}

/** [Live.follow] for a screen: runs while [active], restarts when [topic] changes. */
@Composable
fun LiveRefresh(topic: String?, active: Boolean, every: Long, refresh: suspend (live: Boolean) -> Unit) {
    val r = rememberUpdatedState(refresh)
    LaunchedEffect(topic, active) {
        if (!active) return@LaunchedEffect
        Live.follow(topic, every) { r.value(it) }
    }
}
