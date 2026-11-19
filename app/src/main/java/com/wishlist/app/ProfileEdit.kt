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
import androidx.compose.material3.DropdownMenuItem
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
 * What Profile's rows open (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): the small sheets for your name,
 * birthday and About me, and the Keep your lists safe page.
 */

/** Your name: friends see it, and it shows as "Added by" on wishlists you make with others. */
@Composable
internal fun EditNameSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val people = rememberPeople()
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf(Shared.lastName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Your name")) },
        text = {
            OutlinedTextField(
                me, { me = it.take(30) }, singleLine = true, label = { Text(tr("Name")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = me.isNotBlank(), onClick = {
                people.setName(me)
                scope.launch { try { people.sendName() } catch (e: Exception) {} }
                onClose()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Your birthday (day and month): friends see it and get a reminder two weeks before. */
@Composable
internal fun EditBirthdaySheet(snack: SnackbarHostState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val people = rememberPeople()
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Birthday")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Friends see the day and month, and get a reminder."), style = MaterialTheme.typography.bodyMedium)
                DayMonthPicker(Friends.myBirthday) { b ->
                    scope.launch {
                        try {
                            people.setBirthday(b)
                        } catch (e: Exception) {
                            snack.showToast(tr("Saved on this phone. It goes online when you have a connection."))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done")) } }
    )
}

/** "About me": a line friends see under your name. */
@Composable
internal fun EditBioSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val people = rememberPeople()
    val scope = rememberCoroutineScope()
    var bio by remember { mutableStateOf(Friends.myBio) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("About me")) },
        text = {
            OutlinedTextField(
                bio, { bio = it.replace('\n', ' ').take(Friends.BIO_MAX) }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr("For example: Coffee lover, always cold, into Lego")) },
                supportingText = { Text("${bio.length} / ${Friends.BIO_MAX}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val b = bio
                scope.launch { try { people.setBio(b) } catch (e: Exception) {} }
                onClose()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/**
 * "Keep your lists safe" (owner, 18 November 2026): the recovery key first (show and save it, restore with it), then the
 * backup file as a small row at the bottom (kept on Claude's advice: the last safety net when the server can't help).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeepSafePage(snack: SnackbarHostState, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.activity.compose.BackHandler(onBack = onBack)
    var showKey by remember { mutableStateOf(false) }
    if (showKey) RecoveryKeyDialog { showKey = false }
    var restoring by remember { mutableStateOf(false) }
    if (restoring) RestoreDialog(confirmFirst = true) { restoring = false }
    var file by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle(tr("Keep your lists safe")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, tr("Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileGroup {
                Text(tr("With your recovery key, everything comes back on a new phone."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                Button(onClick = { showKey = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Show and save your key")) }
                TextButton(onClick = { restoring = true }) { Text(tr("Restore with a recovery key")) }
            }
            ProfileGroup {
                ProfileRow(tr("Backup file"), if (file) "" else tr("Optional")) { file = !file }
                if (file) BackupSection(snack)
            }
        }
    }
}
