package com.wishlist.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

// The Add / Edit wish form (moved out of MainActivity.kt in 2.163; rebuilt 19 November 2026: what it holds and does is
// WishFormState in WishFormModel.kt, its photo block and smaller parts are in WishFormParts.kt).

/** Adds a new wish, or edits [edit] when it is not null. */
@Composable
fun WishDialog(
    edit: Wish?, initialUrl: String, chooseList: Boolean = false, startTarget: String? = null,
    // Already read from the page (Find a gift, 2.114): used instead of loading [initialUrl] again.
    initialMeta: Meta? = null,
    // Opened from a shop in Find a gift: saving tells the shop, which stays open (2.116).
    fromShop: Boolean = false,
    // Screenshot to wish (owner, 20 November 2026; ShotsFlow.kt): the cut screenshot as the photo; with several,
    // "Wish 2 of 5", Skip, "Save and next", and Cancel asks first ([onCancelAsk]). [onSaved] gets the wish's name.
    initialImage: String = "",
    step: Pair<Int, Int>? = null,
    onSkip: (() -> Unit)? = null,
    onCancelAsk: (() -> Unit)? = null,
    onSaved: ((String) -> Unit)? = null,
    onClose: () -> Unit
) {
    val places = LocalPlaces.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // What is typed and chosen, reading a link, the photo and saving (WishFormState in WishFormModel.kt).
    val st = remember { WishFormState(ctx, edit, chooseList, fromShop, places, scope, initialUrl, initialImage) }
    val targets = saveTargets(Repo.mineIds.map { it to (Repo.mineNames[it] ?: "My wishlist") }, Shared.lists)
    val starFx = rememberStarFx()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { st.imported(ImageStore.import(ctx, uri)) }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "wish_camera.jpg") }
    // Made when the camera is opened, not when the screen is (2.183): nothing is prepared that may not be used.
    val cameraUriLazy = remember { lazy { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { st.imported(ImageStore.import(ctx, cameraUriLazy.value)) }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun takePhoto() = try { camera.launch(cameraUriLazy.value) } catch (e: Exception) { st.err = tr("Couldn’t open the camera.") }

    fun close() {
        if (onCancelAsk != null) { onCancelAsk(); return }
        st.leave()
        onClose()
    }
    fun saveNow() {
        val name = st.title.trim()
        if (st.save()) { onSaved?.invoke(name); onClose() }
    }

    // The same product already on one of your lists (owner, 2.137): said under the link; saving still works.
    val dupOf = remember(st.url) {
        val u = cleanUrl(st.url)
        if (u == null || edit != null) null
        else Repo.allMine(ctx).firstOrNull { d -> d.wishes.any { w -> w.bin == 0L && cleanUrl(w.url) == u } }
            ?.let { if (it.name == "My wishlist") tr(it.name) else it.name }
    }
    // A product link on the clipboard (owner, 2.137): offered with one tap, never pasted by itself.
    val clip = remember {
        if (edit != null || initialUrl.isNotEmpty()) null else try {
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val c = cm.primaryClip
            val t = if (c != null && c.itemCount > 0) c.getItemAt(0).coerceToText(ctx)?.toString() else null
            t?.let { Regex("https?://\\S+").find(it)?.value }?.let { cleanUrl(it) }
        } catch (e: Exception) { null }
    }

    LaunchedEffect(Unit) { st.start(startTarget, initialUrl, initialMeta) }

    // Quiet (2.207, docs/styles/quiet.md): ✕ at the top to cancel and Save as a full-width button at the bottom.
    val quiet = LocalLook.current.design == "quiet"

    AlertDialog(
        onDismissRequest = { close() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            val heading = if (step != null) tr("Wish %d of %d", step.first, step.second) else if (edit == null) tr("Add a wish") else tr("Edit wish")
            // Quiet's dialogs centre their title (2.210): the ✕ sits at the left edge, the title in the middle.
            if (quiet) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(onClick = { close() }, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-12).dp)) {
                    Icon(AppIcons.Close, tr("Cancel"))
                }
                Text(heading, modifier = Modifier.padding(horizontal = 40.dp))
            } else Text(heading)
        },
        text = {
            // The form trimmed (owner, 16 November 2026: "it feels very full"): no headings; adding starts with the link
            // (it fills in the photo, name and price), editing with the photo; Love it and Private as two small chips;
            // the rarely used details folded under "More details" (open on Edit when one is filled); the exact-or-similar
            // choice is gone (owner: removed everywhere).
            val linkField: @Composable () -> Unit = {
                val host = clip?.let { c -> try { java.net.URI(c).host.orEmpty().removePrefix("www.") } catch (e: Exception) { "" } }.orEmpty()
                val canPaste = clip != null && st.url.isBlank() && !st.busy
                OutlinedTextField(
                    st.url,
                    { new ->
                        // Pasting a whole link fills in the details by itself.
                        val pasted = edit == null && new.length - st.url.length > 3 &&
                            Regex("https?://\\S+\\.\\S+").containsMatchIn(new)
                        st.url = new
                        if (pasted) st.go(new)
                    },
                    label = { Text(tr("Product link")) },
                    // The copied link is offered inside the field (owner, 16 November 2026), not as a block above it.
                    placeholder = if (canPaste && host.isNotEmpty()) ({ Text(tr("Copied from %s", host), maxLines = 1) }) else null,
                    // No "Fetch details" button: pasting a link reads the page, and so does the keyboard's Done key after
                    // typing or changing one. A small circle shows while the page is being read.
                    trailingIcon = when {
                        st.busy -> ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
                        canPaste -> ({ TextButton(onClick = { clip?.let { st.go(it) } }) { Text(tr("Paste")) } })
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!st.busy && st.url.isNotBlank()) st.go(st.url) }),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (st.err.isNotEmpty()) {
                    Text(st.err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                dupOf?.let { Text(tr("This item is already on “%s”", it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            val photo: @Composable () -> Unit = {
                ImageBlock(st.image, onPick = { st.photoMenu = true }, onRemove = { st.removePhoto() }, big = true)
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chooseList && edit == null) {
                    SaveToField(
                        targets = targets, selected = Shared.active ?: MyLists.saveKey(Repo.openMine, false),
                        onPick = { st.pickTarget(it) },
                        modifier = Modifier.fillMaxWidth(),
                        onNew = { st.newListDlg = true }
                    )
                }
                if (edit == null) linkField()
                photo()
                // No "Love it" or "Private" on a shared list: nobody can say that for the person the gifts are for, and
                // a shared list is for the givers (there is nothing to hide there).
                if (Shared.active == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = st.star, onClick = { if (!st.star) starFx.play(); st.star = !st.star },
                        label = { Text(tr("Love it")) },
                        leadingIcon = { LoveIcon(st.star, if (st.star) StarGold else MaterialTheme.colorScheme.onSurfaceVariant, 18.dp, Modifier.starPulse(starFx)) },
                        modifier = Modifier.semantics { stateDescription = if (st.star) tr("Givers see it first") else "" }
                    )
                    FilterChip(
                        selected = st.priv, onClick = { st.priv = !st.priv },
                        label = { Text(tr("Private")) },
                        leadingIcon = { Icon(PrivateIcon, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.semantics { stateDescription = if (st.priv) tr("Only you see it") else "" }
                    )
                }
                if (edit != null) linkField()
                // Name and price side by side: the price only needs room for "€9999,99", the name gets the rest and
                // stays on one line (a long name scrolls inside the field instead of taking three lines).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    // Every wish needs a name (owner, 20 November 2026; before, a photo alone was enough). Saving without
                    // one marks the field red.
                    OutlinedTextField(
                        st.title, { st.title = it; if (it.isNotBlank()) st.nameErr = false },
                        label = { Text(tr("Name*")) }, singleLine = true,
                        isError = st.nameErr,
                        supportingText = if (st.nameErr) ({ Text(tr("What are you wishing for?")) }) else null,
                        modifier = Modifier.weight(1f), keyboardOptions = Capitalised)
                    OutlinedTextField(
                        // Narrow, sized for "9999,99"; any amount can be typed, a longer one scrolls inside the field.
                        st.price, { st.price = it },
                        label = { Text(tr("Price (%s)", st.cur), maxLines = 1) }, singleLine = true,
                        // The symbol of the currency in the label: € for EUR, $ for USD, £ for GBP, …
                        prefix = { Text(currencySymbol(st.cur)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(104.dp)
                    )
                }
                // "More details" (owner, 16 November 2026): category, how many, size and description folded away; open
                // by itself when editing a wish that has one of them, so nothing is changed unseen. Folded, it says what
                // is set.
                MoreDetails(
                    summary = detailsSummary(st.cat, st.qty, st.sizeSel, st.desc),
                    startOpen = edit != null && detailsSummary(edit.category, edit.qty, edit.size, edit.desc).isNotEmpty()
                ) {
                    // One line, however many categories there are; it opens a bottom sheet (16 November 2026).
                    CategoryField(st.cat, Repo.cats, onPick = { st.cat = it }, onNew = { st.newCatDlg = true }, onEdit = { st.editCats = true }, modifier = Modifier.fillMaxWidth())
                    SizeField(st.sizeSel, { st.sizeSel = it }, Modifier.fillMaxWidth())
                    QuantityField(st.qty) { st.qty = it }
                    // No "Note for givers" field any more (owner, 2.137): Description is for your own words now. A note
                    // written before stays with the wish and keeps showing.
                    OutlinedTextField(
                        st.desc, { st.desc = it }, label = { Text(tr("Description")) },
                        maxLines = 3, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                }
            }
        },
        confirmButton = {
            // With more screenshots to come: "Save and next" (each wish is saved at once, owner 20 November 2026).
            val more = step != null && step.first < step.second
            if (quiet) Button(onClick = { saveNow() }, modifier = Modifier.fillMaxWidth()) { Text(if (more) tr("Save and next") else tr("Save wish")) }
            else TextButton(onClick = { saveNow() }) { Text(if (more) tr("Save and next") else tr("Save")) }
        },
        dismissButton = when {
            onSkip != null -> ({ TextButton(onClick = onSkip) { Text(tr("Skip")) } })
            quiet -> null
            else -> ({ TextButton(onClick = { close() }) { Text(tr("Cancel")) } })
        }
    )

    // Take photo or Choose photo (2.212): a small bottom sheet like every other menu, not a drop-down.
    if (st.photoMenu) PhotoSourceSheet(onTake = { st.photoMenu = false; takePhoto() }, onChoose = { st.photoMenu = false; pickImage() }) { st.photoMenu = false }

    // A category made here is chosen for this wish straight away.
    // The free crop after taking or choosing a photo (owner, 7 November 2026). Cancel keeps the photo as it was taken.
    // Since 2.144 the same editor as the profile and cover photo (owner, 7 November 2026): a square frame, move and
    // pinch, turn, lighter or darker, and "Fit whole photo". What is in the frame becomes the wish's photo. The back
    // arrow drops the new photo (the wish keeps the picture it had).
    st.cropping?.let { src ->
        HeroEditor(
            src, HeroEdit(), wish = true,
            onSave = { chosen, e -> st.photoEdited(src, chosen, e) },
            onCancel = { st.dropCrop(src) }
        )
    }
    if (st.newListDlg) NewListDialog(
        onCreated = { id -> st.newListDlg = false; st.pickTarget(MyLists.saveKey(id, false)) },
        onClose = { st.newListDlg = false }
    )
    if (st.newCatDlg) CategoryDialog(onAdded = { st.cat = it }) { st.newCatDlg = false }
    // The picked category follows a rename there; a deleted one becomes "No category".
    if (st.editCats) EditCategoriesScreen(onRenamed = { o, n -> if (st.cat == o) st.cat = n }) { st.editCats = false; if (st.cat.isNotEmpty() && st.cat !in Repo.cats) st.cat = "" }
}
