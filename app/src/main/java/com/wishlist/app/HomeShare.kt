package com.wishlist.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The share card of one of your own wishlists (owner, 7 November 2026, after comparing GoWish and Giftful). Opened from
 * the ⋮ on a list's block on Home, and from the share button on the list itself.
 *  - The list: its photos (a collage of three), its name and "3 wishes", so you see what you are sharing.
 *  - One big "Share" (Android's share sheet: WhatsApp, Signal, mail …), then a slim row with the link and "Copy".
 *    Only one "copied" message: Android 13 and newer show their own, so ours only shows on older phones.
 *  - A link can't open a list on "Only me" or "My friends": then sharing is greyed out with the reason and one button
 *    that lets anyone with the link see it. No dead link is ever sent.
 *  - Offline (that switch needs the server): "You're offline. Connect to share this list." with "Try again".
 *  - A list still called "My wishlist" is named first (owner, 2.135).
 */
@Composable
fun ListShareDialog(id: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val clip = LocalClipboardManager.current
    if (id !in Repo.mineIds) {
        LaunchedEffect(id) { onClose() }
        return
    }
    val d = remember(id, Repo.mineVersion, Repo.myWishes, Repo.myName, Repo.shareCode, ListVisibility.version) { Repo.mine(ctx, id) }
    val who = remember(id, Repo.mineVersion, Repo.shareCode, ListVisibility.version, ListLink.off) { ListVisibility.of(ctx, id) }
    val shown = d.wishes.filter { !it.priv }
    val pics = newestPics(shown)
    val unnamed = d.name == "My wishlist" || d.name == tr("My wishlist")
    val name = if (unnamed) tr("My wishlist") else d.name
    var busy by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var newName by remember(id) { mutableStateOf(if (Shared.lastName.isNotBlank()) tr("%s’s wishlist", Shared.lastName.trim()) else "") }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }

    fun makeVisible() {
        busy = true
        scope.launch {
            offline = try {
                ListVisibility.set(ctx, id, Who.LINK)
                false
            } catch (e: Exception) {
                true
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The list itself.
                val pid = id.hashCode().toLong()
                Box(
                    Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)).background(look.pictureBg(pid, pics.isNotEmpty())),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        pics.size >= 3 -> Collage(pics)
                        pics.isNotEmpty() -> WishImage(pics[0], Modifier.fillMaxSize(), ContentScale.Crop)
                        else -> PlaceholderIcon("gift", 44.dp)
                    }
                }
                Text(
                    name, style = MaterialTheme.typography.titleLarge, fontWeight = look.titleWeight, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(wishesLabel(shown.size), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

                val code = d.code
                when {
                    !Config.enabled -> Text(tr("Online sharing isn’t set up in this build yet."), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    unnamed -> {
                        Text(
                            tr("Give your wishlist a name first, so friends know whose it is."),
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                        OutlinedTextField(
                            newName, { newName = it.take(60) }, singleLine = true, label = { Text(tr("Wishlist name*")) },
                            keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = newName.isNotBlank() && newName.trim() != tr("My wishlist"),
                            onClick = { Repo.renameMine(ctx, id, newName.trim()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tr("Save name")) }
                    }
                    who != Who.LINK || code == null -> {
                        // Greyed out: a link can't open this list (owner, 7 November 2026), with one way to fix it.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(AppIcons.Lock, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(
                                if (who == Who.FRIENDS) tr("Only your friends in the app can see this wishlist, so a link won’t open it.")
                                else tr("Only you can see this wishlist, so a link won’t open it."),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Box(Modifier.alpha(0.38f)) { ShareButtons(null, false, {}, {}) }
                        if (offline) Text(
                            tr("You’re offline. Connect to share this list."), style = MaterialTheme.typography.bodyMedium,
                            color = cs.error, textAlign = TextAlign.Center
                        )
                        Button(enabled = !busy, onClick = { makeVisible() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (offline) tr("Try again") else tr("Let anyone with the link see it"))
                        }
                    }
                    else -> {
                        ShareButtons(
                            shareLink(code), copied,
                            onShare = { shareText(ctx, tr("Share wishlist"), inviteText(code)) },
                            onCopy = {
                                clip.setText(AnnotatedString(shareLink(code)))
                                // Android 13 and newer say "Copied" themselves.
                                if (Build.VERSION.SDK_INT < 33) copied = true
                            }
                        )
                        Text(
                            tr("Don’t open your own link: you’d see what’s reserved."),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}

/** The big Share button and, under it, the link with "Copy". [link] null: greyed out (nothing happens). */
@Composable
private fun ShareButtons(link: String?, copied: Boolean, onShare: () -> Unit, onCopy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        Button(enabled = link != null, onClick = onShare, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(AppIcons.Share, null, modifier = Modifier.size(20.dp))
            Text(tr("Share"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.fillMaxWidth().lookSurface(LocalLook.current, MaterialTheme.shapes.small, cs.surfaceVariant, raised = false)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                (link ?: Config.SHARE_URL).removePrefix("https://"), style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            TextButton(enabled = link != null, onClick = onCopy) {
                Text(if (copied) tr("Copied") else tr("Copy"), fontWeight = FontWeight.Bold, color = if (copied) look.drop else cs.primary)
            }
        }
    }
}
