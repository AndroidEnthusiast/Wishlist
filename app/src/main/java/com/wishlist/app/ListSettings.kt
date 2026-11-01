package com.wishlist.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch

/** The heading of one of the four groups of a list's settings. */
@Composable
private fun GroupTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleLarge, fontWeight = LocalLook.current.titleWeight,
        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 28.dp)
    )
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp)
    )
}

/**
 * The settings of the list that is open (the ⚙ in its top bar): name, note for friends, categories and sharing.
 * On a shared list also your name on that list and the manage buttons. Settings for the whole app are in [SettingsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListSettingsScreen(snack: SnackbarHostState, onDeleted: (() -> Unit)? = null, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val shared = Shared.current
    var name by remember { mutableStateOf(Repo.listName) }
    var about by remember { mutableStateOf(Repo.about) }
    var newCat by remember { mutableStateOf("") }
    var delCat by remember { mutableStateOf<String?>(null) }
    var renCat by remember { mutableStateOf<String?>(null) }
    var stopDlg by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<String?>(null) }
    var meDlg by remember { mutableStateOf(false) }
    var deleteMineDlg by remember { mutableStateOf(false) }
    var copyDlg by remember { mutableStateOf(false) }
    var visBusy by remember { mutableStateOf(false) }
    var nameSaved by remember { mutableStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Repo.listName, fontWeight = LocalLook.current.titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (shared != null) tr("Wishlist for someone") else tr("Wishlist settings"),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Four groups (owner, 2.95): about this list, who can see it, its wishes, and the danger zone at the bottom.
            GroupTitle(tr("About this list"))
            SectionTitle(tr("Name of the wishlist"))
            // Like your name on Profile: no Save button, the keyboard's tick saves it and closes the keyboard, then a
            // green tick with "Saved" (2.88). An empty name isn't saved: the old one comes back.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) {
                            Repo.setListName(ctx, name)
                            nameSaved++
                        }
                        name = Repo.listName
                        keyboard?.hide()
                        focusManager.clearFocus()
                    })
                )
                SavedFeedback(nameSaved)
            }
            if (shared != null) {
                Text(tr("Everyone on this list sees the new name."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

                SectionTitle(tr("Your name on this list"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(shared.me.ifBlank { Shared.lastName.ifBlank { tr("No name yet") } }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (shared.me.isBlank() || shared.me == Shared.lastName) tr("From your profile. Shown as “Added by” on the wishes you add.")
                            else tr("Only on this list. Your profile has “%s”.", Shared.lastName),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { meDlg = true }) { Text(tr("Use another name")) }
                }
            }

            // An optional cover photo at the top of the list (2.89): none until you choose one.
            SectionTitle(tr("Cover photo"))
            CoverSection(snack)

            SectionTitle(tr("Note for friends"))
            Text(
                if (shared != null) tr("Shown at the top of the list. For example sizes, or things the person already has.")
                else tr("Shown at the top of your wishlist. For example your sizes, or things you don’t want."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            OutlinedTextField(
                about, { about = it.take(ABOUT_MAX) }, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${about.length}/$ABOUT_MAX") }, keyboardOptions = Capitalised)
            Button(onClick = {
                Repo.setAbout(ctx, about)
                about = Repo.about
                scope.launch { snack.showToast(tr("Note saved")) }
            }) { Text(tr("Save note")) }


            GroupTitle(tr("Who can see it"))
            if (shared != null) {
                val title = Repo.listName
                val invite = inviteLink(shared.key)
                SectionTitle(tr("Invite people"))
                Text(
                    tr("They can add and change wishes and reserve gifts. Only send it to people you trust."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { ShareCard.share(ctx, ShareCard.Kind.INVITE, title, invite) } }) { Text(tr("Send invite")) }
                    OutlinedButton(onClick = {
                        clip.setText(AnnotatedString(invite))
                        scope.launch { snack.showToast(tr("Invite link copied")) }
                    }) { Text(tr("Copy link")) }
                }
                if (shared.owner.isNotEmpty()) {
                    TextButton(onClick = { manage = "reset" }) { Text(tr("Make a new invite link")) }
                }
            } else {
                WhoCanSeeSection(snack)
            }

            GroupTitle(tr("Wishes"))
            SectionTitle(tr("Categories"))
            if (Repo.cats.isEmpty()) {
                Text(tr("No categories yet."), color = cs.onSurfaceVariant)
            }
            Repo.cats.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(c, Modifier.weight(1f))
                    IconButton(onClick = { renCat = c }) {
                        Icon(AppIcons.Edit, tr("Edit %s", c), tint = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { delCat = c }) {
                        Icon(AppIcons.Delete, tr("Remove %s", c), tint = cs.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newCat, { newCat = it }, label = { Text(tr("New category")) },
                    singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = Capitalised)
                Button(onClick = { Repo.addCat(ctx, newCat); newCat = "" }) { Text(tr("Add")) }
            }

            if (shared == null) {
                // Copy wishes from your other lists into this one (also offered when a list is made).
                if (canCopyFromOthers(ctx, Repo.openMine)) {
                    TextButton(onClick = { copyDlg = true }) { Text(tr("Add wishes from your other lists")) }
                }
                TextButton(onClick = { shareList(ctx, Repo.wishes) }) { Text(tr("Export as plain text")) }
            }

            // Lists you made yourself can be deleted (you always keep one); a wishlist for someone can be left, or
            // deleted for everyone by the person who made it.
            if (shared != null || Repo.mineIds.size > 1) {
                GroupTitle(tr("Danger zone"))
                if (shared != null) {
                    TextButton(onClick = { manage = "leave" }) { Text(tr("Leave this wishlist")) }
                    if (shared.owner.isNotEmpty()) {
                        TextButton(onClick = { manage = "delete" }) { Text(tr("Delete for everyone"), color = cs.error) }
                    }
                } else {
                    TextButton(onClick = { deleteMineDlg = true }) { Text(tr("Delete this wishlist"), color = cs.error) }
                }
            }
        }
    }

    delCat?.let { dc -> DeleteCategoryDialog(dc, onDeleted = {}, onClose = { delCat = null }) }
    renCat?.let { rc -> RenameCategoryDialog(rc, onRenamed = {}, onClose = { renCat = null }) }
    manage?.let { m -> SharedManageDialog(m, onClose = { manage = null }, onGone = { manage = null; onBack() }) }
    if (meDlg && shared != null) {
        var n by remember { mutableStateOf(shared.me.ifBlank { Shared.lastName }) }
        AlertDialog(
            onDismissRequest = { meDlg = false },
            title = { Text(tr("Your name on this list")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("Only for “%s”. Leave it empty to use the name from your profile.", Repo.listName),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    OutlinedTextField(n, { n = it.take(30) }, singleLine = true, label = { Text(tr("Your name")) }, keyboardOptions = Capitalised)
                }
            },
            confirmButton = {
                TextButton(onClick = { Shared.setMe(ctx, shared.code, n); meDlg = false }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { meDlg = false }) { Text(tr("Cancel")) } }
        )
    }
    if (copyDlg) {
        val target = Repo.openMine
        CopyWishesDialog(
            target,
            onDone = { ws ->
                copyDlg = false
                Repo.copyInto(ctx, target, ws)
                scope.launch { snack.showToast(if (ws.size == 1) tr("1 wish added") else tr("%d wishes added", ws.size)) }
            },
            onClose = { copyDlg = false }
        )
    }
    if (deleteMineDlg) {
        val id = Repo.openMine
        AlertDialog(
            onDismissRequest = { deleteMineDlg = false },
            title = { Text(tr("Delete “%s”?", Repo.listName)) },
            text = { Text(tr("The wishlist will be deleted and its wishes moved to the bin. You can restore them there for 30 days.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleteMineDlg = false
                    scope.launch {
                        // Online first (when it has a friends' link), then on the phone.
                        if (Repo.shareCode != null) {
                            try {
                                Sync.unpublish(ctx)
                            } catch (e: Exception) {
                            }
                        }
                        Repo.deleteMine(ctx, id)
                        (onDeleted ?: onBack)()
                    }
                }) { Text(tr("Delete"), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleteMineDlg = false }) { Text(tr("Cancel")) } }
        )
    }
    if (stopDlg) {
        AlertDialog(
            onDismissRequest = { stopDlg = false },
            title = { Text(tr("Stop sharing?")) },
            text = {
                Text(
                    if (Friends.isVisible(Repo.openMine)) tr("The link stops working, your friends can no longer see this list, and all reservations are deleted. Sharing again later creates a new link.")
                    else tr("The link stops working and all reservations are deleted. Sharing again later creates a new link.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    stopDlg = false
                    scope.launch {
                        try {
                            Sync.unpublish(ctx)
                            // Offline means friends can't see it either (the server forgets it by itself).
                            Friends.forget(ctx, Repo.openMine)
                        } catch (e: Exception) {
                            snack.showToast(tr("Couldn’t stop sharing. Check your connection."))
                        }
                    }
                }) { Text(tr("Stop sharing")) }
            },
            dismissButton = { TextButton(onClick = { stopDlg = false }) { Text(tr("Cancel")) } }
        )
    }
}
