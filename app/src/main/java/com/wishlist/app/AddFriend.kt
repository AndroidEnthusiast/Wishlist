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
 * Adding a friend by their code (split out of FriendsUi.kt in the rebuild, owner 19 November 2026; MAP "How a screen is built"): the dialog and the code
 * typed into six boxes.
 */

/**
 * Enter a friend code. Asks for your name first when you haven't got one yet (they need to know who's asking).
 * [onClose] gets the message to show (the screen behind shows it: this dialog is gone by then).
 */
@Composable
fun AddFriendDialog(onClose: (String?) -> Unit, prefill: String = "", prefillName: String = "") {
    val ctx = LocalContext.current
    val people = rememberPeople()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val needName = remember { Shared.lastName.isBlank() }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf(prefill) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!needName) try { focus.requestFocus() } catch (e: Exception) {} }

    fun send() {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        if (needName && name.isBlank()) { error = tr("What’s your name?"); return }
        if (clean.length != 6) { error = tr("A friend code has 6 letters and numbers."); return }
        busy = true
        scope.launch {
            try {
                if (needName) {
                    people.setName(name)
                    people.sendName()
                }
                val (status, who) = people.addFriend(clean)
                val n = who.ifBlank { tr("them") }
                busy = false
                when (status) {
                    "sent" -> onClose(tr("Request sent to %s. They need to approve it.", n))
                    "friends" -> onClose(tr("You and %s are friends now", n))
                    "already" -> onClose(tr("You’re already friends with %s, or your request is still waiting.", n))
                    "self" -> error = tr("That’s your own code.")
                    else -> error = tr("No one has that code. Check it and try again.")
                }
            } catch (e: Exception) {
                busy = false
                error = tr("Couldn’t reach the server. Check your connection.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onClose(null) },
        title = { Text(if (prefill.isNotEmpty()) tr("Send %s a friend request?", prefillName.ifBlank { tr("them") }) else tr("Add a friend")) },
        text = {
            val err = error
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (prefill.isNotEmpty()) tr("Once they approve, you see each other’s wishlists.")
                    else tr("Enter your friend’s code. Once they approve, you see each other’s wishlists."),
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                )
                if (needName) OutlinedTextField(
                    name, { name = it.take(30); error = null }, singleLine = true, label = { Text(tr("Your name")) },
                    keyboardOptions = Capitalised
                )
                CodeBoxes(code, { code = it; error = null }, isError = err != null, onDone = { if (!busy) send() }, modifier = Modifier.focusRequester(focus))
                if (err != null) Text(err, color = cs.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { send() }, enabled = !busy) { Text(tr("Send request")) } },
        dismissButton = { TextButton(onClick = { onClose(null) }) { Text(tr("Cancel")) } }
    )
}

/**
 * A friend code typed into six blocks, three, a dash, three, like the code itself (owner, 2.104). Letters become
 * capitals. Pasting works with the code alone ("ABC-123", "abc123") or with the whole shared message: the code is
 * picked out of it. One hidden text field takes the typing; the blocks only show it.
 */
@Composable
fun CodeBoxes(code: String, onChange: (String) -> Unit, isError: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cs = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    androidx.compose.foundation.text.BasicTextField(
        value = androidx.compose.ui.text.input.TextFieldValue(code, androidx.compose.ui.text.TextRange(code.length)),
        onValueChange = { v -> onChange(codeFrom(v.text)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters, autoCorrect = false,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii, imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = tr("Friend code") },
        decorationBox = { inner ->
            Box {
                // The real (invisible) field stays underneath, so long-press > Paste works anywhere on the blocks.
                Box(Modifier.matchParentSize()) { inner() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until 6) {
                        if (i == 3) Text(
                            "–", style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        val active = focused && i == code.length.coerceAtMost(5)
                        val shape = MaterialTheme.shapes.small
                        Box(
                            Modifier.weight(1f).padding(horizontal = 3.dp).height(54.dp)
                                .lookSurface(look, shape, cs.surfaceVariant, raised = false)
                                .border(
                                    if (active || isError) 2.dp else 0.dp,
                                    when { isError -> cs.error; active -> look.textAccent; else -> Color.Transparent }, shape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                code.getOrNull(i)?.toString() ?: "", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, fontFamily = look.display, color = cs.onSurface
                            )
                        }
                    }
                }
            }
        }
    )
}

/** The code in what was typed or pasted: "ABC-123" anywhere in a text first, otherwise its first 6 letters and digits. */
fun codeFrom(text: String): String {
    Regex("([A-Za-z0-9]{3})-([A-Za-z0-9]{3})").find(text)?.let { return (it.groupValues[1] + it.groupValues[2]).uppercase() }
    return text.filter { it.isLetterOrDigit() && it.code < 128 }.uppercase().take(6)
}
