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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch

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
fun ListSettingsScreen(snack: SnackbarHostState, onBack: () -> Unit) {
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
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Repo.listName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (shared != null) tr("Shared wishlist settings") else tr("Wishlist settings"),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(tr("Name of the wishlist"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = {
                    Repo.setListName(ctx, name)
                    name = Repo.listName
                    scope.launch { snack.showSnackbar(tr("Name saved")) }
                }) { Text(tr("Save")) }
            }
            if (shared != null) {
                Text(tr("Everyone on this list sees the new name."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

                SectionTitle(tr("Your name on this list"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(shared.me.ifBlank { Shared.lastName.ifBlank { tr("No name yet") } }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (shared.me.isBlank() || shared.me == Shared.lastName) tr("From App settings. Shown as “Added by” on the wishes you add.")
                            else tr("Only on this list. App settings has “%s”.", Shared.lastName),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { meDlg = true }) { Text(tr("Use another name")) }
                }
            }

            SectionTitle(tr("Note for friends"))
            Text(
                if (shared != null) tr("Shown at the top of the list. For example sizes, or things the person already has.")
                else tr("Shown at the top of your shared list. For example your sizes, or things you don’t want."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            OutlinedTextField(
                about, { about = it.take(ABOUT_MAX) }, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${about.length}/$ABOUT_MAX") }
            )
            Button(onClick = {
                Repo.setAbout(ctx, about)
                about = Repo.about
                scope.launch { snack.showSnackbar(tr("Note saved")) }
            }) { Text(tr("Save note")) }

            SectionTitle(tr("Categories"))
            if (Repo.cats.isEmpty()) {
                Text(tr("No categories yet."), color = cs.onSurfaceVariant)
            }
            Repo.cats.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(c, Modifier.weight(1f))
                    IconButton(onClick = { renCat = c }) {
                        Icon(Icons.Default.Edit, tr("Edit %s", c), tint = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { delCat = c }) {
                        Icon(Icons.Default.Delete, tr("Remove %s", c), tint = cs.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newCat, { newCat = it }, label = { Text(tr("New category")) },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Button(onClick = { Repo.addCat(ctx, newCat); newCat = "" }) { Text(tr("Add")) }
            }

            if (shared != null) {
                val title = Repo.listName
                val invite = inviteLink(shared.key)
                val friends = sharedListLink(shared.code)
                SectionTitle(tr("Invite to edit"))
                Text(
                    tr("They can add and change wishes and reserve gifts. Only send it to people you trust."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { ShareCard.share(ctx, ShareCard.Kind.INVITE, title, invite) } }) { Text(tr("Send invite")) }
                    OutlinedButton(onClick = {
                        clip.setText(AnnotatedString(invite))
                        scope.launch { snack.showSnackbar(tr("Invite link copied")) }
                    }) { Text(tr("Copy link")) }
                }
                SectionTitle(tr("Link for givers who don’t edit"))
                Text(
                    tr("They can only look and reserve a gift. Everyone on this list sees what’s reserved."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { ShareCard.share(ctx, ShareCard.Kind.FRIENDS, title, friends) } }) { Text(tr("Send link")) }
                    OutlinedButton(onClick = {
                        clip.setText(AnnotatedString(friends))
                        scope.launch { snack.showSnackbar(tr("Link copied")) }
                    }) { Text(tr("Copy link")) }
                }
                SectionTitle(tr("Manage"))
                if (shared.owner.isNotEmpty()) {
                    TextButton(onClick = { manage = "reset" }) { Text(tr("Make a new invite link")) }
                }
                TextButton(onClick = { manage = "leave" }) { Text(tr("Leave this wishlist")) }
                if (shared.owner.isNotEmpty()) {
                    TextButton(onClick = { manage = "delete" }) { Text(tr("Delete for everyone"), color = cs.error) }
                }
            } else {
                SectionTitle(tr("Sharing"))
                val code = Repo.shareCode
                if (!Config.enabled) {
                    Text(tr("Online sharing isn’t set up in this build yet."), color = cs.onSurfaceVariant)
                } else if (code == null) {
                    Text(
                        tr("Not shared yet. Share your list so friends can reserve gifts. You won’t see what they reserve."),
                        color = cs.onSurfaceVariant
                    )
                    Button(onClick = { startShare(ctx, scope, snack) }) { Text(tr("Share my wishlist")) }
                } else {
                    Text(tr("Friends open this link and tap Reserve. You never see what they reserve."), color = cs.onSurfaceVariant)
                    Text(shareLink(code), color = cs.onSurface)
                    if (Sync.photoStatus.isNotEmpty()) {
                        Text(Sync.photoStatus, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        tr("Don’t open your own link in a browser, or you’ll see what’s reserved."),
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { startShare(ctx, scope, snack) }) { Text(tr("Share link")) }
                        OutlinedButton(onClick = {
                            clip.setText(AnnotatedString(shareLink(code)))
                            scope.launch { snack.showSnackbar(tr("Link copied")) }
                        }) { Text(tr("Copy link")) }
                    }
                }
                TextButton(onClick = { shareList(ctx, Repo.wishes) }) { Text(tr("Export as plain text")) }
                if (Config.enabled && code != null) {
                    TextButton(onClick = { stopDlg = true }) { Text(tr("Stop sharing"), color = cs.error) }
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
                        tr("Only for “%s”. Leave it empty to use the name from App settings.", Repo.listName),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    OutlinedTextField(n, { n = it.take(30) }, singleLine = true, label = { Text(tr("Your name")) })
                }
            },
            confirmButton = {
                TextButton(onClick = { Shared.setMe(ctx, shared.code, n); meDlg = false }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { meDlg = false }) { Text(tr("Cancel")) } }
        )
    }
    if (stopDlg) {
        AlertDialog(
            onDismissRequest = { stopDlg = false },
            title = { Text(tr("Stop sharing?")) },
            text = { Text(tr("The link stops working and all reservations are deleted. Sharing again later creates a new link.")) },
            confirmButton = {
                TextButton(onClick = {
                    stopDlg = false
                    scope.launch {
                        try {
                            Sync.unpublish(ctx)
                        } catch (e: Exception) {
                            snack.showSnackbar(tr("Couldn’t stop sharing. Check your connection."))
                        }
                    }
                }) { Text(tr("Stop sharing")) }
            },
            dismissButton = { TextButton(onClick = { stopDlg = false }) { Text(tr("Cancel")) } }
        )
    }
}
