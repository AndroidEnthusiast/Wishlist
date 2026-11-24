package com.wishlist.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

/* Messages (see MessagesList.kt): the bar to type in, picking a wish to send, and photos full screen. */

/**
 * The bar to type in (owner, 24 November 2026, like Signal): a + (a wish, a photo from the gallery, a whole wishlist:
 * [onPlus]), the text field with the camera inside it on the right ([onCamera] opens it straight away), and send.
 */
@Composable
internal fun InputBar(
    text: String, onText: (String) -> Unit, sending: Boolean, onSend: () -> Unit, onPlus: () -> Unit,
    onCamera: () -> Unit,
    // A wish is attached ("Chat about this wish"): it can be sent without words too.
    attached: Boolean = false
) {
    val look = LocalLook.current
    val ready = (text.isNotBlank() || attached) && !sending
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // The + sits in a box exactly as tall as a one-line text box, so its middle lines up with it in every style
        // (owner, 2.128); bottom-aligned when the text box grows.
        Box(
            Modifier.size(48.dp, 56.dp).clip(CircleShape).clickable(enabled = !sending, onClickLabel = tr("Send a wish, photo or wishlist"), onClick = onPlus),
            contentAlignment = Alignment.Center
        ) { Icon(AppIcons.Add, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(28.dp)) }
        val shape = RoundedCornerShape(24.dp)
        TextField(
            text, onText, placeholder = { Text(tr("Message")) }, maxLines = 5,
            trailingIcon = {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !sending, onClickLabel = tr("Take a photo"), onClick = onCamera),
                    contentAlignment = Alignment.Center
                ) { PhotoIcon(cs.onSurfaceVariant) }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f).lookSurface(look, shape, cs.surfaceVariant, raised = false)
        )
        Box(
            Modifier.padding(start = 8.dp, bottom = 4.dp).size(48.dp).lookSurface(look, look.fabShape, if (!ready) cs.outline else look.accent)
                .clickable(enabled = ready, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = look.onAccent, strokeWidth = 2.dp)
            else SendIcon(look.onAccent)
        }
    }
}

/** The +'s menu (owner, 24 November 2026): a sheet like every menu, each row with its icon in a tile. */
@Composable
internal fun PlusMenu(onWish: () -> Unit, onGallery: () -> Unit, onList: () -> Unit, onClose: () -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onClose) {
        DropdownMenuItem(text = { Text(tr("A wish from my wishlists")) }, leadingIcon = { Icon(AppIcons.Gift, null) }, onClick = { onClose(); onWish() })
        DropdownMenuItem(text = { Text(tr("Photo from gallery")) }, leadingIcon = { Icon(AppIcons.Gallery, null) }, onClick = { onClose(); onGallery() })
        DropdownMenuItem(text = { Text(tr("A whole wishlist")) }, leadingIcon = { Icon(AppIcons.ListIcon, null) }, onClick = { onClose(); onList() })
    }
}

/** Pick one of your own wishlists to send as a card; a list only you can see says so (it asks first when picked). */
@Composable
internal fun ListPicker(onPick: (MineData) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lists = remember { Repo.allMine(ctx) }
    PickSheet(tr("Send a wishlist"), onClose = onClose) {
        SheetGroup(lists.map { d ->
            {
                val n = d.wishes.count { !it.priv && it.bin == 0L }
                val hidden = d.code == null || ListVisibility.of(ctx, d.id) == Who.ME
                val count = if (n == 0) tr("No wishes yet") else if (n == 1) tr("1 wish") else tr("%d wishes", n)
                SheetRow(
                    { Icon(listKindIcon(false), null) }, if (d.name == "My wishlist") tr(d.name) else d.name, { onPick(d) },
                    detail = if (hidden) tr("%s · Only you can see it", count) else count
                )
            }
        })
    }
}

/** Pick a wish from your own lists to send. */
@Composable
internal fun WishPicker(onPick: (Wish, String?) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lists = remember { Repo.allMine(ctx) }
    // A bottom sheet like every picker (owner, 22 November 2026; PickSheet.kt): each wishlist's wishes in a group.
    PickSheet(tr("Send a wish"), onClose = onClose) {
        // No wishes yet: say so and how to add one (the review of 20 November 2026: the picker stood empty).
        if (lists.none { d -> d.wishes.any { !it.priv && it.bin == 0L } }) PickNote(tr("You have no wishes to send yet. Add one with the + at the bottom, then send it here."))
        else lists.forEach { d ->
            val ws = d.wishes.filter { !it.priv && it.bin == 0L }
            if (ws.isNotEmpty()) {
                PickHeading(if (d.name == "My wishlist") tr(d.name) else d.name)
                SheetGroup(ws.map { w -> { SheetRow({ PickWishPhoto(w) }, w.label, { onPick(w, d.code) }, tile = false) } })
            }
        }
    }
}

/** The photo you are about to send, full screen on black, with Cancel and Send (owner, 2.109: a preview first). */
@Composable
internal fun PhotoPreview(image: String, onCancel: () -> Unit, onSend: () -> Unit) {
    val look = LocalLook.current
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(model = image, contentDescription = tr("Photo"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                LinkButton(onClick = onCancel) { Text(tr("Cancel"), color = Color.White) }
                Row(
                    Modifier.lookSurface(look, look.fabShape, look.accent).clickable(onClick = onSend).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("Send"), color = look.onAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    SendIcon(look.onAccent)
                }
            }
        }
    }
}

/** A photo from the chat, full screen on black: pinch to zoom, drag to move, double tap to reset; Back closes it. */
@Composable
internal fun PhotoViewer(file: File, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = file, contentDescription = tr("Photo"), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            )
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
                Icon(AppIcons.Close, tr("Close"), tint = Color.White)
            }
        }
    }
}
