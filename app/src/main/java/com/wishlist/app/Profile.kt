package com.wishlist.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Your Profile (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): your photo and name, then rows (Name,
 * Birthday, About me, Keep your lists safe), Blocked people and Delete my profile.
 */

/**
 * Your profile (menu > Profile, or your picture on Friends): photo, name and friend code. The name is the one the app
 * uses everywhere (also "Added by" on group wishlists); it used to be in App settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(snack: SnackbarHostState) {
    // Profile like App settings (owner, 18 November 2026): one short page; "Keep your lists safe" opens its own page.
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    Moving(page, depth = { if (it.isEmpty()) 0 else 1 }) { p ->
        if (p.isEmpty()) ProfileMain(snack) { page = "safe" } else KeepSafePage(snack) { page = "" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMain(snack: SnackbarHostState, onSafe: () -> Unit) {
    val ctx = LocalContext.current
    val people = rememberPeople()
    // Which small editing sheet is open: "name", "birthday", "bio" or null.
    var editing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val clip = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Friends.load(ctx)
    val me = Shared.lastName
    var photoMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!Config.enabled) return@LaunchedEffect
        try { Friends.refresh(ctx) } catch (e: Exception) { }
    }

    // A new photo (chosen or taken) goes to the editor first, with a round frame, like the cover photo on Home.
    var pending by remember { mutableStateOf<String?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "profile_camera.jpg") }
    // Made when the camera is opened, not when the screen is (2.183): nothing is prepared that may not be used.
    val cameraUriLazy = remember { lazy { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { ImageStore.import(ctx, cameraUriLazy.value)?.let { pending = it } }
    }
    fun save(src: String, e: HeroEdit) {
        busy = true
        scope.launch {
            val ok = people.setPhoto(src, e)
            busy = false
            snack.showToast(if (ok) tr("Photo saved") else tr("Couldn’t save the photo online. Check your connection."))
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Profile")) },
                navigationIcon = { MenuSlot() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The photo, with a pencil: choose a photo, or remove it.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box {
                    Box(Modifier.clip(CircleShape).clickable { photoMenu = true }) { Avatar(Friends.myPhoto, me, 120.dp, ring = Iconic.myRing) }
                    if (busy) CircularProgressIndicator(Modifier.size(120.dp))
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).size(36.dp)
                            .lookChip(look, on = true).clickable { photoMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Edit, tr("Change photo"), tint = look.chipContent(true), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Choose a photo")) }, leadingIcon = { Icon(AppIcons.Gallery, null) }, onClick = {
                            photoMenu = false
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text(tr("Take a photo")) }, leadingIcon = { Icon(AppIcons.Camera, null) }, onClick = {
                            photoMenu = false
                            try {
                                camera.launch(cameraUriLazy.value)
                            } catch (e: Exception) {
                            }
                        })
                        if (Friends.photoSrc.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Edit profile photo")) }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = {
                            photoMenu = false
                            adjusting = true
                        })
                        if (Friends.myPhoto.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Remove photo")) }, leadingIcon = { Icon(AppIcons.Delete, null) }, danger = true, onClick = {
                            photoMenu = false
                            scope.launch {
                                try {
                                    people.removePhoto()
                                    snack.showToast(tr("Photo removed"))
                                } catch (e: Exception) {
                                    snack.showToast(tr("Couldn’t reach the server. Check your connection."))
                                }
                            }
                        })
                    }
                }
            }

            // Your name under the photo, big; tap to change (owner, 18 November 2026: Profile like App settings).
            // In the title font, like a wishlist's name (owner, 21 November 2026).
            Text(
                Shared.lastName.ifBlank { tr("Add your name") }, style = MaterialTheme.typography.headlineSmall,
                fontWeight = look.titleWeight, color = cs.onBackground, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = tr("Edit name")) { editing = "name" }
            )
            // Short rows with their value in grey and ›; each edits in a small sheet. Gen Z chaos: on a solid card.
            ProfileGroup {
                ValueRow(inset = 4.dp, label = tr("Name"), value = Shared.lastName.ifBlank { tr("Add") }) { editing = "name" }
                ValueRow(inset = 4.dp, label = tr("Birthday"), value = birthdayText(Friends.myBirthday).ifBlank { tr("Add") }) { editing = "birthday" }
                ValueRow(inset = 4.dp, label = tr("About me"), value = Friends.myBio.ifBlank { tr("Add") }) { editing = "bio" }
            }
            ProfileGroup {
                ValueRow(inset = 4.dp, label = tr("Keep your lists safe"), value = if (RecoveryKey.saved(ctx)) tr("Key saved") else tr("Not yet"), warn = !RecoveryKey.saved(ctx)) { onSafe() }
            }
            // Blocked people, when there are any (Safety.kt), and deleting everything (DeleteMe.kt), at the very bottom.
            BlockedPeople(snack)
            DeleteMeSection()
        }
    }

    when (editing) {
        "name" -> EditNameSheet { editing = null }
        "birthday" -> EditBirthdaySheet(snack) { editing = null }
        "bio" -> EditBioSheet { editing = null }
    }
    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(), round = true,
            onSave = { chosen, e ->
                pending = null
                // Another photo was picked in the editor: the first one isn't needed.
                if (chosen != img) ImageStore.delete(ctx, img)
                save(chosen, e)
            },
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
    if (adjusting && Friends.photoSrc.isNotEmpty()) {
        HeroEditor(
            Friends.photoSrc, Friends.photoEdit, round = true,
            onSave = { chosen, e -> adjusting = false; save(chosen, e) },
            onCancel = { adjusting = false }
        )
    }
}

/** Profile's rows on one card, like App settings' groups (Quiet); Gen Z chaos: its solid ReadingCard. */
@Composable
internal fun ProfileGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val look = LocalLook.current
    if (look.design != "quiet") { ReadingCard(content = content); return }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).lookCard(look, 3L, look.cardShape(3L, small = true)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content
    )
}

