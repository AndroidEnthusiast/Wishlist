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
import org.json.JSONObject
import java.io.File
import kotlin.math.min

/** Your profile photo and your friends' photos on this phone (a part of [Friends] since the data rebuild, owner 19 November 2026). */
internal object FriendPhotos {
    /** Your own photo on this phone (a file), or "". */
    var myPhoto by mutableStateOf("")
        internal set

    /** Friends' photos on this phone, by profile id (a file address). */
    var photos by mutableStateOf<Map<String, String>>(emptyMap())
        internal set

    /** The photo your profile picture was cut from (kept so it can be edited again), and how it was cut. */
    var photoSrc by mutableStateOf("")
        internal set
    var photoEdit by mutableStateOf(HeroEdit())
        internal set
}

// ---- what you can do with it (callers keep writing Friends.…, as before the rebuild) ----

internal fun Friends.photoDir(c: Context) = File(c.filesDir, "friends").apply { mkdirs() }

/** Loads the photos that are new or changed ("<id>_<version>.jpg"); removes ones nobody needs any more. */
internal suspend fun Friends.loadPhotos(c: Context, s: FriendsState) {
    val dir = photoDir(c)
    val people = s.friends + s.incoming + s.outgoing
    val want = people.filter { it.pv > 0 }.associate { it.id to "${it.id}_${it.pv}.jpg" }
    val out = HashMap<String, String>()
    for ((id, name) in want) {
        val f = File(dir, name)
        if (!f.exists()) {
            try {
                // A file's address or an embedded photo from an older app (PeoplePhotos, 2.185).
                val bytes = PeoplePhotos.bytes(Cloud.rpc("wl_fr_photo", base(c).put("p_other", id))) ?: continue
                withContext(Dispatchers.IO) { f.writeBytes(bytes) }
            } catch (e: Exception) {
                continue
            }
        }
        out[id] = Uri.fromFile(f).toString()
    }
    (dir.listFiles() ?: emptyArray()).filter { it.name !in want.values }.forEach { it.delete() }
    FriendPhotos.photos = out
}

/**
 * Your profile photo after the editor (round frame, see [HeroEditor]): [src] is the whole photo, [edit] how it is
 * turned, cut and lightened. The part in the circle is saved as a small square JPEG and sent online.
 * Returns false when it couldn't be sent (it is kept on the phone and sent the next time).
 */
suspend fun Friends.setPhoto(c: Context, src: String, edit: HeroEdit): Boolean {
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
    FriendPhotos.myPhoto = Uri.fromFile(file).toString()
    FriendPhotos.photoSrc = src
    FriendPhotos.photoEdit = edit
    prefs(c).edit().putString("pf_photo", myPhoto).putString("pf_src", src).putString("pf_edit", edit.toJson()).apply()
    return try {
        ensure(c)
        uploadPhoto(c)
        true
    } catch (e: Exception) {
        false
    }
}

internal fun Friends.oldPhotoFile(): File? = Uri.parse(myPhoto).path?.let { File(it) }?.takeIf { myPhoto.isNotEmpty() }

internal suspend fun Friends.uploadPhoto(c: Context) {
    val f = oldPhotoFile()
    val bytes = if (f != null && f.exists()) withContext(Dispatchers.IO) { f.readBytes() } else null
    savePhotoBytes(c, bytes)
}

/** Your profile photo online: as a file (2.185, PeoplePhotos), or the old way before the scripts are run. */
internal suspend fun Friends.savePhotoBytes(c: Context, bytes: ByteArray?): Boolean {
    val b = base(c)
    if (PeoplePhotos.save("p/" + b.getString("p_id"), b, "wl_pf_photo_url", base(c), bytes)) return true
    Cloud.rpc("wl_pf_photo", base(c).put("p_photo", bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""))
    return true
}

/** Moves your profile photo from the database to a file, once (2.185). */
suspend fun Friends.movePhoto(c: Context) {
    val b = auth(c) ?: return
    PeoplePhotos.moveOld(c, "p", { Cloud.rpc("wl_fr_photo", JSONObject(b.toString()).put("p_other", b.getString("p_id"))) }) { bytes ->
        PeoplePhotos.save("p/" + b.getString("p_id"), b, "wl_pf_photo_url", base(c), bytes)
    }
}

suspend fun Friends.removePhoto(c: Context) {
    oldPhotoFile()?.delete()
    if (photoSrc.isNotEmpty()) ImageStore.delete(c, photoSrc)
    FriendPhotos.myPhoto = ""
    FriendPhotos.photoSrc = ""
    FriendPhotos.photoEdit = HeroEdit()
    prefs(c).edit().putString("pf_photo", "").putString("pf_src", "").putString("pf_edit", "").apply()
    if (prefs(c).getBoolean("pf_made", false)) savePhotoBytes(c, null)
}
