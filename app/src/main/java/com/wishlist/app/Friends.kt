package com.wishlist.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/*
 * Friends (since 2.87), with supabase-friends.sql.
 *  - Your profile: a random id and secret token (only on this phone and online), your name (the app name, see
 *    Shared.setAppName), an optional photo and a friend code you can give to people.
 *  - Someone enters your code: a friend request. You approve or decline it in the Friends tab (and get a notification).
 *  - Each of your own lists has "Visible to friends" in its settings (off by default). Such a list goes online like a
 *    shared list (it gets its friends' link), and your friends can see it and reserve gifts. You never see reservations.
 *  - Per friend: "Unfriend" (you are no longer friends; they aren't told). "Hide my lists from …" went in 2.108
 *    (owner: private wishes are enough); supabase-groups.sql switched every hiding off.
 * Profiles belong to the phone (no e-mail accounts): a new phone starts again.
 *
 * Since the data rebuild (owner, 19 November 2026) Friends is the front door and its parts live in their own files, while
 * every screen keeps writing Friends.… as before: FriendsPhotos.kt ([FriendPhotos]: your photo and friends' photos),
 * FriendsAbout.kt ([FriendAbout]: birthday, About me, birthday reminders), FriendsVisible.kt ([FriendVisibility]:
 * which lists friends can see), FriendsLinks.kt (codes and links). This file: your profile, requests and the Friends
 * tab. FriendsSameTest proves it all does exactly what it did before (with a pretend server, Cloud.pretendServer).
 */

data class FriendList(val code: String, val name: String, val count: Int)

data class Person(
    val id: String,
    val name: String,
    val pv: Long,
    val hidden: Boolean = false,
    val lists: List<FriendList> = emptyList(),
    /** "MM-DD" or "" (2.95). */
    val birthday: String = "",
    /** Their "I like…" line (2.95). */
    val bio: String = ""
)

data class FriendsState(
    val code: String,
    val friends: List<Person>,
    val incoming: List<Person>,
    val outgoing: List<Person>
)

object Friends {
    internal fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** What the Friends tab shows (the last answer from the server, also kept on the phone for offline use). */
    var state by mutableStateOf<FriendsState?>(null)
        internal set

    // What the parts hold, read as before (Friends.myPhoto, Friends.visible, …).
    val myPhoto: String get() = FriendPhotos.myPhoto
    val photos: Map<String, String> get() = FriendPhotos.photos
    val photoSrc: String get() = FriendPhotos.photoSrc
    val photoEdit: HeroEdit get() = FriendPhotos.photoEdit
    val myBirthday: String get() = FriendAbout.myBirthday
    val myBio: String get() = FriendAbout.myBio
    val visible: Set<String> get() = FriendVisibility.visible

    private var loaded = false
    const val BIO_MAX = 100

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        FriendPhotos.myPhoto = p.getString("pf_photo", "") ?: ""
        FriendAbout.myBirthday = p.getString("pf_bday", "") ?: ""
        FriendAbout.myBio = p.getString("pf_bio", "") ?: ""
        // Sizes were removed (owner, 17 November 2026): sizes saved on this phone are deleted, and once more online.
        if (p.contains("pf_sz_c") || p.contains("pf_sz_s") || p.contains("fr_sizes"))
            p.edit().remove("pf_sz_c").remove("pf_sz_s").remove("fr_sizes").putBoolean("pf_sizes_dirty", true).apply()
        FriendPhotos.photoSrc = p.getString("pf_src", "") ?: ""
        FriendPhotos.photoEdit = HeroEdit.fromJson(p.getString("pf_edit", null))
        FriendVisibility.visible = p.getStringSet("fr_vis", null)?.toSet() ?: emptySet()
        state = p.getString("fr_state", null)?.let { parse(it) }
        val dir = photoDir(c)
        FriendPhotos.photos = (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
            .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }
    }

    /** True once this phone has a profile online. */
    fun hasProfile(c: Context) = prefs(c).getString("pf_id", null) != null

    /** Requests that wait for your approval: a red dot on Friends. */
    val waiting: Int get() = state?.incoming?.size ?: 0

    internal fun ids(c: Context): Pair<String, String> {
        val p = prefs(c)
        val id = p.getString("pf_id", null) ?: Sync.newKey().also { p.edit().putString("pf_id", it).apply() }
        val token = p.getString("pf_token", null) ?: Sync.newKey().also { p.edit().putString("pf_token", it).apply() }
        return id to token
    }

    internal fun base(c: Context): JSONObject {
        val (id, token) = ids(c)
        return JSONObject().put("p_id", id).put("p_token", token)
    }

    /** Your profile id and token for a call, or null when this phone has no profile online yet (Members.kt, 2.128). */
    fun auth(c: Context): JSONObject? = if (hasProfile(c) && prefs(c).getBoolean("pf_made", false)) base(c) else null

    internal fun str(t: String): String = t.trim().removeSurrounding("\"")

    /** Makes your profile online the first time; afterwards saves your name. */
    suspend fun ensure(c: Context) {
        val p = prefs(c)
        val fresh = !p.getBoolean("pf_made", false)
        Cloud.rpc("wl_pf_save", base(c).put("p_name", Shared.lastName))
        if (fresh) {
            p.edit().putBoolean("pf_made", true).apply()
            if (myPhoto.isNotEmpty()) uploadPhoto(c)
            pushLists(c)
            if (myBirthday.isNotEmpty() || myBio.isNotEmpty()) pushAbout(c)
            // Instant notifications start now, not after the app is next opened (the review of 20 November 2026:
            // a first friend's messages came only with the half-hourly check until then).
            Push.start(c)
        } else if (p.getBoolean("pf_about_dirty", false)) pushAbout(c)
        if (p.getBoolean("pf_sizes_dirty", false)) try { clearSizes(c) } catch (e: Exception) {}
    }

    // ---- sizes: removed (owner, 17 November 2026, docs/decisions/people.md) ----

    /** Only for the screenshots (ScreenshotTest.friendPage): a pretend friend without a server. */
    internal fun pretend(s: FriendsState) { state = s }

    /** Clears the sizes an earlier version saved online (supabase-sizes-off.sql also clears them on the server). */
    internal suspend fun clearSizes(c: Context) {
        Cloud.rpc("wl_pf_sizes", base(c).put("p_sizes", ""))
        prefs(c).edit().putBoolean("pf_sizes_dirty", false).apply()
    }

    // ---- a private note about a friend (owner, 2.128): only on this phone, never online ----

    fun note(c: Context, id: String): String = prefs(c).getString("fnote_$id", "") ?: ""

    fun setNote(c: Context, id: String, text: String) {
        val t = text.trim().take(500)
        prefs(c).edit().apply { if (t.isEmpty()) remove("fnote_$id") else putString("fnote_$id", t) }.apply()
    }

    /** Your name changed (Profile, or anywhere else the app name is set). */
    suspend fun nameChanged(c: Context) {
        if (prefs(c).getBoolean("pf_made", false)) Cloud.rpc("wl_pf_save", base(c).put("p_name", Shared.lastName))
    }

    /** Loads everything for the Friends tab. Throws when offline (the last state stays on screen). */
    suspend fun refresh(c: Context) = lock.withLock {
        ensure(c)
        val t = Cloud.rpc("wl_fr_state", base(c))
        val s = parse(t) ?: return@withLock
        state = s
        prefs(c).edit().putString("fr_state", t).apply()
        loadPhotos(c, s)
        notifyNews(c, s, onScreen = true)
    }

    /** The background check: notifications for new requests and for requests that were approved. */
    suspend fun poll(c: Context) {
        load(c)
        if (!prefs(c).getBoolean("pf_made", false)) return
        lock.withLock {
            val t = Cloud.rpc("wl_fr_state", base(c))
            val s = parse(t) ?: return@withLock
            state = s
            prefs(c).edit().putString("fr_state", t).apply()
            loadPhotos(c, s)
            notifyNews(c, s, onScreen = false)
            birthdays(c, s)
        }
    }

    /**
     * Requests are told once. On screen they are only remembered (you can see them, and Friends has a red dot); in the
     * background each new one gives a notification. The same for a request of yours that was approved.
     */
    internal fun notifyNews(c: Context, s: FriendsState, onScreen: Boolean) {
        val p = prefs(c)
        val told = p.getStringSet("fr_in_told", null)?.toSet() ?: emptySet()
        val sent = p.getStringSet("fr_out", null)?.toSet() ?: emptySet()
        // Also kept on the Notifications page (2.95); already seen when it happened while Friends was on screen.
        for (r in s.incoming) if (r.id !in told) {
            if (!onScreen) Alerts.notifyFriend(c, r.id, r.name, request = true)
            Notifications.add(c, "friend_req", "r:${r.id}", r.name.ifBlank { tr("Someone") }, photos[r.id] ?: "", read = onScreen)
        }
        for (f in s.friends) if (f.id in sent) {
            // "Friend request accepted": only on the Notifications page, no buzz (owner, 2.122).
            Notifications.add(c, "friend_ok", "p:${f.id}", f.name.ifBlank { tr("Someone") }, photos[f.id] ?: "", read = onScreen)
        }
        p.edit()
            .putStringSet("fr_in_told", s.incoming.map { it.id }.toSet())
            // Your requests that are still waiting, so their approval can be told later.
            .putStringSet("fr_out", s.outgoing.map { it.id }.toSet())
            .apply()
    }

    internal fun parse(t: String): FriendsState? = try {
        val o = JSONObject(t)
        fun people(a: JSONArray?): List<Person> = if (a == null) emptyList() else (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            val ls = p.optJSONArray("lists")
            Person(
                p.getString("id"), p.optString("name", ""), p.optLong("pv", 0L), p.optBoolean("hidden", false),
                if (ls == null) emptyList() else (0 until ls.length()).map { j ->
                    val l = ls.getJSONObject(j)
                    FriendList(l.getString("code"), l.optString("name", ""), l.optInt("count", 0))
                },
                p.optString("birthday", ""), p.optString("bio", "")
            )
        }
        FriendsState(o.optString("code", ""), people(o.optJSONArray("friends")), people(o.optJSONArray("incoming")), people(o.optJSONArray("outgoing")))
    } catch (e: Exception) {
        null
    }

    suspend fun newCode(c: Context) {
        ensure(c)
        Cloud.rpc("wl_pf_new_code", base(c))
        refresh(c)
    }

    /** Returns the server's answer: status ("sent", "friends", "already", "self", "none") and their name. */
    suspend fun add(c: Context, code: String): Pair<String, String> {
        ensure(c)
        val o = JSONObject(Cloud.rpc("wl_fr_add", base(c).put("p_code", code)))
        refresh(c)
        return o.optString("status") to o.optString("name", "")
    }

    suspend fun answer(c: Context, id: String, yes: Boolean) {
        Cloud.rpc("wl_fr_answer", base(c).put("p_other", id).put("p_yes", yes))
        refresh(c)
    }

    /** Unfriend a friend, or take back a request you sent. */
    suspend fun remove(c: Context, id: String) {
        Cloud.rpc("wl_fr_remove", base(c).put("p_other", id))
        refresh(c)
    }

    /** A list of friend [id], only when they let you see it (null otherwise). */
    suspend fun fetchList(c: Context, id: String, code: String): RemoteList? {
        val t = Cloud.rpc("wl_fr_list", base(c).put("p_other", id).put("p_code", code)).trim()
        if (t.isEmpty() || t == "null") return null
        return Cloud.parseRemote(JSONObject(t))
    }
}
