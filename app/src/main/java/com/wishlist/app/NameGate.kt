package com.wishlist.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/*
 * Everyone has a profile name (owner, 6 November 2026; DECISIONS "Wishlist page review"): friends see it on your lists
 * ("Owned by Jorai") and new lists are called after it ("Jorai's wishlist").
 *  - When the app opens without a name (people who skipped it before), it is asked once; it can't be skipped.
 *  - Sharing, inviting or joining asks first when the name is still empty ([ensure]).
 *  - Sharing a list still called "My wishlist" first asks to name it ([ensureListName]), filled in with "Jorai's wishlist".
 */
object NameGate {
    /** Asking for your name: [then] runs after it is saved; [forced] can't be closed (the start of the app). */
    var asking by mutableStateOf(false)
        private set
    private var forced = false
    private var then: (() -> Unit)? = null
    /** Asking to name the open list (still "My wishlist") before [renameThen] shares it. */
    var renaming by mutableStateOf(false)
        private set
    private var renameThen: (() -> Unit)? = null

    fun hasName() = Shared.lastName.isNotBlank()

    /** Runs [action] once you have a name; asks for it first when you don't. */
    fun ensure(action: () -> Unit) {
        if (hasName()) { action(); return }
        forced = false; then = action; asking = true
    }

    /** At the start of the app: no name yet, so it is asked (and can't be skipped). */
    fun askAtStart() {
        if (hasName() || asking) return
        forced = true; then = null; asking = true
    }

    /** The default name of a new list: "Jorai's wishlist" (or "My wishlist" without a name). */
    fun defaultListName(): String = if (hasName()) tr("%s’s wishlist", Shared.lastName) else tr("My wishlist")

    /** Before sharing your open list: a name first, and a list name other than "My wishlist". */
    fun ensureListName(action: () -> Unit) = ensure {
        if (Repo.myName != "My wishlist" && Repo.myName != tr("My wishlist")) action()
        else { renameThen = action; renaming = true }
    }

    @Composable
    fun Host() {
        val ctx = LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        if (asking) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { if (!forced) { asking = false; then = null } },
                properties = DialogProperties(dismissOnClickOutside = !forced, dismissOnBackPress = !forced),
                title = { Text(tr("What’s your name?")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tr("Friends see it on your wishlists, so they know whose list it is."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            name, { name = it.take(30) }, singleLine = true, label = { Text(tr("Your name")) },
                            keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        Shared.setAppName(ctx, name)
                        scope.launch { try { Friends.nameChanged(ctx) } catch (e: Exception) {} }
                        asking = false
                        val next = then
                        then = null
                        next?.invoke()
                    }) { Text(tr("Save")) }
                },
                dismissButton = if (forced) null else ({ TextButton(onClick = { asking = false; then = null }) { Text(tr("Cancel")) } })
            )
        }
        if (renaming) {
            var n by remember { mutableStateOf(defaultListName()) }
            AlertDialog(
                onDismissRequest = { renaming = false; renameThen = null },
                title = { Text(tr("Name your wishlist?")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tr("Friends see this name. “My wishlist” would look like their own list."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            n, { n = it.take(60) }, singleLine = true, label = { Text(tr("Name*")) },
                            keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = n.isNotBlank(), onClick = {
                        Repo.renameMine(ctx, Repo.openMine, n.trim())
                        renaming = false
                        val next = renameThen
                        renameThen = null
                        next?.invoke()
                    }) { Text(tr("Save and share")) }
                },
                dismissButton = { TextButton(onClick = { renaming = false; renameThen = null }) { Text(tr("Cancel")) } }
            )
        }
    }
}
