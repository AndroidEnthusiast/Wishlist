package com.wishlist.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

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
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** What the Friends tab shows (the last answer from the server, also kept on the phone for offline use). */
    var state by mutableStateOf<FriendsState?>(null)
        private set

    /** Your own photo on this phone (a file), or "". */
    var myPhoto by mutableStateOf("")
        private set

    /** Ids of your own lists that friends can see. */
    var visible by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Friends' photos on this phone, by profile id (a file address). */
    var photos by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        myPhoto = p.getString("pf_photo", "") ?: ""
        myBirthday = p.getString("pf_bday", "") ?: ""
        myBio = p.getString("pf_bio", "") ?: ""
        photoSrc = p.getString("pf_src", "") ?: ""
        photoEdit = HeroEdit.fromJson(p.getString("pf_edit", null))
        visible = p.getStringSet("fr_vis", null)?.toSet() ?: emptySet()
        state = p.getString("fr_state", null)?.let { parse(it) }
        val dir = photoDir(c)
        photos = (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
            .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }
    }

    /** True once this phone has a profile online. */
    fun hasProfile(c: Context) = prefs(c).getString("pf_id", null) != null

    /** Requests that wait for your approval: a red dot on Friends. */
    val waiting: Int get() = state?.incoming?.size ?: 0

    private fun ids(c: Context): Pair<String, String> {
        val p = prefs(c)
        val id = p.getString("pf_id", null) ?: Sync.newKey().also { p.edit().putString("pf_id", it).apply() }
        val token = p.getString("pf_token", null) ?: Sync.newKey().also { p.edit().putString("pf_token", it).apply() }
        return id to token
    }

    private fun base(c: Context): JSONObject {
        val (id, token) = ids(c)
        return JSONObject().put("p_id", id).put("p_token", token)
    }

    private fun str(t: String): String = t.trim().removeSurrounding("\"")

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
        } else if (p.getBoolean("pf_about_dirty", false)) pushAbout(c)
    }

    // ---- birthday and "I like…" (2.95) ----

    /** Your birthday as "MM-DD", or "". */
    var myBirthday by mutableStateOf("")
        private set
    /** Your "I like…" line. */
    var myBio by mutableStateOf("")
        private set

    /** Saves your birthday and "I like…" on the phone, then online (an error is thrown when offline: saved next time). */
    suspend fun setAbout(c: Context, birthday: String, bio: String) {
        myBirthday = birthday
        myBio = bio.trim().take(120)
        // "Not sent yet" until the server has it: an offline save goes online with the next refresh ([ensure]).
        prefs(c).edit().putString("pf_bday", myBirthday).putString("pf_bio", myBio).putBoolean("pf_about_dirty", true).apply()
        ensure(c)
        pushAbout(c)
    }

    private suspend fun pushAbout(c: Context) {
        Cloud.rpc("wl_pf_about", base(c).put("p_birthday", myBirthday).put("p_bio", myBio))
        prefs(c).edit().putBoolean("pf_about_dirty", false).apply()
    }

    /** Days until the next birthday "MM-DD" (0 = today), or null without one. */
    fun daysUntil(birthday: String, today: java.time.LocalDate = java.time.LocalDate.now()): Int? {
        val m = Regex("^(\\d{2})-(\\d{2})$").find(birthday) ?: return null
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        fun on(year: Int): java.time.LocalDate? = try {
            // 29 February in a year without it: the 28th.
            java.time.LocalDate.of(year, month, if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) 28 else day)
        } catch (e: Exception) {
            null
        }
        var next = on(today.year) ?: return null
        if (next.isBefore(today)) next = on(today.year + 1) ?: return null
        return java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
    }

    /**
     * Birthday reminders (owner, 2.95): 14 days before a friend's birthday, and again 7 days before only when you
     * haven't reserved anything on their lists yet. Each once a year. Runs with the background check.
     */
    private suspend fun birthdays(c: Context, s: FriendsState) {
        val p = prefs(c)
        val year = java.time.LocalDate.now().year
        for (f in s.friends) {
            val d = daysUntil(f.birthday) ?: continue
            val key14 = "bd14_${f.id}_$year"
            val key7 = "bd7_${f.id}_$year"
            if (d in 8..14 && !p.getBoolean(key14, false)) {
                Alerts.notifyBirthday(c, f.id, f.name, d)
                Notifications.add(c, "birthday", "p:${f.id}", f.name, photos[f.id] ?: "", amount = d.toDouble())
                p.edit().putBoolean(key14, true).apply()
            } else if (d in 1..7 && !p.getBoolean(key7, false)) {
                p.edit().putBoolean(key7, true).putBoolean(key14, true).apply()
                // Only when nothing on their lists is reserved by this phone.
                val reserved = f.lists.any { l ->
                    try {
                        Cloud.fetchReservations(l.code, Repo.deviceId).values.any { it }
                    } catch (e: Exception) {
                        true // can't tell: better no reminder than a wrong one
                    }
                }
                if (!reserved) {
                    Alerts.notifyBirthday(c, f.id, f.name, d)
                    Notifications.add(c, "birthday", "p:${f.id}", f.name, photos[f.id] ?: "", amount = d.toDouble())
                }
            }
        }
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
    private fun notifyNews(c: Context, s: FriendsState, onScreen: Boolean) {
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

    private fun parse(t: String): FriendsState? = try {
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

    // ---- photos ----

    private fun photoDir(c: Context) = File(c.filesDir, "friends").apply { mkdirs() }

    /** Loads the photos that are new or changed ("<id>_<version>.jpg"); removes ones nobody needs any more. */
    private suspend fun loadPhotos(c: Context, s: FriendsState) {
        val dir = photoDir(c)
        val people = s.friends + s.incoming + s.outgoing
        val want = people.filter { it.pv > 0 }.associate { it.id to "${it.id}_${it.pv}.jpg" }
        val out = HashMap<String, String>()
        for ((id, name) in want) {
            val f = File(dir, name)
            if (!f.exists()) {
                try {
                    val b64 = str(Cloud.rpc("wl_fr_photo", base(c).put("p_other", id)))
                    if (b64.isEmpty()) continue
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    withContext(Dispatchers.IO) { f.writeBytes(bytes) }
                } catch (e: Exception) {
                    continue
                }
            }
            out[id] = Uri.fromFile(f).toString()
        }
        (dir.listFiles() ?: emptyArray()).filter { it.name !in want.values }.forEach { it.delete() }
        photos = out
    }

    /** The photo your profile picture was cut from (kept so it can be edited again), and how it was cut. */
    var photoSrc by mutableStateOf("")
        private set
    var photoEdit by mutableStateOf(HeroEdit())
        private set

    /**
     * Your profile photo after the editor (round frame, see [HeroEditor]): [src] is the whole photo, [edit] how it is
     * turned, cut and lightened. The part in the circle is saved as a small square JPEG and sent online.
     * Returns false when it couldn't be sent (it is kept on the phone and sent the next time).
     */
    suspend fun setPhoto(c: Context, src: String, edit: HeroEdit): Boolean {
        val cut = renderCover(c, src, edit, 1f) ?: return false
        val file = withContext(Dispatchers.IO) {
            try {
                val cutFile = File(Uri.parse(cut).path ?: return@withContext null)
                val bmp = BitmapFactory.decodeFile(cutFile.path)
                cutFile.delete()
                if (bmp == null) return@withContext null
                val side = min(bmp.width, bmp.height)
                val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                val small = Bitmap.createScaledBitmap(sq, 320, 320, true)
                val f = File(c.filesDir, "profile_" + System.currentTimeMillis() + ".jpg")
                f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                f
            } catch (e: Exception) {
                null
            }
        } ?: return false
        oldPhotoFile()?.delete()
        if (photoSrc.isNotEmpty() && photoSrc != src) ImageStore.delete(c, photoSrc)
        myPhoto = Uri.fromFile(file).toString()
        photoSrc = src
        photoEdit = edit
        prefs(c).edit().putString("pf_photo", myPhoto).putString("pf_src", src).putString("pf_edit", edit.toJson()).apply()
        return try {
            ensure(c)
            uploadPhoto(c)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun oldPhotoFile(): File? = Uri.parse(myPhoto).path?.let { File(it) }?.takeIf { myPhoto.isNotEmpty() }

    private suspend fun uploadPhoto(c: Context) {
        val f = oldPhotoFile()
        val b64 = if (f != null && f.exists()) Base64.encodeToString(withContext(Dispatchers.IO) { f.readBytes() }, Base64.NO_WRAP) else ""
        Cloud.rpc("wl_pf_photo", base(c).put("p_photo", b64))
    }

    suspend fun removePhoto(c: Context) {
        oldPhotoFile()?.delete()
        if (photoSrc.isNotEmpty()) ImageStore.delete(c, photoSrc)
        myPhoto = ""
        photoSrc = ""
        photoEdit = HeroEdit()
        prefs(c).edit().putString("pf_photo", "").putString("pf_src", "").putString("pf_edit", "").apply()
        if (prefs(c).getBoolean("pf_made", false)) Cloud.rpc("wl_pf_photo", base(c).put("p_photo", ""))
    }

    // ---- codes and requests ----

    /** The friend code as people read it: "K7Q-4MZ". */
    fun pretty(code: String) = if (code.length == 6) code.substring(0, 3) + "-" + code.substring(3) else code

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

    // ---- which of your lists friends can see ----

    fun isVisible(id: String) = id in visible

    /** Turns "Visible to friends" on or off for your list [id] (its code and token must be known when on). */
    suspend fun setVisible(c: Context, id: String, on: Boolean) {
        val before = visible
        visible = if (on) visible + id else visible - id
        prefs(c).edit().putStringSet("fr_vis", visible).apply()
        try {
            ensure(c)
            pushLists(c)
        } catch (e: Exception) {
            // Not saved online: the switch goes back.
            visible = before
            prefs(c).edit().putStringSet("fr_vis", visible).apply()
            throw e
        }
    }

    /** A list went offline or was deleted: friends can't see it any more. */
    fun forget(c: Context, id: String) {
        load(c)
        if (id !in visible) return
        visible = visible - id
        prefs(c).edit().putStringSet("fr_vis", visible).apply()
    }

    /** Your list [from] now lives under the id [to] (the original list was deleted and the next took its place). */
    fun moveId(c: Context, from: String, to: String) {
        load(c)
        if (from !in visible) return
        visible = visible - from + to
        prefs(c).edit().putStringSet("fr_vis", visible).apply()
    }

    /** Sends which lists friends can see (each with its owner token, so the server knows they are yours). */
    suspend fun pushLists(c: Context) {
        val a = JSONArray()
        for (d in Repo.allMine(c)) {
            if (d.id !in visible) continue
            val code = d.code ?: continue
            val token = d.token ?: continue
            a.put(JSONObject().put("code", code).put("token", token))
        }
        Cloud.rpc("wl_pf_lists", base(c).put("p_lists", a))
    }

    // ---- sharing your code ----

    fun shareCode(c: Context, code: String) {
        val text = tr(
            "Let’s be friends on Wishlist, so we can see each other’s wishlists. My friend code is %1\$s. In the app, tap Friends and then Add a friend. No app yet? Get it here: %2\$s",
            pretty(code), Config.APK_URL
        )
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(i, tr("Share your friend code")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
