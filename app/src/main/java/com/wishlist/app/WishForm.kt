package com.wishlist.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Close
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

// The Add / Edit wish form and its photo block. Moved out of MainActivity.kt in 2.163.

/** Adds a new wish, or edits [edit] when it is not null. */
@Composable
fun WishDialog(
    edit: Wish?, initialUrl: String, chooseList: Boolean = false, startTarget: String? = null,
    // Already read from the page (Find a gift, 2.114): used instead of loading [initialUrl] again.
    initialMeta: Meta? = null,
    // Opened from a shop in Find a gift: saving tells the shop, which stays open (2.116).
    fromShop: Boolean = false,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Which wishlist was open when this dialog started. A wish shared in from the browser can be saved to another one.
    val startList = remember { Shared.active }
    val targets = saveTargets(Repo.mineIds.map { it to (Repo.mineNames[it] ?: "My wishlist") }, Shared.lists)
    var url by remember { mutableStateOf(edit?.url ?: initialUrl) }
    var title by remember { mutableStateOf(edit?.title ?: "") }
    var price by remember { mutableStateOf(edit?.price?.let { "%.2f".format(Locale.US, it) } ?: "") }
    var desc by remember { mutableStateOf(edit?.desc ?: "") }
    var image by remember { mutableStateOf(edit?.image ?: "") }
    var cur by remember { mutableStateOf(edit?.currency ?: localCurrency()) }
    // A new wish starts without a category: you pick one yourself if you want (2.64).
    var cat by remember { mutableStateOf(edit?.category ?: "") }
    var note by remember { mutableStateOf(edit?.note ?: "") }
    var sizeSel by remember { mutableStateOf(edit?.size ?: "") }
    // How many, and "Exactly this" / "Similar is fine" (owner, 2.128).
    var qty by remember { mutableStateOf(edit?.qty ?: 1) }
    var exact by remember { mutableStateOf(edit?.exact ?: "") }
    var star by remember { mutableStateOf(edit?.star ?: false) }
    // "Private wish": only on this phone, friends never see it (My wishlist only). Off unless chosen.
    var priv by remember { mutableStateOf(edit?.priv ?: false) }
    val starFx = rememberStarFx()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    // True after Save was tapped without a name: the name field turns red with a short message.
    var nameErr by remember { mutableStateOf(false) }
    var newCatDlg by remember { mutableStateOf(false) }
    var editCats by remember { mutableStateOf(false) }
    // A picture picked in this dialog but not saved yet. It is deleted again if you cancel or replace it.
    var picked by remember { mutableStateOf("") }

    // "+ New wishlist" from the Save to menu: the usual form, then this wish goes there (owner, 7 November 2026).
    var newListDlg by remember { mutableStateOf(false) }
    // Take photo / Choose photo, then a free crop (owner, 7 November 2026: "so the picture is perfect").
    var photoMenu by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<String?>(null) }
    fun imported(p: String?) {
        if (p == null) err = tr("Couldn’t use that picture.") else { cropping = p; err = "" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { imported(ImageStore.import(ctx, uri)) }
    }
    val cameraFile = remember { java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "wish_camera.jpg") }
    // Made when the camera is opened, not when the screen is (2.183): nothing is prepared that may not be used.
    val cameraUriLazy = remember { lazy { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", cameraFile) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { imported(ImageStore.import(ctx, cameraUriLazy.value)) }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun takePhoto() = try { camera.launch(cameraUriLazy.value) } catch (e: Exception) { err = tr("Couldn’t open the camera.") }

    fun close() {
        if (picked.isNotEmpty()) ImageStore.delete(ctx, picked)
        // Cancelled after choosing another wishlist: go back to the one that was open.
        if (Shared.active != startList) {
            if (startList == null) Shared.close() else Shared.open(ctx, startList)
        }
        onClose()
    }

    // The price as the page gave it (2.178): another price at Save was typed by the person, and the first price check
    // leaves a typed price alone (PriceWatch).
    var readPrice by remember { mutableStateOf<String?>(null) }

    // Fills the form from what was read from a page (a link, or the shop page on screen in Find a gift).
    fun useMeta(m: Meta?) {
        if (m == null) {
            err = tr("Couldn’t read that page. Fill in the details yourself.")
        } else if (m.blocked) {
            err = tr("Couldn’t read this shop’s page. Fill in the name and price yourself.")
        } else {
            // The shop's sales text no longer fills Description (owner, 2.137): it stays free for your own words.
            title = m.title; cur = m.currency
            // A picture you chose yourself is kept. Otherwise the image comes from the link.
            if (!ImageStore.isLocal(image)) image = if (m.image.isNotEmpty() || edit == null) m.image else image
            if (m.price != null || edit == null) { price = m.price?.let { "%.2f".format(Locale.US, it) }.orEmpty(); readPrice = price }
            if (m.price == null) err = tr("No price found on that page. Add it to track price drops.")
        }
    }

    // The same product already on one of your lists (owner, 2.137): said under the link; saving still works.
    val dupOf = remember(url) {
        val u = cleanUrl(url)
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

    fun go(raw: String) {
        val u = cleanUrl(raw)
        if (u == null) { err = tr("Enter a full product link"); return }
        url = u; busy = true; err = ""
        scope.launch {
            val m = fetchMeta(u, ctx)
            busy = false
            useMeta(m)
        }
    }
    // A wish shared in from the browser goes where the last one went (My wishlist, private, or a shared list).
    fun pickTarget(key: String) {
        val mineId = MyLists.idOfSaveKey(key)
        if (mineId != null) {
            Shared.close()
            Repo.openMine(ctx, mineId)
        } else if (Shared.lists.any { it.code == key }) {
            Shared.open(ctx, key); priv = false
        }
        // Every wishlist has its own categories; none is picked for you.
        cat = ""
    }
    LaunchedEffect(Unit) {
        if (chooseList && edit == null) pickTarget(startTarget ?: Repo.lastSaveTo)
        if (edit == null && initialUrl.isNotEmpty()) {
            if (initialMeta != null) { url = initialUrl; useMeta(initialMeta) } else go(initialUrl)
        }
    }

    AlertDialog(
        onDismissRequest = { close() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (edit == null) tr("Add a wish") else tr("Edit wish")) },
        text = {
            // One scrolling page with three headings (owner, 2.128): Wish, Details, Visibility. Nothing folded away.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormHeading(tr("Wish"))
                if (chooseList && edit == null) {
                    SaveToField(
                        targets = targets, selected = Shared.active ?: MyLists.saveKey(Repo.openMine, false),
                        onPick = { pickTarget(it) },
                        modifier = Modifier.fillMaxWidth(),
                        onNew = { newListDlg = true }
                    )
                }
                if (clip != null && url.isBlank()) {
                    val host = try { java.net.URI(clip).host.orEmpty().removePrefix("www.") } catch (e: Exception) { "" }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { go(clip) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📋", modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Paste the link you copied"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (host.isNotEmpty()) Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                OutlinedTextField(
                    url,
                    { new ->
                        // Pasting a whole link fills in the details by itself.
                        val pasted = edit == null && new.length - url.length > 3 &&
                            Regex("https?://\\S+\\.\\S+").containsMatchIn(new)
                        url = new
                        if (pasted) go(new)
                    },
                    label = { Text(tr("Product link")) },
                    // No "Fetch details" button any more: pasting a link reads the page, and so does the keyboard's
                    // Done key after typing or changing one. A small circle shows while the page is being read.
                    trailingIcon = if (busy) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy && url.isNotBlank()) go(url) }),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                dupOf?.let { Text(tr("This item is already on “%s”", it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                // (No tip under the link any more: removed by the owner, 2.118.)
                Box {
                    ImageBlock(image, onPick = { photoMenu = true }, onRemove = {
                        if (picked.isNotEmpty() && picked == image) { ImageStore.delete(ctx, picked); picked = "" }
                        image = ""
                    })
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Take photo")) }, onClick = { photoMenu = false; takePhoto() })
                        DropdownMenuItem(text = { Text(tr("Choose photo")) }, onClick = { photoMenu = false; pickImage() })
                    }
                }
                // Name and price side by side: the price only needs room for "€9999,99", the name gets the rest and
                // stays on one line (a long name scrolls inside the field instead of taking three lines).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    // A name is needed only for a wish with a link (owner, 7 November 2026); without a link a name, a
                    // photo or the link itself is enough. Saving with too little marks the field red.
                    OutlinedTextField(
                        title, { title = it; if (it.isNotBlank()) nameErr = false },
                        label = { Text(if (url.isNotBlank()) tr("Name*") else tr("Name")) }, singleLine = true,
                        isError = nameErr,
                        supportingText = if (nameErr) ({
                            Text(if (url.isNotBlank()) tr("What are you wishing for?") else tr("Add a name, a photo or a link."))
                        }) else null,
                        modifier = Modifier.weight(1f), keyboardOptions = Capitalised)
                    OutlinedTextField(
                        // Narrow, sized for "9999,99"; any amount can be typed, a longer one scrolls inside the field.
                        price, { price = it },
                        label = { Text(tr("Price (%s)", cur), maxLines = 1) }, singleLine = true,
                        // The symbol of the currency in the label: € for EUR, $ for USD, £ for GBP, …
                        prefix = { Text(currencySymbol(cur)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(104.dp)
                    )
                }
                FormHeading(tr("Details"))
                // One line, however many categories there are (a row of chips got too long).
                CategoryField(cat, Repo.cats, onPick = { cat = it }, onNew = { newCatDlg = true }, onEdit = { editCats = true }, modifier = Modifier.fillMaxWidth())
                QuantityField(qty) { qty = it }
                ExactField(exact) { exact = it }
                SizeField(sizeSel, { sizeSel = it }, Modifier.fillMaxWidth())
                // No "Note for givers" field any more (owner, 2.137): Description is for your own words now. A note
                // written before stays with the wish and keeps showing.
                OutlinedTextField(
                    desc, { desc = it }, label = { Text(tr("Description")) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth(), keyboardOptions = Capitalised)
                // No "Top wish" on a shared list: nobody can say that for the person the gifts are for.
                if (Shared.active == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (!star) starFx.play(); star = !star }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StarIcon(star, if (star) StarGold else MaterialTheme.colorScheme.onSurfaceVariant, 24.dp, Modifier.starPulse(starFx))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Top wish"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            italics(tr("Shows people you _really_ like this")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = star, onCheckedChange = { star = it })
                }
                // Private wish: My wishlist only (a shared list is for the givers, there is nothing to hide there).
                if (Shared.active == null) FormHeading(tr("Visibility"))
                if (Shared.active == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { priv = !priv }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(PrivateIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Private wish"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("Hides this wish from everyone you share your wishlist with"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = priv, onCheckedChange = { priv = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank() && (url.isNotBlank() || image.isBlank())) { nameErr = true; return@TextButton }
                    val p = price.replace(',', '.').toDoubleOrNull()
                    val u = cleanUrl(url) ?: url.trim()
                    // A picture that was replaced or removed in this dialog is no longer needed.
                    if (edit != null && edit.image != image) ImageStore.delete(ctx, edit.image)
                    picked = ""
                    // Your own wishlists only: collab wishlists share one price between everyone's phones.
                    val typedPrice = p != null && Shared.active == null && u.isNotBlank() && price != readPrice &&
                        (edit == null || !PriceWatch.same(p, edit.price))
                    if (edit == null) {
                        val newId = System.currentTimeMillis()
                        if (typedPrice && p != null) PriceWatch.noteTyped(ctx, newId, u, p)
                        Repo.add(
                            ctx,
                            Wish(
                                newId, u, title.trim(), desc, image, p, null, cur, cat,
                                star = star && Shared.active == null, note = note.trim(), size = sizeSel, by = Repo.authorName,
                                priv = priv && Shared.active == null, qty = qty, exact = exact
                            )
                        )
                        if (chooseList) Repo.setLastSaveTo(ctx, Shared.active ?: MyLists.saveKey(Repo.openMine, false))
                        if (fromShop) ShopNav.saved(Repo.listName)
                    } else {
                        if (typedPrice && p != null) PriceWatch.noteTyped(ctx, edit.id, u, p)
                        Repo.replace(
                            ctx,
                            edit.copy(
                                url = u, title = title.trim(), desc = desc, image = image, price = p,
                                oldPrice = if (p == edit.price) edit.oldPrice else null,
                                currency = cur, category = cat, star = star && Shared.active == null, note = note.trim(), size = sizeSel,
                                priv = priv && Shared.active == null, qty = qty, exact = exact
                            ),
                            base = edit
                        )
                    }
                    onClose()
                }
            ) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text(tr("Cancel")) } }
    )

    // A category made here is chosen for this wish straight away.
    // The free crop after taking or choosing a photo (owner, 7 November 2026). Cancel keeps the photo as it was taken.
    // Since 2.144 the same editor as the profile and cover photo (owner, 7 November 2026): a square frame, move and
    // pinch, turn, lighter or darker, and "Fit whole photo". What is in the frame becomes the wish's photo. The back
    // arrow drops the new photo (the wish keeps the picture it had).
    cropping?.let { src ->
        HeroEditor(
            src, HeroEdit(), wish = true,
            onSave = { chosen, e ->
                cropping = null
                scope.launch {
                    val out = renderWishPhoto(ctx, chosen, e) ?: chosen
                    if (out != chosen) ImageStore.delete(ctx, chosen)
                    if (src != chosen && src != out) ImageStore.delete(ctx, src)
                    if (picked.isNotEmpty() && picked != out) ImageStore.delete(ctx, picked)
                    picked = out; image = out; if (title.isBlank()) nameErr = false
                }
            },
            onCancel = { cropping = null; ImageStore.delete(ctx, src) }
        )
    }
    if (newListDlg) NewListDialog(
        onCreated = { id -> newListDlg = false; pickTarget(MyLists.saveKey(id, false)) },
        onClose = { newListDlg = false }
    )
    if (newCatDlg) CategoryDialog(onAdded = { cat = it }) { newCatDlg = false }
    // The picked category follows a rename there; a deleted one becomes "No category".
    if (editCats) EditCategoriesScreen(onRenamed = { o, n -> if (cat == o) cat = n }) { editCats = false; if (cat.isNotEmpty() && cat !in Repo.cats) cat = "" }
}

/**
 * The picture of a wish (owner, 7 November 2026: not too small, not overpowering): a 128 dp square tile on the left.
 * Empty: a dashed tile with a plus and "Add photo". Filled: the photo fills the tile (wish photos are square since
 * 2.144; a picture from a shop page sits whole on white), tap to change, ✕ to remove.
 */
@Composable
fun ImageBlock(image: String, onPick: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline
    val tile = Modifier.size(128.dp).clip(RoundedCornerShape(16.dp))
    if (image.isEmpty()) {
        Column(
            tile.background(cs.surfaceVariant)
                .drawBehind {
                    drawRoundRect(
                        color = line, cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    )
                }
                .semantics { contentDescription = tr("Add photo") }
                .clickable(onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(AppIcons.Add, null, Modifier.size(32.dp), tint = cs.onSurfaceVariant)
            Text(tr("Add photo"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
    } else {
        Box(
            tile.background(Color.White)
                .semantics { contentDescription = tr("Change photo") }
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            // ✕ in the corner: 32 dp to see, a 48 dp area to tap (docs/STYLE.md).
            Box(
                Modifier.align(Alignment.TopEnd).size(48.dp)
                    .semantics { contentDescription = tr("Remove photo") }
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Close, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}
