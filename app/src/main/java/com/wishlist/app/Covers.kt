package com.wishlist.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

/*
 * A cover photo for one wishlist (2.89, owner): optional, none by default. Set in the list's settings with the same
 * editor as the profile photo (rectangular frame), with a Preview of how it looks at the top of the list. Kept on this
 * phone only ("cover_m:<id>" for your own lists, "cover_s:<code>" for group wishlists): nobody else sees it.
 */

data class ListCover(val image: String, val edit: HeroEdit, val cut: String)

object ListCovers {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    /** Goes up on every change, so screens show the new cover. */
    var version by mutableIntStateOf(0)
        private set

    /** The key of the list that is open: your own ("m:<id>") or a group wishlist ("s:<code>"). */
    fun openKey(): String = Shared.active?.let { "s:$it" } ?: "m:${Repo.openMine}"

    fun get(c: Context, key: String): ListCover? {
        @Suppress("UNUSED_VARIABLE") val v = version
        val t = prefs(c).getString("cover_$key", null) ?: return null
        return try {
            val o = JSONObject(t)
            val img = o.optString("img", "")
            if (img.isEmpty()) null else ListCover(img, HeroEdit.fromJson(o.optString("edit", "{}")), o.optString("cut", ""))
        } catch (e: Exception) {
            null
        }
    }

    /** Saves the cover of [key]: the whole photo, how it is cut, and the cut-out picture. The old pictures go. */
    fun set(c: Context, key: String, image: String, edit: HeroEdit, cut: String) {
        val old = get(c, key)
        if (old != null) {
            if (old.image != image) ImageStore.delete(c, old.image)
            if (old.cut.isNotEmpty() && old.cut != cut) ImageStore.delete(c, old.cut)
        }
        val o = JSONObject().put("img", image).put("edit", edit.toJson()).put("cut", cut)
        prefs(c).edit().putString("cover_$key", o.toString()).apply()
        version++
    }

    fun remove(c: Context, key: String) {
        get(c, key)?.let { ImageStore.delete(c, it.image); if (it.cut.isNotEmpty()) ImageStore.delete(c, it.cut) }
        prefs(c).edit().remove("cover_$key").apply()
        version++
    }

    /** Your list [from] now lives under the id [to] (the original list was deleted and the next took its place). */
    fun move(c: Context, from: String, to: String) {
        val p = prefs(c)
        val t = p.getString("cover_$from", null) ?: return
        p.edit().putString("cover_$to", t).remove("cover_$from").apply()
        version++
    }
}

/** The cover's shape (width / height): the width of the list (16 dp from both sides), 150 dp high. */
@Composable
fun coverAspect(): Float = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120) / 150f

/** The cover photo at the top of a list, when one was chosen. */
@Composable
fun ListCoverPicture(cover: ListCover, modifier: Modifier = Modifier) {
    HeroPicture(
        cover.image, cover.edit,
        modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)), cut = cover.cut
    )
}

/**
 * The narrow card at the top of a list: how many wishes and their total value (the total moved here from Home, 2.89).
 */
@Composable
fun ListSummaryCard(wishes: List<Wish>, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val id = 11L
    val total = totalValue(wishes)
    Row(
        modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(wishes.size.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = look.cardInk(id))
            Text(if (wishes.size == 1) tr("Wish") else tr("Wishes"), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
        }
        Box(Modifier.width(1.dp).height(32.dp).background(look.cardMuted(id).copy(alpha = 0.3f)))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(total ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = look.cardInk(id), maxLines = 1)
            Text(tr("Total value"), style = MaterialTheme.typography.bodySmall, color = look.cardMuted(id))
        }
    }
}

/** The top of a list, above its filters: the cover photo (only when chosen) and, on your own lists, the summary card. */
@Composable
fun ListHeader(wishes: List<Wish>) {
    val ctx = LocalContext.current
    val cover = remember(ListCovers.version, Shared.active, Repo.openMine) { ListCovers.get(ctx, ListCovers.openKey()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cover != null) ListCoverPicture(cover)
        // Only on your own lists (owner, 2.90): a group wishlist's total isn't what its givers need.
        if (Shared.active == null && Show["show_summary"]) ListSummaryCard(wishes)
    }
}

/**
 * List settings > Cover photo: none by default. Choose or take a photo, adjust it in the editor (with Preview), and
 * later edit or remove it.
 */
@Composable
fun CoverSection(snack: androidx.compose.material3.SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val key = ListCovers.openKey()
    val cover = remember(ListCovers.version, key) { ListCovers.get(ctx, key) }
    val fa = coverAspect()
    var pending by remember { mutableStateOf<String?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "cover_camera.jpg") }
    val cameraUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { ImageStore.import(ctx, cameraUri)?.let { pending = it } }
    }
    fun save(img: String, e: HeroEdit) {
        scope.launch {
            ListCovers.set(ctx, key, img, e, renderCover(ctx, img, e, fa) ?: "")
            snack.showToast(tr("Cover photo saved"))
        }
    }

    Text(
        tr("Optional. Shown at the top of this wishlist. Only you see it."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
    if (cover != null) ListCoverPicture(cover, Modifier.padding(vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (cover == null) tr("Choose a photo") else tr("New photo"))
        }
        OutlinedButton(onClick = {
            try {
                camera.launch(cameraUri)
            } catch (e: Exception) {
            }
        }) { Text(tr("Take a photo")) }
    }
    if (cover != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { adjusting = true }) { Text(tr("Edit cover photo")) }
            TextButton(onClick = { ListCovers.remove(ctx, key) }) { Text(tr("Remove cover photo"), color = cs.error) }
        }
    }

    val preview: @Composable (String, HeroEdit, () -> Unit) -> Unit = { img, e, close -> CoverPreview(img, e, close) }
    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(), preview = preview,
            onSave = { chosen, e ->
                pending = null
                if (chosen != img) ImageStore.delete(ctx, img)
                save(chosen, e)
            },
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
    if (adjusting && cover != null) {
        HeroEditor(
            cover.image, cover.edit, preview = preview,
            onSave = { chosen, e -> adjusting = false; save(chosen, e) },
            onCancel = { adjusting = false }
        )
    }
}

/**
 * Preview (in the editor): the top of this list as it will look with the photo as it is now in the frame, with the
 * summary card and the first wishes under it. Nothing is saved; "Back to editing" returns to the editor.
 */
@Composable
fun CoverPreview(img: String, edit: HeroEdit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val wishes = Repo.wishes
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Text(
                Repo.listName, style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The photo exactly as in the frame now (worked out from the edit, as on the list).
                HeroPicture(img, edit, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)))
                if (Shared.active == null) ListSummaryCard(wishes)
                wishes.take(4).forEach { w ->
                    val id = w.id
                    Row(
                        Modifier.fillMaxWidth().lookCard(look, id, look.cardShape(id, small = true)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(look.pictureBg(id, w.image.isNotBlank())),
                            contentAlignment = Alignment.Center
                        ) {
                            if (w.image.isNotBlank()) AsyncImage(w.image, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                            else PlaceholderIcon("gift", 20.dp)
                        }
                        Text(
                            w.title, style = MaterialTheme.typography.bodyLarge, color = look.cardInk(id), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Button(onClick = onClose) { Text(tr("Back to editing")) }
            }
        }
    }
}
