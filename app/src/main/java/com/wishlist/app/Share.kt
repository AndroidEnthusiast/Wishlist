package com.wishlist.app

import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** Keeps the online copy of the owner's list up to date, and handles share codes. */
object Sync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val lock = Mutex()
    private var app: Context? = null

    /** Shows how the last picture upload went, so a problem is visible in Settings. Empty when there is nothing to report. */
    var photoStatus by mutableStateOf("")
        private set

    fun attach(c: Context) { app = c.applicationContext }

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val EMBEDDED = Regex("WL-([A-HJ-NP-Z2-9]{8})")
    private val BARE = Regex("[A-HJ-NP-Z2-9]{8}")

    /** Pushes the list online shortly after the last change. Does nothing unless the list is shared. */
    @Synchronized
    fun push() {
        if (Repo.shareCode == null) return
        job?.cancel()
        job = scope.launch {
            delay(1500)
            try {
                pushNow()
            } catch (e: Exception) {
            }
        }
    }

    suspend fun pushNow() = lock.withLock { pushLocked() }

    /**
     * 1. uploads pictures you added yourself that are not online yet (small copies),
     * 2. pushes the list, with those pictures replaced by their web addresses,
     * 3. removes online pictures that no wish uses anymore.
     */
    private suspend fun pushLocked() {
        // Always the open list of your own, even while a group wishlist is open on screen.
        pushData(Repo.currentMine(), Repo.syncWishes(), live = true)
    }

    /**
     * Before another of your lists is opened: the one being left goes online now, as it is at this moment (a push
     * that was waiting would otherwise send the newly opened list instead). Called by Repo.openMine.
     */
    @Synchronized
    fun flush(c: Context) {
        if (Repo.shareCode == null) return
        job?.cancel()
        val d = Repo.currentMine()
        val wishes = Repo.syncWishes()
        scope.launch {
            try {
                lock.withLock { pushData(d, wishes, live = false) }
            } catch (e: Exception) {
            }
        }
    }

    /** Sends one of your lists that isn't open (changed by the background price check, for example). */
    fun pushStored(c: Context, d: MineData, binned: List<Wish>) {
        if (d.code == null) return
        val wishes = (d.wishes + binned).filter { !it.priv }
        scope.launch {
            try {
                lock.withLock { pushData(d, wishes, live = false) }
            } catch (e: Exception) {
            }
        }
    }

    /** [live]: the open list, whose picture status shows in its settings. */
    private suspend fun pushData(d: MineData, wishes: List<Wish>, live: Boolean) {
        val code = d.code ?: return
        val token = d.token ?: return
        val c = app
        if (c == null) {
            Cloud.pushList(code, token, d.name, d.cats, wishes, d.about)
            return
        }
        val mine = wishes.filter { ImageStore.isLocal(it.image) }.associateBy { it.image.substringAfterLast('/') }
        var up = d.photos
        var problem: String? = null

        for ((name, w) in mine) {
            if (name in up) continue
            val bytes = ImageStore.shareCopy(w.image) ?: continue
            try {
                Cloud.uploadPhoto(code, token, name, bytes)
                up = up + name
                Repo.setUploadedFor(c, d.id, up)
            } catch (e: Exception) {
                problem = e.message ?: tr("unknown error")
            }
        }

        // The cover photo goes online too (owner, 2.105: friends see it): the cut-out picture, with its text.
        val cover = ListCovers.get(c, "m:${d.id}")
        val coverName = cover?.cut?.takeIf { it.isNotEmpty() && ImageStore.isLocal(it) }?.substringAfterLast('/')
        var coverUrl = ""
        if (cover != null && coverName != null) {
            if (coverName !in up) {
                val bytes = ImageStore.shareCopy(cover.cut, 1200)
                if (bytes != null) try {
                    Cloud.uploadPhoto(code, token, coverName, bytes)
                    up = up + coverName
                    Repo.setUploadedFor(c, d.id, up)
                } catch (e: Exception) {
                    problem = e.message ?: tr("unknown error")
                }
            }
            if (coverName in up) coverUrl = Cloud.photoUrl(code, coverName)
        }
        val keep = mine.keys + listOfNotNull(coverName)

        val out = wishes.map { w ->
            if (!ImageStore.isLocal(w.image)) w
            else {
                val n = w.image.substringAfterLast('/')
                if (n in up) w.copy(image = Cloud.photoUrl(code, n)) else w.copy(image = "")
            }
        }
        Cloud.pushList(
            code, token, d.name, d.cats, out, d.about,
            coverUrl, if (coverUrl.isEmpty() || cover == null) "" else cover.edit.toJson(),
            Occasions.ofMine(c, d.id), Friends.sizesJson()
        )

        for (n in up - keep) {
            try {
                Cloud.deletePhoto(code, token, n)
                up = up - n
                Repo.setUploadedFor(c, d.id, up)
            } catch (e: Exception) {
                problem = problem ?: (e.message ?: tr("unknown error"))
            }
        }

        if (!live) return
        photoStatus = when {
            problem != null -> tr("Pictures could not be uploaded: %s", problem)
            mine.isEmpty() -> ""
            else -> tr("Pictures online: %1\$d of %2\$d", mine.keys.count { it in up }, mine.size)
        }
    }

    /** Makes the list available online (first time only) and returns its share code. */
    suspend fun publish(c: Context): String {
        val existing = Repo.shareCode
        if (existing != null) {
            pushNow()
            return existing
        }
        val code = newCode()
        val token = UUID.randomUUID().toString()
        Cloud.createList(code, token, Repo.myName)
        Repo.setShare(c, code, token)
        pushNow()
        return code
    }

    suspend fun unpublish(c: Context) = lock.withLock {
        val code = Repo.shareCode ?: return@withLock
        val token = Repo.ownerToken ?: return@withLock
        // Pictures first (Supabase checks the owner against the list), then the list itself.
        for (n in Repo.uploadedPhotos) {
            try {
                Cloud.deletePhoto(code, token, n)
            } catch (e: Exception) {
            }
        }
        Repo.setUploaded(c, emptySet())
        Cloud.deleteList(code, token)
        Repo.setShare(c, null, null)
        photoStatus = ""
    }

    /** Takes one of your lists offline that may not be open (before deleting it): its pictures, then the list. */
    suspend fun unpublishStored(d: MineData) = lock.withLock {
        val code = d.code ?: return@withLock
        val token = d.token ?: return@withLock
        for (n in d.photos) {
            try {
                Cloud.deletePhoto(code, token, n)
            } catch (e: Exception) {
            }
        }
        Cloud.deleteList(code, token)
    }

    /** Puts your list [id] online (open or not), when it isn't yet. */
    suspend fun publishFor(c: Context, id: String) {
        if (id == Repo.openMine) { publish(c); return }
        val d = Repo.mine(c, id)
        if (d.code != null) return
        val code = newCode()
        val token = UUID.randomUUID().toString()
        Cloud.createList(code, token, d.name)
        Repo.setShareFor(c, id, code, token)
        val nd = Repo.mine(c, id)
        lock.withLock { pushData(nd, (nd.wishes + Repo.binnedOf(id)).filter { !it.priv }, live = false) }
    }

    /** Takes your list [id] offline (open or not). */
    suspend fun unpublishFor(c: Context, id: String) {
        if (id == Repo.openMine) { unpublish(c); return }
        val d = Repo.mine(c, id)
        if (d.code == null) return
        unpublishStored(d)
        Repo.setShareFor(c, id, null, null)
        Repo.setUploadedFor(c, id, emptySet())
    }

    /** Runs [block] in the background (errors are ignored). */
    fun later(block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (e: Exception) {}
        }
    }

    fun newCode(): String {
        val r = SecureRandom()
        return (1..8).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** A long random secret: the "edit key" inside an invite link, or the owner token of a shared wishlist. */
    fun newKey(): String {
        val r = SecureRandom()
        return (1..20).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    private val INVITE_PARAM = Regex("[?&]J=([A-HJ-NP-Z2-9]{20})(?![A-Z0-9])")
    private val INVITE_BARE = Regex("[A-HJ-NP-Z2-9]{20}")

    /** Finds the edit key in an invite link (web or wishlist://), in text that contains one, or when only the key was pasted. */
    fun parseInvite(s: String): String? {
        val up = s.uppercase()
        INVITE_PARAM.find(up)?.let { return it.groupValues[1] }
        val bare = up.trim()
        return if (INVITE_BARE.matches(bare)) bare else null
    }

    /** Finds a "WL-XXXXXXXX" code inside shared text. */
    fun embeddedCode(s: String): String? = EMBEDDED.find(s.uppercase())?.groupValues?.get(1)

    /** Accepts "WL-ABCD2345", "abcd2345", or a whole shared message. */
    fun parseCode(s: String): String? {
        embeddedCode(s)?.let { return it }
        val bare = s.uppercase().replace(Regex("[^A-Z0-9]"), "")
        for (cand in listOf(bare, bare.removePrefix("WL"))) {
            if (BARE.matches(cand)) return cand
        }
        return null
    }
}

fun shareLink(code: String): String = Config.SHARE_URL.trimEnd('/') + "/?c=" + code

/**
 * The link you send to someone you want to edit a shared wishlist with. It goes through the page join/, which only
 * exists for its link preview ("Join our wishlist") and then opens the normal invite page.
 */
fun inviteLink(key: String): String = Config.SHARE_URL.trimEnd('/') + "/join/?j=" + key

/** What gets sent when you share your list: only the link, so it is easy to copy, paste and forward. */
fun inviteText(code: String): String = shareLink(code)

fun shareText(c: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    c.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Plain-text export of the whole list (works without any online setup). */
fun shareList(c: Context, l: List<Wish>) {
    // Private wishes are for you only: they are never in what you share.
    val body = l.filter { !it.priv }.joinToString("\n\n") { w ->
        val p = if (w.price != null) " - " + money(w.price, w.currency) else ""
        w.title + p + "\n" + w.url
    }
    shareText(c, tr("Share wishlist"), "${Repo.listName}\n\n$body")
}

/** Shares the online list code (publishing the list first if needed). Falls back to plain text if sharing isn't set up. */
fun startShare(c: Context, scope: CoroutineScope, snack: SnackbarHostState) {
    if (!Config.enabled) {
        shareList(c, Repo.wishes)
        return
    }
    scope.launch {
        try {
            val code = Sync.publish(c)
            shareText(c, tr("Share wishlist"), inviteText(code))
        } catch (e: Exception) {
            snack.showToast(tr("Couldn’t share: %s", e.message ?: tr("no connection")))
        }
    }
}

/** Sends this installed app's APK through the Android share sheet. */
suspend fun shareApk(c: Context) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(c.cacheDir, "apk")
        dir.mkdirs()
        val dst = File(dir, "Wishlist.apk")
        File(c.applicationInfo.sourceDir).copyTo(dst, overwrite = true)
        FileProvider.getUriForFile(c, c.packageName + ".files", dst)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    c.startActivity(Intent.createChooser(send, tr("Share Wishlist app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
