package com.wishlist.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/*
 * The welcome on the very first start (owner, 2.95): three short steps, each with Skip.
 *  1. Your name (and a photo, optional).  2. The name of your first wishlist.  3. How friends reserve gifts.
 * The questions about notifications and background checks come after it (MainActivity).
 * Someone who already used the app (a name, wishes or more lists) never sees it.
 */
object Welcome {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("wishlist", Context.MODE_PRIVATE)

    fun needed(c: Context): Boolean {
        val p = prefs(c)
        if (p.getBoolean("welcomed", false)) return false
        val used = Shared.lastName.isNotBlank() || Repo.myWishes.isNotEmpty() || Repo.mineIds.size > 1 || Shared.lists.isNotEmpty()
        if (used) {
            p.edit().putBoolean("welcomed", true).apply()
            return false
        }
        return true
    }

    fun done(c: Context) = prefs(c).edit().putBoolean("welcomed", true).apply()
}

@Composable
fun WelcomeFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf(Shared.lastName) }
    var listName by remember { mutableStateOf(tr("My wishlist")) }
    var pending by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let { pending = it } }
    }
    fun finish() {
        Welcome.done(ctx)
        onDone()
    }
    fun next() {
        when (step) {
            1 -> if (name.isNotBlank()) {
                Shared.setAppName(ctx, name)
                scope.launch { try { Friends.nameChanged(ctx) } catch (e: Exception) {} }
            }
            2 -> if (listName.isNotBlank()) Repo.renameMine(ctx, Repo.openMine, listName)
        }
        if (step == 3) finish() else step++
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
                // Top: the step and Skip (skips the whole welcome).
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Step %1\$d of %2\$d", step, 3), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { finish() }) { Text(tr("Skip")) }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        1 -> {
                            PlaceholderIcon("hello", 56.dp, LocalLook.current.accent)
                            Text(tr("Welcome to Wishlist"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                            Text(
                                tr("What’s your name? Friends see it, and it’s shown on the wishes you add to a gift list."),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                            )
                            Box(
                                Modifier.clip(CircleShape).clickable {
                                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            ) { Avatar(Friends.myPhoto, name, 96.dp) }
                            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text(if (Friends.myPhoto.isEmpty()) tr("Add a photo (optional)") else tr("Change photo"))
                            }
                            OutlinedTextField(
                                name, { name = it.take(30) }, singleLine = true, label = { Text(tr("Your name")) },
                                keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        2 -> {
                            PlaceholderIcon("gift", 56.dp, LocalLook.current.accent)
                            Text(tr("Your first wishlist"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                            Text(
                                tr("Give it a name, for example “Birthday” or “Christmas”. You can make more wishlists later."),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                            )
                            OutlinedTextField(
                                listName, { listName = it.take(60) }, singleLine = true, label = { Text(tr("Name of the wishlist")) },
                                keyboardOptions = Capitalised, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            PlaceholderIcon("secret", 56.dp, LocalLook.current.accent)
                            Text(tr("Gifts stay a surprise"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                            Text(
                                tr("Share your wishlist with friends. They tap Reserve on a gift they’ll buy, so nobody gives the same gift twice, and you never see what they reserve."),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { next() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (step == 3) tr("Start") else tr("Next"))
                }
            }
        }
    }
    // A photo goes through the round editor first, as on Profile.
    pending?.let { img ->
        HeroEditor(
            img, HeroEdit(), round = true,
            onSave = { chosen, e ->
                pending = null
                if (chosen != img) ImageStore.delete(ctx, img)
                scope.launch { Friends.setPhoto(ctx, chosen, e) }
            },
            onCancel = { pending = null; ImageStore.delete(ctx, img) }
        )
    }
}
