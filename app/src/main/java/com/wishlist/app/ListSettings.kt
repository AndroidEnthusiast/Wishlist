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
import androidx.compose.ui.focus.onFocusChanged
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
    var stopDlg by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<String?>(null) }
    var deleteMineDlg by remember { mutableStateOf(false) }
    var copyDlg by remember { mutableStateOf(false) }
    var visBusy by remember { mutableStateOf(false) }
    var nameSaved by remember { mutableStateOf(0) }
    var noteSaved by remember { mutableStateOf(0) }
    var occDlg by remember { mutableStateOf(false) }
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
                            if (shared != null) tr("Gift list") else tr("Wishlist settings"),
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

                // No "Your name on this list" any more (owner, 2.102): your profile name is always used.
            }

            // The list's occasion (owner, 2.124): shown at the top of the list as "Birthday · in 3 weeks".
            SectionTitle(tr("Occasion"))
            val occ = Occasions.ofOpen(ctx)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (occ == null) tr("None") else Occasions.label(occ) + " · " + Occasions.dateText(occ),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { occDlg = true }) { Text(if (occ == null) tr("Add") else tr("Change")) }
            }
            if (occ != null) TextButton(onClick = { Occasions.setOpen(ctx, null) }) { Text(tr("Remove occasion")) }
            if (occDlg) OccasionDialog(gift = shared != null, onClose = { occDlg = false })

            // An optional cover photo at the top of the list (2.89): none until you choose one.
            SectionTitle(tr("Cover photo"))
            CoverSection(snack)

            // "Description" (owner, 2.125; it was "Note for friends").
            SectionTitle(tr("Description"))
            Text(
                if (shared != null) tr("Shown at the top of the list.") else tr("Shown at the top of your wishlist."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            // No "Save note" button (owner, 2.105): like the list's name, the keyboard's tick saves it, with the green
            // tick and "Saved" on the right. Leaving the field saves it too, so nothing typed is lost.
            fun saveNote() {
                if (about.trim() == Repo.about.trim()) return
                Repo.setAbout(ctx, about)
                about = Repo.about
                noteSaved++
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    about, { about = it.take(ABOUT_MAX) }, maxLines = 5,
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) saveNote() },
                    supportingText = { Text("${about.length}/$ABOUT_MAX") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = {
                        saveNote()
                        keyboard?.hide()
                        focusManager.clearFocus()
                    })
                )
                SavedFeedback(noteSaved)
            }

            GroupTitle(tr("Who can see it"))
            if (shared != null) {
                val title = Repo.listName
                val invite = inviteLink(shared.key)
                SectionTitle(tr("Invite people"))
                Text(
                    tr("They can edit and reserve wishes. Only send it to people you trust."),
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

            // Categories moved to the wish form's drop-down, "Edit categories" (owner, 2.110): your own lists share
            // them, so they didn't belong to one list's settings.
            if (shared == null) {
                GroupTitle(tr("Wishes"))
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

    manage?.let { m -> SharedManageDialog(m, onClose = { manage = null }, onGone = { manage = null; onBack() }) }
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
            text = { Text(tr("Deleting this wishlist will move all its wishes to the bin, where they can be restored within 30 days.") + groupNote(ctx, Repo.openMine)) },
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
                    if (Friends.isVisible(Repo.openMine)) tr("Friends will lose access, the link will stop working and reservations will be deleted.")
                    else tr("Friends will lose access, the link will stop working and reservations will be deleted.")
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
