package com.wishlist.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The state holder of a list's cover photo (the rebuild, owner 19 November 2026; MAP "How a screen is built"): used
 * by the cover's ⋮ on the list and by the cover part of the list's settings (CoverViews.kt), which both only draw.
 * The cover itself is kept by ListCovers (Covers.kt). [key]: the open list ("m:<id>" or "s:<code>"), [fa]: the
 * frame's shape.
 */
@Stable
internal class CoverModel(
    private val ctx: Context, private val scope: CoroutineScope, private val snack: SnackbarHostState,
    private val key: String, private val fa: Float
) {
    var menu by mutableStateOf(false)
    var change by mutableStateOf(false)
    // A photo just chosen or taken, open in the editor.
    var pending by mutableStateOf<String?>(null)
    // The cover there is, open in the editor again.
    var adjusting by mutableStateOf(false)

    /** A photo chosen or taken (its copy on the phone): the editor opens with it. */
    fun picked(p: String?) { p?.let { pending = it } }

    /** The editor's result: the cut-out cover is made and saved. */
    fun save(img: String, e: HeroEdit) {
        scope.launch {
            ListCovers.set(ctx, key, img, e, renderCover(ctx, img, e, fa) ?: "")
            snack.showToast(tr("Cover photo saved"))
        }
    }

    /** The editor saved a new photo ([img] was chosen; [chosen] may be another one picked inside the editor). */
    fun savePending(img: String, chosen: String, e: HeroEdit) {
        pending = null
        if (chosen != img) ImageStore.delete(ctx, img)
        save(chosen, e)
    }

    /** The editor's back arrow on a new photo: it goes. */
    fun dropPending(img: String) { pending = null; ImageStore.delete(ctx, img) }

    /** Delete from the cover's ⋮ (owner, 2.110): gone at once, with Undo. */
    fun delete() {
        val raw = ListCovers.detach(ctx, key) ?: return
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showToast(tr("Cover photo deleted"), actionLabel = tr("Undo"), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) ListCovers.restore(ctx, key, raw) else ListCovers.forget(ctx, raw)
        }
    }

    /** "Remove cover photo" in the list's settings. */
    fun remove() = ListCovers.remove(ctx, key)
}

/** The cover's state holder for the open list. */
@Composable
internal fun rememberCover(snack: SnackbarHostState): CoverModel {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val key = ListCovers.openKey()
    val fa = coverAspect()
    return remember(key) { CoverModel(ctx, scope, snack, key, fa) }
}

/** Choose a photo and Take a photo for a cover (the phone's photo picker and camera), each giving [m] the photo. */
internal class CoverPickers(val choose: () -> Unit, val take: () -> Unit)

@Composable
internal fun rememberCoverPickers(m: CoverModel): CoverPickers {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { m.picked(ImageStore.import(ctx, uri)) }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "cover_camera.jpg") }
    // Made when the camera is opened, not when the screen is (2.183): nothing is prepared that may not be used.
    val cameraUriLazy = remember { lazy { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { m.picked(ImageStore.import(ctx, cameraUriLazy.value)) }
    }
    return CoverPickers(
        choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        take = { try { camera.launch(cameraUriLazy.value) } catch (e: Exception) { } }
    )
}
