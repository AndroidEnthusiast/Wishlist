package com.wishlist.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/*
 * The people on a collab wishlist, as small circles in the bottom right corner of its block on Home (owner,
 * 7 November 2026): up to three pictures (a profile photo, or the first letter), then a white "+2" circle when there
 * are more. App members come from the server (supabase-collab.sql, wl_sh_people, for everyone holding the list's
 * edit key); people who only use the browser have no profile, so the names in "Added by" that aren't an app member
 * are added as letters (owner's choice, knowing a name typed in two ways counts twice). Shown from 2 people.
 */

/** An app member of a collab wishlist: profile id, name and photo version. */
data class CollabMember(val id: String, val name: String, val pv: Long)

/** One person on a collab wishlist: [photo] is a file address on this phone, or null (then the first letter). */
data class CollabPerson(val key: String, val name: String, val photo: String?)

object CollabPeople {
    /** App members per list code. Remembered on the phone, refreshed online. */
    var members by mutableStateOf<Map<String, List<CollabMember>>>(emptyMap())
        private set
    /** Photos of members who aren't your friends, by profile id (a file address). */
    var faces by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private val lock = Mutex()
    private var loaded = false
    private val fetched = HashSet<String>()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)
    private fun faceDir(c: Context) = File(c.filesDir, "collabfaces").apply { mkdirs() }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        members = try {
            val o = org.json.JSONObject(prefs(c).getString("collab_people", null) ?: "{}")
            o.keys().asSequence().associateWith { k -> parse(o.getJSONArray(k)) }
        } catch (e: Exception) {
            emptyMap()
        }
        faces = (faceDir(c).listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
            .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }
    }

    private fun parse(a: JSONArray): List<CollabMember> = (0 until a.length()).map { a.getJSONObject(it) }
        .map { CollabMember(it.optString("id"), it.optString("name"), it.optLong("pv")) }.filter { it.id.isNotEmpty() }

    private fun save(c: Context) {
        val o = org.json.JSONObject()
        members.forEach { (code, l) ->
            val a = JSONArray()
            l.forEach { m -> a.put(org.json.JSONObject().put("id", m.id).put("name", m.name).put("pv", m.pv)) }
            o.put(code, a)
        }
        prefs(c).edit().putString("collab_people", o.toString()).apply()
    }

    /**
     * Asks the server who is on [l] (once per app start per list) and loads the photos that are new. Quietly keeps
     * what the phone knew when offline or before the Supabase script was run.
     */
    suspend fun refresh(c: Context, l: SharedList) {
        load(c)
        if (!Config.enabled || !fetched.add(l.code)) return
        lock.withLock {
            val list = try {
                parse(JSONArray(Cloud.rpc("wl_sh_people", org.json.JSONObject().put("p_key", l.key))))
            } catch (e: Exception) {
                fetched.remove(l.code); return
            }
            members = members + (l.code to list)
            save(c)
            // Photos: yours and your friends' are on the phone already; the rest are loaded for this list.
            val me = Friends.auth(c)?.optString("p_id").orEmpty()
            val dir = faceDir(c)
            val out = HashMap(faces)
            for (m in list) {
                if (m.id == me || m.pv <= 0 || Friends.photos.containsKey(m.id)) continue
                val f = File(dir, "${m.id}_${m.pv}.jpg")
                if (!f.exists()) try {
                    val bytes = PeoplePhotos.bytes(Cloud.rpc("wl_sh_face", org.json.JSONObject().put("p_key", l.key).put("p_profile", m.id))) ?: continue
                    withContext(Dispatchers.IO) {
                        (dir.listFiles() ?: emptyArray()).filter { it.name.startsWith(m.id + "_") }.forEach { it.delete() }
                        f.writeBytes(bytes)
                    }
                } catch (e: Exception) {
                    continue
                }
                out[m.id] = Uri.fromFile(f).toString()
            }
            faces = out
        }
    }

    /** Everyone on list [code]: app members first (oldest first), then browser people from "Added by" as letters. */
    fun of(c: Context, code: String, wishes: List<Wish>): List<CollabPerson> {
        load(c)
        val me = Friends.auth(c)?.optString("p_id").orEmpty()
        val app = members[code].orEmpty().map { m ->
            val photo = when {
                m.id == me -> Friends.myPhoto.ifEmpty { null }
                else -> Friends.photos[m.id] ?: faces[m.id]
            }
            CollabPerson(m.id, m.name, photo)
        }
        // Your own phone is on the list even before the server knows it (or without a profile online).
        val withMe = if (app.isEmpty() && Shared.lastName.isNotBlank()) listOf(CollabPerson("me", Shared.lastName, Friends.myPhoto.ifEmpty { null })) else app
        fun same(a: String, b: String): Boolean {
            val x = a.trim().lowercase()
            val y = b.trim().lowercase()
            return x == y || x.substringBefore(' ') == y.substringBefore(' ')
        }
        val names = withMe.map { it.name }
        val web = wishes.map { it.by.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .filter { n -> names.none { same(it, n) } }
            .map { CollabPerson("web:" + it.lowercase(), it, null) }
        return withMe + web
    }
}

/**
 * The circles in the bottom right corner of a collab wishlist's block: up to three people, overlapping a little with
 * a white edge, then a white "+N". 26 dp each (owner: a bit smaller than a chat's send button). Nothing below 2 people.
 */
@Composable
fun PeopleCircles(code: String, wishes: List<Wish>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val l = Shared.lists.firstOrNull { it.code == code }
    CollabPeople.load(ctx)
    LaunchedEffect(code) { if (l != null) CollabPeople.refresh(ctx, l) }
    // Reading the state here redraws the circles when the members or photos come in.
    val people = androidx.compose.runtime.remember(CollabPeople.members, CollabPeople.faces, Friends.photos, wishes) { CollabPeople.of(ctx, code, wishes) }
    if (people.size < 2) return
    val shown = people.take(3)
    val more = people.size - shown.size
    val size = 26.dp
    val label = people.joinToString(", ") { it.name.ifBlank { tr("Someone") } }
    Row(
        modifier.semantics { contentDescription = tr("On this list: %s", label) },
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { p ->
            Box(Modifier.size(size).clip(CircleShape).background(Color.White).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Avatar(p.photo, p.name, size - 4.dp)
            }
        }
        if (more > 0) Box(
            Modifier.size(size).clip(CircleShape).background(Color.White).border(1.dp, Color(0x22000000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("+$more", color = Color(0xFF1F1F1F), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
