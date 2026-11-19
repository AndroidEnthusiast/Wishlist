package com.wishlist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Group photos and the faces of members who aren't your friends, on this phone (a part of [Groups] since the data rebuild, owner 19 November 2026; callers keep writing Groups.…). */
internal object GroupPhotos {
    /** Group photos on this phone, by group id (a file address). */
    var photos by mutableStateOf<Map<String, String>>(emptyMap())
        internal set
    /** Photos of group members who aren't friends, by profile id (friends' photos are in [Friends.photos]). */
    var faces by mutableStateOf<Map<String, String>>(emptyMap())
        internal set
}

// ---- what you can do with it (callers keep writing Friends.…, as before the rebuild) ----

// ---- photos ----

internal fun Groups.groupDir(c: Context) = File(c.filesDir, "groups").apply { mkdirs() }
internal fun Groups.faceDir(c: Context) = File(c.filesDir, "groupfaces").apply { mkdirs() }

internal fun Groups.str(t: String) = t.trim().removeSurrounding("\"")

internal fun Groups.files(dir: File): Map<String, String> = (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }
    .associate { it.name.substringBefore('_') to Uri.fromFile(it).toString() }

internal suspend fun Groups.loadPhotos(c: Context) {
    val gd = groupDir(c)
    val want = groups.filter { it.pv > 0 }.associate { it.id to "${it.id}_${it.pv}.jpg" }
    val out = HashMap<String, String>()
    for ((id, name) in want) {
        val f = File(gd, name)
        if (!f.exists()) try {
            val bytes = PeoplePhotos.bytes(Cloud.rpc("wl_gr_photo", base(c).put("p_group", id))) ?: continue
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        } catch (e: Exception) {
            continue
        }
        out[id] = Uri.fromFile(f).toString()
    }
    (gd.listFiles() ?: emptyArray()).filter { it.name !in want.values }.forEach { it.delete() }
    GroupPhotos.photos = out

    // Members who aren't your friends (friends' photos are loaded by Friends).
    val fd = faceDir(c)
    val me = myId(c)
    val people = groups.flatMap { it.members }.filter { it.id != me && it.pv > 0 && !it.friend }.distinctBy { it.id }
    val wantFaces = people.associate { it.id to "${it.id}_${it.pv}.jpg" }
    val outFaces = HashMap<String, String>()
    for ((id, name) in wantFaces) {
        val f = File(fd, name)
        if (!f.exists()) try {
            val bytes = PeoplePhotos.bytes(Cloud.rpc("wl_gr_face", base(c).put("p_other", id))) ?: continue
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        } catch (e: Exception) {
            continue
        }
        outFaces[id] = Uri.fromFile(f).toString()
    }
    (fd.listFiles() ?: emptyArray()).filter { it.name !in wantFaces.values }.forEach { it.delete() }
    GroupPhotos.faces = outFaces
}

/** A member's picture: your own, a friend's, or one loaded for the group. */
fun Groups.faceOf(c: Context, m: GroupMember): String? =
    if (m.id == myId(c)) Friends.myPhoto.ifEmpty { null } else Friends.photos[m.id] ?: faces[m.id]

/**
 * The group's photo after the editor ([src] cut as [edit], in the frame [fa]): a small JPEG sent online, seen by
 * every member. Null [src]: the photo is removed.
 */
suspend fun Groups.setPhoto(c: Context, gid: String, src: String?, edit: HeroEdit, fa: Float) {
    val jpeg: ByteArray? = if (src == null) null else {
        val cut = renderCover(c, src, edit, fa) ?: throw CloudException("photo")
        withContext(Dispatchers.IO) {
            val f = File(Uri.parse(cut).path ?: throw CloudException("photo"))
            val bmp = BitmapFactory.decodeFile(f.path) ?: throw CloudException("photo")
            f.delete()
            val w = minOf(900, bmp.width)
            val small = Bitmap.createScaledBitmap(bmp, w, (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1), true)
            val out = java.io.ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }
    // As a file (2.185, PeoplePhotos), or the old way before the scripts are run.
    if (!PeoplePhotos.save("g/" + gid, base(c), "wl_gr_photo_url", base(c).put("p_group", gid), jpeg)) {
        Cloud.rpc("wl_gr_set_photo", base(c).put("p_group", gid).put("p_photo", jpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""))
    }
    refresh(c)
}
